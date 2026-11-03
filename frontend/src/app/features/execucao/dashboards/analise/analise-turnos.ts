import { CesDataTableColumn } from '@ces/ui';
import {
  DashboardTurnosFiltros,
  DashboardTurnosResponse,
  EquipeTurno,
  LancamentoTurno,
  NotaDoRecorte
} from '../../../../core/models/dashboard.models';
import { DashboardService } from '../../../../core/services/dashboard.service';
import {
  AlvoAnalise,
  DestaqueAnalise,
  GraficoAnalise,
  VistaAnalise,
  chaveDoAlvo
} from '../../../../shared/components/dashboard/dashboard-analise.models';
import {
  SITUACAO_TURNO,
  dataLocal,
  diaMes,
  duracao,
  moeda,
  moedaCompacta,
  nomeCurto,
  ordenarEquipesTurno,
  percentual,
  tomDaMeta
} from '../execucao-dashboards.utils';
import {
  SEM_TIPO,
  diaDe,
  diaHora,
  faixasDeValor,
  graficosDasNotas,
  horaDe,
  horaMinuto,
  linkListagemNotas,
  origemNotas,
  plural,
  rotuloHora
} from './analise-comum';
import { chaveTipo } from './analise-comum';

/**
 * Análises (drill-down) do dashboard de turnos — um dia de operação. Os lançamentos de cada equipe já
 * vêm na resposta do dashboard, então os gráficos por hora aparecem na hora; as notas completas
 * (referência, ações) vêm da origem.
 */

export interface ContextoAnaliseTurnos {
  readonly d: DashboardTurnosResponse;
  readonly filtros: DashboardTurnosFiltros;
  readonly service: DashboardService;
  readonly nomeBase: string | null;
  readonly nomeProcesso: string | null;
}

interface LancamentoDaEquipe extends LancamentoTurno {
  readonly idEquipe: string;
  readonly codigo: string;
}

const alvo = (dimension: string, value: string): AlvoAnalise => ({ dimension, value });
const navegar = (dimension: string, value: string) => ({ tipo: 'navegar' as const, alvo: alvo(dimension, value) });

function diaPorExtenso(iso: string): string {
  return dataLocal(iso).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' });
}

function recorteTexto(c: ContextoAnaliseTurnos): string {
  return `${diaPorExtenso(c.d.data)} · ${c.nomeBase ?? 'todas as bases'} · ${c.nomeProcesso ?? 'todos os processos'}`;
}

/** A consulta de turnos lê a seleção múltipla separada por vírgula na URL. */
function naConsulta(selecao: readonly string[] | undefined): string | null {
  return selecao?.length ? selecao.join(',') : null;
}

function filtrosDoDia(c: ContextoAnaliseTurnos) {
  return { inicio: c.d.data, fim: c.d.data, idBaseOperacional: c.filtros.idBaseOperacional, idProcesso: c.filtros.idProcesso };
}

function verTudo(c: ContextoAnaliseTurnos, extra: { idEquipe?: string; tipo?: string | null } = {}) {
  if (!extra.idEquipe && (c.filtros.idBaseOperacional?.length || c.filtros.idProcesso?.length)) return undefined;
  return linkListagemNotas({ inicio: c.d.data, fim: c.d.data, ...extra });
}

function alturaBarras(n: number): number {
  return Math.max(220, Math.min(n, 30) * 28 + 60);
}

function lancamentos(d: DashboardTurnosResponse): LancamentoDaEquipe[] {
  return d.equipes
    .flatMap((e) => e.lancamentos.map((l) => ({ ...l, idEquipe: e.idEquipe, codigo: e.codigo })))
    .sort((a, b) => Date.parse(a.dataLancamento) - Date.parse(b.dataLancamento));
}

/** Horas (inteiras) com lançamento, da primeira à última, com notas, valor e acumulado. */
function serieHoraria(lista: readonly LancamentoTurno[]): { horas: number[]; notas: number[]; valor: number[]; acumulado: number[] } {
  if (!lista.length) return { horas: [], notas: [], valor: [], acumulado: [] };
  const hs = lista.map((l) => horaDe(l.dataLancamento));
  const primeira = Math.min(...hs);
  const horas = Array.from({ length: Math.max(...hs) - primeira + 1 }, (_, i) => primeira + i);
  const notas = horas.map(() => 0);
  const valor = horas.map(() => 0);
  lista.forEach((l, i) => {
    notas[hs[i] - primeira] += 1;
    valor[hs[i] - primeira] += l.valor ?? 0;
  });
  let soma = 0;
  const acumulado = valor.map((v) => Math.round((soma += v) * 100) / 100);
  return { horas, notas, valor: valor.map((v) => Math.round(v * 100) / 100), acumulado };
}

function situacaoTexto(e: EquipeTurno, d: DashboardTurnosResponse): string {
  switch (e.situacao) {
    case 'EM_TURNO':
      return e.abertoDesdeOutroDia ? 'Em turno desde outro dia' : `Em turno desde ${horaMinuto(e.abertura)}`;
    case 'ENCERRADO':
      return `Encerrado às ${horaMinuto(e.fechamento)}`;
    default:
      return d.hoje ? 'Não abriu turno' : 'Sem turno';
  }
}

function colunasTurnos(d: DashboardTurnosResponse): CesDataTableColumn<EquipeTurno>[] {
  return [
    { key: 'codigo', header: 'Equipe', priority: 1 },
    { key: 'situacao', header: 'Situação', priority: 1, value: (e) => situacaoTexto(e, d) },
    { key: 'abertura', header: 'Abertura', priority: 2, value: (e) => e.abertura ?? '', display: (e) => horaMinuto(e.abertura) },
    { key: 'fechamento', header: 'Fechamento', priority: 3, value: (e) => e.fechamento ?? '', display: (e) => horaMinuto(e.fechamento) },
    { key: 'minutosEmTurno', header: 'Em turno', priority: 2, value: (e) => e.minutosEmTurno, display: (e) => duracao(e.minutosEmTurno) },
    { key: 'abertoPor', header: 'Encarregado', priority: 3, display: (e) => (e.abertoPor ? nomeCurto(e.abertoPor) : '—') },
    { key: 'membros', header: 'Pessoas', type: 'integer', priority: 3, value: (e) => e.membros.length },
    { key: 'placa', header: 'Veículo', priority: 3, display: (e) => e.placa ?? '—' },
    { key: 'notas', header: 'Notas', type: 'integer', priority: 2 },
    { key: 'producao', header: 'Produção', type: 'currency', priority: 1 },
    { key: 'percentualMetaDiaria', header: '% meta do dia', type: 'percent', priority: 2 }
  ];
}

