package com.gbti.probe;

import com.gbti.Journal;
import com.gbti.JournalBook;
import com.gbti.Journals;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.gbti.probe.ProbeSupport.copies;
import static com.gbti.probe.ProbeSupport.firstCopy;
import static com.gbti.probe.ProbeSupport.oneLine;
import static com.gbti.probe.ProbeSupport.said;
import static com.gbti.probe.ProbeSupport.text;
import static com.gbti.probe.ProbeSupport.titleOf;
import static com.gbti.probe.ProbeSupport.use;

/**
 * Several journals, on every version: created by command and from files, given on join only when
 * marked, tagged with their id, listed, deleted with a backup, and the default journal kept safe.
 */
final class JournalsProbe {
    static final int CHECKS = 17;
    private static final Path ROOT = Path.of("config/travelers-journal");
    private static final Path CONFIG = ROOT.resolve("config.txt");
    private static final Path JOIN_LIST = ROOT.resolve("joinlist.txt");
    private static final Path GUIDE = ROOT.resolve("journals/probe-guide");
    private static final Path WELCOME = ROOT.resolve("journals/probe-welcome");
    private static final Path BACKUPS = ROOT.resolve("backups/journals/probe-guide");

    private final ProbeSupport.Checks checks;
    private ServerPlayer bob;

    JournalsProbe(ProbeSupport.Checks checks) {
        this.checks = checks;
    }

    void prepare(MinecraftServer server) throws Exception {
        // By command: a new journal starts with a title from its id and is given by command only
        said(server, "journals create probe-guide");
        Journal guide = Journals.get("probe-guide");
        checks.check("create", guide != null && guide.title().equals("Probe guide") && !guide.giveOnJoin()
                && Files.exists(GUIDE.resolve("journal.txt")) && Files.exists(GUIDE.resolve("journal.md")),
                "journal " + (guide == null ? "missing" : guide.title() + ", give on join " + guide.giveOnJoin()));
        String bad = said(server, "journals create Bad_Id");
        String twice = said(server, "journals create probe-guide");
        String reserved = said(server, "journals create default");
        checks.check("create-refuses", bad.contains("lower-case") && !Files.exists(ROOT.resolve("journals/Bad_Id"))
                && twice.contains("already") && reserved.contains("already"),
                "said " + oneLine(bad + twice + reserved));

        // From files, as an admin would write them, then a reload
        Files.createDirectories(WELCOME);
        Files.writeString(WELCOME.resolve("journal.txt"), "title=Welcome aboard\nauthor=Probe Inn\ngive_on_join=true\n", StandardCharsets.UTF_8);
        Files.writeString(WELCOME.resolve("journal.md"), "**Welcome** aboard\n", StandardCharsets.UTF_8);
        said(server, "journals reload");
        Journal welcome = Journals.get("probe-welcome");
        checks.check("files-journal-loaded", welcome != null && welcome.title().equals("Welcome aboard")
                && welcome.author().equals("Probe Inn") && welcome.giveOnJoin(),
                "journal " + (welcome == null ? "missing" : welcome.title() + " by " + welcome.author()));

        bob = ProbeSupport.join(server, "ProbeBob");
    }

