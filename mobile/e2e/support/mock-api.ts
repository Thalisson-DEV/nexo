import { Page, Route, Request, BrowserContext, expect } from '@playwright/test';

/**
 * Backend falso para os E2E. Guarda o estado do "servidor" e registra toda escrita
 * recebida, para os testes afirmarem payload, cabeçalhos e (não) duplicação.
 *
 * Os formatos de resposta espelham o que `SyncService`/`TurnoService` consomem hoje
 * (characterization): mudar um formato aqui é mudar o contrato.
 */

export type Perfil = 'CAMPO' | 'ADMINISTRATIVO';

export interface EscritaRecebida {
  method: string;
  path: string;
  body: unknown;
  idempotencyKey: string | null;
  authorization: string | null;
}

export interface MockOptions {
  perfil?: Perfil;
  turnoAberto?: boolean;
  /** Token expira em N segundos (padrão: 8h). */
  expiraEmSegundos?: number;
  /** Execuções já registradas no servidor (histórico da equipe), espalhadas nos últimos dias. */
  historico?: number;
}

export const IDS = {
  usuario: 'u-encarregado-1',
  equipe: 'e-1',
  estoque: 'est-1',
  turno: 't-1',
  veiculo: 'v-1',
  colaboradores: ['c-1', 'c-2'],
  acoes: ['a-1', 'a-2', 'a-3'],
  materiais: ['m-cabo', 'm-medidor'],
} as const;

function b64url(obj: unknown): string {
  return Buffer.from(JSON.stringify(obj)).toString('base64url');
}

/** JWT não assinado: o app só decodifica (jwt-decode), quem valida é o backend. */
export function fakeJwt(perfil: Perfil, expiraEmSegundos = 8 * 3600): string {
  const payload = {
    sub: IDS.usuario,
    nome: perfil === 'CAMPO' ? 'José Encarregado' : 'Ana Técnica SST',
    equipeId: perfil === 'CAMPO' ? IDS.equipe : undefined,
    estoqueId: perfil === 'CAMPO' ? IDS.estoque : undefined,
    roles: [],
    tipoUsuario: perfil,
    exp: Math.floor(Date.now() / 1000) + expiraEmSegundos,
  };
  return `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url(payload)}.assinatura-falsa`;
}

const page = <T>(content: T[]) => ({
  content,
  page: { size: 500, number: 0, totalElements: content.length, totalPages: 1 },
});

export class MockApi {
  readonly escritas: EscritaRecebida[] = [];
  perfil: Perfil;
  turnoAberto: boolean;
  /** Quando true, toda chamada /api responde como falha de rede. */
  offline = false;
  /** Inspeções aceitas pelo POST; o GET /minhas devolve as mesmas, como o servidor real. */
  readonly inspecoesCriadas: Record<string, unknown>[] = [];
  /** Desvios atribuídos ao usuário (tela Desvios, consulta online). */
  readonly desvios: { id: string; status: string; [campo: string]: unknown }[] = [
    { id: 'dv-a', idInspecao: 'ins-1', dataInspecao: '2026-10-01', processo: { id: 'pr-1', nome: 'Comercial' },
      baseOperacional: { id: 'b-1', nome: 'Base Fortaleza' }, equipe: { id: 'e-1', codigo: 'EQ-101', nome: 'Equipe 101' },
      idPergunta: 'p-1', pergunta: 'Usa capacete com jugular?', gravidade: 'ALTA', descricao: 'Capacete sem jugular',
      acaoCorretiva: null, prazo: '2026-10-05', status: 'ABERTO', dataResolucao: null, vencido: true, anexos: [] },
    { id: 'dv-b', idInspecao: 'ins-1', dataInspecao: '2026-10-01', processo: { id: 'pr-1', nome: 'Comercial' },
      baseOperacional: { id: 'b-1', nome: 'Base Fortaleza' }, equipe: { id: 'e-1', codigo: 'EQ-101', nome: 'Equipe 101' },
      idPergunta: 'p-2', pergunta: 'Luvas isolantes em bom estado?', gravidade: 'CRITICA', descricao: 'Luva com furo',
      acaoCorretiva: 'Trocar a luva', prazo: '2099-12-31', status: 'EM_TRATAMENTO', dataResolucao: null, vencido: false, anexos: [] },
  ];
  /**
   * Token de acesso vencido no servidor: chamadas autenticadas com o token antigo recebem
   * 401 TOKEN_EXPIRADO. Um token obtido em /v1/auth/refresh volta a ser aceito.
   */
  sessaoExpirada = false;
  /** A sessão do aparelho acabou no servidor: /v1/auth/refresh responde SESSAO_INVALIDA. */
  sessaoEncerrada = false;
  /** Último token emitido pela renovação (aceito mesmo com `sessaoExpirada`). */
  private tokenRenovado: string | null = null;
  /** Há cookie de sessão no navegador (houve login e não houve logout), como no backend real. */
  private cookieDeSessao = false;
  private renovacoes = 0;
  private readonly expiraEm: number;
  private readonly historico: number;
  private readonly recebidas = new Map<string, unknown>();

