import * as fs from 'fs';
import * as path from 'path';
import { exec } from 'child_process';
import { promisify } from 'util';
import * as dotenv from 'dotenv';
import { fileURLToPath } from 'url';
import { getCurrentVersion, incrementVersion } from './utils/version-utils.js';
import { buildJar } from './utils/build-utils.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Load environment variables
dotenv.config({ path: path.join(__dirname, '.env') });

const execAsync = promisify(exec);

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

async function createGitHubRelease(version, jarPath, changelogContent) {
    try {
        const tag = `v${version}`;
        
        // Create and push tag
        await execAsync(`git tag ${tag}`);
        await execAsync('git add .');
        await execAsync(`git commit -m "Release ${tag}"`);
        await execAsync('git push origin develop --tags');

        // Create GitHub release with jar attachment
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

        // Create GitHub release using curl (with proper Windows path handling)
        const curlCommand = `curl -X POST -H "Authorization: token ${process.env.GITHUB_TOKEN}" -H "Content-Type: application/json" -d "@${tempFile.replace(/\\/g, '/')}" "https://api.github.com/repos/${process.env.GITHUB_REPO}/releases"`;
        
        const releaseResponse = await execAsync(curlCommand);
        const release = JSON.parse(releaseResponse.stdout);
        
        // Upload asset (with proper Windows path handling)
        const assetName = path.basename(jarPath);
        const uploadCommand = `curl -X POST -H "Authorization: token ${process.env.GITHUB_TOKEN}" -H "Content-Type: application/java-archive" --data-binary "@${jarPath.replace(/\\/g, '/')}" "${release.upload_url.replace('{?name,label}', `?name=${assetName}`)}"`; 
        
        await execAsync(uploadCommand);
        
        // Cleanup
        fs.unlinkSync(tempFile);
        
        console.log(`GitHub release ${tag} created successfully with jar attachment!`);
    } catch (error) {
        console.error('Failed to create GitHub release:', error);
        throw error;
    }
}

async function deploy() {
    try {
        const currentVersion = getCurrentVersion();
        console.log(`Current version: ${currentVersion}`);
        
        // Get release type from command line args
        const releaseType = process.argv[2] || 'patch';
        if (!['major', 'minor', 'patch'].includes(releaseType)) {
            throw new Error('Invalid release type. Use: major, minor, or patch');
        }
        
        // Increment version (without build number)
        const newVersion = incrementVersion(currentVersion, releaseType);
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

        // Get changelog content
        const changelogContent = await getChangelogContent(newVersion);
        if (!changelogContent) {
            throw new Error('No changelog content found for this version');
        }
        
        // Build jar (without build number)
        const jarPath = await buildJar(newVersion, false);
        console.log(`Built jar: ${jarPath}`);
        
        // Create GitHub release with jar
        await createGitHubRelease(newVersion, jarPath, changelogContent);
        
        console.log(' Release completed successfully!');
        console.log(`Version: ${newVersion}`);
        
    } catch (error) {
        console.error(' Release failed:', error.message);
        process.exit(1);
    }
}

deploy();
