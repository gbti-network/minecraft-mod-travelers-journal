package com.gbti.probe;

import com.gbti.Journal;
import com.gbti.JournalBook;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
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
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        CHANNELS.put(uuid, new EmbeddedChannel(connection));
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
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
        server.getCommands().performPrefixedCommand(source, command);
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
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) return "";
        StringBuilder text = new StringBuilder();
        for (Filterable<Component> page : content.pages()) {
            text.append(page.raw().getString()).append('\n');
        }
        return text.toString();
    }

    static String titleOf(ItemStack book) {
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content == null ? "" : content.title().raw();
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
