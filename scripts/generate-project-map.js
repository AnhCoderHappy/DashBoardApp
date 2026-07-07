const fs = require('fs');
const path = require('path');

// Folders to scan for core source code
const CORE_FOLDERS = [
  'packages/shared/src',
  'apps/backend/src',
  'apps/worker/src',
  'apps/dashboard/src',
  'supabase'
];

// File extensions to include in mapping
const ALLOWED_EXTENSIONS = new Set(['.ts', '.tsx', '.css', '.sql', '.html']);

// Exclude list to prevent reading binary or config assets in these folders
const EXCLUDE_FILES = new Set(['seed.sql']); // We keep migrations, ignore seed or other files if needed

function getLanguage(filePath) {
  const ext = path.extname(filePath).toLowerCase();
  switch (ext) {
    case '.ts': return 'typescript';
    case '.tsx': return 'tsx';
    case '.css': return 'css';
    case '.sql': return 'sql';
    case '.html': return 'html';
    default: return 'text';
  }
}

// Recursively walks a folder and returns all files matching extensions
function getFilesRecursive(dirPath, rootDir) {
  let results = [];
  let list;
  
  try {
    list = fs.readdirSync(dirPath);
  } catch (err) {
    return [];
  }

  list.forEach((file) => {
    const fullPath = path.join(dirPath, file);
    const stat = fs.statSync(fullPath);
    
    if (stat.isDirectory()) {
      results = results.concat(getFilesRecursive(fullPath, rootDir));
    } else {
      const ext = path.extname(file).toLowerCase();
      const relative = path.relative(rootDir, fullPath).replace(/\\/g, '/');
      if (ALLOWED_EXTENSIONS.has(ext) && !EXCLUDE_FILES.has(file)) {
        results.push({
          name: file,
          fullPath,
          relative,
          language: getLanguage(fullPath)
        });
      }
    }
  });

  return results;
}

// Build a clean folder tree visual from a list of relative paths
function buildTreeVisual(files) {
  const tree = {};
  
  // Convert list of files to a nested tree object
  files.forEach(file => {
    const parts = file.relative.split('/');
    let current = tree;
    parts.forEach((part, idx) => {
      if (!current[part]) {
        current[part] = idx === parts.length - 1 ? null : {};
      }
      current = current[part];
    });
  });

  // Render tree object to ASCII text
  function renderNode(node, prefix = '') {
    let result = '';
    if (!node) return result;
    
    const keys = Object.keys(node).sort((a, b) => {
      const aIsDir = node[a] !== null;
      const bIsDir = node[b] !== null;
      if (aIsDir && !bIsDir) return -1;
      if (!aIsDir && bIsDir) return 1;
      return a.localeCompare(b);
    });

    keys.forEach((key, index) => {
      const isLast = index === keys.length - 1;
      const connector = isLast ? '└── ' : '├── ';
      const nextPrefix = prefix + (isLast ? '    ' : '│   ');
      const isDir = node[key] !== null;

      result += `${prefix}${connector}${key}${isDir ? '/' : ''}\n`;
      if (isDir) {
        result += renderNode(node[key], nextPrefix);
      }
    });

    return result;
  }

  return renderNode(tree);
}

function generateMap() {
  const rootDir = path.resolve(__dirname, '..');
  console.log('🔍 Scanning workspace for core source files...');

  let allFiles = [];
  CORE_FOLDERS.forEach(folder => {
    const folderPath = path.join(rootDir, folder);
    if (fs.existsSync(folderPath)) {
      allFiles = allFiles.concat(getFilesRecursive(folderPath, rootDir));
    }
  });

  // Sort files by path alphabetically
  allFiles.sort((a, b) => a.relative.localeCompare(b.relative));

  console.log(`Found ${allFiles.length} source files to map.`);

  // 1. Build Title and File Tree
  let mapContent = `# Project Map & Source Code Snapshot — MData\n\n`;
  mapContent += 'Generated on: ' + new Date().toLocaleString('vi-VN') + '\n';
  mapContent += 'This file contains the complete source code tree and contents of all core logic files (libraries and config files are omitted).\n\n';
  
  mapContent += '## File Structure Tree\n\n';
  mapContent += '```txt\n';
  mapContent += 'MData/\n';
  mapContent += buildTreeVisual(allFiles);
  mapContent += '```\n\n';

  // 2. Append Content of Each File
  mapContent += '## Source Code Contents\n\n';
  
  allFiles.forEach(file => {
    try {
      const content = fs.readFileSync(file.fullPath, 'utf8');
      mapContent += `### File: \`${file.relative}\`\n\n`;
      mapContent += '```' + file.language + '\n';
      mapContent += content;
      // Add trailing newline if missing
      if (!content.endsWith('\n')) mapContent += '\n';
      mapContent += '```\n\n';
      mapContent += '---\n\n';
    } catch (err) {
      console.error(`Error reading file ${file.relative}:`, err.message);
    }
  });

  const outputPath = path.join(rootDir, 'project-map.md');
  fs.writeFileSync(outputPath, mapContent, 'utf8');
  
  console.log(`\n✅ Project map with file contents generated at: ${outputPath}`);
  console.log(`📊 Total files included: ${allFiles.length}`);
}

generateMap();
