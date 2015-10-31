package com.alibaba.middleware.race.storage;


import com.alibaba.middleware.race.mom.Message;
import com.alibaba.middleware.race.utility.SerializationUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by liuchun on 2015/8/11.
 */
public class SimpleFileManager {
    // the base folder
    private static String baseFolder = "store";
    // data file read input stream
    private RandomAccessFile readMessageStream = null;
    // data file write output stream
    private RandomAccessFile writeMessageStream = null;
    // index file write output stream
    private RandomAccessFile writeIndexStream = null;
    // msgId --> offset
    private ConcurrentHashMap<String, Long> offsetCache;

    private static SimpleFileManager instance = null;
    private static Object lock = new Object();

    private SimpleFileManager(){
        //baseFolder = System.getProperty("user.home") + File.separator + "store";
        File file = new File(baseFolder);
        if(!file.exists()){
            file.mkdir();
        }

        try{
            String dataPath = baseFolder + File.separator + "data.log";
            String indexPath = baseFolder + File.separator + "data.index";
            writeMessageStream = new RandomAccessFile(dataPath, "rw");
            writeIndexStream = new RandomAccessFile(indexPath, "rw");
            readMessageStream = new RandomAccessFile(dataPath, "r");
        }catch (FileNotFoundException e){
            e.printStackTrace();
        }

        offsetCache = new ConcurrentHashMap<>();
    }

    /**
     * 单例模式,全局唯一实例
     * @return
     */
    public static SimpleFileManager getInstance(){
        if(instance == null){
            synchronized (lock){
                if(instance == null){
                    instance = new SimpleFileManager();
                }
            }
        }

        return instance;
    }

    /**
     * 写一条消息到文件系统
     * @param message
     * @return
     */
    public boolean writeMessage(Message message){
        String msgId = message.getMsgId();
        UUID uuid = UUID.fromString(msgId);

        byte[]  data = SerializationUtil.serializer(message);
        //写的时候锁住这两个文件
        synchronized (writeIndexStream){
            synchronized (writeMessageStream){
                try{
                    long offset = writeMessageStream.getFilePointer();
                    // write message to data file
                    writeMessageStream.writeInt(data.length);
                    writeMessageStream.write(data);
                    writeMessageStream.getFD().sync();
                    // wirte msgId and offset to index file
                    writeIndexStream.writeLong(uuid.getLeastSignificantBits());
                    writeIndexStream.writeLong(uuid.getMostSignificantBits());
                    writeIndexStream.writeLong(offset);
                    writeIndexStream.getFD().sync();
                    // cache msgId and offset in memory
                    offsetCache.put(msgId, offset);
                }catch (IOException e){
                    e.printStackTrace();
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * 根据msgId从文件系统读取一条消息
     * @param msgId
     * @return
     */
    public Message readMessage(String msgId){
        Message message = null;
        long offset = offsetCache.get(msgId);  //获取偏移量
        try{
            readMessageStream.seek(offset);
            int length = readMessageStream.readInt();
            byte[] data = new byte[length];
            readMessageStream.read(data, 0, length);

            message = SerializationUtil.deserializer(data, Message.class);
        }catch (IOException e){
            e.printStackTrace();
        }

        return message;
    }

    /**
     * 批量写message到文件系统
     * @param messageList
     * @return
     */
    public boolean writeAllMessages(List<Message> messageList){

        synchronized (writeIndexStream){
            synchronized (writeMessageStream){
                for(Message message : messageList){
                    String msgId = message.getMsgId();
                    UUID uuid = UUID.fromString(msgId);

                    byte[] data = SerializationUtil.serializer(message);

                    try{
                        long offset = writeMessageStream.getFilePointer();
                        // write message to data file
                        writeMessageStream.writeInt(data.length);
                        writeMessageStream.write(data);
                        //writeMessageStream.getFD().sync();
                        // wirte msgId and offset to index file
                        writeIndexStream.writeLong(uuid.getLeastSignificantBits());
                        writeIndexStream.writeLong(uuid.getMostSignificantBits());
                        writeIndexStream.writeLong(offset);
                        //writeIndexStream.getFD().sync();
                        // cache msgId and offset in memory
                        offsetCache.put(msgId, offset);
                    }catch (IOException e){
                        e.printStackTrace();
                    }
                }
                // sync to underlying device
                try{
                    writeMessageStream.getFD().sync();
                    writeIndexStream.getFD().sync();
                }catch (IOException e){
                    e.printStackTrace();
                }
            }
        }

        return true;
    }

    /**
     * 移除msgId-->offset的映射
     * @param msgId
     */
    public void removeMsgId(String msgId){
        offsetCache.remove(msgId);
    }

    /*
    public static void main(String[] args) {
        SimpleFileManager fileManager = SimpleFileManager.getInstance();
    }*/
}
