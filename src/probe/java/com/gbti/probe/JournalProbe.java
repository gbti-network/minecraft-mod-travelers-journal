package com.gbti.probe;

import com.gbti.Journal;
import com.gbti.JournalBook;
import com.gbti.Journals;
import com.mojang.brigadier.tree.CommandNode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.gbti.probe.ProbeSupport.command;
import static com.gbti.probe.ProbeSupport.copies;
import static com.gbti.probe.ProbeSupport.firstCopy;
import static com.gbti.probe.ProbeSupport.join;
import static com.gbti.probe.ProbeSupport.oneLine;
import static com.gbti.probe.ProbeSupport.rejoin;
import static com.gbti.probe.ProbeSupport.said;
import static com.gbti.probe.ProbeSupport.text;
import static com.gbti.probe.ProbeSupport.use;

/**
 * Test mod for the boot harness, never shipped. Loaded beside the packaged Traveler's Journal jar
 * on a throwaway server, it joins mock players the way a real login does and checks what a player
 * would get. It starts with "[TJ-PROBE] START expect=N", logs "[TJ-PROBE] PASS name" or
 * "[TJ-PROBE] FAIL name: detail" for each check, and ends with "[TJ-PROBE] DONE pass=N fail=M".
 *
 * The checks run in phases a few seconds apart: the default journal (this class), then several
 * journals (JournalsProbe), then, on 1.21.6 and later, the in-game manager (DialogProbe).
 */
public class JournalProbe implements ModInitializer {
    private static final Path CONTENT = Path.of("config/travelers-journal/travelers_journal.md");
    private static final Path CONFIG = Path.of("config/travelers-journal/config.txt");
    private static final Path JOIN_LIST = Path.of("config/travelers-journal/joinlist.txt");
    private static final Path MODE = Path.of("config/travelers-journal-probe.txt");
    // The mock player joins once the server is ticking and the force-loaded spawn area has loaded
    // (a join from inside SERVER_STARTED can block forever waiting for entities to load). The
    // checks run once its own chunks have loaded, well short of the 15 s keep-alive timeout.
    static final int JOIN_TICKS = 40;
    static final int SETTLE_TICKS = 60;
    static final int CHECKS = 28;
    // The dialog checks exist on the jars that have dialogs, 1.21.6 and later
    //? if >=1.21.6 {
    private static final int DIALOG_CHECKS = DialogProbe.CHECKS;
    //?} else {
    /*private static final int DIALOG_CHECKS = 0;
    *///?}

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
    private static final List<String> DEFAULT_TEXT = List.of("# Welcome to the Server!", "",
            "This is your Traveler's Journal. Use it to record your adventures and discoveries.");

    private final ProbeSupport.Checks checks = new ProbeSupport.Checks();
    // Made only outside upgrade mode, whose first boot runs beside an old jar without these classes
    private JournalsProbe journals;
    private int joinCountdown = -1;
    private int checkCountdown = -1;
    private int journalsCountdown = -1;
    private ServerPlayer alice;

