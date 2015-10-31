package com.alibaba.middleware.race.netty;

import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;

/**
 * Created by liuchun on 2015/8/5.
 */
public class ProducerClientPool extends BasePooledObjectFactory<ProducerClient> {
    private static int POOL_SIZE = 200;
    private String host;
    private int port;
    private GenericObjectPool<ProducerClient> pool;

    public ProducerClientPool(String host, int port){
        this.host = host;
        this.port = port;
        pool = new GenericObjectPool<>(this);

        pool.setMaxTotal(POOL_SIZE);
        pool.setMaxIdle(POOL_SIZE);
    }

    /**
     * 从连接池中取出一个Client,并确保连接上Broker
     * @return
     */
    public ProducerClient getClient(){
        ProducerClient client = null;
        try{
            client = pool.borrowObject();
            if(!client.isConnected()){
                client.connect();  // assure the client connected to server
            }
        }catch (InterruptedException e){
            e.printStackTrace();
        }catch (Exception e){
            e.printStackTrace();
        }

        return  client;
    }

    /**
     * 回收一个Client
     * @param client
     */
    public void recycleClient(ProducerClient client){
        if(client != null){
            pool.returnObject(client);
        }
    }

    /**
     * 销毁连接池中的所有对象
     */
    public void destoryClientPool(){
        pool.clear();
    }
    @Override
    public ProducerClient create() throws Exception {
        return new ProducerClient(host, port);
    }

    @Override
    public PooledObject<ProducerClient> wrap(ProducerClient producerClient) {
        return new DefaultPooledObject<>(producerClient);
    }

    @Override
    public boolean validateObject(PooledObject<ProducerClient> p) {
        return p.getObject().isConnected();
    }

    @Override
    public void activateObject(PooledObject<ProducerClient> p) throws Exception {
        p.getObject().connect();
    }

    @Override
    public void destroyObject(PooledObject<ProducerClient> p) throws Exception {
        p.getObject().close();
    }
}
