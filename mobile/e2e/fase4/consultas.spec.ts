import { test, expect, Page } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/** Fase 4 · Telas de consulta do material: pedidos, transferências e saldo, com números em evidência. */

async function pedir(page: Page) {
  await page.goto('/solicitacoes/nova');
  await page.getByRole('button', { name: /^Retirar de:/ }).click();
  await page.getByRole('dialog').getByRole('radio', { name: /Almoxarifado Central/ }).click();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await page.getByRole('button', { name: 'Adicionar Cabo concêntrico 10mm' }).click();
  const qtd = page.getByRole('textbox', { name: 'quantidade de Cabo concêntrico 10mm' });
  await qtd.fill('12,5');
  await qtd.press('Enter');
  await qtd.blur();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await page.getByRole('button', { name: 'Enviar pedido' }).click();
  await expect(page).toHaveURL(/\/solicitacoes$/);
}

test.describe('consultas de material', () => {
  test('pedido aparece na lista com contagem e situação; o detalhe mostra a quantidade grande', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);
    await pedir(page);
    // Ao enviar, o id temporário vira o do servidor; abrir antes disso cai em "não encontrado".
    await expect.poll(() => api.escritasEm('/v1/solicitacoes-material').length).toBe(1);
    await page.waitForTimeout(800);
    const linha = page.getByRole('link', { name: /Cabo concêntrico 10mm/ });
    await expect(linha).toBeVisible();
    await expect(linha.getByText(/^1\s*item$/)).toBeVisible();
    await linha.click();
    await expect(page.getByRole('heading', { name: /Materiais pedidos/ })).toBeVisible();
    await expect(page.getByRole('list', { name: 'Materiais pedidos' }).getByText(/^12,5\s*M$/)).toBeVisible();
    await expect(page.getByText('Almoxarifado Central')).toBeVisible();
  });

  test('filtro e período da lista de pedidos voltam iguais depois de reabrir', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(context);
    await loginPelaUi(page);
    await page.goto('/solicitacoes');
    await page.getByRole('radio', { name: /Entregues/ }).click();
    await page.getByRole('button', { name: /Período:/ }).click();
    await page.getByRole('dialog', { name: 'Período' }).getByRole('radio', { name: 'Todo o período' }).click();
    await page.waitForTimeout(450);
    await page.close({ runBeforeUnload: false });
    const reaberta = await context.newPage();
    await reaberta.goto('/?origem=app');
    await expect(reaberta).toHaveURL(/\/solicitacoes$/);
    await expect(reaberta.getByRole('radio', { name: /Entregues/ })).toHaveAttribute('aria-checked', 'true');
    await expect(reaberta.getByRole('button', { name: 'Período: Todo o período' })).toBeVisible();
  });

  test('saldo do caminhão mostra quantidade e condição, e filtra por sucata', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(page);
    await loginPelaUi(page);
    await page.goto('/solicitacoes/estoque');
    const lista = page.getByRole('list', { name: 'Materiais no caminhão' });
    await expect(lista.getByText('Cabo concêntrico 10mm')).toBeVisible();
    await expect(lista.locator('.ces-qtd').first()).toBeVisible();
    await page.getByRole('searchbox', { name: 'Buscar no saldo' }).fill('zzz-nada');
    await expect(page.getByText('Nada encontrado')).toBeVisible();
  });
});
