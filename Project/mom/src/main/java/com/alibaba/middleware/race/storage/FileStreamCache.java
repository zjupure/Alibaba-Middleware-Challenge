package com.alibaba.middleware.race.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by wyd on 2015/8/6.
 */
public class FileStreamCache<T> {
    private Map<String, ArrayList<T>> cache;
    private int arrayLength;

    public FileStreamCache(int arrayLength) {
        this.arrayLength = arrayLength;
        cache = new HashMap<>();
    }

    /**
     * cache a file stream for (topic, msgId)
     * return the odd stream for (topic, msgId) or null for haven't been set
     * @param topic
     * @param msgId
     * @param stream
     */
    public void addFileStream(String topic, long msgId, T stream) {
        int count = (int) (msgId % arrayLength);
        if (cache.containsKey(topic)) {
            cache.get(topic).set(count, stream);
        } else {
            ArrayList<T> arrayList = new ArrayList<>();
            for (int i = 0; i < arrayLength; i++) {
                arrayList.add(null);
            }
            cache.put(topic, arrayList);
            cache.get(topic).set(count, stream);
        }
    }

    public T getFileSteam(String topic, long msgId) {
        int count = (int) (msgId % arrayLength);
        if (cache.containsKey(topic)) {
            return cache.get(topic).get(count);
        }
        return null;
    }
}
