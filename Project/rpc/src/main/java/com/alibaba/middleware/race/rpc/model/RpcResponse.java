package com.alibaba.middleware.race.rpc.model;

import java.io.Serializable;

/**
 * Created by huangsheng.hs on 2015/3/27.
 */
public class RpcResponse implements Serializable{
    private static final long serialVersionUID = -7254088932622989015L;
    // 请求的ID
    private String requestID;
    // 异常
    private Throwable errorMsg;
    // 响应内容
    private Object appResponse;

    public RpcResponse(){

    }

    public RpcResponse(String requestID, Throwable errorMsg, Object appResponse){
        this.requestID = requestID;
        this.errorMsg = errorMsg;
        this.appResponse = appResponse;
    }

    public String getRequestID() {
        return requestID;
    }

    public void setRequestID(String requestID) {
        this.requestID = requestID;
    }

    public Throwable getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(Throwable errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Object getAppResponse() {
        return appResponse;
    }

    public void setAppResponse(Object appResponse) {
        this.appResponse = appResponse;
    }

    public boolean isError(){
        return errorMsg == null ? false : true;
    }
    @Override
    public String toString() {
        return "RpcResponse [requestID=" + requestID + ", error=" + errorMsg +
                ", appResponse=" + appResponse + "]";
    }
}
