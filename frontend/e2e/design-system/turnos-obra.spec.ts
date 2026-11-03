import { Page, expect, test } from '@playwright/test';

/** Telas de análise dos turnos de Obras, com a API simulada: quem lança é a equipe, então não há ação de escrita. */

const TURNO = '55555555-5555-4555-8555-555555555555';
const EQUIPE = '22222222-2222-4222-8222-222222222222';
const PROGRAMACAO = '44444444-4444-4444-8444-444444444444';
const OBRA = '11111111-1111-4111-8111-111111111111';

const linha = {
  id: TURNO, idEquipe: EQUIPE, codigoEquipe: 'FIC-CON-01', tipoEquipe: 'CONSTRUCAO', baseOperacional: 'JUAZEIRO', placa: 'ABC1D23', status: 'FECHADO',
  dataInicio: '2026-10-30T07:30:00-03:00', dataFim: '2026-10-30T17:00:00-03:00', duracaoMinutos: 570, abertoDesdeOutroDia: false, encerradoAutomaticamente: false,
  abertoPor: 'Encarregado', membros: 4, substitutos: 1, realizado: 900, metaDiaria: 1000, percentualMeta: 90, bateuMeta: false, semRelatorio: true, ocorrencias: 1, aprs: 1, aprsEmRascunho: 0, servicos: 2, obras: ['7000001']
};

const FORMULARIO = {
  local: 'Poste P1', riscos: ['Choque elétrico'], outroRisco: null, medidas: ['Sinalização e isolamento da área'], medidasAdicionais: null,
  verificacoes: [{ pergunta: 'A área está sinalizada e isolada?', resposta: 'SIM' }], idsParticipantes: ['c1', 'c2']
};

const detalheTurno = {
  ...linha, idVeiculo: 'v1',
  composicao: [
    { idColaborador: 'c1', nome: 'ANA FICTICIA', funcao: 'ENCARREGADO', lider: true, substituto: false },
    { idColaborador: 'c2', nome: 'BRUNO FICTICIO', funcao: 'ELETRICISTA', lider: false, substituto: true }
  ],
  programacoes: [{ id: PROGRAMACAO, numero: 1687, idObra: OBRA, numeroObra: '7000001', tituloObra: 'Obra de teste', descricao: 'Implantar rede', inicio: '30/10/2026', fim: '31/10/2026', situacao: 'PROGRAMADA', valor: 300, itens: 1, itensConcluidos: 0, liberacao: 'LIBERADA' }],
  atividades: [], apontamentos: [], fotos: [], ocorrencias: [],
  aprs: [{ id: 'a1', atividade: 'Implantar rede', idObra: OBRA, numeroObra: '7000001', idProgramacao: PROGRAMACAO, idResponsavel: 'c1', responsavel: 'ANA FICTICIA', status: 'CONCLUIDA', dataConclusao: '2026-10-30T07:40:00-03:00', usuario: 'Encarregado', data: '2026-10-30T07:35:00-03:00', anexos: [], formulario: FORMULARIO }],
  relatorio: null
};

const turnoSemApr = {
  ...detalheTurno,
  aprs: [],
  programacoes: detalheTurno.programacoes.map((p) => ({ ...p, liberacao: 'PENDENTE' }))
};

const turnoImpedido = {
  ...detalheTurno,
  aprs: [],
  ocorrencias: [{ id: 'o1', tipo: 'CLIMA', descricao: 'Chuva forte desde a chegada', idObra: OBRA, numeroObra: '7000001', impedeExecucao: true, usuario: 'Encarregado', data: '2026-10-30T08:00:00-03:00' }],
  programacoes: detalheTurno.programacoes.map((p) => ({ ...p, liberacao: 'IMPEDIDA' }))
};

const boletim = {
  data: '30/10/2026', diaAnterior: '29/10/2026', hoje: true, geradoEm: '2026-10-30T10:00:00-03:00',
  resumo: { equipes: 1, emTurno: 0, encerrados: 0, semTurno: 1, agendamentosSemTurno: 1, programadoDia: 300, executadoDia: 0 },
  equipes: [{
    idEquipe: EQUIPE, codigo: 'FIC-CON-01', tipoEquipe: 'CONSTRUCAO', idBase: 'b1', baseOperacional: 'JUAZEIRO', situacao: 'SEM_TURNO', abertoDesdeOutroDia: false, placa: 'ABC1D23',
    componentes: [{ idColaborador: 'c1', nome: 'ANA FICTICIA', funcao: 'ENCARREGADO', lider: true, substituto: false }],
    lider: { idColaborador: 'c1', nome: 'ANA FICTICIA', funcao: 'ENCARREGADO', lider: true, substituto: false },
    minutosEmTurno: 0, noCiclo: { programado: 300, executado: 0 }, noDia: { programado: 300, executado: 0 }, diaAnterior: { programado: 0, executado: 0 }, metaDiaria: 1000, percentualMeta: 0,
    agendamentoSemTurno: true, agenda: [{ idProgramacao: PROGRAMACAO, numero: 1687, idObra: OBRA, numeroObra: '7000001', tituloObra: 'Obra de teste', descricao: 'Implantar rede', valor: 300, itens: 1, itensConcluidos: 0, liberacao: 'PENDENTE' }]
  }]
};

