package com.alibaba.middleware.race.rpc.netty;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

/**
 * Created by wyd on 2015/7/26.
 */
public class TcpClientPool extends BasePooledObjectFactory<TcpClient> {
    private String host;
    private int port;
    private int timeout;
    private GenericObjectPool<TcpClient> pool;

    public TcpClientPool(String host, int port, int timeout) {
        this.host = host;
        this.port = port;
        this.timeout = timeout;
        pool = new GenericObjectPool<TcpClient>(this);

        int coreCount = Runtime.getRuntime().availableProcessors() * 2;
        pool.setMaxTotal(coreCount*2);
        pool.setMaxIdle(coreCount);
    }

    public TcpClient getTcpClient() throws Exception{
        TcpClient client =  pool.borrowObject();
        if (!client.isConnected()) {
            client.connect();
        }
        return client;
    }

    public void recycle(TcpClient client){
        if (client != null) {
            pool.returnObject(client);
        }
    }

    @Override
    public TcpClient create() throws Exception {
        //System.out.println("construct TcpClient");
        return new TcpClient(host, port, timeout);
    }

    @Override
    public PooledObject<TcpClient> wrap(TcpClient client) {
        return new DefaultPooledObject<TcpClient>(client);
    }

    @Override
    public boolean validateObject(PooledObject<TcpClient> p) {
        return p.getObject().isConnected();
    }

    @Override
    public void activateObject(PooledObject<TcpClient> p) throws Exception {
        p.getObject().connect();
    }

    @Override
    public void destroyObject(PooledObject<TcpClient> p) throws Exception {
        p.getObject().close();
    }
}
