package com.gbti.utils;

import com.gbti.FileManager;

public class FormattedString {

    private String content;
    private int asterisks;
    private boolean underlined;
    private String color;
    private boolean lastWasQuote = false;

    public FormattedString(String content) {
        this.content = content;
        this.asterisks = 0;
    }

    public FormattedString asterisks(int asterisks) {
        this.asterisks = asterisks;
        return this;
    }

    public FormattedString underlined(boolean value) {
        this.underlined = value;
        return this;
    }

    public FormattedString color(String color) {
        this.color = color;
        return this;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        // Replace quotes with Unicode quotation marks
        if (content != null) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (c == '\'') {
                    result.append(lastWasQuote ? '\u2019' : '\u2018'); // U+2018 LEFT or U+2019 RIGHT SINGLE QUOTE
                    lastWasQuote = !lastWasQuote;
                } else if (c == '"') {
                    result.append(lastWasQuote ? '\u201D' : '\u201C'); // U+201C LEFT or U+201D RIGHT DOUBLE QUOTE
                    lastWasQuote = !lastWasQuote;
                } else {
                    result.append(c);
                }
            }
            this.content = result.toString();
        } else {
            this.content = content;
        }
    }

    public void addChar(char suffix) {
        this.setContent(this.content + suffix);
    }

    public int getAsterisks() {
        return asterisks;
    }

    public void setAsterisks(int asterisks) {
        this.asterisks = asterisks;
    }

    public String toBookString() {
        return "{\"text\":\"" + this.content + "\",\"underlined\":" + this.underlined + ",\"bold\":"
                + (asterisks >=2) + ",\"italic\":" + (asterisks % 2 == 1) + ",\"color\":\""
                + ((!color.isEmpty() && FileManager.COLORS.containsKey(this.color)) ? FileManager.COLORS.get(this.color) : "black") + "\"}";
    }
}
