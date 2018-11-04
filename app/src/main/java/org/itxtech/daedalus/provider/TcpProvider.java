package org.itxtech.daedalus.provider;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import androidx.annotation.NonNull;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.server.DNSServerHelper;
import org.pcap4j.packet.IpPacket;

import java.io.*;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;

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
public class TcpProvider extends UdpProvider {

    private static final String TAG = "TcpProvider";

    protected final TcpProvider.WospList dnsIn = new TcpProvider.WospList();

    public TcpProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
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
                {
                    int i = -1;
                    for (TcpProvider.WaitingOnSocketPacket wosp : dnsIn) {
                        i++;
                        StructPollfd pollFd = polls[2 + i] = new StructPollfd();
                        pollFd.fd = ParcelFileDescriptor.fromSocket(wosp.socket).getFileDescriptor();
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
                    Iterator<TcpProvider.WaitingOnSocketPacket> iter = dnsIn.iterator();
                    while (iter.hasNext()) {
                        i++;
                        TcpProvider.WaitingOnSocketPacket wosp = iter.next();
                        if ((polls[i + 2].revents & OsConstants.POLLIN) != 0) {
                            Log.d(TAG, "Read from TCP DNS socket" + wosp.socket);
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
                service.providerLoopCallback();
            }
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    protected byte[] processUdpPacket(DatagramPacket outPacket, IpPacket parsedPacket) {
        if (parsedPacket == null) {
            return new byte[0];
        }
        return outPacket.getData();
    }

    @Override
    protected void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket) throws DaedalusVpnService.VpnNetworkException {
        Socket dnsSocket;
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = SocketChannel.open().socket();

            service.protect(dnsSocket);

            SocketAddress address = new InetSocketAddress(outPacket.getAddress(), DNSServerHelper.getPortOrDefault(outPacket.getAddress(), outPacket.getPort()));
            dnsSocket.connect(address, 5000);
            dnsSocket.setSoTimeout(5000);
            Logger.info("TcpProvider: Sending DNS query request");
            DataOutputStream dos = new DataOutputStream(dnsSocket.getOutputStream());
            byte[] packet = processUdpPacket(outPacket, parsedPacket);
            dos.writeShort(packet.length);
            dos.write(packet);
            dos.flush();

            if (parsedPacket != null) {
                dnsIn.add(new TcpProvider.WaitingOnSocketPacket(dnsSocket, parsedPacket));
            } else {
                dnsSocket.close();
            }
        } catch (IOException e) {
            if (e.getCause() instanceof ErrnoException) {
                ErrnoException errnoExc = (ErrnoException) e.getCause();
                if ((errnoExc.errno == OsConstants.ENETUNREACH) || (errnoExc.errno == OsConstants.EPERM)) {
                    throw new DaedalusVpnService.VpnNetworkException("Cannot send message:", e);
                }
            }
            Log.w(TAG, "handleDnsRequest: Could not send packet to upstream", e);
        }
    }

    private void handleRawDnsResponse(IpPacket parsedPacket, Socket dnsSocket) {
        try {
            DataInputStream stream = new DataInputStream(dnsSocket.getInputStream());
            int length = stream.readUnsignedShort();
            Log.d(TAG, "Reading length: " + String.valueOf(length));
            byte[] data = new byte[length];
            stream.read(data);
            dnsSocket.close();
            handleDnsResponse(parsedPacket, data);
        } catch (Exception ignored) {

        }
    }

    /**
     * Helper class holding a socket, the packet we are waiting the answer for, and a time
     */
    public static class WaitingOnSocketPacket {
        final Socket socket;
        final IpPacket packet;
        private final long time;

        WaitingOnSocketPacket(Socket socket, IpPacket packet) {
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
    public static class WospList implements Iterable<TcpProvider.WaitingOnSocketPacket> {
        private final LinkedList<TcpProvider.WaitingOnSocketPacket> list = new LinkedList<>();

        void add(TcpProvider.WaitingOnSocketPacket wosp) {
            try {
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
            } catch (Exception ignored) {
            }
        }

        @NonNull
        public Iterator<TcpProvider.WaitingOnSocketPacket> iterator() {
            return list.iterator();
        }

        int size() {
            return list.size();
        }

    }
}
