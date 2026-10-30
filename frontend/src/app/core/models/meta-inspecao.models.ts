// ============================================================
// Metas de Inspeção (SST) — cadastro e acompanhamento
// ============================================================

/** O alvo da meta: uma função inteira ou um colaborador específico. Nunca os dois. */
export type EscopoMeta = 'FUNCAO' | 'COLABORADOR';

export type PeriodicidadeMeta =
  | 'SEMANAL'
  | 'QUINZENAL'
  | 'MENSAL'
  | 'BIMESTRAL'
  | 'TRIMESTRAL'
  | 'SEMESTRAL'
  | 'ANUAL'
  | 'PERSONALIZADO';

export type StatusMeta = 'ATINGIDA' | 'EM_ANDAMENTO' | 'NAO_ATINGIDA';

// ------------------------------------------------------------
// Cadastro
// ------------------------------------------------------------
export interface MetaInspecaoRequest {
  /** UUID do tipo de inspeção vinculado — obrigatório. */
  idTipoInspecao: string;
  /** UUID — exclusivo com idColaborador. */
  idFuncao?: string | null;
  /** UUID — exclusivo com idFuncao. */
  idColaborador?: string | null;
  /** Inteiro, 1..999. */
  quantidade: number;
  periodicidade: PeriodicidadeMeta;
  /** 'YYYY-MM-DD' — ancora a primeira janela. */
  vigenciaInicio: string;
  /** 'YYYY-MM-DD' | null = vigência indeterminada. */
  vigenciaFim?: string | null;
  /** Até 200 caracteres — é o rótulo da meta na listagem. */
  descricao?: string | null;
}

export interface MetaInspecaoResponse {
  id: string;
  idTipoInspecao: string;
  nomeTipoInspecao: string | null;
  /** Derivado pelo backend a partir do alvo enviado. */
  escopo: EscopoMeta;
  idFuncao: string | null;
  nomeFuncao: string | null;
  idColaborador: string | null;
  nomeColaborador: string | null;
  quantidade: number;
  periodicidade: PeriodicidadeMeta;
  vigenciaInicio: string;
  vigenciaFim: string | null;
  descricao: string | null;
  ativo: boolean;
  /** Janela vigente hoje; `null` quando a meta está fora da vigência. */
  janelaAtualInicio: string | null;
  janelaAtualFim: string | null;
}

export interface MetaInspecaoFilter {
  idTipoInspecao?: string | null;
  escopo?: EscopoMeta | null;
  idFuncao?: string | null;
  idColaborador?: string | null;
  periodicidade?: PeriodicidadeMeta | null;
  /** 'YYYY-MM-DD' — só metas cuja vigência cobre a data. */
  vigenteEm?: string | null;
  ativo?: boolean | null;
  /** Busca rápida por descrição (case/accent-insensitive). */
  buscaGlobal?: string | null;
  page?: number;
  size?: number;
  sort?: string;
}

// ------------------------------------------------------------
// Acompanhamento (cobrança na janela vigente)
// ------------------------------------------------------------
/**
 * Campos aceitos em `ordenarPor`. O endpoint não é paginado (devolve a lista
 * inteira da data de referência), por isso a ordenação **não** usa o
 * `sort=campo,dir` do Spring.
 */
export type OrdenacaoAcompanhamentoMeta =
  | 'PERCENTUAL'
  | 'REALIZADO'
  | 'META'
  | 'SALDO'
  | 'DIAS_RESTANTES'
  | 'COLABORADOR'
  | 'FUNCAO'
  | 'BASE_OPERACIONAL'
  | 'PROCESSO'
  | 'CENTRO_CUSTO'
  | 'STATUS';

export type DirecaoOrdenacao = 'ASC' | 'DESC';

/**
 * Todos os recortes são aplicados sobre o **colaborador** alcançado pela meta,
 * não sobre a inspeção — inclusive `idProcesso`, que aqui é o processo de
 * negócio do cadastro de RH (diferente do `idProcesso` dos dashboards, que é o
 * processo da inspeção).
 */
export interface AcompanhamentoMetaFilter {
  /** 'YYYY-MM-DD' — default do backend: hoje. */
  dataReferencia?: string | null;
  idTipoInspecao?: string[] | null;
  idFuncao?: string[] | null;
  idColaborador?: string[] | null;
  idBaseOperacional?: string[] | null;
  /** Regional comercial da base operacional (entidade Centro de Faturamento). */
  idCentroCusto?: string[] | null;
  idProcesso?: string[] | null;
  status?: StatusMeta[] | null;
  /** Default do backend: 'PERCENTUAL'. */
  ordenarPor?: OrdenacaoAcompanhamentoMeta | null;
  /** Default do backend: 'ASC'. */
  direcao?: DirecaoOrdenacao | null;
}

