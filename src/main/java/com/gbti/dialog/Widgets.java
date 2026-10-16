package com.gbti.dialog;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundClearDialogPacket;
import net.minecraft.server.dialog.ActionButton;
import net.minecraft.server.dialog.CommonButtonData;
import net.minecraft.server.dialog.CommonDialogData;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.DialogAction;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.dialog.body.DialogBody;
import net.minecraft.server.dialog.body.PlainMessage;
import net.minecraft.server.dialog.input.BooleanInput;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.List;
import java.util.Optional;

/**
 * Building blocks for the journal dialogs, after Jockey Radio's DialogWidgets. Every dialog keeps
 * the game running (pause false) and stays open after a click (NONE), so the server's reply
 * replaces it; Escape closes it.
 */
final class Widgets {
    static final int WIDE = 300;
    // A multi-line box of about ten lines; the client scrolls the rest
    private static final int TEXT_HEIGHT = 100;

    private Widgets() {
    }

    static CommonDialogData common(Component title, List<DialogBody> body, List<Input> inputs) {
        return new CommonDialogData(title, Optional.empty(), true, false, DialogAction.NONE, body, inputs);
    }

    static DialogBody line(Component text) {
        return new PlainMessage(text, WIDE);
    }

    static DialogBody line(Component text, int width) {
        return new PlainMessage(text, width);
    }

    static ActionButton button(String label, int width, String action, CompoundTag additions) {
        return new ActionButton(new CommonButtonData(Component.literal(label), width), Optional.of(custom(action, additions)));
    }

    static ActionButton button(Component label, int width, String action, CompoundTag additions) {
        return new ActionButton(new CommonButtonData(label, width), Optional.of(custom(action, additions)));
    }

    // Sends every input with the button's additions; an input overwrites an addition of the same key
    private static Action custom(String action, CompoundTag additions) {
        //? if >=1.21.11 {
        return new CustomAll(Identifier.fromNamespaceAndPath(DialogRouter.NAMESPACE, action), Optional.of(additions.copy()));
        //?} else {
        /*return new CustomAll(ResourceLocation.fromNamespaceAndPath(DialogRouter.NAMESPACE, action), Optional.of(additions.copy()));
        *///?}
    }

    // The codec refuses an initial value longer than the box allows, so it is shortened first
    static Input text(String key, Component label, String initial, int maxLength) {
        String value = initial == null ? "" : initial.length() > maxLength ? initial.substring(0, maxLength) : initial;
        return new Input(key, new TextInput(WIDE, label, true, value, maxLength, Optional.empty()));
    }

    static Input textBox(String key, Component label, String initial, int maxLength) {
        String value = initial == null ? "" : initial.length() > maxLength ? initial.substring(0, maxLength) : initial;
        return new Input(key, new TextInput(WIDE, label, true, value, maxLength,
                Optional.of(new TextInput.MultilineOptions(Optional.empty(), Optional.of(TEXT_HEIGHT)))));
    }

    static Input toggle(String key, Component label, boolean initial) {
        return new Input(key, new BooleanInput(label, initial, "true", "false"));
    }

    static void open(ServerPlayer player, Dialog dialog) {
        player.openDialog(Holder.direct(dialog));
    }

    static void close(ServerPlayer player) {
        player.connection.send(ClientboundClearDialogPacket.INSTANCE);
    }
}
