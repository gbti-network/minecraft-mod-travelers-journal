// Release pipeline for Traveler's Journal: one jar for every supported Minecraft version, holding
// a jar per range of versions inside it (scripts/bundle.py).
// Linux or WSL only, because the test matrix is a bash script that boots real servers.
//
//   npm run release:stage                 build every node, run the test matrix, bundle the jars,
//                                         test the bundle on every version, from and back to every
//                                         past release and through world moves, and stage it in
//                                         build/release/<version>/
//   npm run release:publish [-- --dry-run]
//                                         tag the staged commit, push it with the tag, create the
//                                         GitHub release, and upload to Modrinth and CurseForge when
//                                         MODRINTH_TOKEN and CURSEFORGE_TOKEN are set (environment
//                                         or scripts/.env). --dry-run prints every step instead.
//
// Before staging, set mod_version in gradle.properties, add a "## [<version>]" section to
// .product/changelog.md and commit both. Staging refuses uncommitted changes and publishing tags
// exactly the commit that was staged; neither step commits anything. The jar is listed for the
// Minecraft versions it passed on and no others. Planet Minecraft has no upload
// API, so its step is printed for doing by hand.
import { execFileSync, spawnSync } from 'child_process';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const SCRIPTS = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.join(SCRIPTS, '..');
const MOD_NAME = 'travelers-journal';
const TITLE = "Traveler's Journal";
const GITHUB_REPO = 'gbti-network/minecraft-mod-travelers-journal';
const MODRINTH_PROJECT = '6Uocsa4f';
const MODRINTH_FABRIC_API = 'P7dR8mSH';
const CURSEFORGE_PROJECT = 1743974;
const USER_AGENT = 'gbti-network/minecraft-mod-travelers-journal (release script)';
// Every release before 2.0.0 was built for 1.21.1, so that is where existing servers upgrade from
const UPGRADE_FROM_MINECRAFT = '1.21.1';
// Worlds moved to a later Minecraft must keep their journals, in inventories, chests and lecterns:
// from NBT books (1.20.4 and older) to item components, across 1.21.5's change to stored text,
// and into 26.x
const WORLD_MOVES = [['1.20.1', '1.21.1'], ['1.20.4', '1.21.1'], ['1.21.1', '1.21.5'], ['1.21.5', '26.1'], ['1.20.1', '26.3']];
// The probe's checks per passing cell, stated here as well as by the probe itself, so a phase that
// quietly stops running fails the release instead of shrinking the count both sides agree on
const CHECKS_BEFORE_MANAGER = 53;
const CHECKS_WITH_MANAGER = 73;
// Versions inside the supported span that no jar covers; the shipped jar must be refused there
const UNCOVERED = ['1.20.5', '1.20.6'];

function fail(message) {
    console.error(`release: ${message}`);
    process.exit(1);
}

function run(command, args, env = {}) {
    const result = spawnSync(command, args, { cwd: ROOT, stdio: 'inherit', env: { ...process.env, ...env } });
    if (result.status !== 0) fail(`${command} ${args.join(' ')} exited with ${result.status}`);
}

function git(...args) {
    return execFileSync('git', args, { cwd: ROOT, encoding: 'utf8' }).trim();
}

function loadEnv() {
    const file = path.join(SCRIPTS, '.env');
    if (!fs.existsSync(file)) return;
    for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
        const match = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*?)\s*$/);
        if (match && !(match[1] in process.env)) process.env[match[1]] = match[2].replace(/^(['"])(.*)\1$/, '$2');
    }
}

function modVersion() {
    const match = fs.readFileSync(path.join(ROOT, 'gradle.properties'), 'utf8').match(/^mod_version\s*=\s*(\S+)\s*$/m);
    if (!match) fail('no mod_version in gradle.properties');
    return match[1];
}

function changelog(version) {
    const lines = fs.readFileSync(path.join(ROOT, '.product', 'changelog.md'), 'utf8').split(/\r?\n/);
    const start = lines.findIndex(line => line.startsWith(`## [${version}]`));
    if (start < 0) return '';
    const end = lines.findIndex((line, i) => i > start && line.startsWith('## ['));
    return lines.slice(start + 1, end < 0 ? undefined : end).join('\n').trim();
}

// Compares Minecraft versions part by part, so 1.20.10 would come after 1.20.4
function mcBefore(a, b) {
    const pa = a.split('.').map(Number);
    const pb = b.split('.').map(Number);
    for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
        const d = (pa[i] ?? 0) - (pb[i] ?? 0);
        if (d) return d < 0;
    }
    return false;
}

