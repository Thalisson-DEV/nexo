import { test, expect, Page, BrowserContext } from '@playwright/test';
import { MockApi, loginPelaUi, IDS } from '../support/mock-api';

/** Fase 4 · Pedido de material: contrato do envio, quantidade fracionária e retomada. */

async function montarPedido(page: Page) {
  await page.goto('/solicitacoes/nova');
  await page.getByRole('button', { name: /^Retirar de:/ }).click();
  await page.getByRole('dialog').getByRole('radio', { name: /Almoxarifado Central/ }).click();
  await expect(page.getByText('Caminhão EQ-101')).toBeVisible();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await page.getByRole('button', { name: 'Adicionar Cabo concêntrico 10mm' }).click();
  const qtd = page.getByRole('textbox', { name: 'quantidade de Cabo concêntrico 10mm' });
  await qtd.fill('2,5');
  await qtd.press('Enter');
  await qtd.blur();
  await expect(qtd).toHaveValue('2,5');
}

async function matar(page: Page, context: BrowserContext): Promise<Page> {
  await page.waitForTimeout(450);
  await page.close({ runBeforeUnload: false });
  const nova = await context.newPage();
  await nova.goto('/?origem=app');
  return nova;
}

test.describe('pedido de material', () => {
  test('envia o corpo esperado, com quantidade fracionária e quem vai retirar', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);
    await montarPedido(page);
    await page.getByRole('button', { name: /Continuar/ }).click();
    await expect(page.getByRole('list', { name: 'Materiais pedidos' }).getByText('2,5')).toBeVisible();
    await page.getByRole('button', { name: 'Enviar pedido' }).click();
    await expect(page).toHaveURL(/\/solicitacoes$/);
    await expect.poll(() => api.escritasEm('/v1/solicitacoes-material').length).toBe(1);
    const { dataPrevista, ...corpo } = api.escritasEm('/v1/solicitacoes-material')[0].body as Record<string, unknown>;
    expect(dataPrevista).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(corpo).toEqual({
      idEstoqueOrigem: 'est-dep', idEquipe: IDS.equipe, idColaboradorSaque: 'c-1', saqueForaEquipe: false,
      itens: [{ idMaterial: 'm-cabo', quantidade: 2.5 }],
    });
  });

  test('retirada por alguém de fora da equipe vai sem colaborador', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);
    await page.goto('/solicitacoes/nova');
    await page.getByRole('button', { name: /^Retirar de:/ }).click();
    await page.getByRole('dialog').getByRole('radio', { name: /Almoxarifado Central/ }).click();
    await page.getByRole('switch', { name: /fora da equipe/ }).click();
    await page.getByRole('button', { name: /Continuar/ }).click();
    await page.getByRole('button', { name: 'Adicionar Medidor monofásico' }).click();
    await page.getByRole('button', { name: /Continuar/ }).click();
    await page.getByRole('button', { name: 'Enviar pedido' }).click();
    await expect.poll(() => api.escritasEm('/v1/solicitacoes-material').length).toBe(1);
    expect(api.escritasEm('/v1/solicitacoes-material')[0].body).toMatchObject({ idColaboradorSaque: null, saqueForaEquipe: true });
  });

  test('processo morto no meio: volta com origem, materiais e quantidades', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(context);
    await loginPelaUi(page);
    await montarPedido(page);
    const reaberta = await matar(page, context);
    await expect(reaberta).toHaveURL(/\/solicitacoes\/nova$/);
    await expect(reaberta.getByText(/Materiais · 2 de 3/)).toBeVisible();
    await expect(reaberta.getByRole('textbox', { name: 'quantidade de Cabo concêntrico 10mm' })).toHaveValue('2,5');
    await reaberta.getByRole('button', { name: 'Voltar' }).click();
    await expect(reaberta.getByRole('button', { name: /^Retirar de: Almoxarifado Central/ })).toBeVisible();
  });

  test('sem turno aberto, não deixa pedir', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: false }).install(page);
    await loginPelaUi(page);
    await page.goto('/solicitacoes/nova');
    await expect(page).toHaveURL(/\/solicitacoes$/);
  });
});
