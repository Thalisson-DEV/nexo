#!/usr/bin/env node

/**
 * Guarda do padrão de listagem "tabela primeiro" (docs/design-system/components/list-page.md):
 *
 * 1. `app-crud-page` foi removido: nenhum arquivo o referencia.
 * 2. `TableModule` (primeng/table) em features/shared: listagens usam `ces-data-table`; só as
 *    exceções justificadas de list-pattern-exceptions.mjs (tabelas que não são listagens) o importam.
 * 3. Telas no padrão (com `ces-page-header`) não declaram hosts locais de toast/confirmação.
 */

import { readFile, readdir } from 'node:fs/promises';
import { dirname, extname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';
import { TABLE_MODULE_EXCEPTIONS } from './list-pattern-exceptions.mjs';

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const APP = resolve(ROOT, 'src', 'app');

async function* walk(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = resolve(dir, entry.name);
    if (entry.isDirectory()) yield* walk(full);
    else if (['.ts', '.html'].includes(extname(entry.name)) && !entry.name.endsWith('.spec.ts')) yield full;
  }
}

const toRepo = (file) => relative(ROOT, file).split(sep).join('/');
const errors = [];
const tableExceptions = new Set(Object.values(TABLE_MODULE_EXCEPTIONS).flat());
const tableUsers = new Set();
const sources = new Map();

for await (const file of walk(APP)) {
  sources.set(toRepo(file), await readFile(file, 'utf8'));
}

for (const [path, text] of sources) {
  if (/\bCrudPageComponent\b|<app-crud-page\b/.test(text)) {
    errors.push(
      `${path}: app-crud-page foi removido. Use o padrão de listagem (ces-page-header + ces-filter-panel + ces-data-table), ver docs/design-system/components/list-page.md.`
    );
  }

  const inFeature = path.startsWith('src/app/features/') || path.startsWith('src/app/shared/');
  if (inFeature && /from\s+['"]primeng\/table['"]/.test(text)) {
    tableUsers.add(path);
    if (!tableExceptions.has(path)) {
      errors.push(
        `${path}: listagens usam ces-data-table (modo servidor para dados paginados), não TableModule. Ver docs/design-system/prime-ng-coverage.md (table).`
      );
    }
  }
}

// Telas no padrão: sem hosts locais de feedback/confirmação (no .ts e no .html irmão).
for (const [path, text] of sources) {
  if (!path.endsWith('.ts')) continue;
  const html = sources.get(path.replace(/\.ts$/, '.html')) ?? '';
  const markup = text + html;
  if (!/<ces-page-header\b/.test(markup)) continue;
  if (/<p-toast\b/i.test(markup) || /<p-confirm(?:dialog|popup)\b/i.test(markup)) {
    errors.push(`${path}: tela no padrão de listagem não declara p-toast/p-confirmDialog; use os hosts globais.`);
  }
  if (/providers\s*:\s*\[[^\]]*\b(MessageService|ConfirmationService)\b/.test(text)) {
    errors.push(`${path}: tela no padrão de listagem não provê MessageService/ConfirmationService locais.`);
  }
}

for (const path of tableExceptions) {
  if (!tableUsers.has(path)) {
    errors.push(`${path} não importa mais TableModule: remova-o de TABLE_MODULE_EXCEPTIONS em tools/design-system/list-pattern-exceptions.mjs.`);
  }
}

console.log('Padrão de listagem (tabela primeiro)');
console.log(`TableModule em exceções justificadas: ${tableUsers.size} arquivo(s)`);

if (errors.length) {
  console.error(`\nVerificação falhou com ${errors.length} erro(s):`);
  for (const error of errors) console.error(`- ${error}`);
  process.exitCode = 1;
} else {
  console.log('\nVerificação passou.');
}
