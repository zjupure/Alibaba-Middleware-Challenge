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
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * encode the MomMessage to serialized data
 *
 * protocol
 * |--- 4 bytes ---| --- 1 byte ---| --- content --- |
 * 0x00 : subscription  0x01: message  0x02: provider result  0x03: consumer result
 *
 */
public class MomEncoder extends MessageToByteEncoder{

    @Override
    protected void encode(ChannelHandlerContext ctx, Object obj, ByteBuf out) throws Exception {
        byte[]  data;
        byte type = 0;

        if(obj instanceof ConsumerSubscription){
            type = 0x00;
        }else if(obj instanceof Message){
            type = 0x01;
        }else if(obj instanceof SendResult){
            type = 0x02;
        }else if(obj instanceof ConsumeResult){
            type = 0x03;
        }else{
            type = 0x05;
        }

        data = SerializationUtil.serializer(obj);

        int len = data.length + 1;  // subscription length + 1 byte (type indicate)
        out.writeInt(len);
        out.writeByte(type);
        out.writeBytes(data);
    }
}
