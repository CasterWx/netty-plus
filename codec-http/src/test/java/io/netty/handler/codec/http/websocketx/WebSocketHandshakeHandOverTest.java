/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec.http.websocketx;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler.ClientHandshakeStateEvent;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler.ServerHandshakeStateEvent;
import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.List;

import static org.junit.Assert.*;

public class WebSocketHandshakeHandOverTest {

    private boolean serverReceivedHandshake;
    private WebSocketServerProtocolHandler.HandshakeComplete serverHandshakeComplete;
    private boolean clientReceivedHandshake;
    private boolean clientReceivedMessage;
    private boolean serverReceivedCloseHandshake;
    private boolean clientForceClosed;
    private boolean clientHandshakeTimeout;

    private final class CloseNoOpServerProtocolHandler extends WebSocketServerProtocolHandler {
        CloseNoOpServerProtocolHandler(String websocketPath) {
            super(websocketPath, null, false);
        }

        @Override
        protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
            if (frame instanceof CloseWebSocketFrame) {
                serverReceivedCloseHandshake = true;
                return;
            }
            super.decode(ctx, frame, out);
        }
    }

    @Before
    public void setUp() {
        serverReceivedHandshake = false;
        serverHandshakeComplete = null;
        clientReceivedHandshake = false;
        clientReceivedMessage = false;
        serverReceivedCloseHandshake = false;
        clientForceClosed = false;
        clientHandshakeTimeout = false;
    }

    @Test
    public void testHandover() throws Exception {
        EmbeddedChannel serverChannel = createServerChannel(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    serverReceivedHandshake = true;
                    // immediately send a message to the client on connect
                    ctx.writeAndFlush(new TextWebSocketFrame("abc"));
                } else if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                    serverHandshakeComplete = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
                }
            }
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            }
        });

        EmbeddedChannel clientChannel = createClientChannel(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    clientReceivedHandshake = true;
                }
            }
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof TextWebSocketFrame) {
                    clientReceivedMessage = true;
                }
            }
        });

        // Transfer the handshake from the client to the server
        transferAllDataWithMerge(clientChannel, serverChannel);
        assertTrue(serverReceivedHandshake);
        assertNotNull(serverHandshakeComplete);
        assertEquals("/test", serverHandshakeComplete.requestUri());
        assertEquals(8, serverHandshakeComplete.requestHeaders().size());
        assertEquals("test-proto-2", serverHandshakeComplete.selectedSubprotocol());

        // Transfer the handshake response and the websocket message to the client
        transferAllDataWithMerge(serverChannel, clientChannel);
        assertTrue(clientReceivedHandshake);
        assertTrue(clientReceivedMessage);
    }

    @Test(expected = WebSocketHandshakeException.class)
    public void testClientHandshakeTimeout() throws Exception {
        EmbeddedChannel serverChannel = createServerChannel(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == ServerHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    serverReceivedHandshake = true;
                    // immediately send a message to the client on connect
                    ctx.writeAndFlush(new TextWebSocketFrame("abc"));
                } else if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
                    serverHandshakeComplete = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
                }
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            }
        });

        EmbeddedChannel clientChannel = createClientChannel(new SimpleChannelInboundHandler<Object>() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    clientReceivedHandshake = true;
                } else if (evt == ClientHandshakeStateEvent.HANDSHAKE_TIMEOUT) {
                    clientHandshakeTimeout = true;
                }
            }

            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof TextWebSocketFrame) {
                    clientReceivedMessage = true;
                }
            }
        }, 100);
        // Client send the handshake request to server
        transferAllDataWithMerge(clientChannel, serverChannel);
        // Server do not send the response back
        // transferAllDataWithMerge(serverChannel, clientChannel);
        WebSocketClientProtocolHandshakeHandler handshakeHandler =
                (WebSocketClientProtocolHandshakeHandler) clientChannel
                        .pipeline().get(WebSocketClientProtocolHandshakeHandler.class.getName());

        while (!handshakeHandler.getHandshakeFuture().isDone()) {
            Thread.sleep(10);
            // We need to run all pending tasks as the handshake timeout is scheduled on the EventLoop.
            clientChannel.runScheduledPendingTasks();
        }
        assertTrue(clientHandshakeTimeout);
        assertFalse(clientReceivedHandshake);
        assertFalse(clientReceivedMessage);
        // Should throw WebSocketHandshakeException
        try {
            handshakeHandler.getHandshakeFuture().syncUninterruptibly();
        } finally {
            serverChannel.finishAndReleaseAll();
        }
    }

    @Test(timeout = 10000)
    public void testClientHandshakerForceClose() throws Exception {
        final WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                new URI("ws://localhost:1234/test"), WebSocketVersion.V13, null, true,
                EmptyHttpHeaders.INSTANCE, Integer.MAX_VALUE, true, false, 20);

        EmbeddedChannel serverChannel = createServerChannel(
                new CloseNoOpServerProtocolHandler("/test"),
                new SimpleChannelInboundHandler<Object>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
                    }
                });

        EmbeddedChannel clientChannel = createClientChannel(handshaker, new SimpleChannelInboundHandler<Object>() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                if (evt == ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                    ctx.channel().closeFuture().addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            clientForceClosed = true;
                        }
                    });
                    handshaker.close(ctx.channel(), new CloseWebSocketFrame());
                }
            }
            @Override
            protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
            }
        });

        // Transfer the handshake from the client to the server
        transferAllDataWithMerge(clientChannel, serverChannel);
        // Transfer the handshake from the server to client
        transferAllDataWithMerge(serverChannel, clientChannel);

        // Transfer closing handshake
        transferAllDataWithMerge(clientChannel, serverChannel);
        assertTrue(serverReceivedCloseHandshake);
        // Should not be closed yet as we disabled closing the connection on the server
        assertFalse(clientForceClosed);

        while (!clientForceClosed) {
            Thread.sleep(10);
            // We need to run all pending tasks as the force close timeout is scheduled on the EventLoop.
            clientChannel.runPendingTasks();
        }

        // clientForceClosed would be set to TRUE after any close,
        // so check here that force close timeout was actually fired
        assertTrue(handshaker.isForceCloseComplete());

        // Both should be empty
        assertFalse(serverChannel.finishAndReleaseAll());
        assertFalse(clientChannel.finishAndReleaseAll());
    }

    /**
     * Transfers all pending data from the source channel into the destination channel.<br>
     * Merges all data into a single buffer before transmission into the destination.
     * @param srcChannel The source channel
     * @param dstChannel The destination channel
     */
    private static void transferAllDataWithMerge(EmbeddedChannel srcChannel, EmbeddedChannel dstChannel)  {
        ByteBuf mergedBuffer = null;
        for (;;) {
            Object srcData = srcChannel.readOutbound();

            if (srcData != null) {
                assertTrue(srcData instanceof ByteBuf);
                ByteBuf srcBuf = (ByteBuf) srcData;
                try {
                    if (mergedBuffer == null) {
                        mergedBuffer = Unpooled.buffer();
                    }
                    mergedBuffer.writeBytes(srcBuf);
                } finally {
                    srcBuf.release();
                }
            } else {
                break;
            }
        }

        if (mergedBuffer != null) {
            dstChannel.writeInbound(mergedBuffer);
        }
    }

    private static EmbeddedChannel createClientChannel(ChannelHandler handler) throws Exception {
        return new EmbeddedChannel(
                new HttpClientCodec(),
                new HttpObjectAggregator(8192),
                new WebSocketClientProtocolHandler(new URI("ws://localhost:1234/test"),
                                                   WebSocketVersion.V13, "test-proto-2",
                                                   false, null, 65536),
                handler);
    }

    private static EmbeddedChannel createClientChannel(WebSocketClientHandshaker handshaker,
                                                       ChannelHandler handler) throws Exception {
        return new EmbeddedChannel(
                new HttpClientCodec(),
                new HttpObjectAggregator(8192),
                // Note that we're switching off close frames handling on purpose to test forced close on timeout.
                new WebSocketClientProtocolHandler(handshaker, false, false),
                handler);
    }

    private static EmbeddedChannel createServerChannel(ChannelHandler handler) {
        return new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpObjectAggregator(8192),
                new WebSocketServerProtocolHandler("/test", "test-proto-1, test-proto-2", false),
                handler);
    }

    private static EmbeddedChannel createServerChannel(WebSocketServerProtocolHandler webSocketHandler,
                                                       ChannelHandler handler) {
        return new EmbeddedChannel(
                new HttpServerCodec(),
                new HttpObjectAggregator(8192),
                webSocketHandler,
                handler);
    }

    private static EmbeddedChannel createClientChannel(ChannelHandler handler, long timeoutMillis) throws Exception {
        return new EmbeddedChannel(
                new HttpClientCodec(),
                new HttpObjectAggregator(8192),
                new WebSocketClientProtocolHandler(new URI("ws://localhost:1234/test"),
                                                   WebSocketVersion.V13, "test-proto-2",
                                                   false, null, 65536, timeoutMillis),
                handler);
    }
}
