package com.gbti.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How the book screen lays out a page: the vanilla font's character widths and its word wrap.
 * BookViewScreen wraps a page at 114 px (font.split(page, 114)) and shows 128 / 9 = 14 lines.
 * Pagination and the preview both use this, so a page breaks where the book wraps it.
 *
 * <p>The widths are the vanilla font's. A client with Force Unicode Font or a resource pack's own
 * font measures text a little differently.</p>
 */
public final class BookFont {
    public static final int PAGE_WIDTH = 114;
    public static final int PAGE_LINES = 14;

    private BookFont() {
    }

    // A glyph's width plus one pixel of spacing, and one more for bold
    public static int advance(int codePoint, boolean bold) {
        return glyphWidth(codePoint) + 1 + (bold ? 1 : 0);
    }

    /**
     * Where each wrapped line starts and ends (end exclusive), as the book wraps it: at the last
     * space that fits, which is dropped, or mid-word when no space fits. A line always gives at
     * least one wrapped line, an empty one included.
     */
    public static List<int[]> wrap(int[] advances, boolean[] spaces) {
        List<int[]> out = new ArrayList<>();
        int start = 0;
        int width = 0;
        int lastSpace = -1;
        for (int i = 0; i < advances.length; i++) {
            if (spaces[i]) lastSpace = i;
            width += advances[i];
            if (width > PAGE_WIDTH) {
                int end = lastSpace > start ? lastSpace : Math.max(i, start + 1);
                out.add(new int[]{start, end});
                start = end < advances.length && spaces[end] ? end + 1 : end;
                lastSpace = -1;
                width = 0;
                for (int j = start; j <= i; j++) width += advances[j];
            }
        }
        out.add(new int[]{Math.min(start, advances.length), advances.length});
        return out;
    }

    /** How many lines a page's text fills in the book once wrapped. */
    public static int bookLines(Component page) {
        List<List<int[]>> lines = new ArrayList<>();
        List<Integer> advances = new ArrayList<>();
        List<Boolean> spaces = new ArrayList<>();
        int[] count = {0};
        page.visit((style, text) -> {
            text.codePoints().forEach(codePoint -> {
                if (codePoint == '\n') {
                    count[0] += wrap(toInts(advances), toBooleans(spaces)).size();
                    advances.clear();
                    spaces.clear();
                } else {
                    advances.add(advance(codePoint, style.isBold()));
                    spaces.add(codePoint == ' ');
                }
            });
            return Optional.empty();
        }, Style.EMPTY);
        return count[0] + wrap(toInts(advances), toBooleans(spaces)).size();
    }

    private static int[] toInts(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static boolean[] toBooleans(List<Boolean> values) {
        boolean[] out = new boolean[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
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
