package com.alibaba.middleware.race.broker;

import com.alibaba.middleware.race.model.ConsumerInfo;
import com.alibaba.middleware.race.model.ConsumerManager;
import com.alibaba.middleware.race.model.MessageInfo;
import com.alibaba.middleware.race.mom.Message;
import com.alibaba.middleware.race.storage.SFileManager;
import io.netty.channel.Channel;

import java.util.List;
import java.util.Set;

/**
 * Created by liuchun on 2015/8/8.
 */
public class MessageResendTask extends Thread {
    private ConsumerManager consumerManager;
    private SFileManager fileManager;
    private List<MessageInfo> messageInfos;

    public MessageResendTask(List<MessageInfo> messageInfos){
        consumerManager = ConsumerManager.getInstance();
        fileManager = SFileManager.getInstance();

        this.messageInfos = messageInfos;
    }


    @Override
    public void run() {
        // 处理所有的message
        for(MessageInfo messageInfo : messageInfos){
            String msgId = messageInfo.getMsgId();
            String topic = messageInfo.getTopic();

            //System.out.println("message msgId: " + msgId + " topic: " + topic);
            // 从存储系统查找到对应的Message信息
            Message message = fileManager.readMessage(msgId);

            if(message == null){
                continue;
            }

            // 获取订阅者列表,向消费者开始发送
            Set<String> subscriberSet = messageInfo.getSubscriberList();

            if(subscriberSet == null || subscriberSet.size() <= 0){
                continue;  // 订阅列表为空
            }

            //System.out.println("message from file system, msgId: " + messageInfo.getMsgId() +
            //        " topic: " + messageInfo.getTopic());

            boolean isOnline = false;
            // 循环遍历订阅者,发送消息
            for(String groupId : subscriberSet){
                ConsumerInfo consumerInfo = consumerManager.getGroupIdConsumer(groupId);

                if(consumerInfo == null){
                    continue;  //没有订阅者,取下一条
                }
                //随机选取一个channel
                Channel consumer = consumerInfo.pickChannel();
                //存在活跃的channel
                if(consumer != null){
                    consumer.writeAndFlush(message);
                    isOnline = true;   //有用户在线
                    //System.out.println("send message to subscriber\n");
                }
            }
            // 有用户在线,就重新塞进重传队列
            if(isOnline == true){
                messageInfo.setSendTime(System.currentTimeMillis());
                messageInfo.setStatus(MessageInfo.SENDING);
                consumerManager.addMsgIdToQueue(msgId);
            }else {
                messageInfo.setStatus(MessageInfo.OFFLINE);
            }

        }
    }
}
