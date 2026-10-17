# 📖 Traveler's Journal

A Minecraft mod that provides players with a special journal that updates its contents dynamically. Built with Fabric for every Minecraft version from 1.21 to 26.3.

## ✨ Features

- Dynamic journal content that updates when used
- Automatic distribution to new players
- Several named journals, each given on first join or only by command
- An in-game journal manager on Minecraft 1.21.6 and later
- Server-side implementation for multiplayer compatibility
- Customizable content through configuration
- Real-time preview system for journal formatting

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Download the latest version of Traveler's Journal from the releases page, choosing the jar for your server's Minecraft version (see below)
3. Place the .jar file in your server's `mods` folder
4. Install the required dependencies:
   - Fabric API

### Supported Minecraft versions

Each release has one jar per range of Minecraft versions. The range is in the file name, and Fabric refuses a jar on any version outside it.

| Minecraft | Jar |
|---|---|
| 1.21 to 1.21.1 | `travelers-journal-<version>+mc1.21-1.21.1.jar` |
| 1.21.2 to 1.21.4 | `travelers-journal-<version>+mc1.21.2-1.21.4.jar` |
| 1.21.5 | `travelers-journal-<version>+mc1.21.5.jar` |
| 1.21.6 to 1.21.10 | `travelers-journal-<version>+mc1.21.6-1.21.10.jar` |
| 1.21.11 | `travelers-journal-<version>+mc1.21.11.jar` |
| 26.1 to 26.2 | `travelers-journal-<version>+mc26.1-26.2.jar` |
| 26.3 | `travelers-journal-<version>+mc26.3.jar` |

The 1.21.x jars need Java 21 or newer, and the 26.x jars need Java 25, as Minecraft itself does. The in-game manager uses Minecraft's server dialogs, which start at 1.21.6; on 1.21 to 1.21.5 the journals are edited as files.

### Upgrading

Replace the old jar in your `mods` folder with the new one and restart. The files in `config/travelers-journal/` carry over as they are:
- your settings, text and join list;
- the journals your players already carry, including ones under a legacy title, which refresh the next time they are used;
- players already on the join list, who do not get a second copy.

Your journal becomes the journal `default`, still read from those same files. Nothing is moved or rewritten, so going back to an earlier release finds everything where it left it. Every release is tested upgrading from each earlier release.

## ⌨️ Commands

For operators and the server console. `/journals` is a shorter name for `/travelers_journal`, and the two work the same way. Operators need level 2 unless `operator_level` in `config.txt` says otherwise.

| Command | What it does |
|---|---|
| `/journals` | In game on 1.21.6 and later, opens the journal manager. Elsewhere, lists these commands. |
| `/journals list` | Shows every journal, and whether it goes to new players. |
| `/journals give <players> [journal]` | Gives each player a journal, `default` unless one is named. A player with a full inventory finds it at their feet. |
| `/journals create <id>` | Adds a journal in `journals/<id>/`. Ids use lower-case letters, digits and `-`. |
| `/journals delete <journal>` | Moves a journal's folder to `backups/journals/<id>/`. Copies players already carry stay as signed books. `default` cannot be deleted. |
| `/journals edit [journal]` | In game on 1.21.6 and later, opens the journal in the editor. Elsewhere, names its file. |
| `/journals reload` | Reads every journal's files again. Each copy shows the new text the next time it is used. |

## 📚 Managing journals in game

On Minecraft 1.21.6 and later, an operator who types `/journals` gets the manager, built from Minecraft's own dialog screens, so players need no client mod.

- **The hub** lists every journal, marked `first join` or `by command`, with Create journal and Reload from files.
- **The editor** holds the book title (up to 32 characters), the author, a "Give on first join" switch and the text in one box, in the same markup as the files. Save writes the files, keeping the previous text as a `.bak` file beside it. Preview shows the pages as they will read, without saving.
- **Give to me** puts a copy in your inventory, and **Delete journal** asks first, then moves the journal's folder to a backup.

The server checks every button press again, so only operators at the configured level can change anything. The editor's box holds 15,000 characters; a longer journal opens without the box and is edited as a file.

## 🔧 Configuration

The mod can be configured through files in the `config/travelers-journal/` directory:

### Basic Settings (`config.txt`)
```properties
title=Traveler's Journal
author=Server
give_on_join=true
operator_level=2
debug_logging=false
legacy_titles=Traveller's Journal,Travelers Journal
```

- `title`, `author` and `give_on_join` are the `default` journal's.
- `operator_level` (0 to 4) is who may use the commands and the manager; `/journals reload` applies a change.
- `legacy_titles` are older titles that still count as the `default` journal.
- A config from an earlier release may also hold `give_at_spawn`, which no release has ever used; it is ignored.

### More journals
Each other journal has a folder, `config/travelers-journal/journals/<id>/`:

- `journal.txt` holds `title=`, `author=` and `give_on_join=` (`false` unless set);
- `journal.md` holds the text, in the markup below;
- `joinlist.txt` lists the players who were given it on first join.

Create one with `/journals create <id>`, in the manager, or by writing the folder and running `/journals reload`.

### Journal Content
Create or edit `config/travelers-journal/travelers_journal.md` (or a journal's `journal.md`) to set its content. The mod supports:

- Markdown-style formatting:
  - Bold: `**text**`
  - Italic: `*text*`
  - Underline: `_text_`
- Color codes: `<color>text<>` where color is one of:
  - `black`, `dark_blue`, `dark_green`, `dark_aqua`
  - `dark_red`, `dark_purple`, `gold`, `gray`
  - `dark_gray`, `blue`, `green`, `aqua`
  - `red`, `light_purple`, `yellow`, `white`
- Page breaks: Use `[page]` to start a new page. A page also ends when it fills the 14 lines the book shows, counting lines as the book wraps them, and a line too long for what is left carries on at the top of the next page.

### 📝 Preview Your Journal

After installing the mod, you can preview how your journal will look in-game:

1. Navigate to `config/travelers-journal/preview/`
2. Open `index.html` in your web browser
3. Edit your journal content in the left panel
4. See a real-time preview on the right

The preview supports:
- Page breaks with `[page]`
- Markdown-style formatting:
  - Bold: `**text**`
  - Italic: `*text*`
  - Underline: `_text_`
- Color codes: `<c>text<>` where c is 0-9 or a-f
- Bullet points: Start a line with `- `

This makes it easy to format and test your journal content before players see it in-game!

## 🤝 Support

### Join the GBTI Network! 🎮 🚀 💻
The GBTI Network is more than just a community - it's a thriving ecosystem of developers, gamers, and creators! Our members enjoy:

- 💻 Exclusive development tools and resources
- 📚 Private repositories and code libraries
- 👥 Active Discord community for collaboration
- 🌟 Member directory listing to showcase your work
- 🤝 Co-op opportunities and project partnerships
- 🎯 Early access to new features and tools

Support our work by becoming a [GBTI Network member](https://gbti.network/membership/).

### Other ways to Support:

- 🌟 Star us on [GitHub](https://github.com/gbti-network/minecraft-mod-travelers-journal)
- 🐛 Report issues on our [Issue Tracker](https://github.com/gbti-network/minecraft-mod-travelers-journal/issues)
- 💡 Contribute through [Pull Requests](https://github.com/gbti-network/minecraft-mod-travelers-journal/pulls)
- ⭐ Rate us on [Modrinth](#)

## 📄 License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.

---

**Note**: For development and contribution guidelines, please see [CONTRIBUTING.md](CONTRIBUTING.md)
