package com.gbti.utils;

import java.util.ArrayList;
import java.util.List;

public class StringUtils {

    // The lines a book page shows once its text is wrapped
    public static final int MAX_LINES_PER_PAGE = BookFont.PAGE_LINES;

    private record Glyph(int codePoint, FormattedString run) {
    }

    /**
     * Splits the journal's lines into pages. A page is a list of lines and a line is a list of
     * formatted runs. A page breaks at a "[page]" line, and when it fills the 14 lines the book
     * shows once it wraps each line at its width. A line longer than what is left on a page is cut
     * where the book would wrap it and carries on at the top of the next page, so no text is hidden.
     */
    public static List<List<List<FormattedString>>> getPages(List<String> lines) {
        List<List<List<FormattedString>>> pages = new ArrayList<>();
        List<List<FormattedString>> currentPage = new ArrayList<>();
        int used = 0;

        for (String line : lines) {
            // Check for page break marker
            if (line.trim().equals("[page]")) {
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage);
                    currentPage = new ArrayList<>();
                    used = 0;
                }
                continue;
            }

            List<Glyph> glyphs = glyphs(getFormattedLine(line));
            while (true) {
                List<int[]> wrapped = wrap(glyphs);
                int room = MAX_LINES_PER_PAGE - used;
                if (wrapped.size() <= room) {
                    currentPage.add(runs(glyphs));
                    used += wrapped.size();
                    break;
                }
                if (room > 0) {
                    currentPage.add(runs(glyphs.subList(0, wrapped.get(room - 1)[1])));
                    glyphs = glyphs.subList(wrapped.get(room)[0], glyphs.size());
                }
                pages.add(currentPage);
                currentPage = new ArrayList<>();
                used = 0;
            }
        }

        // Add the last page, or one empty page for an empty journal
        if (!currentPage.isEmpty() || pages.isEmpty()) {
            pages.add(currentPage);
        }
        return pages;
    }

    private static List<Glyph> glyphs(List<FormattedString> runs) {
        List<Glyph> glyphs = new ArrayList<>();
        for (FormattedString run : runs) {
            run.getContent().codePoints().forEach(codePoint -> glyphs.add(new Glyph(codePoint, run)));
        }
        return glyphs;
    }

    private static List<int[]> wrap(List<Glyph> glyphs) {
        int[] advances = new int[glyphs.size()];
        boolean[] spaces = new boolean[glyphs.size()];
        for (int i = 0; i < glyphs.size(); i++) {
            Glyph glyph = glyphs.get(i);
            advances[i] = BookFont.advance(glyph.codePoint(), glyph.run().isBold());
            spaces[i] = glyph.codePoint() == ' ';
        }
        return BookFont.wrap(advances, spaces);
    }

    // Back to runs, one per stretch of a single source run, each keeping its formatting
    private static List<FormattedString> runs(List<Glyph> glyphs) {
        List<FormattedString> runs = new ArrayList<>();
        FormattedString source = null;
        FormattedString current = null;
        for (Glyph glyph : glyphs) {
            if (glyph.run() != source) {
                source = glyph.run();
                current = source.emptyCopy();
                runs.add(current);
            }
            current.addCodePoint(glyph.codePoint());
        }
        return runs;
    }

    public static List<FormattedString> getFormattedLine(String line) {
        int asterisks = 0;
        boolean underlined = false;
        List<FormattedString> formatList = new ArrayList<>();
        formatList.add(new FormattedString(""));
        boolean metaCharacter = false;
        boolean isColoring = false;
        String color = "";

        for (int i = 0; i < line.length(); i++) {
            char currentChar = line.charAt(i);

            if(currentChar == '>') {
                isColoring = false;
                formatList.add(new FormattedString(""));
            } else if(isColoring) color += currentChar;
            else if(metaCharacter) {
                metaCharacter = false;
                formatList.get(formatList.size() - 1).asterisks(asterisks).underlined(underlined).color(color).addChar(currentChar);
            }
            else if(currentChar == '\\') {
                metaCharacter = true;
            }
            else if (currentChar == '*') {
                if (line.length() > i + 1) {
                    if (line.charAt(i + 1) == '*') {
                        asterisks = asterisks >= 2 ? asterisks - 2 : asterisks + 2;
                        if(!formatList.get(formatList.size() - 1).getContent().isEmpty()) formatList.add(new FormattedString(""));
                        i++;
                    } else {
                        asterisks = (asterisks == 1 || asterisks == 3) ? asterisks - 1 : asterisks + 1;
                        if(!formatList.get(formatList.size() - 1).getContent().isEmpty()) formatList.add(new FormattedString(""));
                    }
                }
            } else if(currentChar == '_') {
                if(underlined) {
                    underlined = false;
                    formatList.add(new FormattedString(""));
                } else {
                    underlined = true;
                    formatList.add(new FormattedString(""));
                }
            } else if(currentChar == '<') {
                if (line.length() > i + 1 && line.charAt(i+1) == '>') {
                    formatList.add(new FormattedString(""));
                    color="";
                    isColoring = false;
                    i++;
                } else {
                    isColoring = true;
                }
            } else if(currentChar == '-' && i == 0 && line.length() > 1 && line.charAt(1) == ' ') {
                formatList.get(formatList.size() - 1).asterisks(asterisks).underlined(underlined).color(color).addChar('・');
            }
            else {
                formatList.get(formatList.size() - 1).asterisks(asterisks).underlined(underlined).color(color).addChar(currentChar);
            }
        }
        return formatList.stream().filter(elem -> !elem.getContent().isEmpty()).toList();
    }
}
