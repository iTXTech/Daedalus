package org.itxtech.daedalus.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.preference.PreferenceManager;
import android.support.v7.app.NotificationCompat;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import de.measite.minidns.DNSMessage;
import de.measite.minidns.util.InetAddressUtil;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.activity.SettingsActivity;
import org.itxtech.daedalus.receiver.StatusBarBroadcastReceiver;
import org.pcap4j.packet.*;
import org.pcap4j.packet.factory.PacketFactoryPropertiesLoader;
import org.pcap4j.util.PropertiesLoader;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Daedalus Project
 *
 * @author iTXTech
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 */
public class DaedalusVpnService extends VpnService implements Runnable {
    public static final String ACTION_ACTIVATE = "org.itxtech.daedalus.service.DaedalusVpnService.ACTION_ACTIVATE";
    public static final String ACTION_DEACTIVATE = "org.itxtech.daedalus.service.DaedalusVpnService.ACTION_DEACTIVATE";

    private static final int NOTIFICATION_ACTIVATED = 0;

    private static final String TAG = "Daedalus";

    public static String primaryServer;
    public static String secondaryServer;

    public static NotificationCompat.Builder notification = null;

    public static long numberOfQueries = 0;

    private Thread mThread = null;
    private ParcelFileDescriptor descriptor;
    private boolean running = false;
    private long lastUpdate = 0;

