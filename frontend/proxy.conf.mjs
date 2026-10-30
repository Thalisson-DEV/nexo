// Desenvolvimento: o app também responde pelo backend em /api e /.well-known, como o nginx faz em
// produção. É o que deixa uma IA externa (Claude, ChatGPT, Cursor...) conectar pelo endereço do app.
// O destino vem do apiUrl de environment.development.ts.
import { readFileSync } from 'node:fs';

const ambiente = readFileSync(new URL('./src/environments/environment.development.ts', import.meta.url), 'utf8');
const apiUrl = /apiUrl:\s*'([^']+)'/.exec(ambiente)?.[1] ?? 'http://localhost:8080/api';
const backend = new URL(apiUrl).origin;

export default {
  '/api': { target: backend, secure: false, xfwd: true },
  '/.well-known': {
    target: backend,
    secure: false,
    xfwd: true,
    pathRewrite: { '^/\\.well-known': '/api/.well-known' },
  },
};
