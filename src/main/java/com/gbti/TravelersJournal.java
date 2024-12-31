package com.gbti;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.gbti.utils.StringUtils;

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
        FileManager.readFiles();
        
        // Register commands
        registerCommands();
        
        // Register events
        registerEvents();
        
        logDebug("Travelers Journal initialized");
    }

    private void registerEvents() {
        // Give new players the journal
        ServerEntityEvents.ENTITY_LOAD.register(((entity, world) -> {
            if(entity instanceof ServerPlayerEntity player) {
                if(!FileManager.joinList.contains(player.getUuidAsString()) && 
                   !(player.getInventory().containsAny(itemStack -> 
                       isJournalBook(itemStack)))) {
                    giveBookToPlayer(player);
                    FileManager.writeJoinList(player.getUuidAsString());
                }
            }
        }));

        // Update journal when used
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if(player instanceof ServerPlayerEntity serverPlayer) {
                if(player.getStackInHand(hand).getItem().equals(Items.WRITTEN_BOOK) && 
                   isJournalBook(player.getStackInHand(hand))) {
                    player.setStackInHand(hand, ItemStack.EMPTY);
                    replaceBookInPlayerInv(serverPlayer, player.getInventory().selectedSlot);
                    return TypedActionResult.pass(player.getStackInHand(hand));
                }
            }
            return TypedActionResult.pass(player.getStackInHand(hand));
        });
    }

    private boolean isJournalBook(ItemStack stack) {
        if (!stack.getItem().equals(Items.WRITTEN_BOOK)) {
            return false;
        }
        String bookTitle = stack.getName().getString();
        logDebug("Checking book title: '{}' against current title: '{}' and legacy titles: {}", 
                bookTitle, FileManager.journalTitle, FileManager.legacyTitles);
        
        // First check current title
        if (bookTitle.equalsIgnoreCase(FileManager.journalTitle)) {
            logDebug("Matched current title");
            return true;
        }
        // Then check legacy titles
        for (String legacyTitle : FileManager.legacyTitles) {
            if (bookTitle.equalsIgnoreCase(legacyTitle)) {
                logDebug("Matched legacy title: {}", legacyTitle);
                return true;
            }
        }
        logDebug("No title match found");
        return false;
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess, environment) -> dispatcher.register(CommandManager.literal("travelers_journal")
            .requires(source -> {
                if(source instanceof ServerCommandSource serverSource) return serverSource.hasPermissionLevel(2);
                else return false;
            })
            .then(CommandManager.literal("reload")
                .executes(context -> {
                    FileManager.readFiles();
                    context.getSource().sendFeedback(() -> Text.literal("Reloaded journal files"), false);
                    return 1;
                })
            )
            .then(CommandManager.literal("give")
                .then(CommandManager.argument("targets", EntityArgumentType.players())
                    .executes(context -> {
                        for (ServerPlayerEntity target : EntityArgumentType.getPlayers(context, "targets")) {
                            giveBookToPlayer(target);
                        }
                        context.getSource().sendFeedback(() -> Text.literal("Given journal to specified players"), false);
                        return 1;
                    })
                )
            )
        )));
    }

    public static void replaceBookInPlayerInv(ServerPlayerEntity player, int slot) {
        player.getServer().getCommandManager().executeWithPrefix(
            player.getServer().getCommandSource(), 
            "item replace entity " + player.getName().getString() + " container." + slot + " with " + StringUtils.getBookString()
        );
    }

    public static void giveBookToPlayer(ServerPlayerEntity player) {
        player.getServer().getCommandManager().executeWithPrefix(
            player.getServer().getCommandSource(), 
            "give " + player.getName().getString() + " " + StringUtils.getBookString()
        );
    }
}