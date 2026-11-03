import { CesTone, cesAttainmentBand } from '@ces/ui';
import {
  EquipeOperacional,
  EquipeTurno,
  LancamentoTurno,
  ProducaoDia,
  ProducaoPorObra,
  ProducaoPorTipoServico,
  SituacaoMeta,
  SituacaoTurno
} from '../../../core/models/dashboard.models';

/**
 * Regras de apresentação dos dashboards operacional e de turnos. Ficam fora dos
 * componentes para serem testadas sem TestBed e para que as duas telas falem igual.
 */

export interface RotuloSituacao {
  rotulo: string;
  tom: CesTone;
  icone: string;
}

export const SITUACAO_META: Record<SituacaoMeta, RotuloSituacao> = {
  META_ATINGIDA: { rotulo: 'Meta atingida', tom: 'success', icone: 'pi pi-check-circle' },
  NO_RITMO: { rotulo: 'No ritmo', tom: 'info', icone: 'pi pi-arrow-up-right' },
  ABAIXO_DO_RITMO: { rotulo: 'Abaixo do ritmo', tom: 'warning', icone: 'pi pi-exclamation-triangle' },
  SEM_META: { rotulo: 'Sem meta cadastrada', tom: 'neutral', icone: 'pi pi-minus-circle' }
};

export const SITUACAO_TURNO: Record<SituacaoTurno, RotuloSituacao> = {
  EM_TURNO: { rotulo: 'Em turno', tom: 'success', icone: 'pi pi-play-circle' },
  ENCERRADO: { rotulo: 'Turno encerrado', tom: 'neutral', icone: 'pi pi-stop-circle' },
  SEM_TURNO: { rotulo: 'Sem turno', tom: 'neutral', icone: 'pi pi-circle' }
};

/** Tom do bloco de meta, nas mesmas faixas do `ces-meter`; neutro sem meta. */
export function tomDaMeta(percentualMeta: number | null | undefined): CesTone {
  return percentualMeta === null || percentualMeta === undefined ? 'neutral' : cesAttainmentBand(percentualMeta);
}

const MOEDA = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const MOEDA_INTEIRA = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  maximumFractionDigits: 0
});

/** "R$ 850", "R$ 12,3 mil", "R$ 1,25 mi" — para leitura rápida em cartões. */
export function moedaCompacta(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—';
  const abs = Math.abs(valor);
  if (abs >= 1_000_000) {
    return `R$ ${(valor / 1_000_000).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} mi`;
  }
  if (abs >= 10_000) {
    return `R$ ${(valor / 1_000).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} mil`;
  }
  return abs >= 1_000 ? MOEDA_INTEIRA.format(valor) : MOEDA.format(valor);
}

export function moeda(valor: number | null | undefined): string {
  return valor === null || valor === undefined || !Number.isFinite(valor) ? '—' : MOEDA.format(valor);
}

export function percentual(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—';
  return `${valor.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%`;
}

/** "45 min", "2h", "8h 05min". */
export function duracao(minutos: number | null | undefined): string {
  if (minutos === null || minutos === undefined || minutos < 0) return '—';
  const total = Math.round(minutos);
  if (total < 60) return `${total} min`;
  const horas = Math.floor(total / 60);
  const resto = total % 60;
  return resto === 0 ? `${horas}h` : `${horas}h ${String(resto).padStart(2, '0')}min`;
}

/** Variação percentual com sinal explícito: "+12,5%", "−3%". */
export function variacao(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—';
  const texto = Math.abs(valor).toLocaleString('pt-BR', { maximumFractionDigits: 1 });
  if (valor > 0) return `+${texto}%`;
  if (valor < 0) return `−${texto}%`;
  return '0%';
}

/** Data ISO yyyy-MM-dd para exibição dd/MM, sem passar por fuso. */
export function diaMes(isoData: string): string {
  const [, mes, dia] = isoData.split('-');
  return `${dia}/${mes}`;
}

