package com.alibaba.middleware.race.model;

import com.alibaba.middleware.race.mom.Consumer;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by liuchun on 2015/8/5.
 */
public class ConsumerInfo {
    // groupId
    private String groupId;
    // topic
    private String topic;
    // filter
    private boolean filter = false;
    private String propertyKey = "";
    private String propertyValue = "";
    // channel group, manage all the channel
    private ChannelGroup channelGroup;

    public ConsumerInfo(String groupId, String topic){
        this.groupId = groupId;
        this.topic = topic;

        channelGroup = new DefaultChannelGroup(groupId, GlobalEventExecutor.INSTANCE);
    }

    public ConsumerInfo(String groupId, String topic, String filter){
        this(groupId, topic);

        setFilter(filter);
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public boolean hasFilter() {
        return filter;
    }

    /**
     * 设置过滤器
     * @param filter
     */
    public void setFilter(String filter){
        if(filter == null || filter.equals("")){
            this.filter = false;   //没有过滤器
        }else{
            this.filter = true;
            String[] key_value = filter.split("=");
            propertyKey = key_value[0];
            propertyValue = key_value[1];
        }
    }

    public String getPropertyKey() {
        return propertyKey;
    }


    public String getPropertyValue() {
        return propertyValue;
    }

    /**
     * 添加Channel到group
     * @param channel
     */
    public void addChannel(Channel channel){
        channelGroup.add(channel);
    }

    /**
     * 从group中随机pick一个channel
     * @return
     */
    public Channel pickChannel(){
        //没有活跃的channel
        if(channelGroup.size() <= 0){
            return null;
        }

        Object[] obj = channelGroup.toArray();
        int size = channelGroup.size();
        int pos = new Random().nextInt(size);

        return (Channel)obj[pos];
    }
}
