package org.tinyradius.server;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;
import org.tinyradius.dictionary.DefaultDictionary;
import org.tinyradius.dictionary.Dictionary;
import org.tinyradius.packet.AccessRequest;
import org.tinyradius.packet.RadiusPacket;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DeduplicatorHandlerTest {

    private Dictionary dictionary = DefaultDictionary.INSTANCE;

    @Test
    void handlePacket() throws InterruptedException {
        final AtomicInteger id = new AtomicInteger();
        final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);
        final NioDatagramChannel datagramChannel = new NioDatagramChannel();
        eventLoopGroup.register(datagramChannel).syncUninterruptibly();

        final DeduplicatorHandler<RadiusPacket> deduplicatorHandler = new DeduplicatorHandler<>(
                (channel, packet, remoteAddress, sharedSecret) -> {
                    final Promise<RadiusPacket> promise = channel.eventLoop().newPromise();
                    promise.trySuccess(new RadiusPacket(DefaultDictionary.INSTANCE, 2, id.getAndIncrement()));
                    return promise;
                },
                new HashedWheelTimer(), 1000);

        final RadiusPacket request = new AccessRequest(dictionary, 100, null).encodeRequestPacket("test");

        // response id 0
        assertEquals(0, deduplicatorHandler
                .handlePacket(datagramChannel, request, null, null)
                .syncUninterruptibly().getNow()
                .getPacketIdentifier());

        // duplicate - return null
        assertNull(deduplicatorHandler
                .handlePacket(datagramChannel, request, null, null)
                .syncUninterruptibly().getNow());

        // wait for cache to timeout
        Thread.sleep(1500);

        // response id 1
        assertEquals(1, deduplicatorHandler
                .handlePacket(datagramChannel, request, null, null)
                .syncUninterruptibly().getNow()
                .getPacketIdentifier());

        // duplicate - return null
        assertNull(deduplicatorHandler
                .handlePacket(datagramChannel, request, null, null)
                .syncUninterruptibly().getNow());
    }
}