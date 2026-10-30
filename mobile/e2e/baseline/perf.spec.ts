import { test, expect, Page, CDPSession } from '@playwright/test';
import { writeFileSync, mkdirSync } from 'node:fs';
import { MockApi } from '../support/mock-api';

/**
 * Baseline de desempenho e de abertura offline da UI ATUAL.
 * Só Chromium (CDP para limitar rede/CPU). Resultado em docs/mobile/baseline/perf.json.
 */

test.skip(({ browserName }) => browserName !== 'chromium', 'métricas via CDP só no Chromium');
test.use({ serviceWorkers: 'allow' });
test.describe.configure({ mode: 'serial' });

const resultados: Record<string, unknown> = {};

async function limitar(cdp: CDPSession) {
  // "Slow 4G" do Lighthouse: 150 ms RTT, ~1,6 Mbps down, 750 Kbps up; CPU 4x.
  await cdp.send('Network.enable');
  await cdp.send('Network.emulateNetworkConditions', {
    offline: false, latency: 150, downloadThroughput: (1.6 * 1024 * 1024) / 8, uploadThroughput: (750 * 1024) / 8,
  });
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
}

async function lcp(page: Page): Promise<number> {
  return page.evaluate(() => new Promise<number>(resolve => {
    let ultimo = 0;
    new PerformanceObserver(list => {
      for (const e of list.getEntries()) ultimo = e.startTime;
    }).observe({ type: 'largest-contentful-paint', buffered: true });
    setTimeout(() => resolve(ultimo), 500);
  }));
}

test('abertura a frio do login em celular intermediário (Slow 4G, CPU 4x)', async ({ page, context }) => {
  await new MockApi().install(context);
  const cdp = await context.newCDPSession(page);
  await limitar(cdp);

  const inicio = Date.now();
  await page.goto('/login', { waitUntil: 'load' });
  const load = Date.now() - inicio;
  await expect(page.locator('#login')).toBeVisible({ timeout: 30_000 });
  const interativo = Date.now() - inicio;
  const nav = await page.evaluate(() => {
    const n = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;
    return { domContentLoaded: Math.round(n.domContentLoadedEventEnd), transferKb: Math.round(n.transferSize / 1024) };
  });
  // Todo .js baixado (script e modulepreload), em bytes comprimidos na rede.
  const recursos = await page.evaluate(() =>
    Math.round(performance.getEntriesByType('resource')
      .filter(r => new URL(r.name).pathname.endsWith('.js'))
      .reduce((s, r) => s + (r as PerformanceResourceTiming).transferSize, 0) / 1024));

  resultados['frio_slow4g'] = {
    loadMs: load, campoLoginVisivelMs: interativo, lcpMs: Math.round(await lcp(page)),
    ...nav, jsTransferidoKb: recursos,
    // A UI antiga tinha uma splash de ~3 s por cima de tudo; a nova não tem.
    splashBloqueiaMs: process.env['CES_BASELINE'] ? 3000 : 0,
  };
});

test('abertura offline a partir do cache do service worker', async ({ page, context }) => {
  await new MockApi().install(context);
  await page.goto('/login');
  await page.evaluate(() => navigator.serviceWorker.ready);
  // Espera o ngsw terminar o prefetch do app shell.
  await page.waitForFunction(async () => (await caches.keys()).some(k => k.includes('ngsw')), null, { timeout: 30_000 });
  await page.waitForTimeout(3000);

  await context.setOffline(true);
  const inicio = Date.now();
  await page.reload();
  await expect(page.locator('#login')).toBeVisible({ timeout: 15_000 });
  const ms = Date.now() - inicio;

  // A fonte de ícones das telas antigas precisa estar no cache do SW para abrir offline.
  // (A UI antiga a buscava no Google Fonts, fora do alcance do SW.)
  const fonteNoCache = await page.evaluate(async () => {
    for (const nome of await caches.keys()) {
      const chaves = await (await caches.open(nome)).keys();
      if (chaves.some(r => /material-icons|fonts\.gstatic/.test(r.url))) return true;
    }
    return false;
  });
  resultados['offline_cache'] = { loginVisivelMs: ms, fonteIconesNoCacheOffline: fonteNoCache };
});

test.afterAll(() => {
  // CES_BASELINE=1 regrava a baseline (build da base); sem ela, mede o build atual.
  const dir = process.env['CES_BASELINE'] ? '../docs/mobile/baseline' : '../docs/mobile/piloto';
  mkdirSync(dir, { recursive: true });
  writeFileSync(`${dir}/perf.json`, JSON.stringify({ medidoEm: new Date().toISOString(), ...resultados }, null, 2));
});
