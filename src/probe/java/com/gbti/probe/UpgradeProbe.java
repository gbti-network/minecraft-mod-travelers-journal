package com.gbti.probe;

import com.gbti.Journal;
import com.gbti.Journals;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * The upgrade test for scripts/test-matrix.sh: a server that ran an older Traveler's Journal must
 * keep its journal when the jar is replaced. The harness runs two boots on one world.
 *
 * "upgrade-before" runs beside the OLD jar, so it uses vanilla code only (the old jar has none of
 * this version's classes, and nothing here touches them in that mode). A player joins and gets
 * the old jar's journal, and is handed a book under a legacy title, as older installs left them.
 *
 * "upgrade-after" runs beside the NEW jar on the same world and checks that nothing was lost:
 * every file the old jar left is byte for byte the same once the new jar has started, and the
 * config, the join list, both books and the journal still reaching new players all work.
 *
 * The downgrade test (scripts/test-matrix.sh downgrade) goes the other way. "downgrade-new" runs
 * beside the NEW jar, makes sure the player carries a journal and saves new text the way the
 * manager does; "downgrade-old" runs beside the OLD jar, vanilla code only again, and checks the
 * old jar keeps the journal, gives no second copy, refreshes it to the text the new jar saved and
 * still gives newcomers one.
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
    // What the old jar left, copied by the harness after the first boot
    private static final Path SNAPSHOT = Path.of("config-before/travelers-journal");
    // How many written books the player carried when the new jar was last running
    private static final Path STATE = Path.of("config/travelers-journal-probe-state.txt");
    private static final String SAVED_TEXT = "Written by the newer release";

    private final String mode;
    private final boolean before;
    private String untouched = "not compared";
    private int passed;
    private int failed;
    private int joinCountdown = -1;
    private int checkCountdown = -1;
    private ServerPlayer veteran;
    private ServerPlayer newcomer;
    private List<String> joinListBefore = List.of();

    UpgradeProbe(String mode) {
        this.mode = mode;
        this.before = mode.equals("upgrade-before");
    }

    private int expected() {
        return switch (mode) {
            case "upgrade-before" -> 2;
            case "upgrade-after" -> 11;
            case "downgrade-new" -> 2;
            case "downgrade-old" -> 4;
            default -> 0;
        };
    }

    void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOG.info("[TJ-PROBE] START {} expect={}", mode, expected());
            // Before anything else touches the files: the new jar has only started
            if (mode.equals("upgrade-after")) untouched = compareWithSnapshot();
            ProbeSupport.command(server, server.createCommandSourceStack(), "forceload add -32 -32 31 31");
            joinCountdown = JournalProbe.JOIN_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (joinCountdown > 0 && --joinCountdown == 0) {
                guarded(() -> join(server));
                checkCountdown = JournalProbe.SETTLE_TICKS;
            } else if (checkCountdown > 0 && --checkCountdown == 0) {
                guarded(() -> {
                    switch (mode) {
                        case "upgrade-before" -> checkBefore(server);
                        case "upgrade-after" -> checkAfter(server);
                        case "downgrade-new" -> checkDowngradeNew(server);
                        case "downgrade-old" -> checkDowngradeOld(server);
                        default -> check("mode", false, "unknown probe mode " + mode);
                    }
                });
                LOG.info("[TJ-PROBE] DONE pass={} fail={}", passed, failed);
            }
        });
    }

    private void join(MinecraftServer server) throws Exception {
        if (before) {
            // Releases before 2.0.0 read their files only when they already existed, so the boot
            // that wrote them ran with no text and gave nobody a book. A real install had booted
            // before; reload reads the files the way such a boot did.
            ProbeSupport.command(server, server.createCommandSourceStack(), "travelers_journal reload");
        }
        if (Files.exists(SNAPSHOT.resolve("joinlist.txt"))) {
            joinListBefore = Files.readAllLines(SNAPSHOT.resolve("joinlist.txt"));
        }
        veteran = ProbeSupport.join(server, VETERAN);
        if (mode.equals("upgrade-after") || mode.equals("downgrade-old")) {
            newcomer = ProbeSupport.join(server, NEWCOMER);
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
        Journal journal = Journals.defaultJournal();
        check("after-files-untouched", untouched.isEmpty(), untouched);
        check("after-config-kept", journal.title().equals(title) && journal.author().equals(author)
                && journal.legacyTitles().contains(LEGACY_TITLE),
                journal.title() + " by " + journal.author() + ", legacy " + journal.legacyTitles());
        // A setting the old config lacks takes its default, and nothing writes it in; one it has is kept
        String oldLevel = snapshotSetting("operator_level");
        String oldGive = snapshotSetting("give_on_join");
        int level = oldLevel == null ? Journals.DEFAULT_OPERATOR_LEVEL : Integer.parseInt(oldLevel.trim());
        boolean give = oldGive == null || Boolean.parseBoolean(oldGive.trim());
        check("after-new-settings-default", Journals.operatorLevel() == level && journal.giveOnJoin() == give
                && (oldLevel != null || !Files.readString(CONFIG).contains("operator_level")), "level " + Journals.operatorLevel()
                + " (expected " + level + "), give on join " + journal.giveOnJoin() + " (expected " + give + ")");
        check("after-no-second-copy", books(veteran) == 2, "written books held: " + books(veteran));

        ItemStack old = find(veteran, title);
        ItemStack legacy = find(veteran, LEGACY_TITLE);
        check("after-old-book-recognised", JournalBook.isJournal(old), "the old jar's journal is not recognised");
        check("after-legacy-book-recognised", JournalBook.isJournal(legacy), "the legacy-titled book is not recognised");

        // Every upgrade fixture starts its journal with a plain line, so page 1 starts with it
        String first = Files.readAllLines(CONTENT).stream().filter(line -> !line.isBlank()).findFirst().orElse("").trim();
        veteran.getInventory().clearContent();
        veteran.setItemInHand(InteractionHand.MAIN_HAND, old.copy());
        ProbeSupport.use(server, veteran, InteractionHand.MAIN_HAND);
        check("after-old-book-refreshes", current(veteran.getItemInHand(InteractionHand.MAIN_HAND), title, author, first),
                "main hand reads: " + ProbeSupport.text(veteran.getItemInHand(InteractionHand.MAIN_HAND)).replace("\n", "\\n"));
        // The old book carried no id; its refreshed copy is tagged as the default journal
        check("after-refresh-tags-default", JournalBook.taggedId(old) == null
                && Journal.DEFAULT_ID.equals(JournalBook.taggedId(veteran.getItemInHand(InteractionHand.MAIN_HAND))),
                "old tag " + JournalBook.taggedId(old) + ", new tag " + JournalBook.taggedId(veteran.getItemInHand(InteractionHand.MAIN_HAND)));
        veteran.getInventory().clearContent();
        veteran.setItemInHand(InteractionHand.MAIN_HAND, legacy.copy());
        ProbeSupport.use(server, veteran, InteractionHand.MAIN_HAND);
        check("after-legacy-book-refreshes", current(veteran.getItemInHand(InteractionHand.MAIN_HAND), title, author, first),
                "main hand reads: " + ProbeSupport.text(veteran.getItemInHand(InteractionHand.MAIN_HAND)).replace("\n", "\\n"));

        List<String> joinListNow = Files.readAllLines(JOIN_LIST);
        // Compared with what the old jar left, so the fixture's own entries count too
        check("after-join-list-kept", joinListBefore.contains(veteran.getStringUUID()) && joinListNow.containsAll(joinListBefore),
                "old jar left " + joinListBefore.size() + " entries, now " + joinListNow.size());
        int newcomerJournals = 0;
        for (int i = 0; i < newcomer.getInventory().getContainerSize(); i++) {
            ItemStack stack = newcomer.getInventory().getItem(i);
            if (JournalBook.isJournal(stack)) newcomerJournals += stack.getCount();
        }
        check("after-newcomer-gets-one", newcomerJournals == 1, "newcomer's journals: " + newcomerJournals);

        server.getPlayerList().remove(veteran);
        server.getPlayerList().remove(newcomer);
    }

    private void checkDowngradeNew(MinecraftServer server) throws Exception {
        int journals = 0;
        for (int i = 0; i < veteran.getInventory().getContainerSize(); i++) {
            ItemStack stack = veteran.getInventory().getItem(i);
            if (JournalBook.isJournal(stack)) journals += stack.getCount();
        }
        check("downgrade-new-has-journal", journals >= 1, "journals held under the newer jar: " + journals);
        // New text saved the way the manager saves it, which the old jar must read when it is back
        Journal journal = Journals.defaultJournal();
        Journals.save(journal, journal.title(), journal.author(), journal.giveOnJoin(), SAVED_TEXT + "\nbefore going back");
        check("downgrade-new-saved", Files.readString(CONTENT).startsWith(SAVED_TEXT), "text: " + Files.readString(CONTENT).replace("\n", "\\n"));
        Files.writeString(STATE, Integer.toString(books(veteran)), StandardCharsets.UTF_8);
        server.getPlayerList().remove(veteran);
    }

    // Beside the old jar again: vanilla code only, as in upgrade-before
    private void checkDowngradeOld(MinecraftServer server) throws Exception {
        String title = config("title", "Traveler's Journal");
        String author = config("author", "Server");
        int held = Files.exists(STATE) ? Integer.parseInt(Files.readString(STATE).trim()) : -1;
        check("downgrade-journal-kept", count(veteran, title) >= 1, "journals titled " + title + ": " + count(veteran, title));
        check("downgrade-no-second-copy", books(veteran) == held, "written books " + books(veteran) + ", under the newer jar " + held);
        ItemStack journal = find(veteran, title);
        veteran.getInventory().clearContent();
        veteran.setItemInHand(InteractionHand.MAIN_HAND, journal.copy());
        ProbeSupport.use(server, veteran, InteractionHand.MAIN_HAND);
        check("downgrade-refreshes", current(veteran.getItemInHand(InteractionHand.MAIN_HAND), title, author, SAVED_TEXT),
                "main hand reads: " + vanillaText(veteran.getItemInHand(InteractionHand.MAIN_HAND)).replace("\n", "\\n"));
        check("downgrade-newcomer-gets-one", count(newcomer, title) == 1, "newcomer's journals: " + count(newcomer, title));
        server.getPlayerList().remove(veteran);
        server.getPlayerList().remove(newcomer);
    }

    // Every file the old jar left, compared byte for byte with what is there now; empty when equal
    private static String compareWithSnapshot() {
        if (!Files.isDirectory(SNAPSHOT)) return "no snapshot of the old jar's files";
        StringBuilder changed = new StringBuilder();
        int compared = 0;
        try (Stream<Path> files = Files.walk(SNAPSHOT)) {
            for (Path old : files.filter(Files::isRegularFile).toList()) {
                Path now = Path.of("config/travelers-journal").resolve(SNAPSHOT.relativize(old).toString());
                compared++;
                if (!Files.exists(now)) changed.append(now).append(" is gone; ");
                else if (Files.mismatch(old, now) != -1) changed.append(now).append(" changed; ");
            }
        } catch (IOException e) {
            return "could not compare: " + e;
        }
        if (compared == 0) return "the snapshot holds no files";
        return changed.toString();
    }

    private static String snapshotSetting(String key) {
        try {
            String value = null;
            for (String line : Files.readAllLines(SNAPSHOT.resolve("config.txt"))) {
                if (line.startsWith(key + "=")) value = line.substring(key.length() + 1);
            }
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private static String vanillaText(ItemStack book) {
        WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) return "";
        StringBuilder text = new StringBuilder();
        for (Filterable<Component> page : content.pages()) text.append(page.raw().getString()).append('\n');
        return text.toString();
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

    // Books, not slots: identical books stack, so a second copy can hide in the first one's slot
    private static int count(ServerPlayer player, String title) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (title.equals(titleOf(stack))) count += stack.getCount();
        }
        return count;
    }

    private static int books(ServerPlayer player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() == Items.WRITTEN_BOOK) count += stack.getCount();
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
