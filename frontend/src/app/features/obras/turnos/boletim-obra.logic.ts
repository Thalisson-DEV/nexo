import { AgendaDoBoletim, BoletimEquipes, EquipeBoletim } from '../../../core/models/obras-turnos.models';
import { AlvoAnalise, VistaAnalise, chaveDoAlvo } from '../../../shared/components/dashboard/dashboard-analise.models';
import { LeituraGerencial, VereditoGerencial } from '../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import { SITUACAO_TURNO, duracao, moedaCompacta, nomeCurto, percentual, tomDaMeta } from '../../execucao/dashboards/execucao-dashboards.utils';
import { LIBERACAO } from './turnos-obra.logic';

export type FiltroBoletim = 'todas' | 'em-turno' | 'apr-pendente' | 'agendadas' | 'encerradas' | 'sem-turno';
export type AgrupamentoBoletim = 'nenhum' | 'base' | 'tipo' | 'meta';

export interface ResumoBoletimVisivel {
  equipes: number;
  emTurno: number;
  encerrados: number;
  semTurno: number;
  agendadasSemTurno: number;
  aprPendente: number;
  abertosDeOutroDia: number;
  programado: number;
  executado: number;
  meta: number | null;
  executadoComMeta: number;
  percentualMeta: number | null;
}

export interface GrupoBoletim {
  chave: string;
  rotulo: string;
  icone: string;
  equipes: EquipeBoletim[];
  emTurno: number;
  executado: number;
  percentualMeta: number | null;
}

/** Programação com itens por fazer. A concluída continua no boletim, mas não cobra mais nada da equipe. */
export function agendaEmAberto(a: AgendaDoBoletim): boolean {
  return a.itensConcluidos < a.itens;
}

/** Equipe que está trabalhando numa obra sem APR concluída nem motivo registrado. */
export function temAprPendente(e: EquipeBoletim): boolean {
  return e.situacao !== 'SEM_TURNO' && e.agenda.some((a) => agendaEmAberto(a) && (a.liberacao === 'PENDENTE' || a.liberacao === 'EM_PREENCHIMENTO'));
}

export function resumirBoletim(equipes: readonly EquipeBoletim[]): ResumoBoletimVisivel {
  // A API omite campos nulos: meta ausente chega como `undefined`.
  const comMeta = equipes.filter((e) => e.metaDiaria != null);
  const meta = comMeta.reduce((t, e) => t + (e.metaDiaria ?? 0), 0);
  const executadoComMeta = comMeta.reduce((t, e) => t + e.noDia.executado, 0);
  return {
    equipes: equipes.length,
    emTurno: equipes.filter((e) => e.situacao === 'EM_TURNO').length,
    encerrados: equipes.filter((e) => e.situacao === 'ENCERRADO').length,
    semTurno: equipes.filter((e) => e.situacao === 'SEM_TURNO').length,
    agendadasSemTurno: equipes.filter((e) => e.agendamentoSemTurno).length,
    aprPendente: equipes.filter(temAprPendente).length,
    abertosDeOutroDia: equipes.filter((e) => e.abertoDesdeOutroDia).length,
    programado: equipes.reduce((t, e) => t + e.noDia.programado, 0),
    executado: equipes.reduce((t, e) => t + e.noDia.executado, 0),
    meta: comMeta.length ? meta : null,
    executadoComMeta,
    percentualMeta: comMeta.length && meta > 0 ? (executadoComMeta / meta) * 100 : null
  };
}

export function atendeFiltro(e: EquipeBoletim, filtro: FiltroBoletim): boolean {
  switch (filtro) {
    case 'em-turno': return e.situacao === 'EM_TURNO';
    case 'apr-pendente': return temAprPendente(e);
    case 'agendadas': return e.agendamentoSemTurno;
    case 'encerradas': return e.situacao === 'ENCERRADO';
    case 'sem-turno': return e.situacao === 'SEM_TURNO';
    default: return true;
  }
}