export interface AcompanhamentoMetaItem {
  idMeta: string;
  idTipoInspecao: string;
  nomeTipoInspecao: string | null;
  /** De onde veio a meta aplicada (a individual prevalece sobre a da função). */
  origem: EscopoMeta;
  idColaborador: string;
  nomeColaborador: string | null;
  /** Se false, o realizado é zero por falta de acesso — ele não consegue lançar inspeção. */
  possuiUsuario: boolean;
  idFuncao: string | null;
  nomeFuncao: string | null;
  idBaseOperacional: string | null;
  nomeBaseOperacional: string | null;
  /** `null` quando o colaborador não tem base operacional vinculada. */
  idCentroCusto: string | null;
  nomeCentroCusto: string | null;
  idProcesso: string | null;
  nomeProcesso: string | null;
  periodicidade: PeriodicidadeMeta;
  janelaInicio: string;
  janelaFim: string;
  /** 0 quando a janela já se encerrou. */
  diasRestantes: number;
  /** Quantidade cheia da janela (inteiro) — diferente da meta rateada do dashboard. */
  meta: number;
  realizado: number;
  /** Quanto falta; 0 quando atingida. */
  saldo: number;
  /** Pode passar de 100. */
  percentualAtingimento: number;
  status: StatusMeta;
}

export interface AcompanhamentoMetasResponse {
  dataReferencia: string;
  totalColaboradores: number;
  atingiram: number;
  emAndamento: number;
  naoAtingiram: number;
  somaMetas: number;
  somaRealizado: number;
  percentualColaboradoresNaMeta: number;
  percentualExecucaoGlobal: number;
  itens: AcompanhamentoMetaItem[];
}

// ============================================================
// Rótulos / metadados de UI
// ============================================================
export const PERIODICIDADE_LABELS: Record<PeriodicidadeMeta, string> = {
  SEMANAL: 'Semanal',
  QUINZENAL: 'Quinzenal',
  MENSAL: 'Mensal',
  BIMESTRAL: 'Bimestral',
  TRIMESTRAL: 'Trimestral',
  SEMESTRAL: 'Semestral',
  ANUAL: 'Anual',
  PERSONALIZADO: 'Personalizado'
};

/** Complemento usado na frase de prévia: "4 inspeções **por mês**". */
export const PERIODICIDADE_FRASE: Record<PeriodicidadeMeta, string> = {
  SEMANAL: 'por semana',
  QUINZENAL: 'por quinzena',
  MENSAL: 'por mês',
  BIMESTRAL: 'por bimestre',
  TRIMESTRAL: 'por trimestre',
  SEMESTRAL: 'por semestre',
  ANUAL: 'por ano',
  PERSONALIZADO: 'no período da campanha'
};

export const PERIODICIDADE_OPTIONS = (Object.keys(PERIODICIDADE_LABELS) as PeriodicidadeMeta[]).map(
  (value) => ({ label: PERIODICIDADE_LABELS[value], value })
);

export const ESCOPO_META_LABELS: Record<EscopoMeta, string> = {
  FUNCAO: 'Função',
  COLABORADOR: 'Colaborador'
};

export const ESCOPO_META_BADGE: Record<EscopoMeta, string> = {
  FUNCAO: 'bg-slate-50 text-slate-600',
  COLABORADOR: 'bg-primary-50 text-primary-700'
};

export const STATUS_META_LABELS: Record<StatusMeta, string> = {
  ATINGIDA: 'Atingida',
  EM_ANDAMENTO: 'Em andamento',
  NAO_ATINGIDA: 'Não atingida'
};

export const STATUS_META_BADGE: Record<StatusMeta, string> = {
  ATINGIDA: 'bg-green-50 text-green-600',
  EM_ANDAMENTO: 'bg-amber-50 text-amber-600',
  NAO_ATINGIDA: 'bg-red-50 text-red-600'
};

export const ORDENACAO_META_LABELS: Record<OrdenacaoAcompanhamentoMeta, string> = {
  PERCENTUAL: 'Atingimento',
  REALIZADO: 'Realizado',
  META: 'Meta',
  SALDO: 'Saldo',
  DIAS_RESTANTES: 'Dias restantes',
  COLABORADOR: 'Colaborador',
  FUNCAO: 'Função',
  BASE_OPERACIONAL: 'Base operacional',
  PROCESSO: 'Processo',
  CENTRO_CUSTO: 'Centro de custo',
  STATUS: 'Situação'
};

/**
 * Direção que faz sentido ao clicar numa coluna pela primeira vez. Em cobrança,
 * o topo da lista deve trazer quem está pior: menor percentual, maior dívida.
 */
export const ORDENACAO_META_DIRECAO_INICIAL: Record<OrdenacaoAcompanhamentoMeta, DirecaoOrdenacao> = {
  PERCENTUAL: 'ASC',
  REALIZADO: 'DESC',
  META: 'DESC',
  SALDO: 'DESC',
  DIAS_RESTANTES: 'ASC',
  COLABORADOR: 'ASC',
  FUNCAO: 'ASC',
  BASE_OPERACIONAL: 'ASC',
  PROCESSO: 'ASC',
  CENTRO_CUSTO: 'ASC',
  STATUS: 'ASC'
};

/** Limites do campo `quantidade` (validados também no backend). */
export const META_QUANTIDADE_MIN = 1;
export const META_QUANTIDADE_MAX = 999;
export const META_DESCRICAO_MAX = 200;

