import { Page, expect, test } from '@playwright/test';

/** Fluxo do planejamento com a API simulada: anexar o PDF, marcar pontos, lançar vão e aplicar kit. */

const OBRA = '11111111-1111-4111-8111-111111111111';

const PDF = [
  '%PDF-1.4',
  '1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj',
  '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj',
  '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 800 500]/Contents 4 0 R/Resources<</Font<</F1 5 0 R>>>>>>endobj',
  '4 0 obj<</Length 40>>stream',
  'BT /F1 18 Tf 40 450 Td (Folha 1) Tj ET',
  'endstream endobj',
  '5 0 obj<</Type/Font/Subtype/Type1/BaseFont/Helvetica>>endobj',
  'trailer<</Root 1 0 R>>',
  '%%EOF'
].join('\n');

interface Estado {
  plano: Record<string, any>;
}

function planoInicial(): Record<string, any> {
  return {
    idObra: OBRA, numeroObra: '7000001', tituloObra: 'Obra de teste', idContrato: 'c1', classeTensao: 'BT',
    projetoMestre: null, rotacoes: {}, pontos: [], vaos: [], kits: [], anotacoes: [], avancoKits: [],
    indicadores: { percentualExecutado: 0, valorExecutado: 0, valorProgramado: 0, valorPendente: 0, valorTotal: 0, servicosSemValor: 0, pontosSemKit: 0, vaosSemKit: 0 }
  };
}

async function simularApi(page: Page, estado: Estado): Promise<void> {
  const responder = (route: any, corpo: unknown, status = 200) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(corpo) });
  const atualizar = () => {
    const { plano } = estado;
    plano['indicadores'].pontosSemKit = plano['pontos'].filter((p: any) => !plano['kits'].some((k: any) => k.idPonto === p.id)).length;
    plano['indicadores'].vaosSemKit = plano['vaos'].filter((v: any) => !plano['kits'].some((k: any) => k.idVao === v.id)).length;
    return plano;
  };

  await page.route(/\/v1\//, async (route) => {
    const pedido = route.request();
    const url = new URL(pedido.url());
    const metodo = pedido.method();
    const caminho = url.pathname;

    if (caminho.endsWith('/planejamento') && metodo === 'GET') return responder(route, atualizar());
    if (caminho.includes('/analises/disponibilidade')) return responder(route, { disponivel: false, ultima: null });
    if (caminho.endsWith('/projeto-mestre') && metodo === 'POST') {
      estado.plano['projetoMestre'] = { idAnexo: 'a1', nomeArquivo: 'projeto.pdf', tamanhoBytes: PDF.length };
      return responder(route, atualizar());
    }
    if (caminho.endsWith('/anexos/a1/download')) {
      return route.fulfill({ status: 200, contentType: 'application/pdf', body: PDF });
    }
    if (caminho.endsWith('/anexos')) return responder(route, []);
    if (caminho.endsWith('/tipos-anexos/todos-ativos')) return responder(route, [{ id: 't1', nome: 'PROJETO', ativo: true }]);
    if (caminho.endsWith('/planejamento/pontos') && metodo === 'POST') {
      const dados = pedido.postDataJSON();
      estado.plano['pontos'].push({ id: `p${dados.numero}`, ...dados, tipoExecucao: 'NAO_DEFINIDO', temPoda: false, temAterramento: false, origem: 'MANUAL', versao: 0 });
      return responder(route, atualizar(), 201);
    }
    if (caminho.endsWith('/planejamento/vaos') && metodo === 'POST') {
      const dados = pedido.postDataJSON();
      estado.plano['vaos'].push({ id: 'v1', ...dados, origem: 'MANUAL', versao: 0 });
      return responder(route, atualizar(), 201);
    }
    if (caminho.endsWith('/obras/kits')) {
      return responder(route, { content: [{ id: 'k1', codigo: 'KIT-01', descricao: 'Poste 11m' }], page: { totalElements: 1, totalPages: 1, number: 0, size: 20 } });
    }
    if (caminho.endsWith('/planejamento/kits') && metodo === 'POST') {
      const dados = pedido.postDataJSON();
      estado.plano['kits'].push({
        id: 'ka1', idPonto: dados.idsPontos[0], idKit: dados.idKit, codigo: 'KIT-01', descricao: 'Poste 11m', operacao: dados.operacao,
        multiplicador: 1, executadoAntes: false, versao: 0,
        itens: [{ id: 'i1', tipo: 'SERVICO', codigo: 'S1', descricao: 'INSTALAR POSTE', operacao: 'INSTALAR', unidade: 'UN', quantidadePrevista: 1, quantidadeExecutada: 0, status: 'PENDENTE', valorUnitario: 100, versao: 0 }]
      });
      estado.plano['indicadores'].valorTotal = 100;
      estado.plano['indicadores'].valorPendente = 100;
      return responder(route, { planejamento: atualizar(), jaTinham: [] });
    }
    return responder(route, { content: [], page: { totalElements: 0, totalPages: 0, number: 0, size: 20 } });
  });
}

