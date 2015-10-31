package com.alibaba.middleware.race.netty;

import com.alibaba.middleware.race.mom.ConsumeResult;
import com.alibaba.middleware.race.mom.ConsumerSubscription;
import com.alibaba.middleware.race.mom.Message;
import com.alibaba.middleware.race.mom.MessageListener;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * Created by liuchun on 2015/8/5.
 */
public class ConsumerClient {
    // broker ip
    private String host;
    // broker port
    private int port;
    // Message Listener
    private MessageListener listener;
    // groupId
    private String groupId;

    private Bootstrap bootstrap;
    private Channel channel;
    private EventLoopGroup workergroup;

    private boolean connected;

    public ConsumerClient(String host, int port, MessageListener listener, String groupId){
        this.host = host;
        this.port = port;
        this.listener = listener;
        this.groupId = groupId;

        connected = false;
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
                                .addLast("handler", new ConsumerClientHandler());
                    }
                });
    }

    public void connect() throws InterruptedException{
        if (isConnected()) {
            return;
        }
        channel = bootstrap.connect(host, port).sync().channel();
    }

    public void close(){
        channel.close().awaitUninterruptibly();
        workergroup.shutdownGracefully();
    }

    public boolean isConnected() {
        return connected;
    }

    /**
     * 消费者发送订阅信息
     * @param subscription
     */
    public void sendSubscription(ConsumerSubscription subscription){

        channel.writeAndFlush(subscription);
    }

    public void sendConsumerResult(ConsumeResult consumeResult){

        channel.writeAndFlush(consumeResult);
    }

    /**
     * 消费者处理Handler
     */
    public class ConsumerClientHandler extends SimpleChannelInboundHandler<Message>{
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, Message message) throws Exception {
            ConsumeResult consumeResult = listener.onMessage(message);

            // 设置消息id和groupId
            consumeResult.setMsgId(message.getMsgId());
            consumeResult.setGroupId(groupId);

            sendConsumerResult(consumeResult);
            //System.out.println("send consume result to broker");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            cause.printStackTrace();
            ctx.close();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            // 连接上broker
            connected = true;
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // broker挂机
            connected = false;
        }
    }
}
