package com.alibaba.middleware.race.rpc.netty;

import com.alibaba.middleware.race.rpc.utility.SerializationUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * RPC Encoder for the TCP outputstream
 * Created by liuchun on 2015/7/24.
 *
 */
public class RpcEncoder extends MessageToByteEncoder{
    private Class<?> clazz;

    public RpcEncoder(Class<?> clazz){
        this.clazz = clazz;
    }


    @Override
    protected void encode(ChannelHandlerContext ctx, Object in, ByteBuf out) throws Exception {
        if(clazz.isInstance(in)){
            byte[] data = SerializationUtil.serializer(in);
            out.writeInt(data.length);
            out.writeBytes(data);
        }
    }
}
