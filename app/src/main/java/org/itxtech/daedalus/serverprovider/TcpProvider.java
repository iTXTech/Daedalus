package org.itxtech.daedalus.serverprovider;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
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
public class TcpProvider extends Provider {

    public TcpProvider(Channel channel) {
        super(channel);
    }

    @Override
    public void query(DnsMessage message, InetSocketAddress receiver) throws Exception {
        Bootstrap bootstrap = new Bootstrap()
                .group(channel.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 1000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(new IdleStateHandler(1, 1, 0))
                                .addLast(new SimpleChannelInboundHandler<Object>() {
                                    @Override
                                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                        if (evt instanceof IdleStateEvent) {
                                            IdleStateEvent e = (IdleStateEvent) evt;
                                            Logger.info("IDLE: " + e.toString());
                                            if (e.isFirst()) {
                                                ctx.connect(ServiceHolder.secondaryServer.getInetSocketAddress())
                                                        .addListener(future -> ctx.writeAndFlush(
                                                                Unpooled.copiedBuffer(message.toArray())));
                                            } else {
                                                ctx.channel().close();
                                            }
                                        }
                                    }

                                    @Override
                                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        Logger.info("MSG " + msg.toString());
                                        sendResponse((ByteBuf) msg, receiver);
                                        ctx.channel().close();
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                        Logger.logException(cause);
                                        ctx.close();
                                    }
                                });
                    }
                });
        ChannelFuture f = bootstrap.connect(ServiceHolder.primaryServer.getInetSocketAddress());
        f.addListener(future -> {
            if (future.isSuccess()) {
                Logger.info("GGG " + future.toString());
                f.channel().writeAndFlush(Unpooled.copiedBuffer(message.toArray()));
            } else {
                bootstrap.connect(ServiceHolder.secondaryServer.getInetSocketAddress()).addListener(fu -> {
                    if (fu.isSuccess()) {
                        f.channel().writeAndFlush(Unpooled.copiedBuffer(message.toArray()));
                    }
                });
            }
        });
    }
}
