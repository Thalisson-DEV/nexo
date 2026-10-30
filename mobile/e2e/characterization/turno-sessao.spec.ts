import { test, expect } from '@playwright/test';
import { MockApi, loginPelaUi, IDS } from '../support/mock-api';

test.use({
  geolocation: { latitude: -3.7319, longitude: -38.5267, accuracy: 12 },
  permissions: ['geolocation'],
});

test.describe('turno (comportamento atual)', () => {
  test('abrir turno sem alterar a composição envia só a equipe (veículo/colaboradores nulos)', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: false });
    await api.install(page);
    await loginPelaUi(page);

    await page.getByRole('link', { name: /Abrir turno/i }).click();
    await expect(page).toHaveURL(/\/turno\/abrir$/);
    await page.getByRole('button', { name: 'Abrir turno' }).click();
    await expect(page).toHaveURL(/\/hoje$/);

    const [abertura] = api.escritasEm('/v1/turnos/abrir');
    expect(abertura.body).toEqual({
      idEquipe: IDS.equipe,
      idVeiculo: null,
      idsColaboradores: null,
    });
  });

  test('abrir turno exige rede (botão desabilitado offline)', async ({ page, context }) => {
    await new MockApi({ perfil: 'CAMPO', turnoAberto: false }).install(page);
    await loginPelaUi(page);
    await page.getByRole('link', { name: /Abrir turno/i }).click();
    await expect(page).toHaveURL(/\/turno\/abrir$/);
    await context.setOffline(true);
    await expect(page.getByRole('button', { name: 'Abrir turno' })).toBeDisabled();
  });
});

test.describe('sessão encerrada no servidor', () => {
  test('401 no meio do fluxo: fica na tela; ao entrar de novo pelo aviso, volta direto para ela', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO', turnoAberto: true });
    await api.install(page);
    await loginPelaUi(page);

    await page.goto('/campo/nova');
    await page.locator('#nota').fill('9990001');

    await page.getByRole('button', { name: /^Tipo de serviço/ }).click();
    await page.getByRole('radio', { name: /CORTE/ }).click();
    // A sessão acabou no servidor enquanto o app estava em segundo plano.
    // A volta da rede dispara a sincronização automática, que recebe 401 e não consegue renovar.
    api.sessaoExpirada = true;
    api.sessaoEncerrada = true;
    // (eventos explícitos: o WebKit do Playwright não os dispara com setOffline)
    // Em ticks separados: o effect do Angular só reage se enxergar a transição.
    await page.evaluate(() => window.dispatchEvent(new Event('offline')));
    await page.waitForTimeout(300);
    await page.evaluate(() => window.dispatchEvent(new Event('online')));

    // NÃO sai da tela: em campo o login não funcionaria sem sinal. Avisa e deixa seguir.
    await expect(page.getByText('Entre novamente para enviar')).toBeVisible({ timeout: 15_000 });
    await expect(page).toHaveURL(/\/campo\/nova$/);
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    await expect(page).toHaveURL(/\/login\?volta=%2Fcampo%2Fnova$/, { timeout: 15_000 });
    await page.locator('#login').fill('encarregado');
    await page.locator('#senha').fill('senha-secreta');
    await page.getByRole('button', { name: 'Entrar' }).click();

    // Mesma conta e dados já no aparelho: sem tela de carga, direto para onde estava.
    await expect(page).toHaveURL(/\/campo\/nova$/, { timeout: 20_000 });
  });
});