// ============================================================
// Helpers de janela — SOMENTE para a prévia do formulário.
// Depois de salvar, `janelaAtualInicio`/`janelaAtualFim` da resposta são a
// fonte de verdade; não recalcule a janela de uma meta já persistida.
// ============================================================

/** Converte 'YYYY-MM-DD' em Date local (evita o deslocamento de fuso do `new Date('YYYY-MM-DD')`). */
export function parseDataIso(value: string | null | undefined): Date | null {
  if (!value) return null;
  const [ano, mes, dia] = value.slice(0, 10).split('-').map(Number);
  if (!ano || !mes || !dia) return null;
  return new Date(ano, mes - 1, dia);
}

export function toDataIso(date: Date): string {
  const mes = String(date.getMonth() + 1).padStart(2, '0');
  const dia = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${mes}-${dia}`;
}

export function formatarDataBr(value: string | null | undefined): string {
  const date = parseDataIso(value);
  return date ? date.toLocaleDateString('pt-BR') : '—';
}

/** Passo de cada periodicidade: dias corridos ou meses de calendário. */
const PASSO_JANELA: Record<
  Exclude<PeriodicidadeMeta, 'PERSONALIZADO'>,
  { dias?: number; meses?: number }
> = {
  SEMANAL: { dias: 7 },
  QUINZENAL: { dias: 15 },
  MENSAL: { meses: 1 },
  BIMESTRAL: { meses: 2 },
  TRIMESTRAL: { meses: 3 },
  SEMESTRAL: { meses: 6 },
  ANUAL: { meses: 12 }
};

function avancar(inicio: Date, periodicidade: Exclude<PeriodicidadeMeta, 'PERSONALIZADO'>, ciclos: number): Date {
  const passo = PASSO_JANELA[periodicidade];
  const proximo = new Date(inicio);
  if (passo.dias) {
    proximo.setDate(proximo.getDate() + passo.dias * ciclos);
  } else {
    const dia = inicio.getDate();
    proximo.setDate(1);
    proximo.setMonth(proximo.getMonth() + (passo.meses ?? 1) * ciclos);
    // Preserva o dia de ancoragem, truncando em meses mais curtos (31/01 → 28/02).
    const ultimoDia = new Date(proximo.getFullYear(), proximo.getMonth() + 1, 0).getDate();
    proximo.setDate(Math.min(dia, ultimoDia));
  }
  return proximo;
}

export interface JanelaMeta {
  inicio: string;
  fim: string;
}

/**
 * Calcula a janela vigente numa data de referência a partir da periodicidade e
 * da data de ancoragem. As janelas se repetem sem gap nem sobreposição.
 * Retorna `null` quando a referência está fora da vigência.
 */
export function calcularJanela(
  periodicidade: PeriodicidadeMeta,
  vigenciaInicio: string,
  vigenciaFim: string | null | undefined,
  referencia: Date = new Date()
): JanelaMeta | null {
  const inicio = parseDataIso(vigenciaInicio);
  if (!inicio) return null;
  const fim = parseDataIso(vigenciaFim ?? null);
  const hoje = new Date(referencia.getFullYear(), referencia.getMonth(), referencia.getDate());

  if (hoje < inicio) return null;
  if (fim && hoje > fim) return null;

  // Campanha pontual: a janela é única e igual à vigência.
  if (periodicidade === 'PERSONALIZADO') {
    return fim ? { inicio: toDataIso(inicio), fim: toDataIso(fim) } : null;
  }

  // Avança ciclo a ciclo até cobrir a referência (limite defensivo para vigências longas).
  let atual = inicio;
  for (let ciclo = 1; ciclo <= 5000; ciclo++) {
    const proximo = avancar(inicio, periodicidade, ciclo);
    if (hoje < proximo) {
      const ultimoDia = new Date(proximo);
      ultimoDia.setDate(ultimoDia.getDate() - 1);
      // A vigência pode encerrar antes do fim natural da janela.
      const fimJanela = fim && fim < ultimoDia ? fim : ultimoDia;
      return { inicio: toDataIso(atual), fim: toDataIso(fimJanela) };
    }
    atual = proximo;
  }
  return null;
}

/** Frase da prévia: "4 inspeções por mês, a cada janela iniciada em 01/08/2026, por prazo indeterminado." */
export function descreverMeta(
  quantidade: number | null,
  periodicidade: PeriodicidadeMeta | null,
  vigenciaInicio: string | null,
  vigenciaFim: string | null
): string | null {
  if (!quantidade || !periodicidade || !vigenciaInicio) return null;
  const plural = quantidade === 1 ? 'inspeção' : 'inspeções';
  const prazo = vigenciaFim ? `até ${formatarDataBr(vigenciaFim)}` : 'por prazo indeterminado';
  if (periodicidade === 'PERSONALIZADO') {
    return `${quantidade} ${plural} em uma janela única de ${formatarDataBr(vigenciaInicio)} ${prazo}.`;
  }
  return `${quantidade} ${plural} ${PERIODICIDADE_FRASE[periodicidade]}, a cada janela iniciada em ${formatarDataBr(vigenciaInicio)}, ${prazo}.`;
}
