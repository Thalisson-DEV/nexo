import { test, expect, Page, BrowserContext } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/**
 * Fase 4 · Inspeção de segurança com fotos (perfil SST).
 * Foto entra no armazenamento do aparelho no instante da captura: abrir a câmera
 * pode fazer o Android matar o app, e a foto que volta não pode se perder.
 */

/**
 * O WebKit do Playwright para Windows não grava Blob no IndexedDB (testado: a transação
 * falha). O rascunho tem reserva em bytes e funciona; a fila de envio (código anterior a
 * este trabalho) guarda Blob e não funciona nesse ambiente. Safari de iPhone grava Blob:
 * o envio no iPhone fica no checklist manual.
 */
const SEM_BLOB_NA_FILA = (projeto: string) => projeto === 'ios';

// PNG 1×1 válido (decodificável pelo navegador, como uma foto pequena).
const PNG = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==', 'base64');

async function escolher(page: Page, campo: RegExp, opcao: string) {
  await page.getByRole('button', { name: campo }).click();
  await page.getByRole('dialog').getByRole('radio', { name: new RegExp(opcao) }).click();
}

async function preencherAteFotos(page: Page) {
  await page.goto('/inspecoes/nova');
  await escolher(page, /^Formulário:/, 'Checklist de EPI');
  await escolher(page, /^Processo:/, 'Comercial');
  await escolher(page, /^Equipe:/, 'EQ-101');
  await escolher(page, /^Base operacional:/, 'Base Fortaleza');
  await expect(page.getByRole('button', { name: /^Inspecionados: José Encarregado, Maria Eletricista/ })).toBeVisible();
  await page.getByRole('button', { name: /Continuar/ }).click();

  await page.getByRole('radio', { name: 'Não' }).click();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await expect(page.getByText(/Desvios · 3 de 5/)).toBeVisible();
  await page.getByLabel('Ação corretiva *').fill('Capacete entregue e uso orientado');
  await page.getByRole('checkbox', { name: 'José Encarregado' }).click();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await expect(page.getByText(/Fotos · 4 de 5/)).toBeVisible();
}

async function tirarFoto(page: Page) {
  await page.locator('#evidencia-camera').setInputFiles({ name: 'local.png', mimeType: 'image/png', buffer: PNG });
  await expect(page.getByRole('list', { name: 'Fotos da inspeção' }).getByRole('listitem')).toHaveCount(1);
}

async function matar(page: Page, context: BrowserContext): Promise<Page> {
  await page.waitForTimeout(450);
  await page.close({ runBeforeUnload: false });
  const nova = await context.newPage();
  await nova.goto('/?origem=app');
  return nova;
}

