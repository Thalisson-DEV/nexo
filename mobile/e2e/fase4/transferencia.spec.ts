import { test, expect, Page, BrowserContext } from '@playwright/test';
import { MockApi, loginPelaUi, IDS } from '../support/mock-api';

/** Fase 4 · Transferência com duas assinaturas: contrato, saldo e retomada entre as assinaturas. */

async function assinar(page: Page, quem: 'Quem entrega' | 'Quem recebe') {
  await page.getByRole('button', { name: new RegExp(quem) }).click();
  const quadro = page.getByLabel('Quadro de assinatura');
  await expect(quadro).toBeVisible();
  await page.waitForTimeout(350); // folha terminando de subir
  const caixa = (await quadro.boundingBox())!;
  await page.mouse.move(caixa.x + 30, caixa.y + 60);
  await page.mouse.down();
  for (let i = 1; i <= 10; i++) await page.mouse.move(caixa.x + 30 + i * 20, caixa.y + 60 + (i % 2) * 40);
  await page.mouse.up();
  await page.getByRole('button', { name: 'Confirmar assinatura' }).click();
  await expect(page.getByRole('img', { name: new RegExp(`Assinatura de ${quem.toLowerCase()}`) })).toBeVisible();
}

async function ateAssinaturas(page: Page) {
  await page.goto('/transferencias/nova');
  await page.getByRole('button', { name: /^Vai para:/ }).click();
  await page.getByRole('dialog').getByRole('radio', { name: /Almoxarifado Central/ }).click();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await page.getByRole('button', { name: 'Adicionar Cabo concêntrico 10mm' }).click();
  await page.getByRole('button', { name: 'Aumentar quantidade de Cabo concêntrico 10mm' }).click();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await expect(page.getByRole('list', { name: 'Itens da transferência' }).getByText(/^2\s*M$/)).toBeVisible();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await expect(page.getByText(/Assinatura digital · 4 de 4/)).toBeVisible();
}

async function matar(page: Page, context: BrowserContext): Promise<Page> {
  await page.waitForTimeout(450);
  await page.close({ runBeforeUnload: false });
  const nova = await context.newPage();
  await nova.goto('/?origem=app');
  return nova;
}

test.describe('transferência', () => {
  test('sem as duas assinaturas não confirma; com elas envia itens e assinaturas', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);
    await ateAssinaturas(page);
    await page.getByRole('button', { name: 'Confirmar transferência' }).click();
    await expect(page.getByText('Colha as duas assinaturas para confirmar.')).toBeVisible();
    await assinar(page, 'Quem entrega');
    await assinar(page, 'Quem recebe');
    await page.getByRole('button', { name: 'Confirmar transferência' }).click();
    await expect(page).toHaveURL(/\/transferencias$/);
    await expect.poll(() => api.escritasEm('/v1/movimentacoes-estoque/transferencia').length, { timeout: 15_000 }).toBe(1);
    const corpo = api.escritasEm('/v1/movimentacoes-estoque/transferencia')[0].body as Record<string, any>;
    expect(corpo).toMatchObject({ idEstoqueOrigem: IDS.estoque, idEstoqueDestino: 'est-dep',
      itens: [{ idMaterial: 'm-cabo', quantidade: 2, condicao: 'NOVO', numSerie: null }] });
  });

  test('não passa do saldo do caminhão', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(page);
    await loginPelaUi(page);
    await page.goto('/transferencias/nova');
    await page.getByRole('button', { name: /^Vai para:/ }).click();
    await page.getByRole('dialog').getByRole('radio', { name: /Almoxarifado Central/ }).click();
    await page.getByRole('button', { name: /Continuar/ }).click();
    await page.getByRole('button', { name: 'Adicionar Cabo concêntrico 10mm' }).click();
    const qtd = page.getByRole('textbox', { name: 'quantidade de Cabo concêntrico 10mm' });
    await qtd.fill('500');
    await qtd.press('Enter');
    await qtd.blur();
    await expect(page.getByText('Mais do que o saldo do caminhão.')).toBeVisible();
  });

  test('app morto entre a assinatura de quem entrega e a de quem recebe: a primeira continua lá', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(context);
    await loginPelaUi(page);
    await ateAssinaturas(page);
    await assinar(page, 'Quem entrega');

    const reaberta = await matar(page, context);
    await expect(reaberta).toHaveURL(/\/transferencias\/nova$/);
    await expect(reaberta.getByText(/Assinatura digital · 4 de 4/)).toBeVisible();
    await expect(reaberta.getByRole('img', { name: /Assinatura de quem entrega/ })).toBeVisible();
    await assinar(reaberta, 'Quem recebe');
    await reaberta.getByRole('button', { name: 'Confirmar transferência' }).click();
    await expect.poll(() => api.escritasEm('/v1/movimentacoes-estoque/transferencia').length, { timeout: 15_000 }).toBe(1);
  });
});
