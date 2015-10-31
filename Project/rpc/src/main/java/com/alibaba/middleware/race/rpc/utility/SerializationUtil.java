package com.alibaba.middleware.race.rpc.utility;

import com.dyuproject.protostuff.LinkedBuffer;
import com.dyuproject.protostuff.ProtostuffIOUtil;
import com.dyuproject.protostuff.Schema;
import com.dyuproject.protostuff.runtime.RuntimeSchema;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Kryo.DefaultInstantiatorStrategy;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.JavaSerializer;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 序列化工具, protostuff
 * Created by liuchun on 2015/7/24.
 *
 */
public class SerializationUtil {
    private static Map<Class<?>, Schema<?>> cachedSchema = new ConcurrentHashMap<Class<?>, Schema<?>>();
    private static Objenesis objenesis = new ObjenesisStd(true);
    // Kryo is not thread safe, pooling kryo instances
    private static ThreadLocal<Kryo>  kryos = new ThreadLocal<Kryo>(){
        @Override
        protected Kryo initialValue() {
            Kryo kryo = new Kryo();
            // configure kryo instance, customize settings
            kryo.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
            // set exception class , java serilization
            kryo.addDefaultSerializer(Throwable.class, new JavaSerializer());

            return kryo;
        }
    };

    /**
     * 获取Schema实例
     * @param clazz
     * @param <T>
     * @return
     */
    private static <T> Schema getSchema(Class<T> clazz){

        Schema<T> schema = (Schema<T>)cachedSchema.get(clazz);
        if(schema == null){
            schema = RuntimeSchema.getSchema(clazz);
            if(schema != null){
                cachedSchema.put(clazz, schema);
            }
        }

        return schema;
    }

    /**
     * 序列化
     * @param obj
     * @param <T>
     * @return
     */
    public  static <T> byte[] serializer(T obj, String type){
        // 根据type类型选择序列化方式
        if(type.equals("protostuff")){
            return serializer_Protostuff(obj);
        }else if(type.equals("kryo")){
            return serializer_Kryo(obj);
        }else {
            return serializer_Java(obj);
        }
    }

    // 默认使用Java序列化
    public static <T> byte[] serializer(T obj){
        return serializer(obj, "kryo");
    }

    /**
     * 反序列化
     * @param data
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T> T deserializer(byte[] data, Class<T> clazz, String type){
        // 根据type类型选择序列化方式
        if(type.equals("protostuff")){
            return deserializer_Protostuff(data, clazz);
        }else if(type.equals("kryo")){
            return deserializer_Kryo(data, clazz);
        }else{
            return deserializer_Java(data, clazz);
        }

    }

    // 默认使用Java进行反序列化
    public static <T> T deserializer(byte[] data, Class<T> clazz){
        return deserializer(data, clazz, "kryo");
    }


    /**
     * Kryo工具序列化
     * @param <T>
     * @return
     */
    private static <T> byte[] serializer_Kryo(T obj){

        Kryo kryo = kryos.get();
        kryo.register(obj.getClass());
        // 输出流
        Output output = new Output(4096);
        kryo.writeObject(output, obj);

        byte[] buffer = output.toBytes();
        output.close();

        return buffer;

    }

    /**
     * Kryo工具反序列化
     * @param data
     * @param clazz
     * @param <T>
     * @return
     */
    private static <T> T deserializer_Kryo(byte[] data, Class<T> clazz){

        Kryo kryo = kryos.get();
        kryo.register(clazz);

        Input input = new Input(data);
        T obj = kryo.readObject(input, clazz);


        input.close();

        return obj;
    }


    /**
     * Java内置工具进行序列化
     * @param obj
     * @param <T>
     * @return
     */
    private static <T> byte[] serializer_Java(T obj){

        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream os = new ObjectOutputStream(bos);
            // 序列化对象到输出流
            os.writeObject(obj);
            // 从流中获取字节
            byte[] buffer = bos.toByteArray();
            os.close();
            bos.close();

            return buffer;
        }catch (IOException e){
            throw new IllegalStateException(e.getMessage(), e);
        }

    }

    /**
     * Java内置工具反序列化
     * @param data
     * @param clazz
     * @param <T>
     * @return
     */
    private static <T> T deserializer_Java(byte[] data, Class<T> clazz){

        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInputStream is = new ObjectInputStream(bis);
            // 从输入流读取序列化对象
            T obj = (T)is.readObject();
            is.close();
            bis.close();

            return obj;
        }catch (ClassNotFoundException e){
            throw new IllegalStateException(e.getMessage(), e);
        } catch (IOException e){
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    /**
     * Protostuff序列化
     * 已知Bug：对List序列化出问题
     * @param obj
     * @param <T>
     * @return
     */
    private  static <T> byte[] serializer_Protostuff(T obj){

        Class<T> clazz = (Class<T>)obj.getClass();
        LinkedBuffer buffer = LinkedBuffer.allocate(4096); // 4096 bytes
        try{
            Schema<T> schema = getSchema(clazz);

            return ProtostuffIOUtil.toByteArray(obj, schema, buffer);
        }catch (Exception e){
            throw new IllegalStateException(e.getMessage(), e);
        }finally {
            buffer.clear();
        }
    }

    /**
     * Protostuff反序列化
     * @param data
     * @param clazz
     * @param <T>
     * @return
     */
    private static <T> T deserializer_Protostuff(byte[] data, Class<T> clazz){

        try{
            T obj = objenesis.newInstance(clazz);
            Schema<T> schema = getSchema(clazz);
            ProtostuffIOUtil.mergeFrom(data, obj, schema);

            return obj;
        }catch (Exception e){
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

}
