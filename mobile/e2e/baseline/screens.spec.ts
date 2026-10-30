import { test, expect, Page } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/**
 * Captura a baseline visual da UI ATUAL (antes do redesenho) em
 * docs/mobile/baseline/screens/<projeto>/. Não compara nada: é registro.
 * Histórico: roda contra o build da base (origin/main @ 8425f426), não contra a UI nova.
 * Para regenerar: faça checkout da base, `npm run build`, e `npx playwright test e2e/baseline`.
 */
test.skip(!process.env['CES_BASELINE'], 'baseline da UI antiga: defina CES_BASELINE=1 no build da base');

test.use({
  geolocation: { latitude: -3.7319, longitude: -38.5267, accuracy: 12 },
  permissions: ['geolocation'],
});

const dir = (p: string) => `../docs/mobile/baseline/screens/${test.info().project.name}/${p}.png`;

async function shot(page: Page, nome: string) {
  await page.waitForTimeout(600); // animações de entrada
  await page.screenshot({ path: dir(nome) });
}

test('baseline · encarregado de campo', async ({ page }) => {
  test.setTimeout(90_000);
  const api = new MockApi({ perfil: 'CAMPO', turnoAberto: false });
  await api.install(page);

  await page.goto('/login');
  await expect(page.locator('app-splash-screen > div')).toHaveCount(0, { timeout: 15_000 });
  await shot(page, '01-login');

  await loginPelaUi(page);
  await shot(page, '02-inicio-sem-turno');

  await page.goto('/turno/abrir');
  await expect(page.getByText('Abertura de Turno')).toBeVisible();
  await shot(page, '03-abrir-turno');
  await page.getByRole('button', { name: 'Abrir Turno de Trabalho' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  await shot(page, '04-inicio-com-turno');

  await page.goto('/campo/nova');
  await page.locator('#nota').fill('7004512');
  await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
  await page.getByRole('radio', { name: /CORTE/ }).click();
  await expect(page.getByText('Coordenadas confirmadas')).toBeVisible();
  await shot(page, '05-execucao-passo1');
  await page.locator('footer').getByRole('button', { name: /Avançar/ }).click();
  await page.getByRole('button', { name: /Ligação nova/ }).click();
  await shot(page, '06-execucao-passo2');
  await page.locator('footer').getByRole('button', { name: /Avançar/ }).click();
  await shot(page, '07-execucao-passo3');
  await page.getByRole('button', { name: /^add\s*Aplicar$/ }).first().click();
  await page.locator('footer').getByRole('button', { name: /Avançar/ }).click();
  await shot(page, '08-execucao-passo4');

  for (const [rota, nome] of [
    ['/campo', '09-campo-lista'],
    ['/solicitacoes', '10-pedidos-lista'],
    ['/solicitacoes/nova', '11-pedido-novo'],
    ['/solicitacoes/estoque', '12-estoque'],
    ['/transferencias', '13-transferencias-lista'],
    ['/transferencias/nova', '14-transferencia-nova'],
    ['/perfil', '15-perfil'],
  ] as const) {
    await page.goto(rota);
    await page.waitForLoadState('networkidle');
    await shot(page, nome);
  }
});

test('baseline · técnico de SST (administrativo)', async ({ page }) => {
  test.setTimeout(90_000);
  await new MockApi({ perfil: 'ADMINISTRATIVO' }).install(page);
  await loginPelaUi(page);
  await shot(page, '20-sst-inicio');
  for (const [rota, nome] of [
    ['/inspecoes', '21-sst-inspecoes'],
    ['/inspecoes/nova', '22-sst-inspecao-nova'],
    ['/desvios', '23-sst-desvios'],
  ] as const) {
    await page.goto(rota);
    await page.waitForLoadState('networkidle');
    await shot(page, nome);
  }
});