const PESO_SITUACAO: Record<EquipeBoletim['situacao'], number> = { EM_TURNO: 0, ENCERRADO: 1, SEM_TURNO: 2 };

/** O que pede ação vem primeiro: agenda sem turno, APR pendente; depois em turno, encerradas e sem turno. */
export function ordenarEquipes(equipes: readonly EquipeBoletim[]): EquipeBoletim[] {
  return [...equipes].sort(
    (a, b) =>
      Number(b.agendamentoSemTurno) - Number(a.agendamentoSemTurno) ||
      Number(temAprPendente(b)) - Number(temAprPendente(a)) ||
      PESO_SITUACAO[a.situacao] - PESO_SITUACAO[b.situacao] ||
      a.codigo.localeCompare(b.codigo, 'pt-BR')
  );
}

function grupo(chave: string, rotulo: string, icone: string, equipes: EquipeBoletim[]): GrupoBoletim {
  const r = resumirBoletim(equipes);
  return { chave, rotulo, icone, equipes, emTurno: r.emTurno, executado: r.executado, percentualMeta: r.percentualMeta };
}

const FAIXAS_DA_META: readonly { chave: string; rotulo: string; icone: string }[] = [
  { chave: 'danger', rotulo: 'Abaixo da meta do dia', icone: 'pi pi-times-circle' },
  { chave: 'warning', rotulo: 'Perto da meta do dia', icone: 'pi pi-exclamation-circle' },
  { chave: 'success', rotulo: 'Meta do dia batida', icone: 'pi pi-check-circle' },
  { chave: 'neutral', rotulo: 'Sem meta no dia', icone: 'pi pi-minus-circle' }
];

export function agruparEquipes(equipes: readonly EquipeBoletim[], criterio: AgrupamentoBoletim): GrupoBoletim[] {
  if (!equipes.length) return [];
  if (criterio === 'nenhum') return [grupo('todas', 'Todas as equipes', 'pi pi-users', [...equipes])];
  if (criterio === 'meta') {
    return FAIXAS_DA_META.map((f) => grupo(f.chave, f.rotulo, f.icone, equipes.filter((e) => tomDaMeta(e.percentualMeta) === f.chave))).filter((g) => g.equipes.length);
  }
  const nome = (e: EquipeBoletim) => (criterio === 'base' ? e.baseOperacional : e.tipoEquipe) ?? null;
  const nomes = [...new Set(equipes.map(nome))].sort((a, b) => (a === null ? 1 : b === null ? -1 : a.localeCompare(b, 'pt-BR')));
  const icone = criterio === 'base' ? 'pi pi-building' : 'pi pi-tags';
  const vazio = criterio === 'base' ? 'Sem base operacional' : 'Sem tipo de equipe';
  return nomes.map((n) => grupo(n ?? '', n ?? vazio, icone, equipes.filter((e) => nome(e) === n)));
}

/** Equipes que tinham algo a fazer ou fizeram algo no dia, da maior execução para a menor. */
export function serieDoBoletim(equipes: readonly EquipeBoletim[]): { rotulos: string[]; series: { name: string; values: (number | null)[] }[] } {
  const ativas = equipes
    .filter((e) => e.situacao !== 'SEM_TURNO' || e.noDia.programado > 0 || e.noDia.executado > 0)
    .sort((a, b) => b.noDia.executado - a.noDia.executado || a.codigo.localeCompare(b.codigo, 'pt-BR'));
  const temMeta = ativas.some((e) => e.metaDiaria != null);
  return {
    rotulos: ativas.map((e) => e.codigo),
    series: [
      { name: 'Executado', values: ativas.map((e) => e.noDia.executado) },
      { name: 'Programado', values: ativas.map((e) => e.noDia.programado) },
      ...(temMeta ? [{ name: 'Meta diária', values: ativas.map((e) => e.metaDiaria ?? null) }] : [])
    ]
  };
}