function tabelaTurnos(d: DashboardTurnosResponse, linhas: readonly EquipeTurno[], titulo: string, id = 'turnos') {
  return {
    id,
    titulo,
    icone: 'pi pi-clock',
    descricao: 'Composição confirmada na abertura do turno. Clique numa equipe para abrir a análise dela.',
    colunas: colunasTurnos(d),
    linhas,
    chaveLinha: (e: EquipeTurno) => e.idEquipe,
    rotuloLinha: (e: EquipeTurno) => `Equipe ${e.codigo}`,
    aoAtivar: (e: EquipeTurno) => navegar('equipe', e.idEquipe),
    rotuloAcao: 'Analisar',
    nomeArquivo: `turnos-${d.data}`
  };
}

function graficoProducaoPorEquipe(d: DashboardTurnosResponse): GraficoAnalise {
  const ativas = d.equipes.filter((e) => e.situacao !== 'SEM_TURNO' || e.notas > 0).sort((a, b) => b.producao - a.producao);
  return {
    id: 'producao-por-equipe',
    titulo: 'Produção do dia × meta diária',
    descricao: 'Clique numa equipe para abrir a análise dela.',
    tipo: 'bar-horizontal',
    rotulos: ativas.map((e) => e.codigo),
    series: [
      { name: 'Produção', values: ativas.map((e) => e.producao) },
      ...(ativas.some((e) => e.metaDiaria != null) ? [{ name: 'Meta diária', values: ativas.map((e) => e.metaDiaria) }] : [])
    ],
    formato: 'currency',
    altura: alturaBarras(ativas.length),
    aoSelecionar: (i) => navegar('equipe', ativas[i].idEquipe),
    rotuloSelecao: 'Analisar equipe'
  };
}

export function vistaTurnos(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise | null {
  switch (a.dimension) {
    case 'kpi':
      switch (a.value) {
        case 'turnos': return vistaSituacao(a, c);
        case 'producao': return vistaProducao(a, c);
        case 'sem-lancamento': return vistaSemLancamento(a, c);
        case 'ticket': return vistaTicket(a, c);
        case 'sem-turno': return vistaSemTurno(a, c);
        default: return null;
      }
    case 'equipe': return vistaEquipe(a, c);
    case 'hora': return vistaHora(a, c);
    case 'tipo': return vistaTipo(a, c);
    default: return null;
  }
}

// ------------------------------------------------------------------ cartões

function vistaSituacao(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const comTurno = ordenarEquipesTurno(d.equipes.filter((e) => e.situacao !== 'SEM_TURNO'));
  const encerrados = d.equipes.filter((e) => e.situacao === 'ENCERRADO');
  const duracaoMedia = encerrados.length ? encerrados.reduce((t, e) => t + e.minutosEmTurno, 0) / encerrados.length : null;
  const pessoas = comTurno.reduce((t, e) => t + e.membros.length, 0);
  const aberturas = comTurno.filter((e) => e.abertura && !e.abertoDesdeOutroDia);
  const porHoraAbertura = new Map<number, number>();
  aberturas.forEach((e) => porHoraAbertura.set(horaDe(e.abertura!), (porHoraAbertura.get(horaDe(e.abertura!)) ?? 0) + 1));
  const horasAbertura = [...porHoraAbertura.keys()].sort((x, y) => x - y);
  const porTempo = [...comTurno].sort((x, y) => y.minutosEmTurno - x.minutosEmTurno);
  const situacoes = [
    { rotulo: 'Em turno', total: r.emTurno, alvo: null },
    { rotulo: 'Encerrado', total: r.encerrados, alvo: null },
    { rotulo: 'Sem turno', total: r.semTurno, alvo: navegar('kpi', 'sem-turno') }
  ].filter((s) => s.total > 0);

  const destaques: DestaqueAnalise[] = [];
  if (r.turnosAbertosDeOutroDia > 0) {
    destaques.push({ tom: 'warning', icone: 'pi pi-exclamation-triangle', texto: `${plural(r.turnosAbertosDeOutroDia, 'turno aberto', 'turnos abertos')} desde outro dia e ainda não ${r.turnosAbertosDeOutroDia === 1 ? 'fechado' : 'fechados'} — provável esquecimento.` });
  }
  if (aberturas.length) {
    const primeira = aberturas.reduce((m, e) => (Date.parse(e.abertura!) < Date.parse(m.abertura!) ? e : m));
    destaques.push({ tom: 'neutral', icone: 'pi pi-sun', texto: `Primeira abertura: ${primeira.codigo} às ${horaMinuto(primeira.abertura)}.` });
  }

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Turnos do dia',
    titulo: `${r.emTurno + r.encerrados} de ${r.equipesAtivas} equipes com turno`,
    descricao: recorteTexto(c),
    kpis: [
      { rotulo: d.hoje ? 'Em turno agora' : 'Em turno', valor: r.emTurno.toLocaleString('pt-BR'), tom: 'brand', icone: 'pi pi-play-circle' },
      { rotulo: 'Encerrados', valor: r.encerrados.toLocaleString('pt-BR'), icone: 'pi pi-stop-circle' },
      { rotulo: 'Sem turno', valor: r.semTurno.toLocaleString('pt-BR'), icone: 'pi pi-user-minus', tom: r.semTurno && d.diaUtil ? 'warning' : 'neutral' },
      { rotulo: 'Pessoas em campo', valor: pessoas.toLocaleString('pt-BR'), icone: 'pi pi-users', detalhe: 'na composição de abertura' },
      { rotulo: 'Duração média', valor: duracao(duracaoMedia), icone: 'pi pi-stopwatch', detalhe: 'dos turnos encerrados' }
    ],
    destaques,
    graficos: [
      {
        id: 'situacao-turnos',
        titulo: 'Situação das equipes',
        descricao: 'Clique em "Sem turno" para ver quem não abriu.',
        tipo: 'doughnut',
        rotulos: situacoes.map((s) => s.rotulo),
        series: [{ name: 'Equipes', values: situacoes.map((s) => s.total) }],
        formato: 'integer',
        altura: 230,
        aoSelecionar: (i) => situacoes[i].alvo,
        rotuloSelecao: 'Ver equipes'
      },
      {
        id: 'aberturas-por-hora',
        titulo: 'Aberturas por hora',
        descricao: 'A que horas as equipes começaram o turno.',
        tipo: 'bar',
        rotulos: horasAbertura.map(rotuloHora),
        series: [{ name: 'Turnos abertos', values: horasAbertura.map((h) => porHoraAbertura.get(h)!) }],
        formato: 'integer',
        altura: 230
      },
      {
        id: 'tempo-em-turno',
        titulo: 'Horas em turno por equipe',
        descricao: 'Clique numa equipe para abrir a análise dela.',
        tipo: 'bar-horizontal',
        rotulos: porTempo.map((e) => e.codigo),
        series: [{ name: 'Horas em turno', values: porTempo.map((e) => Math.round((e.minutosEmTurno / 60) * 10) / 10) }],
        formato: 'number',
        altura: alturaBarras(porTempo.length),
        inteiro: true,
        aoSelecionar: (i) => navegar('equipe', porTempo[i].idEquipe),
        rotuloSelecao: 'Analisar equipe'
      }
    ],
    tabelas: [tabelaTurnos(d, comTurno, 'Turnos')],
    links: [{ rotulo: 'Consulta de turnos', icone: 'pi pi-external-link', rota: '/execucao/turnos', queryParams: { aberturaDe: d.data, aberturaAte: d.data, base: naConsulta(c.filtros.idBaseOperacional), processo: naConsulta(c.filtros.idProcesso), res: '1' } }],
    copiloto: {
      foco: { tipo: 'metrica', id: 'emTurno', rotulo: 'Turnos do dia' },
      pergunta: `Em ${diaPorExtenso(d.data)}, ${r.emTurno} equipes estão em turno, ${r.encerrados} encerraram e ${r.semTurno} não abriram. Como foram os horários de abertura e a duração dos turnos, e há algo fora do padrão?`
    }
  };
}

