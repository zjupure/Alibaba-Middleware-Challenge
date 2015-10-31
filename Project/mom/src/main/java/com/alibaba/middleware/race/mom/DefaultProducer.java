package com.alibaba.middleware.race.mom;

import com.alibaba.middleware.race.netty.ProducerClient;
import com.alibaba.middleware.race.netty.ProducerClientPool;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

/**
 * Created by liuchun on 2015/8/1.
 */
public class DefaultProducer implements Producer{
    // broker ip
    private String brokerIP;
    // broker port
    private int port;
    // producer groupId
    private String groupId;
    // producer topic
    private String topic;
    // network client
    //private ProducerClient client;
    // network client pool
    private ProducerClientPool clientPool;
    // asyn thread pool
    private ExecutorService threadpool;

    public DefaultProducer() {

        brokerIP = "127.0.0.1";
        //brokerIP = System.getProperty("SIP");
        port = 9999;

        threadpool = Executors.newCachedThreadPool();
    }

    @Override
    public void start() {
        clientPool = new ProducerClientPool(brokerIP, port);
        //生成一个client,放入连接池
        ProducerClient client = clientPool.getClient();
        clientPool.recycleClient(client);
        //client = new ProducerClient(brokerIP, port);
        //client.connect();
    }

    @Override
    public void setTopic(String topic) {
        this.topic = topic;
    }

    @Override
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public SendResult sendMessage(Message message) {
        int retry = 0;

        //Random random = new Random();
        //long msgId = ((long)random.nextInt(Integer.MAX_VALUE) << 32) + random.nextInt();
        message.setMsgId(UUID.randomUUID().toString());
        message.setTopic(topic);
        message.setBornTime(System.currentTimeMillis());

        ProducerClient client = clientPool.getClient();
        SendResult sendResult = new SendResult();
        while (retry < 3){
            try {
                //synchronized (client){
                    sendResult = client.sendMessage(message);
                //}

                return sendResult;
            }catch (TimeoutException e){
                retry++;
            }finally {
                clientPool.recycleClient(client);
            }
        }

        sendResult.setMsgId(message.getMsgId());
        sendResult.setStatus(SendStatus.FAIL);
        sendResult.setInfo("timeout");
        return sendResult;
    }

    @Override
    public void asyncSendMessage(Message message, SendCallback callback) {
        //Random random = new Random();
        //long msgId = ((long)random.nextInt(Integer.MAX_VALUE) << 32) + random.nextInt();
        message.setMsgId(UUID.randomUUID().toString());
        message.setTopic(topic);
        message.setBornTime(System.currentTimeMillis());
        // 加入线程池,异步处理
        threadpool.execute(new asynCallTask(message, callback));
    }

    @Override
    public void stop() {
        //client.close();
        Timer timer = new Timer("stop");
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                clientPool.destoryClientPool();
            }
        }, 5000);  // 5s后销毁所有连接
        //clientPool.destoryClientPool();
    }

    /**
     * 异步调用任务
     */
    public class asynCallTask extends Thread{
        private Message message;
        private SendCallback callback;

        public asynCallTask(Message message, SendCallback callback){
            this.message = message;
            this.callback = callback;
        }
        @Override
        public void run() {
            int retry = 0;
            // 每次发消息之前,从连接池获取一个Client
            ProducerClient client = clientPool.getClient();
            SendResult sendResult = new SendResult();
            while (retry < 3){
                try {
                    //synchronized (client){
                        sendResult = client.sendMessage(message);
                    //}
                    callback.onResult(sendResult);
                    return;
                }catch (TimeoutException e){
                    retry++;
                }finally {
                    clientPool.recycleClient(client);
                }
            }

            sendResult.setMsgId(message.getMsgId());
            sendResult.setStatus(SendStatus.FAIL);
            sendResult.setInfo("timeout");
            callback.onResult(sendResult);
        }
    }
}
