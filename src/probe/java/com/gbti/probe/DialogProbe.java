package com.gbti.probe;

import com.gbti.Journal;
import com.gbti.Journals;
import com.gbti.dialog.BookLayout;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.network.protocol.common.ClientboundShowDialogPacket;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.ConfirmationDialog;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.MultiActionDialog;
import net.minecraft.server.dialog.NoticeDialog;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.ItemBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.gbti.probe.ProbeSupport.command;
import static com.gbti.probe.ProbeSupport.copies;
import static com.gbti.probe.ProbeSupport.oneLine;

/**
 * The in-game manager, on 1.21.6 and later only (the build leaves this file out of older jars).
 * A mock operator opens the dialogs by command and presses their buttons as a client does: each
 * press takes the action and additions of a button in the dialog the server sent, overlays every
 * input's value, and goes through the click packet's codec, vanilla's listener and the mod's mixin.
 * What the server sends back is read from the mock player's channel.
 */
final class DialogProbe {
    static final int CHECKS = 20;
    private static final Path FOLDERS = Path.of("config/travelers-journal/journals");
    private static final Path MADE = FOLDERS.resolve("probe-dialog");
    private static final Path LONG = FOLDERS.resolve("probe-long");

    private final ProbeSupport.Checks checks;
    private ServerPlayer carol;
    private Dialog shown;
    private boolean cleared;
    private String chat = "";

    DialogProbe(ProbeSupport.Checks checks) {
        this.checks = checks;
    }

