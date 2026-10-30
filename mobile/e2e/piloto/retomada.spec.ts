import { test, expect, Page, BrowserContext } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/**
 * Fase 3 — retomada exata nos fluxos do piloto (Hoje, Nova execução, Serviços).
 * "Processo morto" = fechar a página sem eventos de saída e reabrir pelo ícone
 * (start_url) no MESMO perfil do navegador (IndexedDB e localStorage preservados).
 */

test.use({
  geolocation: { latitude: -3.7319, longitude: -38.5267, accuracy: 12 },
  permissions: ['geolocation'],
});

const DIA = 24 * 60 * 60 * 1000;

async function matarEReabrir(page: Page, context: BrowserContext): Promise<Page> {
  await page.waitForTimeout(450); // > debounce de gravação (300 ms) — nada de evento de saída
  await page.close({ runBeforeUnload: false });
  const nova = await context.newPage();
  await nova.goto('/?origem=app');
  return nova;
}

/** Lê/escreve o banco de sessão direto (para corromper ou plantar formatos antigos). */
async function sessaoDb<T>(page: Page, fn: string, arg?: unknown): Promise<T> {
  return page.evaluate(([corpo, a]) => new Promise<T>((resolve, reject) => {
    const req = indexedDB.open('ces-field-sessao');
    req.onerror = () => reject(req.error);
    req.onsuccess = () => {
      const db = req.result;
      // eslint-disable-next-line no-new-func
      Promise.resolve(new Function('db', 'arg', corpo as string)(db, a)).then(r => { db.close(); resolve(r as T); }, reject);
    };
  }), [fn, arg] as const);
}

const PUT_SNAPSHOTS = `
  return new Promise((ok, erro) => {
    const tx = db.transaction('snapshots', 'readwrite');
    for (const r of arg) tx.objectStore('snapshots').put(r);
    tx.oncomplete = () => ok(true); tx.onerror = () => erro(tx.error);
  });`;

const CONTAR_RASCUNHOS = `
  return new Promise(ok => {
    const req = db.transaction('rascunhos').objectStore('rascunhos').getAllKeys();
    req.onsuccess = () => ok(req.result);
  });`;

/** Item da lista que está no topo da área rolável e quanto dele já passou. */
function itemNoTopo(page: Page): Promise<string> {
  return page.locator('main').evaluate(el => {
    const topo = el.getBoundingClientRect().top;
    const item = Array.from(el.querySelectorAll<HTMLElement>('[data-ancora]')).find(i => i.getBoundingClientRect().bottom > topo + 1);
    return item ? `${item.dataset['ancora']}@${Math.round(topo - item.getBoundingClientRect().top)}` : 'nenhum';
  });
}

async function comecarLancamento(page: Page, nota: string) {
  await page.goto('/campo/nova');
  await page.locator('#nota').fill(nota);
  await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
  await page.getByRole('radio', { name: /CORTE/ }).click();
  await expect(page.getByText('Local registrado')).toBeVisible();
  await page.getByRole('button', { name: /Continuar/ }).click();
  await page.getByRole('button', { name: /Religação/ }).click();
  await expect(page.getByText(/Serviços · 2 de 4/)).toBeVisible();
}

