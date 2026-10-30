import { test, expect, Page } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/**
 * Regressão visual das telas do piloto (Hoje, Nova execução, Serviços) em claro e
 * escuro. Gera também as imagens para docs/mobile/piloto/screens.
 * Primeira execução: `npx playwright test e2e/visual --update-snapshots`.
 */

test.use({
  geolocation: { latitude: -3.7319, longitude: -38.5267, accuracy: 12 },
  permissions: ['geolocation'],
});

test('celular em modo escuro: o app abre no tema claro (padrão)', async ({ page }) => {
  await page.emulateMedia({ colorScheme: 'dark' });
  await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(page);
  await loginPelaUi(page);
  const fundo = await page.evaluate(() => getComputedStyle(document.body).backgroundColor);
  expect(fundo).toBe('rgb(244, 246, 249)');
  expect(await page.evaluate(() => document.documentElement.getAttribute('data-tema'))).toBeNull();
});

for (const tema of ['light', 'dark'] as const) {
  test.describe(`tema ${tema}`, () => {
    // O escuro é escolha do usuário (Conta → Aparência), não do sistema.
    test.beforeEach(async ({ page }) => {
      if (tema === 'dark') await page.addInitScript(() => localStorage.setItem('ces_tema', 'escuro'));
    });

    async function foto(page: Page, nome: string) {
      await page.waitForTimeout(400); // transições de entrada
      const caminho = `../docs/mobile/piloto/screens/${test.info().project.name}/${nome}-${tema}.png`;
      await page.screenshot({ path: caminho });
      await expect(page).toHaveScreenshot(`${nome}-${tema}.png`, { maxDiffPixelRatio: 0.02, animations: 'disabled' });
    }

    test('telas do piloto', async ({ page }) => {
      test.setTimeout(90_000);
      const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
      await api.install(page);
      // Data e hora fixas: "Hoje" e horários estáveis entre execuções.
      await page.clock.setFixedTime(new Date('2026-10-08T10:30:00-03:00'));
      await loginPelaUi(page);
      await expect(page).toHaveURL(/\/hoje$/);
      await foto(page, '01-hoje');

      await page.getByRole('link', { name: /Nova execução/ }).click();
      await page.locator('#nota').fill('7004512');
      await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
      await page.getByRole('radio', { name: /CORTE/ }).click();
      await expect(page.getByText('Local registrado')).toBeVisible();
      await foto(page, '02-execucao-nota');

      await page.getByRole('button', { name: /Continuar/ }).click();
      await page.getByRole('button', { name: /Ligação nova/ }).click();
      await foto(page, '03-execucao-servicos');

      await page.getByRole('button', { name: /Continuar/ }).click();
      await page.getByRole('button', { name: 'Aplicar Cabo concêntrico 10mm' }).click();
      await foto(page, '04-execucao-materiais');

      await page.getByRole('button', { name: 'Aplicar Medidor monofásico' }).click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await foto(page, '05-execucao-series');
      await page.getByRole('button', { name: /SN-0001/ }).click();
      await page.getByRole('button', { name: 'Pronto' }).click();

      await page.getByRole('button', { name: /Continuar/ }).click();
      await foto(page, '06-execucao-conferir');
    });
  });
}
