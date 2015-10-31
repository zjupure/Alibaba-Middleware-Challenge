package com.alibaba.middleware.race.rpc.api.impl;

import com.alibaba.middleware.race.rpc.aop.ConsumerHook;
import com.alibaba.middleware.race.rpc.api.RpcConsumer;
import com.alibaba.middleware.race.rpc.async.ResponseCallbackListener;
import com.alibaba.middleware.race.rpc.async.ResponseFuture;
import com.alibaba.middleware.race.rpc.context.RpcContext;
import com.alibaba.middleware.race.rpc.model.RpcRequest;
import com.alibaba.middleware.race.rpc.model.RpcResponse;
import com.alibaba.middleware.race.rpc.netty.TcpClient;
import com.alibaba.middleware.race.rpc.netty.TcpClientPool;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Created by liuchun on 2015/7/24.
 */
public class RpcConsumerImpl extends RpcConsumer{
    // 是否使用ZooKeeper注册中心
    private static final boolean ZOOKEEPER_ENABLE = false;
    // 主机IP和端口号
    private String host;
    private int port;
    // 接口定义类
    private Class<?> interfaceClazz;
    // 版本号
    private String version;
    // 超时时间
    private int timeout = 10000;
    // Consumer Hook
    private ConsumerHook hook;

    private TcpClientPool clientPool;
    private HashMap<String, ResponseCallbackListener> asynCallMap;
    private ThreadPoolExecutor threadPool;

    private int callCnt = 0;

    public RpcConsumerImpl() {

        this.host = "127.0.0.1";
        //this.host = System.getProperty("SIP");
        this.port = 8888;

    }

    public RpcConsumerImpl(String host, int port) {
        this.host = host;
        this.port = port;
        //asynCallMap = new HashMap<String, ResponseCallbackListener>();
        //clientPool = new TcpClientPool(host, port, timeout);
    }

    @Override
    public RpcConsumer interfaceClass(Class<?> interfaceClass) {
        this.interfaceClazz = interfaceClass;
        return this;
    }

    @Override
    public RpcConsumer version(String version) {
        this.version = version;
        return this;
    }

    @Override
    public RpcConsumer clientTimeout(int clientTimeout) {
        this.timeout = clientTimeout;
        return this;
    }

    @Override
    public RpcConsumer hook(ConsumerHook hook) {
        this.hook = hook;
        return this;
    }

    @Override
    public Object instance() {
        asynCallMap = new HashMap<String, ResponseCallbackListener>();
        clientPool = new TcpClientPool(host, port, timeout);
        threadPool = new ThreadPoolExecutor(15, 30, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<Runnable>(15));

        RpcContext.setOwner(this);

        return Proxy.newProxyInstance(interfaceClazz.getClassLoader(), new Class<?>[]{interfaceClazz}, this);
    }

    @Override
    public void asynCall(String methodName) {
        asynCallMap.put(methodName, null);
    }

    @Override
    public <T extends ResponseCallbackListener> void asynCall(String methodName, T callbackListener) {
        asynCallMap.put(methodName, callbackListener);
    }

    @Override
    public void cancelAsyn(String methodName) {
        asynCallMap.remove(methodName);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        //System.out.println(++callCnt);
        // 创建并初始化RPC请求
        RpcRequest request = new RpcRequest();
        request.setRequestID(UUID.randomUUID().toString());
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);

        // 钩子函数
        if(hook != null){
            if(!method.getName().equals("getDO")){
                hook.before(request);
            }
        }

        boolean isSyncCall = !asynCallMap.containsKey(request.getMethodName());
        ResponseCallbackListener listener = asynCallMap.get(request.getMethodName());

        if (!isSyncCall) {
            if (listener == null) {
                //System.out.println("future call");
                new ResponseFuture().setFuture((Future<Object>) threadPool.submit(new FutureCallProcessor(request)));
                return null;
            } else {
                //System.out.println("callback call");
                threadPool.execute(new CallbackCallProcessor(request, listener));
                return null;
            }
        }

        // 发送远程调用请求
        RpcResponse response = null;
        TcpClient client = null;
        try {
            client = clientPool.getTcpClient();
            response = client.sendRequest(request);
        } catch (Exception e) {
            if (e instanceof TimeoutException) {
                throw e;
            }else{
                e.printStackTrace();
            }
        } finally {
            clientPool.recycle(client);
        }


        if(hook != null){
            if(!method.getName().equals("getDO")){
                hook.after(request);
            }
        }

        if(response.isError()){
            //response.getErrorMsg().printStackTrace();
            throw response.getErrorMsg();
        }else{
            // 测试代码
            /*
            Object obj = response.getAppResponse();
            if(method.getName() == "getString"){
                System.out.println((String)obj);
            }else if(method.getName() == "getMap"){
                Map<String, Object> map = (Map<String, Object>)obj;
                for(Map.Entry<String, Object> entry : map.entrySet()){
                    System.out.println(entry.getKey() + "--->" + entry.getValue());
                }
            }*/

            return response.getAppResponse();
        }
    }

    public TcpClientPool getClientPool() {
        return clientPool;
    }

    private class CallbackCallProcessor implements Runnable {
        private ResponseCallbackListener listener;
        private RpcRequest request;
        private Object result;
        private Exception exception;
        private boolean isTimeout;

        public CallbackCallProcessor(RpcRequest request, ResponseCallbackListener listener) {
            this.request = request;
            this.listener = listener;
            this.result = null;
            this.exception = null;
            this.isTimeout = false;
        }

        public void run() {
            RpcResponse response;
            TcpClient client = null;
            try {
                client = clientPool.getTcpClient();
                response = client.sendRequest(request);

                if (response.isError()) {
                    exception = (Exception) response.getErrorMsg();
                } else {
                    result = response.getAppResponse();
                }
            } catch (Exception e) {
                if (e instanceof TimeoutException) {
                    isTimeout = true;
                }
                // other exceptions process
            } finally {
                clientPool.recycle(client);
            }

            if (isTimeout) {
                listener.onTimeout();
            } else if (result != null) {
                listener.onResponse(result);
            } else {
                listener.onException(exception);
            }

            if(hook != null){
                hook.after(request);
            }
        }
    }

    private class FutureCallProcessor implements Callable<Object> {
        private RpcRequest request;

        public FutureCallProcessor(RpcRequest request) {
            this.request = request;
        }

        public Object call() throws Exception {
            RpcResponse response = null;
            TcpClient client = null;
            try {
                client = clientPool.getTcpClient();
                response = client.sendRequest(request);

            } catch (Exception e) {
                if (e instanceof TimeoutException) {
                    throw e;
                }
            }
            finally {
                clientPool.recycle(client);
            }

            if(hook != null){
                hook.after(request);
            }

            return response;
        }
    }
}