test.describe('Serviços: lista grande com filtro, período e rolagem', () => {
  test('processo morto com a lista rolada e filtrada: volta igual', async ({ page, context }, info) => {
    test.setTimeout(90_000);
    // O IndexedDB do WebKit para Windows (o do Playwright) grava ~15x mais devagar que o
    // Chromium: 2.000 itens levam >60 s só na carga. Medido e registrado no relatório da
    // Fase 3; o volume grande fica com o Android (alvo do campo) e com o teste manual no iPhone.
    const volume = info.project.name === 'ios' ? 400 : 2000;
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true, historico: volume }).install(context);
    await loginPelaUi(page, 'encarregado', 60_000);
    await page.getByRole('link', { name: 'Serviços' }).click();
    await page.getByRole('button', { name: 'Período: Últimos 30 dias' }).click();
    await page.getByRole('radio', { name: 'Todo o período' }).click();
    await page.getByRole('radio', { name: /Enviadas/ }).click();
    await expect(page.getByText('Nota 6000000')).toBeVisible();

    const rolagem = page.locator('main');
    await rolagem.evaluate(el => el.scrollTo(0, 6000));
    await page.waitForTimeout(400);
    const antes = await rolagem.evaluate(el => el.scrollTop);
    expect(antes).toBeGreaterThan(5000);

    const reaberta = await matarEReabrir(page, context);
    await expect(reaberta).toHaveURL(/\/campo$/);
    await expect(reaberta.getByRole('radio', { name: /Enviadas/ })).toHaveAttribute('aria-checked', 'true');
    await expect(reaberta.getByRole('button', { name: 'Período: Todo o período' })).toBeVisible();
    await expect.poll(() => reaberta.locator('main').evaluate(el => el.scrollTop), { timeout: 15_000 }).toBeGreaterThan(antes - 80);
  });

  test('abrir o detalhe e voltar mantém o mesmo item no topo', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true, historico: 300 }).install(context);
    await loginPelaUi(page);
    await page.goto('/campo');
    await expect(page.getByText('Nota 6000000')).toBeVisible();
    await page.locator('main').evaluate(el => el.scrollTo(0, 2500));
    await page.waitForTimeout(350);
    const antes = await itemNoTopo(page);

    // Toca numa linha que já está na tela (sem o Playwright rolar até ela).
    const id = antes.split('@')[0];
    await page.locator(`[data-ancora="${id}"] button`).dispatchEvent('click');
    await expect(page).toHaveURL(new RegExp(`/campo/${id}$`));
    await page.getByRole('button', { name: 'Voltar' }).click();
    await expect(page).toHaveURL(/\/campo$/);
    // Mesmo item no topo; o deslocamento pode variar 1–2 px por arredondamento subpixel.
    const [idAntes, pxAntes] = antes.split('@');
    await expect.poll(async () => {
      const [idDepois, pxDepois] = (await itemNoTopo(page)).split('@');
      return idDepois === idAntes && Math.abs(Number(pxDepois) - Number(pxAntes)) <= 4;
    }).toBe(true);
  });

  test('folha de período aberta continua aberta depois do processo morto', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true, historico: 20 }).install(context);
    await loginPelaUi(page);
    await page.goto('/campo');
    await page.getByRole('button', { name: /Período:/ }).click();
    await expect(page.getByRole('dialog', { name: 'Período' })).toBeVisible();
    const reaberta = await matarEReabrir(page, context);
    await expect(reaberta).toHaveURL(/\/campo$/);
    await expect(reaberta.getByRole('dialog', { name: 'Período' })).toBeVisible();
  });

  test('registro que não existe mais: tela segura, sem erro', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(page);
    await loginPelaUi(page);
    await page.goto('/campo/nao-existe-mais');
    await expect(page.getByText('Execução não encontrada')).toBeVisible();
    await page.getByRole('button', { name: 'Ver serviços' }).click();
    await expect(page).toHaveURL(/\/campo$/);
  });
});

test.describe('janelas de tempo fora do app', () => {
  for (const [rotulo, ms] of [['1 s', 1000], ['30 s', 30_000], ['10 min', 600_000], ['3 h', 3 * 3600_000]] as const) {
    test(`volta depois de ${rotulo}: mesmo passo e dados`, async ({ page, context }) => {
      await context.clock.install({ time: new Date('2026-10-08T09:00:00-03:00') });
      await new MockApi({ perfil: 'CAMPO', turnoAberto: true, expiraEmSegundos: 2 * DIA / 1000 }).install(context);
      await loginPelaUi(page);
      await comecarLancamento(page, '4440001');
      await page.waitForTimeout(450);
      await page.close({ runBeforeUnload: false });

      await context.clock.fastForward(ms);
      const reaberta = await context.newPage();
      await reaberta.goto('/?origem=app');
      await expect(reaberta.getByText(/Serviços · 2 de 4/)).toBeVisible();
      await expect(reaberta.getByRole('button', { name: /Religação/ })).toHaveAttribute('aria-pressed', 'true');
    });
  }

  test('volta depois de 13 h: abre em Hoje, mas o lançamento continua a um toque', async ({ page, context }) => {
    await context.clock.install({ time: new Date('2026-10-08T07:00:00-03:00') });
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true, expiraEmSegundos: 2 * DIA / 1000 }).install(context);
    await loginPelaUi(page);
    await comecarLancamento(page, '4440013');
    await page.waitForTimeout(450);
    await page.close({ runBeforeUnload: false });

    await context.clock.fastForward(13 * 3600_000);
    const reaberta = await context.newPage();
    await reaberta.goto('/?origem=app');
    await expect(reaberta).toHaveURL(/\/hoje$/);
    await expect(reaberta.getByText('Nota 4440013 · passo 2 de 4')).toBeVisible();
    await reaberta.getByRole('button', { name: 'Continuar' }).click();
    await expect(reaberta.getByText(/Serviços · 2 de 4/)).toBeVisible();
    await expect(reaberta.getByRole('button', { name: /Religação/ })).toHaveAttribute('aria-pressed', 'true');
  });
});

