package com.alibaba.middleware.race.mom;

public class ConsumeResult {
	private ConsumeStatus status=ConsumeStatus.FAIL;
	private String info;
    private String msgId;
	private String groupId;

	public void setStatus(ConsumeStatus status) {
		this.status = status;
	}
	public ConsumeStatus getStatus() {
		return status;
	}
	public void setInfo(String info) {
		this.info = info;
	}
	public String getInfo() {
		return info;
	}

	public String getMsgId() {
		return msgId;
	}

	public void setMsgId(String msgId) {
		this.msgId = msgId;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}
}
