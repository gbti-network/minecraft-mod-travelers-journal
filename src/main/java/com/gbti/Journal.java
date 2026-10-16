package com.gbti;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One journal: its book settings, its text and the players it has been given to. The journal
 * "default" is the one every release has had, kept in config.txt, travelers_journal.md and
 * joinlist.txt; every other journal has its own folder under journals/.
 */
public final class Journal {
    public static final String DEFAULT_ID = "default";

    private final String id;
    private final Path settingsFile;
    private final Path textFile;
    private final Path joinListFile;
    String title = "Traveler's Journal";
    String author = "Server";
    boolean giveOnJoin;
    final List<String> legacyTitles = new ArrayList<>();
    List<String> lines = new ArrayList<>();
    final Set<String> joinList = new LinkedHashSet<>();

    Journal(String id, Path settingsFile, Path textFile, Path joinListFile) {
        this.id = id;
        this.settingsFile = settingsFile;
        this.textFile = textFile;
        this.joinListFile = joinListFile;
    }

    public String id() {
        return id;
    }

    public boolean isDefault() {
        return DEFAULT_ID.equals(id);
    }

    public String title() {
        return title;
    }

    public String author() {
        return author;
    }

    public boolean giveOnJoin() {
        return giveOnJoin;
    }

    public List<String> lines() {
        return Collections.unmodifiableList(lines);
    }

    public List<String> legacyTitles() {
        return Collections.unmodifiableList(legacyTitles);
    }

    public Set<String> joinList() {
        return Collections.unmodifiableSet(joinList);
    }

    public String text() {
        return String.join("\n", lines);
    }

    // The text file inside config/travelers-journal, e.g. journals/rules/journal.md
    public String textName() {
        return Journals.ROOT.relativize(textFile).toString().replace('\\', '/');
    }

    // The text file from the server folder, e.g. config/travelers-journal/journals/rules/journal.md
    public String textPath() {
        return textFile.toString().replace('\\', '/');
    }

    Path settingsFile() {
        return settingsFile;
    }

    Path textFile() {
        return textFile;
    }

    Path joinListFile() {
        return joinListFile;
    }

    Path folder() {
        return textFile.getParent();
    }
}
