package com.gbti.probe;

import com.gbti.FileManager;
import com.gbti.JournalBook;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Test mod for the boot harness, never shipped. Loaded beside the packaged Traveler's Journal jar
 * on a throwaway server, it joins mock players the way a real login does and checks what a player
 * would get. Each check logs "[TJ-PROBE] PASS name" or "[TJ-PROBE] FAIL name: detail", and the run
 * ends with "[TJ-PROBE] DONE pass=N fail=M".
 */
public class JournalProbe implements ModInitializer {
    private static final Logger LOG = LogManager.getLogger("TJProbe");
    private static final Path CONTENT = Path.of("config/travelers-journal/travelers_journal.md");
    private static final Path JOIN_LIST = Path.of("config/travelers-journal/joinlist.txt");
    // The mock player joins once the server is ticking and the force-loaded spawn area has loaded
    // (a join from inside SERVER_STARTED can block forever waiting for entities to load). The
    // checks run once its own chunks have loaded, well short of the 15 s keep-alive timeout.
    private static final int JOIN_TICKS = 40;
    private static final int SETTLE_TICKS = 60;

    private static final List<String> JOURNAL = List.of(
            "**Bold** *Italic* _Under_ <red>Red<> \"Quoted\" it's",
            "- Bullet",
            "\\*Stars\\* and \\\\ slash",
            "",
            "[page]",
            "Page two",
            "Line 1", "Line 2", "Line 3", "Line 4", "Line 5", "Line 6", "Line 7",
            "Line 8", "Line 9", "Line 10", "Line 11", "Line 12", "Line 13", "Line 14");
    private static final String PAGE_ONE = "Bold Italic Under Red \"Quoted\" it's\n・ Bullet\n*Stars* and \\ slash\n";
    private static final String UPDATED = "Updated after reload";

