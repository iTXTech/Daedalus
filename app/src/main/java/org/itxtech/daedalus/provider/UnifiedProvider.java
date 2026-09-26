package org.itxtech.daedalus.provider;

import android.net.Network;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.server.AbstractDnsServer;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.QueryLog;
import org.minidns.dnsmessage.DnsMessage;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpSelector;
import org.pcap4j.packet.UdpPacket;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
 * The DNS forwarder of the advanced mode. Each query read from the VPN device is handled
 * on a worker thread: it is sent to the primary server with a short timeout and, when that
 * fails, to the secondary one, so that a dead server never makes the app wait for the
 * system resolver's timeout. The protocol of every server is chosen by
 * {@link DnsTransport} (DoT, DoH, UDP or TCP), and the servers can be swapped at any time
 * when the network rules select different ones.
 */
public class UnifiedProvider extends Provider {
    // Per-server timeout while another server is still to be tried
    public static final int SERVER_TIMEOUT = 2000;
    // Whole chain, primary and fallback together. The system resolver gives up after 5 s
    // and would count a timeout against the VPN DNS address, so always answer before that.
    public static final int TOTAL_TIMEOUT = 4500;
    private static final int MIN_TIMEOUT = 1000;
    // Idle wake-up of the device loop, for the connection pool sweep and the statistics
    private static final int POLL_TIMEOUT = 5000;

    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "DnsQuery");
        thread.setDaemon(true);
        // A failing query must never take the whole app down
        thread.setUncaughtExceptionHandler((t, e) -> Logger.logException(e));
        return thread;
    });
    private final Object writeLock = new Object();
    private FileOutputStream outputStream = null;
    private volatile AbstractDnsServer[] servers = new AbstractDnsServer[0];
    private volatile String selection = "";
    private volatile Network network = null;
    private final DnsConnectionPool pool = new DnsConnectionPool();

    private final DnsTransport.Hooks hooks = new DnsTransport.Hooks() {
        @Override
        public void protect(Socket socket) {
            service.protect(socket);
            // Send through the network the rules were matched against, not the system default,
            // which may still be mobile data for a moment after Wi-Fi connects
            Network current = network;
            if (current != null && !socket.isConnected()) {
                try {
                    current.bindSocket(socket);
                } catch (IOException e) {
                    Logger.warning("Cannot bind socket to " + current + ": " + e.getMessage());
                }
            }
        }

        @Override
        public void protect(DatagramSocket socket) {
            service.protect(socket);
            Network current = network;
            if (current != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    current.bindSocket(socket);
                } catch (IOException e) {
                    Logger.warning("Cannot bind socket to " + current + ": " + e.getMessage());
                }
            }
        }

        @Override
        public boolean allowResolve() {
            // A lookup through the system resolver would come straight back into this provider
            return false;
        }

        @Override
        public Network getNetwork() {
            return network;
        }

        @Override
        public DnsConnectionPool getPool() {
            return pool;
        }
    };

    public UnifiedProvider(ParcelFileDescriptor descriptor, DaedalusVpnService service) {
        super(descriptor, service);
    }

    /**
     * Sets the upstream servers: every query tries the primary first and falls back to the
     * secondary, whichever of the two VPN DNS addresses the system sent it to. The system
     * resolver picks the address by its own statistics, which must not override the order
     * chosen by the user. The selection names the rule that chose the servers, for the
     * query log; the network is the one the rule was matched against and the one the
     * queries are sent through.
     */
    public void setServers(AbstractDnsServer primary, AbstractDnsServer secondary, String selection, Network network) {
        ArrayList<AbstractDnsServer> list = new ArrayList<>(2);
        if (primary != null) {
            list.add(primary);
        }
        if (secondary != null && (primary == null || !sameServer(primary, secondary))) {
            list.add(secondary);
        }
        this.network = network;
        servers = list.toArray(new AbstractDnsServer[0]);
        this.selection = selection == null ? "" : selection;
        // Connections to the previous servers, possibly bound to a network that is gone
        pool.closeAll();
    }

    private static boolean sameServer(AbstractDnsServer a, AbstractDnsServer b) {
        return a.getAddress().equalsIgnoreCase(b.getAddress()) && a.getPort() == b.getPort() && a.isProxied() == b.isProxied();
    }

    @Override
    public void process() {
        try {
            FileDescriptor[] pipes = Os.pipe();
            mInterruptFd = pipes[0];
            mBlockFd = pipes[1];
            FileInputStream inputStream = new FileInputStream(descriptor.getFileDescriptor());
            outputStream = new FileOutputStream(descriptor.getFileDescriptor());

            byte[] packet = new byte[32767];
            while (running) {
                StructPollfd deviceFd = new StructPollfd();
                deviceFd.fd = inputStream.getFD();
                deviceFd.events = (short) OsConstants.POLLIN;
                StructPollfd blockFd = new StructPollfd();
                blockFd.fd = mBlockFd;
                blockFd.events = (short) (OsConstants.POLLHUP | OsConstants.POLLERR);

                Os.poll(new StructPollfd[]{deviceFd, blockFd}, POLL_TIMEOUT);
                if (blockFd.revents != 0) {
                    Logger.info("UnifiedProvider: Told to stop VPN");
                    running = false;
                    return;
                }
                if ((deviceFd.revents & OsConstants.POLLIN) != 0) {
                    readPacketFromDevice(inputStream, packet);
                }
                service.providerLoopCallback();
                pool.sweep();
            }
        } catch (Exception e) {
            Logger.logException(e);
        } finally {
            executor.shutdownNow();
            pool.closeAll();
        }
    }

    @Override
    protected void handleDnsRequest(byte[] packetData) {
        IpPacket parsedPacket;
        try {
            parsedPacket = (IpPacket) IpSelector.newPacket(packetData, 0, packetData.length);
        } catch (Exception e) {
            Logger.debug("handleDnsRequest: Discarding invalid IP packet");
            return;
        }
        if (!(parsedPacket.getPayload() instanceof UdpPacket)) {
            return;
        }

        InetAddress destAddr = parsedPacket.getHeader().getDstAddr();
        if (destAddr == null || !service.dnsAliases.contains(destAddr.getHostAddress())) {
            Logger.debug("handleDnsRequest: Discarding packet to unknown destination " + destAddr);
            return;
        }

        UdpPacket parsedUdp = (UdpPacket) parsedPacket.getPayload();
        if (parsedUdp.getPayload() == null) {
            // Empty UDP packets (Firefox uses them to warm up) need no answer
            return;
        }

        byte[] dnsRawData = parsedUdp.getPayload().getRawData();
        DnsMessage dnsMsg;
        try {
            dnsMsg = new DnsMessage(dnsRawData);
        } catch (IOException e) {
            Logger.debug("handleDnsRequest: Discarding non-DNS or invalid packet");
            return;
        }
        if (dnsMsg.getQuestion() == null) {
            Logger.debug("handleDnsRequest: Discarding DNS packet with no query " + dnsMsg);
            return;
        }
        if (Daedalus.getPrefs().getBoolean("settings_debug_output", false)) {
            Logger.debug("DnsRequest: " + dnsMsg);
        }

        if (resolve(parsedPacket, dnsMsg)) {
            return;
        }

        AbstractDnsServer[] chain = servers;
        if (chain.length == 0) {
            Logger.warning("handleDnsRequest: No upstream DNS server, dropping query for " + dnsMsg.getQuestion().name);
            return;
        }
        try {
            executor.execute(() -> forward(parsedPacket, dnsMsg, chain));
        } catch (Exception e) {
            Logger.logException(e);
        }
    }

    private void forward(IpPacket packet, DnsMessage message, AbstractDnsServer[] chain) {
        String question = message.getQuestion().name + " " + message.getQuestion().type.name();
        String selection = this.selection;
        long start = SystemClock.elapsedRealtime();
        StringBuilder failures = new StringBuilder();
        for (int i = 0; i < chain.length; i++) {
            AbstractDnsServer server = chain[i];
            // Servers with a fallback behind them get SERVER_TIMEOUT; the last one gets
            // whatever is left of the total budget (all of it when the others failed fast)
            long remaining = TOTAL_TIMEOUT - (SystemClock.elapsedRealtime() - start);
            int timeout = (int) Math.max(MIN_TIMEOUT, i == chain.length - 1 ? remaining : Math.min(SERVER_TIMEOUT, remaining));
            try {
                DnsTransport.Result result = DnsTransport.query(server, message, timeout, hooks);
                handleDnsResponse(packet, result.raw);
                QueryLog.add(question, selection, DnsTransport.describe(server), QueryLog.summarize(result.message),
                        SystemClock.elapsedRealtime() - start, failures.length() == 0 ? null : failures.toString());
                return;
            } catch (Exception e) {
                String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Logger.warning("Query for " + question + " to " + server.getRealName() + " failed: " + reason);
                if (failures.length() > 0) {
                    failures.append("; ");
                }
                failures.append(DnsTransport.describe(server)).append(": ").append(reason);
            }
        }
        // Every server failed: answer SERVFAIL so that the app does not sit in the resolver's timeout
        try {
            handleDnsResponse(packet, message.asBuilder()
                    .setQrFlag(true)
                    .setRecursionAvailable(true)
                    .setResponseCode(DnsMessage.RESPONSE_CODE.SERVER_FAIL)
                    .build().toArray());
        } catch (Exception e) {
            Logger.logException(e);
        }
        QueryLog.add(question, selection, "", DnsMessage.RESPONSE_CODE.SERVER_FAIL.name(),
                SystemClock.elapsedRealtime() - start, failures.toString());
    }

    /**
     * Responses are written to the device straight from the worker threads; every write is
     * one whole packet, serialized by the lock.
     */
    @Override
    protected void queueDeviceWrite(IpPacket ipOutPacket) {
        synchronized (writeLock) {
            if (outputStream == null) {
                return;
            }
            try {
                outputStream.write(ipOutPacket.getRawData());
                dnsQueryTimes++;
            } catch (IOException e) {
                Logger.warning("Cannot write DNS response to the VPN device: " + e);
            }
        }
    }
}
