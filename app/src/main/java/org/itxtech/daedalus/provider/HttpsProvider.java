package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;
import androidx.annotation.NonNull;
import okhttp3.OkHttpClient;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.minidns.dnsmessage.DnsMessage;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.UdpPacket;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

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
    public static final String HTTPS_SUFFIX = "https://";

    protected final WospList dnsIn = new WospList(false);

    HttpsProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    OkHttpClient getHttpClient(String accept) {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .addInterceptor((chain) -> chain.proceed(chain.request().newBuilder()
                        .header("Accept", accept)
                        .build()))
                .dns(hostname -> {
                    if (DnsServerHelper.domainCache.containsKey(hostname)) {
                        return DnsServerHelper.domainCache.get(hostname);
                    }
                    return Arrays.asList(InetAddress.getAllByName(hostname));
                })
                .build();
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
            uri = service.dnsServers.get(destAddr.getHostAddress()).getAddress();//https uri
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
            Logger.debug("handleDnsRequest: Discarding DNS packet with no query " + dnsMsg);
            return;
        }

        if (!resolve(parsedPacket, dnsMsg) && uri != null) {
            sendRequestToServer(parsedPacket, dnsMsg, uri);
            //SHOULD use a DNS ID of 0 in every DNS request (according to draft-ietf-doh-dns-over-https-11)
        }
    }

    protected abstract void sendRequestToServer(IpPacket parsedPacket, DnsMessage message, String uri);
    //uri example: 1.1.1.1:1234/dnsQuery. The specified provider will add https:// and parameters
}
