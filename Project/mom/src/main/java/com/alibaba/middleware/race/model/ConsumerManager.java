package com.alibaba.middleware.race.model;

import com.alibaba.middleware.race.mom.Message;
import io.netty.channel.Channel;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created by liuchun on 2015/8/5.
 *
 * 消费者管理
 */
public class ConsumerManager {
    // topic --> groupId List
    private ConcurrentHashMap<String, Set<String>> topicList;
    // groupId --> consumer
    private ConcurrentHashMap<String, ConsumerInfo> groupIdList;
    // message retry list, msgId ---> message
    private ConcurrentHashMap<String, MessageInfo> messageList;
    // message queue, msgId resend queue
    private BlockingQueue<String>  msgIdQueue;
    // message to storage queue, message
    private BlockingQueue<Message> messagesQueue;
    // msgId --> Producer
    private ConcurrentHashMap<String, Channel> msgIdProvider;
    // private last time add element to queue
    private volatile long lastTime;

    private static ConsumerManager instance = null;
    private static Object lock = new Object();

    private ConsumerManager() {
        topicList = new ConcurrentHashMap<>();
        groupIdList = new ConcurrentHashMap<>();
        messageList = new ConcurrentHashMap<>();

        msgIdProvider = new ConcurrentHashMap<>();

        msgIdQueue = new LinkedBlockingQueue<>();
        messagesQueue = new LinkedBlockingDeque<>();
        lastTime = System.currentTimeMillis();
    }

    /**
     * 单例模式,对消息数据进行维护和管理
     *
     * @return
     */
    public static ConsumerManager getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new ConsumerManager();
                }
            }
        }

        return instance;
    }

    /**
     * 增加topic订阅信息
     * @param topic
     * @param groupSet
     */
    public void addTopicSubscriber(String topic, Set<String>  groupSet){
        topicList.put(topic, groupSet);
    }

    /**
     * 增加groupId对应的消费者
     * @param groupId
     * @param consumerInfo
     */
    public void addGroupIdConsumer(String groupId, ConsumerInfo consumerInfo){
        groupIdList.put(groupId, consumerInfo);
    }

    /**
     * 增加msgId对应的MessageInfo信息
     * @param msgId
     * @param messageInfo
     */
    public void addMsgIdMessageInfo(String msgId, MessageInfo messageInfo){
        messageList.put(msgId, messageInfo);
    }

    /**
     * 删除msgId全部投递成功的消息
     * @param msgId
     */
    public void removeMsgId(String msgId){
        messageList.remove(msgId);
    }

    /**
     * 获取topic的订阅者GroupId列表
     *
     * @param topic
     * @return
     */
    public Set<String> getTopicSubscriber(String topic) {
        //没有人订阅该topic
        if(!topicList.containsKey(topic)){
            return null;
        }

        return topicList.get(topic);
    }

    /**
     * 获取groupId对应的用户
     * @param groupId
     * @return
     */
    public ConsumerInfo getGroupIdConsumer(String groupId){
        //该groupId没有用户
        if(!groupIdList.containsKey(groupId)){
            return null;
        }

        return groupIdList.get(groupId);
    }

    /**
     * 根据msgId获取Message信息
     * @param msgId
     * @return
     */
    public MessageInfo getMsgIdMessageInfo(String msgId){
        //该msgId不存在,可能已删除
        if(!messageList.containsKey(msgId)){
            return null;
        }

        return messageList.get(msgId);
    }

    /**
     * 获取重传消息队列的长度
     * @return
     */
    public int getMsgIdQueueSize(){
        return  msgIdQueue.size();
    }

    /**
     * 添加元素到队尾
     * @param msgId
     */
    public void addMsgIdToQueue(String msgId){
        msgIdQueue.add(msgId);
    }


    /**
     * 取出队列中第一个有效的Message
     * @return
     */
    public MessageInfo getValidMessageInfo(){
        MessageInfo messageInfo = null;

        synchronized (msgIdQueue) {
            while (msgIdQueue.size() > 0) {
                // 取队首元素,并检查合法性
                String msgId = msgIdQueue.remove();

                messageInfo = getMsgIdMessageInfo(msgId);
                // msgId对应的messageInfo消息未完全投递成功
                if (messageInfo == null) {
                    continue;  // 该message不存在,取下一条
                } else if (messageInfo.getUnSuccess() == 0) {
                    messageInfo = null;
                    continue;    // 已全部消费成功,取下一条
                } else {
                    if (System.currentTimeMillis() - messageInfo.getSendTime() < 50) {
                        // 离上次发送时间小于50ms
                        msgIdQueue.add(msgId);  //重新塞进队列
                    } else {
                        break;  // 确实需要重传的message
                    }
                }
            }
        }

        return messageInfo;
    }

    /**
     * 获取一定数目的MessagInfo
     * @param nums
     * @return
     */
    public List<MessageInfo> getValidMessageInfos(int nums){
        List<MessageInfo> messageInfos = new ArrayList<>();

        while(msgIdQueue.size() > 0 && nums > 0){
            MessageInfo messageInfo = getValidMessageInfo();

            if(messageInfo == null){
                break;  // 队列为空
            }

            messageInfos.add(messageInfo);
            nums--;
        }

        return messageInfos;
    }

    /**
     * 返回消息队列的大小
     * @return
     */
    public int getMessageQueueSize(){
        return messagesQueue.size();
    }

    /**
     * 添加message到消息队列
     * @param message
     */
    public void addMessageQueue(Message message){
        messagesQueue.add(message);
    }

    /**
     * 获取一条消息
     * @return
     */
    public Message getMessageFromQueue(){
        Message message = null;

        synchronized (messagesQueue){
            if(messagesQueue.size() > 0){
                message = messagesQueue.remove();
            }
        }

        return message;
    }

    /**
     * 获取num条消息
     * @param num
     * @return
     */
    public List<Message> getMessageFromQueue(int num){
        List<Message> messageList = new ArrayList<>();

        synchronized (messagesQueue){
            while(messagesQueue.size() > 0 && num > 0){
                Message message = messagesQueue.remove();
                messageList.add(message);
                num--;
            }
        }

        return messageList;
    }

    /**
     * 把msgId对应的channel添加进集合
     * @param msgId
     * @param channel
     */
    public void addMsgIdChannel(String msgId, Channel channel){
        msgIdProvider.put(msgId, channel);
    }

    /**
     * 移除发送成功的msdId--->channel
     * @param msdId
     */
    public void removeMsgIdChannel(String msdId){
        msgIdProvider.remove(msdId);
    }

    /**
     * 根据MsgId查找对应的Channel
     * @param msgId
     */
    public Channel getMsgIdChannel(String msgId){
        return msgIdProvider.get(msgId);
    }

    public long getLastTime() {
        synchronized (lock){
            return lastTime;
        }
    }

    public void setLastTime(long lastTime) {
        synchronized (lock){
            this.lastTime = lastTime;
        }
    }
}
