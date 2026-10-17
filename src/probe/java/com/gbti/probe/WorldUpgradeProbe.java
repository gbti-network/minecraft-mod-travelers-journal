package com.gbti.probe;

import com.gbti.Journal;
import com.gbti.JournalBook;
import com.gbti.Journals;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.gbti.probe.ProbeSupport.command;
import static com.gbti.probe.ProbeSupport.copies;
import static com.gbti.probe.ProbeSupport.firstCopy;
import static com.gbti.probe.ProbeSupport.oneLine;
import static com.gbti.probe.ProbeSupport.text;
import static com.gbti.probe.ProbeSupport.use;

/**
 * The world upgrade test for scripts/test-matrix.sh: a server on 1.20.4 or older moves its world to
 * a later Minecraft, where books are item components instead of NBT. The harness runs the old
 * version's jar on a new world, then opens that world on the later version with its jar.
 *
 * "world-before" gives a player the default journal and a named one, then logs them out.
 *
 * "world-after" logs the player back in, so Minecraft upgrades their saved inventory, and checks
 * that both books still belong to their journals, kept their pages, and refresh when used.
 */
final class WorldUpgradeProbe {
    private static final Path WELCOME = Path.of("config/travelers-journal/journals/probe-welcome");
    private static final String PLAYER = "ProbeTraveller";
    private static final String WELCOME_TEXT = "**Welcome** aboard";
    private static final String REFRESHED_TEXT = "Welcome back, after the upgrade";

    private final boolean before;
    private final ProbeSupport.Checks checks = new ProbeSupport.Checks();
    private int joinCountdown = -1;
    private int checkCountdown = -1;
    private ServerPlayer player;

    WorldUpgradeProbe(boolean before) {
        this.before = before;
    }

    void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ProbeSupport.LOG.info("[TJ-PROBE] START {} expect={}", before ? "world-before" : "world-after", before ? 1 : 3);
            command(server, server.createCommandSourceStack(), "forceload add -32 -32 31 31");
            joinCountdown = JournalProbe.JOIN_TICKS;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (joinCountdown > 0 && --joinCountdown == 0) {
                checks.guarded(() -> join(server));
                checkCountdown = JournalProbe.SETTLE_TICKS;
            } else if (checkCountdown > 0 && --checkCountdown == 0) {
                checks.guarded(() -> {
                    if (before) checkBefore(server);
                    else checkAfter(server);
                });
                checks.done();
            }
        });
    }

    private void join(MinecraftServer server) throws Exception {
        if (before) {
            // A named journal from files, given by command only, beside the default one
            Files.createDirectories(WELCOME);
            Files.writeString(WELCOME.resolve("journal.txt"), "title=Welcome aboard\nauthor=Probe Inn\n", StandardCharsets.UTF_8);
            Files.writeString(WELCOME.resolve("journal.md"), WELCOME_TEXT + "\n", StandardCharsets.UTF_8);
            command(server, server.createCommandSourceStack(), "journals reload");
        }
        // Joining loads the saved player, which Minecraft upgrades on the later version
        player = ProbeSupport.join(server, PLAYER);
        if (before) {
            command(server, server.createCommandSourceStack(), "journals give " + PLAYER + " probe-welcome");
        }
    }

    private void checkBefore(MinecraftServer server) {
        Journal welcome = Journals.get("probe-welcome");
        checks.check("world-before-carries", copies(player, Journals.defaultJournal()) == 1 && copies(player, welcome) == 1
                        && "probe-welcome".equals(JournalBook.taggedId(firstCopy(player, welcome))),
                "default " + copies(player, Journals.defaultJournal()) + ", welcome " + copies(player, welcome));
        server.getPlayerList().remove(player); // logging out saves the player for the next boot
    }

    private void checkAfter(MinecraftServer server) throws Exception {
        Journal standard = Journals.defaultJournal();
        Journal welcome = Journals.get("probe-welcome");
        // Both copies still belong to their journals, and joining again gave no second default one
        checks.check("world-after-journals-kept", welcome != null && copies(player, standard) == 1 && copies(player, welcome) == 1
                        && Journal.DEFAULT_ID.equals(JournalBook.taggedId(firstCopy(player, standard)))
                        && "probe-welcome".equals(JournalBook.taggedId(firstCopy(player, welcome))),
                "default " + copies(player, standard) + ", welcome " + copies(player, welcome)
                        + ", tags " + JournalBook.taggedId(firstCopy(player, standard)) + " and " + JournalBook.taggedId(firstCopy(player, welcome)));
        if (welcome == null) return;
        ItemStack copy = firstCopy(player, welcome).copy();
        checks.check("world-after-pages-kept", text(copy).startsWith("Welcome aboard"), "pages read: " + oneLine(text(copy)));

        // The upgraded copy refreshes like any other
        Files.writeString(WELCOME.resolve("journal.md"), REFRESHED_TEXT + "\n", StandardCharsets.UTF_8);
        command(server, server.createCommandSourceStack(), "journals reload");
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, copy);
        use(server, player, InteractionHand.MAIN_HAND);
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        checks.check("world-after-refreshes", text(held).contains(REFRESHED_TEXT) && "probe-welcome".equals(JournalBook.taggedId(held)),
                "held reads: " + oneLine(text(held)));
        server.getPlayerList().remove(player);
    }
}
