package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import android.util.Base64;
import okhttp3.*;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.minidns.dnsmessage.DnsMessage;
import org.pcap4j.packet.IpPacket;

import java.io.IOException;

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
public class HttpsIetfProvider extends HttpsProvider {
    private OkHttpClient HTTP_CLIENT = getHttpClient("application/dns-message");


    public HttpsIetfProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    @Override
    protected void sendRequestToServer(IpPacket parsedPacket, DnsMessage message, String uri) {
        whqList.add(new WaitingHttpsRequest(parsedPacket) {
            @Override
            public void doRequest() {
                final int id = message.id;
                final byte[] rawRequest = message.toArray();
                Request request = new Request.Builder()
                        .url(HttpUrl.parse(HTTPS_SUFFIX + uri).newBuilder()
                                .addQueryParameter("dns", Base64.encodeToString(
                                        message.asBuilder().setId(0).build().toArray(),
                                        Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP))
                                .build())
                        .get()
                        .build();
                HTTP_CLIENT.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        result = rawRequest;
                        completed = true;
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        if (response.isSuccessful()) {
                            try {
                                result = new DnsMessage(response.body().bytes()).asBuilder()
                                        .setId(id).build().toArray();
                                completed = true;
                            } catch (Exception ignored) {//throw IllegalArgumentException when response is not correct
                            }
                        }
                    }
                });
            }
        });
    }
}
