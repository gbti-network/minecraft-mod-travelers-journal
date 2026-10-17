# Traveler's Journal

Traveler's Journal is a Minecraft Fabric mod that provides server administrators with tools to create and distribute an in-game book whose contents are controlled through an markdown file. 

Any changes made to the markdown file will auto update across all held copies of the book. 

## Core Functions
- Server-side journal management for centralized control
- Several named journals, each given on first join or only by command
- An in-game journal manager on Minecraft 1.21.6 and later: `/journals` opens it for operators
- On 1.20 to 1.21.5, journals written as files, with a step-by-step guide in the README
- Automatic distribution system that gives new players a journal on first join
- Real-time content updates through simple markdown files
- A preview of each journal's pages in the in-game manager (1.21.6 and later)
- Supports rich text formatting including colors and lists

## Technical Specifications
- Platform: Minecraft 1.20 to 1.20.4 and 1.21 to 26.3, from one jar
- Framework: Fabric
- Dependencies: Fabric API
- License: GPL-3.0

## Configuration Options
- Custom journal titles
- Markdown formatting support
- Color code implementation
- Page break controls
- Spawn distribution settings

## Implementation
The mod operates through a server-side configuration system located in `config/travelers-journal/`. Administrators can modify journal content through standard text files using supported formatting syntax.

## Preview
On Minecraft 1.21.6 and later, the in-game manager (`/journals`) shows each journal's pages laid out as the book shows them, before anything is saved.

## Managing journals by file (1.20 to 1.21.5)
Before 1.21.6 there is no in-game manager, so journals are written as files:

1. `/journals create rules` makes a journal with the id `rules`, a short name the commands use. Its files are in `config/travelers-journal/journals/rules/`.
2. Write the text in `journal.md`, and the title, author and `give_on_join` in `journal.txt`.
3. Run `/journals reload`, then `/journals give @s rules` to read your copy.

To change a journal later, edit its files and run `/journals reload` again; every copy updates the next time it is used. Typed on its own, `/journals` lists the commands and links to the README's guide.

## Formatting Guide
The journal supports the following formatting options:

### Text Styling
- Bold: `**text**`
- Italic: `*text*`
- Underline: `_text_`
- Headings: a line starting with `#` shows as typed, so make a heading bold: `**Rules**`

### Lists
Unordered lists using dashes:
- First item
- Second item
- Third item


### Colors
Wrap text in a colour name: `<red>text<>`. The names are `black`, `dark_blue`, `dark_green`, `dark_aqua`, `dark_red`, `dark_purple`, `gold`, `gray`, `dark_gray`, `blue`, `green`, `aqua`, `red`, `light_purple`, `yellow` and `white`.

Example:
`<red>This text will be red<>`

### Page Management
- Create a new page: Insert `[page]` on a new line. Otherwise the book will automatically paginate.

To check a journal's layout, open it in the in-game manager's preview (1.21.6 and later), or give yourself a copy and read it.