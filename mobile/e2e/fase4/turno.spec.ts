import { test, expect, Page, BrowserContext } from '@playwright/test';
import { MockApi, loginPelaUi, IDS } from '../support/mock-api';

/** Fase 4 · Abrir turno: regras de composição, contrato do envio e retomada. */

async function abrirConferencia(page: Page) {
  await page.getByRole('link', { name: /Abrir turno/i }).click();
  await expect(page).toHaveURL(/\/turno\/abrir$/);
  await expect(page.getByText('ABC1D23')).toBeVisible();
}

async function trocarEquipe(page: Page) {
  await page.getByRole('button', { name: /ABC1D23/ }).click();
  await page.getByRole('dialog').getByRole('button', { name: /XYZ9K87/ }).click();
  await page.getByRole('button', { name: 'Tirar Maria Eletricista do turno' }).click();
  await page.getByRole('button', { name: 'Incluir pessoa' }).click();
  await page.getByRole('dialog').getByRole('button', { name: /Pedro Ajudante/ }).click();
}

test.describe('abrir turno', () => {
  test('líder troca veículo e pessoas: o envio leva exatamente as mudanças', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: false });
    await api.install(page);
    await loginPelaUi(page);
    await abrirConferencia(page);
    await trocarEquipe(page);
    await expect(page.getByText('Fora do turno hoje: Maria Eletricista.')).toBeVisible();
    await page.getByRole('button', { name: 'Abrir turno com as mudanças' }).click();
    await expect(page).toHaveURL(/\/hoje$/);
    expect(api.escritasEm('/v1/turnos/abrir')[0].body).toEqual({ idEquipe: IDS.equipe, idVeiculo: 'v-2', idsColaboradores: ['c-3'] });
  });

  test('líder de outra equipe não pode ser incluído', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: false }).install(page);
    await loginPelaUi(page);
    await abrirConferencia(page);
    await page.getByRole('button', { name: 'Incluir pessoa' }).click();
    await expect(page.getByRole('dialog').getByRole('button', { name: /Rita Líder/ })).toBeDisabled();
  });

  test('processo morto no meio da conferência: volta com a placa e as pessoas trocadas', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: false });
    await api.install(context);
    await loginPelaUi(page);
    await abrirConferencia(page);
    await trocarEquipe(page);
    const reaberta = await matar(page, context);
    await expect(reaberta).toHaveURL(/\/turno\/abrir$/);
    await expect(reaberta.getByText('XYZ9K87')).toBeVisible();
    await expect(reaberta.getByText('Pedro Ajudante')).toBeVisible();
    await expect(reaberta.getByText('Fora do turno hoje: Maria Eletricista.')).toBeVisible();
    await reaberta.getByRole('button', { name: 'Abrir turno com as mudanças' }).click();
    await expect(reaberta).toHaveURL(/\/hoje$/);
    expect(api.escritasEm('/v1/turnos/abrir')[0].body).toEqual({ idEquipe: IDS.equipe, idVeiculo: 'v-2', idsColaboradores: ['c-3'] });
  });

  test('sem internet: não abre, avisa e mantém a conferência', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: false });
    await api.install(page);
    await loginPelaUi(page);
    await abrirConferencia(page);
    await trocarEquipe(page);
    await page.evaluate(() => window.dispatchEvent(new Event('offline')));
    await context.setOffline(true);
    await expect(page.getByText('Abrir o turno precisa de internet. A conferência fica salva.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Abrir turno com as mudanças' })).toBeDisabled();
    expect(api.escritasEm('/v1/turnos/abrir')).toHaveLength(0);
  });
});

async function matar(page: Page, context: BrowserContext): Promise<Page> {
  await page.waitForTimeout(450);
  await page.close({ runBeforeUnload: false });
  const nova = await context.newPage();
  await nova.goto('/?origem=app');
  return nova;
}
