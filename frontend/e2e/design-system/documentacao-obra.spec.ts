import { Page, expect, test } from '@playwright/test';

/** Documentação técnica da obra e modelos de checklist, com a API simulada. */

const OBRA = '11111111-1111-4111-8111-111111111111';
const MODELO = '22222222-2222-4222-8222-222222222222';
const PONTO = '33333333-3333-4333-8333-333333333333';

const obra = {
  id: OBRA, numero: '7000001', titulo: 'Obra de teste', statusNome: 'EM EXECUÇÃO', statusConcluida: false, tipoObra: 'OBRA', anexos: [], subprojetos: [], equipes: [], projetosRelacionados: [], ocorrencias: [], historicoStatus: []
};

const estrutura = {
  grupos: [{
    titulo: 'Acesso',
    perguntas: [
      { id: 'p1', enunciado: 'O acesso está livre?', tipo: 'ESCOLHA_UNICA', obrigatoria: true, aceitaNaoSeAplica: false, opcoes: ['Sim', 'Não'] },
      { id: 'p2', enunciado: 'Distância até a rede (m)', tipo: 'NUMERO', obrigatoria: true, aceitaNaoSeAplica: true, opcoes: null }
    ]
  }]
};

const modelo = { id: MODELO, finalidade: 'VISITA_PREVIA', titulo: 'Pré-vistoria', descricao: null, estrutura, grupos: 1, perguntas: 2, emUso: true, ativo: true };

function documentacao(sobrescrever: object = {}) {
  return {
    visitaRealizada: false, visitas: [], temProjetoMestre: true,
    pontos: [{ id: PONTO, numero: '1', pagina: 1, tipo: 'POSTE', executado: true, ficha: null, idModelo: null, tituloModelo: null, itensEvidencia: [], evidencias: [] }],
    asBuilt: { pontosExecutados: 1, fichasCompletas: 0, pontosPendentes: ['1'], pontosSemEvidencia: ['1'] },
    documentos: [], equipamentos: [],
    ...sobrescrever
  };
}

interface Estado {
  documentacao: ReturnType<typeof documentacao>;
  escritas: { metodo: string; caminho: string; corpo: any }[];
}

async function simularApi(page: Page, estado: Estado): Promise<void> {
  const responder = (route: any, corpo: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });
  await page.route(/\/v1\//, async (route) => {
    const pedido = route.request();
    const caminho = new URL(pedido.url()).pathname;
    if (/favorit|notifica/.test(caminho)) return responder(route, []);
    if (pedido.method() !== 'GET' && caminho.includes('/obras/')) {
      estado.escritas.push({ metodo: pedido.method(), caminho, corpo: caminho.endsWith('/evidencias') ? null : pedido.postDataJSON() });
      if (caminho.endsWith('/visitas')) estado.documentacao = documentacao({ visitaRealizada: true, visitas: [{ id: 'v1', idModelo: MODELO, tituloModelo: 'Pré-vistoria', dataVisita: '2026-11-01', responsavel: 'ANA FICTICIA', observacoes: null, respostas: { itens: [] }, usuario: 'Teste', data: '2026-11-01T10:00:00-03:00' }] });
      return responder(route, {}, 200);
    }
    if (caminho.endsWith(`/obras/${OBRA}/documentacao`)) return responder(route, estado.documentacao);
    if (caminho.endsWith('/obras/checklists')) return responder(route, { content: [modelo], page: { size: 25, number: 0, totalElements: 1, totalPages: 1 } });
    if (caminho.endsWith(`/obras/checklists/${MODELO}`)) return responder(route, modelo);
    if (caminho.endsWith(`/obras/${OBRA}`)) return responder(route, obra);
    if (caminho.endsWith('/orcamento')) return responder(route, { importado: false, itens: [] });
    if (/mural|\/obras\/(status|tipos-anexos|agentes)/.test(caminho)) return responder(route, []);
    if (caminho.includes(`/obras/${OBRA}/`)) return responder(route, []);
    return responder(route, { content: [], page: { size: 25, number: 0, totalElements: 0, totalPages: 0 } });
  });
}

async function entrar(page: Page, permissoes: string[]): Promise<void> {
  await page.addInitScript((lista) => {
    const carga = btoa(JSON.stringify({ exp: 4102444800, sub: 'teste' }));
    const sessao = { id: 'u1', tema: 'light', corPrimaria: 'blue', animacoesAtivadas: false, token: `x.${carga}.y`, login: 'teste', nome: 'Teste', permissoes: lista };
    localStorage.setItem('ces_session_enc', btoa(encodeURIComponent(JSON.stringify(sessao)).split('').reverse().join('')));
  }, permissoes);
}

const SUPERVISOR = ['obras:obra:read', 'obras:documentacao:read', 'obras:documentacao:write'];

test('o supervisor registra a visita prévia, e a obra passa a "Realizada"', async ({ page }) => {
  const estado: Estado = { documentacao: documentacao(), escritas: [] };
  await entrar(page, SUPERVISOR);
  await simularApi(page, estado);
  await page.goto(`/obras/${OBRA}`);

  const secao = page.locator('app-obra-documentacao');
  await expect(secao.getByText('Não realizada')).toBeVisible({ timeout: 20_000 });
  await secao.getByRole('button', { name: 'Registrar visita' }).click();

  const gaveta = page.locator('.p-drawer');
  await gaveta.getByRole('button', { name: 'Salvar visita' }).click();
  await expect(gaveta.getByText('Responda esta pergunta.')).toBeVisible();
  expect(estado.escritas).toEqual([]);

  await gaveta.getByRole('textbox', { name: /Responsável/i }).fill('ANA FICTICIA');
  await gaveta.getByRole('button', { name: 'Sim', exact: true }).click();
  await gaveta.getByRole('checkbox', { name: 'Não se aplica' }).click();
  await gaveta.getByRole('button', { name: 'Salvar visita' }).click();

  await expect(secao.getByText('Realizada', { exact: true })).toBeVisible();
  const envio = estado.escritas.find((e) => e.caminho.endsWith('/visitas'))!;
  expect(envio.corpo.respostas).toEqual([
    { idPergunta: 'p1', valor: null, selecionadas: ['Sim'], naoSeAplica: false },
    { idPergunta: 'p2', valor: null, selecionadas: [], naoSeAplica: true }
  ]);
});

