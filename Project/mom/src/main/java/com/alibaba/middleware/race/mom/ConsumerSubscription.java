package com.alibaba.middleware.race.mom;

/**
 * Created by wyd on 2015/8/2.
 */
// the subscription information from consumer
public class ConsumerSubscription {
    private String groupId;
    private String topic;
    private String filter;

    public ConsumerSubscription(String groupId, String topic, String filter) {
        this.topic = topic;
        this.groupId = groupId;
        this.filter = filter;
    }

    public String getTopic() {
        return topic;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getFilter() {
        return filter;
    }
}
