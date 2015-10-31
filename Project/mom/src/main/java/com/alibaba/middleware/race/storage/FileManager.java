package com.alibaba.middleware.race.storage;

import com.alibaba.middleware.race.mom.Message;
import com.alibaba.middleware.race.utility.SerializationUtil;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by wyd on 2015/8/5.
 */
public class FileManager {
    // the base folder
    private static String baseFolder = "store";
    // number of partitions for each topic
    private static int partitionCount = 1;
    // default segment file size
    private static int segment_size = 1024*1024; // 1M
    // recent use output file streams
    private FileStreamCache<RandomAccessFile> outputStreamCache;
    // recent use index file writes
    private FileStreamCache<RandomAccessFile> indexWriterCache;
    // recent use read file streams
    private FileStreamCache<RandomAccessFile> inputStreamCache;
    // current offset
    private ConcurrentHashMap<String, ArrayList<Long>> currOffset;
    // the index file cache, map of msgId-offset pairs
    private ConcurrentHashMap<String, Long> indexCache;
    // a index record, include a msgId and a offset
    private static int INDEX_RECORD_lENGTH = 16;

    private static FileManager instance = null;
    private static Object lock = new Object();

    private FileManager(){
        outputStreamCache = new FileStreamCache<>(partitionCount);
        indexWriterCache = new FileStreamCache<>(partitionCount);
        inputStreamCache = new FileStreamCache<>(partitionCount);
        currOffset = new ConcurrentHashMap<>();
        indexCache = new ConcurrentHashMap<>();

        baseFolder = System.getProperty("user.home") + File.separator + "store";
        //baseFolder = "store";
        File file = new File(baseFolder);
        if(!file.exists()){
            file.mkdir();  // 目录不存在,则建立新目录
        }

        baseFolder += File.separator;

        /*
        if(!recoverOffset()){
            System.out.println("data recover failed\n");
        }*/
    }

    public FileManager(String baseFolder, int partitionCount, int maxMessageLimit) throws Exception{
        this.baseFolder = baseFolder;
        this.partitionCount = partitionCount;
        outputStreamCache = new FileStreamCache<RandomAccessFile>(partitionCount);
        indexWriterCache = new FileStreamCache<RandomAccessFile>(partitionCount);
        inputStreamCache = new FileStreamCache<RandomAccessFile>(partitionCount);
        currOffset = new ConcurrentHashMap<>();
        indexCache = new ConcurrentHashMap<>();
        // init the all count in msgCount to be maxMessageLimit, will create new file for all topic
        // recover the currOffset from the index file
        /*if (!recoverOffset()) {
            throw new Exception("recover offset failure");
        }*/
    }




    /**
     * 单例模式,全局唯一实例
     * @return
     */
    public static FileManager getInstance(){
        if(instance == null){
            synchronized (lock){
                if(instance == null){
                    instance = new FileManager();
                }
            }
        }

        return instance;
    }

