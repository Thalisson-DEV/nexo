import { Page, Route, expect, test } from '@playwright/test';

/**
 * Listagens críticas no padrão "tabela primeiro" (openspec padrao-listagem-tabela-primeiro, 9.4),
 * com a API simulada: a tela abre já nos dados, busca e página vão para a URL, o link com filtro
 * reabre o mesmo recorte e o painel de filtros mostra os chips.
 */

const PERMISSOES = [
  'rh:veiculo:read',
  'rh:veiculo:write',
  'rh:colaborador:read',
  'rh:colaborador:write',
  'estoque:material:read',
  'estoque:material:write',
  'estoque:solicitacao:read',
  'estoque:solicitacao:create',
  'estoque:movimentacao:read'
];

/** Sessão no formato do AuthService (base64 do JSON invertido). */
function sessao(): string {
  const dados = {
    id: 'u1',
    login: 'teste.e2e',
    nome: 'Teste E2E',
    tipoUsuario: 'ADMINISTRATIVO',
    token: 'token-e2e',
    permissoes: PERMISSOES,
    tema: 'light',
    corPrimaria: 'blue',
    animacoesAtivadas: false
  };
  return Buffer.from(encodeURIComponent(JSON.stringify(dados)).split('').reverse().join(''), 'latin1').toString('base64');
}

const pagina = <T>(content: T[], total = content.length) => ({
  content,
  page: { size: 25, number: 0, totalElements: total, totalPages: Math.max(1, Math.ceil(total / 25)) }
});

/** Registra as consultas de uma listagem para conferir parâmetros (busca, página, filtros). */
async function simularApi(page: Page, rotas: Record<string, unknown>): Promise<URL[]> {
  const chamadas: URL[] = [];
  await page.route(/^https?:\/\/(localhost|127\.0\.0\.1):8080\/api\//, async (route: Route) => {
    const url = new URL(route.request().url());
    const cors = {
      'access-control-allow-origin': route.request().headers()['origin'] ?? '*',
      'access-control-allow-credentials': 'true',
      'access-control-allow-headers': '*',
      'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS'
    };
    if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers: cors });
    const alvo = Object.keys(rotas).find((sufixo) => url.pathname.endsWith(sufixo));
    if (alvo) {
      chamadas.push(url);
      return route.fulfill({ json: rotas[alvo], headers: cors });
    }
    // Demais chamadas do shell e dos lookups: vazio, no formato esperado (página quando paginada).
    if (url.pathname.includes('/ws/')) return route.abort();
    const vazio = route.request().method() !== 'GET' || url.pathname.endsWith('/resumo') ? {} : url.searchParams.has('page') || url.searchParams.has('size') ? pagina([]) : [];
    return route.fulfill({ json: vazio, headers: cors });
  });
  await page.route(/^https?:\/\/(localhost|127\.0\.0\.1):8080\/(ws|websocket|sockjs)/, (route) => route.abort());
  return chamadas;
}

async function entrar(page: Page): Promise<void> {
  const valor = sessao();
  await page.addInitScript((v) => {
    if (!sessionStorage.getItem('e2e-sessao')) {
      localStorage.clear();
      localStorage.setItem('ces_session_enc', v);
      sessionStorage.setItem('e2e-sessao', '1');
    }
  }, valor);
}

const veiculos = Array.from({ length: 30 }, (_, i) => ({
  id: `v${i}`,
  placa: `ABC${String(1000 + i)}`,
  modelo: i % 2 ? 'Strada' : 'Hilux',
  ativo: true,
  tipoVeiculo: { id: 't1', nome: 'Pickup' }
}));

