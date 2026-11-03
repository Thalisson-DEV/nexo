import { Page, expect, test } from '@playwright/test';

/** Programar a partir do planejamento, reprogramar pelo Gantt e abrir o Gantt direto pela URL, com a API simulada. */

const OBRA = '11111111-1111-4111-8111-111111111111';
const EQUIPE_A = '22222222-2222-4222-8222-222222222222';
const EQUIPE_B = '33333333-3333-4333-8333-333333333333';
const PROGRAMACAO = '44444444-4444-4444-8444-444444444444';

const PDF = [
  '%PDF-1.4',
  '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj',
  '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj',
  '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 800 500]>>endobj',
  'trailer<</Root 1 0 R>>',
  '%%EOF'
].join('\n');

function equipe(id: string, codigo: string, capacidadeMinutos: number) {
  return {
    id, codigo, idTipoEquipe: 't1', tipoEquipe: 'RETROESCAVADEIRA', idBase: 'b1', base: 'JUAZEIRO', distanciaKm: 120, tempoDeslocamentoMin: 150,
    deslocamentoEstimado: true, conflitos: [], jornadaMinutos: 480, plataformaMinutos: 30, capacidadeMinutos, diasUteis: 3, metaDia: 1000, metaPeriodo: 3000,
    valorProgramadoPeriodo: 2900, valorSelecao: 300, percentualOcupacao: 96.7, percentualMeta: 106.7
  };
}

function detalhe(sobrescrever: Record<string, unknown> = {}) {
  return {
    id: PROGRAMACAO, numero: 1687, idObra: OBRA, numeroObra: '7000001', tituloObra: 'Obra de teste', idEquipe: EQUIPE_A, codigoEquipe: 'RET-04',
    inicio: '03/11/2026', fim: '05/11/2026', descricao: 'Implantar rede', linhaViva: false, desligamento: true, finalizaObra: false, turno: 'DIA_TODO',
    situacaoIntervencao: 'CRIADO', situacao: 'PROGRAMADA', valor: 300, versao: 0,
    itens: [{ idItem: 'i1', local: 'P1', tipo: 'SERVICO', codigo: 'S1', descricao: 'INSTALAR POSTE', unidade: 'UN', quantidade: 3, quantidadeExecutada: 0, valorUnitario: 100, valorTotal: 300, status: 'PROGRAMADO' }],
    ...sobrescrever
  };
}

function plano(programado: boolean) {
  return {
    idObra: OBRA, numeroObra: '7000001', tituloObra: 'Obra de teste', idContrato: 'c1', classeTensao: 'BT',
    projetoMestre: { idAnexo: 'a1', nomeArquivo: 'projeto.pdf', tamanhoBytes: PDF.length }, rotacoes: {},
    pontos: [{ id: 'p1', numero: '1', pagina: 1, x: 0.2, y: 0.2, tipo: 'POSTE', tipoExecucao: 'NAO_DEFINIDO', temPoda: false, temAterramento: false, origem: 'MANUAL', versao: 0 }],
    vaos: [],
    kits: [{
      id: 'k1', idPonto: 'p1', idKit: 'kit', codigo: 'KIT-01', descricao: 'POSTE', operacao: 'INSTALAR', multiplicador: 1, executadoAntes: false, versao: 0,
      itens: [{ id: 'i1', tipo: 'SERVICO', codigo: 'S1', descricao: 'INSTALAR POSTE', operacao: 'INSTALAR', unidade: 'UN', quantidadePrevista: 3, quantidadeExecutada: 0, status: programado ? 'PROGRAMADO' : 'PENDENTE', valorUnitario: 100, idProgramacao: programado ? PROGRAMACAO : null, versao: 0 }]
    }],
    anotacoes: [], avancoKits: [],
    programacoes: programado ? [{ id: PROGRAMACAO, numero: 1687, idEquipe: EQUIPE_A, codigoEquipe: 'RET-04', inicio: '03/11/2026', fim: '05/11/2026', situacao: 'PROGRAMADA' }] : [],
    indicadores: { percentualExecutado: 0, valorExecutado: 0, valorProgramado: programado ? 300 : 0, valorPendente: programado ? 0 : 300, valorTotal: 300, servicosSemValor: 0, pontosSemKit: 0, vaosSemKit: 0 }
  };
}

interface Estado {
  programado: boolean;
  programar?: Record<string, any>;
  reprogramar?: Record<string, any>;
  ganttPedidos: string[];
}

