package com.gbti;

import com.gbti.utils.FormattedString;
import com.gbti.utils.StringUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a journal as a written book item from its text. The book is assembled from text
 * components rather than a /give command string, so the text needs no escaping and is read the
 * same way on every Minecraft version. Each book carries its journal's id as custom data.
 */
public final class JournalBook {
    // The custom data key holding the journal id: {travelers_journal:"<id>"}
    public static final String ID_KEY = "travelers_journal";
    // A written book holds at most 100 pages; the rest of a longer journal is left out
    private static final int MAX_PAGES = 100;

    private JournalBook() {
    }

    public static ItemStack create(Journal journal) {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (Component page : pages(journal.lines(), false)) {
            pages.add(Filterable.passThrough(page));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(bookTitle(journal.title())), journal.author(), 0, pages, true));
        CompoundTag tag = new CompoundTag();
        tag.putString(ID_KEY, journal.id());
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return book;
    }

    /** The pages of a journal's text. For a dialog, black is shown as white so it can be read. */
    public static List<Component> pages(List<String> lines, boolean forDialog) {
        List<Component> pages = new ArrayList<>();
        for (List<List<FormattedString>> page : StringUtils.getPages(lines)) {
            if (pages.size() == MAX_PAGES) {
                TravelersJournal.logError("[TJ] The journal is longer than {} pages; the rest is left out", MAX_PAGES);
                break;
            }
            pages.add(toComponent(page, forDialog));
        }
        return pages;
    }

    /**
     * The journal a book is a copy of, or null. A book carrying an id belongs to that journal, and
     * to none once it is deleted, so it stays an ordinary book. A book without one comes from a
     * release before 1.3.0 and is matched as those releases did: by the default journal's title or
     * one of its legacy titles.
     */
    public static Journal journalOf(ItemStack stack) {
        if (stack.getItem() != Items.WRITTEN_BOOK) {
            return null;
        }
        String id = taggedId(stack);
        if (id != null) {
            return Journals.get(id);
        }
        Journal journal = Journals.defaultJournal();
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        String bookTitle = content != null ? content.title().raw() : stack.getHoverName().getString();
        if (bookTitle.equalsIgnoreCase(bookTitle(journal.title()))) {
            return journal;
        }
        for (String legacyTitle : journal.legacyTitles()) {
            if (bookTitle.equalsIgnoreCase(legacyTitle)) {
                TravelersJournal.logDebug("Matched legacy title: {}", legacyTitle);
                return journal;
            }
        }
        return null;
    }

    public static boolean isJournal(ItemStack stack) {
        return journalOf(stack) != null;
    }

    public static String taggedId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        //? if >=1.21.5 {
        String id = tag.getStringOr(ID_KEY, "");
        //?} else {
        /*String id = tag.getString(ID_KEY);
        *///?}
        return id.isEmpty() ? null : id;
    }

    private static String bookTitle(String title) {
        return title.length() > Journals.TITLE_MAX ? title.substring(0, Journals.TITLE_MAX) : title;
    }

    private static Component toComponent(List<List<FormattedString>> lines, boolean forDialog) {
        MutableComponent page = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                page.append("\n");
            }
            for (FormattedString run : lines.get(i)) {
                page.append(Component.literal(run.getContent()).withStyle(style(run, forDialog)));
            }
        }
        return page;
    }

    private static Style style(FormattedString run, boolean forDialog) {
        // getColor() is one of the sixteen colour names, which are the enum's names in lower case
        ChatFormatting color = ChatFormatting.valueOf(run.getColor().toUpperCase(Locale.ROOT));
        if (forDialog && color == ChatFormatting.BLACK) {
            color = ChatFormatting.WHITE;
        }
        return Style.EMPTY
                .withBold(run.isBold())
                .withItalic(run.isItalic())
                .withUnderlined(run.isUnderlined())
                .withColor(color);
    }
}
