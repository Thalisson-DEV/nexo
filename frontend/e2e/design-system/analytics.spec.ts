import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => localStorage.clear());
  await page.goto('/__design-system/analitico');
  await expect(page.getByRole('heading', { name: 'Analítico e drill-down' })).toBeVisible({
    timeout: 15_000,
  });
});

test('detalha uma categoria do gráfico pelo caminho de teclado e fecha o painel', async ({ page }) => {
  await page.getByRole('button', { name: 'Ver tabela de dados de Inspeções por mês' }).click();
  const detalhar = page.getByRole('button', { name: /^Detalhar / }).first();
  await detalhar.focus();
  await page.keyboard.press('Enter');

  const painel = page.locator('.p-drawer');
  await expect(painel.getByRole('heading', { level: 2 })).toContainText('Inspeções em');
  // Criado já aberto: precisa estar anexado ao body e acima da página (z-index), não sob ela.
  const camada = await painel.evaluate((el) => ({ noBody: el.parentElement === document.body, z: Number(getComputedStyle(el).zIndex) }));
  expect(camada.noBody).toBe(true);
  expect(camada.z).toBeGreaterThan(0);
  await page.keyboard.press('Escape');
  await expect(painel).toBeHidden();
});

test('ordena, busca e pagina a tabela analítica', async ({ page, isMobile }) => {
  const busca = page.getByRole('searchbox', { name: 'Pesquisar' });
  await busca.fill('joao');
  if (isMobile) {
    await expect(page.locator('.ces-data-table__card')).toHaveCount(1);
    await expect(page.locator('.ces-data-table__card')).toContainText('João Teles');
    return;
  }
  await expect(page.locator('.ces-data-table__table tbody tr')).toHaveCount(1);
  await busca.fill('');

  const realizado = page.getByRole('columnheader', { name: /Realizado/ });
  await realizado.getByRole('button').click();
  await expect(realizado).toHaveAttribute('aria-sort', 'ascending');
  await expect(page.locator('.ces-data-table__table tbody tr').first()).toContainText('Luís Pires');

  await expect(page.locator('.ces-data-table__range')).toContainText('1–10 de 12');
  await page.getByRole('button', { name: 'Próxima página' }).click();
  await expect(page.locator('.ces-data-table__range')).toContainText('11–12 de 12');
});

test('em telas compactas mostra cards, ordenação por seletor e folha inferior', async ({ page, isMobile }) => {
  test.skip(!isMobile, 'comportamento exclusivo de telas compactas');
  await expect(page.locator('.ces-data-table__table')).toHaveCount(0);
  await expect(page.locator('.ces-data-table__card')).toHaveCount(10);
  await page.getByRole('button', { name: 'Carregar mais' }).click();
  await expect(page.locator('.ces-data-table__card')).toHaveCount(12);

  await page.getByLabel('Ordenar por').selectOption({ label: 'Inspetor ↓' });
  await expect(page.locator('.ces-data-table__card').first()).toContainText('Luís Pires');

  await page.getByRole('button', { name: 'Ver histórico: Luís Pires' }).click();
  const painel = page.locator('.p-drawer');
  await expect(painel).toHaveClass(/p-drawer-bottom/);
  await expect(painel.getByRole('heading', { level: 2 })).toContainText('Histórico de Luís Pires');
  await painel.getByRole('button', { name: 'Fechar', exact: true }).click();
  await expect(painel).toBeHidden();
});

test('não possui violações axe sérias ou críticas nos temas claro e escuro', async ({ page }) => {
  // Sem transições: o axe não pode ler cores no meio da troca de tema.
  await page.addStyleTag({ content: '*, *::before, *::after { transition: none !important; }' });
  for (const tema of ['claro', 'escuro']) {
    if (tema === 'escuro') await page.getByRole('button', { name: 'Usar tema escuro' }).click();
    const results = await new AxeBuilder({ page }).analyze();
    const severe = results.violations.filter(
      (violation) => violation.impact === 'critical' || violation.impact === 'serious',
    );
    expect(severe, `tema ${tema}`).toEqual([]);
  }
});
