# 📖 Traveler's Journal

A Minecraft mod that provides players with a special journal that updates its contents dynamically. Built with Fabric for Minecraft 1.20 to 1.20.4 and every version from 1.21 to 26.3.

## ✨ Features

- Dynamic journal content that updates when used
- Automatic distribution to new players
- Several named journals, each given on first join or only by command
- An in-game journal manager on Minecraft 1.21.6 and later, and journals written as files with guided commands on 1.20 to 1.21.5
- Server-side implementation for multiplayer compatibility
- Customizable content through configuration
- A preview of each journal's pages in the in-game manager (1.21.6 and later)

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Download the latest `travelers-journal-<version>.jar` from the releases page. The same jar works on every supported Minecraft version (see below)
3. Place the .jar file in your server's `mods` folder
4. Install the required dependencies:
   - Fabric API

### Supported Minecraft versions

One jar runs on Minecraft 1.20 to 1.20.4, 1.21 to 1.21.11 and 26.1 to 26.3. It carries a build for each range of versions, and Fabric loads the one that matches your server, so a server that moves to another supported version keeps the same jar. On 1.20.5 and 1.20.6, Fabric refuses it at startup.

The server needs the Java version its Minecraft needs: 17 or newer for 1.20.x, 21 for 1.21.x and 25 for 26.x. The in-game manager uses Minecraft's server dialogs, which start at 1.21.6; on 1.20 to 1.21.5 the journals are edited as files, as "Managing journals by file" below describes.

Fabric's mod list shows the jar as two entries: Traveler's Journal, and the build for your version (`travelers-journal-mc`).

### Upgrading

Replace the old jar in your `mods` folder with the new one and restart. The files in `config/travelers-journal/` carry over as they are:
- your settings, text and join list;
- the journals your players already carry, including ones under a legacy title, which refresh the next time they are used;
- players already on the join list, who do not get a second copy.

Your journal becomes the journal `default`, still read from those same files. Nothing is moved or rewritten, so going back to an earlier release finds everything where it left it. Every release is tested upgrading from each earlier release, and going back to each one.

Moving your world to a newer Minecraft keeps the journals too. Up to 1.20.4 a book is stored differently, and Minecraft converts it when the world opens on a later version; each copy still belongs to its journal and refreshes when used.

## ⌨️ Commands

For operators and the server console. `/journals` is a shorter name for `/travelers_journal`, and the two work the same way. Operators need level 2 unless `operator_level` in `config.txt` says otherwise.

| Command | What it does |
|---|---|
| `/journals` | In game on 1.21.6 and later, opens the journal manager. Elsewhere, lists these commands. |
| `/journals list` | Shows every journal, and whether it goes to new players. |
| `/journals give <players> [journal]` | Gives each player a journal, `default` unless one is named. A player with a full inventory finds it at their feet. |
| `/journals create <id>` | Adds a journal in `journals/<id>/`. The id is a short name such as `rules`: the commands use it to name the journal, and it is the folder's name. Ids use lower-case letters, digits and `-`, up to 32 characters. The book's title is set separately. |
| `/journals delete <journal>` | Moves a journal's folder to `backups/journals/<id>/`. Copies players already carry stay as signed books. `default` cannot be deleted. |
| `/journals edit [journal]` | In game on 1.21.6 and later, opens the journal in the editor. Elsewhere, names its text and settings files. |
| `/journals reload` | Reads every journal's files again. Each copy shows the new text the next time it is used. |

Typed without what it needs, `create`, `give` or `delete` shows its usage with an example.

## 📚 Managing journals in game

On Minecraft 1.21.6 and later, an operator who types `/journals` gets the manager, built from Minecraft's own dialog screens, so players need no client mod.

- **The hub** lists every journal by title, plus Create journal and Reload from files.
- **The editor** holds the book title (up to 32 characters), the author, a "Give on first join" switch and the text in one box, in the same markup as the files. Save writes the files, keeping the previous text as a `.bak` file beside it. Preview shows the pages as they will read, without saving.
- **Give to me** puts a copy in your inventory, and **Delete journal** asks first, then moves the journal's folder to a backup.
- The editor, the preview and the delete confirmation show the journal's book as an icon; hover over it to see the title and author as players will.

The server checks every button press again, so only operators at the configured level can change anything. The editor's box holds 15,000 characters; a longer journal opens without the box and is edited as a file.

## 📝 Managing journals by file (1.20 to 1.21.5)

Before 1.21.6 there is no in-game manager. Each journal is written in two files, and the commands put them to work. A journal has an id, a short name such as `rules` that the commands use and that names its folder; the title on the book is set separately and can be up to 32 characters.

1. Run `/journals create rules`. It makes `config/travelers-journal/journals/rules/` with a starter text and settings. On these versions its reply, like `/journals`, links to this guide.
2. Write the text in `journals/rules/journal.md`, in the markup under Journal Content below.
3. Set the book in `journals/rules/journal.txt`:
   ```properties
   title=Server Rules
   author=Server
   give_on_join=false
   ```
   With `give_on_join=true`, every new player gets it once.
4. Run `/journals reload`.
5. Run `/journals give @s rules` and read your copy. There is no preview before 1.21.6, so your own copy is how to check where the pages break.
6. To change it later, edit the files and run `/journals reload` again. Every copy shows the new text the next time it is used.
7. When it is no longer needed, `/journals delete rules` moves its folder to `backups/journals/rules/`.

The journal every server starts with is `default`: its text is `travelers_journal.md` and its title, author and `give_on_join` are in `config.txt`, both in `config/travelers-journal/`. The files can be edited while the server runs, and nothing changes until `/journals reload`. `/journals edit` names a journal's files whenever you need them.

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
  - Headings: a line starting with `#` shows as typed, so make a heading bold: `**Rules**`
- Color codes: `<color>text<>` where color is one of:
  - `black`, `dark_blue`, `dark_green`, `dark_aqua`
  - `dark_red`, `dark_purple`, `gold`, `gray`
  - `dark_gray`, `blue`, `green`, `aqua`
  - `red`, `light_purple`, `yellow`, `white`
- Page breaks: Use `[page]` to start a new page. A page also ends when it fills the 14 lines the book shows, counting lines as the book wraps them, and a line too long for what is left carries on at the top of the next page.

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
