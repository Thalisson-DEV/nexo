import { test, expect, Page } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/**
 * Lançamento de execução (nota + GPS → serviços → materiais → conferência).
 *
 * Contrato do envio fixado na Fase 0 (corpo e Idempotency-Key) — a UI nova precisa
 * produzir exatamente o mesmo POST. Os antigos [DEFEITO ATUAL] foram invertidos:
 * agora exigem a retomada exata.
 */

test.use({
  geolocation: { latitude: -3.7319, longitude: -38.5267, accuracy: 12 },
  permissions: ['geolocation'],
});

async function preencherPasso1(page: Page, nota = '7004512') {
  await page.goto('/campo/nova');
  await expect(page.getByText(/· 1 de 4/)).toBeVisible();
  await page.locator('#nota').fill(nota);
  await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
  await page.getByRole('radio', { name: /CORTE/ }).click();
  await expect(page.getByText('Local registrado')).toBeVisible();
}

async function avancar(page: Page) {
  await page.getByRole('button', { name: /Continuar/ }).click();
}

test.describe('lançamento de execução', () => {
  test('online: envia um único POST com chave de idempotência e o corpo esperado', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);

    await preencherPasso1(page);
    await avancar(page);
    await expect(page.getByText(/· 2 de 4/)).toBeVisible();
    await page.getByRole('button', { name: /Ligação nova/ }).click();
    await avancar(page);
    await expect(page.getByText(/· 3 de 4/)).toBeVisible();
    await page.getByRole('button', { name: 'Aplicar Cabo concêntrico 10mm' }).click();
    await avancar(page);
    await expect(page.getByText(/· 4 de 4/)).toBeVisible();
    await page.getByRole('button', { name: 'Lançar execução' }).click();

    await expect(page).toHaveURL(/\/campo$/);
    await expect.poll(() => api.escritasEm('/v1/execucoes-comerciais').length).toBe(1);

    const [envio] = api.escritasEm('/v1/execucoes-comerciais');
    expect(envio.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/);
    expect(envio.authorization).toMatch(/^Bearer /);
    const { dataLancamento, ...corpo } = envio.body as Record<string, unknown>;
    expect(Date.parse(String(dataLancamento))).not.toBeNaN();
    // Contrato do POST /v1/execucoes-comerciais: lançamento de etapa única.
    expect(corpo).toEqual({
      referenciaNota: '7004512',
      tipoNota: 'COMUM',
      idEquipe: 'e-1',
      idTurno: 't-1',
      idTipoServico: 'ts-cs',
      latitude: -3.7319,
      longitude: -38.5267,
      acoes: ['a-1'],
      materiais: [{ idMaterial: 'm-cabo', quantidade: 1, operacao: 'APLICADO', numSerie: null, origem: 'EQUIPE' }],
    });
  });

  test('material com série: envia uma linha por série escolhida', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);
    await preencherPasso1(page, '7004600');
    await avancar(page);
    await avancar(page);
    await page.getByRole('button', { name: 'Aplicar Medidor monofásico' }).click();
    await page.getByRole('button', { name: /SN-0002/ }).click();
    await page.getByRole('button', { name: 'Pronto' }).click();
    await avancar(page);
    await page.getByRole('button', { name: 'Lançar execução' }).click();
    await expect.poll(() => api.escritasEm('/v1/execucoes-comerciais').length).toBe(1);
    expect((api.escritasEm('/v1/execucoes-comerciais')[0].body as { materiais: unknown[] }).materiais)
      .toEqual([{ idMaterial: 'm-medidor', quantidade: 1, operacao: 'APLICADO', numSerie: 'SN-0002', origem: 'EQUIPE' }]);
  });

  test('offline: grava no aparelho, não chama a API e envia uma única vez ao reconectar', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);

    await preencherPasso1(page, '8800001');
    await context.setOffline(true);
    api.offline = true;
    await avancar(page);
    await avancar(page);
    await avancar(page);
    await page.getByRole('button', { name: 'Lançar execução' }).click();
    // Sem serviços nem materiais o app pede confirmação explícita.
    await page.getByRole('button', { name: 'Sim, lançar' }).click();
    await expect(page.getByText(/salva no aparelho/i)).toBeVisible();
    expect(api.escritasEm('/v1/execucoes-comerciais')).toHaveLength(0);

    api.offline = false;
    await context.setOffline(false);
    await expect.poll(() => api.escritasEm('/v1/execucoes-comerciais').length, { timeout: 15_000 }).toBe(1);
    await page.waitForTimeout(1500);
    expect(api.escritasEm('/v1/execucoes-comerciais')).toHaveLength(1);
  });

  test('sem turno aberto, o lançamento manda abrir o turno', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: false });
    await api.install(page);
    await loginPelaUi(page);
    await page.goto('/campo/nova');
    await expect(page).toHaveURL(/\/turno\/abrir$/);
  });

  test('recarregar no passo 2 mantém o passo, a nota e o serviço marcado', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(page);
    await loginPelaUi(page);
    await preencherPasso1(page, '5550001');
    await avancar(page);
    await page.getByRole('button', { name: /Religação/ }).click();
    await page.waitForTimeout(500); // > debounce do rascunho (300 ms)

    await page.reload();

    await expect(page.getByText(/· 2 de 4/)).toBeVisible();
    await expect(page.getByRole('button', { name: /Religação/ })).toHaveAttribute('aria-pressed', 'true');
    await page.getByRole('button', { name: 'Voltar' }).click();
    await expect(page.locator('#nota')).toHaveValue('5550001');
  });

  test('processo morto: reabrir pelo ícone volta à mesma tela, mesmo passo e tudo preenchido', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(context);
    await loginPelaUi(page);
    await preencherPasso1(page, '5550002');
    await avancar(page);
    await page.getByRole('button', { name: /Ligação nova/ }).click();
    await avancar(page);
    await page.getByRole('button', { name: 'Aplicar Cabo concêntrico 10mm' }).click();
    await page.getByRole('button', { name: 'Aumentar quantidade de Cabo concêntrico 10mm' }).click();
    await page.getByRole('button', { name: 'Aumentar quantidade de Cabo concêntrico 10mm' }).click();
    await page.waitForTimeout(500);

    // Sistema mata o processo: a página some sem eventos de saída confiáveis.
    await page.close({ runBeforeUnload: false });
    const reaberta = await context.newPage();
    await reaberta.goto('/?origem=app');

    await expect(reaberta).toHaveURL(/\/campo\/nova$/);
    await expect(reaberta.getByText(/Materiais · 3 de 4/)).toBeVisible();
    await expect(reaberta.getByRole('textbox', { name: 'quantidade de Cabo concêntrico 10mm' })).toHaveValue('3');
    await reaberta.getByRole('button', { name: 'Voltar' }).click();
    await expect(reaberta.getByRole('button', { name: /Ligação nova/ })).toHaveAttribute('aria-pressed', 'true');
    await reaberta.getByRole('button', { name: 'Voltar' }).click();
    await expect(reaberta.locator('#nota')).toHaveValue('5550002');

    // E o envio continua idêntico ao de quem nunca saiu do app.
    for (let i = 0; i < 3; i++) await reaberta.getByRole('button', { name: /Continuar/ }).click();
    await reaberta.getByRole('button', { name: 'Lançar execução' }).click();
    await expect.poll(() => api.escritasEm('/v1/execucoes-comerciais').length).toBe(1);
    expect((api.escritasEm('/v1/execucoes-comerciais')[0].body as { materiais: unknown[] }).materiais)
      .toEqual([{ idMaterial: 'm-cabo', quantidade: 3, operacao: 'APLICADO', numSerie: null, origem: 'EQUIPE' }]);
  });

  test('depois de lançar, o rascunho some e um novo lançamento começa limpo', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);
    await preencherPasso1(page, '7770001');
    await avancar(page);
    await page.getByRole('button', { name: /Corte/ }).click();
    await avancar(page);
    await avancar(page);
    await page.getByRole('button', { name: 'Lançar execução' }).click();
    await expect(page).toHaveURL(/\/campo$/);

    await page.goto('/campo/nova');
    await expect(page.getByText(/· 1 de 4/)).toBeVisible();
    await expect(page.locator('#nota')).toHaveValue('');
  });
});