export interface BarraDia {
  data: string;
  altura: number;
  obras: number;
  valor: number;
  vazio: boolean;
  rotulo: string;
}

/** Barras da produção diária normalizadas pelo maior dia (altura em %, mínimo visível em dias com produção). */
export function barrasProducao(dias: readonly ProducaoDia[]): BarraDia[] {
  const maior = Math.max(0, ...dias.map((d) => d.valor ?? 0));
  return dias.map((dia) => {
    const valor = dia.valor ?? 0;
    const altura = maior > 0 ? Math.max(valor > 0 ? 6 : 0, Math.round((valor / maior) * 100)) : 0;
    return {
      data: dia.data,
      altura,
      obras: dia.obras,
      valor,
      vazio: dia.obras === 0,
      rotulo: `${diaMes(dia.data)}: ${dia.obras === 0 ? 'sem execução' : `${dia.obras} ${dia.obras === 1 ? 'obra' : 'obras'}, ${moeda(valor)}`}`
    };
  });
}

export interface JanelaTurno {
  inicio: number;
  fim: number;
}

/**
 * Janela da linha do tempo de um turno: da abertura (ou do primeiro lançamento, se
 * anterior) até o fechamento, ou até agora quando o turno segue aberto. Limitada ao
 * dia analisado.
 */
export function janelaDoTurno(
  equipe: Pick<EquipeTurno, 'abertura' | 'fechamento' | 'situacao' | 'lancamentos'>,
  dia: { inicio: number; fim: number },
  agora: number
): JanelaTurno | null {
  const tempos = equipe.lancamentos.map((l) => Date.parse(l.dataLancamento));
  const abertura = equipe.abertura ? Date.parse(equipe.abertura) : null;
  const candidatosInicio = [...tempos, ...(abertura !== null ? [abertura] : [])];
  if (!candidatosInicio.length) return null;

  const inicio = Math.max(dia.inicio, Math.min(...candidatosInicio));
  const referenciaFim = equipe.situacao === 'EM_TURNO'
    ? agora
    : equipe.fechamento
      ? Date.parse(equipe.fechamento)
      : Math.max(...tempos, inicio);
  const fim = Math.min(dia.fim, Math.max(referenciaFim, ...tempos, inicio + 60_000));
  return { inicio, fim };
}

export interface MarcaLancamento {
  idExecucao: string;
  posicao: number;
  hora: string;
  rotulo: string;
}

/** Posição (0–100%) de cada lançamento dentro da janela do turno. */
export function marcasDaLinhaDoTempo(lancamentos: readonly LancamentoTurno[], janela: JanelaTurno): MarcaLancamento[] {
  const extensao = Math.max(1, janela.fim - janela.inicio);
  return lancamentos.map((lancamento) => {
    const instante = Date.parse(lancamento.dataLancamento);
    const posicao = Math.min(100, Math.max(0, ((instante - janela.inicio) / extensao) * 100));
    const hora = horaCurta(lancamento.dataLancamento);
    return {
      idExecucao: lancamento.idExecucao,
      posicao: Math.round(posicao * 10) / 10,
      hora,
      rotulo: `Nota${lancamento.tipoServico ? ' de ' + lancamento.tipoServico : ''} lançada às ${hora}, ${moeda(lancamento.valor)}`
    };
  });
}

