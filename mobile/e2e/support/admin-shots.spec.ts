import { test } from '@playwright/test';
import { MockApi, loginPelaUi } from './mock-api';

/** Capturas das telas do perfil administrativo (SST) para revisão visual. Rodar com CES_SHOTS=1. */
test.skip(!process.env['CES_SHOTS'], 'só sob demanda');

test('telas do administrativo', async ({ page }, info) => {
  test.skip(info.project.name !== 'android');
  const api = new MockApi({ perfil: 'ADMINISTRATIVO' });
  await api.install(page);
  await loginPelaUi(page);
  const pasta = '../docs/mobile/validacao/admin';
  const foto = async (nome: string) => { await page.waitForTimeout(700); await page.screenshot({ path: `${pasta}/${nome}.png`, fullPage: true }); };

  await page.goto('/hoje'); await foto('01-hoje');
  await page.goto('/inspecoes'); await foto('02-inspecoes');
  await page.goto('/inspecoes/nova'); await foto('03-inspecao-nova');
  await page.goto('/desvios'); await foto('04-desvios');
  await page.getByRole('button', { name: /Usa capacete com jugular/ }).click(); await foto('05-desvio-tratar');
  await page.goto('/desvios'); await page.getByRole('button', { name: /Filtros|Filtrar/ }).click(); await foto('06-desvios-filtros');
  await page.goto('/perfil'); await foto('07-conta');
});
