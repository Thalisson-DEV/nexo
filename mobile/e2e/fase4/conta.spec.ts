import { test, expect, Page, BrowserContext } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/** Fase 4 · Conta: regras de saída, preferências de rede e retomada da confirmação. */

async function matar(page: Page, context: BrowserContext): Promise<Page> {
  await page.waitForTimeout(450);
  await page.close({ runBeforeUnload: false });
  const nova = await context.newPage();
  await nova.goto('/?origem=app');
  return nova;
}

test.describe('conta', () => {
  test('com turno aberto não sai', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(page);
    await loginPelaUi(page);
    await page.goto('/perfil');
    await expect(page.getByText('Turno aberto')).toBeVisible();
    const sair = page.getByRole('button', { name: /Sair da conta/ });
    await expect(sair).toHaveAttribute('aria-disabled', 'true');
    await expect(page.getByText('Encerre o turno na tela Hoje antes de sair')).toBeVisible();
    await sair.click({ force: true });
    await expect(page.getByText('Encerre seu turno antes de sair.').first()).toBeVisible();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(page).toHaveURL(/\/perfil$/);
  });

  test('sem turno: confirma na folha e volta ao login; a próxima abertura não entra na conta', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: false }).install(page);
    await loginPelaUi(page);
    await page.goto('/perfil');
    await page.getByRole('button', { name: /Sair da conta/ }).click();
    const folha = page.getByRole('dialog', { name: 'Sair da conta?' });
    await folha.getByRole('button', { name: 'Ficar' }).click();
    await expect(folha).toBeHidden();
    await page.getByRole('button', { name: /Sair da conta/ }).click();
    await page.getByRole('dialog', { name: 'Sair da conta?' }).getByRole('button', { name: 'Sair', exact: true }).click();
    await expect(page).toHaveURL(/\/login/);
    await page.goto('/?origem=app');
    await expect(page).toHaveURL(/\/login/);
  });

  test('preferências de rede valem depois de reabrir', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: false }).install(context);
    await loginPelaUi(page);
    await page.goto('/perfil');
    const offline = page.getByRole('switch', { name: /Trabalhar sem internet/ });
    await offline.click();
    await expect(offline).toHaveAttribute('aria-checked', 'true');
    const reaberta = await matar(page, context);
    await expect(reaberta).toHaveURL(/\/perfil$/);
    await expect(reaberta.getByRole('switch', { name: /Trabalhar sem internet/ })).toHaveAttribute('aria-checked', 'true');
  });

  test('app morto com a confirmação de saída aberta: volta com ela aberta', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: false }).install(context);
    await loginPelaUi(page);
    await page.goto('/perfil');
    await page.getByRole('button', { name: /Sair da conta/ }).click();
    await expect(page.getByRole('dialog', { name: 'Sair da conta?' })).toBeVisible();
    await page.waitForTimeout(400);
    const reaberta = await matar(page, context);
    await expect(reaberta.getByRole('dialog', { name: 'Sair da conta?' })).toBeVisible();
  });
});
