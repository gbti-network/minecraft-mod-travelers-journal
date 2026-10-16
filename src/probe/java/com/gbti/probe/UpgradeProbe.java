package com.gbti.probe;

import com.gbti.FileManager;
import com.gbti.JournalBook;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The upgrade test for scripts/test-matrix.sh: a server that ran an older Traveler's Journal must
 * keep its journal when the jar is replaced. The harness runs two boots on one world.
 *
 * "upgrade-before" runs beside the OLD jar, so it uses vanilla code only (the old jar has none of
 * this version's classes, and nothing here touches them in that mode). A player joins and gets
 * the old jar's journal, and is handed a book under a legacy title, as older installs left them.
 *
 * "upgrade-after" runs beside the NEW jar on the same world and checks that nothing was lost:
 * the config, the join list, both books, and the journal still reaching new players.
 */
class UpgradeProbe {
    private static final Logger LOG = LogManager.getLogger("TJProbe");
    private static final Path CONFIG = Path.of("config/travelers-journal/config.txt");
    private static final Path CONTENT = Path.of("config/travelers-journal/travelers_journal.md");
    private static final Path JOIN_LIST = Path.of("config/travelers-journal/joinlist.txt");
    private static final String VETERAN = "ProbeVeteran";
    private static final String NEWCOMER = "ProbeNewcomer";
    // One of the legacy titles every config since 1.2.6 lists by default
    private static final String LEGACY_TITLE = "Traveller's Journal";

    private final boolean before;
    private int passed;
    private int failed;
    private int joinCountdown = -1;
    private int checkCountdown = -1;
    private ServerPlayer veteran;
    private ServerPlayer newcomer;
    private List<String> joinListBefore = List.of();

    UpgradeProbe(boolean before) {
        this.before = before;
    }

    void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOG.info("[TJ-PROBE] START {}", before ? "upgrade-before" : "upgrade-after");
            JournalProbe.command(server, server.createCommandSourceStack(), "forceload add -32 -32 31 31");
            joinCountdown = JournalProbe.JOIN_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (joinCountdown > 0 && --joinCountdown == 0) {
                guarded(() -> join(server));
                checkCountdown = JournalProbe.SETTLE_TICKS;
            } else if (checkCountdown > 0 && --checkCountdown == 0) {
                guarded(() -> {
                    if (before) checkBefore(server);
                    else checkAfter(server);
                });
                LOG.info("[TJ-PROBE] DONE pass={} fail={}", passed, failed);
            }
        });
    }

    private void join(MinecraftServer server) throws Exception {
        if (before) {
            // Releases before 1.3.0 read their files only when they already existed, so the boot
            // that wrote them ran with no text and gave nobody a book. A real install had booted
            // before; reload reads the files the way such a boot did.
            JournalProbe.command(server, server.createCommandSourceStack(), "travelers_journal reload");
        }
        if (!before && Files.exists(JOIN_LIST)) {
            joinListBefore = Files.readAllLines(JOIN_LIST);
        }
        veteran = JournalProbe.join(server, VETERAN);
        if (!before) {
            newcomer = JournalProbe.join(server, NEWCOMER);
        }
    }

    private void checkBefore(MinecraftServer server) {
        String title = config("title", "Traveler's Journal");
        check("before-journal-given", count(veteran, title) == 1, "old jar's journals: " + count(veteran, title));
        veteran.getInventory().setItem(8, book(LEGACY_TITLE, "Text from an older install"));
        check("before-legacy-book-held", count(veteran, LEGACY_TITLE) == 1, "legacy books: " + count(veteran, LEGACY_TITLE));
        server.getPlayerList().remove(veteran); // logging out saves the player for the next boot
    }

    private void checkAfter(MinecraftServer server) throws Exception {
        String title = config("title", "Traveler's Journal");
        String author = config("author", "Server");
        check("after-config-kept", FileManager.journalTitle.equals(title) && FileManager.journalAuthor.equals(author)
                && FileManager.legacyTitles.contains(LEGACY_TITLE),
                FileManager.journalTitle + " by " + FileManager.journalAuthor + ", legacy " + FileManager.legacyTitles);
        check("after-no-second-copy", books(veteran) == 2, "written books held: " + books(veteran));

        ItemStack old = find(veteran, title);
        ItemStack legacy = find(veteran, LEGACY_TITLE);
        check("after-old-book-recognised", JournalBook.isJournal(old), "the old jar's journal is not recognised");
        check("after-legacy-book-recognised", JournalBook.isJournal(legacy), "the legacy-titled book is not recognised");

        // Every upgrade fixture starts its journal with a plain line, so page 1 starts with it
        String first = Files.readAllLines(CONTENT).stream().filter(line -> !line.isBlank()).findFirst().orElse("").trim();
        veteran.getInventory().clearContent();
        veteran.setItemInHand(InteractionHand.MAIN_HAND, old.copy());
        JournalProbe.use(server, veteran, InteractionHand.MAIN_HAND);
        check("after-old-book-refreshes", current(veteran.getItemInHand(InteractionHand.MAIN_HAND), title, author, first),
                "main hand reads: " + JournalProbe.text(veteran.getItemInHand(InteractionHand.MAIN_HAND)).replace("\n", "\\n"));
        veteran.getInventory().clearContent();
        veteran.setItemInHand(InteractionHand.MAIN_HAND, legacy.copy());
        JournalProbe.use(server, veteran, InteractionHand.MAIN_HAND);
        check("after-legacy-book-refreshes", current(veteran.getItemInHand(InteractionHand.MAIN_HAND), title, author, first),
                "main hand reads: " + JournalProbe.text(veteran.getItemInHand(InteractionHand.MAIN_HAND)).replace("\n", "\\n"));

        List<String> joinListNow = Files.readAllLines(JOIN_LIST);
        check("after-join-list-kept", joinListBefore.contains(veteran.getStringUUID()) && joinListNow.containsAll(joinListBefore),
                "before " + joinListBefore.size() + " entries, now " + joinListNow.size());
        int newcomerJournals = 0;
        for (int i = 0; i < newcomer.getInventory().getContainerSize(); i++) {
            if (JournalBook.isJournal(newcomer.getInventory().getItem(i))) newcomerJournals++;
        }
        check("after-newcomer-gets-one", newcomerJournals == 1, "newcomer's journals: " + newcomerJournals);

        server.getPlayerList().remove(veteran);
        server.getPlayerList().remove(newcomer);
    }

    private static String config(String key, String fallback) {
        try {
            for (String line : Files.readAllLines(CONFIG)) {
                if (line.startsWith(key + "=")) return line.substring(key.length() + 1);
            }
        } catch (Exception e) {
            // no config yet: the mod's own default applies
        }
        return fallback;
    }

    private static ItemStack book(String title, String text) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), "Someone", 0,
                List.of(Filterable.passThrough(Component.literal(text))), true));
        return book;
    }

    private static String titleOf(ItemStack stack) {
        WrittenBookContent content = stack.getItem() == Items.WRITTEN_BOOK ? stack.get(DataComponents.WRITTEN_BOOK_CONTENT) : null;
        return content == null ? null : content.title().raw();
    }

    private static int count(ServerPlayer player, String title) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (title.equals(titleOf(player.getInventory().getItem(i)))) count++;
        }
        return count;
    }

    private static int books(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == Items.WRITTEN_BOOK) count++;
        }
        return count;
    }

    private static ItemStack find(ServerPlayer player, String title) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (title.equals(titleOf(stack))) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean current(ItemStack book, String title, String author, String firstLine) {
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        return content != null && content.title().raw().equals(title) && content.author().equals(author)
                && !content.pages().isEmpty() && content.pages().get(0).raw().getString().startsWith(firstLine);
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
