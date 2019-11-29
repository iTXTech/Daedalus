package org.itxtech.daedalus.serverprovider;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import org.itxtech.daedalus.service.ServiceHolder;
import org.itxtech.daedalus.util.Logger;
import org.minidns.dnsmessage.DnsMessage;

import java.net.InetSocketAddress;

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
public class UdpProvider extends Provider {

    public UdpProvider(Channel channel) {
        super(channel);
    }

    @Override
    public void query(DnsMessage message, InetSocketAddress receiver) {
        Bootstrap bootstrap = new Bootstrap()
                .group(channel.eventLoop())
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) throws Exception {
                        ch.pipeline().addLast(new IdleStateHandler(1, 0, 0))
                                .addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        if (evt instanceof IdleStateEvent) {
                                            IdleStateEvent e = (IdleStateEvent) evt;
                                            if (e.isFirst()) {
                                                ctx.writeAndFlush(new DatagramPacket(
                                                        Unpooled.copiedBuffer(message.toArray()),
                                                        ServiceHolder.secondaryServer.getInetSocketAddress()));
                                            } else {
                                                ctx.channel().close();
                                            }
                                        }
                                    }

                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) throws Exception {
                                        sendResponse(msg.content(), receiver);
                                        ctx.channel().close();
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                        Logger.logException(cause);
                                        ctx.close();
                                    }
                                });
                    }
                })
                .option(ChannelOption.SO_BROADCAST, true);
        ChannelFuture future = bootstrap.bind(0);
        future.addListener(f -> future.channel().writeAndFlush(new DatagramPacket(
                Unpooled.copiedBuffer(message.toArray()),
                ServiceHolder.primaryServer.getInetSocketAddress())));
    }
}
