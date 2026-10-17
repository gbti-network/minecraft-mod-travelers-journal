package com.gbti;

import com.gbti.utils.FormattedString;
import com.gbti.utils.StringUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//? if >=1.20.5 {
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
//?} else {
/*import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
*///?}

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a journal as a written book item from its text. The book is assembled from text
 * components rather than a /give command string, so the text needs no escaping and is read the
 * same way on every Minecraft version. Each book carries its journal's id as custom data.
 *
 * Before 1.20.5 a book is NBT rather than item components; book, contents, taggedId and titleOf
 * are the only places that differ, so the rest of the mod reads books the same way everywhere.
 */
public final class JournalBook {
    // The custom data key holding the journal id: {travelers_journal:"<id>"}
    public static final String ID_KEY = "travelers_journal";
    // A written book holds at most 100 pages; the rest of a longer journal is left out
    private static final int MAX_PAGES = 100;

    private JournalBook() {
    }

    /** What a written book holds: its title, author and pages. */
    public record Contents(String title, String author, List<Component> pages) {
    }

    public static ItemStack create(Journal journal) {
        return create(journal.id(), journal.title(), journal.author(), journal.lines());
    }

    // A copy from values not saved yet, as the manager's preview shows it
    public static ItemStack create(String id, String title, String author, List<String> lines) {
        ItemStack book = book(bookTitle(title), author, pages(lines, false));
        //? if >=1.20.5 {
        CompoundTag tag = new CompoundTag();
        tag.putString(ID_KEY, id);
        book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        //?} else {
        /*// The id sits in the item's own tag, beside the pages. Minecraft's upgrade to 1.20.5 moves
        // keys it does not know into custom data, which is where later versions look for it.
        book.getOrCreateTag().putString(ID_KEY, id);
        *///?}
        return book;
    }

    /** A signed written book with these pages and no journal id, as any player could write one. */
    public static ItemStack book(String title, String author, List<Component> pages) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        //? if >=1.20.5 {
        List<Filterable<Component>> filterable = new ArrayList<>();
        for (Component page : pages) {
            filterable.add(Filterable.passThrough(page));
        }
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), author, 0, filterable, true));
        //?} else {
        /*// Each page is a JSON text component, stored as a string; resolved means the server has
        // nothing left to fill in when the book is opened
        ListTag list = new ListTag();
        for (Component page : pages) {
            list.add(StringTag.valueOf(Component.Serializer.toJson(page)));
        }
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", title);
        tag.putString("author", author);
        tag.putInt("generation", 0);
        tag.putBoolean("resolved", true);
        tag.put("pages", list);
        *///?}
        return book;
    }

    /** The title, author and pages of a written book, or null for any other item. */
    public static Contents contents(ItemStack stack) {
        if (stack.getItem() != Items.WRITTEN_BOOK) {
            return null;
        }
        List<Component> pages = new ArrayList<>();
        //? if >=1.20.5 {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content == null) {
            return null;
        }
        for (Filterable<Component> page : content.pages()) {
            pages.add(page.raw());
        }
        return new Contents(content.title().raw(), content.author(), pages);
        //?} else {
        /*CompoundTag tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        ListTag list = tag.getList("pages", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            pages.add(page(list.getString(i)));
        }
        return new Contents(tag.getString("title"), tag.getString("author"), pages);
        *///?}
    }

    //? if <1.20.5 {
    /*// A page a player wrote by hand may not be valid JSON, so it reads as plain text then
    private static Component page(String json) {
        try {
            Component page = Component.Serializer.fromJson(json);
            return page == null ? Component.empty() : page;
        } catch (RuntimeException e) {
            return Component.literal(json);
        }
    }
    *///?}

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
        String bookTitle = titleOf(stack);
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
        //? if >=1.20.5 {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        if (tag == null) {
            return null;
        }
        *///?}
        //? if >=1.21.5 {
        String id = tag.getStringOr(ID_KEY, "");
        //?} else {
        /*String id = tag.getString(ID_KEY);
        *///?}
        return id.isEmpty() ? null : id;
    }

    // The title the book stores, or its displayed name when it stores none. The pages are not
    // read, so matching a book to a journal costs no parsing.
    private static String titleOf(ItemStack stack) {
        //? if >=1.20.5 {
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        if (content != null) {
            return content.title().raw();
        }
        //?} else {
        /*CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains("title", Tag.TAG_STRING)) {
            return tag.getString("title");
        }
        *///?}
        return stack.getHoverName().getString();
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
