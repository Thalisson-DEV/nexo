import { readFile, readdir } from 'node:fs/promises';
import { dirname, extname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

export const REPOSITORY_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
export const PRIMENG_PACKAGE_JSON = resolve(REPOSITORY_ROOT, 'node_modules', 'primeng', 'package.json');
export const COVERAGE_REGISTRY_PATH = resolve(
  REPOSITORY_ROOT,
  'src',
  'app',
  'design-system',
  'coverage',
  'primeng-coverage.registry.mjs'
);

export const DISPOSITIONS = Object.freeze([
  'wrapper',
  'pattern',
  'styled-direct',
  'infrastructure',
  'excluded'
]);

export const PRACTICAL_ENTRYPOINT_POLICY = Object.freeze({
  id: 'exact-public-consumer-export-v2',
  summary:
    'Start from every exact key in PrimeNG package exports. Treat metadata and generated ./types/* leaves as filtered package surfaces; disposition every remaining exact public consumer subpath, including nested icon leaves.',
  exclusions: Object.freeze([
    'Wildcard export keys are patterns rather than independently inventoryable entrypoints.',
    'primeng/package.json is package metadata, not an application UI or runtime surface.',
    'Generated primeng/types/* leaves are public type-import surfaces, but are accounted for as a filtered family rather than repeated component dispositions.',
    'Empty, internal-looking, or implementation-oriented public exports stay in the practical registry with an explicit excluded disposition.'
  ])
});

const SOURCE_EXTENSIONS = new Set([
  '.cjs',
  '.css',
  '.html',
  '.js',
  '.less',
  '.mjs',
  '.scss',
  '.ts'
]);
const IGNORED_DIRECTORY_NAMES = new Set([
  '.angular',
  '.git',
  '.idea',
  '.vscode',
  'coverage',
  'dist',
  'node_modules',
  'out-tsc'
]);

const LEGACY_MARKER_RULES = Object.freeze([
  {
    id: 'deprecated-entrypoint-alias',
    description: 'Known pre-v20 component alias imported instead of its current PrimeNG entrypoint.',
    pattern:
      /primeng\/(?:calendar|dropdown|inputswitch|inputtextarea|overlaypanel|sidebar)(?=['"`\s;)]|$)/g
  },
  {
    id: 'legacy-resource-import',
    description: 'Legacy primeng/resources stylesheet import; styled mode no longer uses this package path.',
    pattern: /primeng\/resources(?:\/[^'"`)\s;]*)?/g
  },
  {
    id: 'legacy-template-directive',
    description: 'pTemplate syntax retained from older PrimeNG templates; review for named template references.',
    pattern: /(?:^|\s|\[|\()pTemplate\b/g,
    templateContext: true
  },
  {
    id: 'deep-primeng-style-override',
    description: '::ng-deep override coupled to PrimeNG internals.',
    pattern: /::ng-deep(?:(?![{}]).)*(?:\.p-|p-)/gs,
    styleContext: true
  },
  {
    id: 'primeng-internal-class-selector',
    description: 'Stylesheet selector coupled directly to a PrimeNG .p-* implementation class.',
    pattern: /\.p-(?![0-9]+\b)[a-z][a-z0-9-]*/g,
    styleContext: true
  },
  {
    id: 'primeflex-v2-prefix',
    description: 'Candidate PrimeFlex v2 prefixed utility; verify before migration or removal.',
    pattern: /\bp-(?:d|m[trblxy]?|p[trblxy]?|col|offset|grid)-[a-z0-9-]+\b/g
  }
]);

export async function readJson(filePath) {
  return JSON.parse(await readFile(filePath, 'utf8'));
}

function hasRuntimeJavaScriptTarget(target) {
  if (typeof target === 'string') {
    return /\.(?:c?js|mjs)(?:$|\?)/.test(target);
  }

  if (Array.isArray(target)) {
    return target.some(hasRuntimeJavaScriptTarget);
  }

  if (target && typeof target === 'object') {
    return Object.entries(target).some(
      ([condition, value]) => condition !== 'types' && hasRuntimeJavaScriptTarget(value)
    );
  }

  return false;
}

export function exportKeyToEntrypoint(exportKey) {
  if (exportKey === '.') {
    return 'primeng';
  }

  if (!exportKey.startsWith('./')) {
    return null;
  }

  return `primeng/${exportKey.slice(2)}`;
}

export function classifyPackageExport(exportKey, target) {
  const entrypoint = exportKeyToEntrypoint(exportKey);

  if (!entrypoint) {
    return {
      entrypoint: null,
      exportKey,
      practical: false,
      reason: 'The export key is a condition, not a public package subpath.'
    };
  }

  if (exportKey.includes('*')) {
    return {
      entrypoint,
      exportKey,
      practical: false,
      reason: 'Wildcard export patterns are not independently inventoryable entrypoints.'
    };
  }

  if (exportKey === './package.json') {
    return {
      entrypoint,
      exportKey,
      practical: false,
      reason: 'Package metadata is not an application runtime entrypoint.'
    };
  }

  if (exportKey.startsWith('./types/')) {
    return {
      entrypoint,
      exportKey,
      practical: false,
      reason:
        'Generated type-secondary export; public for import type, but accounted for as a filtered family rather than a runtime component disposition.'
    };
  }

  if (!hasRuntimeJavaScriptTarget(target)) {
    return {
      entrypoint,
      exportKey,
      practical: false,
      reason: 'The export does not resolve to runtime JavaScript.'
    };
  }

  return {
    entrypoint,
    exportKey,
    practical: true,
    reason: 'Exact public consumer export with a runtime JavaScript target.'
  };
}

export function getPackageExportInventory(packageJson) {
  const exportsField = packageJson.exports;
  if (typeof exportsField === 'string' || Array.isArray(exportsField)) {
    return [classifyPackageExport('.', exportsField)];
  }

  if (!exportsField || typeof exportsField !== 'object') {
    return [];
  }

  const exportEntries = Object.entries(exportsField);
  const hasSubpathKeys = exportEntries.some(([key]) => key.startsWith('.'));

  if (!hasSubpathKeys) {
    return [classifyPackageExport('.', exportsField)];
  }

  return exportEntries
    .filter(([key]) => key.startsWith('.'))
    .map(([key, target]) => classifyPackageExport(key, target))
    .sort((left, right) => left.exportKey.localeCompare(right.exportKey));
}

function toPosixPath(path) {
  return path.split(sep).join('/');
}

function lineNumberAt(text, index) {
  let line = 1;
  for (let cursor = 0; cursor < index; cursor += 1) {
    if (text.charCodeAt(cursor) === 10) {
      line += 1;
    }
  }
  return line;
}

function addOccurrence(map, key, occurrence) {
  const current = map.get(key) ?? [];
  current.push(occurrence);
  map.set(key, current);
}

function occurrence(filePath, sourceRoot, text, index, match, extra = {}) {
  return {
    file: toPosixPath(relative(sourceRoot, filePath)),
    line: lineNumberAt(text, index),
    match,
    ...extra
  };
}

function addTemplateRegions(regions, text, filePath) {
  if (extname(filePath) === '.html') {
    regions.push({ text, offset: 0 });
    return;
  }

  if (extname(filePath) !== '.ts') {
    return;
  }

  const inlineTemplatePattern = /\btemplate\s*:\s*(`(?:\\[\s\S]|[^`])*`|'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")/g;
  for (const match of text.matchAll(inlineTemplatePattern)) {
    const literal = match[1];
    const literalOffset = match.index + match[0].indexOf(literal);
    regions.push({ text: literal.slice(1, -1), offset: literalOffset + 1 });
  }
}

function addStyleRegions(regions, text, filePath) {
  const extension = extname(filePath);
  if (extension === '.css' || extension === '.less' || extension === '.scss') {
    regions.push({ text, offset: 0 });
    return;
  }

  if (extension !== '.ts') {
    return;
  }

  const stylesArrayPattern = /\bstyles\s*:\s*\[([\s\S]*?)\](?=\s*[,}])/g;
  for (const arrayMatch of text.matchAll(stylesArrayPattern)) {
    const arrayText = arrayMatch[1];
    const arrayOffset = arrayMatch.index + arrayMatch[0].indexOf(arrayText);
    const literalPattern = /(`(?:\\[\s\S]|[^`])*`|'(?:\\.|[^'\\])*'|"(?:\\.|[^"\\])*")/g;
    for (const literalMatch of arrayText.matchAll(literalPattern)) {
      const literal = literalMatch[1];
      regions.push({
        text: literal.slice(1, -1),
        offset: arrayOffset + literalMatch.index + 1
      });
    }
  }
}

function scanHtmlOpeningTags(region, callback) {
  const tagPattern = /<\s*([A-Za-z][A-Za-z0-9-]*)\b([^<>]*?)>/g;
  for (const match of region.text.matchAll(tagPattern)) {
    callback({
      tagName: match[1],
      attributes: match[2],
      index: region.offset + match.index,
      match: match[0]
    });
  }
}

function extractImportedNames(clause) {
  const names = new Set();
  const normalized = clause.replace(/\btype\s+/g, ' ').trim();
  const namedMatch = normalized.match(/\{([\s\S]*?)\}/);

  if (namedMatch) {
    for (const item of namedMatch[1].split(',')) {
      const importedName = item.trim().split(/\s+as\s+/)[0]?.trim();
      if (importedName) {
        names.add(importedName);
      }
    }
  }

  const namespaceMatch = normalized.match(/\*\s+as\s+([A-Za-z_$][\w$]*)/);
  if (namespaceMatch) {
    names.add(`* as ${namespaceMatch[1]}`);
  }

  const beforeNamed = normalized.split(/[,{]/)[0]?.trim();
  if (beforeNamed && !beforeNamed.startsWith('*')) {
    names.add(beforeNamed);
  }

  return [...names].sort();
}

async function walkSourceFiles(directory, sourceRoot, files) {
  const entries = await readdir(directory, { withFileTypes: true });

  for (const entry of entries) {
    const absolutePath = resolve(directory, entry.name);
    const repositoryRelative = toPosixPath(relative(REPOSITORY_ROOT, absolutePath));

    if (entry.isDirectory()) {
      if (IGNORED_DIRECTORY_NAMES.has(entry.name)) {
        continue;
      }
      if (repositoryRelative === 'src/app/design-system/coverage') {
        continue;
      }
      await walkSourceFiles(absolutePath, sourceRoot, files);
      continue;
    }

    if (entry.isFile() && SOURCE_EXTENSIONS.has(extname(entry.name))) {
      files.push(absolutePath);
    }
  }
}

export async function collectSourceInventory({
  sourceRoot = resolve(REPOSITORY_ROOT, 'src')
} = {}) {
  const files = [];
  await walkSourceFiles(sourceRoot, sourceRoot, files);
  files.sort();

  const imports = new Map();
  const importedSymbols = new Map();
  const elementSelectors = new Map();
  const directiveCandidates = new Map();
  const classTokens = new Map();
  const legacyMarkers = new Map();

  for (const filePath of files) {
    const text = await readFile(filePath, 'utf8');
    const templateRegions = [];
    const styleRegions = [];
    addTemplateRegions(templateRegions, text, filePath);
    addStyleRegions(styleRegions, text, filePath);

    const staticImportPattern =
      /^\s*(?:import|export\s+(?=[{*]))\s*(?:type\s+)?((?:(?!^\s*(?:import|export)\b)[\s\S])*?)\s+from\s*(['"])(primeng(?:\/[^'"\s]+)?)\2\s*;?/gm;
    for (const match of text.matchAll(staticImportPattern)) {
      const specifier = match[3];
      const sourceOccurrence = occurrence(filePath, sourceRoot, text, match.index, match[0]);
      addOccurrence(imports, specifier, sourceOccurrence);
      const symbolSet = importedSymbols.get(specifier) ?? new Set();
      for (const symbol of extractImportedNames(match[1])) {
        symbolSet.add(symbol);
      }
      importedSymbols.set(specifier, symbolSet);
    }

    const directImportPatterns = [
      /\bimport\s*\(\s*(['"])(primeng(?:\/[^'"\s]+)?)\1\s*\)/g,
      /\brequire\s*\(\s*(['"])(primeng(?:\/[^'"\s]+)?)\1\s*\)/g,
      /(?:^|[;\r\n])\s*import\s*(['"])(primeng(?:\/[^'"\s]+)?)\1/g,
      /@(?:import|use)\s+(?:url\(\s*)?(['"]?)(primeng\/[^'"\s;)]+)\1/g
    ];

    for (const pattern of directImportPatterns) {
      for (const match of text.matchAll(pattern)) {
        const specifier = match[2];
        addOccurrence(
          imports,
          specifier,
          occurrence(filePath, sourceRoot, text, match.index, match[0])
        );
      }
    }

    for (const region of templateRegions) {
      scanHtmlOpeningTags(region, ({ tagName, attributes, index, match }) => {
        if (/^p-[A-Za-z][A-Za-z0-9-]*$/.test(tagName)) {
          addOccurrence(
            elementSelectors,
            tagName,
            occurrence(filePath, sourceRoot, text, index, match)
          );
        }

        for (const directiveMatch of attributes.matchAll(
          /(?:^|\s|\[|\()(?<name>p(?:InputText|InputTextarea|Ripple|SortableColumn|Template|Textarea|Tooltip))\b/g
        )) {
          const name = directiveMatch.groups.name;
          addOccurrence(
            directiveCandidates,
            name,
            occurrence(
              filePath,
              sourceRoot,
              text,
              index + match.indexOf(attributes) + directiveMatch.index,
              directiveMatch[0].trim()
            )
          );
        }

        const classAttributePattern =
          /(?:^|\s)(?:class|styleClass)\s*=\s*(['"])([\s\S]*?)\1/g;
        for (const attributeMatch of attributes.matchAll(classAttributePattern)) {
          for (const tokenMatch of attributeMatch[2].matchAll(
            /\bp-(?![0-9]+\b)[a-z][a-z0-9-]*\b/g
          )) {
            const attributeOffset = index + match.indexOf(attributes) + attributeMatch.index;
            const tokenOffset = attributeMatch[0].indexOf(attributeMatch[2]) + tokenMatch.index;
            addOccurrence(
              classTokens,
              tokenMatch[0],
              occurrence(
                filePath,
                sourceRoot,
                text,
                attributeOffset + tokenOffset,
                tokenMatch[0],
                { context: 'template-class' }
              )
            );
          }
        }
      });
    }

    for (const region of styleRegions) {
      for (const match of region.text.matchAll(/\.((?:p)-(?![0-9]+\b)[a-z][a-z0-9-]*)\b/g)) {
        addOccurrence(
          classTokens,
          match[1],
          occurrence(filePath, sourceRoot, text, region.offset + match.index, match[0], {
            context: 'style-selector'
          })
        );
      }
    }

    for (const rule of LEGACY_MARKER_RULES) {
      const regions = rule.styleContext
        ? styleRegions
        : rule.templateContext
          ? templateRegions
          : [{ text, offset: 0 }];
      for (const region of regions) {
        for (const match of region.text.matchAll(rule.pattern)) {
          addOccurrence(
            legacyMarkers,
            rule.id,
            {
              ...occurrence(
                filePath,
                sourceRoot,
                text,
                region.offset + match.index,
                match[0]
              ),
              description: rule.description
            }
          );
        }
      }
    }
  }

  return {
    sourceRoot,
    scannedFileCount: files.length,
    imports,
    importedSymbols,
    elementSelectors,
    directiveCandidates,
    classTokens,
    legacyMarkers
  };
}

export function mapToSerializable(map, { symbols } = {}) {
  return [...map.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([name, occurrences]) => ({
      name,
      count: occurrences.length,
      fileCount: new Set(occurrences.map((item) => item.file)).size,
      ...(symbols?.has(name) ? { symbols: [...symbols.get(name)].sort() } : {}),
      occurrences
    }));
}
