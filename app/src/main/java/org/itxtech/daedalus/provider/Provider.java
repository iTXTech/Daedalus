package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import androidx.annotation.NonNull;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.RuleResolver;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.record.A;
import org.minidns.record.AAAA;
import org.minidns.record.Record;
import org.pcap4j.packet.*;

import java.io.*;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.Socket;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public abstract class Provider {
    private static final String TAG = "Provider";

    protected ParcelFileDescriptor descriptor;
    protected DaedalusVpnService service;
    protected boolean running = false;
    protected static long dnsQueryTimes = 0;

    protected FileDescriptor mBlockFd = null;
    protected FileDescriptor mInterruptFd = null;
    protected final Queue<byte[]> deviceWrites = new LinkedList<>();
    protected WospList dnsIn = new WospList(true);

    Provider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        this.descriptor = descriptor;
        this.service = service;
        dnsQueryTimes = 0;
    }

    public final long getDnsQueryTimes() {
        return dnsQueryTimes;
    }

    public void process() {
        try {
            FileDescriptor[] pipes = Os.pipe();
            mInterruptFd = pipes[0];
            mBlockFd = pipes[1];
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
                if (dnsIn.closeable) {
                    int i = -1;
                    for (WaitingOnSocketPacket wosp : dnsIn) {
                        i++;
                        StructPollfd pollFd = polls[2 + i] = new StructPollfd();
                        if (wosp.socket instanceof DatagramSocket) {
                            pollFd.fd = ParcelFileDescriptor.fromDatagramSocket((DatagramSocket) wosp.socket).getFileDescriptor();
                        } else if (wosp.socket instanceof Socket) {
                            pollFd.fd = ParcelFileDescriptor.fromSocket((Socket) wosp.socket).getFileDescriptor();
                        }
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

                int i = -1;
                Iterator<WaitingOnSocketPacket> iter = dnsIn.iterator();
                while (iter.hasNext()) {
                    i++;
                    WaitingOnSocketPacket wosp = iter.next();
                    if (!dnsIn.closeable && wosp.completed) {
                        handleDnsResponse(wosp.packet, wosp.result);
                        iter.remove();
                    } else if (dnsIn.closeable && (polls[i + 2].revents & OsConstants.POLLIN) != 0) {
                        Log.d(TAG, "Read from UDP DNS socket" + wosp.socket);
                        iter.remove();
                        handleRawDnsResponse(wosp.packet, wosp.socket);
                        ((Closeable) wosp.socket).close();
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
                service.providerLoopCallback();
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    protected void handleRawDnsResponse(IpPacket parsedPacket, Object dnsSocket) {

    }

    static class WaitingOnSocketPacket {
        final Object socket;
        final IpPacket packet;
        private final long time;
        boolean completed = false;
        byte[] result;

        WaitingOnSocketPacket(Object socket, IpPacket packet) {
            this.socket = socket;
            this.packet = packet;
            this.time = System.currentTimeMillis();
        }

        long ageSeconds() {
            return (System.currentTimeMillis() - time) / 1000;
        }

        public void doRequest() {

        }
    }

    /**
     * Queue of WaitingOnSocketPacket, bound on time and space.
     */
    public static class WospList implements Iterable<WaitingOnSocketPacket> {
        private final LinkedList<WaitingOnSocketPacket> list = new LinkedList<>();

        private boolean closeable;

        WospList(boolean closeable) {
            this.closeable = closeable;
        }

        public void add(WaitingOnSocketPacket wosp) {
            if (closeable) {
                try {
                    if (list.size() > 1024) {
                        Log.d(TAG, "Dropping socket due to space constraints: " + list.element().socket);
                        ((Closeable) list.element().socket).close();
                        list.remove();
                    }
                    while (!list.isEmpty() && list.element().ageSeconds() > 10) {
                        Log.d(TAG, "Timeout on socket " + list.element().socket);
                        ((Closeable) list.element().socket).close();
                        list.remove();
                    }
                } catch (Exception e) {
                    Logger.logException(e);
                }
            }
            list.add(wosp);
            wosp.doRequest();

        }

        @NonNull
        public Iterator<WaitingOnSocketPacket> iterator() {
            return list.iterator();
        }

        int size() {
            return list.size();
        }
    }

    public final void start() {
        running = true;
    }

    public final void shutdown() {
        running = false;
    }

    public void stop() {
        try {
            if (mInterruptFd != null) {
                Os.close(mInterruptFd);
            }
            if (mBlockFd != null) {
                Os.close(mBlockFd);
            }
            if (this.descriptor != null) {
                this.descriptor.close();
                this.descriptor = null;
            }
        } catch (Exception ignored) {
        }
    }

    protected void queueDeviceWrite(IpPacket ipOutPacket) {
        dnsQueryTimes++;
        deviceWrites.add(ipOutPacket.getRawData());
    }

    public boolean resolve(IpPacket parsedPacket, DnsMessage dnsMsg) {
        String dnsQueryName = dnsMsg.getQuestion().name.toString();

        try {
            String response = RuleResolver.resolve(dnsQueryName, dnsMsg.getQuestion().type);
            if (response != null && dnsMsg.getQuestion().type == Record.TYPE.A) {
                Logger.info("Provider: Resolved " + dnsQueryName + "  Local resolver response: " + response);
                DnsMessage.Builder builder = dnsMsg.asBuilder()
                        .setQrFlag(true)
                        .addAnswer(new Record<>(dnsQueryName, Record.TYPE.A, 1, 64,
                                new A(Inet4Address.getByName(response).getAddress())));
                handleDnsResponse(parsedPacket, builder.build().toArray());
                return true;
            } else if (response != null && dnsMsg.getQuestion().type == Record.TYPE.AAAA) {
                Logger.info("Provider: Resolved " + dnsQueryName + "  Local resolver response: " + response);
                DnsMessage.Builder builder = dnsMsg.asBuilder()
                        .setQrFlag(true)
                        .addAnswer(new Record<>(dnsQueryName, Record.TYPE.AAAA, 1, 64,
                                new AAAA(Inet6Address.getByName(response).getAddress())));
                handleDnsResponse(parsedPacket, builder.build().toArray());
                return true;
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
        return false;
    }

    /**
     * Handles a responsePayload from an upstream DNS server
     *
     * @param requestPacket   The original request packet
     * @param responsePayload The payload of the response
     */
    void handleDnsResponse(IpPacket requestPacket, byte[] responsePayload) {
        if (Daedalus.getPrefs().getBoolean("settings_debug_output", false)) {
            try {
                Logger.debug("DnsResponse: " + new DnsMessage(responsePayload).toString());
            } catch (IOException e) {
                Logger.logException(e);
            }
        }
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

    protected void writeToDevice(FileOutputStream outFd) throws DaedalusVpnService.VpnNetworkException {
        try {
            outFd.write(deviceWrites.poll());
        } catch (IOException e) {
            throw new DaedalusVpnService.VpnNetworkException("Outgoing VPN output stream closed");
        }
    }

    protected void readPacketFromDevice(FileInputStream inputStream, byte[] packet) throws DaedalusVpnService.VpnNetworkException {
        // Read the outgoing packet from the input stream.
        int length;
        try {
            length = inputStream.read(packet);
        } catch (IOException e) {
            throw new DaedalusVpnService.VpnNetworkException("Cannot read from device", e);
        }
        if (length == 0) {
            return;
        }
        final byte[] readPacket = Arrays.copyOfRange(packet, 0, length);
        handleDnsRequest(readPacket);
    }

    protected abstract void handleDnsRequest(byte[] packetData) throws DaedalusVpnService.VpnNetworkException;
}
