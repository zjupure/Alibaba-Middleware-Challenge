package com.alibaba.middleware.race.netty;

/**
 * Created by wyd on 2015/8/2.
 */

import com.alibaba.middleware.race.mom.ConsumeResult;
import com.alibaba.middleware.race.mom.ConsumerSubscription;
import com.alibaba.middleware.race.mom.Message;
import com.alibaba.middleware.race.mom.SendResult;
import com.alibaba.middleware.race.utility.SerializationUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * decode the MomMessage from the serialized data
 */
public class MomDecoder extends ByteToMessageDecoder{

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        //
        if(in.readableBytes() < 4)
            return;

        in.markReaderIndex();
        int dataLen = in.readInt();
        if(dataLen < 0){
            ctx.close();
        }

        if(in.readableBytes() < dataLen){
            in.resetReaderIndex();
            return;
        }

        byte type = in.readByte();
        byte[] data = new byte[dataLen-1];
        in.readBytes(data);

        Object obj = new Object();
        switch (type){
            case 0x00:
                obj = SerializationUtil.deserializer(data, ConsumerSubscription.class);
                break;
            case 0x01:
                obj = SerializationUtil.deserializer(data, Message.class);
                break;
            case 0x02:
                obj = SerializationUtil.deserializer(data, SendResult.class);
                break;
            case 0x03:
                obj = SerializationUtil.deserializer(data, ConsumeResult.class);
                break;
            default:break;
        }

        out.add(obj);
    }
}
