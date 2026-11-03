import { map } from 'rxjs';
import { CesDataTableColumn } from '@ces/ui';
import { NotaDoRecorte, NotasDoRecorteFiltros, ProducaoPorTipoServico } from '../../../../core/models/dashboard.models';
import { DashboardService } from '../../../../core/services/dashboard.service';
import {
  AcaoAnalise,
  DetalheRegistro,
  FiltroOrigem,
  GraficoAnalise,
  LinkAnalise,
  OrigemAnalise
} from '../../../../shared/components/dashboard/dashboard-analise.models';
import { duracao, moeda } from '../execucao-dashboards.utils';

/**
 * Peças comuns das análises (drill-down) dos dashboards de execução: a origem "notas que compõem
 * o número", o detalhe de uma nota e as agregações que só existem com as notas em mãos.
 */

/** Uma página basta para o drill: o backend limita a 500 e o restante segue na listagem completa. */
export const LIMITE_NOTAS = 500;
export const SEM_TIPO = 'sem-tipo';
const FUSO = 'America/Sao_Paulo';

const HORA = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, hour: '2-digit', hourCycle: 'h23' });
const HORA_MINUTO = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, hour: '2-digit', minute: '2-digit' });
const DIA_HORA = new Intl.DateTimeFormat('pt-BR', {
  timeZone: FUSO,
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit'
});
const DIA_ISO = new Intl.DateTimeFormat('en-CA', { timeZone: FUSO, year: 'numeric', month: '2-digit', day: '2-digit' });

/** Hora (0–23) de um instante no dia de São Paulo. */
export function horaDe(iso: string): number {
  return Number(HORA.format(new Date(iso)));
}

export function horaMinuto(iso: string | null | undefined): string {
  return iso ? HORA_MINUTO.format(new Date(iso)) : '—';
}

export function diaHora(iso: string | null | undefined): string {
  return iso ? DIA_HORA.format(new Date(iso)) : '—';
}

/** yyyy-MM-dd do instante no dia de São Paulo. */
export function diaDe(iso: string): string {
  return DIA_ISO.format(new Date(iso));
}

export function rotuloHora(hora: number): string {
  return `${String(hora).padStart(2, '0')}h`;
}

export function plural(n: number, um: string, varios: string): string {
  return `${n.toLocaleString('pt-BR')} ${n === 1 ? um : varios}`;
}

/** Chave de um tipo de serviço na análise; as notas sem tipo usam {@link SEM_TIPO}. */
export function chaveTipo(tipo: Pick<ProducaoPorTipoServico, 'idTipoServico'>): string {
  return tipo.idTipoServico ?? SEM_TIPO;
}

export function rotuloTipo(nota: Pick<NotaDoRecorte, 'codigoTipoServico' | 'tipoServico'>): string {
  if (!nota.tipoServico) return 'Não informado';
  return nota.codigoTipoServico ? `${nota.codigoTipoServico} · ${nota.tipoServico}` : nota.tipoServico;
}

/** Fatia (0–100) de `parte` em `total`; 0 sem total. */
export function fatia(parte: number, total: number): number {
  return total > 0 ? Math.round((parte / total) * 1000) / 10 : 0;
}

export function colunasNotas(opcoes: { equipe?: boolean } = {}): CesDataTableColumn<NotaDoRecorte>[] {
  return [
    {
      key: 'dataLancamento',
      header: 'Lançamento',
      priority: 1,
      width: '8.5rem',
      value: (n) => n.dataLancamento,
      display: (n) => diaHora(n.dataLancamento),
      searchable: false
    },
    { key: 'referenciaNota', header: 'Nota', priority: 1 },
    ...(opcoes.equipe !== false
      ? [{ key: 'codigoEquipe', header: 'Equipe', priority: 1 as const, display: (n: NotaDoRecorte) => n.codigoEquipe ?? '—' }]
      : []),
    { key: 'tipoServico', header: 'Tipo de serviço', priority: 2, value: (n) => rotuloTipo(n), display: (n) => rotuloTipo(n) },
    {
      key: 'acoes',
      header: 'Ações',
      priority: 2,
      value: (n) => n.acoes.length,
      display: (n) => (n.acoes.length ? `${n.acoes.length} · ${n.acoes[0]}${n.acoes.length > 1 ? '…' : ''}` : 'Nenhuma')
    },
    { key: 'materiais', header: 'Materiais', type: 'integer', priority: 3 },
    { key: 'valor', header: 'Valor', type: 'currency', priority: 1 }
  ];
}

