package com.alibaba.middleware.race.mom;

public interface Consumer {
	/**
	 * 启动消费者，初始化底层资源。要在属性设置和订阅操作发起之后执行
	 */
	void start();

	/**
	 * 发起订阅操作
	 * 
	 * @param topic
	 *            只接受该topic的消息
	 * @param filter
	 *            属性过滤条件，例如 area=hz，表示只接受area属性为hz的消息。消息的过滤要在服务端进行
	 * @param listener
	 */
	void subscribe(String topic, String filter, MessageListener listener);

	/**
	 * 设置消费者组id，broker通过这个id来识别消费者机器
	 * 
	 * @param groupId
	 */
	void setGroupId(String groupId);

	/**
	 * 停止消费者，broker不再投递消息给此消费者机器。
	 */
	void stop();
}
