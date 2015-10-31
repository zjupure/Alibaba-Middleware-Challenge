package com.alibaba.middleware.race.broker;

import com.alibaba.middleware.race.model.ConsumerInfo;
import com.alibaba.middleware.race.model.ConsumerManager;
import com.alibaba.middleware.race.model.MessageInfo;
import com.alibaba.middleware.race.mom.*;
import com.alibaba.middleware.race.storage.SFileManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Created by liuchun on 2015/8/5.
 */
public class BrokerServerHandler extends ChannelInboundHandlerAdapter{
    private ConsumerManager consumerManager;
    private SFileManager fileManager;
    private ExecutorService threadpool;

    public BrokerServerHandler(){
        consumerManager = ConsumerManager.getInstance();

        threadpool = Executors.newFixedThreadPool(20);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
        // 处理Producer的消息和Consumer的消费结果
        // 获取Channel
        Channel channel = ctx.channel();
        if(obj instanceof ConsumerSubscription){
            // 消费者订阅信息
            ConsumerSubscription subscription = (ConsumerSubscription)obj;
            // 处理订阅信息
            handleSubscription(subscription, channel);

        }else if(obj instanceof Message){
            // 生产者发送的消息, 消息持久化, 过滤并发送给Consumer
            Message message = (Message)obj;
            // 处理消息
            handleMessage(message, channel);

        }else if(obj instanceof ConsumeResult){
            // 消费者消费结果反馈
            ConsumeResult consumeResult = (ConsumeResult)obj;
            // 处理消费反馈
            handlerConsumeResult(consumeResult);
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {

    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client connect to broker");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        System.out.println("client disconnect to broker");
        ctx.close();
    }

    /**
     * 处理消费者的订阅信息
     * @param subscription
     */
    private void handleSubscription(ConsumerSubscription subscription, Channel channel){
        //System.out.println("consumer subscription message");

        String groupId = subscription.getGroupId();
        String topic = subscription.getTopic();
        String filter = subscription.getFilter();

        //System.out.println("groupId: " + groupId + " topic: " + topic +
        //        " filter: " + filter + "\n");
        // 根据topic查找,是否已经为该Topic建立了映射
        Set<String>  groupSet = consumerManager.getTopicSubscriber(topic);
        if(groupSet == null){
            // 新的topic订阅
            groupSet = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
            groupSet.add(groupId);

            consumerManager.addTopicSubscriber(topic, groupSet);
        }else {
            // 已订阅过的topic,判断该groupId是否已经在列表中
            if(!groupSet.contains(groupId)){
                groupSet.add(groupId);
            }
        }

        // 根据groupId查找,判定该消费者是单机还是属于某个集群
        ConsumerInfo consumerInfo = consumerManager.getGroupIdConsumer(groupId);
        if(consumerInfo == null){
            // 该集群目前还没有机器
            consumerInfo = new ConsumerInfo(groupId, topic, filter);
            consumerInfo.addChannel(channel);

            consumerManager.addGroupIdConsumer(groupId, consumerInfo);
        }else {
            // 已经有其他的订阅者,更新订阅信息,增加Channel
            consumerInfo.setTopic(topic);
            consumerInfo.setFilter(filter);
            consumerInfo.addChannel(channel);
        }
    }

    /**
     * 处理生产者的消息
     * @param message
     */
    private void handleMessage(Message message, Channel channel){
        //塞进待处理队列
        String msgId = message.getMsgId();
        consumerManager.addMsgIdChannel(msgId, channel);
        consumerManager.addMessageQueue(message);
    }

    /**
     * 处理消费者反馈
     * @param consumeResult
     */
    private void handlerConsumeResult(ConsumeResult consumeResult){
        //System.out.println("received consume result");

        String msgId = consumeResult.getMsgId();
        String groupId = consumeResult.getGroupId();
        ConsumeStatus consumeStatus = consumeResult.getStatus();

        // 处理消费者反馈
        if(consumeStatus == consumeStatus.SUCCESS){
            //System.out.println("consume success msgId: " + msgId);
            // 消息消费成功, 重传列表计数器--
            MessageInfo messageInfo = consumerManager.getMsgIdMessageInfo(msgId);

            if(messageInfo != null){
                messageInfo.removeSubscriber(groupId);
                messageInfo.setStatus(MessageInfo.SUCCESS);
                // 检查该条消息是否被所有消费者成功消费
                if(messageInfo.getUnSuccess() <= 0){
                    //System.out.println("delete this message");
                    // 删除该条消息
                    consumerManager.removeMsgId(msgId);
                    fileManager.removeMessage(msgId);
                }
            }
        }else{
            MessageInfo messageInfo = consumerManager.getMsgIdMessageInfo(msgId);

            if(messageInfo != null){
                messageInfo.setStatus(MessageInfo.FAILED);
            }
            // 消息消费失败, 需要重传
            //System.out.println("message consume failed");
        }
    }
}