const quantas = (n: number, uma: string, varias: string) => `${n} ${n === 1 ? uma : varias}`;
const kpi = (value: string): AlvoAnalise => ({ dimension: 'kpi', value });

export interface VisaoBoletim {
  veredito: VereditoGerencial;
  leituras: LeituraGerencial[];
  pergunta: string;
}

/** Veredito do dia e leituras acionáveis, só com o que o próprio boletim calcula. */
export function visaoDoBoletim(b: Pick<BoletimEquipes, 'hoje'>, r: ResumoBoletimVisivel): VisaoBoletim {
  const comTurno = r.emTurno + r.encerrados;
  const progresso = r.percentualMeta != null
    ? { valor: r.percentualMeta, rotulo: `${percentual(r.percentualMeta)} da meta diária (${moedaCompacta(r.executadoComMeta)} de ${moedaCompacta(r.meta)})` }
    : undefined;
  const problemas: string[] = [];
  if (r.agendadasSemTurno) problemas.push(`${quantas(r.agendadasSemTurno, 'equipe tem', 'equipes têm')} programação e não abriu turno`);
  if (r.aprPendente) problemas.push(`${quantas(r.aprPendente, 'equipe está', 'equipes estão')} com APR pendente`);
  if (r.abertosDeOutroDia) problemas.push(`${quantas(r.abertosDeOutroDia, 'turno segue aberto', 'turnos seguem abertos')} desde outro dia`);

  let veredito: VereditoGerencial;
  if (problemas.length) {
    veredito = {
      tom: r.agendadasSemTurno ? 'danger' : 'warning',
      rotulo: r.agendadasSemTurno ? 'Agir agora' : 'Atenção',
      titulo: r.agendadasSemTurno ? 'Programação sem equipe em campo' : r.aprPendente ? 'Serviço sem APR concluída' : 'Turno esquecido',
      texto: `${problemas.join('; ')}. ${moedaCompacta(r.executado)} executados de ${moedaCompacta(r.programado)} programados.`,
      progresso
    };
  } else if (!comTurno) {
    veredito = { tom: 'neutral', rotulo: 'Sem turnos', titulo: 'Nenhuma equipe com turno', texto: `${quantas(r.equipes, 'equipe', 'equipes')} no recorte, sem turno ${b.hoje ? 'aberto até agora' : 'neste dia'} e sem programação pendente.` };
  } else {
    veredito = {
      tom: 'success',
      rotulo: 'Dentro do plano',
      titulo: 'Programação coberta',
      texto: `${comTurno} de ${r.equipes} equipes com turno e APR em dia. ${moedaCompacta(r.executado)} executados de ${moedaCompacta(r.programado)} programados.`,
      progresso
    };
  }

  const leituras: LeituraGerencial[] = [
    {
      id: 'cobertura',
      tom: r.agendadasSemTurno ? 'danger' : 'success',
      icone: 'pi pi-users',
      titulo: 'Agendamento sem turno',
      valor: String(r.agendadasSemTurno),
      texto: r.agendadasSemTurno ? 'Têm programação no dia e a equipe não abriu turno.' : 'Toda equipe com programação no dia abriu turno.',
      acao: { rotulo: 'Ver as equipes', alvo: kpi('agendadas') }
    },
    {
      id: 'apr',
      tom: r.aprPendente ? 'warning' : 'success',
      icone: 'pi pi-shield',
      titulo: 'APR pendente',
      valor: String(r.aprPendente),
      texto: r.aprPendente ? 'Em turno, sem APR concluída nem motivo para não executar. O apontamento fica bloqueado.' : 'As obras do dia têm APR concluída ou motivo registrado.',
      acao: { rotulo: 'Ver as equipes', alvo: kpi('apr-pendente') }
    },
    {
      id: 'execucao',
      tom: r.programado && r.executado >= r.programado ? 'success' : 'info',
      icone: 'pi pi-wallet',
      titulo: 'Executado contra o programado',
      valor: r.programado ? percentual((r.executado / r.programado) * 100) : '—',
      texto: `${moedaCompacta(r.executado)} executados de ${moedaCompacta(r.programado)} programados no dia.`,
      acao: { rotulo: 'Analisar execução', alvo: kpi('execucao') }
    }
  ];
  return {
    veredito,
    leituras,
    pergunta: 'Faça uma leitura completa do boletim das equipes de Obras neste dia: cobertura de turnos, APR pendente e executado contra o programado.'
  };
}

