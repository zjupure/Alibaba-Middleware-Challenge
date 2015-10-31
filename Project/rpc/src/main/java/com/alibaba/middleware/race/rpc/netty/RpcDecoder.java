package com.alibaba.middleware.race.rpc.netty;

import com.alibaba.middleware.race.rpc.utility.SerializationUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * RPC decode from the TCP inputstream
 * Created by liuchun on 2015/7/24.
 *
 */
public class RpcDecoder extends ByteToMessageDecoder {
    private Class<?> clazz;

    public RpcDecoder(Class<?> clazz){
        this.clazz = clazz;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 经过LengthFieldBasedFrameDecoder解码的数据
        if(in.readableBytes() < 4){
            return;
        }

        in.markReaderIndex();
        int dataLen = in.readInt();  //长度
        if(dataLen < 0){
            ctx.close();
        }

        if(in.readableBytes() < dataLen){
            in.resetReaderIndex();
            return;
        }

        byte[] data = new byte[dataLen]; //缓存区
        in.readBytes(data);
        Object obj = SerializationUtil.deserializer(data, clazz);
        out.add(obj);
    }
}
