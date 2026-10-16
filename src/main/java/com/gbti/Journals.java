package com.gbti;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every journal the server has. "default" is read from the files every earlier release used,
 * where they are, so an upgraded server keeps its journal untouched. Other journals live in
 * config/travelers-journal/journals/<id>/ as journal.txt, journal.md and joinlist.txt.
 */
public final class Journals {
    static final Path ROOT = Path.of("config", "travelers-journal");
    static final Path CONFIG = ROOT.resolve("config.txt");
    static final Path DEFAULT_TEXT = ROOT.resolve("travelers_journal.md");
    static final Path DEFAULT_JOIN_LIST = ROOT.resolve("joinlist.txt");
    static final Path FOLDERS = ROOT.resolve("journals");
    static final Path BACKUPS = ROOT.resolve("backups").resolve("journals");

    public static final Pattern ID = Pattern.compile("[a-z0-9-]{1,32}");
    // A written book's title holds 32 characters; the author is held to the same
    public static final int TITLE_MAX = 32;
    public static final int AUTHOR_MAX = 32;
    public static final int DEFAULT_OPERATOR_LEVEL = 2;

    // New installs get give_on_join and operator_level. give_at_spawn, which every earlier release
    // wrote and none ever used, is left out; on servers that have it, it is still ignored.
    private static final String DEFAULT_CONFIG = "title=Traveler's Journal\nauthor=Server\ngive_on_join=true\n"
            + "operator_level=" + DEFAULT_OPERATOR_LEVEL + "\ndebug_logging=false\n"
            + "legacy_titles=Traveller's Journal,Travelers Journal\n";
    private static final String DEFAULT_CONTENT = "# Welcome to the Server!\n\n"
            + "This is your Traveler's Journal. Use it to record your adventures and discoveries.";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss", Locale.ROOT);

    private static Map<String, Journal> journals = new LinkedHashMap<>();
    private static int operatorLevel = DEFAULT_OPERATOR_LEVEL;

    private Journals() {
    }

    /** Reads every journal from disk again, writing the default files first when they are missing. */
    public static void reload() {
        Map<String, Journal> loaded = new LinkedHashMap<>();
        Journal fallback = new Journal(Journal.DEFAULT_ID, CONFIG, DEFAULT_TEXT, DEFAULT_JOIN_LIST);
        fallback.giveOnJoin = true;
        try {
            loaded.put(Journal.DEFAULT_ID, loadDefault());
        } catch (Exception e) {
            TravelersJournal.logError("[TJ] Failed to read the journal files in {}: {}", ROOT, e.toString());
            loaded.put(Journal.DEFAULT_ID, fallback);
        }
        String defaultAuthor = loaded.get(Journal.DEFAULT_ID).author;
        if (Files.isDirectory(FOLDERS)) {
            try (Stream<Path> folders = Files.list(FOLDERS)) {
                for (Path folder : folders.filter(Files::isDirectory).sorted().toList()) {
                    String id = folder.getFileName().toString();
                    if (!ID.matcher(id).matches() || id.equals(Journal.DEFAULT_ID)) continue;
                    try {
                        Journal journal = loadFolder(folder, defaultAuthor);
                        if (journal != null) loaded.put(id, journal);
                    } catch (Exception e) {
                        TravelersJournal.logError("[TJ] Failed to read the journal in {}: {}", folder, e.toString());
                    }
                }
            } catch (IOException e) {
                TravelersJournal.logError("[TJ] Failed to list {}: {}", FOLDERS, e.toString());
            }
        }
        journals = loaded;
        TravelersJournal.logDebug("Loaded journals: {}", loaded.keySet());
    }

    private static Journal loadDefault() throws IOException {
        Files.createDirectories(ROOT);
        JournalFiles.writeIfMissing(DEFAULT_TEXT, DEFAULT_CONTENT);
        JournalFiles.writeIfMissing(CONFIG, DEFAULT_CONFIG);
        // Read both back even when they were only just written, so a first boot uses the defaults
        Journal journal = new Journal(Journal.DEFAULT_ID, CONFIG, DEFAULT_TEXT, DEFAULT_JOIN_LIST);
        journal.lines = JournalFiles.readLines(DEFAULT_TEXT);
        Map<String, String> settings = JournalFiles.readSettings(CONFIG);
        journal.title = settings.getOrDefault("title", journal.title);
        journal.author = settings.getOrDefault("author", journal.author);
        journal.giveOnJoin = !"false".equals(trimmed(settings.get("give_on_join")));
        for (String title : settings.getOrDefault("legacy_titles", "").split(",")) {
            if (!title.trim().isEmpty()) journal.legacyTitles.add(title.trim());
        }
        TravelersJournal.debugLoggingEnabled = "true".equals(trimmed(settings.get("debug_logging")));
        operatorLevel = level(settings.get("operator_level"));
        readJoinList(journal);
        return journal;
    }

