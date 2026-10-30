import { test, expect, Page } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';
import { gerarVersao2, MARCA_V2 } from '../support/gerar-versao-2';
import { environment } from '../../src/environments/environment';

/**
 * Atualização do app (nova versão do service worker) no meio de um lançamento,
 * e rollback para a versão anterior — com o Angular Service Worker de verdade.
 *
 * Garantias:
 * - a versão nova NÃO recarrega a tela enquanto o usuário trabalha;
 * - ela entra quando o app vai para segundo plano, e a tela volta igual;
 * - voltar para a versão anterior (redeploy) também preserva tudo, e o banco
 *   principal continua na versão do build antigo (que consegue abri-lo).
 */

test.skip(({ browserName }) => browserName !== 'chromium', 'interceptação de requisições do SW só no Chromium');
// Servidor próprio (ver playwright.config.ts) e um teste por vez: a versão servida é estado global.
test.describe.configure({ mode: 'serial' });
test.use({
  baseURL: 'http://localhost:4301',
  serviceWorkers: 'allow',
  geolocation: { latitude: -3.7319, longitude: -38.5267, accuracy: 12 },
  permissions: ['geolocation'],
});

async function eventosAtualizacao(page: Page): Promise<string[]> {
  return page.evaluate(() => new Promise<string[]>(resolve => {
    const req = indexedDB.open('ces-field-sessao');
    req.onsuccess = () => {
      const tudo = req.result.transaction('eventos').objectStore('eventos').getAll();
      tudo.onsuccess = () => resolve((tudo.result as { tipo: string; dados?: { etapa?: string } }[])
        .filter(e => e.tipo === 'sw.atualizacao').map(e => e.dados?.etapa ?? ''));
    };
  }));
}

async function procurarAtualizacao(page: Page) {
  await page.evaluate(() => navigator.serviceWorker.controller?.postMessage({ action: 'CHECK_FOR_UPDATES', nonce: Date.now() }));
}

async function irParaSegundoPlano(page: Page) {
  await page.evaluate(() => {
    Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
    document.dispatchEvent(new Event('visibilitychange'));
  });
}

async function mainContemMarca(page: Page): Promise<boolean> {
  return page.evaluate(async (marca) => {
    const src = Array.from(document.scripts).map(s => s.src).find(s => /main-.*\.js$/.test(s));
    return !!src && (await (await fetch(src)).text()).includes(marca);
  }, MARCA_V2);
}

test('versão nova no meio do lançamento: não interrompe, entra no segundo plano e mantém tudo; rollback idem', async ({ page, context, request }) => {
  test.setTimeout(120_000);
  gerarVersao2();
  await request.get('/__versao/v1');
  await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(context);

  await loginPelaUi(page);
  await page.evaluate(() => navigator.serviceWorker.ready);
  await page.waitForFunction(async () => (await caches.keys()).some(k => k.includes('ngsw')), null, { timeout: 30_000 });
  await page.reload(); // passa a ser controlada pelo SW
  await expect.poll(() => page.evaluate(() => !!navigator.serviceWorker.controller)).toBe(true);

  // Lançamento em andamento.
  await page.goto('/campo/nova');
  await page.locator('#nota').fill('6060606');
  await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
  await page.getByRole('radio', { name: /CORTE/ }).click();
  await expect(page.getByText('Local registrado')).toBeVisible();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await page.getByRole('button', { name: /Religação/ }).click();
  await page.waitForTimeout(450);
  await page.evaluate(() => ((window as unknown as { __semRecarregar: boolean }).__semRecarregar = true));

  // Deploy da versão 2.
  await request.get('/__versao/v2');
  await procurarAtualizacao(page);
  await expect.poll(() => eventosAtualizacao(page), { timeout: 30_000 }).toContain('pronta');

  // A tela NÃO recarregou e o trabalho segue na frente do usuário.
  expect(await page.evaluate(() => (window as unknown as { __semRecarregar?: boolean }).__semRecarregar)).toBe(true);
  await expect(page.getByRole('button', { name: /Religação/ })).toHaveAttribute('aria-pressed', 'true');

  // App vai para segundo plano: a versão nova entra e a tela volta igual.
  await irParaSegundoPlano(page);
  await page.waitForFunction(() => !(window as unknown as { __semRecarregar?: boolean }).__semRecarregar, null, { timeout: 30_000 });
  await expect(page).toHaveURL(/\/campo\/nova$/);
  await expect(page.getByText(/Serviços · 2 de 4/)).toBeVisible();
  await expect(page.getByRole('button', { name: /Religação/ })).toHaveAttribute('aria-pressed', 'true');
  expect(await mainContemMarca(page)).toBe(true);

  // Rollback: redeploy da versão 1.
  await page.evaluate(() => ((window as unknown as { __semRecarregar: boolean }).__semRecarregar = true));
  await request.get('/__versao/v1');
  await procurarAtualizacao(page);
  await expect.poll(async () => (await eventosAtualizacao(page)).filter(e => e === 'pronta').length, { timeout: 30_000 }).toBe(2);
  await irParaSegundoPlano(page);
  await page.waitForFunction(() => !(window as unknown as { __semRecarregar?: boolean }).__semRecarregar, null, { timeout: 30_000 });
  await expect(page.getByText(/Serviços · 2 de 4/)).toBeVisible();
  await expect(page.getByRole('button', { name: /Religação/ })).toHaveAttribute('aria-pressed', 'true');
  expect(await mainContemMarca(page)).toBe(false);

  // O banco principal (fila de envio) continua na versão do build antigo: Dexie vN = IndexedDB N*10.
  const versaoBanco = await page.evaluate(() => new Promise<number>(r => {
    const req = indexedDB.open('ces-field-db');
    req.onsuccess = () => { r(req.result.version); req.result.close(); };
  }));
  expect(versaoBanco).toBe(environment.dbVersion * 10);
});
