// Builds the jar for every supported Minecraft version (./gradlew buildAndCollect) into build/libs/.
//
//   npm run build                  build only
//   npm run build -- --deploy      also copy the GBTI server's jar into its mods folder
//
// --deploy replaces any travelers-journal jar in the server's mods folder, so stop the server
// first. The GBTI server runs Minecraft 1.21.1, so it gets the 1.21.1 node's jar.
import { spawnSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const PROJECT_ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const SERVER_MODS_DIR = process.env.GBTI_SERVER_MODS
    || (process.platform === 'win32' ? 'D:\\_Outfits\\GBTI\\MinecraftServer\\.server\\mods'
        : '/mnt/d/_Outfits/GBTI/MinecraftServer/.server/mods');
const SERVER_NODE = '1.21.1';
const MOD_NAME = 'travelers-journal';

const gradlew = process.platform === 'win32' ? 'gradlew.bat' : './gradlew';
const build = spawnSync(gradlew, ['buildAndCollect'], { cwd: PROJECT_ROOT, stdio: 'inherit', shell: process.platform === 'win32' });
if (build.status !== 0) {
    console.error('Build failed');
    process.exit(1);
}

const libs = path.join(PROJECT_ROOT, 'build', 'libs');
const jars = fs.readdirSync(libs).filter(file => file.endsWith('.jar') && !file.endsWith('-probe.jar'));
console.log(`Built ${jars.length} jars in ${libs}:`);
jars.forEach(jar => console.log(`  ${jar}`));

if (process.argv.includes('--deploy')) {
    const nodeLibs = path.join(PROJECT_ROOT, 'versions', SERVER_NODE, 'build', 'libs');
    const jar = fs.readdirSync(nodeLibs).find(file => file.startsWith(`${MOD_NAME}-`)
        && file.endsWith('.jar') && !file.endsWith('-sources.jar') && !file.endsWith('-probe.jar'));
    if (!jar) {
        console.error(`No ${SERVER_NODE} jar in ${nodeLibs}`);
        process.exit(1);
    }
    for (const old of fs.readdirSync(SERVER_MODS_DIR).filter(file => file.startsWith(MOD_NAME) && file.endsWith('.jar'))) {
        fs.unlinkSync(path.join(SERVER_MODS_DIR, old));
        console.log(`Removed ${old} from the server`);
    }
    fs.copyFileSync(path.join(nodeLibs, jar), path.join(SERVER_MODS_DIR, jar));
    console.log(`Copied ${jar} to ${SERVER_MODS_DIR}`);
}
