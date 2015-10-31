package com.alibaba.middleware.race.storage;

import com.alibaba.middleware.race.broker.BrokerServerHandler;
import com.alibaba.middleware.race.mom.Message;
import com.alibaba.middleware.race.utility.SerializationUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wyd on 2015/8/12.
 */
public class SFileManager {
    // the base folder
    private static String baseFolder = "store";
    // write message stream
    private RandomAccessFile writeMessageStream;
    // write index stream
    private RandomAccessFile writeIndexStream;
    // read message stream
    private RandomAccessFile readMessageStream;
    // current offset
    //private long currOffset;
    // the index file cache, map of msgId-offset pairs
    private ConcurrentHashMap<String, Long> indexCache;

    private static SFileManager instance = null;
    private static Object lock = new Object();

    /**
     * 单例模式,全局唯一实例
     * @return
     */
    public static SFileManager getInstance(){
        if(instance == null){
            synchronized (lock){
                if(instance == null){
                    instance = new SFileManager();
                }
            }
        }

        return instance;
    }

    public SFileManager() {
        baseFolder = System.getProperty("user.home") + File.separator + "store";
        //baseFolder = "store";
        File file = new File(baseFolder);
        if(!file.exists()){
            file.mkdir();  // 目录不存在,则建立新目录
        }
        baseFolder += File.separator;

        // for local test
        //baseFolder = "F:\\storage\\";

        try {
            writeMessageStream = new RandomAccessFile(baseFolder + "message", "rw");
            writeIndexStream = new RandomAccessFile(baseFolder + "index", "rw");
            readMessageStream = new RandomAccessFile(baseFolder + "message", "r");
        } catch (IOException e) {
            e.printStackTrace();
        }
        //currOffset = 0;
        indexCache = new ConcurrentHashMap<>();
        // recover currOffset and indexCache here
    }

    /**
     * 写一条消息到存储系统
     * @param msg
     * @return
     */
    public boolean writeMessage(Message msg) {
        String msgId = msg.getMsgId();
        UUID uuid = UUID.fromString(msgId);

        byte[] data = SerializationUtil.serializer(msg);
        synchronized (writeIndexStream) {
            synchronized (writeMessageStream) {
                // lock the current output file and index file
                try {
                    long offset = writeMessageStream.getFilePointer();
                    // write the message first and then update the index file
                    writeMessageStream.writeInt(data.length);
                    writeMessageStream.write(data);
                    writeMessageStream.getFD().sync();
                    // write the index file
                    /*writeIndexStream.writeLong(uuid.getLeastSignificantBits());
                    writeIndexStream.writeLong(uuid.getMostSignificantBits());
                    writeIndexStream.writeLong(currOffset);
                    writeIndexStream.getFD().sync();*/
                    indexCache.put(msgId, offset);
                    //currOffset += data.length + 4;
                } catch (Exception e) {
                    // e.printStackTrace();
                    // write failure
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 从存储系统读取一条消息
     * @param msgId
     * @return
     */
    public Message readMessage(String msgId) {
        Message msg = null;
        UUID uuid = UUID.fromString(msgId);
        long offset = indexCache.get(msgId);

        try {
            readMessageStream.seek(offset);
            // read the message len
            int dataLength = readMessageStream.readInt();
            byte[] data = new byte[dataLength];
            readMessageStream.read(data, 0, dataLength);
            msg = SerializationUtil.deserializer(data, Message.class);
        } catch (IOException e) {
            // read message date failure
            msg = null;
        }
        return msg;
    }

    /**
     * 移除msgId-->offset的偏移量
     * @param msgId
     */
    public void removeMessage(String msgId) {
        indexCache.remove(msgId);
    }


    /**
     * 批量写消息到文件系统
     * @param messages
     * @return
     */
    public List<Message> writeMessage(List<Message> messages) {
        //System.out.println("message size: " + messages.size());
        List<Message> writeResult = null;
        synchronized (writeIndexStream) {
            synchronized (writeMessageStream) {
                writeResult = new ArrayList<>();
                // lock the current output file and index file
                for (Message message : messages) {
                    String topic = message.getTopic();
                    String msgId = message.getMsgId();
                    UUID uuid = UUID.fromString(msgId);
                    byte[] data = SerializationUtil.serializer(message);
                    try {
                        long offset = writeMessageStream.getFilePointer();
                        // write the message first and then update the index file
                        writeMessageStream.writeInt(data.length);
                        writeMessageStream.write(data);
                        // write the index file
                        /*writeIndexStream.writeLong(uuid.getLeastSignificantBits());
                        writeIndexStream.writeLong(uuid.getMostSignificantBits());
                        writeIndexStream.writeLong(currOffset);*/
                        indexCache.put(msgId, offset);
                        //currOffset += data.length + 4;
                        writeResult.add(message);
                    } catch (Exception e) {
                        // e.printStackTrace();
                        // write failure
                        // return false;
                    }
                }

                try {
                    //long startTime = System.currentTimeMillis();
                    writeMessageStream.getFD().sync();
                    //long endTime = System.currentTimeMillis();
                    //long diff = endTime - startTime;
                    //System.out.println("time: " + diff);
                } catch (IOException e) {
                    writeResult = null;
                }
            }
        }
        return writeResult;
    }

    /*
    public static void main(String[] args) {
        SFileManager manager = null;
        Random random = new Random(System.currentTimeMillis());
        int testWrite = 300;
        int numOfOnceWriteToDevice = 30;
        int testRead = 50;
        int cnt = 0;
        String[] topicRead = new String[testRead];
        String[] msgIdRead = new String[testRead];
        List<Message> msgBuffer = new ArrayList<>();
        try {
            manager = new SFileManager();
        } catch (Exception e) {
            // init the manager error
            e.printStackTrace();
        }

        Message msg;
        String[] topics = {"topic1", "topic2", "topic3"};
        String[] bodies = {"body1", "body2", "body3"};
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < testWrite / numOfOnceWriteToDevice; i++) {
            for (int j = 0; j < numOfOnceWriteToDevice; j++) {
                msg = new Message();
                String msgId = UUID.randomUUID().toString();
                int tmp = Math.abs(random.nextInt());
                //msgId = new Long(0);
                //tmp = 0;
                msg.setTopic(topics[tmp % topics.length]);
                msg.setBody(bodies[tmp % topics.length].getBytes());
                msg.setMsgId(msgId);
                if (i * numOfOnceWriteToDevice + j > 10 && i * numOfOnceWriteToDevice + j <= 60) {
                    topicRead[cnt] = msg.getTopic();
                    msgIdRead[cnt] = msg.getMsgId();
                    cnt++;
                }
                msgBuffer.add(msg);
            }
            // System.out.println(i + "\n");
            if (manager.writeMessage(msgBuffer) == null) {
                System.out.println("write message error: " + i + "\n");
                return;
            }
        }
        long time = System.currentTimeMillis() - startTime;
        System.out.println("time: " + time + "\n");
        System.out.println("success");
    }*/
}
