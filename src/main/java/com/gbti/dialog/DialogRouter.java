package com.gbti.dialog;

import com.gbti.Journal;
import com.gbti.JournalCommands;
import com.gbti.Journals;
import com.gbti.TravelersJournal;
import com.gbti.dialog.JournalScreens.Status;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Handles a click on a journal dialog button. Nothing the client sends is trusted: the player's
 * level is checked again on every click, the journal must exist, and every field is checked
 * before anything is written. Runs on the server thread (see CustomClickActionMixin).
 */
public final class DialogRouter {
    public static final String NAMESPACE = "travelers_journal";
    private static final String GONE = "That journal no longer exists.";

    private DialogRouter() {
    }

    public static void handle(ServerPlayer player, String action, Optional<Tag> payload) {
        CompoundTag tag = payload.isPresent() && payload.get() instanceof CompoundTag compound ? compound : new CompoundTag();
        if (!JournalCommands.allowed(player.createCommandSourceStack())) {
            player.sendSystemMessage(Component.literal("Only operators can manage journals.").withStyle(ChatFormatting.RED));
            Widgets.close(player);
            return;
        }
        Form form = Form.read(tag);
        Journal journal = Journals.get(form.id());
        switch (action) {
            case "close" -> Widgets.close(player);
            case "hub" -> JournalScreens.hub(player, null);
            case "reload" -> {
                Journals.reload();
                JournalCommands.refreshCommands(player.createCommandSourceStack().getServer());
                JournalScreens.hub(player, Status.done("Reloaded from files."));
            }
            case "create_form" -> JournalScreens.create(player, Form.blank(Journals.defaultJournal().author()), null);
            case "create" -> create(player, form);
            default -> {
                if (journal == null) {
                    JournalScreens.hub(player, Status.refused(GONE, null));
                } else {
                    onJournal(player, action, journal, form);
                }
            }
        }
    }

    private static void onJournal(ServerPlayer player, String action, Journal journal, Form form) {
        switch (action) {
            case "edit" -> JournalScreens.editor(player, journal, Form.of(journal), null);
            case "back" -> JournalScreens.editor(player, journal, form, null);
            case "save" -> save(player, journal, form);
            case "preview" -> JournalScreens.preview(player, journal, form);
            case "give_me" -> {
                TravelersJournal.give(player, journal);
                JournalScreens.editor(player, journal, form, Status.done("A copy of the saved journal is in your inventory."));
            }
            case "delete_ask" -> {
                if (journal.isDefault()) JournalScreens.editor(player, journal, form, null);
                else JournalScreens.confirmDelete(player, journal);
            }
            case "delete" -> delete(player, journal);
            default -> TravelersJournal.logDebug("Ignored journal dialog action {}", action);
        }
    }

    private static void save(ServerPlayer player, Journal journal, Form form) {
        Status refused = check(form, "saved");
        if (refused != null) {
            JournalScreens.editor(player, journal, form, refused);
            return;
        }
        try {
            Journal saved = Journals.save(journal, form.title(), form.author(), form.give(), form.text());
            JournalScreens.editor(player, saved, Form.of(saved), Status.done("Saved. Copies refresh the next time players use them."));
        } catch (Exception e) {
            TravelersJournal.logError("[TJ] Failed to save journal {}: {}", journal.id(), e.toString());
            JournalScreens.editor(player, journal, form, Status.refused("The files could not be written: " + e.getMessage(), null));
        }
    }

    private static void create(ServerPlayer player, Form form) {
        String id = form.id();
        Status refused = null;
        if (!Journals.validId(id) && !Journal.DEFAULT_ID.equals(id)) {
            refused = Status.refused("Ids use lower-case letters, digits and -, up to 32 characters. Nothing was created.", "id");
        } else if (Journal.DEFAULT_ID.equals(id) || Journals.get(id) != null) {
            refused = Status.refused("There is already a journal called " + id + ". Nothing was created.", "id");
        }
        String title = form.title().isBlank() && refused == null ? Journals.titleFor(id) : form.title();
        Form filled = new Form(id, title, form.author(), form.give(), form.text() == null ? "" : form.text());
        if (refused == null) refused = check(filled, "created");
        if (refused != null) {
            JournalScreens.create(player, filled, refused);
            return;
        }
        try {
            Journal created = Journals.create(id, filled.title(), filled.author(), filled.give(), filled.text());
            JournalScreens.editor(player, created, Form.of(created), Status.done("Created. It is saved in " + created.textName() + "."));
        } catch (Exception e) {
            TravelersJournal.logError("[TJ] Failed to create journal {}: {}", id, e.toString());
            JournalScreens.create(player, filled, Status.refused("The files could not be written: " + e.getMessage(), null));
        }
    }

    private static void delete(ServerPlayer player, Journal journal) {
        if (journal.isDefault()) {
            JournalScreens.editor(player, journal, Form.of(journal), null);
            return;
        }
        try {
            Journals.delete(journal);
            JournalScreens.hub(player, Status.done("Deleted " + journal.title() + "."));
        } catch (Exception e) {
            TravelersJournal.logError("[TJ] Failed to delete journal {}: {}", journal.id(), e.toString());
            JournalScreens.editor(player, journal, Form.of(journal), Status.refused("The files could not be moved: " + e.getMessage(), null));
        }
    }

    // The same limits the boxes hold, checked again here because a client can send anything
    private static Status check(Form form, String verb) {
        String nothing = " Nothing was " + verb + ".";
        if (form.title().isBlank()) {
            return Status.refused("Book titles need at least one character." + nothing, "title");
        }
        if (form.title().length() > Journals.TITLE_MAX) {
            return Status.refused("Book titles hold " + Journals.TITLE_MAX + " characters. This one has "
                    + form.title().length() + "." + nothing, "title");
        }
        if (form.author().length() > Journals.AUTHOR_MAX) {
            return Status.refused("Authors hold " + Journals.AUTHOR_MAX + " characters. This one has "
                    + form.author().length() + "." + nothing, "author");
        }
        if (form.text() != null && form.text().length() > Form.TEXT_MAX) {
            return Status.refused("The text holds 15,000 characters. This one has " + form.text().length() + "." + nothing, "text");
        }
        return null;
    }
}
