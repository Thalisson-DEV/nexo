import http from 'node:http';
import { randomUUID } from 'node:crypto';
import Busboy from 'busboy';
import { Sandbox } from 'railway';

const port = Number(process.env.PORT || 8000);
const maxFileBytes = 100 * 1024 * 1024;
const maxBodyBytes = 1024 * 1024;
const staged = new Map();
const fileTtlMs = 60 * 60 * 1000;
const isolatedPython = "if [ \"$(id -u)\" -eq 0 ]; then exec unshare --net -- setpriv --bounding-set=-all --reuid=nobody --regid=nogroup --clear-groups /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin MPLCONFIGDIR=/tmp /opt/ces-python/bin/python -B $1; else exec sudo -n unshare --net -- setpriv --bounding-set=-all --reuid=nobody --regid=nogroup --clear-groups /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin MPLCONFIGDIR=/tmp /opt/ces-python/bin/python -B $1; fi";
const pythonTemplate = Sandbox.template()
  .withPackages('python3', 'python3-venv', 'util-linux')
  .run('/usr/bin/python3 -m venv /opt/ces-python && /opt/ces-python/bin/pip install --no-cache-dir numpy pandas scipy matplotlib openpyxl pillow');
let templateReady;
let operational = false;
let readinessError = 'Aguardando inicialização';

function readyTemplate() {
  if (!templateReady) templateReady = pythonTemplate.build();
  return templateReady;
}

async function warmup() {
  if (!process.env.RAILWAY_TOKEN || !process.env.RAILWAY_ENVIRONMENT_ID) {
    readinessError = 'RAILWAY_TOKEN ou RAILWAY_ENVIRONMENT_ID ausente';
    return;
  }
  let sandbox;
  try {
    await readyTemplate();
    sandbox = await Sandbox.create(pythonTemplate, { networkIsolation: 'ISOLATED', idleTimeoutMinutes: 5 });
    const probe = await sandbox.exec("if [ \"$(id -u)\" -eq 0 ]; then unshare --net -- setpriv --bounding-set=-all --reuid=nobody --regid=nogroup --clear-groups /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin MPLCONFIGDIR=/tmp /opt/ces-python/bin/python -c 'import os, numpy, pandas, scipy, matplotlib, openpyxl, PIL; assert os.getuid() != 0'; else sudo -n unshare --net -- setpriv --bounding-set=-all --reuid=nobody --regid=nogroup --clear-groups /usr/bin/env -i HOME=/tmp PATH=/usr/bin:/bin MPLCONFIGDIR=/tmp /opt/ces-python/bin/python -c 'import os, numpy, pandas, scipy, matplotlib, openpyxl, PIL; assert os.getuid() != 0'; fi", { timeoutSec: 20 });
    if (probe.exitCode !== 0) throw new Error(`Isolamento Python indisponível: ${probe.stderr.slice(0, 300)}`);
    operational = true;
    readinessError = '';
  } catch (error) {
    readinessError = error.message;
    console.error('Falha ao inicializar o interpretador:', readinessError);
  } finally {
    if (sandbox) await sandbox.destroy().catch(error => console.error('Falha ao limpar VM de verificação:', error));
  }
}

function json(res, status, body) {
  res.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(body));
}

function boundedBody(req, limit = maxBodyBytes) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', chunk => {
      size += chunk.length;
      if (size > limit) {
        reject(new Error('Request muito grande'));
        req.destroy();
      } else chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

function receiveFile(req) {
  return new Promise((resolve, reject) => {
    const parser = Busboy({ headers: req.headers, limits: { files: 1, fileSize: maxFileBytes } });
    let bytes;
    parser.on('file', (_name, stream) => {
      const chunks = [];
      stream.on('data', chunk => chunks.push(chunk));
      stream.on('limit', () => reject(new Error('Arquivo muito grande')));
      stream.on('end', () => { bytes = Buffer.concat(chunks); });
    });
    parser.on('finish', () => bytes ? resolve(bytes) : reject(new Error('Arquivo ausente')));
    parser.on('error', reject);
    req.pipe(parser);
  });
}

function validPath(path) {
  if (typeof path !== 'string' || !path || path.length > 200 || path.startsWith('/') ||
      path.includes('\\') || path.split('/').some(part => part === '..' || part === '' || part === '.')) {
    throw new Error('Caminho de arquivo inválido');
  }
  return path;
}

function cleanupStaged() {
  const now = Date.now();
  for (const [id, file] of staged) if (file.expiresAt <= now) staged.delete(id);
}
setInterval(cleanupStaged, 60_000).unref();

