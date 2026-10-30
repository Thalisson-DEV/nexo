// Gera dist/versao-2: cópia do build atual com o main-*.js alterado e o ngsw.json
// com o hash recalculado. Para o service worker é uma versão nova de verdade, o que
// permite testar a atualização no meio de um fluxo e o rollback (voltar para a v1).
import { cpSync, readFileSync, writeFileSync, readdirSync, rmSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { join } from 'node:path';

export const MARCA_V2 = '/* ces-versao-2 */';

export function gerarVersao2(): string {
  // Playwright roda a partir de mobile/.
  const dist = join(process.cwd(), 'dist');
  const origem = join(dist, 'ces-field-app-tmp/browser');
  const destino = join(dist, 'versao-2/browser');
  rmSync(join(dist, 'versao-2'), { recursive: true, force: true });
  cpSync(origem, destino, { recursive: true });

  const main = readdirSync(destino).find((f: string) => /^main-.*\.js$/.test(f));
  if (!main) throw new Error('main-*.js não encontrado no build');
  const caminho = join(destino, main);
  const conteudo = readFileSync(caminho, 'utf8') + `\n${MARCA_V2}\n`;
  writeFileSync(caminho, conteudo);

  const manifesto = JSON.parse(readFileSync(join(destino, 'ngsw.json'), 'utf8'));
  manifesto.hashTable[`/${main}`] = createHash('sha1').update(conteudo).digest('hex');
  manifesto.timestamp = Date.now();
  writeFileSync(join(destino, 'ngsw.json'), JSON.stringify(manifesto, null, 2));
  return `/${main}`;
}
