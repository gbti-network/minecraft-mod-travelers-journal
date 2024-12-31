import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { getBuildNumber, getCurrentVersion, incrementVersion } from './utils/version-utils.js';

// Get __dirname equivalent in ES modules
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.join(__dirname, '..');

// Configuration
const MOD_NAME = 'travelers-journal';
const SERVER_MODS_DIR = 'D:\\_Outfits\\GBTI\\MinecraftServer\\.server\\mods';
const BUILD_DIR = path.join(PROJECT_ROOT, 'build', 'libs');

console.log('🚀 Starting build process...');

// Check if server is running
console.log('⚠️  Make sure the Minecraft server is stopped before continuing.');
console.log('   Press Ctrl+C to cancel, or wait 5 seconds to continue...');
await new Promise(resolve => setTimeout(resolve, 5000));

try {
    // Get current version and increment build number
    const currentVersion = getCurrentVersion();
    const buildNumber = getBuildNumber();
    
    // Split version and ensure it has 4 parts
    const versionParts = currentVersion.split('.');
    while (versionParts.length < 4) {
        versionParts.push('0');
    }
    
    // Set the 4th part to the build number
    versionParts[3] = buildNumber.toString();
    const newVersion = versionParts.join('.');
    
    // Update version in gradle.properties
    const gradlePropertiesPath = path.join(PROJECT_ROOT, 'gradle.properties');
    let gradleContent = fs.readFileSync(gradlePropertiesPath, 'utf8');
    gradleContent = gradleContent.replace(
        /mod_version\s*=\s*.+/,
        `mod_version=${newVersion}`
    );
    fs.writeFileSync(gradlePropertiesPath, gradleContent);
    
    console.log(`📈 Updated version to ${newVersion} (Build #${buildNumber})`);
    
    // Clear the libs folder
    console.log('🗑️ Clearing libs folder...');
    if (fs.existsSync(BUILD_DIR)) {
        const files = fs.readdirSync(BUILD_DIR);
        files.forEach(file => {
            const filePath = path.join(BUILD_DIR, file);
            fs.unlinkSync(filePath);
            console.log(`   Deleted: ${file}`);
        });
    } else {
        fs.mkdirSync(BUILD_DIR, { recursive: true });
    }
    
    // Run Gradle build
    console.log('📦 Running Gradle build...');
    execSync('gradlew.bat build', { stdio: 'inherit', cwd: PROJECT_ROOT });

    // Find the built jar - look for the exact version number
    console.log('🔍 Locating built jar...');
    const files = fs.readdirSync(BUILD_DIR);
    const jarFile = files.find(file => {
        const isMainJar = file.includes(MOD_NAME) && 
                         !file.includes('-sources') && 
                         !file.includes('-dev') &&
                         file.endsWith('.jar');
        return isMainJar;
    });

    if (!jarFile) {
        throw new Error('Could not find built jar file');
    }

    const jarPath = path.join(BUILD_DIR, jarFile);
    const newJarName = `${MOD_NAME}-${newVersion}.jar`;
    const newJarPath = path.join(BUILD_DIR, newJarName);

    // Rename the jar to include the full version
    fs.renameSync(jarPath, newJarPath);

    // Delete old version from server mods
    console.log('🗑️ Removing old version...');
    const oldFiles = fs.readdirSync(SERVER_MODS_DIR);
    oldFiles.forEach(file => {
        if (file.includes(MOD_NAME)) {
            fs.unlinkSync(path.join(SERVER_MODS_DIR, file));
            console.log(`   Deleted: ${file}`);
        }
    });

    // Copy new version
    console.log('📋 Copying new version...');
    fs.copyFileSync(newJarPath, path.join(SERVER_MODS_DIR, newJarName));

    console.log('✅ Build and deploy completed successfully!');
    console.log(`   Deployed: ${newJarName}`);
    console.log(`   Version: ${newVersion} (Build #${buildNumber})`);

} catch (error) {
    console.error('❌ Error:', error.message);
    process.exit(1);
}
