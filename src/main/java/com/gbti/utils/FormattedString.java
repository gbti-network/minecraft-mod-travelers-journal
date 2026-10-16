package com.gbti.utils;

import com.gbti.FileManager;

public class FormattedString {

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
        return color != null && FileManager.COLORS.containsKey(color) ? FileManager.COLORS.get(color) : "black";
    }
}
