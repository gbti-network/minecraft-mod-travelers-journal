package com.gbti.dialog;

import com.gbti.Journal;
import net.minecraft.nbt.CompoundTag;

/**
 * The values an editor shows: a saved journal's, or ones a player submitted and has not saved, so
 * Preview and a refused Save can show the form again as it was. text is null when the journal is
 * too long for the editor's box; the file is then edited directly and Save keeps its text.
 */
record Form(String id, String title, String author, boolean give, String text) {
    // A click payload is read with a 32,768-byte budget that counts 2 bytes a character. 15,000
    // characters of text leave room for the other fields.
    static final int TEXT_MAX = 15_000;

    static Form of(Journal journal) {
        String text = journal.text();
        return new Form(journal.id(), journal.title(), journal.author(), journal.giveOnJoin(),
                text.length() <= TEXT_MAX ? text : null);
    }

    static Form blank(String author) {
        return new Form("", "", author, false, "");
    }

    static Form read(CompoundTag tag) {
        String text = tag.contains("text") ? tag.getStringOr("text", "").replace("\r\n", "\n") : null;
        return new Form(tag.getStringOr("journal", tag.getStringOr("id", "")), tag.getStringOr("title", ""),
                tag.getStringOr("author", ""), tag.getBooleanOr("give", false), text);
    }

    // Which journal a button acts on; the inputs travel beside it
    CompoundTag journalTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("journal", id);
        return tag;
    }

    // Everything, for a screen without inputs (the preview) to hand back to the editor
    CompoundTag fullTag() {
        CompoundTag tag = journalTag();
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putBoolean("give", give);
        if (text != null) tag.putString("text", text);
        return tag;
    }
}
