package org.itxtech.daedalus.provider;

import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.util.Logger;
import org.minidns.dnsmessage.DnsMessage;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
 * Keeps DNS over TLS (RFC 7858) and DNS over TCP (RFC 7766) connections open between
 * queries, so that a query does not pay for a TCP and TLS handshake every time. A
 * connection carries one query at a time; a few idle connections per server are kept
 * for a while and a reused connection that the server has meanwhile closed is replaced
 * transparently.
 */
public class DnsConnectionPool {
    private static final int MAX_IDLE_PER_SERVER = 4;
    private static final long IDLE_TIMEOUT = 30_000;

    private static class Connection {
        final Socket socket;
        long lastUsed;

        Connection(Socket socket) {
            this.socket = socket;
            this.lastUsed = System.currentTimeMillis();
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private final Map<String, ArrayDeque<Connection>> idle = new HashMap<>();

    /**
     * Sends the query over a pooled connection to the server, opening one when none is
     * idle, and puts the connection back afterwards.
     */
    public DnsTransport.Result query(AbstractDnsServer server, DnsMessage message, int timeout,
                                     DnsTransport.Hooks hooks, boolean tls) throws IOException, GeneralSecurityException {
        String key = key(server, tls);
        Connection connection = acquire(key);
        boolean reused = connection != null;
        if (connection == null) {
            connection = open(server, timeout, hooks, tls);
        }
        try {
            return exchange(key, connection, message, timeout);
        } catch (IOException e) {
            if (!reused || e instanceof SocketTimeoutException) {
                throw e;
            }
            // The server had closed the idle connection (EOF, reset, broken pipe): try once
            // more on a fresh one. A timeout is not retried, the fallback server is faster.
            Logger.debug("Pooled connection to " + server.getRealName() + " was closed by the server, reconnecting");
            return exchange(key, open(server, timeout, hooks, tls), message, timeout);
        }
    }

    private DnsTransport.Result exchange(String key, Connection connection, DnsMessage message, int timeout) throws IOException {
        try {
            connection.socket.setSoTimeout(timeout);
            DnsTransport.Result result = DnsTransport.exchange(message, connection.socket);
            release(key, connection);
            return result;
        } catch (IOException e) {
            connection.close();
            throw e;
        }
    }

    private Connection open(AbstractDnsServer server, int timeout, DnsTransport.Hooks hooks, boolean tls)
            throws IOException, GeneralSecurityException {
        Socket socket = DnsTransport.connect(server, timeout, hooks);
        if (tls) {
            try {
                socket = DnsTransport.wrapTls(server, socket);
            } catch (IOException | GeneralSecurityException e) {
                socket.close();
                throw e;
            }
        }
        return new Connection(socket);
    }

    private static String key(AbstractDnsServer server, boolean tls) {
        return server.getAddress() + ":" + server.getPort()
                + (server.isProxied() ? "/socks5/" + server.getProxyHost() + ":" + server.getProxyPort() : "")
                + (tls ? "/tls/" + server.getCertificate() : "/tcp");
    }

    /**
     * The most recently used idle connection to the server, or null.
     */
    private synchronized Connection acquire(String key) {
        ArrayDeque<Connection> connections = idle.get(key);
        if (connections == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        while (!connections.isEmpty()) {
            Connection connection = connections.pollFirst();
            if (connection.socket.isClosed() || now - connection.lastUsed > IDLE_TIMEOUT) {
                connection.close();
                continue;
            }
            return connection;
        }
        return null;
    }

    private synchronized void release(String key, Connection connection) {
        if (connection.socket.isClosed()) {
            return;
        }
        ArrayDeque<Connection> connections = idle.get(key);
        if (connections == null) {
            connections = new ArrayDeque<>();
            idle.put(key, connections);
        }
        if (connections.size() >= MAX_IDLE_PER_SERVER) {
            connection.close();
            return;
        }
        connection.lastUsed = System.currentTimeMillis();
        connections.addFirst(connection);
    }

    /**
     * Closes idle connections that have not been used for a while. Called regularly by
     * the provider loop.
     */
    public synchronized void sweep() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, ArrayDeque<Connection>>> entries = idle.entrySet().iterator();
        while (entries.hasNext()) {
            ArrayDeque<Connection> connections = entries.next().getValue();
            Iterator<Connection> iterator = connections.iterator();
            while (iterator.hasNext()) {
                Connection connection = iterator.next();
                if (connection.socket.isClosed() || now - connection.lastUsed > IDLE_TIMEOUT) {
                    connection.close();
                    iterator.remove();
                }
            }
            if (connections.isEmpty()) {
                entries.remove();
            }
        }
    }

    /**
     * Closes every idle connection, e.g. when the upstream servers or the network change.
     */
    public synchronized void closeAll() {
        for (ArrayDeque<Connection> connections : idle.values()) {
            for (Connection connection : connections) {
                connection.close();
            }
        }
        idle.clear();
    }
}
