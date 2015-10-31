package com.alibaba.middleware.race.rpc.api.impl;

import com.alibaba.middleware.race.rpc.api.RpcProvider;
import com.alibaba.middleware.race.rpc.context.RpcContext;
import com.alibaba.middleware.race.rpc.netty.TcpServer;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by liuchun on 2015/7/24.
 */
public class RpcProviderImpl extends RpcProvider{
    // 是否使用ZooKeeper注册中心
    private static final boolean ZOOKEEPER_ENABLE = false;
    // 主机IP和监听端口号
    private String ip;
    private int port;
    // 接口定义类
    private Class<?> serviceInterface;
    // 版本号
    private String version;
    // 接口实现类
    private Object serviceInstance;
    // 超时时间
    private int timeout;
    // 序列化类型
    private String serializaType;

    public RpcProviderImpl() {
        //读取配置文件
        //ip = "172.0.0.1";
        try {
            InetAddress addr = InetAddress.getLocalHost();
            ip=addr.getHostAddress().toString();
        }catch (UnknownHostException e){
            e.printStackTrace();
        }
        port = 8888;
    }

    @Override
    public RpcProvider serviceInterface(Class<?> serviceInterface) {
        this.serviceInterface = serviceInterface;
        return this;
    }

    @Override
    public RpcProvider version(String version) {
        this.version = version;
        return this;
    }

    @Override
    public RpcProvider impl(Object serviceInstance) {
        this.serviceInstance = serviceInstance;
        return this;
    }

    @Override
    public RpcProvider timeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    @Override
    public RpcProvider serializeType(String serializeType) {
        this.serializaType = serializeType;
        return this;
    }

    @Override
    public void publish() {
        //System.out.println("RpcProviderImpl.publish() is invoked");
        // 通过注册中心发布ip地址和端口号
        if(ZOOKEEPER_ENABLE == true){

        }
        // 设置RpcContext所有者
        RpcContext.setOwner(this);
        // 对方已经获知本机ip地址和端口号,启动服务器开始监听
        TcpServer server = new TcpServer(port);
        server.addMap(serviceInterface.getName(), serviceInstance);
        try{
            server.run();    // 启动服务器,开始监听
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
