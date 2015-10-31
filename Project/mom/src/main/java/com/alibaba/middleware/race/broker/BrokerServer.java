package com.alibaba.middleware.race.broker;

import com.alibaba.middleware.race.netty.MomDecoder;
import com.alibaba.middleware.race.netty.MomEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * Created by liuchun on 2015/8/5.
 */
public class BrokerServer {
    // listening port
    private int port;

    public BrokerServer(int port){
        this.port = port;
    }

    /**
     * 启动broker
     * @param args
     */
    public static void main(String[] args) {
        int port = 9999;
        BrokerServer server = new BrokerServer(port);

        MessageQueueCheckThread thread = new MessageQueueCheckThread();
        thread.start();   //启动后台线程检测是否有数据需要重传

        server.start();
    }


    /**
     * 启动服务器
     */
    public void start(){
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try{
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            // add handlers
                            ch.pipeline().addLast("rpcDecoder", new MomDecoder())
                                    .addLast("rpcEncoder", new MomEncoder())
                                    .addLast("logical", new BrokerServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // bind and start to accept incoming connections
            ChannelFuture future = bootstrap.bind(port).sync();
            // get the channel
            Channel channel = future.channel();

            System.out.println("server start successfully\n");
            // wait until the server socket is closed.
            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.isShuttingDown();
        }

        //never go there
        //System.out.println("server is shutdown!\n");
    }
}
