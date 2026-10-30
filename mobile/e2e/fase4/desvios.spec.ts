import { test, expect, Page, BrowserContext } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/** Fase 4 · Meus desvios: lista online, tratamento e retomada do tratamento em edição. */

async function matar(page: Page, context: BrowserContext): Promise<Page> {
  await page.waitForTimeout(450);
  await page.close({ runBeforeUnload: false });
  const nova = await context.newPage();
  await nova.goto('/?origem=app');
  return nova;
}

test.describe('desvios', () => {
  test('lista pendentes com gravidade, situação e prazo vencido em destaque', async ({ page }) => {
    await new MockApi({ perfil: 'ADMINISTRATIVO' }).install(page);
    await loginPelaUi(page);
    await page.goto('/desvios');
    const lista = page.getByRole('list', { name: 'Desvios' });
    await expect(lista.getByRole('button')).toHaveCount(2);
    await expect(lista.getByText('Gravidade alta')).toBeVisible();
    await expect(lista.getByText('Crítico')).toBeVisible();
    await expect(lista.getByText(/Venceu em 05\/10\/2026/)).toBeVisible();
  });

  test('tratar um desvio envia situação, ação e prazo', async ({ page }) => {
    const api = new MockApi({ perfil: 'ADMINISTRATIVO' });
    await api.install(page);
    await loginPelaUi(page);
    await page.goto('/desvios');
    await page.getByRole('button', { name: /Usa capacete com jugular/ }).click();
    const folha = page.getByRole('dialog', { name: 'Tratar desvio' });
    await folha.getByRole('radio', { name: 'Resolvido' }).click();
    await folha.getByLabel('O que foi ou será feito').fill('Capacete trocado');
    await folha.getByRole('button', { name: 'Salvar' }).click();
    await expect(folha).toBeHidden();
    const put = api.escritasEm('/v1/sst/desvios/meus/dv-a/tratamento');
    expect(put).toHaveLength(1);
    expect(put[0].body).toMatchObject({ status: 'RESOLVIDO', acaoCorretiva: 'Capacete trocado' });
  });

  test('app morto com o tratamento aberto: volta com a folha e o texto digitado', async ({ page, context }) => {
    await new MockApi({ perfil: 'ADMINISTRATIVO' }).install(context);
    await loginPelaUi(page);
    await page.goto('/desvios');
    await page.getByRole('button', { name: /Usa capacete com jugular/ }).click();
    await page.getByRole('dialog', { name: 'Tratar desvio' }).getByLabel('O que foi ou será feito').fill('Equipe orientada');
    const reaberta = await matar(page, context);
    await expect(reaberta).toHaveURL(/\/desvios/);
    const folha = reaberta.getByRole('dialog', { name: 'Tratar desvio' });
    await expect(folha).toBeVisible();
    await expect(folha.getByLabel('O que foi ou será feito')).toHaveValue('Equipe orientada');
  });

  test('sem internet explica que a lista depende do servidor', async ({ page, context }) => {
    await new MockApi({ perfil: 'ADMINISTRATIVO' }).install(page);
    await loginPelaUi(page);
    await page.goto('/desvios');
    await context.setOffline(true);
    await page.evaluate(() => window.dispatchEvent(new Event('offline')));
    await expect(page.getByText('Desvios precisam de internet')).toBeVisible();
  });
});
