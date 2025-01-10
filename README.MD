# 📖 Traveler's Journal

A Minecraft mod that provides players with a special journal that updates its contents dynamically. Built with Fabric for Minecraft 1.21.1.

## ✨ Features

- Dynamic journal content that updates when used
- Automatic distribution to new players
- Server-side implementation for multiplayer compatibility
- Customizable content through configuration
- Real-time preview system for journal formatting

## 🚀 Installation

1. Install [Fabric Loader](https://fabricmc.net/use/)
2. Download the latest version of Traveler's Journal from the releases page
3. Place the .jar file in your server's `mods` folder
4. Install the required dependencies:
   - Fabric API

## 🔧 Configuration

The mod can be configured through files in the `config/travelers-journal/` directory:

### Basic Settings (`config.txt`)
```properties
title=Traveler's Journal
author=Server
give_at_spawn=true
debug_logging=false
legacy_titles=Traveller's Journal,Travelers Journal
```

### Journal Content
Create or edit `config/travelers-journal/travelers_journal.md` to set your journal's content. The mod supports:

- Markdown-style formatting:
  - Bold: `**text**`
  - Italic: `*text*`
  - Underline: `_text_`
- Color codes: `<color>text<>` where color is one of:
  - `black`, `dark_blue`, `dark_green`, `dark_aqua`
  - `dark_red`, `dark_purple`, `gold`, `gray`
  - `dark_gray`, `blue`, `green`, `aqua`
  - `red`, `light_purple`, `yellow`, `white`
- Page breaks: Use `[page]` to start a new page

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

- 🎮 Access to our private Minecraft server
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