/** Detalhe completo de uma nota, com o intervalo desde a nota anterior da mesma equipe. */
export function detalheNota(nota: NotaDoRecorte, todas: readonly NotaDoRecorte[]): DetalheRegistro {
  const daEquipe = todas
    .filter((n) => n.idEquipe === nota.idEquipe)
    .sort((a, b) => Date.parse(a.dataLancamento) - Date.parse(b.dataLancamento));
  const posicao = daEquipe.findIndex((n) => n.id === nota.id);
  const anterior = posicao > 0 ? daEquipe[posicao - 1] : null;
  const intervalo = anterior ? (Date.parse(nota.dataLancamento) - Date.parse(anterior.dataLancamento)) / 60_000 : null;
  const links: LinkAnalise[] = [
    { rotulo: 'Abrir nota completa', icone: 'pi pi-file', rota: ['/execucao/comercial', nota.id] }
  ];
  if (nota.idTurno) {
    links.push({ rotulo: 'Relatório do turno', icone: 'pi pi-file-pdf', rota: ['/execucao/turnos', nota.idTurno, 'detalhamento'] });
  }
  return {
    titulo: `Nota ${nota.referenciaNota}`,
    subtitulo: `${nota.codigoEquipe ?? 'Equipe'} · lançada em ${diaHora(nota.dataLancamento)}`,
    campos: [
      { rotulo: 'Valor faturado', valor: moeda(nota.valor), icone: 'pi pi-wallet' },
      { rotulo: 'Tipo de serviço', valor: rotuloTipo(nota), icone: 'pi pi-tag' },
      { rotulo: 'Equipe', valor: nota.codigoEquipe ?? '—', icone: 'pi pi-users' },
      { rotulo: 'Base operacional', valor: nota.baseOperacional ?? '—', icone: 'pi pi-building' },
      { rotulo: 'Processo', valor: nota.processo ?? '—', icone: 'pi pi-sitemap' },
      { rotulo: 'Materiais', valor: plural(nota.materiais, 'item', 'itens'), icone: 'pi pi-box' },
      {
        rotulo: 'Desde a nota anterior',
        valor: intervalo == null ? 'Primeira nota da equipe no recorte' : `${duracao(intervalo)} (às ${horaMinuto(anterior!.dataLancamento)})`,
        icone: 'pi pi-history'
      },
      {
        rotulo: 'Localização',
        valor: nota.latitude != null && nota.longitude != null ? `${nota.latitude.toFixed(5)}, ${nota.longitude.toFixed(5)}` : 'Não registrada',
        icone: 'pi pi-map-marker'
      }
    ],
    listas: [{ rotulo: `Ações executadas (${nota.acoes.length})`, itens: nota.acoes }],
    links
  };
}

export interface OpcoesOrigemNotas {
  readonly service: DashboardService;
  readonly filtros: NotasDoRecorteFiltros;
  readonly titulo?: string;
  readonly descricao?: string;
  readonly esperado?: { notas: number; valor: number | null };
  readonly filtroInicial?: FiltroOrigem<NotaDoRecorte>;
  readonly derivados?: (notas: readonly NotaDoRecorte[]) => readonly GraficoAnalise[];
  readonly verTudo?: LinkAnalise;
  readonly mostrarEquipe?: boolean;
  readonly nomeArquivo: string;
}

/** As notas concluídas que somam o número analisado, com conferência contra o painel. */
export function origemNotas(o: OpcoesOrigemNotas): OrigemAnalise<NotaDoRecorte> {
  const filtros: NotasDoRecorteFiltros = { ...o.filtros, page: 0, size: LIMITE_NOTAS, sort: ['dataLancamento,desc'] };
  return {
    chave: JSON.stringify(o.filtros),
    titulo: o.titulo ?? 'Notas que compõem este número',
    rotuloAba: 'Origem · notas',
    icone: 'pi pi-file-check',
    descricao:
      o.descricao ??
      'Notas concluídas das equipes do recorte, as mesmas que o painel soma. Clique numa nota para ver as ações, o intervalo desde a anterior e os atalhos.',
    colunas: colunasNotas({ equipe: o.mostrarEquipe }),
    carregar: () =>
      o.service.consultarNotas(filtros).pipe(map((res) => ({ linhas: res.content, total: res.page.totalElements }))),
    chaveLinha: (n) => n.id,
    rotuloLinha: (n) => `Nota ${n.referenciaNota}`,
    filtroInicial: o.filtroInicial,
    conferencia: o.esperado
      ? { quantidadeEsperada: o.esperado.notas, valorEsperado: o.esperado.valor, somar: (n) => n.valor ?? 0, formato: 'moeda' }
      : undefined,
    derivados: o.derivados,
    detalhar: detalheNota,
    verTudo: o.verTudo,
    nomeArquivo: o.nomeArquivo,
    vazio: 'Nenhuma nota concluída neste recorte'
  };
}

/** Link para a listagem de notas com o que ela sabe filtrar (equipe, período e tipo). */
export function linkListagemNotas(p: { idEquipe?: string; inicio?: string; fim?: string; tipo?: string | null }): LinkAnalise {
  return {
    rotulo: 'Ver na listagem de notas',
    icone: 'pi pi-external-link',
    rota: '/execucao/comercial',
    queryParams: {
      equipe: p.idEquipe ?? null,
      lancamentoDe: p.inicio ?? null,
      lancamentoAte: p.fim ?? null,
      tipo: p.tipo ?? null,
      res: '1'
    }
  };
}

