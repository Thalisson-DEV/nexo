import { test, expect } from '@playwright/test';
import { MockApi, loginPelaUi, IDS } from '../support/mock-api';

/**
 * Regressão (achado em aparelho real): no detalhe de uma execução com vários serviços
 * de nome longo, o grupo encolhia na coluna flex e cortava os últimos serviços.
 */
test.use({ viewport: { width: 360, height: 640 } });

const ACOES = [
  { id: 'x-1', codigo: '0101.002', nome: 'SUBSTITUIÇÃO DE MEDIDOR MONOFÁSICO COM RELIGAÇÃO EM BAIXA TENSÃO', ativo: true, valor: 87.4 },
  { id: 'x-2', codigo: '0203.117', nome: 'INSPEÇÃO DE UNIDADE CONSUMIDORA COM IRREGULARIDADE CONSTATADA', ativo: true, valor: 132.9 },
  { id: 'x-3', codigo: '0305.044', nome: 'CORTE NO POSTE COM RETIRADA DE RAMAL E LACRE DO MEDIDOR', ativo: true, valor: 45 },
  { id: 'x-4', codigo: '0409.300', nome: 'RELIGAÇÃO DE URGÊNCIA FORA DO HORÁRIO COMERCIAL', ativo: true, valor: 60 },
];

test('detalhe mostra todos os serviços longos, sem cortar, e a página rola', async ({ page }) => {
  await new MockApi({ perfil: 'CAMPO', turnoAberto: true, historico: 0 }).install(page);
  await page.route('**/api/v1/contratos/acoes**', r => r.fulfill({ json: { content: ACOES, page: { size: 50, number: 0, totalElements: 4, totalPages: 1 } } }));
  await page.route('**/api/v1/execucoes-comerciais/minha-equipe**', r => r.fulfill({ json: { content: [{
    id: 'longa-1', referenciaNota: '20261008001', idTurno: IDS.turno, equipe: { id: IDS.equipe },
    dataLancamento: new Date().toISOString(), statusExecucao: 'CONCLUIDO',
    latitude: -3.73, longitude: -38.52, acoes: ACOES.map(a => ({ acao: { id: a.id } })), materiais: [], valorTotalFaturado: 325.3,
  }], page: { size: 50, number: 0, totalElements: 1, totalPages: 1 } } }));
  await loginPelaUi(page);
  await page.goto('/campo/longa-1');
  const grupo = page.getByRole('list', { name: 'Serviços' });
  await expect(grupo.getByRole('listitem')).toHaveCount(4);
  const caixa = (await grupo.boundingBox())!;
  for (const item of await grupo.getByRole('listitem').all()) {
    const b = (await item.boundingBox())!;
    expect(b.y + b.height).toBeLessThanOrEqual(caixa.y + caixa.height + 1);
  }
  await grupo.getByText('RELIGAÇÃO DE URGÊNCIA FORA DO HORÁRIO COMERCIAL').scrollIntoViewIfNeeded();
  await expect(grupo.getByText('Cód. 0409.300')).toBeInViewport();
});
