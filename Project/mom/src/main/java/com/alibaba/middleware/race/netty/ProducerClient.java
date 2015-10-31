package com.alibaba.middleware.race.netty;

import com.alibaba.middleware.race.mom.DefaultProducer;
import com.alibaba.middleware.race.mom.Message;
import com.alibaba.middleware.race.mom.SendCallback;
import com.alibaba.middleware.race.mom.SendResult;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by liuchun on 2015/8/5.
 */
public class ProducerClient {
    // broker ip
    private String host;
    // broker port
    private int port;
    // timeout
    private int timeout = 10; // default 10s

    // Blocking queue
    private BlockingQueue<Object> resultQueue;
    private Bootstrap bootstrap;
    private Channel channel;
    private EventLoopGroup workergroup;

    private boolean connected;

    public ProducerClient(String host, int port){
        this.host = host;
        this.port = port;

        connected = false;
        resultQueue = new LinkedBlockingDeque<Object>(1);
        initBootstrap();
    }

    /**
     * 初始化Bootstrap
     */
    private void initBootstrap() {
        workergroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(workergroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast("decoder", new MomDecoder())
                                .addLast("encoder", new MomEncoder())
                                .addLast("handler", new ProducerClientHandler());
                    }
                });
    }

    public void connect(){
        if (isConnected()) {
            return;
        }
        try{
            channel = bootstrap.connect(host, port).sync().channel();
        }catch (InterruptedException e){
            e.printStackTrace();
        }

    }

    public void close(){
        channel.close().awaitUninterruptibly();
        workergroup.shutdownGracefully();
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * 生产者同步发送Messager
     * @param msg
     * @return
     */
    public SendResult sendMessage(Message msg) throws TimeoutException{
        // 写数据到Channel
        channel.pipeline().addFirst("timeout", new ReadTimeoutHandler(timeout, TimeUnit.SECONDS));
        channel.writeAndFlush(msg);

        try{
            Object obj = resultQueue.take();

            channel.pipeline().removeFirst();

            if(obj instanceof SendResult){
                return (SendResult)obj;
            }else{
                throw (TimeoutException)obj;
            }
        }catch (InterruptedException e){
            e.printStackTrace();
        }
        return  null;
    }


    /**
     * 生产者处理Handler
     */
    public class ProducerClientHandler extends SimpleChannelInboundHandler<SendResult>{
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, SendResult sendResult) throws Exception {

            resultQueue.put(sendResult);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

            TimeoutException exception = (TimeoutException)cause;
            resultQueue.put(exception);
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            connected = true;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connected = false;
        }
    }
}
