package com.gbti.probe;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
//? if >=1.20.5 {
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.GameProtocols;
//?} else {
/*import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
*///?}

import java.util.ArrayList;
import java.util.List;

/**
 * Encodes every packet the server sends a mock player with the game's own codec, as a real
 * connection does on its way to a client. The mock channel has no encoder, so without this a packet
 * no client could receive (a broken codec rule, a book or dialog too large) would pass unnoticed.
 */
final class PacketCheck {
    // The most a client accepts once a packet is decompressed
    static final int MAX_BYTES = 8 * 1024 * 1024;
    static final List<String> FAILURES = new ArrayList<>();
    static int encoded;
    static int largest;
    static String largestName = "";
    //? if >=1.20.5 {
    @SuppressWarnings("rawtypes")
    private static StreamCodec codec;
    //?}

    private PacketCheck() {
    }

    /** Sits on a mock player's channel and encodes each packet as it is written. */
    static final class Encoder extends ChannelOutboundHandlerAdapter {
        private final MinecraftServer server;

        Encoder(MinecraftServer server) {
            this.server = server;
        }

        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (message instanceof Packet<?> packet) encode(server, packet);
            super.write(context, message, promise);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static void encode(MinecraftServer server, Packet<?> packet) {
        // The real pipeline splits a bundle into its packets before encoding them
        if (packet instanceof BundlePacket<?> bundle) {
            for (Packet<?> part : bundle.subPackets()) encode(server, part);
            return;
        }
        ByteBuf buffer = Unpooled.buffer();
        String name = packet.getClass().getSimpleName();
        try {
            //? if >=1.20.5 {
            if (codec == null) {
                codec = GameProtocols.CLIENTBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(server.registryAccess())).codec();
            }
            codec.encode(buffer, packet);
            //?} else if >=1.20.2 {
            /*int id = ConnectionProtocol.PLAY.codec(PacketFlow.CLIENTBOUND).packetId(packet);
            if (id < 0) throw new IllegalStateException("not a play packet");
            FriendlyByteBuf out = new FriendlyByteBuf(buffer);
            out.writeVarInt(id);
            packet.write(out);
            *///?} else {
            /*int id = ConnectionProtocol.PLAY.getPacketId(PacketFlow.CLIENTBOUND, packet);
            if (id < 0) throw new IllegalStateException("not a play packet");
            FriendlyByteBuf out = new FriendlyByteBuf(buffer);
            out.writeVarInt(id);
            packet.write(out);
            *///?}
            encoded++;
            int bytes = buffer.readableBytes();
            if (bytes > largest) {
                largest = bytes;
                largestName = name;
            }
            if (bytes > MAX_BYTES) FAILURES.add(name + " is " + bytes + " bytes");
        } catch (Throwable t) {
            FAILURES.add(name + ": " + t);
        } finally {
            buffer.release();
        }
    }

    static String summary() {
        return encoded + " packets encoded, the largest " + largest + " bytes (" + largestName + "), "
                + FAILURES.size() + " failed" + (FAILURES.isEmpty() ? "" : ": " + String.join("; ", FAILURES.subList(0, Math.min(3, FAILURES.size()))));
    }
}