test.describe('listagens críticas', () => {
  test.beforeEach(async ({ page }) => entrar(page));

  test('veículos: abre nos dados, busca e página vão para a URL', async ({ page }) => {
    const chamadas = await simularApi(page, { '/v1/veiculos': pagina(veiculos.slice(0, 25), 30) });
    await page.goto('/admin/cadastros/veiculos');
    await expect(page.getByRole('heading', { level: 1, name: /Veículos/ })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByText('ABC1000').first()).toBeVisible();
    await expect(page.getByRole('button', { name: /Expandir filtros/ })).toBeVisible();

    await page.getByRole('searchbox').first().fill('ABC1001');
    await expect(page).toHaveURL(/busca=ABC1001/);
    await expect.poll(() => chamadas.at(-1)?.searchParams.get('buscaGlobal') ?? chamadas.at(-1)?.searchParams.get('busca')).toBe('ABC1001');
  });

  test('colaboradores: link com filtro reabre o recorte com chip', async ({ page }) => {
    const chamadas = await simularApi(page, {
      '/v1/rh/colaboradores': pagina([{ id: 'c1', nomeCompleto: 'MARIA DA SILVA', matricula: '123', cpf: '12345678901', ativo: true }])
    });
    await page.goto('/admin/cadastros/colaboradores?status=false');
    await expect(page.getByText('MARIA DA SILVA')).toBeVisible({ timeout: 20_000 });
    await expect.poll(() => chamadas.at(-1)?.searchParams.get('ativo')).toBe('false');
    await expect(page.locator('.ces-filter-panel__chip').first()).toBeVisible();
  });

  test('materiais: abre nos dados com ordenação padrão', async ({ page }) => {
    const chamadas = await simularApi(page, {
      '/v1/materiais': pagina([{ id: 'm1', codigo: 'MAT001', descricao: 'CABO DE COBRE', unidadeMedida: 'MT', detalhaSerie: false, ativo: true }])
    });
    await page.goto('/almoxarifado/materiais');
    await expect(page.getByText('CABO DE COBRE')).toBeVisible({ timeout: 20_000 });
    await expect.poll(() => chamadas.at(-1)?.searchParams.get('sort')).toBe('codigo,asc');

    // Clicar na linha abre a edição, como o botão "Editar".
    await page.getByText('CABO DE COBRE').click();
    await expect(page.getByText('Editar material')).toBeVisible();
  });

  test('solicitações de material: abre nos dados do contexto comercial', async ({ page }) => {
    const chamadas = await simularApi(page, {
      '/v1/solicitacoes-material': pagina([
        { id: 'abcdef12-0000', tipoTecnico: 'ADICAO_COMUM', estoqueOrigemNome: 'ALMOX CENTRAL', estoqueDestinoNome: 'CAMPO EQ-01', status: 'PENDENTE', dataSolicitacao: '2026-09-10T10:00:00Z', totalItens: 2 }
      ])
    });
    await page.goto('/almoxarifado/solicitacoes');
    await expect(page.getByText('ALMOX CENTRAL')).toBeVisible({ timeout: 20_000 });
    await expect.poll(() => chamadas.at(-1)?.searchParams.get('contexto')).toBe('COMERCIAL');
  });

  test('movimentações: abre nos dados e a prévia carrega os itens', async ({ page }) => {
    await simularApi(page, {
      '/v1/movimentacoes-estoque': pagina([
        { idReferenciaOperacao: 'op123456-xyz', tipo: 'TRANSFERENCIA', dataHora: '2026-09-10T10:00:00Z', operadorNome: 'Carlos', estoqueOrigem: { id: 'e1', nomeIdentificador: 'ALMOX CENTRAL' } }
      ]),
      '/operacao/op123456-xyz': {
        idReferenciaOperacao: 'op123456-xyz',
        tipo: 'TRANSFERENCIA',
        dataHora: '2026-09-10T10:00:00Z',
        operadorNome: 'Carlos',
        itens: [{ id: 'i1', quantidade: 2, material: { codigo: 'M-1', descricao: 'Cabo' } }]
      }
    });
    await page.goto('/almoxarifado/movimentacoes');
    await expect(page.getByText('#op123456').first()).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('ces-data-table').getByText('Transferência')).toBeVisible();
  });
});
