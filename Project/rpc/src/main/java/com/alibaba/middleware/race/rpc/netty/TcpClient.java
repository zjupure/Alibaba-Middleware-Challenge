package com.alibaba.middleware.race.rpc.netty;

import com.alibaba.middleware.race.rpc.model.RpcRequest;
import com.alibaba.middleware.race.rpc.model.RpcResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Created by wyd on 2015/7/24.
 * Last Modified by liuchun on 2015/7/25
 */
public class TcpClient{
    //private static final Logger LOGGER = LoggerFactory.getLogger(TcpClient.class);
    // remote host ip
    private String host;
    // remote host port
    private int port;
    // channel for socket
    private Channel channel;
    // response from server
    private RpcResponse response;
    // exception, eg: timeout
    private Exception exception;
    // blockingqueue
    private BlockingQueue<Object> rpcResponses;
    private Bootstrap bootstrap;
    private boolean connected;
    private int timeout;

    /**
     * construct a unconnected TcpClient
     * the user must explicitly invoke connect() to connect to remote server
     * @param host
     * @param port
     * @param timeout
     */
    public TcpClient(String host, int port, int timeout) {
        //System.out.println("construct TcpClient");
        this.host = host;
        this.port = port;
        this.timeout = timeout;
        this.connected = false;

        rpcResponses = new LinkedBlockingDeque<Object>(1);
        initBootstrap();
    }

    public void initBootstrap() {
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // add handlers
                        ch.pipeline().addLast("decoder", new RpcDecoder(RpcResponse.class))
                                .addLast("encoder", new RpcEncoder(RpcRequest.class))
                                .addLast("Logical", new ClientHandler());
                    }
                });
    }

    /**
     * try to connect to remove server
     * @throws Exception
     */
    public void connect() throws Exception {
        if (isConnected()) {
            return;
        }
        channel = bootstrap.connect(host, port).sync().channel();
        //connected = true;
    }

    public void close() throws Exception{
        channel.closeFuture().sync();
        //connected = false;
    }

    /**
     * if the client connected to remove server
     *
     * @return
     */
    public boolean isConnected() {
        return connected;
    }


    /**
     * send RpcRequest and return the RpcResponse
     * @param request
     * @return
     * @throws Exception
     */
    public RpcResponse sendRequest(RpcRequest request) throws Exception {
        //System.out.println("TcpClient.sendRequest() is invoked");
        response = null;
        exception = null;

        //System.out.println("send request method: " + request.getMethodName());
        //System.out.println("send request id: " + request.getRequestID());
        channel.pipeline().addFirst("timeout", new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS));
        channel.writeAndFlush(request).sync();

        // 上下文请求,直接返回,无需等待响应
        if(request.getClassName().equals("com.alibaba.middleware.race.rpc.context.RpcContext")){
            channel.pipeline().removeFirst();
            return null;
        }
        // 使用阻塞队列,等待结果
        Object obj = rpcResponses.take();
        // 收到结果,移除超时定时器
        channel.pipeline().removeFirst();
        // 处理结果
        if(obj instanceof RpcResponse){
            response = (RpcResponse)obj;
        }else if(obj instanceof TimeoutException){
            exception = (TimeoutException)obj;
            throw exception;
        }
        //System.out.println("received response id: " + response.getRequestID() + "\n");

        return  response;
    }


    /**
     * 客户端处理Handler
     */
    public class ClientHandler extends SimpleChannelInboundHandler<RpcResponse>{

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcResponse rpcResponse) throws Exception {
            //System.out.println("TcpClient handler receive message");
            //response = rpcResponse;
            rpcResponses.put(rpcResponse);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

            if (cause instanceof ReadTimeoutException) {
                //System.out.println("Read time out");
                exception = new TimeoutException("timeout exception");
                rpcResponses.put(rpcResponses);
            }else{
                cause.printStackTrace();
                ctx.close();
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            connected = true;
            //System.out.println("connect to server\n");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            connected = false;
            //System.out.println("disconnect to server\n");
            //ctx.close();
        }

    }
}
