#!/usr/bin/env node

import { resolve } from 'node:path';
import {
  PRIMENG_PACKAGE_JSON,
  REPOSITORY_ROOT,
  collectSourceInventory,
  getPackageExportInventory,
  mapToSerializable,
  readJson
} from './primeng-coverage-lib.mjs';

const flags = new Set(process.argv.slice(2));
const packageJson = await readJson(PRIMENG_PACKAGE_JSON);
const sourceInventory = await collectSourceInventory({
  sourceRoot: resolve(REPOSITORY_ROOT, 'src')
});
const packageExports = getPackageExportInventory(packageJson);

const report = {
  generatedAt: new Date().toISOString(),
  primeNgVersion: packageJson.version,
  scannedRoot: 'src',
  scannedFileCount: sourceInventory.scannedFileCount,
  scanPolicy: {
    imports:
      'Literal static, dynamic, require, side-effect, and CSS/Sass PrimeNG specifiers are detected; non-literal computed specifiers are not guessed.',
    usages:
      'PrimeNG elements, known attribute directives, literal class/styleClass tokens, external styles, and Angular inline template/styles literals are inventoried heuristically without executing the application.',
    exclusions:
      'Generated/build/dependency directories and src/app/design-system/coverage are excluded to avoid self-reporting.'
  },
  imports: mapToSerializable(sourceInventory.imports, {
    symbols: sourceInventory.importedSymbols
  }),
  usages: {
    elementSelectors: mapToSerializable(sourceInventory.elementSelectors),
    directiveCandidates: mapToSerializable(sourceInventory.directiveCandidates),
    classTokens: mapToSerializable(sourceInventory.classTokens)
  },
  legacyMarkers: mapToSerializable(sourceInventory.legacyMarkers),
  packageExports: {
    practical: packageExports.filter((entry) => entry.practical),
    filtered: packageExports.filter((entry) => !entry.practical)
  }
};

if (flags.has('--json')) {
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
  process.exit(0);
}

const printNamedCounts = (title, entries) => {
  console.log(`\n${title} (${entries.length})`);
  if (entries.length === 0) {
    console.log('  none');
    return;
  }

  for (const entry of entries) {
    const symbols = entry.symbols?.length ? ` [${entry.symbols.join(', ')}]` : '';
    console.log(`  ${entry.name}: ${entry.count} in ${entry.fileCount} file(s)${symbols}`);
  }
};

console.log(`PrimeNG ${report.primeNgVersion} inventory`);
console.log(`Scanned ${report.scannedFileCount} source files under ${report.scannedRoot}/.`);
console.log(
  `Installed exports: ${report.packageExports.practical.length} practical, ${report.packageExports.filtered.length} filtered by policy.`
);

printNamedCounts('PrimeNG imports', report.imports);
printNamedCounts('PrimeNG element selectors', report.usages.elementSelectors);
printNamedCounts('PrimeNG directive candidates', report.usages.directiveCandidates);
printNamedCounts('PrimeNG/PrimeFlex class tokens', report.usages.classTokens);
printNamedCounts('Legacy migration markers', report.legacyMarkers);

if (flags.has('--details')) {
  console.log('\nDetailed occurrences');
  for (const section of [
    ...report.imports,
    ...report.usages.elementSelectors,
    ...report.usages.directiveCandidates,
    ...report.usages.classTokens,
    ...report.legacyMarkers
  ]) {
    console.log(`\n${section.name}`);
    for (const item of section.occurrences) {
      console.log(`  ${item.file}:${item.line} ${item.match}`);
    }
  }
}
