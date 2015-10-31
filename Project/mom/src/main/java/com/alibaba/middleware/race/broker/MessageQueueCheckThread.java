package com.alibaba.middleware.race.broker;

import com.alibaba.middleware.race.model.ConsumerManager;
import com.alibaba.middleware.race.model.MessageInfo;
import com.alibaba.middleware.race.mom.Message;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by liuchun on 2015/8/8.
 */
public class MessageQueueCheckThread extends Thread {
    private static int THRESHLOD = 100;  // 少于100条
    private static int INTERVAL = 5;
    private ConsumerManager consumerManager;
    private ExecutorService threadpool;

    public MessageQueueCheckThread(){
        consumerManager = ConsumerManager.getInstance();

        threadpool = Executors.newCachedThreadPool();
    }

    @Override
    public void run() {

        //后台死循环检测消息队列
        while (true) {
            // 5ms检测一次队列是否为空
            if(consumerManager.getMessageQueueSize() > 0){
                List<Message> messages = consumerManager.getMessageFromQueue(THRESHLOD);
                // 新建持久化任务
                if(messages.size() > 0){
                    threadpool.execute(new PersistenceTask(messages));
                }
            }

            if(consumerManager.getMsgIdQueueSize() > 5*THRESHLOD ||
                    System.currentTimeMillis() - consumerManager.getLastTime() > 50){
                // 新建重发消息任务
                List<MessageInfo> messageInfos = consumerManager.getValidMessageInfos(5*THRESHLOD);

                if(messageInfos.size() > 0){
                    threadpool.execute(new MessageResendTask(messageInfos));
                }
            }

            //System.out.println("back thread");
            // 空闲,线程休眠
            try{
                Thread.sleep(INTERVAL);
            }catch (InterruptedException e){
                e.printStackTrace();
            }
        }

    }
}
