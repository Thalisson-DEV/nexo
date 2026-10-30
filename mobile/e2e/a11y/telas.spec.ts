import { test, expect, Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { MockApi, loginPelaUi } from '../support/mock-api';

/**
 * Acessibilidade das telas do piloto (WCAG 2.1 A/AA via axe-core), claro e escuro.
 * Falha com qualquer violação "serious" ou "critical".
 */

test.use({
  geolocation: { latitude: -3.7319, longitude: -38.5267, accuracy: 12 },
  permissions: ['geolocation'],
});

async function auditar(page: Page, tela: string) {
  await page.waitForTimeout(400);
  const r = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  const graves = r.violations.filter(v => v.impact === 'serious' || v.impact === 'critical');
  expect(graves.map(v => `${tela}: ${v.id} (${v.impact}) → ${v.nodes.slice(0, 3).map(n => n.target.join(' ')).join(' | ')}`)).toEqual([]);
}

for (const tema of ['claro', 'escuro'] as const) {
  test(`telas do piloto sem violação grave · tema ${tema}`, async ({ page }) => {
    test.setTimeout(90_000);
    if (tema === 'escuro') await page.addInitScript(() => localStorage.setItem('ces_tema', 'escuro'));
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true, historico: 30 }).install(page);
    await page.goto('/login');
    await auditar(page, 'login');
    await loginPelaUi(page);
    await auditar(page, 'hoje');
    await page.getByRole('button', { name: /Conexão/ }).click();
    await auditar(page, 'folha envios');
    await page.keyboard.press('Escape');

    await page.goto('/campo');
    await auditar(page, 'serviços');
    await page.goto('/campo/hist-1');
    await auditar(page, 'detalhe');
    await page.goto('/material');
    await auditar(page, 'material');

    await page.goto('/campo/nova');
    await page.locator('#nota').fill('7004512');
    await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
    await page.getByRole('radio', { name: /CORTE/ }).click();
    await expect(page.getByText('Local registrado')).toBeVisible();
    await auditar(page, 'execução passo 1');
    await page.getByRole('button', { name: /Continuar/ }).click();
    await auditar(page, 'execução passo 2');
    await page.getByRole('button', { name: /Continuar/ }).click();
    await page.getByRole('button', { name: 'Aplicar Medidor monofásico' }).click();
    await auditar(page, 'folha séries');
  });
}