  constructor(opts: MockOptions = {}) {
    this.perfil = opts.perfil ?? 'CAMPO';
    this.turnoAberto = opts.turnoAberto ?? true;
    this.expiraEm = opts.expiraEmSegundos ?? 8 * 3600;
    this.historico = opts.historico ?? 0;
  }

  /** Execuções sincronizadas, 1 a cada 20 min para trás a partir de agora (formato de /minha-equipe). */
  private execucoesHistorico() {
    const agora = Date.now();
    return Array.from({ length: this.historico }, (_, i) => ({
      id: `hist-${i}`,
      referenciaNota: String(6_000_000 + i),
      idTurno: IDS.turno,
      equipe: { id: IDS.equipe },
      dataLancamento: new Date(agora - (i + 1) * 20 * 60_000).toISOString(),
      statusExecucao: 'CONCLUIDO',
      latitude: -3.73, longitude: -38.52,
      // O primeiro (o mais recente) chega com tipo; os demais imitam notas anteriores ao cadastro.
      tipoServico: [
        { id: 'ts-cl', codigo: 'CL', descricao: 'LIGAÇÃO NOVA' },
        { id: 'ts-cs', codigo: 'CS', descricao: 'CORTE' },
        null,
      ][i % 3],
      acoes: [{ acao: { id: IDS.acoes[i % 3] } }],
      materiais: [],
      valorTotalFaturado: [120.5, 45, 60][i % 3],
    }));
  }

  get token(): string {
    return fakeJwt(this.perfil, this.expiraEm);
  }

  escritasEm(path: string | RegExp): EscritaRecebida[] {
    return this.escritas.filter(e => (typeof path === 'string' ? e.path === path : path.test(e.path)));
  }

  /**
   * @param conviteInstalacao `false` (padrão): o convite "Instale o app" já vem adiado,
   *   porque os testes rodam no navegador e ele cobriria a tela. Specs do convite passam `true`.
   */
  async install(target: Page | BrowserContext, { conviteInstalacao = false } = {}): Promise<void> {
    if (!conviteInstalacao) {
      await target.addInitScript(() => {
        try { localStorage.setItem('ces_instalar_adiado_em', String(Date.now())); } catch { /* sem storage */ }
      });
    }
    await target.route('**/api/**', (route, request) => this.handle(route, request));
  }

  private json(route: Route, status: number, body: unknown) {
    return route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
  }

