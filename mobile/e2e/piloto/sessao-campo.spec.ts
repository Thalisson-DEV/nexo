import { test, expect, Page, BrowserContext } from '@playwright/test';
import { MockApi, fakeJwt, loginPelaUi } from '../support/mock-api';

/**
 * Garantia de campo: quem entrou NÃO volta para a tela de login sem tocar em "Sair".
 *
 * A Concessionária não libera o 4G das equipes: em campo o aparelho fica sem internet, ou com
 * "rede" sem saída (4G bloqueado, Wi-Fi sem internet) — `navigator.onLine` diz `true` e
 * nada responde. O token de acesso vence no bolso. Nada disso pode levar ao login, que
 * não funcionaria sem conexão: o encarregado tem de abrir o app e lançar.
 */

test.use({
  geolocation: { latitude: -3.7319, longitude: -38.5267, accuracy: 12 },
  permissions: ['geolocation'],
});

/** Planta um token já vencido, como o que fica no aparelho depois de um dia em campo. */
async function vencerToken(page: Page): Promise<void> {
  const vencido = fakeJwt('CAMPO', -24 * 3600);
  await page.evaluate((t) => localStorage.setItem('ces_auth_token', t), vencido);
}

/** Fecha o app sem eventos de saída e abre de novo pelo ícone (start_url). */
async function fecharEReabrir(page: Page, context: BrowserContext): Promise<Page> {
  await page.waitForTimeout(450);
  await page.close({ runBeforeUnload: false });
  const nova = await context.newPage();
  await nova.goto('/?origem=app');
  return nova;
}

async function semSplash(page: Page): Promise<void> {
  await expect(page.locator('app-splash-screen > div')).toHaveCount(0, { timeout: 15_000 });
}

test.describe('Garantia de campo: sem login sem rede', () => {
  // Sem internet NENHUMA (modo avião) depende do service worker: ver e2e/sw/sessao-offline.spec.ts.

  test('token vencido + "rede" sem saída (4G bloqueado/Wi-Fi sem internet): não pede login', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(context);
    await loginPelaUi(page);

    // O sistema diz online, mas nenhuma requisição sai: é o caso que derrubava as equipes.
    await vencerToken(page);
    api.offline = true;

    const reaberta = await fecharEReabrir(page, context);
    await semSplash(reaberta);
    await expect(reaberta).toHaveURL(/\/hoje$/);

    await reaberta.goto('/campo/nova');
    await reaberta.locator('#nota').fill('7770001');
    await reaberta.getByRole('button', { name: /^Tipo de serviço/ }).click();
    await reaberta.getByRole('radio', { name: /CORTE/ }).click();
    await reaberta.waitForTimeout(2_000);
    await expect(reaberta).toHaveURL(/\/campo\/nova$/);
    await expect(reaberta.locator('#nota')).toHaveValue('7770001');
  });

  test('a sessão sobrevive a vários dias fechada: reabrir não pede login', async ({ page, context }) => {
    await context.clock.install({ time: new Date('2026-10-08T07:00:00-03:00') });
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true, expiraEmSegundos: 3600 });
    await api.install(context);
    await loginPelaUi(page);
    await page.waitForTimeout(450);
    await page.close({ runBeforeUnload: false });

    api.offline = true;
    await context.clock.fastForward(5 * 24 * 3600_000);
    const reaberta = await context.newPage();
    await reaberta.goto('/?origem=app');
    await semSplash(reaberta);
    await expect(reaberta).not.toHaveURL(/\/login/);
  });

  test('o Android reabre o app na página de login com a sessão válida: segue para dentro, sem pedir senha', async ({ page, context }) => {
    // Em campo o app instalado volta na última página carregada, não pelo ícone. Se ela
    // foi o login, toda reabertura mostrava o formulário com a sessão ainda válida.
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(context);
    await loginPelaUi(page);
    await page.waitForTimeout(450);
    await page.close({ runBeforeUnload: false });

    const reaberta = await context.newPage();
    await reaberta.goto('/login');
    await semSplash(reaberta);
    await expect(reaberta).toHaveURL(/\/hoje$/);
    await expect(reaberta.locator('form')).toHaveCount(0);
    expect(api.escritasEm('/v1/auth/login')).toHaveLength(1);
  });

  test('reaberto no login vindo de uma tela: volta para ela', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(context);
    await loginPelaUi(page);
    await page.waitForTimeout(450);
    await page.close({ runBeforeUnload: false });

    const reaberta = await context.newPage();
    await reaberta.goto('/login?volta=%2Fcampo');
    await semSplash(reaberta);
    await expect(reaberta).toHaveURL(/\/campo$/);
  });

  test('ao voltar a internet, renova sozinho e envia o que ficou na fila, sem login', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(context);
    await loginPelaUi(page);

    await vencerToken(page);
    api.sessaoExpirada = true; // o servidor recusa o token antigo
    const reaberta = await fecharEReabrir(page, context);
    await semSplash(reaberta);

    await expect.poll(() => api.escritasEm('/v1/auth/refresh').length, { timeout: 15_000 }).toBeGreaterThan(0);
    await expect(reaberta).not.toHaveURL(/\/login/);
    await expect(reaberta.getByText('Entre novamente para enviar')).toHaveCount(0);
  });

  test('sessão encerrada no servidor: fica na tela, avisa e só vai ao login se a pessoa tocar', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(context);
    await loginPelaUi(page);

    await vencerToken(page);
    api.sessaoExpirada = true;
    api.sessaoEncerrada = true;
    const reaberta = await fecharEReabrir(page, context);
    await semSplash(reaberta);

    await expect(reaberta.getByText('Entre novamente para enviar')).toBeVisible({ timeout: 15_000 });
    await expect(reaberta).toHaveURL(/\/hoje$/);
    await reaberta.goto('/campo/nova');
    await expect(reaberta.locator('#nota')).toBeVisible();
    await expect(reaberta).not.toHaveURL(/\/login/);
  });

  test('só "Sair" leva ao login', async ({ page, context }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: false });
    await api.install(context);
    await loginPelaUi(page);

    await page.goto('/perfil');
    await page.getByRole('button', { name: /Sair da conta/ }).click();
    await page.getByRole('dialog', { name: 'Sair da conta?' }).getByRole('button', { name: 'Sair', exact: true }).click();
    await expect(page).toHaveURL(/\/login/);
    await expect.poll(() => api.escritasEm('/v1/auth/logout').length).toBe(1);
  });
});
