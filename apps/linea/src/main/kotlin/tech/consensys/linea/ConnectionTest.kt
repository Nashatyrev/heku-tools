package tech.consensys.linea

import io.netty.bootstrap.Bootstrap
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramPacket
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

fun main() {
//    println("===== Testing TCP: ")
//    ConnectionTest().runTcp()

    println()
    println("===== Testing UDP: ")
    ConnectionTest().runUdp()
}

class ConnectionTest {
    val ip = "10.150.1.122"
//    val ip = "127.0.0.1"
    val port = 9004

    fun runTcp() {
        println("Starting server...")
        ServerBootstrap()
            .group(NioEventLoopGroup())
            .channel(NioServerSocketChannel::class.java)
//            .childHandler(LoggingHandler("server", LogLevel.ERROR))
            .childHandler(object : ChannelInboundHandlerAdapter() {
                override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                    println("ctx = [${ctx}], cause = [${cause}]")
                    super.exceptionCaught(ctx, cause)
                }

                override fun channelRegistered(ctx: ChannelHandlerContext?) {
                    println("ctx = [${ctx}]")
                    super.channelRegistered(ctx)
                }
            })
            .bind(ip, port)
            .syncUninterruptibly()
            .get()

        println("Connecting to server...")
        Bootstrap()
            .group(NioEventLoopGroup())
            .channel(NioSocketChannel::class.java)
            .handler(LoggingHandler("client", LogLevel.ERROR))
            .connect(ip, port)
            .syncUninterruptibly()
            .get()

        println("Done")
    }

    fun runUdp() {
        println("Starting server...")
        Bootstrap()
            .group(NioEventLoopGroup())
            .channel(NioDatagramChannel::class.java)
//            .childHandler(LoggingHandler("server", LogLevel.ERROR))
            .handler(object : ChannelInboundHandlerAdapter() {
                override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
                    println("ctx = [${ctx}], cause = [${cause}]")
                    super.exceptionCaught(ctx, cause)
                }

                override fun channelRegistered(ctx: ChannelHandlerContext?) {
                    println("channelRegistered: ctx = [${ctx}]")
                    super.channelRegistered(ctx)
                }

                override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
                    println("channelRead: ctx = [${ctx}], msg = [${msg}]")
                    super.channelRead(ctx, msg)
                }
            })
            .bind(ip, port)
            .syncUninterruptibly()
            .get()

        Thread.sleep(1000)

        println("Connecting to server...")
        val socket = DatagramSocket()
        val p = java.net.DatagramPacket(ByteArray(10), 10, InetAddress.getByName(ip), port)
        socket.send(p)

//        val channel = Bootstrap()
//            .group(NioEventLoopGroup())
//            .channel(NioDatagramChannel::class.java)
//            .handler(LoggingHandler("server", LogLevel.ERROR))
//            .bind(port + 1)
//            .syncUninterruptibly()
//            .channel()
//
//        val destAddr = InetSocketAddress.createUnresolved(ip, port)
//        val packet = DatagramPacket(Unpooled.copiedBuffer(ByteArray(10)), destAddr)
//        channel.writeAndFlush(packet)

        println("Done")
    }
}