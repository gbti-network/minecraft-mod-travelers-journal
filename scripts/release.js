import * as fs from 'fs';
import * as path from 'path';
import { exec } from 'child_process';
import { promisify } from 'util';
import * as dotenv from 'dotenv';
import { fileURLToPath } from 'url';
import { getCurrentVersion } from './utils/version-utils.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Load environment variables
dotenv.config({ path: path.join(__dirname, '.env') });

const execAsync = promisify(exec);

function incrementVersion(currentVersion, versionType) {
    // Get the first 3 parts of the version number
    const versionParts = currentVersion.split('.').slice(0, 3).map(Number);
    
    switch (versionType) {
        case 'major':
            versionParts[0]++;
            versionParts[1] = 0;
            versionParts[2] = 0;
            break;
        case 'minor':
            versionParts[1]++;
            versionParts[2] = 0;
            break;
        case 'patch':
            versionParts[2]++;
            break;
    }
    
    return versionParts.join('.');
}

async function getChangelogContent(version) {
    try {
        const changelogPath = path.join(process.cwd(), '.product', 'changelog.md');
        const changelogContent = fs.readFileSync(changelogPath, 'utf8');
        const versionHeader = `## [${version}]`;
        const lines = changelogContent.split('\n');
        let content = [];
        let isInSection = false;
        
        for (let line of lines) {
            if (line.startsWith(versionHeader)) {
                isInSection = true;
                continue;
            } else if (isInSection && line.startsWith('## [')) {
                break;
            } else if (isInSection && line.trim()) {
                content.push(line);
            }
        }

        return content.join('\n').trim();
    } catch (error) {
        console.error('Error reading changelog:', error);
        return '';
    }
}

async function createGitHubRelease(version, changelogContent) {
    try {
        const tag = `v${version}`;
        
        // Create and push tag
        await execAsync(`git tag ${tag}`);
        await execAsync('git add .');
        await execAsync(`git commit -m "Release ${tag}"`);
        await execAsync('git push origin develop --tags');

        // Create GitHub release
        const releaseData = {
            tag_name: tag,
            name: `Release ${tag}`,
            body: changelogContent || '',
            draft: false,
            prerelease: false
        };

        // Write release data to temp file
        const tempFile = path.join(__dirname, 'release-data.json');
        fs.writeFileSync(tempFile, JSON.stringify(releaseData));

        // Create GitHub release using curl
        const curlCommand = `curl -X POST -H "Authorization: token ${process.env.GITHUB_TOKEN}" -H "Content-Type: application/json" -d "@${tempFile}" https://api.github.com/repos/${process.env.GITHUB_REPO}/releases`;
        await execAsync(curlCommand);
        
        // Cleanup
        fs.unlinkSync(tempFile);
        
        console.log(`GitHub release ${tag} created successfully!`);
    } catch (error) {
        console.error('Failed to create GitHub release:', error);
        throw error;
    }
}

async function updateVersionInFiles(newVersion) {
    // Update gradle.properties
    const gradlePropertiesPath = path.join(process.cwd(), 'gradle.properties');
    let gradleContent = fs.readFileSync(gradlePropertiesPath, 'utf8');
    
    // Update version and reset build number
    gradleContent = gradleContent.replace(
        /mod_version\s*=\s*.+/,
        `mod_version=${newVersion}`
    );
    gradleContent = gradleContent.replace(
        /build_number\s*=\s*.+/,
        'build_number=1'
    );
    
    fs.writeFileSync(gradlePropertiesPath, gradleContent);
}

async function backupFiles() {
    const filesToBackup = [
        path.join(process.cwd(), 'gradle.properties'),
        path.join(process.cwd(), '.product', 'changelog.md')
    ];
    
    const backups = {};
    for (const file of filesToBackup) {
        backups[file] = fs.readFileSync(file, 'utf8');
    }
    return backups;
}

async function revertFiles(backups) {
    for (const [file, content] of Object.entries(backups)) {
        fs.writeFileSync(file, content, 'utf8');
    }
    console.log('Reverted files to original state');
}

async function deploy() {
    let backups;
    try {
        const currentVersion = getCurrentVersion();
        console.log(`Current version: ${currentVersion}`);
        
        // Get release type from command line args
        const releaseType = process.argv[2] || 'patch';
        if (!['major', 'minor', 'patch'].includes(releaseType)) {
            throw new Error('Invalid release type. Use: major, minor, or patch');
        }
        
        // Get base version (first 3 parts)
        const baseVersion = currentVersion.split('.').slice(0, 3).join('.');
        
        // Increment version
        const newVersion = incrementVersion(baseVersion, releaseType);
        console.log(`New version will be: ${newVersion}`);
        
        // Get confirmation
        const readline = (await import('readline')).default;
        const rl = readline.createInterface({
            input: process.stdin,
            output: process.stdout
        });
        
        await new Promise((resolve) => {
            rl.question('Continue with release? (y/N) ', (answer) => {
                rl.close();
                if (answer.toLowerCase() !== 'y') {
                    console.log('Release cancelled');
                    process.exit(0);
                }
                resolve();
            });
        });

        // Backup files before making any changes
        backups = await backupFiles();
        console.log('Created backup of version files');
        
        // Update version in files
        await updateVersionInFiles(newVersion);
        console.log('Updated version in files');
        
        // Get changelog content
        const changelogContent = await getChangelogContent(newVersion);
        if (!changelogContent) {
            throw new Error('No changelog content found for this version');
        }
        
        // Create GitHub release
        await createGitHubRelease(newVersion, changelogContent);
        
        console.log(' Release completed successfully!');
        console.log(`Version: ${newVersion}`);
        
    } catch (error) {
        console.error(' Release failed:', error.message);
        if (backups) {
            console.log('Attempting to revert changes...');
            try {
                await revertFiles(backups);
                // Remove the tag if it was created
                const newVersion = getCurrentVersion();
                try {
                    await execAsync(`git tag -d v${newVersion}`);
                    await execAsync(`git push origin :refs/tags/v${newVersion}`);
                    console.log('Removed version tag');
                } catch (tagError) {
                    // Tag might not exist, ignore error
                }
            } catch (revertError) {
                console.error('Failed to revert changes:', revertError.message);
                console.error('Manual intervention may be required');
            }
        }
        process.exit(1);
    }
}

// Run the deployment
deploy();