    void run(MinecraftServer server) throws Exception {
        CommandSourceStack console = server.createCommandSourceStack();
        carol = ProbeSupport.join(server, "ProbeCarol");
        command(server, console, "op ProbeCarol");

        // A player's bare /journals opens the hub
        as(server, "journals");
        // The hub's one icon is a plain book with no tooltip, its caption box sized to the count so
        // the pair sits centred (owner, 2026-10-16)
        boolean hubIcon = common() != null && common().body().stream().filter(ItemBody.class::isInstance).map(ItemBody.class::cast)
                .anyMatch(icon -> !icon.showTooltip() && icon.description().map(caption -> caption.width() < 100).orElse(false));
        checks.check("dialog-hub-opens", shown instanceof MultiActionDialog hub && title().equals("Traveler's Journals")
                && labels(hub).contains("Create journal") && labels(hub).stream().anyMatch(label -> label.startsWith("Traveler's Journal")),
                "shown " + describe());
        // Each journal by its title alone, in yellow (owner, 2026-10-16), whether it goes to new
        // players or not, and the count line says nothing more
        boolean hubStyled = shown instanceof MultiActionDialog styled && body().trim().equals(Journals.all().size() + " journals.")
                && labels(styled).contains("Pages") && labels(styled).contains("Traveler's Journal") && labels(styled).contains("Welcome aboard")
                && styled.actions().stream().map(button -> button.button().label())
                        .filter(label -> Journals.all().stream().anyMatch(journal -> journal.title().equals(label.getString())))
                        .allMatch(label -> colour(label, label.getString(), ChatFormatting.YELLOW));

        press("Reload from files", Map.of());
        checks.check("dialog-reload", title().equals("Traveler's Journals") && body().contains("Reloaded from files."), "shown " + describe());

        // Create, then Save, through the buttons
        press("Create journal", Map.of());
        checks.check("dialog-create-form", title().equals("New journal") && inputs().containsAll(List.of("id", "title", "author", "give", "text")),
                "shown " + describe());
        press("Create", Map.of("id", "probe-dialog", "title", "Dialog journal", "author", "Probe", "give", false, "text", "Made in a dialog"));
        checks.check("dialog-create", Journals.get("probe-dialog") != null
                && Files.readString(MADE.resolve("journal.md")).equals("Made in a dialog\n") && body().contains("Created."),
                "shown " + describe());
        press("Save", Map.of("title", "Saved by dialog", "give", true, "text", "Line one\n[page]\nLine two"));
        Journal saved = Journals.get("probe-dialog");
        checks.check("dialog-save", saved != null && saved.title().equals("Saved by dialog") && saved.giveOnJoin()
                && Files.readString(MADE.resolve("journal.md")).equals("Line one\n[page]\nLine two\n")
                && Files.readString(MADE.resolve("journal.md.bak")).equals("Made in a dialog\n")
                && body().contains("Saved. Copies refresh the next time players use them."), "shown " + describe());

        // The server checks every Save again: a 33-character title is refused and nothing changes
        String text = Files.readString(MADE.resolve("journal.md"));
        press("Save", Map.of("title", "x".repeat(33), "text", "Changed"));
        checks.check("dialog-title-too-long", Files.readString(MADE.resolve("journal.md")).equals(text)
                && body().contains("Book titles hold 32 characters. This one has 33. Nothing was saved.") && redLabel("title"),
                "shown " + describe());

        // Preview shows the unsaved text as pages and saves nothing; Back returns to it unsaved
        String pages = "Page one\n[page]\nPage two\n[page]\nPage three";
        press("Preview", Map.of("title", "Saved by dialog", "text", pages));
        boolean previewIcon = hasIcon() && body().contains("3 pages, by Probe");
        checks.check("dialog-preview", shown instanceof NoticeDialog && body().contains("Page 1 of 3") && body().contains("Page 3 of 3")
                && body().contains("Page two") && Files.readString(MADE.resolve("journal.md")).equals(text), "shown " + describe());
        press("Back", Map.of());
        checks.check("dialog-back-keeps-unsaved", shown instanceof MultiActionDialog && pages.equals(initial("text"))
                && "Saved by dialog".equals(initial("title")) && Files.readString(MADE.resolve("journal.md")).equals(text),
                "text box holds " + oneLine(String.valueOf(initial("text"))) + ", shown " + describe());

        // The client centres dialog text, so each page line is wrapped at the book's width and
        // padded to it. A section past the book's 14 lines now carries on on a second page, so
        // nothing is cut off
        String paragraph = "A long paragraph that the book wraps over several lines, word by word, the way a player reads it.";
        String crowded = paragraph + "\n" + String.join("\n", java.util.Collections.nCopies(13, "Short line"));
        press("Preview", Map.of("text", crowded));
        List<Integer> widths = shown instanceof NoticeDialog ? common().body().stream().filter(PlainMessage.class::isInstance)
                .map(part -> BookLayout.lineWidths(((PlainMessage) part).contents())).findFirst().orElse(List.of()) : List.of();
        boolean padded = widths.size() == 1 + BookLayout.PAGE_LINES
                && widths.stream().allMatch(width -> width >= BookLayout.PAGE_WIDTH - 4 && width <= BookLayout.PAGE_WIDTH);
        checks.check("dialog-preview-layout", padded && body().contains("Page 2 of 2") && !body().contains("will not show"),
                "line widths " + widths + ", shown " + describe());
        press("Back", Map.of());

        press("Give to me", Map.of());
        checks.check("dialog-give-me", copies(carol, Journals.get("probe-dialog")) == 1
                && body().contains("A copy of the saved journal is in your inventory."), "copies " + copies(carol, Journals.get("probe-dialog"))
                + ", shown " + describe());
        press("Back", Map.of());
        checks.check("dialog-back-to-hub", shown instanceof MultiActionDialog && title().equals("Traveler's Journals"), "shown " + describe());

        // Each hub button opens its own journal. The default journal is switched off, never
        // deleted, so its editor has no Delete
        press("Traveler's Journal", Map.of());
        boolean editorIcon = hasIcon() && body().contains("Id default, saved in travelers_journal.md");
        boolean defaultOpened = title().equals("Traveler's Journal");
        boolean defaultHasDelete = shown instanceof MultiActionDialog editor && labels(editor).contains("Delete journal");
        press("Back", Map.of());
        press("Saved by dialog", Map.of());
        boolean madeOpened = title().equals("Saved by dialog");
        boolean madeHasDelete = shown instanceof MultiActionDialog editor && labels(editor).contains("Delete journal");
        checks.check("dialog-default-no-delete", defaultOpened && madeOpened && !defaultHasDelete && madeHasDelete,
                "default opened " + defaultOpened + " with Delete " + defaultHasDelete + ", made opened " + madeOpened + " with Delete " + madeHasDelete);

        as(server, "journals edit probe-welcome");
        checks.check("dialog-edit-command", title().equals("Welcome aboard") && inputs().contains("text"), "shown " + describe());

        // A journal too long for the box opens without it, and Save keeps its text
        Files.createDirectories(LONG);
        Files.writeString(LONG.resolve("journal.txt"), "title=Long journal\nauthor=Probe\ngive_on_join=false\n", StandardCharsets.UTF_8);
        String longText = ("A line of a very long journal, repeated until it no longer fits the box.\n").repeat(220);
        Files.writeString(LONG.resolve("journal.md"), longText, StandardCharsets.UTF_8);
        press("Back", Map.of());
        press("Reload from files", Map.of());
        press("Long journal", Map.of());
        boolean noBox = !inputs().contains("text") && body().contains("more than the editor holds");
        press("Save", Map.of("title", "Long journal, renamed"));
        checks.check("dialog-long-text-kept", noBox && Files.readString(LONG.resolve("journal.md")).equals(longText)
                && Journals.get("probe-long").title().equals("Long journal, renamed"), "no box " + noBox + ", shown " + describe());

        // A player who is no longer an operator is refused, whatever the payload says
        command(server, console, "deop ProbeCarol");
        send(new ServerboundCustomClickActionPacket(id("save"), Optional.of(form("probe-dialog", "Hijacked", "Probe", true, "Hijacked"))));
        checks.check("dialog-refuses-non-op", Files.readString(MADE.resolve("journal.md")).equals(text)
                && Journals.get("probe-dialog").title().equals("Saved by dialog") && chat.contains("Only operators can manage journals."),
                "chat " + oneLine(chat));
        command(server, console, "op ProbeCarol");

        // Delete asks first, then moves the folder to a backup and returns to the hub
        as(server, "journals edit probe-dialog");
        press("Delete journal", Map.of());
        boolean deleteIcon = hasIcon();
        checks.check("dialog-icons", hubIcon && editorIcon && previewIcon && deleteIcon, "hub " + hubIcon + ", editor " + editorIcon
                + ", preview " + previewIcon + ", delete " + deleteIcon);
        checks.check("dialog-hub-styled", hubStyled, "hub as first shown, styled " + hubStyled);
        boolean asked = shown instanceof ConfirmationDialog && title().equals("Delete Saved by dialog?");
        press("Delete", Map.of());
        checks.check("dialog-delete", asked && Journals.get("probe-dialog") == null && !Files.exists(MADE)
                && body().contains("Deleted Saved by dialog."), "asked " + asked + ", shown " + describe());

        press("Done", Map.of());
        checks.check("dialog-done-closes", cleared && shown == null, "cleared " + cleared + ", shown " + describe());

        // The text box's 15,000 characters fit the click payload's budget; more would not
        checks.check("dialog-payload-budget", fits(15_000) && !fits(16_500), "15,000 fits " + fits(15_000) + ", 16,500 fits " + fits(16_500));

        server.getPlayerList().remove(carol);
    }

