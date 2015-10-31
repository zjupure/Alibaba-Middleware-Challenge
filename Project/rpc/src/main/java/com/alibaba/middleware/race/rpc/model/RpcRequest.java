package com.alibaba.middleware.race.rpc.model;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Created by huangsheng.hs on 2015/5/7.
 */
public class RpcRequest implements Serializable{
    private static final long serialVersionUID = -7254088932622989015L;
    // 请求ID
    private String requestID;
    // 远程调用类名称
    private String className;
    // 远程调用方法名称
    private String methodName;
    // 参数类型
    private Class<?>[] parameterTypes;
    // 参数值
    private Object[] parameters;

    public RpcRequest(){

    }

    public RpcRequest(String requestID, String className, String methodName,
                      Class<?>[] parameterTypes, Object[] parameters){
        this.requestID = requestID;
        this.className = className;
        this.methodName = methodName;
        this.parameterTypes = parameterTypes;
        this.parameters = parameters;
    }

    public String getRequestID() {
        return requestID;
    }

    public void setRequestID(String requestID) {
        this.requestID = requestID;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Class<?>[] getParameterTypes() {
        return parameterTypes;
    }

    public void setParameterTypes(Class<?>[] parameterTypes) {
        this.parameterTypes = parameterTypes;
    }

    public Object[] getParameters() {
        return parameters;
    }

    public void setParameters(Object[] parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "RpcRequest [requestID=" + requestID + ", className=" + className +
                ", methodName=" + methodName + ", parameterTypes=" + Arrays.toString(parameterTypes) +
                ", parameters=" + Arrays.toString(parameters) + "]";
    }
}
