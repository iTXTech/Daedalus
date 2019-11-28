package org.itxtech.daedalus.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.server.DnsServerHelper;
import org.itxtech.daedalus.util.Logger;
import org.itxtech.daedalus.util.RuleResolver;
import org.minidns.dnsmessage.DnsMessage;

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
    private NioEventLoopGroup group;
    private Channel channel;

    @Override
    public void onCreate() {
        super.onCreate();
        if (ServiceHolder.isForeground() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(
                    new NotificationChannel(ServiceHolder.CHANNEL_ID, ServiceHolder.CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH));
            ServiceHolder.buildNotification(this);
            startForeground(ServiceHolder.NOTIFICATION_ACTIVATED, ServiceHolder.getBuilder().build());
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
                    .handler(new ChannelInitializer<DatagramChannel>() {
                        @Override
                        protected void initChannel(DatagramChannel ch) throws Exception {
                            ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                                    ByteBuf buf = msg.content();
                                    byte[] req = new byte[buf.readableBytes()];
                                    buf.readBytes(req);
                                    DnsMessage message = new DnsMessage(req);
                                    Logger.info(message.toString());
                                }
                            });
                        }
                    });
            ChannelFuture f = bootstrap.bind(ServiceHolder.serverAddr).sync();
            Logger.debug("Daedalus Server is listening on " + ServiceHolder.serverAddr.getHostString() + ":" + ServiceHolder.serverAddr.getPort());
            channel = f.channel();
            f.channel().closeFuture().await();
        } catch (Exception e) {
            Logger.logException(e);
        } finally {
            shutdown();
        }
    }

    private void shutdown() {
        group.shutdownGracefully();
    }
}
