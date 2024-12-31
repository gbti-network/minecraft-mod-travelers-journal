# Traveler's Journal

A Minecraft mod that provides a server-wide journal system. The mod automatically updates journal books with server-defined content, making it perfect for sharing server lore, rules, or information with players.

## Features
- Server-wide synchronized journal content
- Automatic journal updates when players use their books
- Customizable journal title and author
- Option to give journal to new players
- Support for legacy book titles for compatibility
- Debug logging for troubleshooting

## Installation
1. Install Fabric for Minecraft 1.21.1
2. Place the mod jar in your mods folder
3. Create the config directory: `config/travelers-journal/`
4. Start the server/game

## Configuration
The mod can be configured by editing `config/travelers-journal/config.txt`:

```properties
title=Traveler's Journal
author=Server
give_at_spawn=true
debug_logging=false
legacy_titles=Traveller's Journal,Travelers Journal
```

### Configuration Options
- `title`: The title that appears on new journal books
- `author`: The author name that appears on journal books
- `give_at_spawn`: If true, gives new players a journal when they first join
- `debug_logging`: Enable detailed logging for troubleshooting
- `legacy_titles`: Comma-separated list of alternate titles that should be recognized as valid journals (useful for compatibility with existing books)

### Journal Content
The journal's content is stored in `config/travelers-journal/travelers_journal.md` and supports standard Minecraft text formatting codes:
- `§0` through `§9`, `§a` through `§f`: Color codes
- `§k`: Obfuscated
- `§l`: Bold
- `§m`: Strikethrough
- `§n`: Underline
- `§o`: Italic
- `§r`: Reset

### Preview System
The mod includes a web-based preview system to help you visualize how your journal content will appear in-game. To use the preview:

1. Open `src/main/resources/preview/index.html` in your web browser
2. Enter your journal content in the left panel
3. See a real-time preview of how it will look in-game on the right

The preview supports:
- Page breaks using `[page]` markers
- Markdown-style formatting:
  - Bold: `**text**`
  - Italic: `*text*`
  - Underline: `_text_`
- Color codes: `<c>text<>` where c is 0-9 or a-f
- Bullet points using `- ` at the start of a line

This makes it easy to format and test your journal content before adding it to the server.

## File Structure
- **`config/travelers-journal/`**: Configuration directory
  - **`config.txt`**: Mod configuration file
  - **`travelers_journal.md`**: Journal content file
  - **`joinlist.txt`**: List of players who have received journals

## Building from Source
1. Clone the repository
2. Run `npm run build`
3. Find the built jar in `.server/mods`

## License
MIT License - Feel free to use this mod in modpacks and modify it as needed.
