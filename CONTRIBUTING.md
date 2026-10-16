# Contributing to Traveler's Journal

We love your input! We want to make contributing to Traveler's Journal as easy and transparent as possible, whether it's:

- Reporting a bug
- Discussing the current state of the code
- Submitting a fix
- Proposing new features
- Becoming a maintainer

## Development Process

We use GitHub to host code, to track issues and feature requests, as well as accept pull requests.

1. Fork the repo and create your branch from `main`
2. If you've added code that should be tested, add tests
3. If you've changed APIs, update the documentation
4. Ensure the test suite passes
5. Make sure your code lints
6. Issue that pull request!

## Build Process

One source tree builds a jar for each range of Minecraft versions, using
[Stonecutter](https://stonecutter.kikugie.dev/). Each folder under `versions/` is one jar: its
`gradle.properties` names the Minecraft version it builds against and the range its jar declares.
Code that differs between versions is marked with Stonecutter comments, for example:

```java
//? if >=1.21.2 {
return InteractionResult.PASS;
//?} else {
/*return InteractionResultHolder.pass(player.getItemInHand(hand));
*///?}
```

You need JDK 25. It also builds the 1.21.x jars, which target Java 21.

1. Build every jar into `build/libs/`:
   ```bash
   ./gradlew buildAndCollect
   ```
   `npm run build` does the same, and `npm run build -- --deploy` also copies the 1.21.1 jar into
   the GBTI server's `mods` folder.
2. Build one jar: `./gradlew :1.21.11:build`.
3. Work on one version in your IDE: `./gradlew "Set active project to 1.21.1"`. Run
   `./gradlew "Reset active project"` before committing, so `src/` is committed in its 26.3 state.

## Testing

`scripts/test-matrix.sh` boots every jar on a real Fabric server for every Minecraft version from
1.21 to 26.3, with the probe test mod in `src/probe` beside it. The probe joins mock players and
checks that a new player gets the journal once, that the text and formatting are right, that
reload and use refresh it, and that only operators can run the command, under either of its
names (`/travelers_journal` and `/journals`). Each jar must pass inside
its declared range, and Fabric must refuse it everywhere else. It runs on Linux or WSL:

```bash
npm test
```

### Upgrades from past releases

A server that ran any earlier Traveler's Journal must keep its journal when it swaps in a new jar:
its config, its text, its join list and the books its players already carry. The upgrade test
proves it on real servers:

1. It downloads every release Modrinth lists for Minecraft 1.21.1, where every release before
   1.3.0 ran, and checks each against Modrinth's SHA-512.
2. For each release it boots the old jar first and the new one second, on the same world.
3. It runs that twice: once with the old jar's own default config, and once with the customised
   install in `src/probe/fixtures/past-install`.

`src/probe/java/com/gbti/probe/UpgradeProbe.java` holds the checks:

- nobody who already had the journal gets a second copy;
- the old book and one under a legacy title are both recognised and refresh on use;
- the join list keeps every entry;
- new players still get the journal.

To add other old jars, for example a server's own build, list them in `EXTRA_PAST`:

```bash
npm run test:upgrades
EXTRA_PAST="/path/to/travelers-journal-1.2.6.17.jar" npm run test:upgrades
```

## Releasing

1. Set `mod_version` in `gradle.properties`, add a `## [<version>]` section to
   `.product/changelog.md`, and commit both.
2. `npm run release:stage` builds every jar, runs the test matrix and the upgrade test, and stages
   the jars, release notes and checksums in `build/release/<version>/`.
3. `npm run release:publish -- --dry-run` shows what publishing will do. Without `--dry-run` it tags
   the commit, pushes, creates the GitHub release and uploads to Modrinth and CurseForge when
   `MODRINTH_TOKEN` and `CURSEFORGE_TOKEN` are set. Planet Minecraft is updated by hand.

## Any contributions you make will be under the GNU General Public License v3.0

In short, when you submit code changes, your submissions are understood to be under the same [GNU General Public License v3.0](LICENSE) that covers the project. Feel free to contact the maintainers if that's a concern.

## Report bugs using GitHub's [issue tracker](https://github.com/gbti-network/minecraft-mod-travelers-journal/issues)

We use GitHub issues to track public bugs. Report a bug by [opening a new issue](https://github.com/gbti-network/minecraft-mod-travelers-journal/issues/new); it's that easy!

## Write bug reports with detail, background, and sample code

**Great Bug Reports** tend to have:

- A quick summary and/or background
- Steps to reproduce
  - Be specific!
  - Give sample code if you can
- What you expected would happen
- What actually happens
- Notes (possibly including why you think this might be happening, or stuff you tried that didn't work)

## License

By contributing, you agree that your contributions will be licensed under its GNU General Public License v3.0.
