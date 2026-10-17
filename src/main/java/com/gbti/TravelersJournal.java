package com.gbti;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.2 {
import net.minecraft.world.InteractionResult;
//?} else {
/*import net.minecraft.world.InteractionResultHolder;
*///?}
//? if >=26.3 {
import net.minecraft.util.Prediction;
//?}
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TravelersJournal implements ModInitializer {
    public static final String MOD_ID = "travelers-journal";
    private static final Logger LOGGER = LogManager.getLogger("TravelersJournal");
    public static boolean debugLoggingEnabled = false;

    public static void logDebug(String message, Object... params) {
        if (debugLoggingEnabled) {
            LOGGER.info("[TJ Debug] " + message, params);
        }
    }

    public static void logError(String message, Object... params) {
        LOGGER.error(message, params);
    }

    @Override
    public void onInitialize() {
        // Load configuration and journal content
        Journals.reload();

        // Register commands
        JournalCommands.register();

        // Register events
        registerEvents();

        logDebug("Travelers Journal initialized");
    }

    private void registerEvents() {
        // Give new players each journal marked give_on_join, once: the join list remembers them
        ServerEntityEvents.ENTITY_LOAD.register(((entity, world) -> {
            if (entity instanceof ServerPlayer player) {
                String uuid = player.getStringUUID();
                for (Journal journal : Journals.all()) {
                    if (journal.giveOnJoin() && !journal.joinList().contains(uuid) && !carries(player, journal)) {
                        give(player, journal);
                        Journals.markGiven(journal, uuid);
                    }
                }
            }
        }));

        // Update journal when used: swap in a fresh copy, then let the book open as usual
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (player instanceof ServerPlayer) {
                Journal journal = JournalBook.journalOf(player.getItemInHand(hand));
                if (journal != null) {
                    player.setItemInHand(hand, JournalBook.create(journal));
                }
            }
            //? if >=1.21.2 {
            return InteractionResult.PASS;
            //?} else {
            /*return InteractionResultHolder.pass(player.getItemInHand(hand));
            *///?}
        });
    }

    // Every slot, armour and offhand included; Inventory.contains(Predicate) only exists from 1.20.5
    private static boolean carries(ServerPlayer player, Journal journal) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (JournalBook.journalOf(player.getInventory().getItem(i)) == journal) {
                return true;
            }
        }
        return false;
    }

    public static void giveBookToPlayer(ServerPlayer player) {
        give(player, Journals.defaultJournal());
    }

    // Like /give: into the inventory, or dropped at the player's feet when it is full
    public static void give(ServerPlayer player, Journal journal) {
        //? if >=26.3 {
        player.getInventory().placeItemBackInInventory(JournalBook.create(journal), Prediction.SERVER_ONLY);
        //?} else {
        /*player.getInventory().placeItemBackInInventory(JournalBook.create(journal));
        *///?}
    }
}