    // Presses a button of the dialog on screen as a client does: the button's own action and
    // additions, then every input's value over them, typed here or as the dialog shows it
    private void press(String label, Map<String, Object> typed) {
        ActionButton button = buttons().stream().filter(candidate -> candidate.button().label().getString().equals(label)).findFirst()
                .orElseThrow(() -> new IllegalStateException("no button '" + label + "' on " + describe()));
        Action action = button.action().orElseThrow(() -> new IllegalStateException("button '" + label + "' does nothing"));
        if (!(action instanceof CustomAll custom)) throw new IllegalStateException("button '" + label + "' sends " + action);
        CompoundTag payload = custom.additions().map(CompoundTag::copy).orElseGet(CompoundTag::new);
        for (Input input : common().inputs()) {
            Object value = typed.containsKey(input.key()) ? typed.get(input.key()) : initial(input.key());
            if (value instanceof Boolean flag) payload.putBoolean(input.key(), flag);
            else payload.putString(input.key(), (String) value);
        }
        send(new ServerboundCustomClickActionPacket(custom.id(), Optional.of(payload)));
    }

    // Through the click packet's codec, as it crosses the network, then vanilla's listener
    private void send(ServerboundCustomClickActionPacket packet) {
        drain();
        ByteBuf buffer = Unpooled.buffer();
        ServerboundCustomClickActionPacket received;
        try {
            ServerboundCustomClickActionPacket.STREAM_CODEC.encode(buffer, packet);
            received = ServerboundCustomClickActionPacket.STREAM_CODEC.decode(buffer);
        } finally {
            buffer.release();
        }
        carol.connection.handleCustomClickAction(received);
        read();
    }

    // A payload written by hand, as a modified client could send it
    private static CompoundTag form(String journal, String title, String author, boolean give, String text) {
        CompoundTag tag = new CompoundTag();
        tag.putString("journal", journal);
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putBoolean("give", give);
        tag.putString("text", text);
        return tag;
    }

    private void as(MinecraftServer server, String commandLine) {
        drain();
        command(server, carol.createCommandSourceStack(), commandLine);
        read();
    }

    private void drain() {
        EmbeddedChannel channel = ProbeSupport.channel(carol);
        while (channel.readOutbound() != null) {
            // everything sent before this step is not about it
        }
        shown = null;
        cleared = false;
        chat = "";
    }