async function simularApi(page: Page, estado: Estado): Promise<void> {
  const responder = (route: any, corpo: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });

  await page.route(/\/v1\//, async (route) => {
    const pedido = route.request();
    const url = new URL(pedido.url());
    const caminho = url.pathname;
    const metodo = pedido.method();

    if (caminho.endsWith('/planejamento')) return responder(route, plano(estado.programado));
    if (caminho.includes('/analises/disponibilidade')) return responder(route, { disponivel: false, ultima: null });
    if (caminho.endsWith('/anexos/a1/download')) return route.fulfill({ status: 200, contentType: 'application/pdf', body: PDF });
    if (caminho.endsWith('/programacoes/previa')) {
      return responder(route, {
        itens: 1, servicos: 1, materiais: 0, materiaisIncluidos: 0, ignoradosProgramados: 40, ignoradosConcluidos: 0, servicosSemValor: 0, nomesServicosSemValor: [], valor: 300,
        equipes: [equipe(EQUIPE_A, 'RET-04', 150), equipe(EQUIPE_B, 'LNV-02', -106)]
      });
    }
    if (caminho.endsWith(`/${OBRA}/programacoes`) && metodo === 'POST') {
      estado.programar = pedido.postDataJSON();
      estado.programado = true;
      return responder(route, { programacao: detalhe(), ignoradosProgramados: 40, ignoradosConcluidos: 0, materiaisIncluidos: 0, servicosSemValor: 0 }, 201);
    }
    if (caminho.endsWith('/programacoes/gantt')) {
      estado.ganttPedidos.push(url.search);
      return responder(route, {
        inicio: '01/11/2026', fim: '30/11/2026',
        equipes: [{
          idEquipe: EQUIPE_A, codigo: 'RET-04', tipoEquipe: 'RETROESCAVADEIRA', base: 'JUAZEIRO', valorProgramado: 300,
          programacoes: [{ id: PROGRAMACAO, numero: 1687, idObra: OBRA, numeroObra: '7000001', tituloObra: 'Obra de teste', inicio: '03/11/2026', fim: '05/11/2026', turno: 'DIA_TODO', situacao: 'ATRASADA', valor: 300, itens: 1 }]
        }]
      });
    }
    if (caminho.endsWith('/programacoes/historico')) return responder(route, { content: [] });
    if (caminho.endsWith('/reprogramacao')) {
      estado.reprogramar = pedido.postDataJSON();
      return responder(route, detalhe({ idEquipe: EQUIPE_B, codigoEquipe: 'LNV-02', versao: 1 }));
    }
    if (caminho.endsWith(`/programacoes/${PROGRAMACAO}`)) return responder(route, detalhe());
    if (caminho.endsWith('/tipos-anexos/todos-ativos') || caminho.endsWith('/anexos')) return responder(route, []);
    return responder(route, []);
  });
}

async function entrar(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const carga = btoa(JSON.stringify({ exp: 4102444800, sub: 'teste' }));
    const sessao = {
      id: 'u1', tema: 'light', corPrimaria: 'blue', animacoesAtivadas: false, token: `x.${carga}.y`, login: 'teste', nome: 'Teste',
      permissoes: ['obras:planejamento:read', 'obras:planejamento:write', 'obras:obra:read', 'obras:programacao:read', 'obras:programacao:write']
    };
    localStorage.setItem('ces_session_enc', btoa(encodeURIComponent(JSON.stringify(sessao)).split('').reverse().join('')));
  });
}

async function escolher(page: Page, gaveta: ReturnType<Page['locator']>, campo: string, opcao: string): Promise<void> {
  await gaveta.getByRole('combobox', { name: campo }).click();
  await page.getByRole('option', { name: opcao, exact: true }).click();
}

test('programa a seleção, exige as condições e avisa o que fica de fora', async ({ page, isMobile }) => {
  test.skip(isMobile, 'O editor sobre a folha é usado no computador.');
  const estado: Estado = { programado: false, ganttPedidos: [] };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto(`/obras/${OBRA}/planejamento`);

  await page.getByRole('checkbox', { name: 'Selecionar P1' }).check({ timeout: 20_000 });
  await page.getByRole('button', { name: 'Programar', exact: true }).click();

  const gaveta = page.locator('.p-drawer');
  await expect(gaveta).toContainText('40 já programados');
  await gaveta.getByRole('radio', { name: /LNV-02/ }).click();
  await expect(gaveta).toContainText('Inviável em um dia');
  await expect(gaveta).toContainText('acima da meta');

  // Sem resposta nas condições, nada é enviado.
  await gaveta.getByRole('button', { name: 'Programar', exact: true }).click();
  await expect(gaveta).toContainText('Informe se tem desligamento.');
  expect(estado.programar).toBeUndefined();

  await expect(gaveta.getByLabel('Descrição do planejamento')).toHaveValue('P1 — KIT-01');
  await escolher(page, gaveta, 'Linha viva', 'Não');
  await escolher(page, gaveta, 'Desligamento', 'Sim');
  await escolher(page, gaveta, 'Situação da intervenção', 'Criado');
  await gaveta.getByRole('button', { name: 'Programar', exact: true }).click();

  await expect.poll(() => estado.programar?.['idEquipe']).toBe(EQUIPE_B);
  expect(estado.programar?.['desligamento']).toBe(true);
  expect(estado.programar?.['idsItens']).toEqual(['i1']);
  await expect(page.locator('app-planejamento-arvore').getByRole('button', { name: /Abrir programação 1687/ })).toBeVisible();
});

