package org.itxtech.daedalus.provider;

import android.os.ParcelFileDescriptor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsname.DnsName;
import org.minidns.record.*;
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
public class HttpsJsonProvider extends HttpsProvider {
    /*
     * Implemented https://developers.cloudflare.com/1.1.1.1/dns-over-https/json-format/
     *             https://developers.google.com/speed/public-dns/docs/dns-over-https
     */

    private OkHttpClient HTTP_CLIENT = getHttpClient("application/dns-json");

    public HttpsJsonProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
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
                                .addQueryParameter("name", message.getQuestion().name.toString())
                                .addQueryParameter("type", message.getQuestion().type.name())
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
                                JsonObject jsonObject = new JsonParser().parse(response.body().string()).getAsJsonObject();
                                DnsMessage.Builder msg = message.asBuilder()
                                        .setRecursionDesired(jsonObject.get("RD").getAsBoolean())
                                        .setRecursionAvailable(jsonObject.get("RA").getAsBoolean())
                                        .setAuthenticData(jsonObject.get("AD").getAsBoolean())
                                        .setCheckingDisabled(jsonObject.get("CD").getAsBoolean());
                                if (jsonObject.has("Answer")) {
                                    JsonArray answers = jsonObject.get("Answer").getAsJsonArray();
                                    for (JsonElement answer : answers) {
                                        JsonObject ans = answer.getAsJsonObject();
                                        Record.TYPE type = Record.TYPE.getType(ans.get("type").getAsInt());
                                        String data = ans.get("data").getAsString();
                                        Data recordData = null;
                                        switch (type) {
                                            case A:
                                                recordData = new A(data);
                                                break;
                                            case AAAA:
                                                recordData = new AAAA(data);
                                                break;
                                            case CNAME:
                                                recordData = new CNAME(data);
                                                break;
                                            case MX:
                                                recordData = new MX(5, data);
                                                break;
                                            case SOA:
                                                String[] sections = data.split(" ");
                                                if (sections.length == 7) {
                                                    recordData = new SOA(sections[0], sections[1],
                                                            Long.valueOf(sections[2]), Integer.valueOf(sections[3]),
                                                            Integer.valueOf(sections[4]), Integer.valueOf(sections[5]),
                                                            Long.valueOf(sections[6]));
                                                }
                                                break;
                                            case DNAME:
                                                recordData = new DNAME(data);
                                                break;
                                            case NS:
                                                recordData = new NS(DnsName.from(data));
                                                break;
                                            case TXT:
                                                recordData = new TXT(data.getBytes());
                                                break;

                                        }
                                        if (recordData != null) {
                                            msg.addAnswer(new Record<>(ans.get("name").getAsString(),
                                                    type, 1,
                                                    ans.get("TTL").getAsLong(),
                                                    recordData));
                                        }
                                    }
                                }
                                result = msg.setQrFlag(true).build().toArray();
                                completed = true;
                            } catch (Exception ignored) {//throw com.google.gson.JsonSyntaxException when response is not correct
                            }
                        }
                    }
                });
            }
        });
    }
}
