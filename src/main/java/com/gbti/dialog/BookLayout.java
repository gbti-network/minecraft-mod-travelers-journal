package com.gbti.dialog;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lays a journal page out the way the book screen does, for the preview dialog. The client centres
 * every line of dialog text (DialogBodyHandlers builds plain messages centred), and a server cannot
 * change that. So each line is wrapped where the book wraps it, at 114 px, and padded with spaces
 * to the page's full width: a centred line that fills the page sits flush left.
 *
 * <p>Widths are the vanilla font's. With Force Unicode Font or a resource pack's own font, lines
 * can sit a few pixels uneven, but nothing is lost.</p>
 */
public final class BookLayout {
    // BookViewScreen: font.split(page, 114), and 128 / 9 lines shown
    public static final int PAGE_WIDTH = 114;
    public static final int PAGE_LINES = 14;
    // FocusableTextWidget keeps 4 px of padding on each side of a plain message
    static final int MESSAGE_WIDTH = PAGE_WIDTH + 8;
    // One pixel of slack, so a width the table gets wrong by one never wraps the padding
    private static final int FILL = PAGE_WIDTH - 1;

    private BookLayout() {
    }

    /** A page as the book shows it: up to 14 lines, and how many lines past those it cuts off. */
    record Page(Component text, int cutOff) {
    }

    private record Glyph(int codePoint, Style style) {
    }

    static Page layout(Component page, Component header) {
        List<List<Glyph>> lines = new ArrayList<>();
        for (List<Glyph> line : sourceLines(page)) {
            lines.addAll(wrap(line));
        }
        MutableComponent text = Component.empty();
        // The book puts its page number at the top right
        text.append(spaces(FILL - width(header))).append(header);
        int shown = Math.min(PAGE_LINES, lines.size());
        for (int i = 0; i < shown; i++) {
            List<Glyph> line = lines.get(i);
            text.append("\n").append(component(line));
            if (!line.isEmpty()) text.append(spaces(FILL - width(line)));
        }
        return new Page(text, lines.size() - shown);
    }

    /** A line's width in the vanilla font, in GUI pixels. */
    public static int width(Component text) {
        int width = 0;
        for (int line : lineWidths(text)) {
            width = Math.max(width, line);
        }
        return width;
    }

    /** The width of each line of a text, in GUI pixels, as the vanilla font draws it. */
    public static List<Integer> lineWidths(Component text) {
        List<Integer> widths = new ArrayList<>();
        for (List<Glyph> line : sourceLines(text)) {
            widths.add(width(line));
        }
        return widths;
    }

    private static List<List<Glyph>> sourceLines(Component page) {
        List<List<Glyph>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        page.visit((style, text) -> {
            text.codePoints().forEach(codePoint -> {
                if (codePoint == '\n') lines.add(new ArrayList<>());
                else lines.get(lines.size() - 1).add(new Glyph(codePoint, style));
            });
            return Optional.empty();
        }, Style.EMPTY);
        return lines;
    }

    // The book's word wrap: break at the last space that fits and drop it, or mid-word when none does
    private static List<List<Glyph>> wrap(List<Glyph> line) {
        List<List<Glyph>> out = new ArrayList<>();
        int start = 0;
        int width = 0;
        int lastSpace = -1;
        for (int i = 0; i < line.size(); i++) {
            Glyph glyph = line.get(i);
            if (glyph.codePoint() == ' ') lastSpace = i;
            width += advance(glyph);
            if (width > PAGE_WIDTH) {
                int end = lastSpace > start ? lastSpace : Math.max(i, start + 1);
                out.add(line.subList(start, end));
                start = end < line.size() && line.get(end).codePoint() == ' ' ? end + 1 : end;
                lastSpace = -1;
                width = width(line.subList(start, i + 1));
            }
        }
        out.add(line.subList(Math.min(start, line.size()), line.size()));
        return out;
    }

    private static Component component(List<Glyph> line) {
        MutableComponent text = Component.empty();
        StringBuilder run = new StringBuilder();
        Style style = null;
        for (Glyph glyph : line) {
            if (style != null && !Objects.equals(style, glyph.style())) {
                text.append(Component.literal(run.toString()).setStyle(style));
                run.setLength(0);
            }
            style = glyph.style();
            run.appendCodePoint(glyph.codePoint());
        }
        if (style != null) text.append(Component.literal(run.toString()).setStyle(style));
        return text;
    }

    // Plain spaces are 4 px and bold ones 5, which together make any width from 12 up
    private static Component spaces(int pixels) {
        int bold = 0;
        while (pixels > 0 && pixels % 4 != 0 && bold < 4 && pixels >= 5) {
            pixels -= 5;
            bold++;
        }
        int plain = Math.max(0, pixels / 4);
        MutableComponent padding = Component.literal(" ".repeat(plain));
        if (bold > 0) padding.append(Component.literal(" ".repeat(bold)).withStyle(Style.EMPTY.withBold(true)));
        return padding;
    }

    private static int width(List<Glyph> line) {
        int width = 0;
        for (Glyph glyph : line) width += advance(glyph);
        return width;
    }

    // The glyph's width plus one pixel of spacing, and one more for bold
    private static int advance(Glyph glyph) {
        return glyphWidth(glyph.codePoint()) + 1 + (glyph.style().isBold() ? 1 : 0);
    }

    private static int glyphWidth(int c) {
        if (c >= 'A' && c <= 'Z') return c == 'I' ? 3 : 5;
        if (c >= 'a' && c <= 'z') {
            return switch (c) {
                case 'i', 'l' -> 1;
                case 'f', 'k', 't' -> 4;
                default -> 5;
            };
        }
        if (c >= '0' && c <= '9') return 5;
        return switch (c) {
            case ' ' -> 3;
            case '!', '.', ',', ':', ';', '\'', '|' -> 1;
            case '`' -> 2;
            case '"', '[', ']' -> 3;
            case '(', ')', '{', '}', '<', '>' -> 4;
            case '@', '~' -> 6;
            // the mod's list bullet, measured on a 26.2 client
            case '・' -> 8;
            default -> c >= 0x2E80 ? 8 : 5;
        };
    }
}