test('arrastar a barra reprograma na hora e oferece desfazer', async ({ page, isMobile }) => {
  test.skip(isMobile, 'No toque, arrastar é rolar a tela: a reprogramação fica no detalhe.');
  const estado: Estado = { programado: true, ganttPedidos: [] };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto('/obras/programacao/semana?data=2026-11-04');

  const barra = page.getByRole('button', { name: /Programação 1687/ });
  await expect(barra).toBeVisible({ timeout: 20_000 });
  const caixa = (await barra.boundingBox())!;
  const coluna = caixa.width / 3;
  await page.mouse.move(caixa.x + caixa.width / 2, caixa.y + caixa.height / 2);
  await page.mouse.down();
  await page.mouse.move(caixa.x + caixa.width / 2 + coluna * 2, caixa.y + caixa.height / 2, { steps: 8 });
  await expect(page.locator('.tempo__fantasma')).toContainText('05/11/2026 a 07/11/2026');
  await page.mouse.up();

  await expect.poll(() => estado.reprogramar?.['inicio']).toBe('05/11/2026');
  expect(estado.reprogramar?.['fim']).toBe('07/11/2026');
  expect(estado.reprogramar?.['idEquipe']).toBe(EQUIPE_A);
  await expect(page.getByRole('button', { name: 'Desfazer' })).toBeVisible();
  await expect(page.locator('.p-drawer')).toHaveCount(0);
});

test('os atalhos de situação filtram e mantêm a contagem', async ({ page }) => {
  const estado: Estado = { programado: true, ganttPedidos: [] };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto('/obras/programacao/semana?data=2026-11-04');

  const barra = page.getByRole('button', { name: /Programação 1687/ });
  await expect(barra).toBeVisible({ timeout: 20_000 });
  const atalhos = page.locator('.prog-atalhos');

  await atalhos.getByRole('button', { name: 'Cancelada: 0' }).click();
  await expect(barra).toHaveCount(0);
  await expect(page).toHaveURL(/situacao=CANCELADA/);

  await atalhos.getByRole('button', { name: 'Atrasada: 1' }).click();
  await expect(barra).toBeVisible();
  await expect(atalhos.getByRole('button', { name: 'Atrasada: 1' })).toHaveAttribute('aria-pressed', 'true');

  await atalhos.getByRole('button', { name: /Ativas/ }).click();
  await expect(atalhos.getByRole('button', { name: /Ativas/ })).toHaveAttribute('aria-pressed', 'true');
});

test('o histórico envia os filtros e os atalhos ao servidor', async ({ page }) => {
  const estado: Estado = { programado: true, ganttPedidos: [] };
  const pedidos: string[] = [];
  page.on('request', (r) => { if (r.url().includes('/programacoes/historico')) pedidos.push(r.url()); });
  await entrar(page);
  await simularApi(page, estado);
  await page.goto('/obras/programacao/historico?mudou=EQUIPE&obra=7000001');

  await expect(page.getByRole('button', { name: /Todos/ }).first()).toHaveAttribute('aria-pressed', 'true', { timeout: 20_000 });
  await expect.poll(() => pedidos.some((u) => u.includes('mudou=EQUIPE') && u.includes('obra=7000001'))).toBe(true);
  await page.locator('.hist-atalhos').getByRole('button', { name: /^Excluída/ }).click();
  await expect.poll(() => pedidos.some((u) => u.includes('tipos=EXCLUIDA') && u.includes('size=25'))).toBe(true);
});

test('abre a semana filtrada pela URL e reprograma com observação', async ({ page }) => {
  const estado: Estado = { programado: true, ganttPedidos: [] };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto('/obras/programacao/semana?data=2026-11-04&base=b1');

  const barra = page.getByRole('button', { name: /Programação 1687/ });
  await expect(barra).toBeVisible({ timeout: 20_000 });
  expect(estado.ganttPedidos[0]).toContain('inicio=2026-11-02');
  expect(estado.ganttPedidos[0]).toContain('fim=2026-11-08');
  expect(estado.ganttPedidos[0]).toContain('idBase=b1');
  await expect(barra).toHaveAttribute('data-situacao', 'ATRASADA');

  await barra.click();
  const gaveta = page.locator('.p-drawer');
  await gaveta.getByRole('button', { name: 'Reprogramar' }).click();
  await gaveta.getByRole('radio', { name: /LNV-02/ }).click();

  await gaveta.getByRole('button', { name: 'Reprogramar' }).click();
  await expect(gaveta).toContainText('Informe a observação da reprogramação.');
  expect(estado.reprogramar).toBeUndefined();

  await gaveta.getByLabel('Observação').fill('turma errada');
  await gaveta.getByRole('button', { name: 'Reprogramar' }).click();
  await expect.poll(() => estado.reprogramar?.['observacao']).toBe('turma errada');
  expect(estado.reprogramar?.['idEquipe']).toBe(EQUIPE_B);
  await expect(gaveta).toContainText('LNV-02');
});