test.describe('inspeção com fotos', () => {
  test('fluxo completo: sobe a foto e registra com resposta, desvio tratado e evidência', async ({ page }, info) => {
    test.skip(SEM_BLOB_NA_FILA(info.project.name), 'fila de envio com Blob não funciona no WebKit para Windows');
    const api = new MockApi({ perfil: 'ADMINISTRATIVO' });
    await api.install(page);
    await loginPelaUi(page);
    await preencherAteFotos(page);
    await tirarFoto(page);
    await page.getByRole('button', { name: /Continuar/ }).click();
    await page.getByRole('button', { name: 'Finalizar inspeção' }).click();
    await expect(page).toHaveURL(/\/inspecoes$/);

    await expect.poll(() => api.escritasEm('/v1/sst/inspecoes').length, { timeout: 15_000 }).toBe(1);
    expect(api.escritasEm('/v1/sst/inspecoes/anexos')).toHaveLength(1);
    const corpo = api.escritasEm('/v1/sst/inspecoes')[0].body as Record<string, any>;
    expect(corpo).toMatchObject({
      idFormulario: 'f-1', idTipoInspecao: 'ti-1', idProcesso: 'pr-1', idEquipe: 'e-1', idBaseOperacional: 'b-1',
      colaboradoresInspecionados: ['c-1', 'c-2'], evidencias: ['ev-0'],
    });
    expect(corpo['respostas']).toEqual([expect.objectContaining({ idPergunta: 'p-1', opcoesSelecionadas: ['o-nao'] })]);
    expect(corpo['desvios']).toEqual([expect.objectContaining({
      idPergunta: 'p-1', idOpcaoResposta: 'o-nao', acaoCorretiva: 'Capacete entregue e uso orientado', colaboradores: ['c-1'], status: 'ABERTO',
    })]);

    // Lista e detalhe no novo padrão: número de desvios em destaque, desvio com a ação tomada.
    const linha = page.getByRole('button', { name: /Checklist de EPI|Inspeção/ }).first();
    await expect(linha.getByText(/^1\s*desvio$/)).toBeVisible();
    await page.waitForTimeout(800);
    await linha.click();
    await expect(page.getByRole('list', { name: 'Desvios' }).getByText('Capacete entregue e uso orientado')).toBeVisible();
  });

  test('app morto logo depois da foto (câmera): volta nas fotos com a foto e envia igual', async ({ page, context }, info) => {
    const api = new MockApi({ perfil: 'ADMINISTRATIVO' });
    await api.install(context);
    await loginPelaUi(page);
    await preencherAteFotos(page);
    await tirarFoto(page);

    const reaberta = await matar(page, context);
    await expect(reaberta).toHaveURL(/\/inspecoes\/nova$/);
    await expect(reaberta.getByText(/Fotos · 4 de 5/)).toBeVisible();
    await expect(reaberta.getByRole('list', { name: 'Fotos da inspeção' }).getByRole('listitem')).toHaveCount(1);
    await reaberta.getByRole('button', { name: 'Voltar' }).click();
    await expect(reaberta.getByLabel('Ação corretiva *')).toHaveValue('Capacete entregue e uso orientado');
    if (SEM_BLOB_NA_FILA(info.project.name)) return; // retomada provada; o envio depende da fila (ver topo)

    await reaberta.getByRole('button', { name: /Continuar/ }).click();
    await reaberta.getByRole('button', { name: /Continuar/ }).click();
    await reaberta.getByRole('button', { name: 'Finalizar inspeção' }).click();
    await expect.poll(() => api.escritasEm('/v1/sst/inspecoes').length, { timeout: 15_000 }).toBe(1);
    expect((api.escritasEm('/v1/sst/inspecoes')[0].body as { evidencias: string[] }).evidencias).toEqual(['ev-0']);
  });

  test('foto gravada mas não incluída (app morreu preparando): é recuperada ao reabrir', async ({ page, context }) => {
    await new MockApi({ perfil: 'ADMINISTRATIVO' }).install(context);
    await loginPelaUi(page);
    await preencherAteFotos(page);
    await page.waitForTimeout(450);
    // Estado exato de um processo morto entre "guardar o original" e "pôr na tela".
    await page.evaluate(async (bytes) => {
      await new Promise<void>((ok, erro) => {
        const req = indexedDB.open('ces-field-sessao');
        req.onsuccess = () => {
          const tx = req.result.transaction('anexos', 'readwrite');
          tx.objectStore('anexos').put({
            id: 'bruto-1', chaveRascunho: 'inspecao:nova', idUsuario: 'u-encarregado-1', nome: 'bruto:interrompida.png',
            // Como bytes: funciona em todo navegador (e é o formato de reserva do app).
            tipo: 'image/png', criadoEm: Date.now(), bytes: new Uint8Array(bytes).buffer,
          });
          tx.oncomplete = () => ok();
          tx.onerror = () => erro(tx.error);
        };
      });
    }, [...PNG]);

    const reaberta = await matar(page, context);
    await expect(reaberta.getByText('1 foto recuperada.')).toBeVisible();
    await expect(reaberta.getByRole('list', { name: 'Fotos da inspeção' }).getByRole('listitem')).toHaveCount(1);
  });

  test('offline: registra no aparelho com a foto e envia uma única vez ao reconectar', async ({ page, context }, info) => {
    test.skip(SEM_BLOB_NA_FILA(info.project.name), 'fila de envio com Blob não funciona no WebKit para Windows');
    const api = new MockApi({ perfil: 'ADMINISTRATIVO' });
    await api.install(page);
    await loginPelaUi(page);
    await preencherAteFotos(page);
    await tirarFoto(page);
    api.offline = true;
    await context.setOffline(true);
    await page.getByRole('button', { name: /Continuar/ }).click();
    await page.getByRole('button', { name: 'Finalizar inspeção' }).click();
    await expect(page).toHaveURL(/\/inspecoes$/);
    expect(api.escritasEm('/v1/sst/inspecoes')).toHaveLength(0);

    api.offline = false;
    await context.setOffline(false);
    await page.evaluate(() => { window.dispatchEvent(new Event('offline')); });
    await page.waitForTimeout(300);
    await page.evaluate(() => { window.dispatchEvent(new Event('online')); });
    await expect.poll(() => api.escritasEm('/v1/sst/inspecoes').length, { timeout: 45_000 }).toBe(1);
    await page.waitForTimeout(1500);
    expect(api.escritasEm('/v1/sst/inspecoes')).toHaveLength(1);
    expect(api.escritasEm('/v1/sst/inspecoes/anexos')).toHaveLength(1);
  });

  test('salva sozinha: sem botão Salvar, sair aparece em Inspeções como não terminada e continua igual', async ({ page }) => {
    await new MockApi({ perfil: 'ADMINISTRATIVO' }).install(page);
    await loginPelaUi(page);
    await preencherAteFotos(page);
    await expect(page.getByRole('status', { name: 'Salvo automaticamente' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Salvar', exact: true })).toHaveCount(0);
    await page.waitForTimeout(450);
    await page.getByRole('button', { name: 'Fechar' }).click();
    await page.goto('/inspecoes');
    const naoTerminadas = page.getByRole('list', { name: 'Inspeções não terminadas' });
    await expect(naoTerminadas.getByRole('button')).toHaveCount(1);
    await naoTerminadas.getByRole('button').click();
    await expect(page.getByText(/Fotos · 4 de 5/)).toBeVisible();
  });

  test('sem foto não avança nem finaliza', async ({ page }) => {
    await new MockApi({ perfil: 'ADMINISTRATIVO' }).install(page);
    await loginPelaUi(page);
    await preencherAteFotos(page);
    await page.getByRole('button', { name: /Continuar/ }).click();
    await expect(page.getByText('Inclua ao menos uma foto.')).toBeVisible();
    await expect(page.getByText(/Fotos · 4 de 5/)).toBeVisible();
  });
});
