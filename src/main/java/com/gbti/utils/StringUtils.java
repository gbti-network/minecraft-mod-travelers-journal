package com.gbti.utils;

import java.util.ArrayList;
import java.util.List;

public class StringUtils {

    public static final int MAX_LINES_PER_PAGE = 14;

    /**
     * Splits the journal's lines into pages. A page is a list of lines and a line is a list of
     * formatted runs. A page breaks at a "[page]" line or after MAX_LINES_PER_PAGE lines.
     */
    public static List<List<List<FormattedString>>> getPages(List<String> lines) {
        List<List<List<FormattedString>>> pages = new ArrayList<>();
        List<List<FormattedString>> currentPage = new ArrayList<>();

        for (String line : lines) {
            // Check for page break marker
            if (line.trim().equals("[page]")) {
                if (!currentPage.isEmpty()) {
                    pages.add(currentPage);
                    currentPage = new ArrayList<>();
                }
                continue;
            }

            if (currentPage.size() >= MAX_LINES_PER_PAGE) {
                pages.add(currentPage);
                currentPage = new ArrayList<>();
            }
            currentPage.add(getFormattedLine(line));
        }

        // Add the last page, or one empty page for an empty journal
        if (!currentPage.isEmpty() || pages.isEmpty()) {
            pages.add(currentPage);
        }
        return pages;
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
                formatList.getLast().asterisks(asterisks).underlined(underlined).color(color).addChar(currentChar);
            }
            else if(currentChar == '\\') {
                metaCharacter = true;
            }
            else if (currentChar == '*') {
                if (line.length() > i + 1) {
                    if (line.charAt(i + 1) == '*') {
                        asterisks = asterisks >= 2 ? asterisks - 2 : asterisks + 2;
                        if(!formatList.getLast().getContent().isEmpty()) formatList.add(new FormattedString(""));
                        i++;
                    } else {
                        asterisks = (asterisks == 1 || asterisks == 3) ? asterisks - 1 : asterisks + 1;
                        if(!formatList.getLast().getContent().isEmpty()) formatList.add(new FormattedString(""));
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
                formatList.getLast().asterisks(asterisks).underlined(underlined).color(color).addChar('・');
            }
            else {
                formatList.getLast().asterisks(asterisks).underlined(underlined).color(color).addChar(currentChar);
            }
        }
        return formatList.stream().filter(elem -> !elem.getContent().isEmpty()).toList();
    }
}
