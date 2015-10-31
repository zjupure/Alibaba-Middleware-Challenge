package com.alibaba.middleware.race.mom;

import com.alibaba.middleware.race.netty.ConsumerClient;

/**
 * Created by liuchun on 2015/8/1.
 */
public class DefaultConsumer implements Consumer {
    // broker ip
    private String brokerIP;
    // broker port
    private int port;
    // groupId
    private String groupId;
    // consumer subscription
    private ConsumerSubscription subscription;
    // consumer client
    private ConsumerClient client;
    // message listener
    private MessageListener listener;

    public DefaultConsumer() {

        brokerIP = "127.0.0.1";
        //brokerIP = System.getProperty("SIP");
        port = 9999;
    }

    @Override
    public void start() {
        // start a tcp connection and set self as the MomMessage callback listener
        client = new ConsumerClient(brokerIP, port, listener, groupId);
        try {
            client.connect();
            // send the subscription information
            client.sendSubscription(subscription);
        } catch (Exception e) {
            // connect false or send failure
            e.printStackTrace();
        }
    }

    @Override
    public void subscribe(String topic, String filter, MessageListener listener) {
        this.listener = listener;

        // construct the subscription information and warp it in a MomMessage
         subscription = new ConsumerSubscription(groupId, topic, filter);
    }

    @Override
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public void stop() {
        client.close();
    }
}