// Supported versions as one run per family (1.20.x, 1.21.x, 26.x): "1.20 to 1.20.4, 1.21 to 1.21.11
// and 26.1 to 26.3". A family is the first two parts below 26, and the year from 26 on.
function versionList(gameVersions) {
    const family = mc => {
        const parts = mc.split('.');
        return Number(parts[0]) >= 26 ? parts[0] : `${parts[0]}.${parts[1]}`;
    };
    const runs = [];
    for (const mc of gameVersions) {
        const last = runs.at(-1);
        if (last && family(last.at(-1)) === family(mc)) last.push(mc);
        else runs.push([mc]);
    }
    const parts = runs.map(r => (r.length === 1 ? r[0] : `${r[0]} to ${r.at(-1)}`));
    return parts.length === 1 ? parts[0] : `${parts.slice(0, -1).join(', ')} and ${parts.at(-1)}`;
}

function expectedChecks(mc) {
    return mcBefore(mc, '1.21.6') ? CHECKS_BEFORE_MANAGER : CHECKS_WITH_MANAGER;
}

// Every passing cell ran exactly the checks this file expects for its version
function holdCounts(rows, what) {
    const wrong = rows.filter(([, , verdict]) => verdict === 'PASS')
        .map(([label, mc, , , evidence]) => [label, mc, Number(((evidence ?? '').match(/pass=(\d+)/) ?? [])[1])])
        .filter(([, mc, ran]) => ran !== expectedChecks(mc));
    if (wrong.length) fail(`${what}: ${wrong.map(([label, mc, ran]) => `${label} on ${mc} ran ${ran} checks, not ${expectedChecks(mc)}`).join('; ')}`);
}

// Past releases Modrinth does not list, such as a server's own build or a release not published
// yet, go in .data/past-releases/ (never committed); the upgrade and downgrade tests take them too
function localPast() {
    const dir = path.join(ROOT, '.data', 'past-releases');
    if (!fs.existsSync(dir)) return [];
    return fs.readdirSync(dir).filter(file => file.endsWith('.jar')).sort().map(file => path.join(dir, file));
}

function readTsv(file) {
    return fs.readFileSync(file, 'utf8').trim().split('\n').map(line => line.split('\t'));
}

