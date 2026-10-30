#!/usr/bin/env node

import { pathToFileURL } from 'node:url';
import {
  COVERAGE_REGISTRY_PATH,
  DISPOSITIONS,
  PRACTICAL_ENTRYPOINT_POLICY,
  PRIMENG_PACKAGE_JSON,
  collectSourceInventory,
  getPackageExportInventory,
  readJson
} from './primeng-coverage-lib.mjs';

const errors = [];
const warnings = [];
const packageJson = await readJson(PRIMENG_PACKAGE_JSON);
const registryModule = await import(`${pathToFileURL(COVERAGE_REGISTRY_PATH).href}?t=${Date.now()}`);
const registry = registryModule.primengCoverageRegistry;

if (!registry || typeof registry !== 'object') {
  errors.push('Registry must export a primengCoverageRegistry object.');
}

if (registry?.schemaVersion !== 1) {
  errors.push(`Unsupported registry schemaVersion ${JSON.stringify(registry?.schemaVersion)}; expected 1.`);
}

if (typeof registry?.primeNgVersion !== 'string' || registry.primeNgVersion.length === 0) {
  errors.push('Registry primeNgVersion must be a non-empty string.');
} else if (registry.primeNgVersion !== packageJson.version) {
  errors.push(
    `Installed PrimeNG version is ${packageJson.version}, but registry targets ${registry.primeNgVersion}.`
  );
}

if (
  typeof registry?.practicalEntrypointDefinition !== 'string' ||
  registry.practicalEntrypointDefinition.trim().length < 40
) {
  errors.push('Registry practicalEntrypointDefinition must transparently explain the filtering rule.');
}

if (registry?.practicalEntrypointPolicy !== PRACTICAL_ENTRYPOINT_POLICY.id) {
  errors.push(
    `Registry practicalEntrypointPolicy must be ${PRACTICAL_ENTRYPOINT_POLICY.id}; received ${JSON.stringify(registry?.practicalEntrypointPolicy)}.`
  );
}

if (!Array.isArray(registry?.entries)) {
  errors.push('Registry entries must be an array.');
}

const entries = Array.isArray(registry?.entries) ? registry.entries : [];
const entrypointCounts = new Map();
const allowedEntryFields = new Set(['entrypoint', 'disposition', 'rationale']);

for (const [index, entry] of entries.entries()) {
  const label = `entries[${index}]`;
  if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
    errors.push(`${label} must be an object.`);
    continue;
  }

  for (const field of ['entrypoint', 'disposition', 'rationale']) {
    if (typeof entry[field] !== 'string' || entry[field].trim().length === 0) {
      errors.push(`${label}.${field} must be a non-empty string.`);
    }
  }

  for (const field of Object.keys(entry)) {
    if (!allowedEntryFields.has(field)) {
      errors.push(`${label}.${field} is not part of the coverage-entry schema.`);
    }
  }

  if (typeof entry.entrypoint === 'string') {
    if (entry.entrypoint !== 'primeng' && !/^primeng\/[a-z0-9-]+(?:\/[a-z0-9-]+)*$/.test(entry.entrypoint)) {
      errors.push(`${label}.entrypoint is not a canonical exact PrimeNG entrypoint.`);
    }
    entrypointCounts.set(entry.entrypoint, (entrypointCounts.get(entry.entrypoint) ?? 0) + 1);
  }

  if (!DISPOSITIONS.includes(entry.disposition)) {
    errors.push(
      `${label}.disposition must be one of ${DISPOSITIONS.join(', ')}; received ${JSON.stringify(entry.disposition)}.`
    );
  }

  if (entry.disposition === 'excluded' && entry.rationale?.trim().length < 12) {
    errors.push(`${label}.rationale must explain why the entrypoint is excluded.`);
  }
}

for (const [entrypoint, count] of entrypointCounts) {
  if (count > 1) {
    errors.push(`Duplicate registry entrypoint ${entrypoint} appears ${count} times.`);
  }
}

