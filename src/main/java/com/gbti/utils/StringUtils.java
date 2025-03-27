package com.gbti.utils;

import com.gbti.FileManager;

import java.util.ArrayList;
import java.util.List;

public class StringUtils {

    public static String getBookString() {
        String bookString = "written_book[written_book_content={pages:['[[";
        final int MAX_LINES_PER_PAGE = 14;
        int currentLineCount = 0;
        StringBuilder currentPage = new StringBuilder();

        for (String line : FileManager.lines) {
            // Check for page break marker
            if (line.trim().equals("[page]")) {
                // Start a new page
                if (currentPage.length() > 0) {
                    bookString += currentPage.toString();
                    bookString = bookString.substring(0, bookString.length() - 1);
                    bookString += "]]','[[";
                    currentPage = new StringBuilder();
                    currentLineCount = 0;
                }
                continue;
            }

            List<FormattedString> formattedLine = getFormattedLine(line);
            
            // Build the line content
            StringBuilder lineContent = new StringBuilder();
            for (FormattedString formattedString : formattedLine) {
                lineContent.append(formattedString.toBookString()).append(",");
            }
            lineContent.append("\"\\\\n\",");

            // Check if we need a new page based on line count
            if (currentLineCount >= MAX_LINES_PER_PAGE) {
                // Add current page to book and start new page
                bookString += currentPage.toString();
                bookString = bookString.substring(0, bookString.length() - 1);
                bookString += "]]','[[";
                currentPage = new StringBuilder();
                currentLineCount = 0;
            }

            // Add line to current page
            currentPage.append(lineContent);
            currentLineCount++;
        }

        // Add the last page
        if (currentPage.length() > 0) {
            bookString += currentPage.toString();
            bookString = bookString.substring(0, bookString.length() - 7);
        }

        bookString += "]]'],title:\"" + FileManager.journalTitle + "\",author:\"" + FileManager.journalAuthor + "\"}]";
        return bookString;
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