function stage() {
    const version = modVersion();
    const notes = changelog(version);
    if (!notes) fail(`.product/changelog.md has no "## [${version}]" section`);
    if (git('status', '--porcelain', '--untracked-files=no')) fail('commit your changes first: staging builds what is committed');
    const commit = git('rev-parse', 'HEAD');

    const libs = path.join(ROOT, 'build', 'libs');
    const matrixDir = path.join(ROOT, 'build', 'test-matrix', version);
    const releaseDir = path.join(ROOT, 'build', 'release', version);
    for (const dir of [libs, matrixDir, releaseDir]) fs.rmSync(dir, { recursive: true, force: true });

    run('./gradlew', ['buildAndCollect']);
    // Oldest Minecraft version first: a numeric sort puts mc1.21.2 before mc1.21.11
    const jars = fs.readdirSync(libs).filter(file => file.endsWith('.jar') && !file.endsWith('-probe.jar'))
        .sort((a, b) => a.localeCompare(b, 'en', { numeric: true }));
    const pairs = jars.flatMap(jar => {
        const probe = jar.replace(/\.jar$/, '-probe.jar');
        if (!fs.existsSync(path.join(libs, probe))) fail(`no probe jar for ${jar}`);
        return [path.join(libs, jar), path.join(libs, probe)];
    });
    run(path.join(SCRIPTS, 'test-matrix.sh'), ['matrix', matrixDir, ...pairs]);

    const results = readTsv(path.join(matrixDir, 'results.tsv'));
    holdCounts(results, 'per-range matrix');
    // Each per-range jar passed inside its declared range and was refused everywhere else, so no
    // two of them claim a version
    const ranges = jars.map(file => {
        const label = file.slice(`${MOD_NAME}-`.length, -'.jar'.length);
        const gameVersions = results.filter(([l, , verdict]) => l === label && verdict === 'PASS').map(([, mc]) => mc);
        if (!gameVersions.length) fail(`${file} passed on no Minecraft version`);
        return { file, game_versions: gameVersions };
    });
    const probeOf = r => path.join(libs, r.file.replace(/\.jar$/, '-probe.jar'));
    const rangeFor = mc => ranges.find(r => r.game_versions.includes(mc)) ?? fail(`no jar passed on Minecraft ${mc}`);
    const gameVersions = ranges.flatMap(r => r.game_versions);

    // The one jar that ships holds every per-range jar, and Fabric loads the one that fits. It is
    // tested as shipped: on every version beside the probe built for that version's range, and
    // refused on the versions inside the span that no range covers.
    const bundleFile = `${MOD_NAME}-${version}.jar`;
    const bundle = path.join(libs, bundleFile);
    run('python3', ['-I', path.join(SCRIPTS, 'bundle.py'), bundle, version, ...ranges.map(r => path.join(libs, r.file))]);
    const bundleDir = path.join(matrixDir, 'bundle');
    run(path.join(SCRIPTS, 'test-matrix.sh'), ['cells', bundleDir,
        ...gameVersions.flatMap(mc => [bundle, probeOf(rangeFor(mc)), mc]),
        ...UNCOVERED.flatMap(mc => [bundle, probeOf(ranges[0]), mc])]);
    const cells = readTsv(path.join(bundleDir, 'cells.tsv'));
    const expected = [...gameVersions.map(mc => [mc, 'PASS']), ...UNCOVERED.map(mc => [mc, 'REFUSED'])];
    const wrong = expected.filter(([mc, verdict]) => cells.find(([, v]) => v === mc)?.[2] !== verdict);
    if (wrong.length) fail(`${bundleFile} did not behave on ${wrong.map(([mc, verdict]) => `${mc} (expected ${verdict})`).join(', ')}`);
    holdCounts(cells, bundleFile);
    console.log(`bundle: passed on ${gameVersions.length} versions, refused on ${UNCOVERED.join(' and ')}`);

    // Every existing server must keep its journal: upgrade each past release on the version they
    // all ran on, then go back to each from this one
    const past = { EXTRA_PAST: [...localPast(), process.env.EXTRA_PAST ?? ''].join(' ').trim() };
    if (past.EXTRA_PAST) console.log(`past releases beside Modrinth's: ${past.EXTRA_PAST}`);
    const upgradeProbe = probeOf(rangeFor(UPGRADE_FROM_MINECRAFT));
    run(path.join(SCRIPTS, 'test-matrix.sh'), ['upgrades', path.join(matrixDir, 'upgrades'), bundle, upgradeProbe, UPGRADE_FROM_MINECRAFT], past);
    run(path.join(SCRIPTS, 'test-matrix.sh'), ['downgrades', path.join(matrixDir, 'downgrades'), bundle, upgradeProbe, UPGRADE_FROM_MINECRAFT], past);

    run(path.join(SCRIPTS, 'test-matrix.sh'), ['world-upgrades', path.join(matrixDir, 'world-upgrades'),
        ...WORLD_MOVES.flatMap(([from, to]) => [bundle, probeOf(rangeFor(from)), from, bundle, probeOf(rangeFor(to)), to])]);

    fs.mkdirSync(releaseDir, { recursive: true });
    const data = fs.readFileSync(bundle);
    fs.writeFileSync(path.join(releaseDir, bundleFile), data);
    const entries = [{ file: bundleFile, version_number: version, game_versions: gameVersions,
        sha256: crypto.createHash('sha256').update(data).digest('hex') }];
    const supported = versionList(gameVersions);
    fs.writeFileSync(path.join(releaseDir, 'release-notes.md'),
        `${notes}\n\n## Download\n\nOne jar, \`${bundleFile}\`, for every supported Minecraft version: ${supported}. It needs Fabric Loader and Fabric API.\n`);
    fs.writeFileSync(path.join(releaseDir, 'SHA256SUMS'), entries.map(e => `${e.sha256}  ${e.file}\n`).join(''));
    fs.writeFileSync(path.join(releaseDir, 'manifest.json'), JSON.stringify({ version, commit, entries, ranges }, null, 2) + '\n');
    console.log(`\nStaged ${TITLE} ${version} from ${commit.slice(0, 7)} in ${releaseDir}:\n\n${bundleFile}, for Minecraft ${supported}`);
}