// ------------------------------------------------------------------ agregações das notas

export interface Agrupado {
  chave: string;
  rotulo: string;
  notas: number;
  valor: number;
}

export function agrupar(
  notas: readonly NotaDoRecorte[],
  chave: (n: NotaDoRecorte) => string,
  rotulo: (n: NotaDoRecorte) => string
): Agrupado[] {
  const mapa = new Map<string, Agrupado>();
  for (const nota of notas) {
    const k = chave(nota);
    const item = mapa.get(k) ?? { chave: k, rotulo: rotulo(nota), notas: 0, valor: 0 };
    item.notas += 1;
    item.valor += nota.valor ?? 0;
    mapa.set(k, item);
  }
  return [...mapa.values()];
}

/** Notas e valor por hora, da primeira à última hora com lançamento (horas vazias entram zeradas). */
export function porHora(notas: readonly NotaDoRecorte[]): { horas: number[]; notas: number[]; valor: number[] } {
  if (!notas.length) return { horas: [], notas: [], valor: [] };
  const horasDasNotas = notas.map((n) => horaDe(n.dataLancamento));
  const primeira = Math.min(...horasDasNotas);
  const ultima = Math.max(...horasDasNotas);
  const horas = Array.from({ length: ultima - primeira + 1 }, (_, i) => primeira + i);
  const contagem = horas.map(() => 0);
  const valor = horas.map(() => 0);
  notas.forEach((n, i) => {
    const idx = horasDasNotas[i] - primeira;
    contagem[idx] += 1;
    valor[idx] += n.valor ?? 0;
  });
  return { horas, notas: contagem, valor: valor.map((v) => Math.round(v * 100) / 100) };
}

/** Faixas de valor das notas (histograma): mostra se o resultado vem de muitas notas pequenas ou de poucas grandes. */
export function faixasDeValor(valores: readonly number[]): { rotulos: string[]; contagem: number[]; limites: [number, number][] } {
  const limites: [number, number][] = [
    [0, 100],
    [100, 250],
    [250, 500],
    [500, 1000],
    [1000, 2500],
    [2500, Infinity]
  ];
  const rotulos = ['Até R$ 100', 'R$ 100–250', 'R$ 250–500', 'R$ 500–1 mil', 'R$ 1–2,5 mil', 'Acima de R$ 2,5 mil'];
  const contagem = limites.map(([de, ate]) => valores.filter((v) => v >= de && v < ate).length);
  return { rotulos, contagem, limites };
}

/** Gráficos derivados das notas, comuns a quase toda análise: ritmo por hora e faixas de valor. */
export function graficosDasNotas(
  notas: readonly NotaDoRecorte[],
  opcoes: { porHora?: boolean; faixas?: boolean; aoSelecionarHora?: (hora: number) => AcaoAnalise | null } = {}
): GraficoAnalise[] {
  if (!notas.length) return [];
  const graficos: GraficoAnalise[] = [];
  if (opcoes.porHora !== false) {
    const h = porHora(notas);
    graficos.push({
      id: 'notas-por-hora',
      titulo: 'Lançamentos por hora',
      descricao: 'Quantas notas entraram em cada hora do dia e quanto elas valeram. Clique numa hora para ver as notas.',
      tipo: 'bar',
      rotulos: h.horas.map(rotuloHora),
      series: [{ name: 'Notas', values: h.notas }],
      formato: 'integer',
      altura: 220,
      aoSelecionar: (i) =>
        opcoes.aoSelecionarHora?.(h.horas[i]) ?? {
          tipo: 'filtrar',
          filtro: { chave: `hora-${h.horas[i]}`, rotulo: `Lançadas das ${rotuloHora(h.horas[i])} às ${rotuloHora(h.horas[i] + 1)}`, aceita: (n: NotaDoRecorte) => horaDe(n.dataLancamento) === h.horas[i] }
        },
      rotuloSelecao: 'Ver notas da hora'
    });
  }
  if (opcoes.faixas !== false) {
    const f = faixasDeValor(notas.map((n) => n.valor ?? 0));
    graficos.push({
      id: 'faixas-de-valor',
      titulo: 'Notas por faixa de valor',
      descricao: 'Distribuição do valor das notas. Clique numa faixa para listar as notas dela.',
      tipo: 'bar',
      rotulos: f.rotulos,
      series: [{ name: 'Notas', values: f.contagem }],
      formato: 'integer',
      altura: 220,
      aoSelecionar: (i) => ({
        tipo: 'filtrar',
        filtro: {
          chave: `faixa-${i}`,
          rotulo: `Valor: ${f.rotulos[i]}`,
          aceita: (n: NotaDoRecorte) => (n.valor ?? 0) >= f.limites[i][0] && (n.valor ?? 0) < f.limites[i][1]
        }
      }),
      rotuloSelecao: 'Ver notas da faixa'
    });
  }
  return graficos;
}
