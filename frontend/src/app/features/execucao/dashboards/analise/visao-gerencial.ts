import { DashboardOperacionalResponse, DashboardTurnosResponse } from '../../../../core/models/dashboard.models';
import { LeituraGerencial, VereditoGerencial } from '../../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import { dataLocal, diaMes, duracao, moedaCompacta, percentual, variacao } from '../execucao-dashboards.utils';
import { fatia, horaDe, plural, rotuloHora } from './analise-comum';

/**
 * Visão gerencial dos dashboards de execução: um veredito do recorte e leituras acionáveis, cada uma
 * abrindo a análise detalhada que a sustenta. Regras sem limiar inventado: só usam meta, ritmo e
 * contagens que o próprio dashboard já calcula.
 */

export interface VisaoGerencial {
  readonly veredito: VereditoGerencial;
  readonly leituras: LeituraGerencial[];
  /** Pergunta para "Leitura completa com o Copiloto". */
  readonly pergunta: string;
}

const kpi = (value: string) => ({ dimension: 'kpi', value });

export function visaoOperacional(d: DashboardOperacionalResponse): VisaoGerencial {
  const r = d.resumo;
  const emAndamento = d.diasUteisEncerrados < d.diasUteisPeriodo;
  const diasRestantes = Math.max(0, d.diasUteisPeriodo - d.diasUteisEncerrados);
  const comMeta = d.equipes.filter((e) => e.metaPeriodo != null);
  const esperado = comMeta.reduce((t, e) => t + (e.metaEsperadaAteAgora ?? 0), 0);
  const falta = r.metaPeriodo != null ? Math.max(0, r.metaPeriodo - r.producaoEquipesComMeta) : 0;
  const pctProjecao = r.metaPeriodo && r.projecaoEquipesComMeta != null ? (r.projecaoEquipesComMeta / r.metaPeriodo) * 100 : null;

  let veredito: VereditoGerencial;
  if (r.metaPeriodo == null || !r.metaPeriodo) {
    veredito = {
      tom: 'neutral',
      rotulo: 'Sem meta',
      titulo: `${moedaCompacta(r.producao)} produzidos no período`,
      texto: `Nenhuma equipe do recorte tem meta cadastrada, então não há ritmo a comparar. ${r.variacaoPercentual != null ? `A produção variou ${variacao(r.variacaoPercentual)} em relação ao período anterior.` : ''}`.trim()
    };
  } else {
    const referencia = emAndamento && pctProjecao != null ? pctProjecao : r.percentualMeta ?? 0;
    const tom = referencia >= 100 ? 'success' : referencia >= 90 ? 'warning' : 'danger';
    const titulo = emAndamento
      ? referencia >= 100 ? 'No ritmo para bater a meta' : referencia >= 90 ? 'Meta em risco' : 'Ritmo insuficiente para a meta'
      : referencia >= 100 ? 'Meta do período batida' : referencia >= 90 ? 'Meta quase batida' : 'Meta do período não atingida';
    const partes = [`As equipes com meta produziram ${moedaCompacta(r.producaoEquipesComMeta)} de ${moedaCompacta(r.metaPeriodo)} (${percentual(r.percentualMeta)}).`];
    if (emAndamento && pctProjecao != null) {
      partes.push(`No ritmo dos ${d.diasUteisEncerrados} dias úteis encerrados, fecham em ${moedaCompacta(r.projecaoEquipesComMeta)} — ${percentual(pctProjecao)} da meta.`);
    }
    if (emAndamento && falta > 0 && diasRestantes > 0) {
      partes.push(`Faltam ${moedaCompacta(falta)} em ${plural(diasRestantes, 'dia útil', 'dias úteis')} (${moedaCompacta(falta / diasRestantes)} por dia).`);
    }
    veredito = {
      tom,
      rotulo: tom === 'success' ? 'Dentro do plano' : tom === 'warning' ? 'Atenção' : 'Agir agora',
      titulo,
      texto: partes.join(' '),
      progresso: {
        valor: r.percentualMeta ?? 0,
        rotulo: `${percentual(r.percentualMeta)} da meta do período`,
        ...(emAndamento && r.metaPeriodo ? { marcador: (esperado / r.metaPeriodo) * 100, rotuloMarcador: `esperado até ontem: ${percentual((esperado / r.metaPeriodo) * 100)}` } : {})
      }
    };
  }

  const leituras: LeituraGerencial[] = [];
  if (r.equipesComMeta) {
    leituras.push({
      id: 'ritmo',
      tom: r.equipesAbaixoDoRitmo ? 'warning' : 'success',
      icone: 'pi pi-flag',
      titulo: 'Equipes abaixo do ritmo',
      valor: `${r.equipesAbaixoDoRitmo} de ${r.equipesComMeta}`,
      texto: r.equipesAbaixoDoRitmo
        ? 'Produziram menos do que a meta esperava até o último dia útil encerrado.'
        : 'Todas as equipes com meta estão no ritmo ou já bateram a meta.',
      acao: { rotulo: 'Ver quem e quanto falta', alvo: kpi('ritmo') }
    });
  }
  const paradas = r.equipesAtivas - r.equipesComProducao;
  const turnoSemExecucao = d.equipes.filter((e) => e.diasComTurno > e.diasComProducao).length;
  leituras.push({
    id: 'paradas',
    tom: paradas ? 'danger' : turnoSemExecucao ? 'warning' : 'success',
    icone: 'pi pi-ban',
    titulo: paradas ? 'Equipes sem execução' : 'Turno aberto sem execução',
    valor: paradas ? `${paradas} de ${r.equipesAtivas}` : plural(turnoSemExecucao, 'equipe', 'equipes'),
    texto: paradas
      ? 'Equipes de obras ativas no recorte que não apontaram execução no período.'
      : turnoSemExecucao ? 'Tiveram dias com turno aberto e nenhum apontamento nas obras.' : 'Todo dia com turno teve execução apontada.',
    acao: { rotulo: 'Ver equipes de atenção', alvo: kpi('ritmo') }
  });
  if (r.variacaoPercentual != null) {
    leituras.push({
      id: 'variacao',
      tom: r.variacaoPercentual >= 0 ? 'success' : 'warning',
      icone: r.variacaoPercentual >= 0 ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right',
      titulo: 'Contra o período anterior',
      valor: variacao(r.variacaoPercentual),
      texto: `${moedaCompacta(r.producao)} agora contra ${moedaCompacta(r.producaoPeriodoAnterior)} no período anterior de mesma duração.`,
      acao: { rotulo: 'Analisar produção', alvo: kpi('producao') }
    });
  }
  const top = [...d.equipes].filter((e) => e.producao > 0).sort((a, b) => b.producao - a.producao).slice(0, 3);
  if (top.length > 1 && r.producao > 0) {
    const share = fatia(top.reduce((t, e) => t + e.producao, 0), r.producao);
    leituras.push({
      id: 'concentracao',
      tom: share >= 70 && d.equipes.length > 4 ? 'warning' : 'info',
      icone: 'pi pi-chart-pie',
      titulo: 'Concentração da produção',
      valor: percentual(share),
      texto: `${top.map((e) => e.codigo).join(', ')} somam essa fatia da produção do período.`,
      acao: { rotulo: 'Ver ranking de equipes', alvo: kpi('producao') }
    });
  }
  const diasUteis = d.producaoDiaria.filter((p) => (p.meta ?? 0) > 0 && dataLocal(p.data) < new Date(new Date().setHours(0, 0, 0, 0)));
  const pior = [...diasUteis].sort((a, b) => a.valor / (a.meta || 1) - b.valor / (b.meta || 1))[0];
  if (pior) {
    leituras.push({
      id: 'pior-dia',
      tom: pior.valor < (pior.meta ?? 0) ? 'warning' : 'info',
      icone: 'pi pi-calendar-minus',
      titulo: 'Dia útil mais fraco',
      valor: diaMes(pior.data),
      texto: `${moedaCompacta(pior.valor)} em ${plural(pior.obras, 'obra', 'obras')}, contra ${moedaCompacta(pior.meta)} de meta do dia.`,
      acao: { rotulo: 'Entender o dia', alvo: { dimension: 'dia', value: pior.data } }
    });
  }
  const destaque = [...comMeta].sort((a, b) => (b.percentualMeta ?? 0) - (a.percentualMeta ?? 0))[0]
    ?? [...d.equipes].sort((a, b) => b.producao - a.producao)[0];
  if (destaque && destaque.producao > 0) {
    leituras.push({
      id: 'destaque',
      tom: 'success',
      icone: 'pi pi-star',
      titulo: 'Equipe destaque',
      valor: destaque.codigo,
      texto: destaque.percentualMeta != null
        ? `${percentual(destaque.percentualMeta)} da meta, ${moedaCompacta(destaque.producao)} em ${plural(destaque.obrasAtendidas, 'obra', 'obras')}.`
        : `${moedaCompacta(destaque.producao)} em ${plural(destaque.obrasAtendidas, 'obra', 'obras')}.`,
      acao: { rotulo: 'Ver o que a equipe fez', alvo: { dimension: 'equipe', value: destaque.idEquipe } }
    });
  }
  const maiorObra = d.obras[0];
  if (maiorObra && d.obras.length > 1) {
    leituras.push({
      id: 'maior-obra',
      tom: 'info',
      icone: 'pi pi-building',
      titulo: 'Obra com mais produção',
      valor: maiorObra.numero,
      texto: `${moedaCompacta(maiorObra.producao)} (${percentual(maiorObra.percentualProducao)} da produção) com ${plural(maiorObra.equipes, 'equipe', 'equipes')}, entre ${plural(d.obras.length, 'obra atendida', 'obras atendidas')}.`,
      acao: { rotulo: 'Analisar a obra', alvo: { dimension: 'obra', value: maiorObra.idObra } }
    });
  }

  return {
    veredito,
    leituras,
    pergunta: `Faça a leitura gerencial do período de ${diaMes(d.inicio)} a ${diaMes(d.fim)}: ${veredito.titulo.toLowerCase()} (${veredito.texto}) Quais as 3 ações mais importantes para o restante do período e quais equipes priorizar?`
  };
}

