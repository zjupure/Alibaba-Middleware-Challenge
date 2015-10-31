package com.alibaba.middleware.race.broker;

import com.alibaba.middleware.race.model.ConsumerInfo;
import com.alibaba.middleware.race.model.ConsumerManager;
import com.alibaba.middleware.race.model.MessageInfo;
import com.alibaba.middleware.race.mom.Message;
import com.alibaba.middleware.race.mom.SendResult;
import com.alibaba.middleware.race.mom.SendStatus;
import com.alibaba.middleware.race.storage.SFileManager;
import io.netty.channel.Channel;

import java.util.List;
import java.util.Set;

/**
 * Created by liuchun on 2015/8/13.
 */
public class PersistenceTask extends Thread{
    private SFileManager fileManager;
    private ConsumerManager consumerManager;
    private List<Message> messages;

    public PersistenceTask(List<Message> messages){
        fileManager = SFileManager.getInstance();
        consumerManager = ConsumerManager.getInstance();

        this.messages = messages;
    }

    @Override
    public void run() {
        //long startTime = System.currentTimeMillis();
        List<Message> successMessages = fileManager.writeMessage(messages);
        //long endTime = System.currentTimeMillis();
        //System.out.println("item number: " + messages.size());
        //long diff = endTime - startTime;
        //System.out.println("time: " + diff);


        SendResult sendResult = new SendResult();
        // 处理落盘成功的消息
        for(Message message : successMessages){
            String msgId = message.getMsgId();
            sendResult.setMsgId(msgId);
            sendResult.setStatus(SendStatus.SUCCESS);
            Channel channel = consumerManager.getMsgIdChannel(msgId);

            if(channel.isActive()){
                channel.writeAndFlush(sendResult);
                sendMessage(message);
                consumerManager.removeMsgIdChannel(msgId);
            }
        }
    }

    /**
     * 向消费者发送消息
     */
    public void sendMessage(Message message){
        // 对消息进行过滤,准备发送
        String msgId = message.getMsgId();
        String topic = message.getTopic();

        //System.out.println("msgId: " + msgId + " topic: " + topic + "\n");
        // 查找订阅该topic的用户群
        Set<String> groupSet = consumerManager.getTopicSubscriber(topic);
        if(groupSet == null || groupSet.size() <= 0){
            //System.out.println("no consumer subscriber this topic");
            return;   //没有消费者订阅该topic
        }

        //System.out.println("there are some subscriber");
        MessageInfo messageInfo = new MessageInfo(msgId, topic);
        // 循环遍历订阅列表中的groupId, 统计订阅用户
        for(String groupId : groupSet){
            //System.out.println("groupId: " + groupId);
            // 找到groupId对应的消费者集群
            ConsumerInfo consumerInfo = consumerManager.getGroupIdConsumer(groupId);

            if(consumerInfo == null){
                //System.out.println("no consumer subscriber this topic with filter");
                continue;   // 没有找到对应的订阅者
            }

            if(consumerInfo.hasFilter() == false){
                //System.out.println("consumer no filter");
                // 没有设置过滤器,增加订阅者到列表
                messageInfo.addSubscriber(groupId);
            }else{
                String key = consumerInfo.getPropertyKey();
                String value = consumerInfo.getPropertyValue();
                String msgValue = message.getProperty(key);
                // Message中key对应的属性值存在,且等于订阅者的属性
                if(msgValue != null && msgValue.equals(value)){
                    // 增加订阅者
                    //System.out.println("key = " + key + " value = " + value);
                    messageInfo.addSubscriber(groupId);
                }
            }
        }
        // 统计完毕,建立映射关系
        if(messageInfo.getUnSuccess() == 0){
            return;   // 消息过滤之后,没有订阅者
        }

        // 获取订阅者列表,向消费者开始发送
        Set<String>  subscriberSet = messageInfo.getSubscriberList();

        if(subscriberSet == null || subscriberSet.size() <= 0){
            return;   //订阅列表为空
        }

        boolean isOnline = false;  //消息到达时,判断是否有消费者在线
        // 循环遍历订阅者,发送消息
        for(String groupId : subscriberSet){
            ConsumerInfo consumerInfo = consumerManager.getGroupIdConsumer(groupId);

            if(consumerInfo == null){
                continue;  // 没有找到对应的订阅者
            }
            //随机选取一个channel
            Channel consumer = consumerInfo.pickChannel();
            //存在活跃的channel
            if(consumer != null){
                consumer.writeAndFlush(message);
                isOnline = true;
                //System.out.println("send message to subscriber\n");
            }
        }
        // 设置消息发送时间,建立映射关系
        if(isOnline){
            messageInfo.setSendTime(System.currentTimeMillis());
            messageInfo.setStatus(MessageInfo.SENDING);
        }else {
            messageInfo.setStatus(MessageInfo.OFFLINE);
        }

        consumerManager.addMsgIdMessageInfo(msgId, messageInfo);
        consumerManager.addMsgIdToQueue(msgId);   // 添加到消息队列
        consumerManager.setLastTime(System.currentTimeMillis());  // 设置队列最后一次写的时间
    }
}