function step(dryRun, command, args) {
    console.log(`${dryRun ? 'would run' : 'running'}: ${command} ${args.map(a => (/\s/.test(a) ? JSON.stringify(a) : a)).join(' ')}`);
    if (!dryRun) run(command, args);
}

function jarBlob(dir, file) {
    return new Blob([fs.readFileSync(path.join(dir, file))], { type: 'application/java-archive' });
}

async function modrinth(manifest, dir, notes, dryRun) {
    const token = process.env.MODRINTH_TOKEN;
    if (!token) {
        console.log('\nModrinth: MODRINTH_TOKEN is not set. Upload by hand at https://modrinth.com/mod/travelers-journal/versions:');
        manifest.entries.forEach(e => console.log(`  ${e.file}  game versions: ${e.game_versions.join(', ')}`));
        return;
    }
    for (const e of manifest.entries) {
        const data = {
            // The game versions are tagged; Modrinth caps a version's name at 64 characters
            name: `${TITLE} ${manifest.version}`,
            version_number: e.version_number,
            changelog: notes,
            dependencies: [{ project_id: MODRINTH_FABRIC_API, dependency_type: 'required' }],
            game_versions: e.game_versions,
            version_type: 'release',
            loaders: ['fabric'],
            featured: false,
            status: 'listed',
            project_id: MODRINTH_PROJECT,
            file_parts: [e.file],
            primary_file: e.file,
        };
        if (dryRun) {
            console.log(`would POST https://api.modrinth.com/v2/version ${JSON.stringify({ ...data, changelog: '(release notes)' })}`);
            continue;
        }
        const form = new FormData();
        form.append('data', JSON.stringify(data));
        form.append(e.file, jarBlob(dir, e.file), e.file);
        const response = await fetch('https://api.modrinth.com/v2/version', {
            method: 'POST', headers: { Authorization: token, 'User-Agent': USER_AGENT }, body: form });
        if (!response.ok) fail(`Modrinth refused ${e.file}: ${response.status} ${await response.text()}`);
        console.log(`Modrinth: uploaded ${e.version_number}`);
    }
}

