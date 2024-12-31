import fs from 'fs';
import path from 'path';

export function getBuildNumber() {
    const gradlePropertiesPath = path.join(process.cwd(), 'gradle.properties');
    const content = fs.readFileSync(gradlePropertiesPath, 'utf8');
    const buildMatch = content.match(/build_number\s*=\s*(\d+)/);
    let buildNumber = 1;
    
    if (buildMatch) {
        buildNumber = parseInt(buildMatch[1]) + 1;
    }
    
    // Update the build number in gradle.properties
    const updatedContent = content.replace(
        /build_number\s*=\s*\d+/,
        `build_number=${buildNumber}`
    );
    fs.writeFileSync(gradlePropertiesPath, updatedContent);
    
    return buildNumber;
}

export function incrementVersion(currentVersion, versionType) {
    const versionParts = currentVersion.split('.').map(Number);
    // Ensure we have 4 parts, adding build number if missing
    while (versionParts.length < 4) {
        versionParts.push(0);
    }
    
    switch (versionType) {
        case 'major':
            versionParts[0]++;
            versionParts[1] = 0;
            versionParts[2] = 0;
            // Keep build number
            break;
        case 'minor':
            versionParts[1]++;
            versionParts[2] = 0;
            // Keep build number
            break;
        case 'patch':
            versionParts[2]++;
            // Keep build number
            break;
        case 'build':
            versionParts[3]++;
            break;
    }
    
    return versionParts.join('.');
}

export function getCurrentVersion() {
    const gradlePropertiesPath = path.join(process.cwd(), 'gradle.properties');
    const content = fs.readFileSync(gradlePropertiesPath, 'utf8');
    const versionMatch = content.match(/mod_version\s*=\s*(.+)/);
    if (!versionMatch) {
        throw new Error('Could not find version in gradle.properties');
    }
    return versionMatch[1].trim();
}
