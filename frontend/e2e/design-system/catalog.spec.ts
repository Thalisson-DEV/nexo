import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.clear());
  await page.goto('/__design-system');
  await expect(page.getByRole('heading', { name: /Uma linguagem única/i })).toBeVisible({
    timeout: 15_000,
  });
});

test('renderiza as famílias principais sem erros de console', async ({ page }) => {
  const consoleErrors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') consoleErrors.push(message.text());
  });

  await expect(page.getByRole('heading', { name: 'Ações' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Campos' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Status' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Estados de conteúdo' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Editar registro' })).toBeVisible();

  expect(consoleErrors).toEqual([]);
});

test('alterna tema e densidade mantendo foco visível', async ({ page }) => {
  const themeButton = page.getByRole('button', { name: 'Usar tema escuro' });
  await themeButton.focus();
  await expect(themeButton).toBeFocused();
  await themeButton.click();

  await expect(page.locator('html')).toHaveClass(/app-dark/);
  await expect(page.getByRole('button', { name: 'Usar tema claro' })).toBeVisible();

  await page.getByRole('button', { name: 'Densidade confortável' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-ces-density', 'comfortable');
});

test('abre o diálogo compartilhado durante um processamento', async ({ page }) => {
  await page.getByRole('button', { name: 'Ver processamento modal' }).click();
  const dialog = page.getByRole('dialog', { name: 'Processando lote' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Confirmando operação')).toBeVisible();
  await expect(dialog.getByRole('button', { name: /fechar/i })).toHaveCount(0);
  await expect(dialog).toBeHidden({ timeout: 5_000 });
});

test('mantém opções e estado vazio do autocomplete no tema escuro', async ({ page }) => {
  await page.getByRole('button', { name: 'Usar tema escuro' }).click();
  const collaborator = page.getByRole('combobox', { name: 'Colaborador' });
  await collaborator.fill('An');

  const option = page.getByRole('option', { name: 'Ana Ribeiro' });
  await expect(option).toBeVisible();

  const colors = await page.locator('.p-autocomplete-overlay').evaluate((overlay) => {
    const surface = document.createElement('div');
    surface.style.background = 'var(--ces-surface)';
    document.body.appendChild(surface);
    const result = {
      overlayBackground: getComputedStyle(overlay).backgroundColor,
      expectedBackground: getComputedStyle(surface).backgroundColor,
      optionColor: getComputedStyle(overlay.querySelector('.p-autocomplete-option')!).color,
    };
    surface.remove();
    return result;
  });

  expect(colors.overlayBackground).toBe(colors.expectedBackground);
  expect(colors.optionColor).not.toBe('rgb(0, 0, 0)');

  await option.click();
  await expect(collaborator).toHaveValue('Ana Ribeiro');

  await collaborator.fill('zz');
  const empty = page.locator('.p-autocomplete-empty-message');
  await expect(empty).toContainText('Nenhum resultado encontrado.');
  expect(await empty.evaluate((element) => getComputedStyle(element).color)).not.toBe(
    'rgb(0, 0, 0)',
  );
});

test('abre select no overlay global e permite selecionar primária', async ({ page }) => {
  const primaryColor = page.getByRole('combobox', { name: 'blue' });
  await primaryColor.click();
  await expect(page.getByRole('option', { name: 'emerald' })).toBeVisible();
  await page.getByRole('option', { name: 'emerald' }).click();
  await expect(page.getByRole('combobox', { name: 'emerald' })).toContainText('emerald');
});

test('não possui violações axe sérias ou críticas', async ({ page }) => {
  const results = await new AxeBuilder({ page }).analyze();
  const severe = results.violations.filter(
    (violation) => violation.impact === 'critical' || violation.impact === 'serious',
  );

  expect(severe).toEqual([]);
});

test('mantém baseline visual do catálogo', async ({ page }, testInfo) => {
  await expect(page).toHaveScreenshot(
    testInfo.project.name.includes('mobile') ? 'catalog-mobile.png' : 'catalog-desktop.png',
    { fullPage: true },
  );
});
