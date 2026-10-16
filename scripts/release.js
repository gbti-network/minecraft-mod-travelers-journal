// Release pipeline for Traveler's Journal: one jar per range of Minecraft versions.
// Linux or WSL only, because the test matrix is a bash script that boots real servers.
//
//   npm run release:stage                 build every node, run the test matrix and stage
//                                         build/release/<version>/
//   npm run release:publish [-- --dry-run]
//                                         tag the staged commit, push it with the tag, create the
//                                         GitHub release, and upload to Modrinth and CurseForge when
//                                         MODRINTH_TOKEN and CURSEFORGE_TOKEN are set (environment
//                                         or scripts/.env). --dry-run prints every step instead.
//
// Before staging, set mod_version in gradle.properties, add a "## [<version>]" section to
// .product/changelog.md and commit both. Staging refuses uncommitted changes and publishing tags
// exactly the commit that was staged; neither step commits anything. Each jar is listed for the
// Minecraft versions it passed in the test matrix and no others. Planet Minecraft has no upload
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

function fail(message) {
    console.error(`release: ${message}`);
    process.exit(1);
}

function run(command, args) {
    const result = spawnSync(command, args, { cwd: ROOT, stdio: 'inherit' });
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

function versionRange(gameVersions) {
    return gameVersions.length === 1 ? gameVersions[0] : `${gameVersions[0]} to ${gameVersions.at(-1)}`;
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
    const jars = fs.readdirSync(libs).filter(file => file.endsWith('.jar') && !file.endsWith('-probe.jar')).sort();
    const pairs = jars.flatMap(jar => {
        const probe = jar.replace(/\.jar$/, '-probe.jar');
        if (!fs.existsSync(path.join(libs, probe))) fail(`no probe jar for ${jar}`);
        return [path.join(libs, jar), path.join(libs, probe)];
    });
    run(path.join(SCRIPTS, 'test-matrix.sh'), ['matrix', matrixDir, ...pairs]);

    const results = fs.readFileSync(path.join(matrixDir, 'results.tsv'), 'utf8').trim().split('\n').map(line => line.split('\t'));
    fs.mkdirSync(releaseDir, { recursive: true });
    const entries = jars.map(file => {
        const versionNumber = file.slice(`${MOD_NAME}-`.length, -'.jar'.length);
        const gameVersions = results.filter(([label, , verdict]) => label === versionNumber && verdict === 'PASS').map(([, mc]) => mc);
        if (!gameVersions.length) fail(`${file} passed on no Minecraft version`);
        const data = fs.readFileSync(path.join(libs, file));
        fs.writeFileSync(path.join(releaseDir, file), data);
        return { file, version_number: versionNumber, game_versions: gameVersions,
            sha256: crypto.createHash('sha256').update(data).digest('hex') };
    });

    const table = ['| Minecraft | File |', '|---|---|',
        ...entries.map(e => `| ${versionRange(e.game_versions)} | \`${e.file}\` |`)].join('\n');
    fs.writeFileSync(path.join(releaseDir, 'release-notes.md'),
        `${notes}\n\n## Downloads\n\nOne jar per range of Minecraft versions. Each needs Fabric Loader and Fabric API.\n\n${table}\n`);
    fs.writeFileSync(path.join(releaseDir, 'SHA256SUMS'), entries.map(e => `${e.sha256}  ${e.file}\n`).join(''));
    fs.writeFileSync(path.join(releaseDir, 'manifest.json'), JSON.stringify({ version, commit, entries }, null, 2) + '\n');
    console.log(`\nStaged ${TITLE} ${version} from ${commit.slice(0, 7)} in ${releaseDir}:\n\n${table}`);
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
            name: `${TITLE} ${manifest.version} for Minecraft ${versionRange(e.game_versions)}`,
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
            displayName: `${TITLE} ${manifest.version} for Minecraft ${versionRange(e.game_versions)}`,
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

    const newest = manifest.entries.at(-1);
    console.log(`\nPlanet Minecraft (by hand): a post holds one file. On the Updates tab of post 7073620, upload ${newest.file}`
        + ` and set the game version to ${newest.game_versions.at(-1)}; the description should name the full range and link Modrinth for the other jars.`);
}

const [command, ...flags] = process.argv.slice(2);
if (command === 'stage') stage();
else if (command === 'publish') await publish(flags.includes('--dry-run'));
else fail('usage: release.js stage | publish [--dry-run]');
