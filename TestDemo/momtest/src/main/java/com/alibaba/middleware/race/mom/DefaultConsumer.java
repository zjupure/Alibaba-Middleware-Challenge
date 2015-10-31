package com.alibaba.middleware.race.mom;




public class DefaultConsumer implements Consumer{

	public DefaultConsumer() {
		String brokerIp=System.getProperty("SIP");

	}
	
	@Override
	public void start() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void subscribe(String topic, String filter, MessageListener listener) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setGroupId(String groupId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		
	}
	
}
