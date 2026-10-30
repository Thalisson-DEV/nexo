import { test } from '@playwright/test';
import { MockApi, loginPelaUi, IDS } from './mock-api';

/** Reprodução: execução com vários serviços de nome longo em Android pequeno. Rodar com CES_SHOTS=1. */
test.skip(!process.env['CES_SHOTS'], 'só sob demanda');
test.use({ viewport: { width: 360, height: 780 } });

const ACOES = [
  { id: 'x-1', codigo: '0101.002', nome: 'SUBSTITUIÇÃO DE MEDIDOR MONOFÁSICO COM RELIGAÇÃO EM BAIXA TENSÃO', ativo: true, valor: 87.4 },
  { id: 'x-2', codigo: '0203.117', nome: 'INSPEÇÃO DE UNIDADE CONSUMIDORA COM IRREGULARIDADE CONSTATADA', ativo: true, valor: 132.9 },
  { id: 'x-3', codigo: '0305.044', nome: 'CORTE NO POSTE', ativo: true, valor: 45 },
];

test('execução com serviços longos', async ({ page }, info) => {
  test.skip(info.project.name !== 'android');
  await new MockApi({ perfil: 'CAMPO', turnoAberto: true, historico: 2 }).install(page);
  await page.route('**/api/v1/contratos/acoes**', r => r.fulfill({ json: { content: ACOES, page: { size: 50, number: 0, totalElements: 3, totalPages: 1 } } }));
  await page.route('**/api/v1/execucoes-comerciais/minha-equipe**', r => r.fulfill({ json: { content: [{
    id: 'longa-1', referenciaNota: '20261008001', idTurno: IDS.turno, equipe: { id: IDS.equipe },
    dataLancamento: new Date().toISOString(), statusExecucao: 'CONCLUIDO',
    latitude: -3.73, longitude: -38.52, acoes: ACOES.map(a => ({ acao: { id: a.id } })), materiais: [], valorTotalFaturado: 265.3,
  }], page: { size: 50, number: 0, totalElements: 1, totalPages: 1 } } }));
  await loginPelaUi(page);
  const pasta = '../docs/mobile/validacao/servicos-longos';
  await page.goto('/hoje'); await page.waitForTimeout(800); await page.screenshot({ path: `${pasta}/hoje.png`, fullPage: true });
  await page.goto('/campo'); await page.waitForTimeout(800); await page.screenshot({ path: `${pasta}/lista.png`, fullPage: true });
  await page.goto('/campo/longa-1'); await page.waitForTimeout(800); await page.screenshot({ path: `${pasta}/detalhe.png`, fullPage: true });
});
