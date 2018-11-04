package org.itxtech.daedalus.provider;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import androidx.annotation.NonNull;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.minidns.dnsmessage.DnsMessage;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.UdpPacket;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
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
abstract public class HttpsProvider extends Provider {
    protected static final String HTTPS_SUFFIX = "https://";

    private static final String TAG = "HttpsProvider";

    protected final WhqList whqList = new WhqList();

    public HttpsProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
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

                StructPollfd[] polls = new StructPollfd[2];
                polls[0] = deviceFd;
                polls[1] = blockFd;
                Os.poll(polls, 100);
                if (blockFd.revents != 0) {
                    Log.i(TAG, "Told to stop VPN");
                    running = false;
                    return;
                }

                Iterator<WaitingHttpsRequest> iterator = whqList.iterator();
                while (iterator.hasNext()) {
                    WaitingHttpsRequest request = iterator.next();
                    if (request.completed) {
                        handleDnsResponse(request.packet, request.result);
                        iterator.remove();
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

    @Override
    protected void handleDnsRequest(byte[] packetData) {
        IpPacket parsedPacket;
        try {
            parsedPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            return;
        }

        if (!(parsedPacket.getPayload() instanceof UdpPacket)) {
            return;
        }

        InetAddress destAddr = parsedPacket.getHeader().getDstAddr();
        if (destAddr == null)
            return;
        String uri;
        try {
            uri = service.dnsServers.get(destAddr.getHostAddress());//https uri
        } catch (Exception e) {
            Logger.logException(e);
            return;
        }

        UdpPacket parsedUdp = (UdpPacket) parsedPacket.getPayload();

        if (parsedUdp.getPayload() == null) {
            return;
        }

        byte[] dnsRawData = (parsedUdp).getPayload().getRawData();
        DnsMessage dnsMsg;
        try {
            dnsMsg = new DnsMessage(dnsRawData);
            if (Daedalus.getPrefs().getBoolean("settings_debug_output", false)) {
                Logger.debug("DnsRequest: " + dnsMsg.toString());
            }
        } catch (IOException e) {
            return;
        }
        if (dnsMsg.getQuestion() == null) {
            Log.i(TAG, "handleDnsRequest: Discarding DNS packet with no query " + dnsMsg);
            return;
        }

        if (!resolve(parsedPacket, dnsMsg) && uri != null) {
            sendRequestToServer(parsedPacket, dnsMsg, uri);
            //SHOULD use a DNS ID of 0 in every DNS request (according to draft-ietf-doh-dns-over-https-11)
        }
    }

    protected abstract void sendRequestToServer(IpPacket parsedPacket, DnsMessage message, String uri);
    //uri example: 1.1.1.1:1234/dnsQuery. The specified provider will add https:// and parameters

    public abstract static class WaitingHttpsRequest {
        public boolean completed = false;
        public byte[] result;
        public final IpPacket packet;

        public WaitingHttpsRequest(IpPacket packet) {
            this.packet = packet;
        }

        public abstract void doRequest();
    }

    public static class WhqList implements Iterable<WaitingHttpsRequest> {
        private final LinkedList<WaitingHttpsRequest> list = new LinkedList<>();

        public void add(WaitingHttpsRequest request) {
            list.add(request);
            request.doRequest();
        }

        @NonNull
        public Iterator<WaitingHttpsRequest> iterator() {
            return list.iterator();
        }
    }
}