test('sem as fichas completas, o As Built lista os pontos e não é gerado', async ({ page }) => {
  const estado: Estado = { documentacao: documentacao(), escritas: [] };
  await entrar(page, SUPERVISOR);
  await simularApi(page, estado);
  await page.goto(`/obras/${OBRA}`);

  const secao = page.locator('app-obra-documentacao');
  await secao.getByRole('button', { name: 'Gerar versão 1' }).click({ timeout: 20_000 });
  await expect(secao.getByText('Complete as fichas dos pontos 1')).toBeVisible();
  expect(estado.escritas).toEqual([]);
});

test('quem só consulta vê a documentação sem ações de lançamento', async ({ page }) => {
  const estado: Estado = { documentacao: documentacao(), escritas: [] };
  await entrar(page, ['obras:obra:read', 'obras:documentacao:read']);
  await simularApi(page, estado);
  await page.goto(`/obras/${OBRA}`);

  const secao = page.locator('app-obra-documentacao');
  await expect(secao.getByText('Não realizada')).toBeVisible({ timeout: 20_000 });
  for (const acao of ['Registrar visita', 'Gerar versão 1', 'Marcar equipamento']) {
    await expect(secao.getByRole('button', { name: acao })).toHaveCount(0);
  }
  await secao.getByRole('button', { name: 'Abrir o ponto 1' }).click();
  const gaveta = page.locator('.p-drawer');
  await expect(gaveta.getByText('Nenhuma foto lançada.')).toBeVisible();
  await expect(gaveta.locator('input[type=file]')).toHaveCount(0);
  await expect(gaveta.getByRole('button', { name: 'Salvar ficha' })).toHaveCount(0);
});

test('o ponto lista as fotos pedidas pelo modelo e envia a foto na hora', async ({ page }) => {
  const modeloBook = { ...modelo, finalidade: 'AS_BUILT', emUso: false, estrutura: { ...estrutura, evidencias: [{ id: 'f1', titulo: 'Aterramento', obrigatoria: true }] } };
  const estado: Estado = { documentacao: documentacao(), escritas: [] };
  await entrar(page, SUPERVISOR);
  await simularApi(page, estado);
  await page.route(/\/obras\/checklists(\?|$)/, (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [modeloBook], page: { size: 100, number: 0, totalElements: 1, totalPages: 1 } }) }));
  await page.goto(`/obras/${OBRA}`);

  const secao = page.locator('app-obra-documentacao');
  await expect(secao.getByRole('heading', { name: 'Book e As Built' })).toBeVisible({ timeout: 20_000 });
  await secao.getByRole('button', { name: 'Abrir o ponto 1' }).click();

  const gaveta = page.locator('.p-drawer');
  await expect(gaveta.getByText('Aterramento', { exact: true })).toBeVisible();
  await expect(gaveta.getByText('Obrigatória', { exact: true })).toBeVisible();
  await gaveta.locator('input[type=file]').first().setInputFiles({ name: 'haste.jpg', mimeType: 'image/jpeg', buffer: Buffer.from([0xff, 0xd8, 0xff, 0xd9]) });

  await expect.poll(() => estado.escritas.some((e) => e.caminho.endsWith(`/pontos/${PONTO}/evidencias`))).toBe(true);
  await expect(page.getByText('Foto lançada.')).toBeVisible();
});

test('com a ficha completa e foto faltando, avisa e só gera depois de confirmar', async ({ page }) => {
  const estado: Estado = { documentacao: documentacao({ asBuilt: { pontosExecutados: 1, fichasCompletas: 1, pontosPendentes: [], pontosSemEvidencia: ['1'] } }), escritas: [] };
  await entrar(page, SUPERVISOR);
  await simularApi(page, estado);
  await page.goto(`/obras/${OBRA}`);

  const secao = page.locator('app-obra-documentacao');
  await secao.getByRole('button', { name: 'Gerar versão 1' }).click({ timeout: 20_000 });
  await expect(page.getByText('Faltam fotos no ponto 1.', { exact: false })).toBeVisible();
  expect(estado.escritas).toEqual([]);
  await page.getByRole('button', { name: 'Gerar mesmo assim' }).click();
  await expect.poll(() => estado.escritas.some((e) => e.caminho.endsWith('/as-built'))).toBe(true);
});

test('modelo em uso avisa que as perguntas só mudam duplicando', async ({ page }) => {
  const estado: Estado = { documentacao: documentacao(), escritas: [] };
  await entrar(page, ['obras:documentacao:read', 'obras:documentacao:manage']);
  await simularApi(page, estado);
  await page.goto('/obras/cadastros/checklists');

  await page.getByRole('button', { name: /Editar/ }).first().click({ timeout: 20_000 });
  const gaveta = page.locator('.p-drawer');
  await expect(gaveta.getByText('Modelo em uso')).toBeVisible();
  await expect(gaveta.getByRole('button', { name: 'Adicionar pergunta' })).toHaveCount(0);
});
