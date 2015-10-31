package com.alibaba.middleware.race.mom;

public interface Producer {
	/**
	 * 启动生产者，初始化底层资源。在所有属性设置完毕后，才能调用这个方法
	 */
	void start();
	/**
	 * 设置生产者可发送的topic
	 * @param topic
	 */
	void setTopic(String topic);
	/**
	 * 设置生产者id，broker通过这个id来识别生产者集群
	 * @param groupId
	 */
	void setGroupId(String groupId);
	/**
	 * 发送消息
	 * @param message
	 * @return
	 */
    SendResult sendMessage(Message message);
    /**
     * 异步callback发送消息，当前线程不阻塞。broker返回ack后，触发callback
     * @param message
     * @param callback
     */
	void asyncSendMessage(Message message,SendCallback callback);
	/**
	 * 停止生产者，销毁资源
	 */
	void stop();
	
}
