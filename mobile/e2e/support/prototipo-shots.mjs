// Gera as imagens do protótipo da Fase 1 (claro e escuro) a partir de docs/mobile/prototipo/proposta.html.
// Uso: node e2e/support/prototipo-shots.mjs
import { chromium } from '@playwright/test';
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';

const src = fileURLToPath(new URL('../../../docs/mobile/prototipo/proposta.html', import.meta.url));
const out = fileURLToPath(new URL('../../../docs/mobile/prototipo/screens/', import.meta.url));
mkdirSync(out, { recursive: true });

// O Artifact envolve a página num esqueleto; aqui fazemos o mesmo para renderizar localmente.
const tmp = out + '_render.html';
writeFileSync(tmp, `<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover"></head><body style="margin:0">${readFileSync(src, 'utf8')}</body></html>`);

const browser = await chromium.launch();
try {
  for (const scheme of ['light', 'dark']) {
    const page = await browser.newPage({ viewport: { width: 1100, height: 900 }, colorScheme: scheme, deviceScaleFactor: 2 });
    await page.goto(pathToFileURL(tmp).href);
    for (const tela of ['instalar', 'hoje', 'lancar', 'fila', 'retomada']) {
      await page.click(`[data-go="${tela}"]`);
      if (tela === 'lancar') {
        await page.click('[data-act="avancar"]');
        await page.click('[data-act="avancar"]');
      }
      await page.waitForTimeout(450);
      await page.locator('.device').screenshot({ path: `${out}${tela}-${scheme}.png` });
    }
    await page.close();
  }
} finally {
  await browser.close();
  rmSync(tmp, { force: true });
}
