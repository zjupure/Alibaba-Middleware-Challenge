# Alibaba-Middleware-Challenge
Alibaba Middleware Challenge, including RPC (Remote Procedure Call) and MOM (Message-Oriented Middleware) compent

RPC

RPC（Remote Procedure Call ）——远程过程调用，它是一种通过网络从远程计算机程序上请求服务，而不需要了解底层网络技术的协议。RPC协议假定某些传输协议的存在（如TCP或UDP），为通信程序之间携带信息数据。在OSI网络通信模型中，RPC跨越了传输层和应用层。RPC使得开发包括网络分布式多程序在内的应用程序更加容易。 框架——让编程人员便捷地使用框架所提供的功能。由于RPC的特性：聚焦于应用的分布式服务化开发，所以成为一个对开发人员无感知的接口代理，显然是RPC框架优秀的设计方式。

MOM

MOM（Message-Oriented Middleware）——面向消息的中间件，用于分布式系统中的异步通信，提供了点对点模式和发布订阅模式。和RPC的同步通信相比，使用消息中间件降低了系统之间的耦合。系统间的通信无需显式互相调用，只需要生产消息，消费消息，MOM充当系统间的可靠通信媒介。通过消息中间价提供的异步通信机制，分布式系统的可扩展性、性能和可用性会有进一步的提升。