async function curseforge(manifest, dir, notes, dryRun) {
    const token = process.env.CURSEFORGE_TOKEN;
    if (!token) {
        console.log(`\nCurseForge: CURSEFORGE_TOKEN is not set. Upload by hand in the author console (project ${CURSEFORGE_PROJECT}):`);
        manifest.entries.forEach(e => console.log(`  ${e.file}  game versions: ${e.game_versions.join(', ')}, Fabric, Server`));
        return;
    }
    const api = async route => {
        const response = await fetch(`https://minecraft.curseforge.com${route}`, { headers: { 'X-Api-Token': token, 'User-Agent': USER_AGENT } });
        if (!response.ok) fail(`CurseForge ${route}: ${response.status} ${await response.text()}`);
        return response.json();
    };
    const typeSlugs = new Map((await api('/api/game/version-types')).map(type => [type.id, type.slug]));
    const versions = await api('/api/game/versions');
    const find = (name, slugTest) => versions.find(v => v.name === name && slugTest(typeSlugs.get(v.gameVersionTypeID) ?? ''));
    const fabric = find('Fabric', slug => slug === 'modloader') ?? fail('CurseForge lists no Fabric loader');
    const server = find('Server', slug => slug === 'environment');

    for (const e of manifest.entries) {
        const ids = e.game_versions.map(mc => (find(mc, slug => slug.startsWith('minecraft-'))
            ?? fail(`CurseForge lists no Minecraft version ${mc}`)).id);
        const metadata = {
            changelog: notes,
            changelogType: 'markdown',
            displayName: `${TITLE} ${manifest.version}`,
            gameVersions: [...ids, fabric.id, ...(server ? [server.id] : [])],
            releaseType: 'release',
            relations: { projects: [{ slug: 'fabric-api', type: 'requiredDependency' }] },
        };
        if (dryRun) {
            console.log(`would POST /api/projects/${CURSEFORGE_PROJECT}/upload-file ${JSON.stringify({ ...metadata, changelog: '(release notes)' })}`);
            continue;
        }
        const form = new FormData();
        form.append('metadata', JSON.stringify(metadata));
        form.append('file', jarBlob(dir, e.file), e.file);
        const response = await fetch(`https://minecraft.curseforge.com/api/projects/${CURSEFORGE_PROJECT}/upload-file`, {
            method: 'POST', headers: { 'X-Api-Token': token, 'User-Agent': USER_AGENT }, body: form });
        if (!response.ok) fail(`CurseForge refused ${e.file}: ${response.status} ${await response.text()}`);
        console.log(`CurseForge: uploaded ${e.version_number} (file ${(await response.json()).id}, now in moderation)`);
    }
}

async function publish(dryRun) {
    loadEnv();
    const version = modVersion();
    const dir = path.join(ROOT, 'build', 'release', version);
    const manifestFile = path.join(dir, 'manifest.json');
    if (!fs.existsSync(manifestFile)) fail(`nothing staged for ${version}; run npm run release:stage first`);
    const manifest = JSON.parse(fs.readFileSync(manifestFile, 'utf8'));
    if (git('rev-parse', 'HEAD') !== manifest.commit) fail('HEAD is not the commit that was staged; stage again');
    if (git('status', '--porcelain', '--untracked-files=no')) fail('the tree has uncommitted changes');
    const tag = `v${version}`;
    if (git('tag', '--list', tag)) fail(`tag ${tag} already exists`);
    const branch = git('rev-parse', '--abbrev-ref', 'HEAD');
    const notes = fs.readFileSync(path.join(dir, 'release-notes.md'), 'utf8');

    step(dryRun, 'git', ['tag', tag]);
    step(dryRun, 'git', ['push', 'origin', `HEAD:refs/heads/${branch}`, `refs/tags/${tag}`]);
    step(dryRun, 'gh', ['release', 'create', tag, '-R', GITHUB_REPO, '--title', `Release ${tag}`,
        '--notes-file', path.join(dir, 'release-notes.md'), ...manifest.entries.map(e => path.join(dir, e.file))]);
    await modrinth(manifest, dir, notes, dryRun);
    await curseforge(manifest, dir, notes, dryRun);

    const jar = manifest.entries[0];
    console.log(`\nPlanet Minecraft (by hand): on the Updates tab of post 7073620, upload ${jar.file} and set the game version`
        + ` to ${jar.game_versions.at(-1)}; the description should name every supported version: ${versionList(jar.game_versions)}.`);
}

const [command, ...flags] = process.argv.slice(2);
if (command === 'stage') stage();
else if (command === 'publish') await publish(flags.includes('--dry-run'));
else fail('usage: release.js stage | publish [--dry-run]');