    private final WospList dnsIn = new WospList();
    private FileDescriptor mBlockFd = null;
    private FileDescriptor mInterruptFd = null;
    private final Queue<byte[]> deviceWrites = new LinkedList<>();
    /**
     * Number of iterations since we last cleared the pcap4j cache
     */
    private int pcap4jFactoryClearCacheCounter = 0;
    /**
     * After how many iterations we should clear pcap4js packetfactory property cache
     */
    private final int PCAP4J_FACTORY_CLEAR_NASTY_CACHE_EVERY = 1024;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ACTION_ACTIVATE:
                    if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("settings_notification", true)) {

                        NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);

                        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

                        Intent nIntent = new Intent(this, MainActivity.class);
                        PendingIntent pIntent = PendingIntent.getActivity(this, 0, nIntent, PendingIntent.FLAG_UPDATE_CURRENT);
                        builder.setContentTitle(getResources().getString(R.string.notification_activated))
                                .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
                                .setSmallIcon(R.mipmap.ic_security_black_18dp)
                                .setAutoCancel(true)
                                .setOngoing(true)
                                .setTicker(getResources().getString(R.string.notification_activated))
                                .setContentIntent(pIntent)
                                .addAction(R.mipmap.ic_security_black_18dp, getResources().getString(R.string.button_text_deactivate),
                                        PendingIntent.getBroadcast(this, 0,
                                                new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_DEACTIVATE_CLICK_ACTION), 0))
                                .addAction(R.mipmap.ic_security_black_18dp, getResources().getString(R.string.action_settings),
                                        PendingIntent.getBroadcast(this, 0,
                                                new Intent(StatusBarBroadcastReceiver.STATUS_BAR_BTN_SETTINGS_CLICK_ACTION), 0));

                        Notification notification = builder.build();

                        manager.notify(NOTIFICATION_ACTIVATED, notification);

                        DaedalusVpnService.notification = builder;
                    }

                    numberOfQueries = 0;
                    if (this.mThread == null) {
                        this.mThread = new Thread(this, "DaedalusVpn");
                        this.running = true;
                        this.mThread.start();
                    }
                    return START_STICKY;
                case ACTION_DEACTIVATE:
                    stopThread();

                    NotificationManager notificationManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
                    notificationManager.cancel(NOTIFICATION_ACTIVATED);

                    notification = null;
                    return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopThread();
    }

    private void stopThread() {
        try {
            if (this.descriptor != null) {
                this.descriptor.close();
                this.descriptor = null;
            }
            if (this.mThread != null) {
                this.running = false;
                this.mThread.interrupt();
                Os.close(mInterruptFd);
                Os.close(mBlockFd);
                this.mThread = null;
            }
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        }
        stopSelf();
    }

    @Override
    public void onRevoke() {
        stopThread();
    }

    @Override
    public void run() {
        try {
            FileDescriptor[] pipes = Os.pipe();
            mInterruptFd = pipes[0];
            mBlockFd = pipes[1];

            Builder builder = new Builder();
            String format = null;
            for (String prefix : new String[]{"192.0.2", "198.51.100", "203.0.113", "10.0.0.", "192.168.50"}) {
                try {
                    builder.addAddress(prefix + ".1", 24);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                format = prefix;
                break;
            }
            if (this.descriptor != null) {
                this.descriptor.close();
            }

            boolean statisticQuery = PreferenceManager.getDefaultSharedPreferences(this).getBoolean("settings_query", false);
            Log.d(TAG, "tun0 add " + format + " pServ " + primaryServer + " sServ " + secondaryServer);
            Inet4Address primaryDNSServer = InetAddressUtil.ipv4From(primaryServer);
            Inet4Address secondaryDNSServer = InetAddressUtil.ipv4From(secondaryServer);
            builder.addDnsServer(primaryDNSServer);
            builder.addDnsServer(secondaryDNSServer);

            if (statisticQuery) {
                builder.addRoute(primaryDNSServer, primaryDNSServer.getAddress().length * 8)
                        .addRoute(secondaryDNSServer, secondaryDNSServer.getAddress().length * 8);
            }

            this.descriptor = builder.setSession("Daedalus").setConfigureIntent(PendingIntent.getActivity(this, 0, new Intent(this, SettingsActivity.class), PendingIntent.FLAG_ONE_SHOT)).setBlocking(true).establish();

            if (statisticQuery) {
                Log.d(TAG, "Starting count queries");
                FileInputStream inputStream = new FileInputStream(descriptor.getFileDescriptor());
                FileOutputStream outputStream = new FileOutputStream(descriptor.getFileDescriptor());

                byte[] packet = new byte[32767];
                while (running) {
                    StructPollfd deviceFd = new StructPollfd();
                    deviceFd.fd = inputStream.getFD();
                    deviceFd.events = (short) OsConstants.POLLIN;
                    StructPollfd blockFd = new StructPollfd();
                    blockFd.fd = mBlockFd;
                    blockFd.events = (short) (OsConstants.POLLHUP | OsConstants.POLLERR);

                    if (!deviceWrites.isEmpty())
                        deviceFd.events |= (short) OsConstants.POLLOUT;

                    StructPollfd[] polls = new StructPollfd[2 + dnsIn.size()];
                    polls[0] = deviceFd;
                    polls[1] = blockFd;
                    {
                        int i = -1;
                        for (WaitingOnSocketPacket wosp : dnsIn) {
                            i++;
                            StructPollfd pollFd = polls[2 + i] = new StructPollfd();
                            pollFd.fd = ParcelFileDescriptor.fromDatagramSocket(wosp.socket).getFileDescriptor();
                            pollFd.events = (short) OsConstants.POLLIN;
                        }
                    }

                    Log.d(TAG, "doOne: Polling " + polls.length + " file descriptors");
                    Os.poll(polls, -1);
                    if (blockFd.revents != 0) {
                        Log.i(TAG, "Told to stop VPN");
                        running = false;
                        return;
                    }

                    // Need to do this before reading from the device, otherwise a new insertion there could
                    // invalidate one of the sockets we want to read from either due to size or time out
                    // constraints
                    {
                        int i = -1;
                        Iterator<WaitingOnSocketPacket> iter = dnsIn.iterator();
                        while (iter.hasNext()) {
                            i++;
                            WaitingOnSocketPacket wosp = iter.next();
                            if ((polls[i + 2].revents & OsConstants.POLLIN) != 0) {
                                Log.d(TAG, "Read from DNS socket" + wosp.socket);
                                iter.remove();
                                handleRawDnsResponse(wosp.packet, wosp.socket);
                                wosp.socket.close();
                            }
                        }
                    }
                    if ((deviceFd.revents & OsConstants.POLLOUT) != 0) {
                        Log.d(TAG, "Write to device");
                        writeToDevice(outputStream);
                    }
                    if ((deviceFd.revents & OsConstants.POLLIN) != 0) {
                        Log.d(TAG, "Read from device");
                        readPacketFromDevice(inputStream, packet);
                    }

                    // pcap4j has some sort of properties cache in the packet factory. This cache leaks, so
                    // we need to clean it up.
                    if (++pcap4jFactoryClearCacheCounter % PCAP4J_FACTORY_CLEAR_NASTY_CACHE_EVERY == 0) {
                        try {
                            PacketFactoryPropertiesLoader l = PacketFactoryPropertiesLoader.getInstance();
                            Field field = l.getClass().getDeclaredField("loader");
                            field.setAccessible(true);
                            PropertiesLoader loader = (PropertiesLoader) field.get(l);
                            Log.d(TAG, "Cleaning cache");
                            loader.clearCache();
                        } catch (NoSuchFieldException e) {
                            Log.e(TAG, "Cannot find declared loader field", e);
                        } catch (IllegalAccessException e) {
                            Log.e(TAG, "Cannot get declared loader field", e);
                        }
                    }

                    updateUserInterface();
                }
            }

            while (running) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            Log.d(TAG, e.toString());
        } finally {
            Log.d(TAG, "quit");
            stopThread();
        }
    }

    private void updateUserInterface() {
        long time = System.currentTimeMillis();
        if (time - lastUpdate >= 1000) {
            lastUpdate = time;
            Log.i(TAG, "notify");
            notification.setContentTitle(getResources().getString(R.string.notification_queries) + " " + String.valueOf(numberOfQueries));

            NotificationManager manager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ACTIVATED, notification.build());
        }
    }

    private void writeToDevice(FileOutputStream outFd) throws VpnNetworkException {
        try {
            outFd.write(deviceWrites.poll());
        } catch (IOException e) {
            // TODO: Make this more specific, only for: "File descriptor closed"
            throw new VpnNetworkException("Outgoing VPN output stream closed");
        }
    }

    private void readPacketFromDevice(FileInputStream inputStream, byte[] packet) throws VpnNetworkException, SocketException {
        // Read the outgoing packet from the input stream.
        int length;

        try {
            length = inputStream.read(packet);
        } catch (IOException e) {
            throw new VpnNetworkException("Cannot read from device", e);
        }


        if (length == 0) {
            // TODO: Possibly change to exception
            Log.w(TAG, "Got empty packet!");
            return;
        }

        final byte[] readPacket = Arrays.copyOfRange(packet, 0, length);

        handleDnsRequest(readPacket);
    }

    private void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket) throws VpnNetworkException {
        DatagramSocket dnsSocket;
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = new DatagramSocket();

            this.protect(dnsSocket);

            dnsSocket.send(outPacket);

            if (parsedPacket != null) {
                dnsIn.add(new WaitingOnSocketPacket(dnsSocket, parsedPacket));
            } else {
                dnsSocket.close();
            }
        } catch (IOException e) {
            if (e.getCause() instanceof ErrnoException) {
                ErrnoException errnoExc = (ErrnoException) e.getCause();
                if ((errnoExc.errno == OsConstants.ENETUNREACH) || (errnoExc.errno == OsConstants.EPERM)) {
                    throw new VpnNetworkException("Cannot send message:", e);
                }
            }
            Log.w(TAG, "handleDnsRequest: Could not send packet to upstream", e);
        }
    }

    private void queueDeviceWrite(IpPacket ipOutPacket) {
        numberOfQueries++;
        Log.i(TAG, "QT " + numberOfQueries);
        deviceWrites.add(ipOutPacket.getRawData());
    }

    private void handleRawDnsResponse(IpPacket parsedPacket, DatagramSocket dnsSocket) throws IOException {
        byte[] datagramData = new byte[1024];
        DatagramPacket replyPacket = new DatagramPacket(datagramData, datagramData.length);
        dnsSocket.receive(replyPacket);
        handleDnsResponse(parsedPacket, datagramData);
    }


    /**
     * Handles a responsePayload from an upstream DNS server
     *
     * @param requestPacket   The original request packet
     * @param responsePayload The payload of the response
     */
    private void handleDnsResponse(IpPacket requestPacket, byte[] responsePayload) {
        UdpPacket udpOutPacket = (UdpPacket) requestPacket.getPayload();
        UdpPacket.Builder payLoadBuilder = new UdpPacket.Builder(udpOutPacket)
                .srcPort(udpOutPacket.getHeader().getDstPort())
                .dstPort(udpOutPacket.getHeader().getSrcPort())
                .srcAddr(requestPacket.getHeader().getDstAddr())
                .dstAddr(requestPacket.getHeader().getSrcAddr())
                .correctChecksumAtBuild(true)
                .correctLengthAtBuild(true)
                .payloadBuilder(
                        new UnknownPacket.Builder()
                                .rawData(responsePayload)
                );


        IpPacket ipOutPacket;
        if (requestPacket instanceof IpV4Packet) {
            ipOutPacket = new IpV4Packet.Builder((IpV4Packet) requestPacket)
                    .srcAddr((Inet4Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet4Address) requestPacket.getHeader().getSrcAddr())
                    .correctChecksumAtBuild(true)
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();

        } else {
            ipOutPacket = new IpV6Packet.Builder((IpV6Packet) requestPacket)
                    .srcAddr((Inet6Address) requestPacket.getHeader().getDstAddr())
                    .dstAddr((Inet6Address) requestPacket.getHeader().getSrcAddr())
                    .correctLengthAtBuild(true)
                    .payloadBuilder(payLoadBuilder)
                    .build();
        }

        queueDeviceWrite(ipOutPacket);
    }

    /**
     * Handles a DNS request, by either blocking it or forwarding it to the remote location.
     *
     * @param packetData The packet data to read
     * @throws VpnNetworkException If some network error occurred
     */
    private void handleDnsRequest(byte[] packetData) throws VpnNetworkException {

        IpPacket parsedPacket;
        try {
            parsedPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            Log.i(TAG, "handleDnsRequest: Discarding invalid IP packet", e);
            return;
        }

        if (!(parsedPacket.getPayload() instanceof UdpPacket)) {
            Log.i(TAG, "handleDnsRequest: Discarding unknown packet type " + parsedPacket.getPayload());
            return;
        }

        InetAddress destAddr = parsedPacket.getHeader().getDstAddr();
        if (destAddr == null)
            return;

        UdpPacket parsedUdp = (UdpPacket) parsedPacket.getPayload();


        if (parsedUdp.getPayload() == null) {
            Log.i(TAG, "handleDnsRequest: Sending UDP packet without payload: " + parsedUdp);

            // Let's be nice to Firefox. Firefox uses an empty UDP packet to
            // the gateway to reduce the RTT. For further details, please see
            // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
            DatagramPacket outPacket = new DatagramPacket(new byte[0], 0, 0, destAddr, parsedUdp.getHeader().getDstPort().valueAsInt());
            forwardPacket(outPacket, null);
            return;
        }

        byte[] dnsRawData = (parsedUdp).getPayload().getRawData();
        DNSMessage dnsMsg;
        try {
            dnsMsg = new DNSMessage(dnsRawData);
        } catch (IOException e) {
            Log.i(TAG, "handleDnsRequest: Discarding non-DNS or invalid packet", e);
            return;
        }
        if (dnsMsg.getQuestion() == null) {
            Log.i(TAG, "handleDnsRequest: Discarding DNS packet with no query " + dnsMsg);
            return;
        }
        String dnsQueryName = dnsMsg.getQuestion().name.toString();
        Log.i(TAG, "handleDnsRequest: DNS Name " + dnsQueryName + " , sending to " + destAddr);
        DatagramPacket outPacket = new DatagramPacket(dnsRawData, 0, dnsRawData.length, destAddr, parsedUdp.getHeader().getDstPort().valueAsInt());
        forwardPacket(outPacket, parsedPacket);
    }

    static class VpnNetworkException extends Exception {
        VpnNetworkException(String s) {
            super(s);
        }

        VpnNetworkException(String s, Throwable t) {
            super(s, t);
        }

    }

    /**
     * Helper class holding a socket, the packet we are waiting the answer for, and a time
     */
    private static class WaitingOnSocketPacket {
        final DatagramSocket socket;
        final IpPacket packet;
        private final long time;

        WaitingOnSocketPacket(DatagramSocket socket, IpPacket packet) {
            this.socket = socket;
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }

        long ageSeconds() {
            return (System.currentTimeMillis() - time) / 1000;
        }
    }

    /**
     * Queue of WaitingOnSocketPacket, bound on time and space.
     */
    private static class WospList implements Iterable<WaitingOnSocketPacket> {
        private final LinkedList<WaitingOnSocketPacket> list = new LinkedList<>();

        void add(WaitingOnSocketPacket wosp) {
            if (list.size() > 1024) {
                Log.d(TAG, "Dropping socket due to space constraints: " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            while (!list.isEmpty() && list.element().ageSeconds() > 10) {
                Log.d(TAG, "Timeout on socket " + list.element().socket);
                list.element().socket.close();
                list.remove();
            }
            list.add(wosp);
        }

        public Iterator<WaitingOnSocketPacket> iterator() {
            return list.iterator();
        }

        int size() {
            return list.size();
        }

    }
}