    private static Journal loadFolder(Path folder, String defaultAuthor) throws IOException {
        Path settingsFile = folder.resolve("journal.txt");
        Path textFile = folder.resolve("journal.md");
        if (!Files.exists(settingsFile) && !Files.exists(textFile)) return null;
        Journal journal = new Journal(folder.getFileName().toString(), settingsFile, textFile, folder.resolve("joinlist.txt"));
        journal.title = titleFor(journal.id());
        journal.author = defaultAuthor;
        if (Files.exists(settingsFile)) {
            Map<String, String> settings = JournalFiles.readSettings(settingsFile);
            journal.title = settings.getOrDefault("title", journal.title);
            journal.author = settings.getOrDefault("author", journal.author);
            journal.giveOnJoin = "true".equals(trimmed(settings.get("give_on_join")));
        }
        if (Files.exists(textFile)) journal.lines = JournalFiles.readLines(textFile);
        readJoinList(journal);
        return journal;
    }

    private static void readJoinList(Journal journal) throws IOException {
        if (!Files.exists(journal.joinListFile())) return;
        for (String line : JournalFiles.readLines(journal.joinListFile())) {
            if (!line.trim().isEmpty()) journal.joinList.add(line.trim());
        }
    }

    public static List<Journal> all() {
        return List.copyOf(journals.values());
    }

    public static Journal get(String id) {
        return journals.get(id);
    }

    public static Journal defaultJournal() {
        Journal journal = journals.get(Journal.DEFAULT_ID);
        if (journal == null) {
            reload();
            journal = journals.get(Journal.DEFAULT_ID);
        }
        return journal;
    }

    /** The operator level the commands and the manager need, from operator_level in config.txt. */
    public static int operatorLevel() {
        return operatorLevel;
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches() && !id.equals(Journal.DEFAULT_ID);
    }

    /** "server-rules" becomes "Server rules": the title a journal starts with. */
    public static String titleFor(String id) {
        String words = id.replace('-', ' ').trim();
        String title = words.isEmpty() ? id : Character.toUpperCase(words.charAt(0)) + words.substring(1);
        return title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title;
    }

    public static String starterText(String title) {
        return "# " + title + "\n\nWrite this journal's text here.";
    }

    /** Creates journals/<id>/. The id must be valid and not taken: check with validId and get first. */
    public static Journal create(String id, String title, String author, boolean giveOnJoin, String text) throws IOException {
        if (!validId(id)) throw new IllegalArgumentException("invalid journal id: " + id);
        Path folder = FOLDERS.resolve(id);
        if (journals.containsKey(id) || Files.exists(folder)) throw new IllegalStateException("journal exists: " + id);
        Files.createDirectories(folder);
        JournalFiles.replace(folder.resolve("journal.txt"), settingsText(title, author, giveOnJoin));
        JournalFiles.replace(folder.resolve("journal.md"), withNewline(text));
        reload();
        return journals.get(id);
    }

    /**
     * Saves a journal's settings and, unless text is null, its text. Renaming "default" keeps its
     * old title among the legacy titles, so the untagged copies earlier releases handed out are
     * still recognised.
     */
    public static Journal save(Journal journal, String title, String author, boolean giveOnJoin, String text) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("title", title);
        values.put("author", author);
        values.put("give_on_join", Boolean.toString(giveOnJoin));
        if (journal.isDefault() && !title.equalsIgnoreCase(journal.title)
                && journal.legacyTitles.stream().noneMatch(journal.title::equalsIgnoreCase)) {
            List<String> legacy = new ArrayList<>(journal.legacyTitles);
            legacy.add(journal.title);
            values.put("legacy_titles", String.join(",", legacy));
        }
        JournalFiles.updateSettings(journal.settingsFile(), values);
        if (text != null) JournalFiles.replace(journal.textFile(), withNewline(text));
        reload();
        return journals.get(journal.id());
    }

    /** Moves journals/<id>/ to backups/journals/<id>/<time>/ and returns where it went. */
    public static Path delete(Journal journal) throws IOException {
        if (journal.isDefault()) throw new IllegalArgumentException("the default journal cannot be deleted");
        Path backup = BACKUPS.resolve(journal.id()).resolve(LocalDateTime.now().format(STAMP));
        Files.createDirectories(backup.getParent());
        Files.move(journal.folder(), backup);
        reload();
        return backup;
    }

    public static void markGiven(Journal journal, String uuid) {
        try {
            JournalFiles.appendLine(journal.joinListFile(), uuid);
            journal.joinList.add(uuid);
        } catch (IOException e) {
            TravelersJournal.logError("[TJ] Failed to write to join list: {}", e.getMessage());
        }
    }

    private static String settingsText(String title, String author, boolean giveOnJoin) {
        return "title=" + title + "\nauthor=" + author + "\ngive_on_join=" + giveOnJoin + "\n";
    }

    private static String withNewline(String text) {
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n');
        return normalised.isEmpty() || normalised.endsWith("\n") ? normalised : normalised + "\n";
    }

    private static int level(String value) {
        try {
            return Math.max(0, Math.min(4, Integer.parseInt(trimmed(value))));
        } catch (NumberFormatException e) {
            return DEFAULT_OPERATOR_LEVEL;
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}