test.describe('falhas do armazenamento local', () => {
  test('retrato da sessão corrompido: abre em Hoje sem erro e o rascunho continua lá', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(context);
    await loginPelaUi(page);
    await comecarLancamento(page, '3330001');
    await page.waitForTimeout(450);
    await sessaoDb(page, PUT_SNAPSHOTS, [
      { chave: 'atual', dados: { schema: 2, rota: '/campo/nova', checksum: 'quebrado' } },
      { chave: 'anterior', dados: '{"isto nao e um objeto' },
    ]);
    const erros: string[] = [];
    const reaberta = await (async () => {
      await page.close({ runBeforeUnload: false });
      const p = await context.newPage();
      p.on('pageerror', e => erros.push(e.message));
      await p.goto('/?origem=app');
      return p;
    })();
    await expect(reaberta).toHaveURL(/\/hoje$/);
    await expect(reaberta.getByText('Nota 3330001 · passo 2 de 4')).toBeVisible();
    expect(erros).toEqual([]);
  });

  test('retrato no formato antigo (schema v1) é migrado e restaurado', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(context);
    await loginPelaUi(page);
    await page.goto('/material');
    await page.waitForTimeout(450);
    await sessaoDb(page, PUT_SNAPSHOTS, [
      { chave: 'atual', dados: { versao: 1, ts: Date.now() - 60_000, usuario: 'u-encarregado-1', url: '/solicitacoes/estoque', ui: {} } },
    ]);
    await page.close({ runBeforeUnload: false });
    const reaberta = await context.newPage();
    await reaberta.goto('/?origem=app');
    await expect(reaberta).toHaveURL(/\/solicitacoes\/estoque$/);
  });
});

test.describe('concorrência', () => {
  test('duas abas no mesmo lançamento: a última vence e a outra versão é preservada', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(context);
    await loginPelaUi(page);
    await page.goto('/campo/nova');
    await page.locator('#nota').fill('1110001');
    await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
    await page.getByRole('radio', { name: /CORTE/ }).click();
    await page.waitForTimeout(450);

    const outra = await context.newPage();
    await outra.goto('/campo/nova');
    await expect(outra.locator('#nota')).toHaveValue('1110001');
    await outra.locator('#nota').fill('2220002');
    await outra.getByRole('button', { name: /^Tipo de serviço/ }).click();
    await outra.getByRole('radio', { name: /CORTE/ }).click();
    await outra.waitForTimeout(450);

    // A primeira aba não viu a mudança e continua digitando.
    await page.locator('#nota').fill('1110009');
    await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
    await page.getByRole('radio', { name: /CORTE/ }).click();
    await page.waitForTimeout(450);

    const chaves = await sessaoDb<string[]>(page, CONTAR_RASCUNHOS);
    expect(chaves).toContain('execucao:nova');
    expect(chaves.some(c => c.startsWith('execucao:nova#conflito-'))).toBe(true);

    await outra.close();
    await page.close({ runBeforeUnload: false });
    const reaberta = await context.newPage();
    await reaberta.goto('/campo/nova');
    await expect(reaberta.locator('#nota')).toHaveValue('1110009');
  });
});

test.describe('Hoje', () => {
  test('folha de envios aberta com item na fila: volta aberta depois do processo morto', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(context);
    await loginPelaUi(page);
    // Lança sem acesso ao servidor: o item fica na fila.
    api.offline = true;
    await page.goto('/campo/nova');
    await page.locator('#nota').fill('9090909');
    await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
    await page.getByRole('radio', { name: /CORTE/ }).click();
    await expect(page.getByText('Local registrado')).toBeVisible();
    for (let i = 0; i < 3; i++) await page.getByRole('button', { name: /Continuar/ }).click();
    await page.getByRole('button', { name: 'Lançar execução' }).click();
    await page.getByRole('button', { name: 'Sim, lançar' }).click();
    await page.getByRole('link', { name: /Hoje/ }).click();
    await page.getByRole('button', { name: /aguardando envio/ }).click();
    const folha = page.getByRole('dialog', { name: 'Envios' });
    await expect(folha).toBeVisible();
    await expect(folha.getByText('Nota 9090909')).toBeVisible();

    const reaberta = await matarEReabrir(page, context);
    await expect(reaberta).toHaveURL(/\/hoje$/);
    await expect(reaberta.getByRole('dialog', { name: 'Envios' })).toBeVisible();
    await expect(reaberta.getByRole('dialog', { name: 'Envios' }).getByText('Nota 9090909')).toBeVisible();
    await reaberta.keyboard.press('Escape');
    await expect(reaberta.getByRole('dialog', { name: 'Envios' })).toBeHidden();
  });

  test('voltar do Android fecha a folha em vez de sair da tela', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: true }).install(page);
    await loginPelaUi(page);
    await page.getByRole('button', { name: /Conexão/ }).click();
    await expect(page.getByRole('dialog', { name: 'Envios' })).toBeVisible();
    await page.goBack();
    await expect(page.getByRole('dialog', { name: 'Envios' })).toBeHidden();
    await expect(page).toHaveURL(/\/hoje$/);
  });
});