const packageExportInventory = getPackageExportInventory(packageJson);
const practicalExports = packageExportInventory.filter((entry) => entry.practical);
const practicalEntrypoints = new Set(practicalExports.map((entry) => entry.entrypoint));
const registryEntrypoints = new Set(entries.map((entry) => entry.entrypoint));

for (const entrypoint of [...practicalEntrypoints].sort()) {
  if (!registryEntrypoints.has(entrypoint)) {
    errors.push(`Practical installed export ${entrypoint} has no registry disposition.`);
  }
}

for (const entrypoint of [...registryEntrypoints].sort()) {
  if (!practicalEntrypoints.has(entrypoint)) {
    errors.push(`Registry entry ${entrypoint} is not a practical export of installed PrimeNG.`);
  }
}

const sourceInventory = await collectSourceInventory();
const usedEntrypoints = [...sourceInventory.imports.keys()].sort();
for (const entrypoint of usedEntrypoints) {
  if (!registryEntrypoints.has(entrypoint)) {
    errors.push(`Currently imported PrimeNG entrypoint ${entrypoint} is absent from the registry.`);
  }

  if (!practicalEntrypoints.has(entrypoint)) {
    errors.push(
      `Currently imported PrimeNG entrypoint ${entrypoint} is not covered by the practical export policy.`
    );
  }
}

const packageExportKeys = new Set(packageExportInventory.map((entry) => entry.exportKey));
const documentedFilteredExportKeys = new Set();
const filteredExportPatternCounts = new Map();
for (const filteredExport of registry?.filteredPackageExports ?? []) {
  if (
    !filteredExport ||
    typeof filteredExport.exportKey !== 'string' ||
    typeof filteredExport.rationale !== 'string' ||
    filteredExport.rationale.trim().length === 0
  ) {
    errors.push('Each filteredPackageExports item must include exportKey and rationale strings.');
    continue;
  }

  filteredExportPatternCounts.set(
    filteredExport.exportKey,
    (filteredExportPatternCounts.get(filteredExport.exportKey) ?? 0) + 1
  );

  const matchingKeys = filteredExport.exportKey.endsWith('/*')
    ? [...packageExportKeys].filter((key) =>
        key.startsWith(filteredExport.exportKey.slice(0, -1))
      )
    : packageExportKeys.has(filteredExport.exportKey)
      ? [filteredExport.exportKey]
      : [];

  if (matchingKeys.length === 0) {
    warnings.push(
      `Documented filtered export ${filteredExport.exportKey} does not match installed package exports.`
    );
  }

  for (const matchingKey of matchingKeys) {
    documentedFilteredExportKeys.add(matchingKey);
  }
}

for (const [exportKey, count] of filteredExportPatternCounts) {
  if (count > 1) {
    errors.push(`Duplicate filteredPackageExports key ${exportKey} appears ${count} times.`);
  }
}

for (const filteredExport of packageExportInventory.filter((entry) => !entry.practical)) {
  if (!documentedFilteredExportKeys.has(filteredExport.exportKey)) {
    errors.push(
      `Filtered installed export ${filteredExport.exportKey} is not covered by filteredPackageExports rationale.`
    );
  }
}

const dispositionCounts = Object.fromEntries(
  DISPOSITIONS.map((disposition) => [
    disposition,
    entries.filter((entry) => entry.disposition === disposition).length
  ])
);

console.log(`PrimeNG coverage validation for ${packageJson.version}`);
console.log(`Policy: ${PRACTICAL_ENTRYPOINT_POLICY.id}`);
console.log(`Practical installed entrypoints: ${practicalEntrypoints.size}`);
console.log(`Registry entries: ${registryEntrypoints.size}`);
console.log(`Currently imported entrypoints: ${usedEntrypoints.length}`);
console.log(
  `Dispositions: ${DISPOSITIONS.map((name) => `${name}=${dispositionCounts[name]}`).join(', ')}`
);

for (const warning of warnings) {
  console.warn(`WARN ${warning}`);
}

if (errors.length > 0) {
  console.error(`\nValidation failed with ${errors.length} error(s):`);
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exitCode = 1;
} else {
  console.log('\nValidation passed.');
}