async function entrar(page: Page): Promise<void> {
  await page.addInitScript(() => {
    const carga = btoa(JSON.stringify({ exp: 4102444800, sub: 'teste' }));
    const sessao = {
      id: 'u1', tema: 'light', corPrimaria: 'blue', animacoesAtivadas: false, token: `x.${carga}.y`, login: 'teste', nome: 'Teste', permissoes: ['obras:planejamento:read', 'obras:planejamento:write', 'obras:obra:read', 'obras:kit:read']
    };
    localStorage.setItem('ces_session_enc', btoa(encodeURIComponent(JSON.stringify(sessao)).split('').reverse().join('')));
  });
}

test('anexa o projeto, marca pontos, lança vão e aplica kit', async ({ page, isMobile }) => {
  test.skip(isMobile, 'O editor sobre a folha é usado no computador.');
  const estado: Estado = { plano: planoInicial() };
  await entrar(page);
  await simularApi(page, estado);
  await page.goto(`/obras/${OBRA}/planejamento`);

  await expect(page.getByText('Esta obra ainda não tem projeto')).toBeVisible({ timeout: 20_000 });
  await page.getByRole('button', { name: 'Anexar projeto' }).click();
  const gaveta = page.locator('.p-drawer');
  await gaveta.getByRole('combobox').click();
  await page.getByRole('option', { name: 'PROJETO' }).click();
  await gaveta.locator('input[type=file]').setInputFiles({ name: 'projeto.pdf', mimeType: 'application/pdf', buffer: Buffer.from(PDF) });
  await gaveta.getByRole('button', { name: 'Enviar projeto' }).click();

  const folha = page.locator('.plan-sobre');
  await expect(page.locator('ces-pdf-canvas canvas')).toBeVisible();
  await expect.poll(async () => (await folha.boundingBox())?.width ?? 0, { timeout: 15_000 }).toBeGreaterThan(100);

  // O número sugerido segue a sequência e o Enter confirma.
  await page.keyboard.press('p');
  await folha.click({ position: { x: 100, y: 100 } });
  await expect(page.getByLabel('Número')).toHaveValue('1');
  await page.keyboard.press('Enter');
  await expect(folha.locator('.plan-marcador')).toHaveCount(1);
  await folha.click({ position: { x: 260, y: 160 } });
  await expect(page.getByLabel('Número')).toHaveValue('2');
  await page.keyboard.press('Enter');
  await expect(folha.locator('.plan-marcador')).toHaveCount(2);
  await page.keyboard.press('Escape');

  await page.keyboard.press('v');
  await folha.locator('.plan-marcador').nth(0).click();
  await folha.locator('.plan-marcador').nth(1).click();
  await expect(folha.locator('.plan-trecho')).toHaveCount(1);
  await page.keyboard.press('Escape');
  await page.keyboard.press('Escape');

  await folha.locator('.plan-marcador').nth(0).click();
  const cartao = page.locator('.plan-cartao');
  await cartao.getByRole('searchbox', { name: 'Buscar kit' }).fill('KIT');
  await cartao.getByRole('button', { name: /KIT-01/ }).click();

  await expect(page.locator('.plan-indicadores__total')).toContainText('100,00');
  await expect(page.locator('app-planejamento-arvore')).toContainText('KIT-01');
  await expect(folha.locator('.plan-marcador').nth(0)).toHaveAttribute('data-estado', 'pendente');
});
