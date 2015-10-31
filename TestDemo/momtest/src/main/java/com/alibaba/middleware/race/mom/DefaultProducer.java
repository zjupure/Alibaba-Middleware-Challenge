package com.alibaba.middleware.race.mom;





public class DefaultProducer implements Producer{

	public DefaultProducer() {
		String brokerIp=System.getProperty("SIP");
	}
	
	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setTopic(String topic) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setGroupId(String groupId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public SendResult sendMessage(Message message) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void asyncSendMessage(Message message, SendCallback callback) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}
	
}