export function horaCurta(iso: string | null | undefined): string {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

export type OrdemOperacional = 'meta' | 'producao' | 'equipe';

const PESO_SITUACAO_META: Record<SituacaoMeta, number> = {
  ABAIXO_DO_RITMO: 0,
  NO_RITMO: 1,
  META_ATINGIDA: 2,
  SEM_META: 3
};

/** Ordena as equipes do dashboard operacional; "meta" põe primeiro quem precisa de atenção. */
export function ordenarEquipesOperacional(equipes: readonly EquipeOperacional[], ordem: OrdemOperacional): EquipeOperacional[] {
  const porCodigo = (a: EquipeOperacional, b: EquipeOperacional) => a.codigo.localeCompare(b.codigo, 'pt-BR');
  const copia = [...equipes];
  switch (ordem) {
    case 'producao':
      return copia.sort((a, b) => b.producao - a.producao || porCodigo(a, b));
    case 'equipe':
      return copia.sort(porCodigo);
    default:
      return copia.sort(
        (a, b) =>
          PESO_SITUACAO_META[a.situacao] - PESO_SITUACAO_META[b.situacao] ||
          (a.percentualMeta ?? Infinity) - (b.percentualMeta ?? Infinity) ||
          porCodigo(a, b)
      );
  }
}

const PESO_SITUACAO_TURNO: Record<SituacaoTurno, number> = { EM_TURNO: 0, ENCERRADO: 1, SEM_TURNO: 2 };

/** Equipes em turno sem lançar vêm primeiro (há mais tempo, antes); depois em turno, encerradas e sem turno. */
export function ordenarEquipesTurno(equipes: readonly EquipeTurno[]): EquipeTurno[] {
  return [...equipes].sort(
    (a, b) =>
      Number(b.semLancamentoRecente) - Number(a.semLancamentoRecente) ||
      (b.semLancamentoRecente ? (b.minutosSemLancamento ?? 0) - (a.minutosSemLancamento ?? 0) : 0) ||
      PESO_SITUACAO_TURNO[a.situacao] - PESO_SITUACAO_TURNO[b.situacao] ||
      a.codigo.localeCompare(b.codigo, 'pt-BR')
  );
}

export type AgrupamentoTurnos = 'nenhum' | 'base' | 'processo' | 'meta';

export interface GrupoDeEquipes {
  readonly chave: string;
  readonly rotulo: string;
  readonly icone: string;
  readonly equipes: EquipeTurno[];
  readonly emTurno: number;
  readonly producao: number;
  /** Produção das equipes com meta sobre a soma das metas do dia; `null` sem nenhuma meta. */
  readonly percentualMeta: number | null;
}

/** Faixas do agrupamento por meta, na ordem em que pedem atenção. */
const FAIXAS_DA_META: readonly { chave: string; rotulo: string; icone: string }[] = [
  { chave: 'danger', rotulo: 'Abaixo da meta do dia', icone: 'pi pi-times-circle' },
  { chave: 'warning', rotulo: 'Perto da meta do dia', icone: 'pi pi-exclamation-circle' },
  { chave: 'success', rotulo: 'Meta do dia batida', icone: 'pi pi-check-circle' },
  { chave: 'neutral', rotulo: 'Sem meta no dia', icone: 'pi pi-minus-circle' }
];

/**
 * Separa as equipes (já ordenadas) em grupos por base, processo ou faixa da meta do dia,
 * mantendo a ordem de cada uma dentro do grupo. Bases e processos vão em ordem alfabética,
 * com quem não tem a dimensão preenchida por último.
 */
export function agruparEquipesTurno(equipes: readonly EquipeTurno[], criterio: AgrupamentoTurnos): GrupoDeEquipes[] {
  if (criterio === 'nenhum') return equipes.length ? [grupo('todas', 'Todas as equipes', 'pi pi-users', equipes)] : [];

  if (criterio === 'meta') {
    return FAIXAS_DA_META.map((f) =>
      grupo(f.chave, f.rotulo, f.icone, equipes.filter((e) => tomDaMeta(e.percentualMetaDiaria) === f.chave))
    ).filter((g) => g.equipes.length);
  }

  const nome = (e: EquipeTurno) => (criterio === 'base' ? e.baseOperacional : e.processo);
  const nomes = [...new Set(equipes.map(nome))].sort((a, b) =>
    a === null ? 1 : b === null ? -1 : a.localeCompare(b, 'pt-BR')
  );
  const icone = criterio === 'base' ? 'pi pi-building' : 'pi pi-sitemap';
  const vazio = criterio === 'base' ? 'Sem base operacional' : 'Sem processo';
  return nomes.map((n) => grupo(n ?? '', n ?? vazio, icone, equipes.filter((e) => nome(e) === n)));
}

function grupo(chave: string, rotulo: string, icone: string, equipes: readonly EquipeTurno[]): GrupoDeEquipes {
  // A API omite campos nulos: meta ausente chega como `undefined`.
  const comMeta = equipes.filter((e) => e.metaDiaria != null);
  const meta = comMeta.reduce((t, e) => t + (e.metaDiaria ?? 0), 0);
  return {
    chave,
    rotulo,
    icone,
    equipes: [...equipes],
    emTurno: equipes.filter((e) => e.situacao === 'EM_TURNO').length,
    producao: equipes.reduce((t, e) => t + e.producao, 0),
    percentualMeta: meta > 0 ? (comMeta.reduce((t, e) => t + e.producao, 0) / meta) * 100 : null
  };
}

/** Primeiro nome + sobrenome final: "Maria Aparecida Souza" → "Maria Souza". */
export function nomeCurto(nome: string | null | undefined): string {
  if (!nome) return '';
  const partes = nome.trim().split(/\s+/);
  const capitalizar = (p: string) => p.charAt(0).toLocaleUpperCase('pt-BR') + p.slice(1).toLocaleLowerCase('pt-BR');
  return partes.length > 1
    ? `${capitalizar(partes[0])} ${capitalizar(partes[partes.length - 1])}`
    : capitalizar(partes[0]);
}

/** yyyy-MM-dd de uma data local (sem converter para UTC). */
export function isoLocal(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

/** Date local a partir de yyyy-MM-dd (meio-dia, para não virar o dia em nenhum fuso). */
export function dataLocal(iso: string): Date {
  const [ano, mes, dia] = iso.split('-').map(Number);
  return new Date(ano, mes - 1, dia, 12);
}

/** Um tipo de serviço resumido para os cartões de equipe. */
export interface TipoEmDestaque {
  rotulo: string;
  titulo: string;
  notas: number;
}

/**
 * Os tipos com mais notas de uma equipe; o que passa do limite vira "Outros".
 * Os tipos chegam do servidor já ordenados (mais notas primeiro, "não informado" por último).
 */
export function tiposEmDestaque(tipos: readonly ProducaoPorTipoServico[], limite = 3): TipoEmDestaque[] {
  const destaque = tipos.slice(0, limite).map((t) => ({
    rotulo: t.descricao,
    titulo: `${t.codigo ? t.codigo + ' · ' : ''}${t.descricao}: ${t.notas} ${t.notas === 1 ? 'nota' : 'notas'}, ${moeda(t.producao)}`,
    notas: t.notas
  }));
  const resto = tipos.slice(limite);
  if (!resto.length) return destaque;
  const notas = resto.reduce((total, t) => total + t.notas, 0);
  return [
    ...destaque,
    { rotulo: 'Outros', titulo: resto.map((t) => `${t.descricao}: ${t.notas}`).join(' · '), notas }
  ];
}

/** Uma obra resumida para os cartões de equipe. */
export interface ObraEmDestaque {
  rotulo: string;
  titulo: string;
  producao: number;
}

/** As obras de maior produção de uma equipe (já chegam ordenadas); o que passa do limite vira "Outras". */
export function obrasEmDestaque(obras: readonly ProducaoPorObra[], limite = 3): ObraEmDestaque[] {
  const destaque = obras.slice(0, limite).map((o) => ({
    rotulo: o.numero,
    titulo: `${o.numero}${o.titulo ? ' · ' + o.titulo : ''}: ${moeda(o.producao)}`,
    producao: o.producao
  }));
  const resto = obras.slice(limite);
  if (!resto.length) return destaque;
  return [
    ...destaque,
    {
      rotulo: `Outras ${resto.length}`,
      titulo: resto.map((o) => `${o.numero}: ${moeda(o.producao)}`).join(' · '),
      producao: resto.reduce((total, o) => total + o.producao, 0)
    }
  ];
}

export interface SerieGrafico {
  rotulos: string[];
  series: { name: string; values: (number | null)[] }[];
}

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

function arredondar(valor: number): number {
  return Math.round(valor * 100) / 100;
}

/** Produção e meta acumuladas dia a dia: a distância entre as linhas é o quanto falta (ou sobra). */
export function serieAcumulada(dias: readonly ProducaoDia[]): SerieGrafico {
  const temMeta = dias.some((d) => d.meta !== null);
  let producao = 0;
  let meta = 0;
  const producaoAcumulada: number[] = [];
  const metaAcumulada: number[] = [];
  for (const dia of dias) {
    producao += dia.valor ?? 0;
    meta += dia.meta ?? 0;
    producaoAcumulada.push(arredondar(producao));
    metaAcumulada.push(arredondar(meta));
  }
  return {
    rotulos: dias.map((d) => diaMes(d.data)),
    series: [
      { name: 'Produção acumulada', values: producaoAcumulada },
      ...(temMeta ? [{ name: 'Meta acumulada', values: metaAcumulada }] : [])
    ]
  };
}

/** Produção e meta somadas por mês (rótulo "out/26"). */
export function serieMensal(dias: readonly ProducaoDia[]): SerieGrafico {
  const temMeta = dias.some((d) => d.meta !== null);
  const meses = new Map<string, { producao: number; meta: number }>();
  for (const dia of dias) {
    const chave = dia.data.slice(0, 7);
    const acumulado = meses.get(chave) ?? { producao: 0, meta: 0 };
    acumulado.producao += dia.valor ?? 0;
    acumulado.meta += dia.meta ?? 0;
    meses.set(chave, acumulado);
  }
  const chaves = [...meses.keys()].sort();
  return {
    rotulos: chaves.map((c) => `${MESES[Number(c.slice(5, 7)) - 1]}/${c.slice(2, 4)}`),
    series: [
      { name: 'Produção', values: chaves.map((c) => arredondar(meses.get(c)!.producao)) },
      ...(temMeta ? [{ name: 'Meta', values: chaves.map((c) => arredondar(meses.get(c)!.meta)) }] : [])
    ]
  };
}

/** Produção e meta do período por equipe, da maior produção para a menor. */
export function seriePorEquipe(
  equipes: readonly Pick<EquipeOperacional, 'codigo' | 'producao' | 'metaPeriodo'>[]
): SerieGrafico {
  const ordenadas = [...equipes].sort((a, b) => b.producao - a.producao || a.codigo.localeCompare(b.codigo, 'pt-BR'));
  const temMeta = ordenadas.some((e) => e.metaPeriodo !== null);
  return {
    rotulos: ordenadas.map((e) => e.codigo),
    series: [
      { name: 'Produção', values: ordenadas.map((e) => e.producao) },
      ...(temMeta ? [{ name: 'Meta do período', values: ordenadas.map((e) => e.metaPeriodo) }] : [])
    ]
  };
}

/** Produção do dia e meta diária por equipe que abriu turno ou lançou nota. */
export function serieDoDiaPorEquipe(equipes: readonly EquipeTurno[]): SerieGrafico {
  const ativas = equipes
    .filter((e) => e.situacao !== 'SEM_TURNO' || e.notas > 0)
    .sort((a, b) => b.producao - a.producao || a.codigo.localeCompare(b.codigo, 'pt-BR'));
  const temMeta = ativas.some((e) => e.metaDiaria !== null);
  return {
    rotulos: ativas.map((e) => e.codigo),
    series: [
      { name: 'Produção do dia', values: ativas.map((e) => e.producao) },
      ...(temMeta ? [{ name: 'Meta diária', values: ativas.map((e) => e.metaDiaria) }] : [])
    ]
  };
}