export interface LinhaDaAgenda extends AgendaDoBoletim {
  codigoEquipe: string;
  situacaoEquipe: EquipeBoletim['situacao'];
}

export function agendaDoBoletim(equipes: readonly EquipeBoletim[]): LinhaDaAgenda[] {
  return equipes
    .flatMap((e) => e.agenda.map((a) => ({ ...a, codigoEquipe: e.codigo, situacaoEquipe: e.situacao })))
    .sort((a, b) => a.numero - b.numero);
}

const COLUNAS_EQUIPES = [
  { key: 'codigo', header: 'Equipe', priority: 1 as const },
  { key: 'situacao', header: 'Situação', value: (e: EquipeBoletim) => SITUACAO_TURNO[e.situacao].rotulo },
  { key: 'lider', header: 'Líder', value: (e: EquipeBoletim) => (e.lider ? nomeCurto(e.lider.nome) : '—') },
  { key: 'programado', header: 'Programado', type: 'currency' as const, value: (e: EquipeBoletim) => e.noDia.programado },
  { key: 'executado', header: 'Executado', type: 'currency' as const, value: (e: EquipeBoletim) => e.noDia.executado }
];

function vistaDeEquipes(alvo: AlvoAnalise, titulo: string, descricao: string, icone: string, equipes: EquipeBoletim[], dia: string): VistaAnalise {
  const r = resumirBoletim(equipes);
  return {
    chave: chaveDoAlvo(alvo),
    eyebrow: `Boletim · ${dia}`,
    titulo,
    descricao,
    icone,
    kpis: [
      { rotulo: 'Equipes', valor: String(r.equipes), icone: 'pi pi-users' },
      { rotulo: 'Programado', valor: moedaCompacta(r.programado), icone: 'pi pi-calendar' },
      { rotulo: 'Executado', valor: moedaCompacta(r.executado), icone: 'pi pi-wallet' }
    ],
    graficos: [],
    tabelas: [{
      id: 'equipes',
      titulo: 'Equipes',
      colunas: COLUNAS_EQUIPES,
      linhas: equipes,
      chaveLinha: (e: EquipeBoletim) => e.idEquipe,
      rotuloLinha: (e: EquipeBoletim) => e.codigo,
      aoAtivar: (e: EquipeBoletim) => ({ tipo: 'navegar', alvo: { dimension: 'equipe', value: e.idEquipe } }),
      rotuloAcao: 'Analisar equipe',
      vazio: 'Nenhuma equipe neste recorte.'
    }]
  };
}