interface Estado {
  listas: string[];
  escritas: string[];
  detalhe?: unknown;
}

async function simularApi(page: Page, estado: Estado): Promise<void> {
  const responder = (route: any, corpo: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });
  await page.route(/\/v1\//, async (route) => {
    const pedido = route.request();
    const url = new URL(pedido.url());
    const caminho = url.pathname;
    if (/favorit|notifica/.test(caminho)) return responder(route, []);
    if (caminho.includes('/obras/') && pedido.method() !== 'GET') estado.escritas.push(`${pedido.method()} ${caminho}`);
    if (caminho.endsWith('/obras/turnos/boletim')) return responder(route, boletim);
    if (caminho.endsWith('/obras/turnos')) {
      estado.listas.push(url.search);
      return responder(route, {
        indicadores: { turnos: 3, emAndamento: 1, minutosTotais: 1200, comMeta: 2, bateuMeta: 1, semRelatorio: 1, comOcorrencias: 1, servicosExecutados: 2, valorExecutado: 900 },
        content: [linha],
        page: { size: 25, number: 0, totalElements: 1, totalPages: 1 }
      });
    }
    if (caminho.endsWith(`/obras/turnos/${TURNO}`)) return responder(route, estado.detalhe ?? detalheTurno);
    return responder(route, { content: [], page: { size: 25, number: 0, totalElements: 0, totalPages: 0 } });
  });
}

/** Mesmo com permissão de escrita, o web não oferece lançamento. */
async function entrar(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const carga = btoa(JSON.stringify({ exp: 4102444800, sub: 'teste' }));
    const sessao = {
      id: 'u1', tema: 'light', corPrimaria: 'blue', animacoesAtivadas: false, token: `x.${carga}.y`, login: 'teste', nome: 'Teste',
      permissoes: ['obras:obra:read', 'obras:programacao:read', 'obras:turno:read', 'obras:turno:write', 'obras:execucao:write']
    };
    localStorage.setItem('ces_session_enc', btoa(encodeURIComponent(JSON.stringify(sessao)).split('').reverse().join('')));
  });
}

test('os indicadores da listagem filtram sem mudar os números e não há como abrir turno', async ({ page }) => {
  const estado: Estado = { listas: [], escritas: [] };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto('/obras/turnos');

  const semRelatorio = page.locator('.ces-kpis').getByRole('button', { name: /Sem relatório/ });
  await expect(semRelatorio).toContainText('1', { timeout: 20_000 });
  await expect(page.getByText('FIC-CON-01').first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Abrir turno' })).toHaveCount(0);

  await semRelatorio.click();
  await expect(semRelatorio).toHaveAttribute('aria-pressed', 'true');
  await expect.poll(() => estado.listas.some((q) => q.includes('semRelatorio=true'))).toBe(true);
  await expect(page).toHaveURL(/semRelatorio/);
});

test('o detalhe mostra a APR preenchida pela equipe e não oferece lançamento', async ({ page }) => {
  const estado: Estado = { listas: [], escritas: [] };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto(`/obras/turnos/${TURNO}`);

  await expect(page.getByRole('heading', { name: 'Turno de FIC-CON-01' })).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText('Substituto', { exact: true })).toBeVisible();
  await expect(page.getByText('APR concluída')).toBeVisible();
  for (const acao of ['Apontar', 'Fechar turno', 'Preencher APR', 'Registrar', 'Obra não programada', 'Corrigir', 'Escrever']) {
    await expect(page.getByRole('button', { name: acao })).toHaveCount(0);
  }

  await page.getByRole('button', { name: 'Ver APR' }).click();
  const gaveta = page.locator('.p-drawer');
  await expect(gaveta.getByText('Choque elétrico')).toBeVisible();
  await expect(gaveta.getByText('ANA FICTICIA, BRUNO FICTICIO')).toBeVisible();
  await expect(gaveta.getByRole('button', { name: /Salvar|Concluir/ })).toHaveCount(0);
  expect(estado.escritas).toEqual([]);
});

test('o detalhe distingue APR pendente de serviço sem condição de executar', async ({ page }) => {
  const estado: Estado = { listas: [], escritas: [], detalhe: turnoSemApr };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto(`/obras/turnos/${TURNO}`);
  await expect(page.getByText('APR pendente')).toBeVisible({ timeout: 20_000 });

  estado.detalhe = turnoImpedido;
  await page.reload();
  await expect(page.getByText('Sem condição de executar · Clima')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByText('Chuva forte desde a chegada').first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Ver APR' })).toHaveCount(0);
});

test('o boletim segue o dashboard de turnos e só analisa', async ({ page }) => {
  const estado: Estado = { listas: [], escritas: [] };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto('/obras/boletim');

  await expect(page.getByText('sem turno aberto.')).toBeVisible({ timeout: 20_000 });
  await expect(page.getByRole('button', { name: 'Abrir turno' })).toHaveCount(0);

  await page.getByRole('button', { name: 'Miniaturas' }).click();
  await expect(page.locator('.ed-mini').first()).toBeVisible();

  await page.locator('.ed-mini').first().click();
  await expect(page).toHaveURL(/drill=equipe/);
  await expect(page.getByRole('heading', { name: 'Equipe FIC-CON-01' })).toBeVisible();
  expect(estado.escritas).toEqual([]);
});
