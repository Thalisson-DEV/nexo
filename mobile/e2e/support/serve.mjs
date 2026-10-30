// Servidor estático mínimo para os testes E2E: serve o build de produção com
// fallback de SPA (igual ao nginx.conf) e responde 503 em /api quando nenhum
// mock do Playwright interceptou a chamada — nunca toca o backend real.
import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';
import { gzipSync } from 'node:zlib';

const dist = fileURLToPath(new URL('../../dist/', import.meta.url));
// Versão servida. Os testes de atualização trocam com GET /__versao/<v2|v1> (só neste servidor de teste).
let root = join(dist, 'ces-field-app-tmp/browser/');
const versoes = { v1: join(dist, 'ces-field-app-tmp/browser/'), v2: join(dist, 'versao-2/browser/') };
const port = Number(process.env.PORT || 4300);

const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json',
  '.webmanifest': 'application/manifest+json',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
};

async function send(req, res, file) {
  let body = await readFile(file);
  // Igual ao nginx de produção (gzip_comp_level 6), para medir transferência real.
  const gzip = /\bgzip\b/.test(req.headers['accept-encoding'] ?? '') && /\.(js|css|html|json|svg|webmanifest)$/.test(file);
  if (gzip) body = gzipSync(body, { level: 6 });
  const noCache = file.endsWith('index.html') || file.endsWith('ngsw.json') || file.endsWith('ngsw-worker.js');
  res.writeHead(200, {
    'Content-Type': types[extname(file)] ?? 'application/octet-stream',
    'Cache-Control': noCache ? 'no-cache, no-store, must-revalidate' : 'public, max-age=31536000, immutable',
    ...(gzip ? { 'Content-Encoding': 'gzip', Vary: 'Accept-Encoding' } : {}),
  });
  res.end(body);
}

createServer(async (req, res) => {
  const url = new URL(req.url ?? '/', 'http://localhost');
  const troca = url.pathname.match(/^\/__versao\/(v1|v2)$/);
  if (troca) {
    root = versoes[troca[1]];
    res.writeHead(204).end();
    return;
  }
  if (url.pathname.startsWith('/api/')) {
    res.writeHead(503, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ title: 'Sem mock', detail: `Sem mock para ${req.method} ${url.pathname}` }));
    return;
  }
  const file = normalize(join(root, decodeURIComponent(url.pathname)));
  if (!file.startsWith(normalize(root))) {
    res.writeHead(403).end();
    return;
  }
  try {
    if ((await stat(file)).isFile()) return await send(req, res, file);
  } catch {
    /* cai no fallback */
  }
  if (extname(url.pathname)) {
    res.writeHead(404).end();
    return;
  }
  await send(req, res, join(root, 'index.html'));
}).listen(port, () => console.log(`e2e server em http://localhost:${port}`));