    void settled(MinecraftServer server) throws Exception {
        Journal standard = Journals.defaultJournal();
        Journal guide = Journals.get("probe-guide");
        Journal welcome = Journals.get("probe-welcome");
        CommandSourceStack console = server.createCommandSourceStack();

        // A new player gets every journal marked give_on_join, once each, and no other
        checks.check("join-gives-marked", copies(bob, standard) == 1 && copies(bob, welcome) == 1 && copies(bob, guide) == 0
                && Files.readString(WELCOME.resolve("joinlist.txt")).contains(bob.getStringUUID()),
                "default " + copies(bob, standard) + ", welcome " + copies(bob, welcome) + ", guide " + copies(bob, guide));
        ItemStack welcomeCopy = firstCopy(bob, welcome);
        WrittenBookContent content = welcomeCopy.get(DataComponents.WRITTEN_BOOK_CONTENT);
        checks.check("book-tagged", "probe-welcome".equals(JournalBook.taggedId(welcomeCopy)) && content != null
                && content.title().raw().equals("Welcome aboard") && content.author().equals("Probe Inn"),
                "tag " + JournalBook.taggedId(welcomeCopy) + ", title " + titleOf(welcomeCopy));

        String gave = said(server, "journals give ProbeBob probe-guide");
        checks.check("give-named", copies(bob, guide) == 1 && gave.contains("Gave Probe guide to 1 player"),
                "guide copies " + copies(bob, guide) + ", said " + oneLine(gave));
        String unknown = said(server, "journals give ProbeBob nope");
        checks.check("give-unknown", unknown.contains("No journal has the id nope"), "said " + oneLine(unknown));

        String list = said(server, "journals list");
        checks.check("list", list.contains("Journals: 3") && list.contains("default Traveler's Journal, first join")
                && list.contains("probe-guide Probe guide, by command") && list.contains("probe-welcome Welcome aboard, first join"),
                "said " + oneLine(list));
        String edit = said(server, "journals edit probe-welcome");
        checks.check("edit-names-file", edit.contains("config/travelers-journal/journals/probe-welcome/journal.md"), "said " + oneLine(edit));
        String usage = said(server, "journals");
        checks.check("usage-lists-commands", usage.contains("/journals list") && usage.contains("/journals create <id>")
                && usage.contains("/journals delete <journal>") && usage.contains("/journals edit [journal]"), "said " + oneLine(usage));

        // Delete moves the folder to a dated backup; copies already handed out stay as they are
        ItemStack guideCopy = firstCopy(bob, guide).copy();
        String deleted = said(server, "journals delete probe-guide");
        checks.check("delete", Journals.get("probe-guide") == null && !Files.exists(GUIDE) && backupHas("journal.md")
                && deleted.contains("Copies players already carry stay as signed books"), "said " + oneLine(deleted));
        bob.getInventory().clearContent();
        bob.setItemInHand(InteractionHand.MAIN_HAND, guideCopy.copy());
        use(server, bob, InteractionHand.MAIN_HAND);
        ItemStack held = bob.getItemInHand(InteractionHand.MAIN_HAND);
        checks.check("deleted-copy-stays", JournalBook.journalOf(held) == null && "probe-guide".equals(JournalBook.taggedId(held))
                && text(held).equals(text(guideCopy)), "held " + titleOf(held) + ": " + oneLine(text(held)));
        String refused = said(server, "journals delete default");
        checks.check("default-not-deletable", refused.contains("cannot be deleted") && Files.exists(ROOT.resolve("travelers_journal.md"))
                && Journals.defaultJournal() != null, "said " + oneLine(refused));

        // Reloading never writes the join list again
        said(server, "journals reload");
        said(server, "journals reload");
        long entries = Files.readAllLines(JOIN_LIST).stream().filter(bob.getStringUUID()::equals).count();
        checks.check("join-list-once", entries == 1, "Bob's join list entries: " + entries);

        // The server owner sets the level in config.txt, and a reload applies it
        CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot().getChild("journals");
        setLevel(server, 0);
        boolean anyone = node.canUse(bob.createCommandSourceStack());
        setLevel(server, 4);
        boolean owners = node.canUse(console) && !node.canUse(bob.createCommandSourceStack());
        setLevel(server, 2);
        boolean standardLevel = !node.canUse(bob.createCommandSourceStack()) && Journals.operatorLevel() == 2;
        checks.check("operator-level", anyone && owners && standardLevel, "level 0 lets Bob in " + anyone
                + ", level 4 owners only " + owners + ", level 2 restored " + standardLevel);

        // Renaming the default journal keeps its old title, so untagged copies are still recognised
        Journals.save(standard, "Renamed Journal", standard.author(), true, null);
        ItemStack untagged = new ItemStack(Items.WRITTEN_BOOK);
        untagged.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Traveler's Journal"),
                "Someone", 0, List.of(Filterable.passThrough(Component.literal("old text"))), true));
        checks.check("rename-keeps-legacy", Journals.defaultJournal().title().equals("Renamed Journal")
                && Journals.defaultJournal().legacyTitles().contains("Traveler's Journal")
                && JournalBook.journalOf(untagged) == Journals.defaultJournal(), "legacy titles " + Journals.defaultJournal().legacyTitles());
        String config = Files.readString(CONFIG);
        checks.check("save-keeps-settings", config.contains("debug_logging=false") && config.contains("operator_level=2")
                && config.contains("title=Renamed Journal") && Files.exists(ROOT.resolve("config.txt.bak")), "config.txt: " + oneLine(config));
        Journals.save(Journals.defaultJournal(), "Traveler's Journal", standard.author(), true, null);

        server.getPlayerList().remove(bob);
    }

    private static boolean backupHas(String file) throws Exception {
        if (!Files.isDirectory(BACKUPS)) return false;
        try (Stream<Path> stamps = Files.list(BACKUPS)) {
            return stamps.anyMatch(stamp -> Files.exists(stamp.resolve(file)));
        }
    }

    private static void setLevel(MinecraftServer server, int level) throws Exception {
        List<String> lines = new ArrayList<>();
        for (String line : Files.readAllLines(CONFIG)) {
            if (!line.startsWith("operator_level=")) lines.add(line);
        }
        lines.add("operator_level=" + level);
        Files.write(CONFIG, lines, StandardCharsets.UTF_8);
        Journals.reload();
    }
}