    private int passed;
    private int failed;
    private int joinCountdown = -1;
    private int checkCountdown = -1;
    private ServerPlayer alice;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOG.info("[TJ-PROBE] START");
            command(server, server.createCommandSourceStack(), "forceload add -32 -32 31 31");
            joinCountdown = JOIN_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (joinCountdown > 0 && --joinCountdown == 0) {
                guarded(() -> join(server));
                checkCountdown = SETTLE_TICKS;
            } else if (checkCountdown > 0 && --checkCountdown == 0) {
                guarded(() -> settled(server));
                LOG.info("[TJ-PROBE] DONE pass={} fail={}", passed, failed);
            }
        });
    }

    private void join(MinecraftServer server) throws Exception {
        // Reload takes the journal text from disk
        Files.writeString(CONTENT, String.join("\n", JOURNAL) + "\n", StandardCharsets.UTF_8);
        command(server, server.createCommandSourceStack(), "travelers_journal reload");
        check("reload", FileManager.lines.equals(JOURNAL), "lines loaded: " + FileManager.lines.size());

        alice = join(server, "ProbeAlice");
    }

    private void settled(MinecraftServer server) throws Exception {
        CommandSourceStack console = server.createCommandSourceStack();

        // A new player receives the journal once, with the right text and formatting
        check("join-gives-one", journals(alice) == 1, "journals after first join: " + journals(alice));
        WrittenBookContent content = firstJournal(alice).get(DataComponents.WRITTEN_BOOK_CONTENT);
        check("book-content", content != null, "no written book content");
        if (content == null) return;
        check("title-author", content.title().raw().equals("Traveler's Journal") && content.author().equals("Server"),
                content.title().raw() + " by " + content.author());
        check("page-count", content.pages().size() == 3, "pages: " + content.pages().size());
        Component page = content.pages().get(0).raw();
        check("page-text", page.getString().equals(PAGE_ONE), "page 1 reads: " + page.getString().replace("\n", "\\n"));
        check("bold", styleOf(page, "Bold").isBold() && !styleOf(page, "Bold").isItalic(), styleOf(page, "Bold").toString());
        check("italic", styleOf(page, "Italic").isItalic() && !styleOf(page, "Italic").isBold(), styleOf(page, "Italic").toString());
        check("underline", styleOf(page, "Under").isUnderlined(), styleOf(page, "Under").toString());
        check("colour", colour(page, "Red", ChatFormatting.RED), styleOf(page, "Red").toString());
        check("default-black", colour(page, "Bold", ChatFormatting.BLACK), styleOf(page, "Bold").toString());
        check("join-list", FileManager.joinList.contains(alice.getStringUUID())
                && Files.readString(JOIN_LIST).contains(alice.getStringUUID()), "uuid missing from the join list");

        // Rejoining gives no second copy, even with the first one gone
        alice.getInventory().clearContent();
        alice = rejoin(server, alice, "ProbeAlice");
        check("rejoin-gives-none", journals(alice) == 0, "journals after rejoin: " + journals(alice));

        // Only operators can use the command
        CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot().getChild("travelers_journal");
        check("op-allowed", node != null && node.canUse(console), "console cannot use the command");
        check("player-refused", node != null && !node.canUse(alice.createCommandSourceStack()), "a player can use the command");
        command(server, alice.createCommandSourceStack(), "travelers_journal give ProbeAlice");
        check("player-give-refused", journals(alice) == 0, "journals: " + journals(alice));
        command(server, console, "travelers_journal give ProbeAlice");
        check("console-give", journals(alice) == 1, "journals: " + journals(alice));

        // Using the journal swaps in the current text
        ItemStack old = firstJournal(alice).copy();
        Files.writeString(CONTENT, String.join("\n", JOURNAL) + "\n" + UPDATED + "\n", StandardCharsets.UTF_8);
        command(server, console, "travelers_journal reload");
        alice.getInventory().clearContent();
        alice.setItemInHand(InteractionHand.MAIN_HAND, old.copy());
        use(server, alice, InteractionHand.MAIN_HAND);
        check("use-refreshes", text(alice.getItemInHand(InteractionHand.MAIN_HAND)).contains(UPDATED),
                "main hand reads: " + text(alice.getItemInHand(InteractionHand.MAIN_HAND)).replace("\n", "\\n"));

        // ...from the offhand too, leaving the selected hotbar slot alone
        alice.getInventory().clearContent();
        alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 7));
        alice.setItemInHand(InteractionHand.OFF_HAND, old.copy());
        use(server, alice, InteractionHand.OFF_HAND);
        ItemStack main = alice.getItemInHand(InteractionHand.MAIN_HAND);
        check("offhand-refreshes", text(alice.getItemInHand(InteractionHand.OFF_HAND)).contains(UPDATED), "offhand not refreshed");
        check("offhand-keeps-hotbar", main.getItem() == Items.STONE && main.getCount() == 7, "main hand now: " + main);

        // A journal under a legacy title is recognised and replaced
        ItemStack legacy = new ItemStack(Items.WRITTEN_BOOK);
        legacy.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Travelers Journal"),
                "Someone", 0, List.of(Filterable.passThrough(Component.literal("old text"))), true));
        alice.getInventory().clearContent();
        alice.setItemInHand(InteractionHand.MAIN_HAND, legacy);
        use(server, alice, InteractionHand.MAIN_HAND);
        WrittenBookContent replaced = alice.getItemInHand(InteractionHand.MAIN_HAND).get(DataComponents.WRITTEN_BOOK_CONTENT);
        check("legacy-title", replaced != null && replaced.title().raw().equals("Traveler's Journal")
                && text(alice.getItemInHand(InteractionHand.MAIN_HAND)).contains(UPDATED), "legacy book not replaced");

        // A full inventory drops the journal at the player's feet, like /give
        alice.getInventory().clearContent();
        for (int i = 0; i < 36; i++) {
            alice.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        command(server, console, "travelers_journal give ProbeAlice");
        int dropped = server.overworld().getEntitiesOfClass(ItemEntity.class, alice.getBoundingBox().inflate(4),
                entity -> JournalBook.isJournal(entity.getItem())).size();
        check("full-inventory-drops", dropped == 1, "journals dropped: " + dropped);

        server.getPlayerList().remove(alice);
    }

    private static ServerPlayer join(MinecraftServer server, String name) {
        GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)), name);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer player = new ServerPlayer(server, server.overworld(), profile, cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    // Logs out (which saves the player) and logs back in as a fresh entity loaded from disk
    private static ServerPlayer rejoin(MinecraftServer server, ServerPlayer player, String name) {
        server.getPlayerList().remove(player);
        return join(server, name);
    }

    private static void command(MinecraftServer server, CommandSourceStack source, String command) {
        server.getCommands().performPrefixedCommand(source, command);
    }

    // The mock players join the overworld. Entity.level() is not used: its intermediary name
    // changed in 1.21.9, so a probe built for 1.21.5 could not call it there.
    private static void use(MinecraftServer server, ServerPlayer player, InteractionHand hand) {
        player.gameMode.useItem(player, server.overworld(), player.getItemInHand(hand), hand);
    }

    private static int journals(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (JournalBook.isJournal(player.getInventory().getItem(i))) count++;
        }
        return count;
    }

    private static ItemStack firstJournal(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (JournalBook.isJournal(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static String text(ItemStack book) {
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) return "";
        StringBuilder text = new StringBuilder();
        for (Filterable<Component> page : content.pages()) {
            text.append(page.raw().getString()).append('\n');
        }
        return text.toString();
    }

    private static Style styleOf(Component page, String run) {
        return page.visit((style, text) -> text.equals(run) ? Optional.of(style) : Optional.<Style>empty(), Style.EMPTY)
                .orElse(Style.EMPTY);
    }

    private static boolean colour(Component page, String run, ChatFormatting expected) {
        return Objects.equals(styleOf(page, run).getColor(), TextColor.fromLegacyFormat(expected));
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            LOG.info("[TJ-PROBE] PASS {}", name);
        } else {
            failed++;
            LOG.info("[TJ-PROBE] FAIL {}: {}", name, detail);
        }
    }

    private void guarded(Step step) {
        try {
            step.run();
        } catch (Throwable t) {
            failed++;
            LOG.info("[TJ-PROBE] FAIL threw: {}", t.toString());
            LOG.error("[TJ-PROBE] stack trace", t);
        }
    }

    private interface Step {
        void run() throws Exception;
    }
}