    @Override
    public void onInitialize() {
        // test-matrix.sh upgrade writes this file to run the two halves of an upgrade test instead
        String mode = mode();
        if (mode.startsWith("upgrade-")) {
            new UpgradeProbe(mode.equals("upgrade-before")).register();
            return;
        }
        journals = new JournalsProbe(checks);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ProbeSupport.LOG.info("[TJ-PROBE] START expect={}", CHECKS + JournalsProbe.CHECKS + DIALOG_CHECKS);
            command(server, server.createCommandSourceStack(), "forceload add -32 -32 31 31");
            joinCountdown = JOIN_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (joinCountdown > 0 && --joinCountdown == 0) {
                checks.guarded(() -> firstJoin(server));
                checkCountdown = SETTLE_TICKS;
            } else if (checkCountdown > 0 && --checkCountdown == 0) {
                checks.guarded(() -> settled(server));
                checks.guarded(() -> journals.prepare(server));
                journalsCountdown = SETTLE_TICKS;
            } else if (journalsCountdown > 0 && --journalsCountdown == 0) {
                checks.guarded(() -> journals.settled(server));
                //? if >=1.21.6 {
                checks.guarded(() -> new DialogProbe(checks).run(server));
                //?}
                checks.done();
            }
        });
    }

    private static String mode() {
        try {
            return Files.exists(MODE) ? Files.readString(MODE).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private void firstJoin(MinecraftServer server) throws Exception {
        // Every matrix server is a first boot: the defaults the mod just wrote must already be in use
        Journal journal = Journals.defaultJournal();
        checks.check("first-boot-defaults", journal.lines().equals(DEFAULT_TEXT) && journal.legacyTitles().contains("Traveller's Journal"),
                "lines " + journal.lines() + ", legacy titles " + journal.legacyTitles());
        // A new install's config names the two settings 1.3.0 added, and not the unused give_at_spawn
        String config = Files.readString(CONFIG);
        checks.check("first-boot-config", config.contains("give_on_join=true\n") && config.contains("operator_level=2\n")
                && !config.contains("give_at_spawn"), "config.txt: " + oneLine(config));

        // Reload takes the journal text from disk
        Files.writeString(CONTENT, String.join("\n", JOURNAL) + "\n", StandardCharsets.UTF_8);
        command(server, server.createCommandSourceStack(), "travelers_journal reload");
        checks.check("reload", Journals.defaultJournal().lines().equals(JOURNAL), "lines loaded: " + Journals.defaultJournal().lines().size());

        alice = join(server, "ProbeAlice");
    }

    private void settled(MinecraftServer server) throws Exception {
        CommandSourceStack console = server.createCommandSourceStack();
        Journal journal = Journals.defaultJournal();

        // A new player receives the journal once, with the right text and formatting
        checks.check("join-gives-one", copies(alice, journal) == 1, "journals after first join: " + copies(alice, journal));
        WrittenBookContent content = firstCopy(alice, journal).get(DataComponents.WRITTEN_BOOK_CONTENT);
        checks.check("book-content", content != null, "no written book content");
        if (content == null) return;
        checks.check("title-author", content.title().raw().equals("Traveler's Journal") && content.author().equals("Server"),
                content.title().raw() + " by " + content.author());
        checks.check("page-count", content.pages().size() == 3, "pages: " + content.pages().size());
        Component page = content.pages().get(0).raw();
        checks.check("page-text", page.getString().equals(PAGE_ONE), "page 1 reads: " + oneLine(page.getString()));
        checks.check("bold", styleOf(page, "Bold").isBold() && !styleOf(page, "Bold").isItalic(), styleOf(page, "Bold").toString());
        checks.check("italic", styleOf(page, "Italic").isItalic() && !styleOf(page, "Italic").isBold(), styleOf(page, "Italic").toString());
        checks.check("underline", styleOf(page, "Under").isUnderlined(), styleOf(page, "Under").toString());
        checks.check("colour", colour(page, "Red", ChatFormatting.RED), styleOf(page, "Red").toString());
        checks.check("default-black", colour(page, "Bold", ChatFormatting.BLACK), styleOf(page, "Bold").toString());
        checks.check("join-list", journal.joinList().contains(alice.getStringUUID())
                && Files.readString(JOIN_LIST).contains(alice.getStringUUID()), "uuid missing from the join list");

        // Rejoining gives no second copy, even with the first one gone
        alice.getInventory().clearContent();
        alice = rejoin(server, alice, "ProbeAlice");
        checks.check("rejoin-gives-none", copies(alice, journal) == 0, "journals after rejoin: " + copies(alice, journal));

        // Only operators can use the command
        CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot().getChild("travelers_journal");
        checks.check("op-allowed", node != null && node.canUse(console), "console cannot use the command");
        checks.check("player-refused", node != null && !node.canUse(alice.createCommandSourceStack()), "a player can use the command");
        command(server, alice.createCommandSourceStack(), "travelers_journal give ProbeAlice");
        checks.check("player-give-refused", copies(alice, journal) == 0, "journals: " + copies(alice, journal));
        command(server, console, "travelers_journal give ProbeAlice");
        checks.check("console-give", copies(alice, journal) == 1, "journals: " + copies(alice, journal));

        // /journals is the same command under a shorter name, and for operators only too
        CommandNode<CommandSourceStack> alias = server.getCommands().getDispatcher().getRoot().getChild("journals");
        command(server, alice.createCommandSourceStack(), "journals give ProbeAlice");
        checks.check("alias-operators-only", alias != null && alias.canUse(console) && !alias.canUse(alice.createCommandSourceStack())
                && copies(alice, journal) == 1, "journals node " + alias + ", journals: " + copies(alice, journal));
        command(server, console, "journals give ProbeAlice");
        checks.check("alias-give", copies(alice, journal) == 2, "journals: " + copies(alice, journal));

        // Either name on its own lists the subcommands instead of failing as incomplete
        String usage = said(server, "travelers_journal");
        checks.check("usage", usage.contains("/journals give <players>") && usage.contains("/journals reload"), "said: " + oneLine(usage));
        String aliasUsage = said(server, "journals");
        checks.check("alias-usage", aliasUsage.equals(usage), "said: " + oneLine(aliasUsage));

        // Using the journal swaps in the current text (reloaded through the shorter name)
        ItemStack old = firstCopy(alice, journal).copy();
        Files.writeString(CONTENT, String.join("\n", JOURNAL) + "\n" + UPDATED + "\n", StandardCharsets.UTF_8);
        command(server, console, "journals reload");
        alice.getInventory().clearContent();
        alice.setItemInHand(InteractionHand.MAIN_HAND, old.copy());
        use(server, alice, InteractionHand.MAIN_HAND);
        checks.check("use-refreshes", text(alice.getItemInHand(InteractionHand.MAIN_HAND)).contains(UPDATED),
                "main hand reads: " + oneLine(text(alice.getItemInHand(InteractionHand.MAIN_HAND))));

        // ...from the offhand too, leaving the selected hotbar slot alone
        alice.getInventory().clearContent();
        alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 7));
        alice.setItemInHand(InteractionHand.OFF_HAND, old.copy());
        use(server, alice, InteractionHand.OFF_HAND);
        ItemStack main = alice.getItemInHand(InteractionHand.MAIN_HAND);
        checks.check("offhand-refreshes", text(alice.getItemInHand(InteractionHand.OFF_HAND)).contains(UPDATED), "offhand not refreshed");
        checks.check("offhand-keeps-hotbar", main.getItem() == Items.STONE && main.getCount() == 7, "main hand now: " + main);

        // A journal under a legacy title, as releases before 1.3.0 left them, is recognised and replaced
        ItemStack legacy = new ItemStack(Items.WRITTEN_BOOK);
        legacy.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Travelers Journal"),
                "Someone", 0, List.of(Filterable.passThrough(Component.literal("old text"))), true));
        alice.getInventory().clearContent();
        alice.setItemInHand(InteractionHand.MAIN_HAND, legacy);
        use(server, alice, InteractionHand.MAIN_HAND);
        ItemStack replaced = alice.getItemInHand(InteractionHand.MAIN_HAND);
        WrittenBookContent replacedContent = replaced.get(DataComponents.WRITTEN_BOOK_CONTENT);
        checks.check("legacy-title", replacedContent != null && replacedContent.title().raw().equals("Traveler's Journal")
                && text(replaced).contains(UPDATED) && Journal.DEFAULT_ID.equals(JournalBook.taggedId(replaced)),
                "legacy book not replaced with a tagged default journal");

        // A full inventory drops the journal at the player's feet, like /give
        alice.getInventory().clearContent();
        for (int i = 0; i < 36; i++) {
            alice.getInventory().setItem(i, new ItemStack(Items.STONE, 64));
        }
        command(server, console, "travelers_journal give ProbeAlice");
        int dropped = server.overworld().getEntitiesOfClass(ItemEntity.class, alice.getBoundingBox().inflate(4),
                entity -> JournalBook.isJournal(entity.getItem())).size();
        checks.check("full-inventory-drops", dropped == 1, "journals dropped: " + dropped);

        server.getPlayerList().remove(alice);
    }

    private static Style styleOf(Component page, String run) {
        return page.visit((style, text) -> text.equals(run) ? Optional.of(style) : Optional.<Style>empty(), Style.EMPTY)
                .orElse(Style.EMPTY);
    }

    private static boolean colour(Component page, String run, ChatFormatting expected) {
        return Objects.equals(styleOf(page, run).getColor(), TextColor.fromLegacyFormat(expected));
    }
}
