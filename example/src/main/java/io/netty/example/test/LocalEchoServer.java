package io.netty.example.test;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * @author AntzUhl
 * @Date 2020/12/2 20:39
 * @Description
 */
public class LocalEchoServer {

    private static final Integer PORT = 8888;

    public static void main(String[] args) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workGroup)
                .channel(NioServerSocketChannel.class) // 反射的方式调用NioServerSocketChannel的构造函数
                .childOption(ChannelOption.TCP_NODELAY, true)
                .handler(new LocalHandler())
                .childHandler(new ChannelInitializer<ServerChannel>() {
                    @Override
                    protected void initChannel(ServerChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();
                        // TODO
                    }
                });
        ChannelFuture f = b.bind(PORT).sync();
        f.channel().closeFuture().sync();
    }
}
