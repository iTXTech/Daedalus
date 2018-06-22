package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import android.system.ErrnoException;
import android.system.OsConstants;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.server.DNSServerHelper;
import org.pcap4j.packet.IpPacket;

import javax.net.ssl.SSLContext;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Socket;
import java.security.NoSuchAlgorithmException;

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
public class TlsProvider extends TcpProvider{
    public TlsProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    @Override
    protected void forwardPacket(DatagramPacket outPacket, IpPacket parsedPacket) throws DaedalusVpnService.VpnNetworkException {
        Socket dnsSocket;
        try {
            dnsSocket = SSLContext.getInstance("TLSv1.2").getSocketFactory()
                    .createSocket(outPacket.getAddress(),
                            DNSServerHelper.getPortOrDefault(outPacket.getAddress(), outPacket.getPort()));
            //Create TLS v1.2 socket

            service.protect(dnsSocket);

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
        } catch (NoSuchAlgorithmException e){
            Logger.logException(e);
        } catch (IOException e) {
            if (e.getCause() instanceof ErrnoException) {
                ErrnoException errnoExc = (ErrnoException) e.getCause();
                if ((errnoExc.errno == OsConstants.ENETUNREACH) || (errnoExc.errno == OsConstants.EPERM)) {
                    throw new DaedalusVpnService.VpnNetworkException("Cannot send message:", e);
                }
            }
        }
    }
}