    private void read() {
        EmbeddedChannel channel = ProbeSupport.channel(carol);
        channel.flushOutbound();
        for (Object message = channel.readOutbound(); message != null; message = channel.readOutbound()) {
            if (message instanceof ClientboundShowDialogPacket packet) shown = packet.dialog().value();
            if (message instanceof ClientboundClearDialogPacket) cleared = true;
            if (message instanceof ClientboundSystemChatPacket packet) chat += packet.content().getString() + "\n";
        }
    }

    private List<ActionButton> buttons() {
        List<ActionButton> all = new ArrayList<>();
        if (shown instanceof MultiActionDialog dialog) {
            all.addAll(dialog.actions());
            dialog.exitAction().ifPresent(all::add);
        }
        if (shown instanceof NoticeDialog dialog) all.add(dialog.action());
        if (shown instanceof ConfirmationDialog dialog) {
            all.add(dialog.yesButton());
            all.add(dialog.noButton());
        }
        return all;
    }

    // An input's value as the dialog shows it before anything is typed
    private Object initial(String key) {
        if (common() == null) return null;
        for (Input input : common().inputs()) {
            if (!input.key().equals(key)) continue;
            if (input.control() instanceof TextInput box) return box.initial();
            if (input.control() instanceof BooleanInput toggle) return toggle.initial();
            throw new IllegalStateException("input " + key + " is " + input.control());
        }
        return null;
    }

    private CommonDialogData common() {
        if (shown instanceof MultiActionDialog dialog) return dialog.common();
        if (shown instanceof NoticeDialog dialog) return dialog.common();
        if (shown instanceof ConfirmationDialog dialog) return dialog.common();
        return null;
    }

    private String title() {
        return common() == null ? "" : common().title().getString();
    }

    // A journal book drawn as an item, as Jockey Radio draws its discs
    private boolean hasIcon() {
        return common() != null && common().body().stream().anyMatch(part -> part instanceof ItemBody);
    }

    private String body() {
        if (common() == null) return "";
        StringBuilder text = new StringBuilder();
        for (DialogBody part : common().body()) {
            if (part instanceof PlainMessage message) text.append(message.contents().getString()).append('\n');
            if (part instanceof ItemBody item) item.description().ifPresent(caption -> text.append(caption.contents().getString()).append('\n'));
        }
        return text.toString();
    }

    private List<String> inputs() {
        return common() == null ? List.of() : common().inputs().stream().map(Input::key).toList();
    }

    private boolean redLabel(String key) {
        if (common() == null) return false;
        return common().inputs().stream().anyMatch(input -> input.key().equals(key) && input.control() instanceof TextInput box
                && Objects.equals(box.label().getStyle().getColor(), TextColor.fromLegacyFormat(ChatFormatting.RED)));
    }

    // Whether the run of a label with this exact text has this colour
    private static boolean colour(net.minecraft.network.chat.Component label, String run, ChatFormatting expected) {
        return label.visit((style, text) -> text.equals(run) ? Optional.of(Objects.equals(style.getColor(), TextColor.fromLegacyFormat(expected)))
                : Optional.<Boolean>empty(), net.minecraft.network.chat.Style.EMPTY).orElse(false);
    }

    private static List<String> labels(MultiActionDialog dialog) {
        return dialog.actions().stream().map(button -> button.button().label().getString()).toList();
    }

    private String describe() {
        return shown == null ? "nothing" : shown.getClass().getSimpleName() + " '" + title() + "': " + oneLine(body())
                + " inputs " + inputs();
    }

    // Encodes and decodes a full-size form through the packet's own codec
    private static boolean fits(int characters) {
        CompoundTag tag = form("a".repeat(32), "b".repeat(32), "c".repeat(32), true, "é".repeat(characters));
        ServerboundCustomClickActionPacket packet = new ServerboundCustomClickActionPacket(id("save"), Optional.of(tag));
        ByteBuf buffer = Unpooled.buffer();
        try {
            ServerboundCustomClickActionPacket.STREAM_CODEC.encode(buffer, packet);
            return ServerboundCustomClickActionPacket.STREAM_CODEC.decode(buffer).payload().equals(packet.payload());
        } catch (Exception e) {
            return false;
        } finally {
            buffer.release();
        }
    }

    //? if >=1.21.11 {
    private static Identifier id(String action) {
        return Identifier.fromNamespaceAndPath("travelers_journal", action);
    }
    //?} else {
    /*private static ResourceLocation id(String action) {
        return ResourceLocation.fromNamespaceAndPath("travelers_journal", action);
    }
    *///?}
}
