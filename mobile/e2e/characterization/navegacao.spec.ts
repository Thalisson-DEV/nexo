import { test, expect } from '@playwright/test';
import { MockApi, loginPelaUi } from '../support/mock-api';

/**
 * Characterization: comportamento ATUAL de autenticação e navegação por perfil.
 * Estes testes são a rede de segurança do redesenho — a UI nova precisa manter
 * as mesmas regras (destinos por perfil, guards, redirecionamentos).
 */

test.describe('navegação e permissões (comportamento atual)', () => {
  test('sem sessão, qualquer rota protegida cai no login', async ({ page }) => {
    await new MockApi().install(page);
    await page.goto('/campo/nova');
    await expect(page).toHaveURL(/\/login$/);
    await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible();
  });

  test('encarregado de campo: login → sincronização → início, com 4 destinos', async ({ page }) => {
    const api = new MockApi({ perfil: 'CAMPO' });
    await api.install(page);
    await loginPelaUi(page);

    // Navegação aprovada na Fase 1: pedidos, transferências e saldo ficam dentro de Material.
    const nav = page.getByRole('navigation', { name: 'Navegação principal' });
    await expect(nav.getByRole('link')).toHaveText([/Hoje/, /Serviços/, /Material/, /Conta/]);
    // A carga inicial consulta o turno, a composição e os catálogos de material.
    expect(api.escritasEm('/v1/auth/login')).toHaveLength(1);
  });

  test('perfil administrativo: destinos de SST e sem logística', async ({ page }) => {
    const api = new MockApi({ perfil: 'ADMINISTRATIVO' });
    await api.install(page);
    await loginPelaUi(page);

    const nav = page.getByRole('navigation', { name: 'Navegação principal' });
    await expect(nav.getByRole('link')).toHaveText([/Hoje/, /Inspeções/, /Desvios/, /Conta/]);
  });

  test('campo não acessa inspeções nem por URL direta', async ({ page }) => {
    await new MockApi({ perfil: 'CAMPO' }).install(page);
    await loginPelaUi(page);
    await page.goto('/inspecoes');
    await expect(page).toHaveURL(/\/hoje$/);
  });

  test('administrativo não acessa o lançamento de campo', async ({ page }) => {
    await new MockApi({ perfil: 'ADMINISTRATIVO' }).install(page);
    await loginPelaUi(page);
    await page.goto('/campo/nova');
    await expect(page).toHaveURL(/\/hoje$/);
  });

  test('rota desconhecida volta para o início', async ({ page }) => {
    await new MockApi().install(page);
    await loginPelaUi(page);
    await page.goto('/nao-existe');
    await expect(page).toHaveURL(/\/hoje$/);
  });
});
