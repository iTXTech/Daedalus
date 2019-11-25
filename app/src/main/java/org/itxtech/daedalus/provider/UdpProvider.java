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
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.minidns.dnsmessage.DnsMessage;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.UdpPacket;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
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
public class UdpProvider extends Provider {
    public UdpProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    protected void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket, AbstractDnsServer dnsServer) throws DaedalusVpnService.VpnNetworkException {
        DatagramSocket dnsSocket;
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = new DatagramSocket();

            service.protect(dnsSocket);

            dnsSocket.send(outPacket);

            if (parsedPacket != null) {
                dnsIn.add(new WaitingOnSocketPacket(dnsSocket, parsedPacket));
            } else {
                dnsSocket.close();
            }
        } catch (IOException e) {
            handleDnsResponse(parsedPacket, outPacket.getData());
            Logger.warning("DNSProvider: Could not send packet to upstream, forwarding packet directly");
        }
    }

    @Override
    protected void handleRawDnsResponse(IpPacket parsedPacket, Object dnsSocket) {
        try {
            byte[] datagramData = new byte[1024];
            DatagramPacket replyPacket = new DatagramPacket(datagramData, datagramData.length);
            ((DatagramSocket) dnsSocket).receive(replyPacket);
            handleDnsResponse(parsedPacket, datagramData);
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    /**
     * Handles a DNS request, by either blocking it or forwarding it to the remote location.
     *
     * @param packetData The packet data to read
     * @throws DaedalusVpnService.VpnNetworkException If some network error occurred
     */
    @Override
    protected void handleDnsRequest(byte[] packetData) throws DaedalusVpnService.VpnNetworkException {
        IpPacket parsedPacket;
        try {
            parsedPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            Logger.debug("handleDnsRequest: Discarding invalid IP packet");
            Logger.logException(e);
            return;
        }

        if (!(parsedPacket.getPayload() instanceof UdpPacket)) {
            try {
                Logger.debug("handleDnsRequest: Discarding unknown packet type " + parsedPacket.getPayload());
            } catch (Exception ignored) {
            }
            return;
        }

        InetAddress destAddr = parsedPacket.getHeader().getDstAddr();
        if (destAddr == null) {
            return;
        }
        AbstractDnsServer dnsServer;
        try {
            dnsServer = service.dnsServers.get(destAddr.getHostAddress());
            destAddr = InetAddress.getByName(dnsServer.getHostAddress());
        } catch (Exception e) {
            Logger.logException(e);
            Logger.error("handleDnsRequest: DNS server alias query failed for " + destAddr.getHostAddress());
            return;
        }

        UdpPacket parsedUdp = (UdpPacket) parsedPacket.getPayload();

        if (parsedUdp.getPayload() == null) {
            Logger.debug("handleDnsRequest: Sending UDP packet without payload: " + parsedUdp);

            // Let's be nice to Firefox. Firefox uses an empty UDP packet to
            // the gateway to reduce the RTT. For further details, please see
            // https://bugzilla.mozilla.org/show_bug.cgi?id=888268
            DatagramPacket outPacket = new DatagramPacket(new byte[0], 0, 0, destAddr, dnsServer.getPort());
            forwardPacket(outPacket, null, dnsServer);
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
            Logger.debug("handleDnsRequest: Discarding non-DNS or invalid packet");
            Logger.logException(e);
            return;
        }
        if (dnsMsg.getQuestion() == null) {
            Logger.debug("handleDnsRequest: Discarding DNS packet with no query " + dnsMsg);
            return;
        }

        if (!resolve(parsedPacket, dnsMsg)) {
            DatagramPacket outPacket = new DatagramPacket(dnsRawData, 0, dnsRawData.length, destAddr, dnsServer.getPort());
            forwardPacket(outPacket, parsedPacket, dnsServer);
        }
    }
}
