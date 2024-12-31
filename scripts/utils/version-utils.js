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
    // Get only major.minor.patch parts
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

export function getCurrentVersion() {
    const gradlePropertiesPath = path.join(process.cwd(), 'gradle.properties');
    const content = fs.readFileSync(gradlePropertiesPath, 'utf8');
    const versionMatch = content.match(/mod_version\s*=\s*(.+)/);
    if (!versionMatch) {
        throw new Error('Could not find version in gradle.properties');
    }
    // Return only major.minor.patch parts
    return versionMatch[1].trim().split('.').slice(0, 3).join('.');
}

export function getFullVersion() {
    const version = getCurrentVersion();
    const buildNumber = getBuildNumber();
    return `${version}.${buildNumber}`;
}

export function updateVersionInGradle(version, includeBuildNumber = false) {
    const gradlePropertiesPath = path.join(process.cwd(), 'gradle.properties');
    let gradleContent = fs.readFileSync(gradlePropertiesPath, 'utf8');
    
    if (includeBuildNumber) {
        const buildNumber = getBuildNumber();
        version = `${version}.${buildNumber}`;
    }
    
    gradleContent = gradleContent.replace(
        /mod_version\s*=\s*.+/,
        `mod_version=${version}`
    );
    
    fs.writeFileSync(gradlePropertiesPath, gradleContent);
    return version;
}
