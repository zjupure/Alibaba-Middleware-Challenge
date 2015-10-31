package com.alibaba.middleware.race.rpc.netty;

import com.alibaba.middleware.race.rpc.model.RpcRequest;
import com.alibaba.middleware.race.rpc.model.RpcResponse;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by wyd on 2015/7/24.
 * Last Modified by liuchun on 2015/7/25
 */
public class TcpServer{
    //private static final Logger LOGGER = LoggerFactory.getLogger(TcpServer.class);
    // listening port
    private int port;
    // channel for socket
    private Channel channel;
    // service list
    public Map<String, Object> handlerMap;

    public TcpServer(int port){
        this.port = port;
        handlerMap = new HashMap<String, Object>();
    }

    /**
     * 发布一项服务时,就添加到服务列表
     * @param className
     * @param obj
     */
    public void addMap(String className, Object obj){
        handlerMap.put(className, obj);
    }

    /**
     * 启动服务器
     */
    public void run() throws Exception{
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
                            ch.pipeline().addLast("rpcDecoder", new RpcDecoder(RpcRequest.class))
                                    .addLast("rpcEncoder", new RpcEncoder(RpcResponse.class))
                                    .addLast("logical", new SeverHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            // bind and start to accept incoming connections
            ChannelFuture future = bootstrap.bind(port).sync();
            // get the channel
            channel = future.channel();

            System.out.println("server start successfully\n");
            // wait until the server socket is closed.
            channel.closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.isShuttingDown();
        }

        //never go there
        //System.out.println("server is shutdown!\n");
    }


    /**
     * 服务端的Handler
     */
    public class SeverHandler extends SimpleChannelInboundHandler<RpcRequest>{
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, RpcRequest rpcRequest) throws Exception {
            RpcResponse response = new RpcResponse();
            response.setRequestID(rpcRequest.getRequestID());
            //System.out.println("request id: " + rpcRequest.getRequestID());
            //执行远程调用
            try{
                Object result = handle(rpcRequest);  //调用本地服务,处理请求
                response.setAppResponse(result);
                //System.out.print("normal response ");
            }catch (Throwable error){
                if(error instanceof InvocationTargetException){
                    Throwable targetEx = ((InvocationTargetException) error).getTargetException();
                    response.setErrorMsg(targetEx);
                }else{
                    response.setErrorMsg(error);
                }
                //response.setErrorMsg(error);        //异常
                //System.out.print("exception response ");
            }

            if(rpcRequest.getClassName().equals("com.alibaba.middleware.race.rpc.context.RpcContext")){
                // 上下文请求,无需回应
                return;
            }

            ctx.writeAndFlush(response);
            ctx.flush();
            //System.out.println("send to client\n");
        }

        /**
         * 根据请求,调用本地对应的Service
         * @param request
         * @return
         * @throws Throwable
         */
        public Object handle(RpcRequest request) throws Throwable{
            // 获取参数
            String className = request.getClassName();
            String  methodName = request.getMethodName();
            Class<?>[] parameterTypes = request.getParameterTypes();
            Object[] parameters = request.getParameters();

            Class<?>  serviceClass;
            // 若是上下文请求
            if(className.equals("com.alibaba.middleware.race.rpc.context.RpcContext")){
                serviceClass = Class.forName("com.alibaba.middleware.race.rpc.context.RpcContext");
                Method method = serviceClass.getMethod(methodName, parameterTypes);
                method.setAccessible(true);

                return method.invoke(null, parameters);
            }else {
                Object serviceBean = handlerMap.get(className);  //根据类名获取实例
                // 获取具体类,方法名,参数类型,参数列表
                serviceClass = serviceBean.getClass();
                // 根据方法名和参数类型,找到对应的方法
                Method method = serviceClass.getMethod(methodName, parameterTypes);
                method.setAccessible(true);

                return method.invoke(serviceBean, parameters);  //调用本地方法
            }
        }


        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            //LOGGER.error("server caught exception", cause);
            //ctx.close();
        }

        /*
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("client is connected\n");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("client is disconnected\n");
        }*/
    }
}
