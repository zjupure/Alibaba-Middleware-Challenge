package com.alibaba.middleware.race.rpc.utility;


import java.io.ByteArrayOutputStream;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Created by liuchun on 2015/8/4.
 */
public class CompressUtil {

    public static byte[] compress(byte[] data){
        byte[] output = new byte[0];

        Deflater compresser = new Deflater();

        compresser.reset();
        compresser.setInput(data);
        compresser.finish();

        ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length);
        try {
            byte[]  buf = new byte[1024];
            while(!compresser.finished()){
                int length = compresser.deflate(buf);
                bos.write(buf, 0, length);
            }
            output = bos.toByteArray();
            bos.close();
        }catch (Exception e){
            output = data;
            e.printStackTrace();
        }
        compresser.end();

        return output;
    };

    public static byte[] decompress(byte[] data){
        byte[] output = new byte[0];

        Inflater decompresser = new Inflater();
        decompresser.reset();
        decompresser.setInput(data);

        ByteArrayOutputStream bos = new ByteArrayOutputStream(2*data.length);
        try{
            byte[] buf = new byte[1024];
            while(!decompresser.finished()){
                int length = decompresser.inflate(buf);
                bos.write(buf, 0, length);
            }
            output = bos.toByteArray();
            bos.close();
        }catch (Exception e){
            output = data;
            e.printStackTrace();
        }

        decompresser.end();
        return output;
    }
















}
