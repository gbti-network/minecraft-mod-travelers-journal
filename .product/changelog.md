# Changelog


## [2.0.0]
- Runs on Minecraft 1.20 to 1.20.4 and every version from 1.21 to 26.3, from one jar. It carries a build for each range of versions, and Fabric loads the one that matches the server, so moving a server to another supported version keeps the same jar.
- The journal is built directly as a book instead of through a /give command, so its text works on Minecraft 1.21.5 and later and no longer breaks on a backslash in the journal file.
- Quotes and apostrophes appear exactly as typed in the journal file; they are no longer turned into curly quotes.
- Using the journal from the offhand refreshes it there, and no longer replaces the item in the selected hotbar slot.
- Using a journal after its text changed opens the new text straight away. Earlier releases showed the old text once more and the new text only the second time.
- A journal title longer than 32 characters, Minecraft's limit for book titles, is shortened instead of stopping the book from being given.
- Several named journals. The journal you have becomes `default`, read from the same files; others live in `config/travelers-journal/journals/<id>/`, and each can go to every new player once or be given only by command.
- An in-game journal manager on Minecraft 1.21.6 and later: `/journals` opens a list of every journal, and the editor sets each one's title, author, first-join rule and text, with a preview of its pages laid out as the book shows them. The editor, preview and delete screens show the journal's book as an icon, its tooltip giving the title and author. On older versions the journals are edited as files, as before.
- New commands: `/journals list`, `create <id>`, `delete <journal>` (the folder moves to a backup, and copies players carry stay as signed books), `edit [journal]`, and `give <players> [journal]`.
- `/journals` works as a shorter name for `/travelers_journal`. Typed on its own, either name opens the manager in game or lists the commands, instead of failing as an incomplete command.
- Typed without what it needs, `/journals create`, `give` or `delete` shows its usage with an example, instead of failing as an incomplete command.
- The help says what a journal's id is: a short name such as `rules`, used by the commands and as its folder's name. On 1.20 to 1.21.5, where there is no manager, the help and `/journals create` link to the README's guide to managing journals by file.
- New journals, and the default journal on a fresh install, start with a bold title. The markup has never read `#` as a heading, so those lines showed the `#` as typed. Existing files are left as they are.
- `operator_level` in `config.txt` sets who may use the commands and the manager (level 2 by default), and `/journals reload` applies a change. `give_on_join` in `config.txt` switches the default journal on or off for new players.
- Minecraft 1.20 to 1.20.4 are supported for the first time, on Java 17 or newer. Journals there are edited as files. A world moved from one of them to a later Minecraft keeps its journals: each copy still belongs to its journal and refreshes when used.
- Reloading no longer reads the join list in a second time.
- Pages break where the book wraps them, so a long line no longer pushes text off the bottom of a page, where the book hid it. A journal whose pages already fit is unchanged.
- The mod's metadata names its GPL-3.0 licence and links the project page and source code.
- Needs Fabric Loader 0.16.10 or newer, the oldest that current Fabric API runs on.
- The web preview that earlier READMEs described is gone. No release ever installed it, and on 1.21.6 and later the in-game manager previews the pages instead.
- Players who join during a server's first start after installing now get the journal. Earlier versions wrote their default files without reading them back, so those players were marked as served and given nothing.
- Upgrading keeps everything: put the new jar in place of any earlier release, and the settings, text, join list and the journals players already carry keep working. Every earlier release was tested upgrading to this one, and going back from this one to it.

## [1.2.11]
- Cleaning up dev files and folders

## [1.2.6]
- Added support for legacy book titles
- Added debugging logging system that can be enabled/disabled
- Initial release with basic journal functionality
- Added configuration support for customizing journal title and author
- Implemented give-at-first-join functionality
- Syncronizes journal content with markdown resource file.