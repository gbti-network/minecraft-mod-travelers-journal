package com.gbti;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
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

import java.util.function.Predicate;

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
            if(entity instanceof ServerPlayer player) {
                if(!FileManager.joinList.contains(player.getStringUUID()) &&
                   !player.getInventory().contains(JournalBook::isJournal)) {
                    giveBookToPlayer(player);
                    FileManager.writeJoinList(player.getStringUUID());
                }
            }
        }));

        // Update journal when used: swap in a fresh copy, then let the book open as usual
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if(player instanceof ServerPlayer && JournalBook.isJournal(player.getItemInHand(hand))) {
                player.setItemInHand(hand, JournalBook.create());
            }
            //? if >=1.21.2 {
            return InteractionResult.PASS;
            //?} else {
            /*return InteractionResultHolder.pass(player.getItemInHand(hand));
            *///?}
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<CommandSourceStack> command = dispatcher.register(Commands.literal("travelers_journal")
                .requires(operatorsOnly())
                .executes(TravelersJournal::showUsage)
                .then(Commands.literal("reload")
                    .executes(context -> {
                        FileManager.readFiles();
                        context.getSource().sendSuccess(() -> Component.literal("Reloaded journal files"), false);
                        return 1;
                    })
                )
                .then(Commands.literal("give")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> {
                            for (ServerPlayer target : EntityArgument.getPlayers(context, "targets")) {
                                giveBookToPlayer(target);
                            }
                            context.getSource().sendSuccess(() -> Component.literal("Given journal to specified players"), false);
                            return 1;
                        })
                    )
                )
            );
            // The shorter name. A redirect skips the target's requirement, so it carries its own
            dispatcher.register(Commands.literal("journals")
                .requires(operatorsOnly())
                .executes(TravelersJournal::showUsage)
                .redirect(command));
        });
    }

    private static Predicate<CommandSourceStack> operatorsOnly() {
        //? if >=1.21.11 {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        //?} else {
        /*return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
        *///?}
    }

    // Either name on its own lists what it does, instead of failing as an incomplete command
    private static int showUsage(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Traveler's Journal: /journals or /travelers_journal").withStyle(ChatFormatting.GOLD)
            .append(usage("/journals give <players>", "gives players the journal"))
            .append(usage("/journals reload", "reads the journal files again")), false);
        return 1;
    }

    private static Component usage(String command, String effect) {
        return Component.literal("\n" + command).withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" " + effect).withStyle(ChatFormatting.GRAY));
    }

    // Like /give: into the inventory, or dropped at the player's feet when it is full
    public static void giveBookToPlayer(ServerPlayer player) {
        //? if >=26.3 {
        player.getInventory().placeItemBackInInventory(JournalBook.create(), Prediction.SERVER_ONLY);
        //?} else {
        /*player.getInventory().placeItemBackInInventory(JournalBook.create());
        *///?}
    }
}
