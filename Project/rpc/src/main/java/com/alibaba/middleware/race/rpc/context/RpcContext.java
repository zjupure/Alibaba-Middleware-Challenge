package com.alibaba.middleware.race.rpc.context;

import com.alibaba.middleware.race.rpc.api.impl.RpcConsumerImpl;
import com.alibaba.middleware.race.rpc.api.impl.RpcProviderImpl;
import com.alibaba.middleware.race.rpc.model.RpcRequest;
import com.alibaba.middleware.race.rpc.netty.TcpClient;
import com.alibaba.middleware.race.rpc.netty.TcpClientPool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Created by huangsheng.hs on 2015/4/8.
 */
public class RpcContext {
    // RpcContext Owner
    private static Object owner;
    //TODO how can I get props as a provider? tip:ThreadLocal
    public static Map<String,Object> props = new HashMap<String, Object>();

    public static void addProp(String key ,Object value){
        if(owner instanceof RpcConsumerImpl){
            props.put(key,value);
            // 如果是RpcConsumer调用, 执行远程请求
            TcpClientPool pool = ((RpcConsumerImpl)owner).getClientPool();
            TcpClient client = null;
            try{
                client = pool.getTcpClient();
                RpcRequest rpcRequest = new RpcRequest();
                rpcRequest.setRequestID(UUID.randomUUID().toString());
                rpcRequest.setClassName("com.alibaba.middleware.race.rpc.context.RpcContext");
                rpcRequest.setMethodName("addProp");
                rpcRequest.setParameterTypes(new Class<?>[]{String.class, Object.class});
                rpcRequest.setParameters(new Object[]{key, value});
                // 执行远程请求
                client.sendRequest(rpcRequest);
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                pool.recycle(client);
            }

        }else if(owner instanceof RpcProviderImpl){
            // 如果是RpcProvider调用, 直接执行
            props.put(key, value);
        }
    }

    public static Object getProp(String key){
        return props.get(key);
    }

    public static Map<String,Object> getProps(){
       return Collections.unmodifiableMap(props);
    }

    /**
     * 设置RPCContext的宿主
     * @param own
     */
    public static void setOwner(Object own){
        owner = own;
    }
}
