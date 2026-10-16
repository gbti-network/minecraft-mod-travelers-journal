package com.gbti;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.6 {
import com.gbti.dialog.JournalScreens;
//?}
//? if >=1.21.11 {
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.server.permissions.PermissionLevel;
//?}

import java.nio.file.Path;
import java.util.Collection;

/**
 * /travelers_journal and its shorter name /journals. Both are for operators: the level comes from
 * operator_level in config.txt (2 unless the server owner changes it) and is read on every use, so
 * a reload applies a new level at once.
 */
public final class JournalCommands {
    private static final SuggestionProvider<CommandSourceStack> JOURNALS = (context, builder) ->
            SharedSuggestionProvider.suggest(Journals.all().stream().map(Journal::id), builder);
    private static final SuggestionProvider<CommandSourceStack> DELETABLE = (context, builder) ->
            SharedSuggestionProvider.suggest(Journals.all().stream().filter(journal -> !journal.isDefault()).map(Journal::id), builder);

    private JournalCommands() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralCommandNode<CommandSourceStack> command = dispatcher.register(Commands.literal("travelers_journal")
                .requires(JournalCommands::allowed)
                .executes(JournalCommands::bare)
                .then(Commands.literal("list").executes(JournalCommands::list))
                .then(Commands.literal("reload").executes(JournalCommands::reload))
                .then(Commands.literal("give")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> give(context, null))
                        .then(Commands.argument("journal", StringArgumentType.word()).suggests(JOURNALS)
                            .executes(context -> give(context, StringArgumentType.getString(context, "journal"))))
                    )
                )
                .then(Commands.literal("create")
                    .then(Commands.argument("id", StringArgumentType.word())
                        .executes(context -> create(context, StringArgumentType.getString(context, "id"))))
                )
                .then(Commands.literal("delete")
                    .then(Commands.argument("journal", StringArgumentType.word()).suggests(DELETABLE)
                        .executes(context -> delete(context, StringArgumentType.getString(context, "journal"))))
                )
                .then(Commands.literal("edit")
                    .executes(context -> edit(context, Journal.DEFAULT_ID))
                    .then(Commands.argument("journal", StringArgumentType.word()).suggests(JOURNALS)
                        .executes(context -> edit(context, StringArgumentType.getString(context, "journal"))))
                )
            );
            // The shorter name. A redirect skips the target's requirement, so it carries its own
            dispatcher.register(Commands.literal("journals")
                .requires(JournalCommands::allowed)
                .executes(JournalCommands::bare)
                .redirect(command));
        });
    }

    /** Whether a command source may use the commands and the manager, at the configured level. */
    public static boolean allowed(CommandSourceStack source) {
        int level = Journals.operatorLevel();
        //? if >=1.21.11 {
        PermissionCheck check = level <= 0 ? PermissionCheck.AlwaysPass.INSTANCE
                : new PermissionCheck.Require(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
        return check.check(source.permissions());
        //?} else {
        /*return source.hasPermission(level);
        *///?}
    }

    // On its own, either name opens the manager for a player (1.21.6 and later) and lists the
    // commands otherwise, instead of failing as an incomplete command
    private static int bare(CommandContext<CommandSourceStack> context) {
        //? if >=1.21.6 {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            JournalScreens.hub(player);
            return 1;
        }
        //?}
        context.getSource().sendSuccess(JournalCommands::usage, false);
        return 1;
    }

    static Component usage() {
        //? if >=1.21.6 {
        String edit = "opens a journal in the editor";
        //?} else {
        /*String edit = "names a journal's file to edit";
        *///?}
        MutableComponent usage = Component.literal("Traveler's Journal: /journals or /travelers_journal").withStyle(ChatFormatting.GOLD);
        //? if >=1.21.6 {
        usage.append(line("/journals", "opens the journal manager, in game"));
        //?}
        return usage
            .append(line("/journals list", "shows every journal"))
            .append(line("/journals give <players> [journal]", "gives players a journal"))
            .append(line("/journals create <id>", "adds a journal"))
            .append(line("/journals delete <journal>", "removes a journal, keeping a backup"))
            .append(line("/journals edit [journal]", edit))
            .append(line("/journals reload", "reads the journal files again"));
    }

    private static Component line(String command, String effect) {
        return Component.literal("\n" + command).withStyle(ChatFormatting.YELLOW)
            .append(Component.literal(" " + effect).withStyle(ChatFormatting.GRAY));
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        MutableComponent text = Component.literal("Journals: " + Journals.all().size()).withStyle(ChatFormatting.GOLD);
        for (Journal journal : Journals.all()) {
            text.append(Component.literal("\n" + journal.id()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" " + journal.title()).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(", " + giveRule(journal)).withStyle(ChatFormatting.GRAY));
        }
        context.getSource().sendSuccess(() -> text, false);
        return Journals.all().size();
    }

    public static String giveRule(Journal journal) {
        return journal.giveOnJoin() ? "first join" : "by command";
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        Journals.reload();
        refreshCommands(context.getSource().getServer());
        context.getSource().sendSuccess(() -> Component.literal("Reloaded journal files"), false);
        return 1;
    }

    // The level may have changed, and the client only offers the commands its player may use
    public static void refreshCommands(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
    }

    private static int give(CommandContext<CommandSourceStack> context, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Journal journal = id == null ? Journals.defaultJournal() : Journals.get(id);
        if (journal == null) {
            return fail(context, unknown(id));
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer target : targets) {
            TravelersJournal.give(target, journal);
        }
        if (id == null) {
            context.getSource().sendSuccess(() -> Component.literal("Given journal to specified players"), false);
        } else {
            String players = targets.size() == 1 ? "1 player" : targets.size() + " players";
            context.getSource().sendSuccess(() -> Component.literal("Gave " + journal.title() + " to " + players), false);
        }
        return targets.size();
    }

    private static int create(CommandContext<CommandSourceStack> context, String id) {
        if (!Journals.validId(id)) {
            return fail(context, Journal.DEFAULT_ID.equals(id) ? taken(id) : INVALID_ID);
        }
        if (Journals.get(id) != null) {
            return fail(context, taken(id));
        }
        try {
            String title = Journals.titleFor(id);
            Journal journal = Journals.create(id, title, Journals.defaultJournal().author(), false, Journals.starterText(title));
            context.getSource().sendSuccess(() -> Component.literal("Created journal " + id + ". Its text is in "
                    + journal.textPath() + "."), true);
            return 1;
        } catch (Exception e) {
            TravelersJournal.logError("[TJ] Failed to create journal {}: {}", id, e.toString());
            return fail(context, "Could not create the journal: " + e.getMessage());
        }
    }

    private static int delete(CommandContext<CommandSourceStack> context, String id) {
        Journal journal = Journals.get(id);
        if (journal == null) {
            return fail(context, unknown(id));
        }
        if (journal.isDefault()) {
            return fail(context, "The default journal cannot be deleted. Set give_on_join=false in config.txt to stop giving it to new players.");
        }
        try {
            Path backup = Journals.delete(journal);
            context.getSource().sendSuccess(() -> Component.literal("Deleted journal " + id + ". Its files are in "
                    + backup.toString().replace('\\', '/') + ". Copies players already carry stay as signed books."), true);
            return 1;
        } catch (Exception e) {
            TravelersJournal.logError("[TJ] Failed to delete journal {}: {}", id, e.toString());
            return fail(context, "Could not delete the journal: " + e.getMessage());
        }
    }

    private static int edit(CommandContext<CommandSourceStack> context, String id) {
        Journal journal = Journals.get(id);
        if (journal == null) {
            return fail(context, unknown(id));
        }
        //? if >=1.21.6 {
        ServerPlayer player = context.getSource().getPlayer();
        if (player != null) {
            JournalScreens.editor(player, journal);
            return 1;
        }
        String message = "Edit " + journal.textPath() + ", then run /journals reload.";
        //?} else {
        /*String message = "Dialogs need Minecraft 1.21.6 or newer. Edit " + journal.textPath() + ", then run /journals reload.";
        *///?}
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    static final String INVALID_ID = "Journal ids use lower-case letters, digits and -, up to 32 characters.";

    static String taken(String id) {
        return "There is already a journal called " + id + ".";
    }

    static String unknown(String id) {
        return "No journal has the id " + id + ".";
    }

    private static int fail(CommandContext<CommandSourceStack> context, String message) {
        context.getSource().sendFailure(Component.literal(message));
        return 0;
    }
}
