package org.itxtech.daedalus.provider;

import android.net.Network;
import android.util.Base64;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.SocksProxy;
import org.itxtech.daedalus.util.TlsCertificates;
import org.minidns.dnsmessage.DnsMessage;
import org.minidns.dnsmessage.Question;
import org.minidns.dnsname.DnsName;
import org.minidns.record.A;
import org.minidns.record.AAAA;
import org.minidns.record.CNAME;
import org.minidns.record.DNAME;
import org.minidns.record.Data;
import org.minidns.record.MX;
import org.minidns.record.NS;
import org.minidns.record.Record;
import org.minidns.record.SOA;
import org.minidns.record.TXT;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

/**
 * Sends one DNS query to one server, synchronously, with the protocol implied by the
 * server: DoH for addresses with a path, DoT for port 853, otherwise plain DNS over UDP
 * or TCP (TCP when the server goes through the SOCKS5 proxy). A custom server can
 * override the plain-DNS part with {@link AbstractDnsServer#getTransport()}. Shared by
 * the VPN provider and the DNS test.
 */
public class DnsTransport {
    /**
     * Callbacks of the caller: protecting sockets from the VPN, tracking the blocking
     * object so that the query can be aborted, and whether server host names may still be
     * resolved through the system resolver (not inside the VPN, where that would loop
     * back into the provider).
     */
    public interface Hooks {
        default void track(Closeable closeable) throws IOException {
        }

        default void protect(Socket socket) {
        }

        default void protect(DatagramSocket socket) {
        }

        default boolean allowResolve() {
            return true;
        }

        /**
         * The network to send through, or null for the system default network.
         */
        default Network getNetwork() {
            return null;
        }

        /**
         * Pool that keeps TCP and TLS connections open between queries, or null to open
         * a connection per query.
         */
        default DnsConnectionPool getPool() {
            return null;
        }
    }

    public static final Hooks NO_HOOKS = new Hooks() {
    };

    /**
     * A response: the bytes as received from the server (forwarded unchanged by the VPN)
     * and the parsed message.
     */
    public static class Result {
        public final byte[] raw;
        public final DnsMessage message;

        Result(byte[] raw, DnsMessage message) {
            this.raw = raw;
            this.message = message;
        }
    }

    private static final int UDP_BUFFER_SIZE = 4096;

    private static final Map<String, OkHttpClient> httpClients = new HashMap<>();

    /**
     * The protocol used for a server, one of the ProviderPicker.DNS_QUERY_METHOD constants.
     */
    public static int getMethod(AbstractDnsServer server) {
        if (server.isHttpsServer()) {
            return server.getAddress().contains("resolve")
                    ? ProviderPicker.DNS_QUERY_METHOD_HTTPS_JSON : ProviderPicker.DNS_QUERY_METHOD_HTTPS_IETF;
        }
        // A transport the user forced wins over the automatic choice, including port 853;
        // a SOCKS5 proxy still carries TCP only, so UDP goes over TCP as well
        if (server.getTransport() == AbstractDnsServer.TRANSPORT_TCP
                || (server.getTransport() == AbstractDnsServer.TRANSPORT_UDP && server.isProxied())) {
            return ProviderPicker.DNS_QUERY_METHOD_TCP;
        }
        if (server.getTransport() == AbstractDnsServer.TRANSPORT_UDP) {
            return ProviderPicker.DNS_QUERY_METHOD_UDP;
        }
        if (server.getPort() == AbstractDnsServer.DNS_SERVER_TLS_PORT) {
            return ProviderPicker.DNS_QUERY_METHOD_TLS;
        }
        if (server.isProxied() || ProviderPicker.getDnsQueryMethod() == ProviderPicker.DNS_QUERY_METHOD_TCP) {
            // A SOCKS5 proxy only carries TCP
            return ProviderPicker.DNS_QUERY_METHOD_TCP;
        }
        return ProviderPicker.DNS_QUERY_METHOD_UDP;
    }

