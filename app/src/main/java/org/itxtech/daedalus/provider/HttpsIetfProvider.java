package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import android.util.Base64;
import okhttp3.*;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.minidns.dnsmessage.DnsMessage;
import org.pcap4j.packet.IpPacket;

import java.io.IOException;
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
public class HttpsIetfProvider extends HttpsProvider {
    // implemented https://tools.ietf.org/html/draft-ietf-doh-dns-over-https-11

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .addInterceptor((chain) -> {
                Request original = chain.request();

                Request request = original.newBuilder()
                        .header("Accept", "application/dns-message")
                        .build();

                return chain.proceed(request);
            })
            .build();

    public HttpsIetfProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    @Override
    protected void sendRequestToServer(IpPacket parsedPacket, DnsMessage message, String uri) {
        whqList.add(new WaitingHttpsRequest(parsedPacket) {
            @Override
            public void doRequest() {
                final byte[] rawRequest = message.toArray();
                Request request = new Request.Builder()
                        .url(HttpUrl.parse(HTTPS_SUFFIX + uri).newBuilder()
                                .addQueryParameter("dns", Base64.encodeToString(rawRequest, Base64.DEFAULT))
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
                    public void onResponse(Call call, Response response) throws IOException {
                        if (response.isSuccessful()) {
                            result = response.body().bytes();
                            completed = true;
                        }
                    }
                });
            }
        });
    }
}
