package com.alibaba.middleware.race.model;


import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by liuchun on 2015/8/5.
 */
public class MessageInfo {
    public static final int SENDING = 0x00;
    public static final int SUCCESS = 0x01;
    public static final int FAILED = 0x02;
    public static final int TIMEOUT = 0x03;
    public static final int OFFLINE = 0x04;
    // 消息Id
    private String msgId;
    // 消息主题
    private String topic;
    // 生命周期
    private long sendTime;
    // 该消息订阅的groupId
    private Set<String> subscriberList;
    // 还未投递成功的个数
    private int unSuccess = 0;
    // 状态码, 0x00: 正在发送; 0x01: 消费成功; 0x02: 消费失败; 0x03: 消费超时; 0x04: 无消费者在线
    private volatile int status = 0;
    // 重发次数
    private volatile int retry = 0;
    // lock
    private Object lock = new Object();

    public MessageInfo(String msgId, String topic){
        this.msgId = msgId;
        this.topic = topic;
        // 线程安全的集合
        subscriberList = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    }

    /**
     * 每次收到一个消费成功的消息,计数器减1
     */
    public void removeSubscriber(String groupId){
        synchronized (lock){
            unSuccess--;
        }
        // 移除对应的groupId
        subscriberList.remove(groupId);
    }

    /**
     * 增加订阅者
     * @param groupId
     */
    public void addSubscriber(String groupId){
        synchronized (lock){
            unSuccess++;
        }
        // 增加对应的groupId
        subscriberList.add(groupId);
    }

    /**
     * 获取订阅者列表
     * @return
     */
    public Set<String> getSubscriberList() {
        return subscriberList;
    }

    /**
     * 获取未投递成功group个数
     * @return
     */
    public int getUnSuccess(){
        synchronized (lock){
            return unSuccess;
        }
    }

    public String getMsgId(){
        return msgId;
    }

    public String getTopic(){
        return topic;
    }

    public long getSendTime() {
        return sendTime;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }
}