/** Análise aberta ao clicar num indicador ou numa equipe. Os dados já estão em tela, então abre sem nova consulta. */
export function vistaDoBoletim(alvo: AlvoAnalise, equipes: readonly EquipeBoletim[], dia: string): VistaAnalise | null {
  if (alvo.dimension === 'equipe') {
    const e = equipes.find((x) => x.idEquipe === alvo.value);
    if (!e) return null;
    return {
      chave: chaveDoAlvo(alvo),
      eyebrow: `Boletim · ${dia}`,
      titulo: `Equipe ${e.codigo}`,
      descricao: [e.tipoEquipe, e.baseOperacional, SITUACAO_TURNO[e.situacao].rotulo].filter(Boolean).join(' · '),
      icone: 'pi pi-users',
      kpis: [
        { rotulo: 'Executado no dia', valor: moedaCompacta(e.noDia.executado), detalhe: `Programado: ${moedaCompacta(e.noDia.programado)}`, icone: 'pi pi-wallet' },
        { rotulo: 'Até o dia', valor: moedaCompacta(e.noCiclo.executado), detalhe: `Programado: ${moedaCompacta(e.noCiclo.programado)}`, icone: 'pi pi-chart-line' },
        { rotulo: 'Dia útil anterior', valor: moedaCompacta(e.diaAnterior.executado), detalhe: `Programado: ${moedaCompacta(e.diaAnterior.programado)}`, icone: 'pi pi-history' },
        { rotulo: 'Em turno', valor: e.situacao === 'SEM_TURNO' ? '—' : duracao(e.minutosEmTurno), icone: 'pi pi-stopwatch' }
      ],
      graficos: [],
      tabelas: [
        {
          id: 'agenda',
          titulo: 'Programação do dia',
          colunas: [
            { key: 'numero', header: 'Programação', priority: 1 as const, value: (a: AgendaDoBoletim) => `#${a.numero} · ${a.numeroObra}` },
            { key: 'descricao', header: 'Serviço' },
            { key: 'liberacao', header: 'APR', value: (a: AgendaDoBoletim) => LIBERACAO[a.liberacao].rotulo },
            { key: 'itens', header: 'Itens', value: (a: AgendaDoBoletim) => `${a.itensConcluidos} de ${a.itens}` },
            { key: 'valor', header: 'Valor', type: 'currency' as const }
          ],
          linhas: e.agenda,
          chaveLinha: (a: AgendaDoBoletim) => a.idProgramacao,
          vazio: 'Sem programação neste dia.'
        },
        {
          id: 'componentes',
          titulo: 'Componentes',
          colunas: [
            { key: 'nome', header: 'Nome', priority: 1 as const },
            { key: 'funcao', header: 'Função' },
            { key: 'papel', header: 'Papel', value: (m: EquipeBoletim['componentes'][number]) => [m.lider ? 'Líder' : null, m.substituto ? 'Substituto' : 'Titular'].filter(Boolean).join(', ') }
          ],
          linhas: e.componentes,
          chaveLinha: (m: EquipeBoletim['componentes'][number]) => m.idColaborador,
          vazio: 'Sem componentes na equipe.'
        }
      ],
      links: e.idTurno ? [{ rotulo: 'Ver turno', icone: 'pi pi-calendar-clock', rota: ['/obras/turnos', e.idTurno] }] : []
    };
  }
  switch (alvo.value) {
    case 'turnos':
      return vistaDeEquipes(alvo, 'Equipes em turno', 'Quem abriu turno no dia, em andamento ou encerrado.', 'pi pi-users', equipes.filter((e) => e.situacao !== 'SEM_TURNO'), dia);
    case 'agendadas':
      return vistaDeEquipes(alvo, 'Agendamento sem turno', 'Têm programação no dia e não abriram turno.', 'pi pi-exclamation-triangle', equipes.filter((e) => e.agendamentoSemTurno), dia);
    case 'apr-pendente':
      return vistaDeEquipes(alvo, 'APR pendente', 'Em turno, sem APR concluída nem motivo para não executar.', 'pi pi-shield', equipes.filter(temAprPendente), dia);
    case 'sem-turno':
      return vistaDeEquipes(alvo, 'Sem turno', 'Não abriram turno no dia.', 'pi pi-user-minus', equipes.filter((e) => e.situacao === 'SEM_TURNO'), dia);
    case 'execucao': {
      const serie = serieDoBoletim(equipes);
      return {
        ...vistaDeEquipes(alvo, 'Executado contra o programado', 'Quanto cada equipe executou do que estava programado no dia.', 'pi pi-wallet', [...equipes].sort((a, b) => b.noDia.executado - a.noDia.executado), dia),
        graficos: serie.rotulos.length
          ? [{ id: 'execucao', titulo: 'Executado e programado por equipe', tipo: 'bar', rotulos: serie.rotulos, series: serie.series, formato: 'currency', inteiro: true }]
          : []
      };
    }
    default:
      return null;
  }
}
