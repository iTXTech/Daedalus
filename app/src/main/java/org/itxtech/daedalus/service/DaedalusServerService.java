package org.itxtech.daedalus.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.IBinder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.server.DnsServer;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.serverprovider.Provider;
import org.itxtech.daedalus.serverprovider.ProviderPicker;
import org.itxtech.daedalus.util.DnsServersDetector;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.RuleResolver;
import org.minidns.dnsmessage.DnsMessage;

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
public class DaedalusServerService extends Service implements Runnable {
    private Thread thread;
    private EventLoopGroup group;
    private Channel channel;
    private BroadcastReceiver receiver;
    private Provider provider;

    @Override
    public void onCreate() {
        super.onCreate();
        if (ServiceHolder.isForeground() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(
                    new NotificationChannel(ServiceHolder.CHANNEL_ID, ServiceHolder.CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH));
            ServiceHolder.buildNotification(this);
            startForeground(ServiceHolder.NOTIFICATION_ACTIVATED, ServiceHolder.getBuilder().build());
        }
        if (Daedalus.getPrefs().getBoolean("settings_use_system_dns", false)) {
            registerReceiver(receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    updateUpstreamServers(context);
                }
            }, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        }
    }

    private static void updateUpstreamServers(Context context) {
        String[] servers = DnsServersDetector.getServers(context);
        if (servers != null) {
            if (servers.length >= 2) {
                ServiceHolder.primaryServer.setAddress(servers[0]);
                ServiceHolder.primaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
                ServiceHolder.secondaryServer.setAddress(servers[1]);
                ServiceHolder.secondaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
            } else {
                ServiceHolder.primaryServer.setAddress(servers[0]);
                ServiceHolder.primaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
                ServiceHolder.secondaryServer.setAddress(servers[0]);
                ServiceHolder.secondaryServer.setPort(DnsServer.DNS_SERVER_DEFAULT_PORT);
            }
            Logger.info("Upstream DNS updated: " + ServiceHolder.primaryServer.getAddress() + " " + ServiceHolder.secondaryServer.getAddress());
        } else {
            Logger.error("Cannot obtain upstream DNS server!");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            switch (intent.getAction()) {
                case ServiceHolder.ACTION_ACTIVATE:
                    ServiceHolder.setRunning(true, this);
                    startThread();
                    return START_STICKY;
                case ServiceHolder.ACTION_DEACTIVATE:
                    stopThread();
                    return START_NOT_STICKY;
            }
        }
        return START_NOT_STICKY;
    }

    private void startThread() {
        if (this.thread == null) {
            this.thread = new Thread(this, "DaedalusServer");
            this.thread.start();
        }
    }

    private void stopThread() {
        ServiceHolder.setRunning(false, this);
        if (this.thread != null) {
            shutdown();
            this.thread.interrupt();
        }
        stopSelf();
        RuleResolver.clear();
        DnsServerHelper.clearCache();
        if (MainActivity.getInstance() != null) {
            MainActivity.getInstance().startActivity(new Intent(getApplicationContext(), MainActivity.class)
                    .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_SERVICE_DONE));
        } else {
            Daedalus.updateShortcut(getApplicationContext());
        }
    }

    @Override
    public void onDestroy() {
        stopThread();
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void run() {
        group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .option(ChannelOption.SO_RCVBUF, 1024 * 1024)
                    .option(ChannelOption.SO_SNDBUF, 1024 * 1024)
                    .handler(new SimpleChannelInboundHandler<DatagramPacket>() {
                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                            DnsMessage message = getDnsMessage(msg);
                            if (Daedalus.getPrefs().getBoolean("settings_debug_output", false)) {
                                Logger.debug("DnsRequest: " + message.toString());
                            }
                            DnsMessage result = provider.resolve(message);
                            if (result != null) {
                                ctx.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(message.toArray()), msg.sender()));
                            } else {
                                provider.query(message, msg.sender());
                            }
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                            Logger.logException(cause);
                            ctx.close();
                        }
                    });
            ChannelFuture f = bootstrap.bind(ServiceHolder.serverAddr).sync();
            Logger.debug("Daedalus Server is listening on " + ServiceHolder.serverAddr.getHostString() + ":" + ServiceHolder.serverAddr.getPort());
            provider = ProviderPicker.getProvider(f.channel());
            f.channel().closeFuture().await();
        } catch (Exception e) {
            Logger.logException(e);
        } finally {
            shutdown();
        }
    }

    private void shutdown() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }

    public static DnsMessage getDnsMessage(DatagramPacket p) throws IOException {
        ByteBuf buf = p.content();
        byte[] req = new byte[buf.readableBytes()];
        buf.readBytes(req);
        return new DnsMessage(req);
    }
}
