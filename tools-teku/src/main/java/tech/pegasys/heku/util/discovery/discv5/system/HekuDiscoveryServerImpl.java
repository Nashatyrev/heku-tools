/*
 * SPDX-License-Identifier: Apache-2.0
 */

package tech.pegasys.heku.util.discovery.discv5.system;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.beacon.discovery.network.DatagramToEnvelope;
import org.ethereum.beacon.discovery.network.IncomingMessageSink;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer;
import org.ethereum.beacon.discovery.network.NettyDiscoveryServerImpl;
import org.ethereum.beacon.discovery.pipeline.Envelope;
import org.reactivestreams.Publisher;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.ReplayProcessor;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Copy of NettyDiscoveryServerImpl from discovery lib
 * with ability to append additional {@link ChannelHandler}
 *
 * @see #addHandler(ChannelHandler)
 */
public class HekuDiscoveryServerImpl implements NettyDiscoveryServer {
  private static final Logger logger = LogManager.getLogger(NettyDiscoveryServerImpl.class);
  private static final int RECREATION_TIMEOUT = 5000;
  private final ReplayProcessor<Envelope> incomingPackets = ReplayProcessor.cacheLast();
  private final FluxSink<Envelope> incomingSink = incomingPackets.sink();
  private final InetSocketAddress listenAddress;
  private final int trafficReadLimit; // bytes per sec
  private AtomicBoolean listen = new AtomicBoolean(false);
  private Channel channel;
  private NioEventLoopGroup nioGroup;
  private final List<ChannelHandler> additionalHandlers = new ArrayList<>();

  public HekuDiscoveryServerImpl(InetSocketAddress listenAddress, final int trafficReadLimit) {
    this.listenAddress = listenAddress;
    this.trafficReadLimit = trafficReadLimit;
  }

  public void addHandler(ChannelHandler handler) {
    additionalHandlers.add(handler);
  }

  @Override
  public CompletableFuture<NioDatagramChannel> start() {
    logger.info("Starting discovery server on UDP port {}", listenAddress.getPort());
    if (!listen.compareAndSet(false, true)) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Attempted to start an already started server"));
    }
    nioGroup = new NioEventLoopGroup(1);
    return startServer(nioGroup);
  }

  private CompletableFuture<NioDatagramChannel> startServer(final NioEventLoopGroup group) {
    CompletableFuture<NioDatagramChannel> future = new CompletableFuture<>();
    Bootstrap b = new Bootstrap();
    b.group(group)
        .channel(NioDatagramChannel.class)
        .handler(
            new ChannelInitializer<NioDatagramChannel>() {
              @Override
              public void initChannel(NioDatagramChannel ch) {
                ChannelPipeline pipeline = ch.pipeline();
                additionalHandlers.forEach(pipeline::addFirst);
                pipeline
                    .addFirst(new LoggingHandler(LogLevel.TRACE))
                    .addLast(new DatagramToEnvelope())
                    .addLast(new IncomingMessageSink(incomingSink));

                if (trafficReadLimit != 0) {
                  pipeline.addFirst(new ChannelTrafficShapingHandler(0, trafficReadLimit));
                }
              }
            });

    final ChannelFuture bindFuture = b.bind(listenAddress);
    bindFuture.addListener(
        result -> {
          if (!result.isSuccess()) {
            future.completeExceptionally(result.cause());
            return;
          }

          this.channel = bindFuture.channel();
          channel
              .closeFuture()
              .addListener(
                  closeFuture -> {
                    if (!listen.get()) {
                      logger.info("Shutting down discovery server");
                      group.shutdownGracefully();
                      return;
                    }
                    logger.error(
                        "Discovery server closed. Trying to restore after "
                            + RECREATION_TIMEOUT
                            + " milliseconds delay",
                        closeFuture.cause());
                    Thread.sleep(RECREATION_TIMEOUT);
                    startServer(group);
                  });
          future.complete((NioDatagramChannel) this.channel);
        });
    return future;
  }

  @Override
  public Publisher<Envelope> getIncomingPackets() {
    return incomingPackets;
  }

  @Override
  public void stop() {
    if (listen.compareAndSet(true, false)) {
      logger.info("Stopping discovery server");
      if (channel != null) {
        try {
          channel.close().sync();
        } catch (InterruptedException ex) {
          logger.error("Failed to stop discovery server", ex);
        }
        if (nioGroup != null) {
          try {
            nioGroup.shutdownGracefully().sync();
          } catch (InterruptedException ex) {
            logger.error("Failed to stop NIO group", ex);
          }
        }
      }
    } else {
      logger.warn("An attempt to stop already stopping/stopped discovery server");
    }
  }
}
