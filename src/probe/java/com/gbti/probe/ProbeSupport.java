package com.gbti.probe;

import com.gbti.Journal;
import com.gbti.JournalBook;
import com.mojang.authlib.GameProfile;
//? if >=1.20.2 <1.20.5 {
/*import com.mojang.brigadier.exceptions.CommandSyntaxException;
*///?}
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.Connection;
//? if >=1.20.2 <1.20.5 {
/*import net.minecraft.network.ConnectionProtocol;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.2 {
import net.minecraft.server.network.CommonListenerCookie;
//?}
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** What every probe shares: mock players, commands, books, and the PASS/FAIL tally. */
final class ProbeSupport {
    static final Logger LOG = LogManager.getLogger("TJProbe");
    // Each mock player's channel, which holds every packet the server sent that player
    private static final Map<UUID, EmbeddedChannel> CHANNELS = new HashMap<>();

    private ProbeSupport() {
    }

    static ServerPlayer join(MinecraftServer server, String name) {
        // GameProfile became a record in later authlib versions, so its getter is not used
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        CHANNELS.put(uuid, channel);
        // From 1.20.2 to 1.20.4 a connection reads its protocol from the channel before sending
        //? if >=1.20.2 <1.20.5 {
        /*channel.attr(Connection.ATTRIBUTE_SERVERBOUND_PROTOCOL).set(ConnectionProtocol.PLAY.codec(PacketFlow.SERVERBOUND));
        channel.attr(Connection.ATTRIBUTE_CLIENTBOUND_PROTOCOL).set(ConnectionProtocol.PLAY.codec(PacketFlow.CLIENTBOUND));
        *///?}
        // 1.20.2 added the login cookie, and 1.20.5 its transfer flag
        //? if >=1.20.5 {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        //?} else if >=1.20.2 {
        /*CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile);
        *///?}
        //? if >=1.20.2 {
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, cookie.clientInformation());
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        //?} else {
        /*ServerPlayer player = new ServerPlayer(server, server.overworld(), profile);
        server.getPlayerList().placeNewPlayer(connection, player);
        *///?}
        return player;
    }

    // Logs out (which saves the player) and logs back in as a fresh entity loaded from disk
    static ServerPlayer rejoin(MinecraftServer server, ServerPlayer player, String name) {
        server.getPlayerList().remove(player);
        return join(server, name);
    }

    static EmbeddedChannel channel(ServerPlayer player) {
        return CHANNELS.get(player.getUUID());
    }

    static void command(MinecraftServer server, CommandSourceStack source, String command) {
        // performPrefixedCommand changed its return type in 1.20.3, so the probe for the jar that
        // also covers 1.20.2 runs commands through Brigadier and reports a syntax error as the game does
        //? if >=1.20.2 <1.20.5 {
        /*try {
            server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal(e.getMessage()));
        }
        *///?} else {
        server.getCommands().performPrefixedCommand(source, command);
        //?}
    }

    // What a command says back to the console, success and failure alike
    static String said(MinecraftServer server, String command) {
        return said(server, server.createCommandSourceStack(), command);
    }

    static String said(MinecraftServer server, CommandSourceStack source, String command) {
        StringBuilder said = new StringBuilder();
        command(server, source.withSource(new CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.append(message.getString()).append('\n');
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        }), command);
        return said.toString();
    }

    // The mock players join the overworld. Entity.level() is not used: its intermediary name
    // changed in 1.21.9, so a probe built for 1.21.5 could not call it there.
    static void use(MinecraftServer server, ServerPlayer player, InteractionHand hand) {
        player.gameMode.useItem(player, server.overworld(), player.getItemInHand(hand), hand);
    }

    // Copies of one journal a player holds, counting books, not slots: identical journals stack
    static int copies(ServerPlayer player, Journal journal) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (journal != null && JournalBook.journalOf(stack) == journal) count += stack.getCount();
        }
        return count;
    }

    static ItemStack firstCopy(ServerPlayer player, Journal journal) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (journal != null && JournalBook.journalOf(stack) == journal) return stack;
        }
        return ItemStack.EMPTY;
    }

    static String text(ItemStack book) {
        JournalBook.Contents contents = JournalBook.contents(book);
        if (contents == null) return "";
        StringBuilder text = new StringBuilder();
        for (Component page : contents.pages()) {
            text.append(page.getString()).append('\n');
        }
        return text.toString();
    }

    static String titleOf(ItemStack book) {
        JournalBook.Contents contents = JournalBook.contents(book);
        return contents == null ? "" : contents.title();
    }

    static String oneLine(String text) {
        return text.replace("\n", "\\n");
    }

    /** The PASS/FAIL tally one probe run keeps. Each check logs one line. */
    static final class Checks {
        int passed;
        int failed;

        void check(String name, boolean ok, String detail) {
            if (ok) {
                passed++;
                LOG.info("[TJ-PROBE] PASS {}", name);
            } else {
                failed++;
                LOG.info("[TJ-PROBE] FAIL {}: {}", name, detail);
            }
        }

        void guarded(Step step) {
            try {
                step.run();
            } catch (Throwable t) {
                failed++;
                LOG.info("[TJ-PROBE] FAIL threw: {}", t.toString());
                LOG.error("[TJ-PROBE] stack trace", t);
            }
        }

        void done() {
            LOG.info("[TJ-PROBE] DONE pass={} fail={}", passed, failed);
        }
    }

    interface Step {
        void run() throws Exception;
    }
}