async function execute(payload, emit) {
  if (typeof payload.code !== 'string' || payload.code.length > 200_000) throw new Error('Código inválido');
  const timeoutMs = Math.min(Math.max(Number(payload.timeout_ms) || 60_000, 1000), 60_000);
  const inputFiles = Array.isArray(payload.files) ? payload.files : [];
  if (inputFiles.length > 25) throw new Error('Arquivos demais');
  const begin = Date.now();
  let sandbox;
  try {
    if (!operational) throw new Error(readinessError);
    sandbox = await Sandbox.create(pythonTemplate, { networkIsolation: 'ISOLATED', idleTimeoutMinutes: 5 });
    const setup = await sandbox.exec('command -v /opt/ces-python/bin/python && command -v unshare && command -v setpriv && id nobody', { timeoutSec: 10 });
    if (setup.exitCode !== 0) throw new Error('VM sem ferramentas de isolamento');
    await sandbox.exec('mkdir -p /tmp/ces-work && chown nobody:nogroup /tmp/ces-work');
    await sandbox.files.write('/tmp/ces-work/script.py', payload.code);
    for (const file of inputFiles) {
      const path = validPath(file.path);
      const source = staged.get(file.file_id);
      if (!source || source.expiresAt <= Date.now()) throw new Error('Arquivo não encontrado');
      await sandbox.files.write(`/tmp/ces-work/${path}`, source.bytes);
    }
    const prepare = await sandbox.exec('chown -R nobody:nogroup /tmp/ces-work');
    if (prepare.exitCode !== 0) throw new Error('Falha ao preparar arquivos');
    const result = await sandbox.exec(`sh -c '${isolatedPython}' -- script.py`, {
      cwd: '/tmp/ces-work', timeoutSec: Math.ceil(timeoutMs / 1000),
      onStdout: chunk => emit('output', { stream: 'stdout', data: chunk }),
      onStderr: chunk => emit('output', { stream: 'stderr', data: chunk }),
    });
    const files = [];
    const index = await sandbox.exec("python3 -c 'import os,json; print(json.dumps([(e.name,e.stat(follow_symlinks=False).st_size) for e in os.scandir(\"/tmp/ces-work\") if e.is_file(follow_symlinks=False)]))'", { timeoutSec: 10 });
    if (index.exitCode !== 0) throw new Error('Falha ao listar arquivos gerados');
    for (const [name, size] of JSON.parse(index.stdout)) {
      if (name === 'script.py' || size > 25 * 1024 * 1024) continue;
      const bytes = await sandbox.files.read(`/tmp/ces-work/${name}`, { format: 'bytes' });
      const id = randomUUID();
      staged.set(id, { bytes: Buffer.from(bytes), expiresAt: Date.now() + fileTtlMs });
      files.push({ path: name, kind: 'file', file_id: id });
      if (files.length >= 20) break;
    }
    return { stdout: result.stdout, stderr: result.stderr, exit_code: result.exitCode,
      timed_out: result.timedOut, duration_ms: Date.now() - begin, files };
  } finally {
    if (sandbox) await sandbox.destroy().catch(error => console.error('Falha ao limpar VM de execução:', error));
  }
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') {
      return json(res, operational ? 200 : 503,
        { status: operational ? 'ok' : 'unavailable', message: readinessError, version: 'railway-sandbox-1' });
    }
    if (req.method === 'POST' && req.url === '/v1/files') {
      const bytes = await receiveFile(req);
      const id = randomUUID();
      staged.set(id, { bytes, expiresAt: Date.now() + fileTtlMs });
      return json(res, 200, { file_id: id });
    }
    if (req.url?.startsWith('/v1/files/')) {
      const id = decodeURIComponent(req.url.slice('/v1/files/'.length));
      const file = staged.get(id);
      if (!file) return json(res, 404, { error: 'Arquivo não encontrado' });
      if (req.method === 'DELETE') { staged.delete(id); return json(res, 200, { deleted: true }); }
      if (req.method === 'GET') { res.writeHead(200, { 'content-type': 'application/octet-stream' }); return res.end(file.bytes); }
    }
    if (req.method === 'POST' && (req.url === '/v1/execute' || req.url === '/v1/execute/stream')) {
      const payload = JSON.parse((await boundedBody(req)).toString('utf8'));
      const streaming = req.url.endsWith('/stream');
      if (streaming) res.writeHead(200, { 'content-type': 'text/event-stream', 'cache-control': 'no-cache' });
      const emit = (event, data) => { if (streaming && !res.destroyed) res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`); };
      try {
        const result = await execute(payload, emit);
        return streaming ? (emit('result', result), res.end()) : json(res, 200, result);
      } catch (error) {
        return streaming ? (emit('error', { message: error.message }), res.end()) : json(res, 500, { error: error.message });
      }
    }
    json(res, 404, { error: 'Não encontrado' });
  } catch (error) {
    if (!res.headersSent) json(res, 400, { error: error.message });
    else res.end();
  }
});
server.listen(port, '0.0.0.0');
warmup();