  private async handle(route: Route, request: Request): Promise<void> {
    if (this.offline) return route.abort('internetdisconnected');

    const url = new URL(request.url());
    const path = url.pathname.replace(/^\/api/, '');
    const method = request.method();

    if (method !== 'GET') {
      let body: unknown = null;
      try {
        body = request.postDataJSON();
      } catch {
        body = request.postData();
      }
      this.escritas.push({
        method,
        path,
        body,
        idempotencyKey: request.headers()['idempotency-key'] ?? null,
        authorization: request.headers()['authorization'] ?? null,
      });
    }

    if (path === '/v1/auth/login') {
      this.sessaoExpirada = false;
      this.sessaoEncerrada = false;
      this.tokenRenovado = null;
      this.cookieDeSessao = true;
      return this.json(route, 200, { token: this.token });
    }
    if (path === '/v1/auth/logout') {
      this.cookieDeSessao = false;
      return route.fulfill({ status: 204 });
    }
    if (path === '/v1/auth/refresh') {
      if (this.sessaoEncerrada || !this.cookieDeSessao) {
        return this.json(route, 401, { title: 'Sessão Encerrada', detail: 'Sua sessão expirou. Entre novamente.', code: 'SESSAO_INVALIDA' });
      }
      this.renovacoes++;
      this.tokenRenovado = fakeJwt(this.perfil, 8 * 3600 + this.renovacoes);
      return this.json(route, 200, { token: this.tokenRenovado });
    }
    const autorizacao = request.headers()['authorization'] ?? '';
    if (this.sessaoExpirada && autorizacao !== `Bearer ${this.tokenRenovado}`) {
      return this.json(route, 401, { title: 'Token Expirado', detail: 'O token de acesso expirou.', code: 'TOKEN_EXPIRADO' });
    }

    // ---------- Turno ----------
    if (path === `/v1/turnos/equipe/${IDS.equipe}/ativo`) {
      return this.turnoAberto
        ? this.json(route, 200, {
            id: IDS.turno, idEquipe: IDS.equipe, idUsuarioAbertura: IDS.usuario,
            dataInicio: new Date().toISOString(), status: 'ABERTO',
          })
        : this.json(route, 404, { title: 'Sem turno', detail: 'Nenhum turno aberto.' });
    }
    if (path === '/v1/turnos/abrir' && method === 'POST') {
      this.turnoAberto = true;
      return this.json(route, 201, {
        id: IDS.turno, idEquipe: IDS.equipe, idUsuarioAbertura: IDS.usuario,
        dataInicio: new Date().toISOString(), idVeiculo: IDS.veiculo, idsColaboradores: [...IDS.colaboradores],
      });
    }
    if (/^\/v1\/turnos\/[^/]+\/fechar$/.test(path)) {
      this.turnoAberto = false;
      return this.json(route, 200, { id: IDS.turno, status: 'FECHADO' });
    }
    if (path === '/v1/turnos/minha-equipe/composicao') {
      return this.json(route, 200, {
        composicao: {
          idEquipe: IDS.equipe, codigo: 'EQ-101',
          veiculo: { id: IDS.veiculo, placa: 'ABC1D23', modelo: 'Strada' },
          membros: [
            { idColaborador: 'c-1', nome: 'José Encarregado', funcao: 'Encarregado', responsavel: true },
            { idColaborador: 'c-2', nome: 'Maria Eletricista', funcao: 'Eletricista', responsavel: false },
          ],
          metaFaturamentoMensal: 30000, metaFaturamentoDiaria: 1500,
        },
        usuarioResponsavel: true,
        opcoes: {
          colaboradores: [
            { id: 'c-1', nome: 'José Encarregado', funcao: 'Encarregado', equipeAtual: 'EQ-101', responsavelEmOutraEquipe: false },
            { id: 'c-2', nome: 'Maria Eletricista', funcao: 'Eletricista', equipeAtual: 'EQ-101', responsavelEmOutraEquipe: false },
            { id: 'c-3', nome: 'Pedro Ajudante', funcao: 'Ajudante', equipeAtual: 'EQ-200', responsavelEmOutraEquipe: false },
            { id: 'c-4', nome: 'Rita Líder', funcao: 'Encarregada', equipeAtual: 'EQ-300', responsavelEmOutraEquipe: true },
          ],
          veiculos: [{ id: IDS.veiculo, placa: 'ABC1D23', modelo: 'Strada' }, { id: 'v-2', placa: 'XYZ9K87', modelo: 'Saveiro' }],
        },
        acoes: [
          { idAcao: 'a-1', codigo: 'LIG-01', nome: 'Ligação nova', valor: 120.5 },
          { idAcao: 'a-2', codigo: 'COR-02', nome: 'Corte', valor: 45 },
          { idAcao: 'a-3', codigo: 'REL-03', nome: 'Religação', valor: 60 },
        ],
      });
    }

    // ---------- Equipe ----------
    if (path === `/v1/equipes/${IDS.equipe}`) {
      return this.json(route, 200, {
        id: IDS.equipe, codigo: 'EQ-101', tipoEquipe: { nome: 'Leve' }, processo: { nome: 'Comercial' },
        baseOperacional: { id: 'b-1', nome: 'Base Fortaleza' }, veiculo: { id: IDS.veiculo },
        membros: [{ idColaborador: 'c-1' }, { idColaborador: 'c-2' }],
        metaFaturamentoMensal: 30000, metaFaturamentoDiaria: 1500,
      });
    }
    if (path === `/v1/equipes/${IDS.equipe}/membros`) {
      return this.json(route, 200, [
        { idColaborador: 'c-1', idUsuario: IDS.usuario, nomeColaborador: 'José Encarregado', funcao: { nome: 'Encarregado' }, vinculoAtivo: true },
        { idColaborador: 'c-2', idUsuario: 'u-2', nomeColaborador: 'Maria Eletricista', funcao: { nome: 'Eletricista' }, vinculoAtivo: true },
      ]);
    }

    // ---------- Catálogos ----------
    if (path === '/v1/contratos/acoes') {
      return this.json(route, 200, page([
        { id: 'a-1', codigo: 'LIG-01', nome: 'Ligação nova', ativo: true, valor: 120.5 },
        { id: 'a-2', codigo: 'COR-02', nome: 'Corte', ativo: true, valor: 45 },
        { id: 'a-3', codigo: 'REL-03', nome: 'Religação', ativo: true, valor: 60 },
      ]));
    }
    if (path === '/v1/tipos-servico/opcoes') {
      return this.json(route, 200, [
        { id: 'ts-cs', codigo: 'CS', descricao: 'CORTE', ativo: true },
        { id: 'ts-cl', codigo: 'CL', descricao: 'LIGAÇÃO NOVA', ativo: true },
        { id: 'ts-cr', codigo: 'CR', descricao: 'RELIGAÇÃO', ativo: true },
      ]);
    }
    if (path === '/v1/materiais') {
      return this.json(route, 200, page([
        { id: 'm-cabo', codigo: 'CB-10', descricao: 'Cabo concêntrico 10mm', unidadeMedida: 'M', detalhaSerie: false },
        { id: 'm-medidor', codigo: 'MD-01', descricao: 'Medidor monofásico', unidadeMedida: 'UN', detalhaSerie: true },
      ]));
    }
    if (path === '/v1/estoques') {
      return this.json(route, 200, page([
        { id: IDS.estoque, nomeIdentificador: 'Caminhão EQ-101', tipoEstoque: { nome: 'CAMPO' }, idEquipe: IDS.equipe, ativo: true },
        { id: 'est-dep', nomeIdentificador: 'Almoxarifado Central', tipoEstoque: { nome: 'FISICO' }, ativo: true },
        { id: 'est-dep2', nomeIdentificador: 'Almoxarifado Caucaia', tipoEstoque: { nome: 'FISICO' }, ativo: true },
      ]));
    }
    if (path === '/v1/saldos') {
      return this.json(route, 200, page([
        { id: 's-1', estoque: { id: IDS.estoque }, material: { id: 'm-cabo', codigo: 'CB-10', descricao: 'Cabo concêntrico 10mm', unidadeMedida: 'M', detalhaSerie: false }, quantidade: 100, ativo: true, condicao: 'NOVO' },
        { id: 's-2', estoque: { id: IDS.estoque }, material: { id: 'm-medidor', codigo: 'MD-01', descricao: 'Medidor monofásico', unidadeMedida: 'UN', detalhaSerie: true }, quantidade: 2, ativo: true, condicao: 'NOVO' },
      ]));
    }
    if (path === '/v1/saldos/seriais') {
      return this.json(route, 200, page([
        { id: 'ss-1', estoque: { id: IDS.estoque }, material: { id: 'm-medidor' }, numSerie: 'SN-0001', ativo: true, condicao: 'NOVO' },
        { id: 'ss-2', estoque: { id: IDS.estoque }, material: { id: 'm-medidor' }, numSerie: 'SN-0002', ativo: true, condicao: 'NOVO' },
      ]));
    }

    if (path === '/v1/execucoes-comerciais/minha-equipe') {
      return this.json(route, 200, page(this.execucoesHistorico()));
    }

    // ---------- SST (técnico administrativo) ----------
    if (path === '/v1/sst/tipos-inspecao/opcoes') {
      return this.json(route, 200, [{ id: 'ti-1', nome: 'Campo', ordem: 1, ativo: true }, { id: 'ti-2', nome: 'Base', ordem: 2, ativo: true }]);
    }
    if (path === '/v1/sst/formularios-inspecao') {
      return this.json(route, 200, page([{ id: 'f-1', titulo: 'Checklist de EPI', descricao: 'Uso de equipamentos de proteção', ativo: true, totalPerguntas: 2 }]));
    }
    if (path === '/v1/sst/formularios-inspecao/f-1') {
      return this.json(route, 200, {
        id: 'f-1', titulo: 'Checklist de EPI', descricao: 'Uso de equipamentos de proteção', ativo: true, emUso: true,
        grupos: [{ id: 'g-1', titulo: 'Proteção individual', ordem: 1, perguntas: [
          { id: 'p-1', enunciado: 'Capacete em uso?', tipoResposta: 'MULTIPLA_ESCOLHA', obrigatoria: true, ordem: 1, opcoes: [
            { id: 'o-sim', texto: 'Sim', geraDesvio: false, gravidade: null, ordem: 1 },
            { id: 'o-nao', texto: 'Não', geraDesvio: true, gravidade: 'ALTA', ordem: 2 },
          ] },
          { id: 'p-2', enunciado: 'Observação do inspetor', tipoResposta: 'TEXTO', obrigatoria: false, ordem: 2, opcoes: [] },
        ] }],
      });
    }
    if (path === '/v1/bases-operacionais') {
      return this.json(route, 200, page([{ id: 'b-1', nome: 'Base Fortaleza', latitude: -3.73, longitude: -38.52, raio: 500, ativo: true }]));
    }
    if (path === '/v1/equipes') {
      return this.json(route, 200, page([{ id: IDS.equipe, codigo: 'EQ-101', tipoEquipe: { nome: 'Leve' }, processo: { nome: 'Comercial' },
        baseOperacional: { id: 'b-1', nome: 'Base Fortaleza' }, veiculo: { id: IDS.veiculo }, membros: [{ idColaborador: 'c-1' }, { idColaborador: 'c-2' }] }]));
    }
    if (path === '/v1/rh/colaboradores') {
      return this.json(route, 200, page([
        { id: 'c-1', nomeCompleto: 'José Encarregado', funcao: { nome: 'Encarregado' }, ativo: true },
        { id: 'c-2', nomeCompleto: 'Maria Eletricista', funcao: { nome: 'Eletricista' }, ativo: true },
      ]));
    }
    if (path === '/v1/processos-negocio') {
      return this.json(route, 200, page([{ id: 'pr-1', nome: 'Comercial', ativo: true }, { id: 'pr-2', nome: 'Manutenção', ativo: true }]));
    }
    if (path === '/v1/veiculos') {
      return this.json(route, 200, page([{ id: IDS.veiculo, placa: 'ABC1D23', modelo: 'Strada', fabricante: 'Fiat', ativo: true }]));
    }
    // ---------- Meus desvios (consulta online) ----------
    if (path === '/v1/sst/desvios/meus' && method === 'GET') {
      const pendentes = url.searchParams.get('apenasPendentes') === 'true';
      const status = url.searchParams.get('status');
      const lista = this.desvios.filter(d => (!pendentes || d.status !== 'RESOLVIDO') && (!status || d.status === status));
      return this.json(route, 200, { content: lista, page: { size: 20, number: 0, totalElements: lista.length, totalPages: 1 } });
    }
    const tratar = /^\/v1\/sst\/desvios\/meus\/([^/]+)\/tratamento$/.exec(path);
    if (tratar && method === 'PUT') {
      const d = this.desvios.find(x => x.id === tratar[1]);
      if (!d) return this.json(route, 404, { title: 'Não encontrado' });
      const corpo = this.escritas.at(-1)?.body as { status: string; acaoCorretiva?: string; prazo?: string };
      Object.assign(d, { status: corpo.status, acaoCorretiva: corpo.acaoCorretiva ?? d.acaoCorretiva, prazo: corpo.prazo ?? d.prazo });
      return this.json(route, 200, d);
    }
    if (path === '/v1/sst/inspecoes/minhas' && method === 'GET') {
      return this.json(route, 200, { content: this.inspecoesCriadas, page: { size: 50, number: 0, totalElements: this.inspecoesCriadas.length, totalPages: 1 } });
    }
    if (path === '/v1/sst/inspecoes/anexos' && method === 'POST') {
      const n = this.escritasEm('/v1/sst/inspecoes/anexos').length - 1;
      return this.json(route, 201, { id: `ev-${n}`, nomeArquivo: 'foto.jpg', contentType: 'image/jpeg', tamanhoBytes: 1000 });
    }
    if (path === '/v1/sst/inspecoes' && method === 'POST') {
      const corpo = (this.escritas.at(-1)?.body ?? {}) as {
        desvios?: { idPergunta: string; idOpcaoResposta: string; acaoCorretiva?: string; status?: string }[]; respostas?: unknown[];
        idFormulario?: string; idTipoInspecao?: string; idBaseOperacional?: string;
      };
      const criada = {
        id: 'insp-1', idInspetor: IDS.usuario, status: 'CONCLUIDA', dataRealizacao: new Date().toISOString(),
        formulario: { id: corpo.idFormulario }, tipoInspecao: { id: corpo.idTipoInspecao, nome: 'Checklist de EPI' },
        baseOperacional: { id: corpo.idBaseOperacional, nome: 'Base Fortaleza' },
        respostas: corpo.respostas ?? [], evidencias: [],
        desvios: (corpo.desvios ?? []).map((d, i) => ({ ...d, id: `dv-${i}`, status: d.status ?? 'ABERTO' })),
      };
      this.inspecoesCriadas.push(criada);
      return this.json(route, 201, criada);
    }

    // ---------- Escritas operacionais ----------
    if (path === '/v1/execucoes-comerciais' && method === 'POST') {
      const key = request.headers()['idempotency-key'] ?? crypto.randomUUID();
      if (!this.recebidas.has(key)) this.recebidas.set(key, { id: `exec-${this.recebidas.size + 1}` });
      const { id } = this.recebidas.get(key) as { id: string };
      return this.json(route, 201, { id, statusExecucao: 'CONCLUIDO', valorTotalFaturado: 120.5 });
    }
    if (path === '/v1/solicitacoes-material' && method === 'POST') {
      return this.json(route, 201, { id: `sol-${this.escritasEm('/v1/solicitacoes-material').length}`, status: 'PENDENTE' });
    }
    if (path === '/v1/telemetria/mobile' && method === 'POST') {
      const lote = (this.escritas.at(-1)?.body ?? {}) as { eventos?: unknown[] };
      return this.json(route, 202, { recebidos: lote.eventos?.length ?? 0, descartados: 0 });
    }
    if (path.startsWith('/v1/sincronizacao/operacoes')) {
      return method === 'GET' ? this.json(route, 404, { title: 'Não encontrada' }) : this.json(route, 201, { idCaso: 'caso-1' });
    }

    // Listas e catálogos não usados nos fluxos cobertos: vazio paginado.
    if (method === 'GET') return this.json(route, 200, page([]));
    return this.json(route, 501, { title: 'Sem mock', detail: `${method} ${path}` });
  }
}

/**
 * Coloca o app no estado "usuário logado e sincronizado" pelo caminho real
 * (tela de login → /sync → dashboard), sem atalhos no IndexedDB.
 */
export async function loginPelaUi(p: Page, usuario = 'encarregado', timeout = 20_000): Promise<void> {
  await p.goto('/login');
  // A splash animada (~3 s na abertura a frio) cobre a tela e intercepta toques.
  await expect(p.locator('app-splash-screen > div')).toHaveCount(0, { timeout: 15_000 });
  await p.locator('#login').fill(usuario);
  await p.locator('#senha').fill('senha-secreta');
  await p.getByRole('button', { name: 'Entrar' }).click();
  await p.waitForURL('**/hoje', { timeout });
}
