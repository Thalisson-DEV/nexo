import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const config = await readFile(new URL('../nginx.conf', import.meta.url), 'utf8');

test('o proxy da API aceita o limite de 15 MB dos anexos com folga para multipart', () => {
  const apiLocation = config.match(/location \^~ \/api\/ \{([\s\S]*?)\n    \}/)?.[1] ?? '';
  const directive = apiLocation.match(/client_max_body_size\s+(\d+)m\s*;/);

  assert.ok(directive, 'location /api/ precisa declarar client_max_body_size em MB');
  assert.ok(Number(directive[1]) >= 16, 'multipart de arquivo com 15 MB exige ao menos 16 MB');
});
