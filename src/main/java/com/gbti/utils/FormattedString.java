package com.gbti.utils;

import java.util.Set;

public class FormattedString {
    private static final Set<String> COLORS = Set.of("black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
            "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");

    private String content;
    private int asterisks;
    private boolean underlined;
    private String color = "";

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

    public void addChar(char suffix) {
        this.content = this.content + suffix;
    }

    // An empty run with this one's formatting, for splitting a line where a page breaks
    public FormattedString emptyCopy() {
        return new FormattedString("").asterisks(asterisks).underlined(underlined).color(color);
    }

    public void addCodePoint(int codePoint) {
        this.content = this.content + Character.toString(codePoint);
    }

    public int getAsterisks() {
        return asterisks;
    }

    public void setAsterisks(int asterisks) {
        this.asterisks = asterisks;
    }

    public boolean isBold() {
        return asterisks >= 2;
    }

    public boolean isItalic() {
        return asterisks % 2 == 1;
    }

    public boolean isUnderlined() {
        return underlined;
    }

    // One of the sixteen Minecraft colour names; anything else falls back to black
    public String getColor() {
        return color != null && COLORS.contains(color) ? color : "black";
    }
}
