package com.gbti;

import com.gbti.utils.FormattedString;
import com.gbti.utils.StringUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the journal as a written book item from the loaded journal text. The book is assembled
 * from text components rather than a /give command string, so the text needs no escaping and is
 * read the same way on every Minecraft version.
 */
public final class JournalBook {
    // Limits of the written book component; a longer title or more pages cannot be saved
    private static final int TITLE_MAX_LENGTH = 32;
    private static final int MAX_PAGES = 100;

    private JournalBook() {
    }

    public static ItemStack create() {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (List<List<FormattedString>> page : StringUtils.getPages(FileManager.lines)) {
            if (pages.size() == MAX_PAGES) {
                TravelersJournal.logError("[TJ] The journal is longer than {} pages; the rest is left out", MAX_PAGES);
                break;
            }
            pages.add(Filterable.passThrough(toComponent(page)));
        }

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title()), FileManager.journalAuthor, 0, pages, true));
        return book;
    }

    public static boolean isJournal(ItemStack stack) {
        if (stack.getItem() != Items.WRITTEN_BOOK) {
            return false;
        }
        WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
        String bookTitle = content != null ? content.title().raw() : stack.getHoverName().getString();
        TravelersJournal.logDebug("Checking book title: '{}' against current title: '{}' and legacy titles: {}",
                bookTitle, title(), FileManager.legacyTitles);

        // First check current title
        if (bookTitle.equalsIgnoreCase(title())) {
            TravelersJournal.logDebug("Matched current title");
            return true;
        }
        // Then check legacy titles
        for (String legacyTitle : FileManager.legacyTitles) {
            if (bookTitle.equalsIgnoreCase(legacyTitle)) {
                TravelersJournal.logDebug("Matched legacy title: {}", legacyTitle);
                return true;
            }
        }
        TravelersJournal.logDebug("No title match found");
        return false;
    }

    private static String title() {
        String title = FileManager.journalTitle;
        return title.length() > TITLE_MAX_LENGTH ? title.substring(0, TITLE_MAX_LENGTH) : title;
    }

    private static Component toComponent(List<List<FormattedString>> lines) {
        MutableComponent page = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                page.append("\n");
            }
            for (FormattedString run : lines.get(i)) {
                page.append(Component.literal(run.getContent()).withStyle(style(run)));
            }
        }
        return page;
    }

    private static Style style(FormattedString run) {
        // getColor() is one of the sixteen colour names, which are the enum's names in lower case
        return Style.EMPTY
                .withBold(run.isBold())
                .withItalic(run.isItalic())
                .withUnderlined(run.isUnderlined())
                .withColor(ChatFormatting.valueOf(run.getColor().toUpperCase(Locale.ROOT)));
    }
}