    /**
     * String resource naming the protocol used for the server (UDP, TCP, TLS, HTTPS…).
     */
    public static int getMethodName(AbstractDnsServer server) {
        switch (getMethod(server)) {
            case ProviderPicker.DNS_QUERY_METHOD_TCP:
                return R.string.settings_dns_tcp;
            case ProviderPicker.DNS_QUERY_METHOD_TLS:
                return R.string.settings_dns_tls;
            case ProviderPicker.DNS_QUERY_METHOD_HTTPS_IETF:
                return R.string.settings_dns_https_ietf;
            case ProviderPicker.DNS_QUERY_METHOD_HTTPS_JSON:
                return R.string.settings_dns_https_json;
            default:
                return R.string.settings_dns_udp;
        }
    }

    /**
     * "address:port (protocol)" plus the proxy marker, for logs and the query log.
     */
    public static String describe(AbstractDnsServer server) {
        return server.getRealName() + " (" + Daedalus.getInstance().getString(getMethodName(server))
                + (server.isProxied() ? ", SOCKS5)" : ")");
    }

    public static Result query(AbstractDnsServer server, DnsMessage message, int timeout, Hooks hooks)
            throws IOException, GeneralSecurityException {
        switch (getMethod(server)) {
            case ProviderPicker.DNS_QUERY_METHOD_HTTPS_IETF:
                return queryDoh(server, message, timeout, hooks);
            case ProviderPicker.DNS_QUERY_METHOD_HTTPS_JSON:
                return queryDohJson(server, message, timeout, hooks);
            case ProviderPicker.DNS_QUERY_METHOD_TLS:
                return queryTls(server, message, timeout, hooks);
            case ProviderPicker.DNS_QUERY_METHOD_TCP:
                return queryTcp(server, message, timeout, hooks);
            default:
                return queryUdp(server, message, timeout, hooks);
        }
    }

    /**
     * The server's IP: the address resolved by the VPN service on the underlying network
     * when available, otherwise the configured address (a literal, or a host name looked
     * up through the system resolver when the hooks allow it).
     */
    private static InetAddress resolve(AbstractDnsServer server, Hooks hooks) throws UnknownHostException {
        if (server.getHostAddress() != null) {
            return InetAddress.getByName(server.getHostAddress());
        }
        String address = server.getAddress();
        if (!hooks.allowResolve() && !isLiteral(address)) {
            throw new UnknownHostException(address + " has not been resolved");
        }
        return InetAddress.getByName(address);
    }

    public static boolean isLiteral(String text) {
        return text.matches("\\d{1,3}(\\.\\d{1,3}){3}") || (text.contains(":") && text.matches("[0-9a-fA-F:.]+"));
    }

    private static InetSocketAddress target(AbstractDnsServer server, Hooks hooks) throws UnknownHostException {
        if (server.isProxied()) {
            // The proxy resolves host names; IP literals pass through unchanged
            return InetSocketAddress.createUnresolved(server.getAddress(), server.getPort());
        }
        return new InetSocketAddress(resolve(server, hooks), server.getPort());
    }