    /**
     * 写一条消息到存储系统
     * @param msg
     * @return
     */
    public boolean writeMessage(Message msg) {
        String topic = msg.getTopic();
        String msgId = msg.getMsgId();
        UUID uuid = UUID.fromString(msgId);
        int hashcode = hasCode(uuid);
        //long msgId = Long.parseLong(msg.getMsgId(), 16); // 按16进制解析
        int part = hashcode % partitionCount;

        if (!currOffset.containsKey(topic)) {
            synchronized (currOffset) {
                initOffset(topic);
            }
        }

        // get the index file writer
        RandomAccessFile indexWriter = getIndexFileWriter(topic, hashcode);
        if (indexWriter == null) {
            return false;
        }
        byte[] data = SerializationUtil.serializer(msg);
        synchronized (indexWriter) {
            // get the file output stream
            RandomAccessFile raf = getFileOutputStream(topic, hashcode);
            if (raf == null) {
                return false;
            }
            synchronized (raf) {
                // lock the current output file and index file
                try {
                    // write the message first and then update the index file
                    raf.writeInt(data.length);
                    raf.write(data);
                    raf.getFD().sync();
                    long offset = currOffset.get(topic).get(part);
                    indexWriter.writeLong(uuid.getLeastSignificantBits());
                    indexWriter.writeLong(uuid.getMostSignificantBits());
                    indexWriter.writeLong(offset);
                    indexWriter.getFD().sync();
                    currOffset.get(topic).set(part, offset + data.length + 4);
                    indexCache.put(msgId, offset);
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
     * 从存储系统读一条消息
     * @param msgId
     * @param topic
     * @return
     */
    public Message readMessage(String msgId, String topic) {
        Message msg = null;
        UUID uuid = UUID.fromString(msgId);
        int hashcode = hasCode(uuid);
        //long msgId = Long.parseLong(messageId, 16);  //按16进制解析
        // get the index file reader
        if (!indexCache.containsKey(msgId)) {
            return null;
        }
        long offset = indexCache.get(msgId);

        // get the input file stream
        String fileName = getRecordFileName(topic, hashcode, offset);
        if (fileName == null) {
            return null;
        }
        RandomAccessFile inputStream = getBufferedInputStream(topic, hashcode, fileName);
        try {
            long fileOffset = Long.parseLong(fileName);
            inputStream.seek((int) (offset - fileOffset));
            // read the message len
            int dataLength = inputStream.readInt();
            byte[] data = new byte[dataLength];
            inputStream.read(data, 0, dataLength);
            msg = (Message) SerializationUtil.deserializer(data, Message.class);
        } catch (IOException e) {
            // read message date failure
            msg = null;
        }
        return msg;
    }

    public void removeMessage(String msgId) {
        indexCache.remove(msgId);
    }



    private boolean recoverOffset() {
        File baseDir = new File(baseFolder);
        String[] fileNames = baseDir.list();
        for (String dirName : fileNames) {
            String topic = dirName.substring(0, dirName.length() - 1);
            int count = Integer.parseInt(dirName.charAt(dirName.length() - 1) + "");
            File indexFile = new File(baseFolder + dirName + File.separator + "index");
            if (!indexFile.exists()) {
                continue;
            }
            try {
                RandomAccessFile raf = new RandomAccessFile(indexFile, "r");
                // skip to the last index record
                raf.seek(raf.length() - INDEX_RECORD_lENGTH);
                if (!currOffset.containsKey(topic)) {
                    initOffset(topic);
                }
                // skip the last msgId
                raf.readLong();
                currOffset.get(topic).set(count, raf.readLong());
                raf.close();
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * uuid的哈希值
     * @param uuid
     * @return
     */
    private int hasCode(UUID uuid){
        return uuid.hashCode() & 0x7FFFFFFF;
    }

    private byte[] intToBytes(int num) {
        byte[] b = new byte[4];
        b[0] = (byte) (num & 0xff);
        b[1] = (byte) ((num >> 8) & 0xff);
        b[2] = (byte) ((num >> 16) & 0xff);
        b[3] = (byte) (num >>> 24);
        return b;
    }

    private int bytesToInt(byte[] b) {
        int num = (b[0] & 0xff) | ((b[1] << 8) & 0xff00) | ((b[2] << 16) & 0xff0000) | (b[3] << 24);
        return num;
    }

    private void initOffset(String topic) {
        ArrayList<Long> arrayList = new ArrayList<Long>(partitionCount);
        for (int i = 0; i < partitionCount; i++) {
            arrayList.add(new Long(0));
        }
        currOffset.put(topic, arrayList);
    }

    private boolean createNewRecordFile(String topic, long msgId) {
        RandomAccessFile outputStream = outputStreamCache.getFileSteam(topic, msgId);
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                // close record file failure, do nothing
            }
            // create a new record file and set the cache null
            // next time when getFileOutputStream is invoked, a stream connect with the new file will be set into the cache
            outputStreamCache.addFileStream(topic, msgId, null);
        }
        int cnt = (int) (msgId % partitionCount);
        File recordFile = new File(baseFolder + topic + cnt + File.separator + String.format("%016x", currOffset.get(topic).get(cnt)));
        try {
            recordFile.createNewFile();
        } catch (IOException e) {
            // create new file failure
            return false;
        }
        return true;
    }

    private RandomAccessFile getFileOutputStream(String topic, long msgId) {
        // check the current record file have reach the maxMessageLimit
        /*int cnt = (int) (msgId % partitionCount);
        if (msgCount.get(topic).get(cnt) >= maxMessageLimit) {
            if (!createNewRecordFile(topic, msgId)) {
                return null;
            }
            msgCount.get(topic).set(cnt, 0);
        }*/
        RandomAccessFile fStream = outputStreamCache.getFileSteam(topic, msgId);
        if (fStream != null) {
            return fStream;
        }
        File folder = new File(baseFolder + topic + "0");
        if (!folder.isDirectory()) {
            createFoldersForTopic(topic);
        }
        String[] fileNames = folder.list();
        Arrays.sort(fileNames);
        // the last is the index file
        String name = fileNames[fileNames.length - 2];
        try {
            String fileName = baseFolder + topic + (msgId % partitionCount) + File.separator + name;
            fStream = new RandomAccessFile(fileName, "rw");
            // append the file
            //fStream.seek(fStream.length());
            outputStreamCache.addFileStream(topic, msgId, fStream);
        } catch (Exception e) {
            // file not found
            fStream = null;
        }
        return fStream;
    }

    private RandomAccessFile getIndexFileWriter(String topic, long msgId) {
        RandomAccessFile raf = indexWriterCache.getFileSteam(topic, msgId);
        if (raf != null) {
            return raf;
        }
        File folder = new File(baseFolder + topic + "0");
        if (!folder.isDirectory()) {
            createFoldersForTopic(topic);
        }
        try {
            String fileName = baseFolder + topic + (msgId % partitionCount) + File.separator + "index";
            raf = new RandomAccessFile(fileName, "rw");
            // append the index file
            //raf.seek(raf.length());
            indexWriterCache.addFileStream(topic, msgId, raf);
        } catch (IOException e) {
            raf = null;
        }
        return raf;
    }

    private void createFoldersForTopic(String topic) {
        File folder;
        File index;
        File file;
        for (int i = 0; i < partitionCount; i++) {
            folder = new File(baseFolder + topic + i);
            folder.mkdir();
            index = new File(baseFolder + topic + i + File.separator + "index");
            file = new File(baseFolder + topic + i + File.separator + String.format("%016x", new Long(0)));
            try {
                index.createNewFile();
                file.createNewFile();
            } catch (Exception e) {
                // create new file failure
            }
        }
    }

    private String getRecordFileName(String topic, long msgId, long offset) {
        File folder = new File(baseFolder + topic + "0");
        if (!folder.isDirectory()) {
            return null;
        }
        String[] fileNames = folder.list();
        Arrays.sort(fileNames);
        String offsetStr = String.format("%016x", offset);
        String name = null;
        for (int i = 0; i < fileNames.length; i++) {
            if (offsetStr.compareTo(fileNames[i]) < 0) {
                name = fileNames[i - 1].toString();
                break;
            }
        }
        return name;
    }

    private RandomAccessFile getBufferedInputStream(String topic, long msgId, String fileName) {
        RandomAccessFile inputStream = inputStreamCache.getFileSteam(topic, msgId);
        if (inputStream != null) {
            return inputStream;
        }
        //RandomAccessFile inputStream = null;
        try {
            String name = baseFolder + topic + (msgId % partitionCount) + File.separator + fileName;
            inputStream = new RandomAccessFile(name, "r");
            inputStreamCache.addFileStream(topic, msgId, inputStream);
        } catch (FileNotFoundException e) {
            inputStream = null;
        }
        return inputStream;
    }

    /*
    public static void main(String[] args) {
        FileManager manager = null;
        Random random = new Random(System.currentTimeMillis());
        int testWrite = 1000;
        int testRead = 100;
        int cnt = 0;
        String[] topicRead = new String[testRead];
        String[] msgIdRead = new String[testRead];
        try {
            manager = new FileManager("F:\\storage\\", 4, 50);
        } catch (Exception e) {
            // init the manager error
            e.printStackTrace();
        }

        Message msg;
        String[] topics = {"topic1", "topic2", "topic3"};
        String[] bodies = {"body1", "body2", "body3"};
        for (int i = 0; i < testWrite; i++) {
            msg = new Message();
            Long msgId = Math.abs(random.nextLong());
            int tmp = Math.abs(random.nextInt());
            //msgId = new Long(0);
            //tmp = 0;
            msg.setTopic(topics[tmp % topics.length]);
            msg.setBody(bodies[tmp % topics.length].getBytes());
            msg.setMsgId(String.format("%016x", msgId));
            if ((i > 10 && i <= 60) || (i > 300 && i <=350)) {
                topicRead[cnt] = msg.getTopic();
                msgIdRead[cnt] = msg.getMsgId();
                cnt++;
            }
            // System.out.println(i + "\n");
            if  (!manager.writeMessage(msg)) {
                System.out.println("write message error: " + i + "\n");
                System.out.println((tmp % topics.length) + "\n");
                return;
            }
        }
        for (int i = 0; i < testRead; i++) {
            //topicRead[0] += "s";
            msg = manager.readMessage(topicRead[i], msgIdRead[i]);
            if (msg == null || !topicRead[i].equals(msg.getTopic()) || !msg.getMsgId().equals(msgIdRead[i])) {
                System.out.println("read error");
                return;
            }
        }
        System.out.println("success");
    }*/
}