function vistaProducao(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const todos = lancamentos(d);
  const h = serieHoraria(todos);
  const lancaram = d.equipes.filter((e) => e.notas > 0).length;
  const tipos = d.tiposServico;
  const horaPico = h.notas.length ? h.horas[h.notas.indexOf(Math.max(...h.notas))] : null;

  const destaques: DestaqueAnalise[] = [];
  if (r.percentualMetaDiaria != null) {
    const falta = (r.metaDiaria ?? 0) - r.producaoEquipesComMeta;
    destaques.push({
      tom: falta <= 0 ? 'success' : 'info',
      icone: 'pi pi-flag',
      texto: falta <= 0 ? 'As equipes com meta já bateram a meta do dia.' : `Faltam ${moedaCompacta(falta)} para as equipes com meta baterem a meta do dia.`
    });
  }
  if (horaPico != null) destaques.push({ tom: 'neutral', icone: 'pi pi-clock', texto: `Pico de lançamentos às ${rotuloHora(horaPico)}.` });

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Produção do dia',
    titulo: `${moeda(r.producao)} em ${plural(r.notas, 'nota', 'notas')}`,
    descricao: recorteTexto(c),
    kpis: [
      { rotulo: 'Produção', valor: moeda(r.producao), tom: 'brand', icone: 'pi pi-wallet' },
      { rotulo: 'Meta diária', valor: moedaCompacta(r.metaDiaria), icone: 'pi pi-flag', detalhe: r.metaDiaria != null ? `${r.equipesComMeta} equipes com meta` : d.diaUtil ? 'nenhuma equipe com meta' : 'dia não útil' },
      { rotulo: 'Meta atingida', valor: percentual(r.percentualMetaDiaria), icone: 'pi pi-percentage', tom: tomDaMeta(r.percentualMetaDiaria) },
      { rotulo: 'Notas', valor: r.notas.toLocaleString('pt-BR'), icone: 'pi pi-file-check', detalhe: r.notas ? `${moedaCompacta(r.producao / r.notas)} por nota` : undefined },
      { rotulo: 'Equipes que lançaram', valor: `${lancaram} de ${r.equipesAtivas}`, icone: 'pi pi-users' }
    ],
    destaques,
    graficos: [
      {
        id: 'producao-acumulada-hora',
        titulo: 'Produção acumulada ao longo do dia',
        descricao: 'Clique numa hora para ver as notas lançadas nela.',
        tipo: 'line',
        rotulos: h.horas.map(rotuloHora),
        series: [
          { name: 'Produção acumulada', values: h.acumulado },
          ...(r.metaDiaria != null ? [{ name: 'Meta diária', values: h.horas.map(() => r.metaDiaria) }] : [])
        ],
        formato: 'currency',
        altura: 250,
        inteiro: true,
        aoSelecionar: (i) => navegar('hora', String(h.horas[i])),
        rotuloSelecao: 'Analisar hora'
      },
      {
        id: 'notas-por-hora',
        titulo: 'Notas por hora',
        descricao: 'Clique numa hora para analisá-la.',
        tipo: 'bar',
        rotulos: h.horas.map(rotuloHora),
        series: [{ name: 'Notas', values: h.notas }],
        formato: 'integer',
        altura: 230,
        aoSelecionar: (i) => navegar('hora', String(h.horas[i])),
        rotuloSelecao: 'Analisar hora'
      },
      {
        id: 'producao-por-tipo',
        titulo: 'Produção por tipo de serviço',
        descricao: 'Clique numa fatia para analisar o tipo.',
        tipo: 'doughnut',
        rotulos: tipos.map((t) => t.descricao),
        series: [{ name: 'Produção', values: tipos.map((t) => t.producao) }],
        formato: 'currency',
        altura: 230,
        aoSelecionar: (i) => navegar('tipo', chaveTipo(tipos[i])),
        rotuloSelecao: 'Analisar tipo'
      },
      { ...graficoProducaoPorEquipe(d), inteiro: true }
    ],
    origem: origemNotas({
      service: c.service,
      filtros: filtrosDoDia(c),
      esperado: { notas: r.notas, valor: r.producao },
      derivados: (notas) => graficosDasNotas(notas, { porHora: false }),
      verTudo: verTudo(c),
      nomeArquivo: `notas-${d.data}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'producao', rotulo: 'Produção do dia' },
      pergunta: `A produção de ${diaPorExtenso(d.data)} está em ${moeda(r.producao)} (${r.notas} notas, ${percentual(r.percentualMetaDiaria)} da meta diária). Como o ritmo evoluiu ao longo do dia e quais equipes estão puxando o resultado para cima ou para baixo?`
    }
  };
}

function vistaSemLancamento(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const emTurno = d.equipes
    .filter((e) => e.situacao === 'EM_TURNO')
    .sort((x, y) => (y.minutosSemLancamento ?? 0) - (x.minutosSemLancamento ?? 0));
  const pior = emTurno[0];
  const colunas: CesDataTableColumn<EquipeTurno>[] = [
    { key: 'codigo', header: 'Equipe', priority: 1 },
    { key: 'minutosSemLancamento', header: 'Sem lançar há', priority: 1, value: (e) => e.minutosSemLancamento ?? -1, display: (e) => duracao(e.minutosSemLancamento) },
    { key: 'ultimoLancamento', header: 'Último lançamento', priority: 2, value: (e) => e.ultimoLancamento ?? '', display: (e) => (e.ultimoLancamento ? horaMinuto(e.ultimoLancamento) : 'Nenhum desde a abertura') },
    { key: 'abertura', header: 'Em turno desde', priority: 2, value: (e) => e.abertura ?? '', display: (e) => horaMinuto(e.abertura) },
    { key: 'notas', header: 'Notas', type: 'integer', priority: 2 },
    { key: 'producao', header: 'Produção', type: 'currency', priority: 2 },
    { key: 'abertoPor', header: 'Encarregado', priority: 3, display: (e) => (e.abertoPor ? nomeCurto(e.abertoPor) : '—') }
  ];

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Tempo sem lançar',
    titulo: `${plural(r.semLancamentoRecente, 'equipe', 'equipes')} sem lançar há ${duracao(d.limiteSemLancamentoMinutos)} ou mais`,
    descricao: recorteTexto(c),
    kpis: [
      { rotulo: `Sem lançar há ${duracao(d.limiteSemLancamentoMinutos)}+`, valor: r.semLancamentoRecente.toLocaleString('pt-BR'), icone: 'pi pi-hourglass', tom: r.semLancamentoRecente ? 'warning' : 'success' },
      { rotulo: 'Maior tempo sem lançar', valor: pior ? duracao(pior.minutosSemLancamento) : '—', icone: 'pi pi-clock', detalhe: pior?.codigo },
      { rotulo: 'Em turno', valor: r.emTurno.toLocaleString('pt-BR'), icone: 'pi pi-play-circle' }
    ],
    destaques: emTurno.filter((e) => e.semLancamentoRecente).slice(0, 3).map((e) => ({
      tom: 'warning' as const,
      icone: 'pi pi-hourglass',
      texto: `${e.codigo}: ${duracao(e.minutosSemLancamento)} sem lançar ${e.ultimoLancamento ? `(última nota às ${horaMinuto(e.ultimoLancamento)})` : '(nenhuma nota desde a abertura)'}${e.abertoPor ? ` · encarregado ${nomeCurto(e.abertoPor)}` : ''}.`
    })),
    graficos: [{
      id: 'horas-sem-lancar',
      titulo: 'Horas sem lançar, por equipe em turno',
      descricao: 'Clique numa equipe para ver a linha do tempo do turno.',
      tipo: 'bar-horizontal',
      rotulos: emTurno.map((e) => e.codigo),
      series: [{ name: 'Horas sem lançar', values: emTurno.map((e) => Math.round(((e.minutosSemLancamento ?? 0) / 60) * 10) / 10) }],
      formato: 'number',
      altura: alturaBarras(emTurno.length),
      inteiro: true,
      aoSelecionar: (i) => navegar('equipe', emTurno[i].idEquipe),
      rotuloSelecao: 'Analisar equipe'
    }],
    tabelas: [{
      id: 'em-turno',
      titulo: 'Equipes em turno',
      icone: 'pi pi-play-circle',
      colunas,
      linhas: emTurno,
      chaveLinha: (e: EquipeTurno) => e.idEquipe,
      rotuloLinha: (e: EquipeTurno) => `Equipe ${e.codigo}`,
      aoAtivar: (e: EquipeTurno) => navegar('equipe', e.idEquipe),
      rotuloAcao: 'Analisar',
      nomeArquivo: `sem-lancar-${d.data}`
    }],
    copiloto: {
      foco: { tipo: 'metrica', id: 'semLancamentoRecente', rotulo: 'Equipes sem lançar' },
      pergunta: `${r.semLancamentoRecente} equipes em turno estão sem lançar nota há ${duracao(d.limiteSemLancamentoMinutos)} ou mais. Quais são, há quanto tempo, e esse comportamento se repete nos últimos dias?`
    }
  };
}

function vistaTicket(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const valores = lancamentos(d).map((l) => l.valor ?? 0);
  const f = faixasDeValor(valores);
  const comNotas = d.equipes.filter((e) => e.notas > 0).sort((x, y) => y.valorMedioNota - x.valorMedioNota);

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Valor médio por nota',
    titulo: r.notas ? `${moeda(r.producao / r.notas)} por nota` : 'Sem notas no dia',
    descricao: recorteTexto(c),
    kpis: [
      { rotulo: 'Valor médio', valor: r.notas ? moeda(r.producao / r.notas) : '—', tom: 'brand', icone: 'pi pi-tag' },
      { rotulo: 'Notas', valor: r.notas.toLocaleString('pt-BR'), icone: 'pi pi-file-check' },
      { rotulo: 'Maior nota', valor: valores.length ? moedaCompacta(Math.max(...valores)) : '—', icone: 'pi pi-arrow-up' },
      { rotulo: 'Menor nota', valor: valores.length ? moedaCompacta(Math.min(...valores)) : '—', icone: 'pi pi-arrow-down' }
    ],
    graficos: [
      {
        id: 'faixas-de-valor',
        titulo: 'Notas por faixa de valor',
        descricao: 'Clique numa faixa para listar as notas dela.',
        tipo: 'bar',
        rotulos: f.rotulos,
        series: [{ name: 'Notas', values: f.contagem }],
        formato: 'integer',
        altura: 230,
        aoSelecionar: (i) => ({
          tipo: 'filtrar',
          filtro: { chave: `faixa-${i}`, rotulo: `Valor: ${f.rotulos[i]}`, aceita: (n: NotaDoRecorte) => (n.valor ?? 0) >= f.limites[i][0] && (n.valor ?? 0) < f.limites[i][1] }
        }),
        rotuloSelecao: 'Ver notas da faixa'
      },
      {
        id: 'valor-medio-por-equipe',
        titulo: 'Valor médio por nota, por equipe',
        tipo: 'bar-horizontal',
        rotulos: comNotas.map((e) => e.codigo),
        series: [{ name: 'Valor médio', values: comNotas.map((e) => e.valorMedioNota) }],
        formato: 'currency',
        altura: alturaBarras(comNotas.length),
        aoSelecionar: (i) => navegar('equipe', comNotas[i].idEquipe),
        rotuloSelecao: 'Analisar equipe'
      }
    ],
    origem: origemNotas({
      service: c.service,
      filtros: filtrosDoDia(c),
      esperado: { notas: r.notas, valor: r.producao },
      verTudo: verTudo(c),
      nomeArquivo: `notas-${d.data}`
    }),
    copiloto: {
      foco: { tipo: 'metrica', id: 'valorMedioNota', rotulo: 'Valor médio por nota' },
      pergunta: `O valor médio por nota em ${diaPorExtenso(d.data)} foi ${r.notas ? moeda(r.producao / r.notas) : '—'}. Quais equipes e tipos de serviço puxam esse valor para cima ou para baixo?`
    }
  };
}

function vistaSemTurno(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise {
  const { d } = c;
  const r = d.resumo;
  const semTurno = d.equipes.filter((e) => e.situacao === 'SEM_TURNO').sort((x, y) => x.codigo.localeCompare(y.codigo, 'pt-BR'));
  const metaParada = semTurno.reduce((t, e) => t + (e.metaDiaria ?? 0), 0);
  const porBase = new Map<string, number>();
  semTurno.forEach((e) => porBase.set(e.baseOperacional ?? 'Sem base', (porBase.get(e.baseOperacional ?? 'Sem base') ?? 0) + 1));
  const bases = [...porBase.entries()].sort((x, y) => y[1] - x[1]);
  const esquecidos = d.equipes.filter((e) => e.abertoDesdeOutroDia);
  const colunas: CesDataTableColumn<EquipeTurno>[] = [
    { key: 'codigo', header: 'Equipe', priority: 1 },
    { key: 'baseOperacional', header: 'Base', priority: 1, display: (e) => e.baseOperacional ?? '—' },
    { key: 'processo', header: 'Processo', priority: 2, display: (e) => e.processo ?? '—' },
    { key: 'placa', header: 'Veículo', priority: 3, display: (e) => e.placa ?? '—' },
    { key: 'metaDiaria', header: 'Meta do dia', type: 'currency', priority: 2 }
  ];

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Sem turno',
    titulo: `${plural(r.semTurno, 'equipe ativa sem turno', 'equipes ativas sem turno')}`,
    descricao: recorteTexto(c),
    kpis: [
      { rotulo: 'Sem turno', valor: r.semTurno.toLocaleString('pt-BR'), icone: 'pi pi-user-minus', tom: r.semTurno && d.diaUtil ? 'warning' : 'neutral', detalhe: `de ${r.equipesAtivas} equipes ativas` },
      { rotulo: 'Meta diária sem equipe', valor: moedaCompacta(metaParada), icone: 'pi pi-flag', detalhe: 'soma das metas de quem não abriu', tom: metaParada ? 'warning' : 'neutral' },
      { rotulo: 'Abertos desde outro dia', valor: r.turnosAbertosDeOutroDia.toLocaleString('pt-BR'), icone: 'pi pi-exclamation-triangle', tom: r.turnosAbertosDeOutroDia ? 'warning' : 'neutral' }
    ],
    destaques: [
      ...(!d.diaUtil ? [{ tom: 'neutral' as const, icone: 'pi pi-calendar-times', texto: 'Dia não útil: equipes sem turno são esperadas.' }] : []),
      ...esquecidos.map((e) => ({ tom: 'warning' as const, icone: 'pi pi-exclamation-triangle', texto: `${e.codigo} está com turno aberto desde ${horaMinuto(e.abertura)} de outro dia.` }))
    ],
    graficos: bases.length ? [{
      id: 'sem-turno-por-base',
      titulo: 'Sem turno por base operacional',
      tipo: 'bar-horizontal',
      rotulos: bases.map((b) => b[0]),
      series: [{ name: 'Equipes', values: bases.map((b) => b[1]) }],
      formato: 'integer',
      altura: alturaBarras(bases.length),
      inteiro: true
    }] : [],
    tabelas: [{
      id: 'sem-turno',
      titulo: 'Equipes sem turno',
      icone: 'pi pi-user-minus',
      descricao: 'Equipes ativas do recorte que não abriram turno no dia. Clique para ver o histórico recente da equipe.',
      colunas,
      linhas: semTurno,
      chaveLinha: (e: EquipeTurno) => e.idEquipe,
      rotuloLinha: (e: EquipeTurno) => `Equipe ${e.codigo}`,
      aoAtivar: (e: EquipeTurno) => navegar('equipe', e.idEquipe),
      rotuloAcao: 'Ver equipe',
      nomeArquivo: `sem-turno-${d.data}`,
      vazio: 'Todas as equipes abriram turno'
    }],
    copiloto: {
      foco: { tipo: 'metrica', id: 'semTurno', rotulo: 'Equipes sem turno' },
      pergunta: `${r.semTurno} equipes não abriram turno em ${diaPorExtenso(d.data)} (${moedaCompacta(metaParada)} de meta diária). Quais são e com que frequência isso aconteceu com elas neste mês?`
    }
  };
}

// ------------------------------------------------------------------ pontos de gráfico

function vistaEquipe(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise | null {
  const { d } = c;
  const ordem = ordenarEquipesTurno(d.equipes);
  const i = ordem.findIndex((e) => e.idEquipe === a.value);
  if (i < 0) return null;
  const e = ordem[i];
  const lanc = [...e.lancamentos].sort((x, y) => Date.parse(x.dataLancamento) - Date.parse(y.dataLancamento));
  let soma = 0;
  const acumulado = lanc.map((l) => Math.round((soma += l.valor ?? 0) * 100) / 100);
  // A primeira nota conta da abertura, mas nunca de antes do dia analisado (turno aberto em outro dia
  // ou vários turnos no dia não podem virar um "intervalo" de dias). Dia de operação: São Paulo, UTC−3.
  const inicioDia = Date.parse(`${d.data}T00:00:00-03:00`);
  const inicio = e.abertura ? Math.max(Date.parse(e.abertura), inicioDia) : inicioDia;
  const referencias = [inicio, ...lanc.map((l) => Date.parse(l.dataLancamento))];
  const intervalos = lanc.map((l, k) => Math.max(0, Math.round((Date.parse(l.dataLancamento) - referencias[k]) / 60_000)));
  const maiorIntervalo = intervalos.length ? Math.max(...intervalos) : null;
  const registro = (k: number) => ({ tipo: 'registro' as const, id: lanc[k].idExecucao });
  const situacao = SITUACAO_TURNO[e.situacao];

  const destaques: DestaqueAnalise[] = [{ tom: situacao.tom, icone: situacao.icone, texto: `${situacaoTexto(e, d)}.` }];
  if (e.semLancamentoRecente) destaques.push({ tom: 'warning', icone: 'pi pi-hourglass', texto: `Sem lançar há ${duracao(e.minutosSemLancamento)}.` });
  if (e.abertoDesdeOutroDia) destaques.push({ tom: 'warning', icone: 'pi pi-exclamation-triangle', texto: 'Turno aberto desde outro dia e ainda não fechado.' });
  if (e.quantidadeTurnos > 1) destaques.push({ tom: 'neutral', icone: 'pi pi-info-circle', texto: `${e.quantidadeTurnos} turnos no dia; a composição é a do mais recente.` });
  if (maiorIntervalo != null && maiorIntervalo >= d.limiteSemLancamentoMinutos) {
    const k = intervalos.indexOf(maiorIntervalo);
    destaques.push({ tom: 'info', icone: 'pi pi-history', texto: `Maior intervalo sem nota: ${duracao(maiorIntervalo)}, até a nota das ${horaMinuto(lanc[k].dataLancamento)}.` });
  }

  const membros = e.membros.map((m, k) => ({ id: String(k), nome: m, papel: e.abertoPor && m === e.abertoPor ? 'Encarregado' : 'Integrante' }));

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Equipe no dia',
    titulo: e.codigo,
    descricao: [e.baseOperacional, e.processo, e.placa].filter(Boolean).join(' · ') + ` · ${diaPorExtenso(d.data)}`,
    anterior: i > 0 ? { alvo: alvo('equipe', ordem[i - 1].idEquipe), rotulo: ordem[i - 1].codigo } : undefined,
    proximo: i < ordem.length - 1 ? { alvo: alvo('equipe', ordem[i + 1].idEquipe), rotulo: ordem[i + 1].codigo } : undefined,
    kpis: e.situacao === 'SEM_TURNO'
      ? [
          { rotulo: 'Situação', valor: situacaoTexto(e, d), icone: situacao.icone, tom: d.diaUtil ? 'warning' : 'neutral' },
          { rotulo: 'Meta do dia', valor: moedaCompacta(e.metaDiaria), icone: 'pi pi-flag' }
        ]
      : [
          { rotulo: 'Produção', valor: moeda(e.producao), tom: 'brand', icone: 'pi pi-wallet' },
          { rotulo: 'Meta do dia', valor: e.metaDiaria != null ? percentual(e.percentualMetaDiaria) : 'Sem meta', icone: 'pi pi-flag', tom: tomDaMeta(e.percentualMetaDiaria), detalhe: e.metaDiaria != null ? `de ${moedaCompacta(e.metaDiaria)}` : undefined },
          { rotulo: 'Notas', valor: e.notas.toLocaleString('pt-BR'), icone: 'pi pi-file-check', detalhe: e.notas ? `${moedaCompacta(e.valorMedioNota)} por nota` : undefined },
          { rotulo: 'Em turno', valor: duracao(e.minutosEmTurno), icone: 'pi pi-stopwatch', detalhe: e.abertura ? `desde ${diaDe(e.abertura) === d.data ? horaMinuto(e.abertura) : diaHora(e.abertura)}` : undefined },
          { rotulo: 'Maior intervalo', valor: duracao(maiorIntervalo), icone: 'pi pi-history', detalhe: 'sem lançar nota', tom: maiorIntervalo != null && maiorIntervalo >= d.limiteSemLancamentoMinutos ? 'warning' : 'neutral' },
          { rotulo: 'Composição', valor: plural(e.membros.length, 'pessoa', 'pessoas'), icone: 'pi pi-users', detalhe: e.abertoPor ? `encarregado ${nomeCurto(e.abertoPor)}` : undefined }
        ],
    destaques,
    graficos: lanc.length ? [
      {
        id: 'acumulado-turno',
        titulo: 'Produção acumulada nota a nota',
        descricao: 'Cada ponto é uma nota. Clique para ver o detalhe dela.',
        tipo: 'line',
        rotulos: lanc.map((l) => horaMinuto(l.dataLancamento)),
        series: [
          { name: 'Produção acumulada', values: acumulado },
          ...(e.metaDiaria != null ? [{ name: 'Meta do dia', values: lanc.map(() => e.metaDiaria) }] : [])
        ],
        formato: 'currency',
        altura: 250,
        inteiro: true,
        aoSelecionar: registro,
        rotuloSelecao: 'Ver nota'
      },
      {
        id: 'intervalos',
        titulo: 'Minutos até cada nota',
        descricao: 'Tempo desde a nota anterior (a primeira conta da abertura). Barras altas são pausas longas.',
        tipo: 'bar',
        rotulos: lanc.map((l) => horaMinuto(l.dataLancamento)),
        series: [{ name: 'Minutos', values: intervalos }],
        formato: 'integer',
        altura: 230,
        aoSelecionar: registro,
        rotuloSelecao: 'Ver nota'
      },
      ...(e.tiposServico.length ? [{
        id: 'tipos-equipe',
        titulo: 'Notas por tipo de serviço',
        descricao: 'Clique numa fatia para filtrar as notas.',
        tipo: 'doughnut' as const,
        rotulos: e.tiposServico.map((t) => t.descricao),
        series: [{ name: 'Notas', values: e.tiposServico.map((t) => t.notas) }],
        formato: 'integer' as const,
        altura: 230,
        aoSelecionar: (k: number) => ({
          tipo: 'filtrar' as const,
          filtro: { chave: `tipo-${chaveTipo(e.tiposServico[k])}`, rotulo: e.tiposServico[k].descricao, aceita: (n: NotaDoRecorte) => (n.idTipoServico ?? SEM_TIPO) === chaveTipo(e.tiposServico[k]) }
        }),
        rotuloSelecao: 'Ver notas do tipo'
      }] : [])
    ] : [],
    tabelas: membros.length ? [{
      id: 'composicao',
      titulo: 'Composição',
      icone: 'pi pi-users',
      descricao: 'Pessoas confirmadas pelo encarregado na abertura do turno.',
      colunas: [
        { key: 'nome', header: 'Nome', priority: 1 },
        { key: 'papel', header: 'Papel', priority: 1 }
      ] as CesDataTableColumn<{ id: string; nome: string; papel: string }>[],
      linhas: membros,
      chaveLinha: (m: { id: string }) => m.id,
      nomeArquivo: `composicao-${e.codigo}-${d.data}`
    }] : [],
    origem: e.notas > 0 ? origemNotas({
      service: c.service,
      filtros: { inicio: d.data, fim: d.data, idEquipe: e.idEquipe },
      esperado: { notas: e.notas, valor: e.producao },
      derivados: (notas) => graficosDasNotas(notas, { porHora: false }),
      verTudo: verTudo(c, { idEquipe: e.idEquipe }),
      mostrarEquipe: false,
      nomeArquivo: `notas-${e.codigo}-${d.data}`
    }) : undefined,
    links: [
      ...(e.idTurno ? [{ rotulo: 'Relatório do turno', icone: 'pi pi-file-pdf', rota: ['/execucao/turnos', e.idTurno, 'detalhamento'] }] : []),
      {
        rotulo: 'Produção do mês da equipe',
        icone: 'pi pi-chart-line',
        rota: '/execucao/dashboard-operacional',
        queryParams: { drill: `equipe:${e.idEquipe}`, base: c.filtros.idBaseOperacional ?? null, processo: c.filtros.idProcesso ?? null }
      }
    ],
    copiloto: {
      foco: { tipo: 'secao', id: `equipe.${e.codigo}`, rotulo: `Equipe ${e.codigo} em ${diaMes(d.data)}` },
      pergunta: e.situacao === 'SEM_TURNO'
        ? `A equipe ${e.codigo} não abriu turno em ${diaPorExtenso(d.data)}. Com que frequência isso aconteceu neste mês e há algum padrão?`
        : `Analise o turno da equipe ${e.codigo} em ${diaPorExtenso(d.data)}: ${e.notas} notas, ${moeda(e.producao)}, ${duracao(e.minutosEmTurno)} em turno e maior intervalo sem nota de ${duracao(maiorIntervalo)}. O ritmo foi normal comparado aos dias anteriores da equipe?`
    }
  };
}

function vistaHora(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise | null {
  const { d } = c;
  const hora = Number(a.value);
  if (!Number.isInteger(hora) || hora < 0 || hora > 23) return null;
  const todos = lancamentos(d);
  const naHora = todos.filter((l) => horaDe(l.dataLancamento) === hora);
  const horas = [...new Set(todos.map((l) => horaDe(l.dataLancamento)))].sort((x, y) => x - y);
  const k = horas.indexOf(hora);
  const valor = naHora.reduce((t, l) => t + (l.valor ?? 0), 0);
  const porEquipe = new Map<string, { codigo: string; idEquipe: string; notas: number; valor: number }>();
  naHora.forEach((l) => {
    const item = porEquipe.get(l.idEquipe) ?? { codigo: l.codigo, idEquipe: l.idEquipe, notas: 0, valor: 0 };
    item.notas += 1;
    item.valor += l.valor ?? 0;
    porEquipe.set(l.idEquipe, item);
  });
  const equipes = [...porEquipe.values()].sort((x, y) => y.valor - x.valor);
  const rotulo = `${rotuloHora(hora)} às ${rotuloHora(hora + 1)}`;

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Hora do dia',
    titulo: `Das ${rotulo}`,
    descricao: recorteTexto(c),
    anterior: k > 0 ? { alvo: alvo('hora', String(horas[k - 1])), rotulo: rotuloHora(horas[k - 1]) } : undefined,
    proximo: k >= 0 && k < horas.length - 1 ? { alvo: alvo('hora', String(horas[k + 1])), rotulo: rotuloHora(horas[k + 1]) } : undefined,
    kpis: [
      { rotulo: 'Notas na hora', valor: naHora.length.toLocaleString('pt-BR'), tom: 'brand', icone: 'pi pi-file-check', detalhe: `${percentual(d.resumo.notas ? (naHora.length / d.resumo.notas) * 100 : 0)} do dia` },
      { rotulo: 'Produção na hora', valor: moeda(valor), icone: 'pi pi-wallet' },
      { rotulo: 'Equipes que lançaram', valor: equipes.length.toLocaleString('pt-BR'), icone: 'pi pi-users' }
    ],
    graficos: equipes.length ? [{
      id: 'equipes-na-hora',
      titulo: 'Notas por equipe na hora',
      descricao: 'Clique numa equipe para filtrar as notas dela.',
      tipo: 'bar-horizontal',
      rotulos: equipes.map((x) => x.codigo),
      series: [{ name: 'Produção', values: equipes.map((x) => Math.round(x.valor * 100) / 100) }],
      formato: 'currency',
      altura: alturaBarras(equipes.length),
      inteiro: true,
      aoSelecionar: (i) => ({
        tipo: 'filtrar',
        filtro: { chave: `equipe-${equipes[i].idEquipe}`, rotulo: `Equipe ${equipes[i].codigo}`, aceita: (n: NotaDoRecorte) => n.idEquipe === equipes[i].idEquipe }
      }),
      rotuloSelecao: 'Ver notas da equipe'
    }] : [],
    origem: origemNotas({
      service: c.service,
      filtros: filtrosDoDia(c),
      esperado: { notas: naHora.length, valor: Math.round(valor * 100) / 100 },
      filtroInicial: { chave: `hora-${hora}`, rotulo: `Lançadas das ${rotulo}`, aceita: (n) => horaDe(n.dataLancamento) === hora },
      nomeArquivo: `notas-${d.data}-${rotuloHora(hora)}`
    }),
    copiloto: {
      foco: { tipo: 'grafico', id: 'producaoPorHora', rotulo: `Lançamentos das ${rotulo}` },
      pergunta: `Das ${rotulo} de ${diaPorExtenso(d.data)} entraram ${naHora.length} notas (${moeda(valor)}). Esse volume é normal para esse horário? Quais equipes lançaram?`
    }
  };
}

function vistaTipo(a: AlvoAnalise, c: ContextoAnaliseTurnos): VistaAnalise | null {
  const { d } = c;
  const i = d.tiposServico.findIndex((t) => chaveTipo(t) === a.value);
  if (i < 0) return null;
  const t = d.tiposServico[i];
  const porEquipe = d.equipes
    .map((e) => ({ e, t: e.tiposServico.find((x) => chaveTipo(x) === a.value) }))
    .filter((x) => x.t && x.t.notas > 0)
    .sort((x, y) => y.t!.producao - x.t!.producao);
  const anterior = d.tiposServico[i - 1];
  const proximo = d.tiposServico[i + 1];

  return {
    chave: chaveDoAlvo(a),
    eyebrow: 'Tipo de serviço',
    titulo: t.codigo ? `${t.codigo} · ${t.descricao}` : t.descricao,
    descricao: recorteTexto(c),
    anterior: anterior ? { alvo: alvo('tipo', chaveTipo(anterior)), rotulo: anterior.descricao } : undefined,
    proximo: proximo ? { alvo: alvo('tipo', chaveTipo(proximo)), rotulo: proximo.descricao } : undefined,
    kpis: [
      { rotulo: 'Notas', valor: t.notas.toLocaleString('pt-BR'), tom: 'brand', icone: 'pi pi-file-check', detalhe: `${percentual(t.percentualNotas)} das notas do dia` },
      { rotulo: 'Produção', valor: moeda(t.producao), icone: 'pi pi-wallet' },
      { rotulo: 'Valor médio por nota', valor: moeda(t.valorMedioNota), icone: 'pi pi-tag' },
      { rotulo: 'Equipes', valor: porEquipe.length.toLocaleString('pt-BR'), icone: 'pi pi-users', detalhe: 'lançaram este tipo' }
    ],
    graficos: porEquipe.length ? [{
      id: 'tipo-por-equipe',
      titulo: 'Por equipe',
      descricao: 'Clique numa equipe para filtrar as notas dela.',
      tipo: 'bar-horizontal',
      rotulos: porEquipe.map((x) => x.e.codigo),
      series: [{ name: 'Produção', values: porEquipe.map((x) => x.t!.producao) }],
      formato: 'currency',
      altura: alturaBarras(porEquipe.length),
      aoSelecionar: (k) => ({
        tipo: 'filtrar',
        filtro: { chave: `equipe-${porEquipe[k].e.idEquipe}`, rotulo: `Equipe ${porEquipe[k].e.codigo}`, aceita: (n: NotaDoRecorte) => n.idEquipe === porEquipe[k].e.idEquipe }
      }),
      rotuloSelecao: 'Ver notas da equipe'
    }] : [],
    origem: origemNotas({
      service: c.service,
      filtros: { ...filtrosDoDia(c), ...(t.idTipoServico ? { idTipoServico: t.idTipoServico } : { semTipoServico: true }) },
      esperado: { notas: t.notas, valor: t.producao },
      derivados: (notas) => graficosDasNotas(notas, { faixas: false }),
      verTudo: verTudo(c, { tipo: t.idTipoServico ?? SEM_TIPO }),
      nomeArquivo: `notas-${d.data}-${t.codigo ?? 'nao-informado'}`
    }),
    copiloto: {
      foco: { tipo: 'tabela', id: 'tiposServico', rotulo: `Tipo de serviço ${t.descricao}` },
      pergunta: `Em ${diaPorExtenso(d.data)}, o tipo "${t.descricao}" teve ${t.notas} notas e ${moeda(t.producao)}. Quais equipes executaram e como isso se compara aos outros dias?`
    }
  };
}