    private static Result queryUdp(AbstractDnsServer server, DnsMessage message, int timeout, Hooks hooks) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            hooks.track(socket);
            hooks.protect(socket);
            socket.setSoTimeout(timeout);
            byte[] query = message.toArray();
            socket.send(new DatagramPacket(query, query.length, resolve(server, hooks), server.getPort()));
            DatagramPacket packet = new DatagramPacket(new byte[UDP_BUFFER_SIZE], UDP_BUFFER_SIZE);
            socket.receive(packet);
            return checked(message, Arrays.copyOf(packet.getData(), packet.getLength()));
        }
    }

    /**
     * A connected TCP socket to the server, directly or through the SOCKS5 proxy, with the
     * connect and read timeout applied.
     */
    static Socket connect(AbstractDnsServer server, int timeout, Hooks hooks) throws IOException {
        Proxy proxy = SocksProxy.getProxy(server);
        // SocketChannel creates the file descriptor right away, so the socket can be
        // protected from the VPN before it connects; a proxied socket has no descriptor
        // until it is connected, and the proxy (usually localhost) is not routed into the VPN.
        Socket socket = proxy == null ? SocketChannel.open().socket() : new Socket(proxy);
        try {
            hooks.track(socket);
            if (proxy == null) {
                hooks.protect(socket);
            }
            socket.connect(target(server, hooks), timeout);
            if (proxy != null) {
                hooks.protect(socket);
            }
            socket.setSoTimeout(timeout);
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    private static Result queryTcp(AbstractDnsServer server, DnsMessage message, int timeout, Hooks hooks) throws IOException, GeneralSecurityException {
        DnsConnectionPool pool = hooks.getPool();
        if (pool != null) {
            return pool.query(server, message, timeout, hooks, false);
        }
        try (Socket socket = connect(server, timeout, hooks)) {
            return exchange(message, socket);
        }
    }

    /**
     * TLS on top of a connected TCP socket, trusting the system CAs plus the certificate
     * attached to the server. The configured server name enables SNI for servers configured
     * by host name, and for those the certificate must also match the host name, unless
     * the user attached a certificate of their own. The handshake is completed before
     * returning.
     */
    static SSLSocket wrapTls(AbstractDnsServer server, Socket tcpSocket) throws IOException, GeneralSecurityException {
        String host = server.getAddress();
        SSLSocket tlsSocket = (SSLSocket) TlsCertificates.getSocketFactory(server)
                .createSocket(tcpSocket, host, server.getPort(), true);
        try {
            tlsSocket.startHandshake();
            if (!isLiteral(host) && server.getCertificate() == null
                    && !HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tlsSocket.getSession())) {
                throw new SSLPeerUnverifiedException("Certificate does not match the host name " + host);
            }
        } catch (IOException e) {
            tlsSocket.close();
            throw e;
        }
        return tlsSocket;
    }

    /**
     * DNS over TLS (RFC 7858), over a pooled connection when the caller has a pool.
     */
    private static Result queryTls(AbstractDnsServer server, DnsMessage message, int timeout, Hooks hooks)
            throws IOException, GeneralSecurityException {
        DnsConnectionPool pool = hooks.getPool();
        if (pool != null) {
            return pool.query(server, message, timeout, hooks, true);
        }
        try (Socket tcpSocket = connect(server, timeout, hooks)) {
            try (SSLSocket tlsSocket = wrapTls(server, tcpSocket)) {
                return exchange(message, tlsSocket);
            }
        }
    }

    /**
     * Sends the query in TCP wire format (2-byte length prefix) and reads one response.
     */
    static Result exchange(DnsMessage message, Socket socket) throws IOException {
        byte[] query = message.toArray();
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        dos.writeShort(query.length);
        dos.write(query);
        dos.flush();

        DataInputStream dis = new DataInputStream(socket.getInputStream());
        byte[] data = new byte[dis.readUnsignedShort()];
        dis.readFully(data);
        return checked(message, data);
    }

    private static Result checked(DnsMessage query, byte[] raw) throws IOException {
        DnsMessage response = new DnsMessage(raw);
        if (response.id != query.id) {
            throw new IOException("DNS ID mismatch");
        }
        return new Result(raw, response);
    }

    private static OkHttpClient getHttpClient(AbstractDnsServer server, int timeout, Hooks hooks) {
        Proxy proxy = SocksProxy.getProxy(server);
        final boolean allowResolve = hooks.allowResolve();
        Network network = hooks.getNetwork();
        String key = (proxy == null ? "direct" : proxy.toString()) + "/" + timeout + "/" + allowResolve
                + "/" + (network == null ? "default" : network.toString());
        synchronized (httpClients) {
            OkHttpClient client = httpClients.get(key);
            if (client == null) {
                if (httpClients.size() >= 8) {
                    // Clients of networks that went away are of no use any more
                    httpClients.clear();
                }
                OkHttpClient.Builder builder = new OkHttpClient.Builder()
                        .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                        .readTimeout(timeout, TimeUnit.MILLISECONDS)
                        .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                        .proxy(proxy)
                        .dns(hostname -> {
                            // Host names of DoH servers are resolved by the VPN service on the
                            // underlying network; inside the VPN nothing else may be looked up
                            List<InetAddress> cached = DnsServerHelper.domainCache.get(hostname);
                            if (cached != null) {
                                return cached;
                            }
                            if (!allowResolve && !isLiteral(hostname)) {
                                throw new UnknownHostException(hostname + " has not been resolved");
                            }
                            return Arrays.asList(InetAddress.getAllByName(hostname));
                        });
                if (network != null && proxy == null) {
                    // Sockets of this client leave through the selected network, not the default one
                    builder.socketFactory(network.getSocketFactory());
                }
                client = builder.build();
                httpClients.put(key, client);
            }
            return client;
        }
    }

    private static okhttp3.Response execute(AbstractDnsServer server, Request request, int timeout, Hooks hooks) throws IOException {
        Call call = getHttpClient(server, timeout, hooks).newCall(request);
        hooks.track(call::cancel);
        okhttp3.Response response = call.execute();
        if (!response.isSuccessful()) {
            response.close();
            throw new IOException("HTTP " + response.code());
        }
        return response;
    }

    private static HttpUrl.Builder url(AbstractDnsServer server) throws IOException {
        HttpUrl url = HttpUrl.parse("https://" + server.getAddress());
        if (url == null) {
            throw new IOException("Invalid server address: " + server.getAddress());
        }
        return url.newBuilder();
    }

    /**
     * DNS over HTTPS (RFC 8484): GET with the base64url encoded wire-format message. ID 0
     * is used on the wire and the original ID is patched back into the response bytes.
     */
    private static Result queryDoh(AbstractDnsServer server, DnsMessage message, int timeout, Hooks hooks) throws IOException {
        byte[] raw = message.asBuilder().setId(0).build().toArray();
        String b64 = Base64.encodeToString(raw, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        Request request = new Request.Builder()
                .url(url(server).addQueryParameter("dns", b64).build())
                .header("Accept", "application/dns-message")
                .get()
                .build();
        try (okhttp3.Response response = execute(server, request, timeout, hooks)) {
            byte[] body = response.body().bytes();
            if (body.length < 12) {
                throw new IOException("Truncated DoH response");
            }
            body[0] = (byte) (message.id >> 8);
            body[1] = (byte) message.id;
            return checked(message, body);
        }
    }

    /**
     * DNS over HTTPS in the Google/Cloudflare JSON API format.
     */
    private static Result queryDohJson(AbstractDnsServer server, DnsMessage message, int timeout, Hooks hooks) throws IOException {
        Question question = message.getQuestion();
        Request request = new Request.Builder()
                .url(url(server)
                        .addQueryParameter("name", question.name.toString())
                        .addQueryParameter("type", question.type.name())
                        .build())
                .header("Accept", "application/dns-json")
                .get()
                .build();
        try (okhttp3.Response response = execute(server, request, timeout, hooks)) {
            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
            DnsMessage.Builder builder = message.asBuilder()
                    .setRecursionDesired(getBoolean(json, "RD"))
                    .setRecursionAvailable(getBoolean(json, "RA"))
                    .setAuthenticData(getBoolean(json, "AD"))
                    .setCheckingDisabled(getBoolean(json, "CD"));
            if (json.has("Status")) {
                builder.setResponseCode(DnsMessage.RESPONSE_CODE.getResponseCode(json.get("Status").getAsInt()));
            }
            if (json.has("Answer")) {
                JsonArray answers = json.getAsJsonArray("Answer");
                for (JsonElement answer : answers) {
                    JsonObject record = answer.getAsJsonObject();
                    Record.TYPE type = Record.TYPE.getType(record.get("type").getAsInt());
                    String data = record.get("data").getAsString();
                    Data payload = null;
                    switch (type) {
                        case A:
                            payload = new A(data);
                            break;
                        case AAAA:
                            payload = new AAAA(data);
                            break;
                        case CNAME:
                            payload = new CNAME(data);
                            break;
                        case DNAME:
                            payload = new DNAME(data);
                            break;
                        case MX:
                            payload = new MX(5, data);
                            break;
                        case NS:
                            payload = new NS(DnsName.from(data));
                            break;
                        case TXT:
                            payload = new TXT(data.getBytes());
                            break;
                        case SOA:
                            String[] sections = data.split(" ");
                            if (sections.length == 7) {
                                payload = new SOA(sections[0], sections[1],
                                        Long.parseLong(sections[2]), Integer.parseInt(sections[3]),
                                        Integer.parseInt(sections[4]), Integer.parseInt(sections[5]),
                                        Long.parseLong(sections[6]));
                            }
                            break;
                        default:
                            break;
                    }
                    if (payload != null) {
                        long ttl = record.has("TTL") ? record.get("TTL").getAsLong() : 0;
                        builder.addAnswer(new Record<>(record.get("name").getAsString(), type, 1, ttl, payload));
                    }
                }
            }
            DnsMessage result = builder.setQrFlag(true).build();
            return new Result(result.toArray(), result);
        }
    }

    private static boolean getBoolean(JsonObject json, String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }
}
