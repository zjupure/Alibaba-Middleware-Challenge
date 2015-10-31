2015/8/2  10:00  create by wyd
创建了  com.alibaba.middleware.race.netty  包，包括
            TcpClient : 处理 Consumer 到 broker 的连接
            MomMessage : 消息包装类，将 ConsumeResult, Message, SendResult 和 ConsumerSubscription (新增的用于传递订阅信息的类)
            MomDecoder : MomMessage 解码类 (decode未实现)
            MomEncoder : MomMessage 编码类 (encode未实现)

修改了 com.alibaba.middleware.race.mom 包，包括
            新增 ConsumerSubscription 类 : 用于 consumer 向 broker 传递订阅消息
            修改 DefaultConsumer 类 : 一个基本实现
            修改 ConsumeResult 类 : 添加 msgId 字段，标识成功处理的 message

关于 MomMessage 设计 :
        系统消息传递共有 :
            consumer -->  broker     订阅信息 (ConsumerSubscription 自定义的)
            consumer -->  broker     ConsumeResult
            broker   -->  consumer   Message
            producer -->  broker     Message
            broker   -->  producer   SendResult

        共有 4 中消息传递,包括 3 中已定义的和自定义的 ConsumerSubscription
        其中 MomMessage 包含 sender 和 msgType 两个字段标识消息来源和类型
        标识说明在 MomMessage 类中，初步的实现是两个 int 字段，没有使用 enum


2015/8/6  20:00  modified by zjupure

创建了 com.alibaba.middleware.race.broker包
            BrokerServer : broker服务器,启动bossGroup和workGroup
            BrokerServerHandler : broker服务端自定义的Handler,处理Provider和Consumer的业务

创建了 com.alibaba.middleware.race.model包
            ConsumerInfo : 消费者的信息,用groupId唯一标识,内含该group的topic、filter以及对应的Channel信息
            MessageInfo :  消息信息,用msgId唯一标识,含有topic, 该消息的订阅列表, 需投递总数,
                            未投递成功消费者个数, 未投递成功的消费者groupId列表等
            ConsumerManager : 总的信息管理中心, 含有4张映射表
                映射表：topic--->groupIdList,   订阅了该topic的groupId列表
                        groupId-->ConsumerInfo, 同属于一个groupId的topic, filter等属性,以及在线的channel数
                        channel-->groupId,  在线的channel对应的groupId
                        msgId-->MessageInfo, 该msgId对应的消息信息,需要重传的列表

修改了 com.alibaba.middleware.race.mom包中的类ConsumerSubscription、DefaultConsumer、DefaultProducer
            ConsumerSubscription : 合并key, value字段为一个filter字段
            ConsumeResult : 增加了groupId字段,标识反馈所属消费者
            DefaultConsumer : 默认只有一个client与broker进行连接,异步处理所有数据
            DefaultProducer : 采用连接池管理client,支持消息并发

修改了 com.alibaba.middleware.race.netty包,移除了类MomMessage和MomMessageListener,完善了类MomDecoder和MomEncoder
            拆分TCPClient为ConsumerClient和ProducerClient,新增ProducerClientPool.
            底层协议设计：
            ----------------------------------------------------------------
            |        length       |  type  |              body             |
            |        4  byte      |  1 byte|            variable           |
            ----------------------------------------------------------------
            length : int值,为body的字节数+1(type field)
            type = 0x00 : body为ConsumerSubscription
            type = 0x01 : body为Message
            type = 0x03 : body为SendResult
            type = 0x04 : body为ConsumeResult
            type = other : 保留,为以后扩展,如心跳

增加了 com.alibaba.middleware.race.utility包, 常用工具包
            SerializationUtil : 序列化工具,默认采用Kryo工具进行序列化,支持java原生序列化方式

2015/8/7  20:00  modified by zjupure

修改了 com.alibaba.middleware.race.model包的类ConsumerManager, 移除了映射表channel-->groupId，
            采用netty的channelGroup自动管理关联的channel,自动移除close的channel