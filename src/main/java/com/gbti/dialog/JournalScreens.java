package com.gbti.dialog;

import com.gbti.Journal;
import com.gbti.JournalBook;
import com.gbti.JournalCommands;
import com.gbti.Journals;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The journal manager's screens, as picked in sow-028's design round (option A, "One form"): the
 * hub, the editor, the create form, the delete confirmation and the preview. Copy follows the
 * design spec; a status line is green for done and red for refused.
 */
public final class JournalScreens {
    private static final int BUTTON = 200;
    private static final int HALF = 150;
    // A written book page is about 114 units wide
    private static final int PAGE = 114;

    private JournalScreens() {
    }

    /** A line under a screen's title: green for something done, red for something refused. */
    record Status(Component line, String field) {
        static Status done(String text) {
            return new Status(Component.literal(text).withStyle(ChatFormatting.GREEN), null);
        }

        static Status refused(String text, String field) {
            return new Status(Component.literal(text).withStyle(ChatFormatting.RED), field);
        }
    }

    public static void hub(ServerPlayer player) {
        hub(player, null);
    }

    static void hub(ServerPlayer player, Status status) {
        List<Journal> journals = Journals.all();
        List<DialogBody> body = new ArrayList<>();
        if (status != null) body.add(Widgets.line(status.line()));
        String count = journals.size() == 1 ? "1 journal." : journals.size() + " journals.";
        body.add(Widgets.line(Component.literal(count + " The ones marked first join go to every new player once.")));

        List<ActionButton> buttons = new ArrayList<>();
        for (Journal journal : journals) {
            Component label = Component.literal(journal.title())
                    .append(Component.literal("  " + JournalCommands.giveRule(journal)).withStyle(ChatFormatting.GRAY));
            buttons.add(Widgets.button(label, BUTTON, "edit", Form.of(journal).journalTag()));
        }
        buttons.add(Widgets.button("Create journal", BUTTON, "create_form", new CompoundTag()));
        buttons.add(Widgets.button("Reload from files", BUTTON, "reload", new CompoundTag()));
        Widgets.open(player, new MultiActionDialog(Widgets.common(Component.literal("Traveler's Journals"), body, List.of()),
                buttons, Optional.of(Widgets.button("Done", BUTTON, "close", new CompoundTag())), 1));
    }

    public static void editor(ServerPlayer player, Journal journal) {
        editor(player, journal, Form.of(journal), null);
    }

    static void editor(ServerPlayer player, Journal journal, Form form, Status status) {
        List<DialogBody> body = new ArrayList<>();
        if (status != null) body.add(Widgets.line(status.line()));
        body.add(Widgets.line(grey("Id " + journal.id() + ", saved in " + journal.textName())));
        if (form.text() == null) {
            body.add(Widgets.line(grey("This journal has " + String.format(Locale.ROOT, "%,d", journal.text().length())
                    + " characters, more than the editor holds (" + String.format(Locale.ROOT, "%,d", Form.TEXT_MAX) + "). Edit "
                    + journal.textPath() + ", then run /journals reload.")));
        }
        List<Input> inputs = new ArrayList<>(bookInputs(form, status));
        if (form.text() != null) inputs.add(Widgets.textBox("text", label("Text", status, "text"), form.text(), Form.TEXT_MAX));

        CompoundTag target = form.journalTag();
        List<ActionButton> buttons = new ArrayList<>(List.of(
                Widgets.button("Save", HALF, "save", target),
                Widgets.button("Preview", HALF, "preview", target),
                Widgets.button("Give to me", HALF, "give_me", target)));
        // The default journal's files hold the mod's settings, so it is switched off, not deleted
        if (!journal.isDefault()) buttons.add(Widgets.button("Delete journal", HALF, "delete_ask", target));
        Widgets.open(player, new MultiActionDialog(Widgets.common(Component.literal(journal.title()), body, inputs),
                buttons, Optional.of(Widgets.button("Back", BUTTON, "hub", new CompoundTag())), 2));
    }

    public static void create(ServerPlayer player, Form form, Status status) {
        List<DialogBody> body = new ArrayList<>();
        if (status != null) body.add(Widgets.line(status.line()));
        List<Input> inputs = new ArrayList<>();
        inputs.add(Widgets.text("id", label("Id: lower-case letters, digits and -", status, "id"), form.id(), 32));
        inputs.addAll(bookInputs(form, status));
        inputs.add(Widgets.textBox("text", label("Text", status, "text"), form.text() == null ? "" : form.text(), Form.TEXT_MAX));
        Widgets.open(player, new MultiActionDialog(Widgets.common(Component.literal("New journal"), body, inputs),
                List.of(Widgets.button("Create", BUTTON, "create", new CompoundTag())),
                Optional.of(Widgets.button("Back", BUTTON, "hub", new CompoundTag())), 1));
    }

    public static void confirmDelete(ServerPlayer player, Journal journal) {
        List<DialogBody> body = List.of(
                Widgets.line(Component.literal("New players stop getting it. Its files move to backups/journals/" + journal.id()
                        + ", so it can be restored by hand.")),
                Widgets.line(grey("Copies players already carry stay as signed books.")));
        CompoundTag target = Form.of(journal).journalTag();
        Widgets.open(player, new ConfirmationDialog(Widgets.common(Component.literal("Delete " + journal.title() + "?"), body, List.of()),
                Widgets.button("Delete", HALF, "delete", target), Widgets.button("Keep it", HALF, "edit", target)));
    }

    // Each page under a grey "Page n of m", book-page wide; Back returns the unsaved values
    public static void preview(ServerPlayer player, Journal journal, Form form) {
        List<String> lines = form.text() == null ? journal.lines() : Arrays.asList(form.text().split("\n", -1));
        List<Component> pages = JournalBook.pages(lines, true);
        List<DialogBody> body = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            body.add(Widgets.line(grey("Page " + (i + 1) + " of " + pages.size()), PAGE));
            body.add(Widgets.line(pages.get(i), PAGE));
        }
        String title = form.title().isEmpty() ? journal.title() : form.title();
        Widgets.open(player, new NoticeDialog(Widgets.common(Component.literal(title), body, List.of()),
                Widgets.button("Back", BUTTON, "back", form.fullTag())));
    }

    private static List<Input> bookInputs(Form form, Status status) {
        return List.of(
                Widgets.text("title", label("Book title, " + form.title().length() + " of " + Journals.TITLE_MAX + " characters", status, "title"),
                        form.title(), Journals.TITLE_MAX),
                Widgets.text("author", label("Author", status, "author"), form.author(), Journals.AUTHOR_MAX),
                Widgets.toggle("give", Component.literal("Give on first join"), form.give()));
    }

    // A field's label turns red when the status line is about it
    private static Component label(String text, Status status, String field) {
        Component label = Component.literal(text);
        return status != null && field.equals(status.field()) ? label.copy().withStyle(ChatFormatting.RED) : label;
    }

    private static Component grey(String text) {
        return Component.literal(text).withStyle(ChatFormatting.GRAY);
    }
}
