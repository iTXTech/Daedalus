package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.pcap4j.packet.IpPacket;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;

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

    public TcpProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    protected byte[] processUdpPacket(DatagramPacket outPacket, IpPacket parsedPacket) {
        if (parsedPacket == null) {
            return new byte[0];
        }
        return outPacket.getData();
    }

    @Override
    protected void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket, AbstractDnsServer dnsServer) throws DaedalusVpnService.VpnNetworkException {
        Socket dnsSocket;
        try {
            // Packets to be sent to the real DNS server will need to be protected from the VPN
            dnsSocket = SocketChannel.open().socket();

            service.protect(dnsSocket);

            SocketAddress address = new InetSocketAddress(outPacket.getAddress(), dnsServer.getPort());
            dnsSocket.connect(address, 5000);
            dnsSocket.setSoTimeout(5000);
            Logger.info("TcpProvider: Sending DNS query request");
            DataOutputStream dos = new DataOutputStream(dnsSocket.getOutputStream());
            byte[] packet = processUdpPacket(outPacket, parsedPacket);
            dos.writeShort(packet.length);
            dos.write(packet);
            dos.flush();

            if (parsedPacket != null) {
                dnsIn.add(new WaitingOnSocketPacket(dnsSocket, parsedPacket));
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

    @Override
    protected void handleRawDnsResponse(IpPacket parsedPacket, Object dnsSocket) {
        try {
            DataInputStream stream = new DataInputStream(((Socket) dnsSocket).getInputStream());
            int length = stream.readUnsignedShort();
            Log.d(TAG, "Reading length: " + length);
            byte[] data = new byte[length];
            stream.read(data);
            ((Socket) dnsSocket).close();
            handleDnsResponse(parsedPacket, data);
        } catch (Exception ignored) {

        }
    }
}
