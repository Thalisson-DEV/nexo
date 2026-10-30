import { DashboardInspecoesResponse } from '../../../../core/models/sst.models';
import { LeituraGerencial, VereditoGerencial } from '../../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import { nomeOu, pct, plural } from './analise-sst-comum';

export interface VisaoGerencialSst {
  readonly veredito: VereditoGerencial;
  readonly leituras: LeituraGerencial[];
  readonly pergunta: string;
}

const kpi = (value: string) => ({ dimension: 'kpi', value });

/**
 * Visão gerencial do dashboard de inspeções: o plano está sendo cumprido (meta), a amostra é suficiente
 * (cobertura do efetivo e das equipes) e o que a inspeção está encontrando (desvios). Só usa números do
 * contrato do dashboard; cada leitura abre a análise que a sustenta.
 */
export function visaoInspecoes(d: DashboardInspecoesResponse, metaComparavel: boolean): VisaoGerencialSst {
  const r = d.resumo;
  const m = d.meta;
  const col = d.colaboradores;
  const comMeta = metaComparavel && m.possuiMeta;

  let veredito: VereditoGerencial;
  if (!r.totalInspecoes) {
    veredito = { tom: 'danger', rotulo: 'Sem cobertura', titulo: 'Nenhuma inspeção no período', texto: 'Com os filtros selecionados não houve inspeção registrada. Revise o período ou o plano de inspeções.' };
  } else if (comMeta) {
    const tom = m.percentualAtingido >= 100 ? 'success' : m.percentualAtingido >= 80 ? 'warning' : 'danger';
    veredito = {
      tom,
      rotulo: tom === 'success' ? 'Plano cumprido' : tom === 'warning' ? 'Atenção' : 'Plano atrasado',
      titulo: tom === 'success' ? 'Plano de inspeções cumprido' : `Plano em ${pct(m.percentualAtingido)}`,
      texto: `${m.realizadoPeriodo} inspeções de ${m.metaPeriodo.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} previstas; ${m.inspetoresNaMeta} de ${m.colaboradoresComMeta} colaboradores na meta. ${pct(r.percentualSemDesvio)} das inspeções sem desvio e ${pct(col.percentualCobertura)} do efetivo alcançado.`,
      progresso: { valor: m.percentualAtingido, rotulo: `${pct(m.percentualAtingido)} da meta do período (faltam ${m.saldoParaMeta.toLocaleString('pt-BR', { maximumFractionDigits: 1 })})` }
    };
  } else {
    const tom = col.percentualCobertura >= 80 ? 'success' : col.percentualCobertura >= 50 ? 'warning' : 'danger';
    veredito = {
      tom,
      rotulo: metaComparavel ? 'Sem meta' : 'Meta não comparável',
      titulo: `${plural(r.totalInspecoes, 'inspeção', 'inspeções')}, ${pct(col.percentualCobertura)} do efetivo alcançado`,
      texto: `${pct(r.percentualSemDesvio)} sem desvio, ${plural(r.totalDesvios, 'desvio registrado', 'desvios registrados')}. ${metaComparavel ? 'Nenhuma meta vigente cobre o período.' : 'Os filtros de processo, equipe ou formulário restringem só o realizado, por isso a meta não entra na leitura.'}`,
      progresso: { valor: col.percentualCobertura, rotulo: `${pct(col.percentualCobertura)} do efetivo inspecionado` }
    };
  }

  const leituras: LeituraGerencial[] = [];
  if (comMeta) {
    const abaixo = d.porInspetor.filter((i) => i.meta > 0 && !i.atingiuMeta).length;
    leituras.push({
      id: 'meta',
      tom: abaixo ? 'warning' : 'success',
      icone: 'pi pi-flag',
      titulo: 'Inspetores abaixo da meta',
      valor: `${abaixo} de ${d.porInspetor.filter((i) => i.meta > 0).length}`,
      texto: m.colaboradoresSemAcesso
        ? `${plural(m.colaboradoresSemAcesso, 'colaborador com meta não tem', 'colaboradores com meta não têm')} usuário para lançar.`
        : 'Realizado frente à meta individual de cada inspetor.',
      acao: { rotulo: 'Ver quem e quanto falta', alvo: kpi('meta') }
    });
  }
  if (r.totalPeriodoAnterior) {
    leituras.push({
      id: 'volume',
      tom: r.variacaoPercentual >= 0 ? 'success' : 'warning',
      icone: r.variacaoPercentual >= 0 ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right',
      titulo: 'Volume contra o período anterior',
      valor: `${r.variacaoPercentual > 0 ? '+' : ''}${pct(r.variacaoPercentual)}`,
      texto: `${r.totalInspecoes} inspeções agora, ${r.totalPeriodoAnterior} no período anterior.`,
      acao: { rotulo: 'Analisar volume', alvo: kpi('inspecoes') }
    });
  }
  leituras.push({
    id: 'conformidade',
    tom: r.percentualSemDesvio >= 90 ? 'success' : r.percentualSemDesvio >= 70 ? 'warning' : 'danger',
    icone: 'pi pi-shield',
    titulo: 'Inspeções sem desvio',
    valor: pct(r.percentualSemDesvio),
    texto: `${plural(r.totalDesvios, 'desvio', 'desvios')} em ${plural(r.inspecoesComDesvio, 'inspeção', 'inspeções')}.`,
    acao: { rotulo: 'Ver onde estão os desvios', alvo: kpi('conformidade') }
  });
  leituras.push({
    id: 'cobertura',
    tom: col.colaboradoresSemInspecao ? 'warning' : 'success',
    icone: 'pi pi-users',
    titulo: 'Efetivo sem inspeção',
    valor: plural(col.colaboradoresSemInspecao, 'colaborador', 'colaboradores'),
    texto: `${pct(col.percentualCobertura)} do efetivo ativo foi alcançado por alguma inspeção.`,
    acao: { rotulo: 'Analisar cobertura', alvo: kpi('cobertura') }
  });
  if (col.colaboradoresReincidentes) {
    leituras.push({
      id: 'reincidentes',
      tom: 'danger',
      icone: 'pi pi-replay',
      titulo: 'Colaboradores reincidentes',
      valor: String(col.colaboradoresReincidentes),
      texto: 'Receberam 2 ou mais desvios no período — candidatos a orientação direcionada.',
      acao: { rotulo: 'Ver colaboradores', alvo: kpi('cobertura') }
    });
  }
  const semAmostra = d.porEquipe.filter((e) => e.diasSemInspecao > 30);
  if (semAmostra.length) {
    const pior = [...semAmostra].sort((a, b) => b.diasSemInspecao - a.diasSemInspecao)[0];
    leituras.push({
      id: 'equipes-sem-inspecao',
      tom: 'warning',
      icone: 'pi pi-eye-slash',
      titulo: 'Equipes há 30+ dias sem inspeção',
      valor: String(semAmostra.length),
      texto: `${nomeOu(pior.nomeEquipe)} está há ${pior.diasSemInspecao} dias sem inspeção.`,
      acao: { rotulo: 'Ver a equipe', alvo: { dimension: 'equipe', value: pior.idEquipe } }
    });
  }
  const topInspetor = d.porInspetor[0];
  if (topInspetor && topInspetor.percentualDoTotal >= 40 && d.porInspetor.length > 2) {
    leituras.push({
      id: 'dependencia',
      tom: 'warning',
      icone: 'pi pi-user',
      titulo: 'Plano dependente de uma pessoa',
      valor: pct(topInspetor.percentualDoTotal),
      texto: `${nomeOu(topInspetor.nomeInspetor)} faz essa fatia das inspeções.`,
      acao: { rotulo: 'Ver inspetores', alvo: kpi('inspetores') }
    });
  }

  return {
    veredito,
    leituras,
    pergunta: `Faça a leitura gerencial das inspeções de segurança deste recorte: ${veredito.titulo.toLowerCase()} (${veredito.texto}) Quais as 3 ações prioritárias para a próxima semana?`
  };
}