export function visaoTurnos(d: DashboardTurnosResponse): VisaoGerencial {
  const r = d.resumo;
  const comTurno = r.emTurno + r.encerrados;
  const emTurno = d.equipes.filter((e) => e.situacao === 'EM_TURNO');
  const piorParada = [...emTurno].sort((a, b) => (b.minutosSemLancamento ?? 0) - (a.minutosSemLancamento ?? 0))[0];
  const limite = duracao(d.limiteSemLancamentoMinutos);
  const faltaMeta = r.metaDiaria != null ? Math.max(0, r.metaDiaria - r.producaoEquipesComMeta) : 0;

  let veredito: VereditoGerencial;
  const progresso = r.percentualMetaDiaria != null
    ? { valor: r.percentualMetaDiaria, rotulo: `${percentual(r.percentualMetaDiaria)} da meta diária (${moedaCompacta(r.producaoEquipesComMeta)} de ${moedaCompacta(r.metaDiaria)})` }
    : undefined;
  if (d.hoje) {
    if (!comTurno && d.diaUtil) {
      veredito = { tom: 'danger', rotulo: 'Agir agora', titulo: 'Nenhuma equipe abriu turno', texto: `${plural(r.equipesAtivas, 'equipe ativa', 'equipes ativas')} no recorte e nenhum turno aberto até agora.`, progresso };
    } else {
      const problemas: string[] = [];
      if (r.semLancamentoRecente) problemas.push(`${plural(r.semLancamentoRecente, 'equipe em turno está', 'equipes em turno estão')} sem lançar há ${limite} ou mais`);
      if (r.semTurno && d.diaUtil) problemas.push(`${plural(r.semTurno, 'equipe não abriu', 'equipes não abriram')} turno`);
      if (r.turnosAbertosDeOutroDia) problemas.push(`${plural(r.turnosAbertosDeOutroDia, 'turno segue aberto', 'turnos seguem abertos')} desde outro dia`);
      veredito = problemas.length
        ? {
            tom: r.semLancamentoRecente > emTurno.length / 3 ? 'danger' : 'warning',
            rotulo: 'Atenção',
            titulo: r.semLancamentoRecente ? `${plural(r.semLancamentoRecente, 'equipe parada', 'equipes paradas')} em campo` : 'Cobertura incompleta hoje',
            texto: `${problemas.join('; ')}. ${r.emTurno} em turno agora, ${moedaCompacta(r.producao)} lançados${faltaMeta > 0 ? ` e faltam ${moedaCompacta(faltaMeta)} para a meta do dia` : ''}.`,
            progresso
          }
        : {
            tom: 'success',
            rotulo: 'Dentro do plano',
            titulo: 'Operação fluindo',
            texto: `${r.emTurno} ${r.emTurno === 1 ? 'equipe em turno' : 'equipes em turno'}, todas lançando no ritmo; ${moedaCompacta(r.producao)} em ${plural(r.notas, 'nota', 'notas')}${faltaMeta > 0 ? `, faltam ${moedaCompacta(faltaMeta)} para a meta do dia` : ''}.`,
            progresso
          };
    }
  } else if (r.percentualMetaDiaria != null) {
    const tom = r.percentualMetaDiaria >= 100 ? 'success' : r.percentualMetaDiaria >= 90 ? 'warning' : 'danger';
    veredito = {
      tom,
      rotulo: tom === 'success' ? 'Dentro do plano' : tom === 'warning' ? 'Atenção' : 'Abaixo do plano',
      titulo: tom === 'success' ? 'Meta do dia batida' : 'Meta do dia não atingida',
      texto: `${comTurno} de ${r.equipesAtivas} equipes trabalharam; ${moedaCompacta(r.producao)} em ${plural(r.notas, 'nota', 'notas')}.${r.semTurno && d.diaUtil ? ` ${plural(r.semTurno, 'equipe ficou', 'equipes ficaram')} sem turno.` : ''}`,
      progresso
    };
  } else {
    veredito = {
      tom: 'neutral',
      rotulo: d.diaUtil ? 'Sem meta' : 'Dia não útil',
      titulo: `${comTurno} de ${r.equipesAtivas} equipes com turno`,
      texto: `${moedaCompacta(r.producao)} em ${plural(r.notas, 'nota', 'notas')}. ${d.diaUtil ? 'Nenhuma equipe do recorte tem meta diária.' : 'Dia não útil: não há meta diária.'}`
    };
  }

  const leituras: LeituraGerencial[] = [
    {
      id: 'cobertura',
      tom: d.diaUtil && r.semTurno ? 'warning' : 'success',
      icone: 'pi pi-users',
      titulo: 'Equipes em campo',
      valor: `${comTurno} de ${r.equipesAtivas}`,
      texto: d.hoje ? `${r.emTurno} em turno agora, ${r.encerrados} já encerraram.` : `${r.encerrados} turnos encerrados no dia.`,
      acao: { rotulo: 'Ver turnos e horários', alvo: kpi('turnos') }
    }
  ];
  if (d.hoje && emTurno.length) {
    leituras.push({
      id: 'parados',
      tom: r.semLancamentoRecente ? 'danger' : 'success',
      icone: 'pi pi-hourglass',
      titulo: `Sem lançar há ${limite}+`,
      valor: r.semLancamentoRecente ? plural(r.semLancamentoRecente, 'equipe', 'equipes') : 'Nenhuma',
      texto: piorParada && piorParada.semLancamentoRecente
        ? `${piorParada.codigo} é a mais longa: ${duracao(piorParada.minutosSemLancamento)} sem nota.`
        : 'Todas as equipes em turno lançaram recentemente.',
      acao: { rotulo: 'Ver quem está parado', alvo: kpi('sem-lancamento') }
    });
  }
  if (r.semTurno && d.diaUtil) {
    const metaParada = d.equipes.filter((e) => e.situacao === 'SEM_TURNO').reduce((t, e) => t + (e.metaDiaria ?? 0), 0);
    leituras.push({
      id: 'sem-turno',
      tom: 'warning',
      icone: 'pi pi-user-minus',
      titulo: 'Sem turno no dia',
      valor: plural(r.semTurno, 'equipe', 'equipes'),
      texto: metaParada ? `${moedaCompacta(metaParada)} de meta diária dessas equipes ficaram sem execução.` : 'Equipes ativas do recorte que não abriram turno.',
      acao: { rotulo: 'Ver equipes', alvo: kpi('sem-turno') }
    });
  }
  if (r.turnosAbertosDeOutroDia) {
    leituras.push({
      id: 'esquecidos',
      tom: 'warning',
      icone: 'pi pi-exclamation-triangle',
      titulo: 'Turnos esquecidos abertos',
      valor: String(r.turnosAbertosDeOutroDia),
      texto: 'Abertos em outro dia e ainda não fechados: distorcem duração e composição.',
      acao: { rotulo: 'Ver turnos', alvo: kpi('turnos') }
    });
  }
  if (r.metaDiaria != null) {
    leituras.push({
      id: 'meta',
      tom: faltaMeta <= 0 ? 'success' : d.hoje ? 'info' : 'warning',
      icone: 'pi pi-flag',
      titulo: faltaMeta <= 0 ? 'Meta do dia batida' : 'Para a meta do dia',
      valor: faltaMeta <= 0 ? percentual(r.percentualMetaDiaria) : moedaCompacta(faltaMeta),
      texto: faltaMeta <= 0 ? `As equipes com meta já somam ${moedaCompacta(r.producaoEquipesComMeta)}.` : `Faltam para as ${r.equipesComMeta} equipes com meta (${percentual(r.percentualMetaDiaria)} atingido).`,
      acao: { rotulo: 'Ver produção hora a hora', alvo: kpi('producao') }
    });
  }
  const lancamentos = d.equipes.flatMap((e) => e.lancamentos);
  if (lancamentos.length) {
    const porHora = new Map<number, number>();
    lancamentos.forEach((l) => porHora.set(horaDe(l.dataLancamento), (porHora.get(horaDe(l.dataLancamento)) ?? 0) + 1));
    const [hora, notas] = [...porHora.entries()].sort((a, b) => b[1] - a[1] || a[0] - b[0])[0];
    leituras.push({
      id: 'pico',
      tom: 'info',
      icone: 'pi pi-clock',
      titulo: 'Horário de pico',
      valor: `${rotuloHora(hora)}–${rotuloHora(hora + 1)}`,
      texto: `${plural(notas, 'nota lançada', 'notas lançadas')} nessa hora (${percentual(fatia(notas, lancamentos.length))} do dia).`,
      acao: { rotulo: 'Ver as notas da hora', alvo: { dimension: 'hora', value: String(hora) } }
    });
  }
  const destaque = [...d.equipes].filter((e) => e.producao > 0).sort((a, b) => b.producao - a.producao)[0];
  if (destaque) {
    leituras.push({
      id: 'destaque',
      tom: 'success',
      icone: 'pi pi-star',
      titulo: 'Maior produção do dia',
      valor: destaque.codigo,
      texto: `${moedaCompacta(destaque.producao)} em ${plural(destaque.notas, 'nota', 'notas')}${destaque.percentualMetaDiaria != null ? ` (${percentual(destaque.percentualMetaDiaria)} da meta diária)` : ''}.`,
      acao: { rotulo: 'Ver o turno da equipe', alvo: { dimension: 'equipe', value: destaque.idEquipe } }
    });
  }

  const dia = dataLocal(d.data).toLocaleDateString('pt-BR', { weekday: 'long', day: '2-digit', month: '2-digit' });
  return {
    veredito,
    leituras,
    pergunta: `Faça a leitura gerencial dos turnos de ${dia}: ${veredito.titulo.toLowerCase()} (${veredito.texto}) O que o supervisor deve fazer agora, em ordem de prioridade?`
  };
}
