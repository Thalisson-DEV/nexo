import { of } from 'rxjs';
import { CesDataTableColumn } from '@ces/ui';
import { SolicitacaoDoPainel } from '../../../../core/models/painel-solicitacoes.models';
import {
  AlvoAnalise,
  DestaqueAnalise,
  DetalheRegistro,
  GraficoAnalise,
  KpiAnalise,
  LinkAnalise,
  VistaAnalise,
  chaveDoAlvo
} from '../../../../shared/components/dashboard/dashboard-analise.models';
import { LeituraGerencial, VereditoGerencial } from '../../../../shared/components/dashboard/dashboard-visao-gerencial.component';
import {
  ETAPAS,
  ETAPAS_ABERTAS,
  FAIXAS_CICLO,
  FAIXAS_IDADE,
  ModoPainel,
  ROTULO_PROCESSO,
  Recorte,
  SEM_CHAVE,
  TIPOS_OBRA,
  aprovacaoHoras,
  atrasada,
  cicloHoras,
  contar,
  diaDe,
  diaHora,
  diaMes,
  diasDeAtraso,
  distribuicaoDoCiclo,
  ehDevolucao,
  fatia,
  fluxoDasAbertas,
  idadeDias,
  indicadores,
  inteiro,
  materiais,
  motivosDePendencia,
  percentual,
  plural,
  quantidade,
  ranking,
  rotaDaSolicitacao,
  rotuloEtapa,
  rotuloTipo,
  separacaoHoras,
  serieDiaria,
  situacao,
  tempo,
  variacao,
  variacaoSobre
} from './solicitacoes-painel.analise';

/**
 * Leitura gerencial e análises detalhadas (drill-down) do painel de solicitações. Regras sem
 * limiar inventado: usam a data prevista (atraso), a etapa operacional e o período anterior.
 */

export interface VisaoGerencial {
  readonly veredito: VereditoGerencial;
  readonly leituras: LeituraGerencial[];
  readonly pergunta: string;
}

const kpi = (value: string): AlvoAnalise => ({ dimension: 'kpi', value });

export function visaoGerencial(r: Recorte, modo: ModoPainel): VisaoGerencial {
  const ind = indicadores(r.periodo);
  const atrasadas = r.abertas.filter((l) => atrasada(l, r.hoje));
  const nomeModo = modo === 'GERAL' ? 'das solicitações' : `do processo ${ROTULO_PROCESSO[modo].toLowerCase()}`;
  const varCiclo = variacaoSobre(ind.cicloMedia, r.anterior?.cicloMedioHoras ?? null);
  const varVolume = variacaoSobre(ind.total, r.anterior?.total ?? null);

  let veredito: VereditoGerencial;
  if (!ind.total && !r.abertas.length) {
    veredito = {
      tom: 'neutral',
      rotulo: 'Sem movimento',
      titulo: 'Nenhuma solicitação no recorte',
      texto: 'Não há solicitações criadas no período nem abertas de períodos anteriores. Amplie o período ou ajuste os filtros.'
    };
  } else {
    const shareAtraso = fatia(atrasadas.length, r.abertas.length);
    const tom = atrasadas.length === 0 ? 'success' : shareAtraso >= 25 ? 'danger' : 'warning';
    const partes = [
      `${plural(ind.total, 'solicitação criada', 'solicitações criadas')} no período${varVolume !== null ? ` (${variacao(varVolume)} contra o período anterior)` : ''}`,
      ind.taxaConclusao !== null ? `${percentual(ind.taxaConclusao)} já concluídas` : null,
      ind.cicloMediana !== null ? `ciclo mediano de ${tempo(ind.cicloMediana)}` : null
    ].filter(Boolean);
    const fila = r.abertas.length
      ? `${plural(r.abertas.length, 'segue aberta', 'seguem abertas')}${r.backlog.length ? `, ${r.backlog.length} delas de antes do período` : ''}${atrasadas.length ? `; ${plural(atrasadas.length, 'está atrasada', 'estão atrasadas')} na data prevista` : ''}.`
      : 'Nenhuma solicitação em aberto.';
    veredito = {
      tom,
      rotulo: tom === 'success' ? 'Dentro do plano' : tom === 'warning' ? 'Atenção' : 'Agir agora',
      titulo: atrasadas.length
        ? `${plural(atrasadas.length, 'solicitação atrasada', 'solicitações atrasadas')} na fila`
        : r.abertas.length ? 'Fila em dia' : 'Fluxo concluído',
      texto: `${partes.join(', ')}. ${fila}`,
      progresso: ind.taxaConclusao !== null
        ? { valor: ind.taxaConclusao, rotulo: `${percentual(ind.taxaConclusao)} das solicitações do período concluídas (sem as recusadas)` }
        : undefined
    };
  }

  const leituras: LeituraGerencial[] = [];
  const fluxo = fluxoDasAbertas(r.abertas, modo, r.hoje, r.agora).filter((e) => e.total > 0);
  const gargalo = [...fluxo].sort((a, b) => b.total - a.total)[0];
  if (gargalo) {
    leituras.push({
      id: 'gargalo',
      tom: gargalo.atrasadas ? 'warning' : 'info',
      icone: gargalo.icone,
      titulo: 'Onde a fila está parada',
      valor: gargalo.rotulo,
      texto: `${plural(gargalo.total, 'solicitação', 'solicitações')} (${percentual(gargalo.fatia)} das abertas), idade mediana de ${tempo((gargalo.idadeMediana ?? 0) * 24)}${gargalo.atrasadas ? `; ${gargalo.atrasadas} atrasada(s)` : ''}.`,
      acao: { rotulo: 'Ver a etapa', alvo: { dimension: 'etapa', value: gargalo.etapa } }
    });
  }
  if (atrasadas.length) {
    const pior = [...atrasadas].sort((a, b) => diasDeAtraso(b, r.hoje) - diasDeAtraso(a, r.hoje))[0];
    leituras.push({
      id: 'atrasadas',
      tom: 'danger',
      icone: 'pi pi-exclamation-triangle',
      titulo: 'Atrasadas na data prevista',
      valor: plural(atrasadas.length, 'solicitação', 'solicitações'),
      texto: `A mais atrasada${pior.equipe ? ` (equipe ${pior.equipe})` : ''} venceu há ${plural(diasDeAtraso(pior, r.hoje), 'dia', 'dias')} e está em "${rotuloEtapa(pior.etapa).toLowerCase()}".`,
      acao: { rotulo: 'Ver atrasadas', alvo: kpi('atrasadas') }
    });
  }
  if (varCiclo !== null && ind.cicloMedia !== null) {
    leituras.push({
      id: 'ciclo',
      tom: varCiclo <= 0 ? 'success' : varCiclo > 15 ? 'warning' : 'info',
      icone: varCiclo <= 0 ? 'pi pi-arrow-down-right' : 'pi pi-arrow-up-right',
      titulo: varCiclo <= 0 ? 'Atendimento mais rápido' : 'Atendimento mais lento',
      valor: variacao(varCiclo),
      texto: `Ciclo médio de ${tempo(ind.cicloMedia)} contra ${tempo(r.anterior!.cicloMedioHoras)} no período anterior de mesma duração.`,
      acao: { rotulo: 'Analisar o ciclo', alvo: kpi('ciclo') }
    });
  }
  if (r.backlog.length) {
    const maisAntiga = Math.max(...r.backlog.map((l) => idadeDias(l, r.agora)));
    leituras.push({
      id: 'backlog',
      tom: 'warning',
      icone: 'pi pi-history',
      titulo: 'Herdadas de períodos anteriores',
      valor: plural(r.backlog.length, 'aberta', 'abertas'),
      texto: `Criadas antes do período e ainda sem conclusão; a mais antiga tem ${plural(Math.floor(maisAntiga), 'dia', 'dias')}.`,
      acao: { rotulo: 'Ver o backlog', alvo: kpi('backlog') }
    });
  }

  const equipes = ranking(r.periodo, r.backlog, r.hoje, (l) => l.idEquipe, (l) => l.equipe).filter((e) => e.chave !== SEM_CHAVE);
  const equipeCritica = [...equipes].sort((a, b) => b.atrasadas - a.atrasadas || b.abertas - a.abertas)[0];
  if (equipeCritica && (equipeCritica.atrasadas || equipeCritica.abertas > 1)) {
    leituras.push({
      id: 'equipe',
      tom: equipeCritica.atrasadas ? 'warning' : 'info',
      icone: 'pi pi-users',
      titulo: equipeCritica.atrasadas ? 'Equipe com mais atrasos' : 'Equipe com mais pedidos abertos',
      valor: equipeCritica.rotulo,
      texto: `${plural(equipeCritica.abertas, 'aberta', 'abertas')}${equipeCritica.atrasadas ? `, ${equipeCritica.atrasadas} atrasada(s)` : ''}; ${plural(equipeCritica.total, 'solicitação', 'solicitações')} no período.`,
      acao: { rotulo: 'Ver a equipe', alvo: { dimension: 'equipe', value: equipeCritica.chave } }
    });
  }

  const almox = ranking(r.periodo, r.backlog, r.hoje, (l) => l.idAlmoxarifado, (l) => l.almoxarifado).filter((a) => a.chave !== SEM_CHAVE && a.cicloMediana !== null);
  if (almox.length > 1) {
    const lento = [...almox].sort((a, b) => (b.cicloMediana ?? 0) - (a.cicloMediana ?? 0))[0];
    leituras.push({
      id: 'almoxarifado',
      tom: 'info',
      icone: 'pi pi-warehouse',
      titulo: 'Almoxarifado com ciclo mais longo',
      valor: lento.rotulo,
      texto: `Ciclo mediano de ${tempo(lento.cicloMediana)} contra ${tempo(ind.cicloMediana)} no recorte, em ${plural(lento.total, 'solicitação', 'solicitações')}.`,
      acao: { rotulo: 'Ver o almoxarifado', alvo: { dimension: 'almox', value: lento.chave } }
    });
  }

  const demanda = materiais(r.materiais).slice(0, 15).filter((m) => m.atendimento !== null && m.quantidadeAtendida < m.quantidadeSolicitada);
  const faltante = [...demanda].sort((a, b) => (a.atendimento ?? 100) - (b.atendimento ?? 100))[0];
  if (faltante && (faltante.atendimento ?? 100) < 90) {
    leituras.push({
      id: 'material',
      tom: 'warning',
      icone: 'pi pi-box',
      titulo: 'Material de alta demanda pouco atendido',
      valor: faltante.codigo,
      texto: `${faltante.descricao}: ${percentual(faltante.atendimento)} atendido (${quantidade(faltante.quantidadeAtendida)} de ${quantidade(faltante.quantidadeSolicitada)} ${faltante.unidade ?? ''}) em ${plural(faltante.solicitacoes, 'solicitação', 'solicitações')}.`.replace(' )', ')'),
      acao: { rotulo: 'Ver atendimento', alvo: kpi('atendimento') }
    });
  }

  if (ind.recusadas) {
    const motivo = contar(r.periodo.filter((l) => situacao(l) === 'recusada').map((l) => l.motivoRejeicao), 'Sem motivo informado')[0];
    leituras.push({
      id: 'recusas',
      tom: (ind.taxaRecusa ?? 0) >= 10 ? 'warning' : 'info',
      icone: 'pi pi-times-circle',
      titulo: 'Recusas',
      valor: percentual(ind.taxaRecusa),
      texto: `${plural(ind.recusadas, 'solicitação rejeitada ou cancelada', 'solicitações rejeitadas ou canceladas')}${motivo ? `; principal motivo: ${motivo.rotulo} (${motivo.total}).` : '.'}`,
      acao: { rotulo: 'Ver recusas', alvo: kpi('recusas') }
    });
  }

  const pendencia = motivosDePendencia(r.abertas)[0];
  if (pendencia) {
    leituras.push({
      id: 'pendencias',
      tom: 'warning',
      icone: 'pi pi-flag',
      titulo: 'Pendência mais comum nas abertas',
      valor: pendencia.rotulo,
      texto: `Registrada em ${plural(pendencia.total, 'solicitação aberta', 'solicitações abertas')} (${percentual(pendencia.fatia)} das pendências).`,
      acao: { rotulo: 'Ver pendências', alvo: kpi('pendencias') }
    });
  }

  if (modo !== 'TECNICO' && ind.foraPrazoSaque) {
    const comerciais = r.periodo.filter((l) => l.contexto === 'COMERCIAL');
    leituras.push({
      id: 'fora-prazo',
      tom: 'info',
      icone: 'pi pi-calendar-times',
      titulo: 'Pedidos com prazo curto de saque',
      valor: percentual(fatia(comerciais.filter((l) => l.foraPrazoSaque).length, comerciais.length)),
      texto: 'Pedidos com retirada prevista para menos de 2 dias após a abertura: comprimem aprovação e separação.',
      acao: { rotulo: 'Ver os pedidos', alvo: kpi('fora-prazo') }
    });
  }

  if (modo !== 'COMERCIAL') {
    const semPlanejamento = r.abertas.filter((l) => l.etapa === 'PENDENTE_PLANEJAMENTO');
    if (semPlanejamento.length) {
      const semReserva = semPlanejamento.filter((l) => !l.temReserva).length;
      leituras.push({
        id: 'planejamento',
        tom: 'warning',
        icone: 'pi pi-calendar-plus',
        titulo: 'Técnicas sem planejamento completo',
        valor: plural(semPlanejamento.length, 'solicitação', 'solicitações'),
        texto: `Falta data prevista, equipe de saque ou reserva${semReserva ? ` (${semReserva} sem reserva registrada)` : ''}: não entram na fila de separação.`,
        acao: { rotulo: 'Ver a etapa', alvo: { dimension: 'etapa', value: 'PENDENTE_PLANEJAMENTO' } }
      });
    }
    const devolucoes = r.abertas.filter((l) => l.etapa === 'PENDENTE_DEVOLUCAO');
    if (devolucoes.length) {
      leituras.push({
        id: 'devolucoes',
        tom: devolucoes.some((l) => atrasada(l, r.hoje)) ? 'warning' : 'info',
        icone: 'pi pi-replay',
        titulo: 'Devoluções técnicas em aberto',
        valor: plural(devolucoes.length, 'devolução', 'devoluções'),
        texto: `Material que ainda precisa voltar ao almoxarifado (222/922); ${devolucoes.filter((l) => atrasada(l, r.hoje)).length} passou(aram) da data prevista.`,
        acao: { rotulo: 'Ver devoluções', alvo: { dimension: 'etapa', value: 'PENDENTE_DEVOLUCAO' } }
      });
    }
  }

  if (modo === 'GERAL') {
    const com = indicadores(r.periodo.filter((l) => l.contexto === 'COMERCIAL'));
    const tec = indicadores(r.periodo.filter((l) => l.contexto === 'TECNICO'));
    if (com.total && tec.total && com.cicloMediana !== null && tec.cicloMediana !== null) {
      const maisLento = tec.cicloMediana > com.cicloMediana ? 'TECNICO' : 'COMERCIAL';
      leituras.push({
        id: 'processos',
        tom: 'info',
        icone: 'pi pi-arrows-h',
        titulo: 'Comercial × técnico',
        valor: `${tempo(com.cicloMediana)} × ${tempo(tec.cicloMediana)}`,
        texto: `Ciclo mediano de cada processo. O ${ROTULO_PROCESSO[maisLento].toLowerCase()} responde por ${percentual(fatia(maisLento === 'TECNICO' ? tec.total : com.total, ind.total))} do volume.`,
        acao: { rotulo: `Analisar o ${ROTULO_PROCESSO[maisLento].toLowerCase()}`, alvo: { dimension: 'processo', value: maisLento } }
      });
    }
  }

  return {
    veredito,
    leituras,
    pergunta: `Faça a leitura gerencial ${nomeModo} de material deste recorte: ${veredito.titulo.toLowerCase()} (${veredito.texto}) Onde estão os gargalos, quais equipes e almoxarifados priorizar e quais 3 ações tomar agora?`
  };
}

// ------------------------------------------------------------------ análises detalhadas

export interface ContextoAnalise {
  readonly r: Recorte;
  readonly modo: ModoPainel;
  readonly inicio: string;
  readonly fim: string;
  readonly recorteTexto: string;
}

const navegar = (dimension: string, value: string) => ({ tipo: 'navegar' as const, alvo: { dimension, value } });

function colunas(c: ContextoAnalise): CesDataTableColumn<SolicitacaoDoPainel>[] {
  const hoje = c.r.hoje;
  return [
    { key: 'dataSolicitacao', header: 'Aberta em', priority: 1, width: '8rem', display: (l) => diaHora(l.dataSolicitacao), searchable: false },
    ...(c.modo === 'GERAL' ? [{ key: 'contexto', header: 'Processo', priority: 2 as const, value: (l: SolicitacaoDoPainel) => ROTULO_PROCESSO[l.contexto] }] : []),
    ...(c.modo !== 'COMERCIAL' ? [{ key: 'tipoTecnico', header: 'Tipo', priority: 2 as const, value: (l: SolicitacaoDoPainel) => (l.tipoTecnico ? rotuloTipo(l.tipoTecnico) : 'Comercial') }] : []),
    { key: 'etapa', header: 'Etapa', priority: 1, value: (l) => rotuloEtapa(l.etapa) },
    { key: 'equipe', header: 'Equipe', priority: 1, value: (l) => l.equipe ?? '—' },
    { key: 'almoxarifado', header: 'Almoxarifado', priority: 2, value: (l) => l.almoxarifado ?? '—' },
    { key: 'dataPrevista', header: 'Prevista', priority: 2, value: (l) => l.dataPrevista ?? '', display: (l) => (l.dataPrevista ? `${diaMes(l.dataPrevista)}${atrasada(l, hoje) ? ' · atrasada' : ''}` : '—') },
    {
      key: 'tempo',
      header: 'Ciclo / idade',
      priority: 2,
      value: (l) => cicloHoras(l) ?? idadeDias(l, c.r.agora) * 24,
      display: (l) => (situacao(l) === 'concluida' ? tempo(cicloHoras(l)) : situacao(l) === 'aberta' ? `${tempo(idadeDias(l, c.r.agora) * 24)} aberta` : '—'),
      searchable: false
    },
    { key: 'itens', header: 'Itens', type: 'integer', priority: 3 },
    {
      key: 'atendido',
      header: '% atendido',
      type: 'percent',
      priority: 3,
      value: (l) => (l.quantidadeSolicitada > 0 ? fatia(l.quantidadeAtendida, l.quantidadeSolicitada) : null)
    }
  ];
}

function detalhe(l: SolicitacaoDoPainel, c: ContextoAnalise): DetalheRegistro {
  const s = situacao(l);
  const links: LinkAnalise[] = [{ rotulo: 'Abrir a solicitação', icone: 'pi pi-external-link', rota: rotaDaSolicitacao(l) }];
  return {
    titulo: `Solicitação ${l.contexto === 'TECNICO' ? rotuloTipo(l.tipoTecnico) : 'comercial'}`,
    subtitulo: `${l.equipe ?? 'Sem equipe'} · aberta em ${diaHora(l.dataSolicitacao)}`,
    campos: [
      { rotulo: 'Etapa', valor: rotuloEtapa(l.etapa), icone: 'pi pi-sitemap' },
      { rotulo: 'Status', valor: l.status, icone: 'pi pi-tag' },
      { rotulo: 'Data prevista', valor: l.dataPrevista ? `${diaMes(l.dataPrevista)}${atrasada(l, c.r.hoje) ? ` (atrasada há ${plural(diasDeAtraso(l, c.r.hoje), 'dia', 'dias')})` : ''}` : 'Não definida', icone: 'pi pi-calendar' },
      { rotulo: s === 'concluida' ? 'Ciclo até a entrega' : 'Em aberto há', valor: s === 'concluida' ? tempo(cicloHoras(l)) : tempo(idadeDias(l, c.r.agora) * 24), icone: 'pi pi-stopwatch' },
      { rotulo: 'Aprovação', valor: tempo(aprovacaoHoras(l)), icone: 'pi pi-check' },
      ...(l.contexto === 'TECNICO' ? [{ rotulo: 'Separação', valor: tempo(separacaoHoras(l)), icone: 'pi pi-box' }] : []),
      { rotulo: 'Almoxarifado', valor: l.almoxarifado ?? '—', icone: 'pi pi-warehouse' },
      { rotulo: 'Destino', valor: l.estoqueDestino ?? '—', icone: 'pi pi-arrow-right' },
      { rotulo: 'Base · processo', valor: [l.baseOperacional, l.processo].filter(Boolean).join(' · ') || '—', icone: 'pi pi-building' },
      ...(l.contexto === 'TECNICO'
        ? [
            { rotulo: 'Obra', valor: [l.numeroObra, l.tipoObra ? TIPOS_OBRA[l.tipoObra] ?? l.tipoObra : null, l.ocorrencia].filter(Boolean).join(' · ') || '—', icone: 'pi pi-briefcase' },
            { rotulo: 'Reserva', valor: l.temReserva ? 'Registrada' : 'Sem reserva', icone: 'pi pi-bookmark' }
          ]
        : []),
      { rotulo: 'Quantidades', valor: `${quantidade(l.quantidadeAtendida)} de ${quantidade(l.quantidadeSolicitada)} (${plural(l.itens, 'item', 'itens')})`, icone: 'pi pi-box' }
    ],
    listas: [
      ...(l.motivosPendencia ? [{ rotulo: 'Pendências', itens: l.motivosPendencia.split(', ') }] : []),
      ...(l.motivoRejeicao ? [{ rotulo: 'Motivo de rejeição', itens: [l.motivoRejeicao] }] : [])
    ],
    links
  };
}

/** Indicadores e gráficos de um subconjunto de solicitações — a base de toda análise. */
function vistaDeSubconjunto(
  c: ContextoAnalise,
  alvo: AlvoAnalise,
  o: {
    eyebrow: string;
    titulo: string;
    descricao: string;
    icone: string;
    linhas: SolicitacaoDoPainel[];
    destaques?: DestaqueAnalise[];
    kpisExtras?: KpiAnalise[];
    graficosExtras?: GraficoAnalise[];
    semGraficoEtapas?: boolean;
    nomeArquivo: string;
  }
): VistaAnalise {
  const doPeriodo = o.linhas.filter((l) => !l.anteriorAoPeriodo);
  const ind = indicadores(doPeriodo);
  const abertas = o.linhas.filter((l) => situacao(l) === 'aberta');
  const atrasadas = abertas.filter((l) => atrasada(l, c.r.hoje)).length;
  const kpis: KpiAnalise[] = [
    { rotulo: 'Solicitações', valor: inteiro(o.linhas.length), detalhe: o.linhas.length !== doPeriodo.length ? `${doPeriodo.length} no período + ${o.linhas.length - doPeriodo.length} anteriores` : 'no período', icone: 'pi pi-inbox' },
    { rotulo: 'Concluídas', valor: percentual(ind.taxaConclusao), detalhe: `${ind.concluidas} de ${ind.total - ind.recusadas} (sem recusadas)`, tom: 'success', icone: 'pi pi-check-circle' },
    { rotulo: 'Em aberto', valor: inteiro(abertas.length), detalhe: atrasadas ? `${atrasadas} atrasada(s)` : 'nenhuma atrasada', tom: atrasadas ? 'warning' : 'neutral', icone: 'pi pi-clock' },
    { rotulo: 'Ciclo mediano', valor: tempo(ind.cicloMediana), detalhe: ind.cicloP90 !== null ? `90% em até ${tempo(ind.cicloP90)}` : 'sem concluídas', icone: 'pi pi-stopwatch' },
    ...(o.kpisExtras ?? [])
  ];
  const graficos: GraficoAnalise[] = [];
  if (!o.semGraficoEtapas && abertas.length) {
    const fluxo = fluxoDasAbertas(abertas, c.modo, c.r.hoje, c.r.agora).filter((e) => e.total > 0);
    graficos.push({
      id: 'etapas',
      titulo: 'Abertas por etapa',
      descricao: 'Clique numa etapa para filtrar as solicitações.',
      tipo: 'bar-horizontal',
      rotulos: fluxo.map((e) => e.rotulo),
      series: [
        { name: 'No prazo', values: fluxo.map((e) => e.total - e.atrasadas) },
        { name: 'Atrasadas', values: fluxo.map((e) => e.atrasadas) }
      ],
      formato: 'integer',
      altura: Math.max(180, fluxo.length * 36 + 60),
      aoSelecionar: (i) => ({ tipo: 'filtrar', filtro: { chave: `etapa-${fluxo[i].etapa}`, rotulo: fluxo[i].rotulo, aceita: (l: SolicitacaoDoPainel) => l.etapa === fluxo[i].etapa } }),
      rotuloSelecao: 'Ver solicitações da etapa'
    });
  }
  const ciclo = distribuicaoDoCiclo(doPeriodo);
  if (ciclo.some((n) => n > 0)) {
    graficos.push({
      id: 'ciclo',
      titulo: 'Tempo até a entrega',
      descricao: 'Concluídas por faixa de ciclo (abertura → entrega). Clique para filtrar.',
      tipo: 'bar',
      rotulos: FAIXAS_CICLO.map((f) => f.rotulo),
      series: [{ name: 'Solicitações', values: ciclo }],
      formato: 'integer',
      altura: 220,
      aoSelecionar: (i) => ({
        tipo: 'filtrar',
        filtro: {
          chave: `ciclo-${FAIXAS_CICLO[i].id}`,
          rotulo: `Ciclo: ${FAIXAS_CICLO[i].rotulo}`,
          aceita: (l: SolicitacaoDoPainel) => {
            const h = cicloHoras(l);
            return h !== null && h >= FAIXAS_CICLO[i].de && h < FAIXAS_CICLO[i].ate;
          }
        }
      }),
      rotuloSelecao: 'Ver solicitações da faixa'
    });
  }
  graficos.push(...(o.graficosExtras ?? []));

  return {
    chave: chaveDoAlvo(alvo),
    eyebrow: o.eyebrow,
    titulo: o.titulo,
    descricao: `${o.descricao} · ${c.recorteTexto}`,
    icone: o.icone,
    kpis,
    destaques: o.destaques,
    graficos,
    origem: {
      chave: `${chaveDoAlvo(alvo)}:${c.inicio}:${c.fim}`,
      titulo: 'Solicitações que compõem este número',
      rotuloAba: 'Origem · solicitações',
      icone: 'pi pi-list',
      descricao: 'Clique numa solicitação para ver marcos, quantidades, pendências e o atalho para abri-la.',
      colunas: colunas(c),
      carregar: () => of({ linhas: o.linhas, total: o.linhas.length }),
      chaveLinha: (l: SolicitacaoDoPainel) => l.id,
      rotuloLinha: (l: SolicitacaoDoPainel) => `Solicitação de ${diaHora(l.dataSolicitacao)}`,
      detalhar: (l: SolicitacaoDoPainel) => detalhe(l, c),
      nomeArquivo: o.nomeArquivo,
      vazio: 'Nenhuma solicitação neste recorte'
    },
    links: [
      c.modo === 'TECNICO' || (c.modo === 'GERAL' && o.linhas.every((l) => l.contexto === 'TECNICO'))
        ? { rotulo: 'Kanban técnico', icone: 'pi pi-th-large', rota: '/almoxarifado/solicitacoes-tecnicas/kanban' }
        : { rotulo: 'Lista de solicitações', icone: 'pi pi-list', rota: '/almoxarifado/solicitacoes-tecnicas' }
    ],
    copiloto: {
      foco: { tipo: 'secao', id: `analise.${chaveDoAlvo(alvo)}`, rotulo: o.titulo },
      pergunta: `Analise ${o.titulo.toLowerCase()} neste recorte: o que explica os tempos e os atrasos, e o que fazer primeiro?`
    }
  };
}

export function vistaPainel(alvo: AlvoAnalise, c: ContextoAnalise): VistaAnalise | null {
  const { r } = c;
  const todas = [...r.periodo, ...r.backlog];
  const base = (o: Parameters<typeof vistaDeSubconjunto>[2]) => vistaDeSubconjunto(c, alvo, o);
  switch (alvo.dimension) {
    case 'kpi':
      return vistaKpi(alvo, c, base, todas);
    case 'etapa': {
      const etapa = alvo.value as keyof typeof ETAPAS;
      if (!ETAPAS[etapa]) return null;
      const lista = ETAPAS_ABERTAS[c.modo];
      const i = lista.indexOf(etapa);
      const vista = base({
        eyebrow: 'Etapa do fluxo',
        titulo: ETAPAS[etapa].rotulo,
        descricao: 'Solicitações paradas nesta etapa agora, incluindo as de antes do período',
        icone: ETAPAS[etapa].icone,
        linhas: todas.filter((l) => l.etapa === etapa).sort((a, b) => idadeDias(b, r.agora) - idadeDias(a, r.agora)),
        semGraficoEtapas: true,
        graficosExtras: [graficoIdade(todas.filter((l) => l.etapa === etapa), c)],
        nomeArquivo: `solicitacoes-etapa-${etapa.toLowerCase()}`
      });
      return {
        ...vista,
        anterior: i > 0 ? { alvo: { dimension: 'etapa', value: lista[i - 1] }, rotulo: ETAPAS[lista[i - 1]].rotulo } : undefined,
        proximo: i >= 0 && i < lista.length - 1 ? { alvo: { dimension: 'etapa', value: lista[i + 1] }, rotulo: ETAPAS[lista[i + 1]].rotulo } : undefined
      };
    }
    case 'idade': {
      const f = FAIXAS_IDADE.find((x) => x.id === alvo.value);
      if (!f) return null;
      return base({
        eyebrow: 'Idade das abertas',
        titulo: `Abertas há ${f.rotulo.toLowerCase()}`,
        descricao: 'Solicitações em aberto nesta faixa de idade',
        icone: 'pi pi-hourglass',
        linhas: r.abertas.filter((l) => idadeDias(l, r.agora) >= f.de && idadeDias(l, r.agora) < f.ate),
        nomeArquivo: `solicitacoes-abertas-${f.id}`
      });
    }
    case 'dia': {
      const linhas = r.periodo.filter((l) => diaDe(l.dataSolicitacao) === alvo.value);
      return base({
        eyebrow: 'Dia',
        titulo: `Criadas em ${diaMes(alvo.value)}`,
        descricao: 'Solicitações abertas neste dia e como estão hoje',
        icone: 'pi pi-calendar',
        linhas,
        nomeArquivo: `solicitacoes-${alvo.value}`
      });
    }
    case 'equipe':
    case 'almox':
    case 'obra': {
      const campo = alvo.dimension === 'equipe' ? 'idEquipe' : alvo.dimension === 'almox' ? 'idAlmoxarifado' : 'idObra';
      const linhas = todas.filter((l) => (l[campo] ?? SEM_CHAVE) === alvo.value);
      if (!linhas.length) return null;
      const exemplo = linhas[0];
      const nome = alvo.value === SEM_CHAVE
        ? (alvo.dimension === 'equipe' ? 'Sem equipe' : 'Não informado')
        : alvo.dimension === 'equipe' ? exemplo.equipe ?? 'Equipe' : alvo.dimension === 'almox' ? exemplo.almoxarifado ?? 'Almoxarifado' : `Obra ${exemplo.numeroObra ?? ''}`.trim();
      const vista = base({
        eyebrow: alvo.dimension === 'equipe' ? 'Equipe' : alvo.dimension === 'almox' ? 'Almoxarifado' : 'Obra',
        titulo: nome,
        descricao: alvo.dimension === 'equipe' ? [exemplo.baseOperacional, exemplo.processo].filter(Boolean).join(' · ') || 'Solicitações da equipe' : 'Solicitações do período e abertas anteriores',
        icone: alvo.dimension === 'equipe' ? 'pi pi-users' : alvo.dimension === 'almox' ? 'pi pi-warehouse' : 'pi pi-briefcase',
        linhas,
        destaques: destaquesDoSubconjunto(linhas, c),
        graficosExtras: [graficoPorDia(linhas.filter((l) => !l.anteriorAoPeriodo), c)],
        nomeArquivo: `solicitacoes-${alvo.dimension}-${nome.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`
      });
      return vista;
    }
    case 'tipo': {
      const linhas = todas.filter((l) => l.tipoTecnico === alvo.value);
      return base({
        eyebrow: 'Tipo técnico',
        titulo: rotuloTipo(alvo.value),
        descricao: ehDevolucao({ tipoTecnico: alvo.value }) ? 'Devoluções de material ao almoxarifado' : 'Adições de material para a obra',
        icone: 'pi pi-tags',
        linhas,
        destaques: destaquesDoSubconjunto(linhas, c),
        nomeArquivo: `solicitacoes-tipo-${alvo.value}`
      });
    }
    case 'tipo-obra': {
      const linhas = todas.filter((l) => (l.tipoObra ?? SEM_CHAVE) === alvo.value);
      return base({
        eyebrow: 'Tipo de obra',
        titulo: TIPOS_OBRA[alvo.value] ?? 'Não informado',
        descricao: 'Solicitações técnicas por tipo de obra',
        icone: 'pi pi-briefcase',
        linhas,
        nomeArquivo: `solicitacoes-obra-${alvo.value.toLowerCase()}`
      });
    }
    case 'processo': {
      if (alvo.value !== 'COMERCIAL' && alvo.value !== 'TECNICO') return null;
      const linhas = todas.filter((l) => l.contexto === alvo.value);
      return base({
        eyebrow: 'Processo',
        titulo: `Processo ${ROTULO_PROCESSO[alvo.value].toLowerCase()}`,
        descricao: 'Solicitações do processo no recorte',
        icone: alvo.value === 'TECNICO' ? 'pi pi-wrench' : 'pi pi-shopping-cart',
        linhas,
        destaques: destaquesDoSubconjunto(linhas, c),
        graficosExtras: [graficoPorDia(linhas.filter((l) => !l.anteriorAoPeriodo), c)],
        nomeArquivo: `solicitacoes-${alvo.value.toLowerCase()}`
      });
    }
    case 'rejeicao':
    case 'pendencia': {
      const linhas = alvo.dimension === 'rejeicao'
        ? r.periodo.filter((l) => situacao(l) === 'recusada' && (l.motivoRejeicao ?? 'Sem motivo informado') === alvo.value)
        : todas.filter((l) => l.possuiPendencia && (l.motivosPendencia ?? 'Não informado').split(', ').includes(alvo.value));
      return base({
        eyebrow: alvo.dimension === 'rejeicao' ? 'Motivo de rejeição' : 'Motivo de pendência',
        titulo: alvo.value,
        descricao: alvo.dimension === 'rejeicao' ? 'Solicitações recusadas por este motivo' : 'Solicitações com esta pendência registrada',
        icone: alvo.dimension === 'rejeicao' ? 'pi pi-times-circle' : 'pi pi-flag',
        linhas,
        nomeArquivo: `solicitacoes-${alvo.dimension}`
      });
    }
    default:
      return null;
  }
}

type Base = (o: Parameters<typeof vistaDeSubconjunto>[2]) => VistaAnalise;

function vistaKpi(alvo: AlvoAnalise, c: ContextoAnalise, base: Base, todas: SolicitacaoDoPainel[]): VistaAnalise | null {
  const { r } = c;
  const ind = indicadores(r.periodo);
  switch (alvo.value) {
    case 'volume':
      return base({
        eyebrow: 'Indicador',
        titulo: 'Solicitações do período',
        descricao: 'Todas as solicitações criadas no período, por dia e situação atual',
        icone: 'pi pi-inbox',
        linhas: r.periodo,
        kpisExtras: [{ rotulo: 'Por dia', valor: ind.porDia.toLocaleString('pt-BR', { maximumFractionDigits: 1 }), detalhe: `${plural(Math.round(ind.itensPorSolicitacao ?? 0), 'item', 'itens')} por solicitação`, icone: 'pi pi-calendar' }],
        graficosExtras: [graficoPorDia(r.periodo, c)],
        destaques: destaquesDoSubconjunto(r.periodo, c),
        nomeArquivo: 'solicitacoes-periodo'
      });
    case 'abertas':
      return base({
        eyebrow: 'Indicador',
        titulo: 'Em aberto agora',
        descricao: 'Fila atual: criadas no período e herdadas de antes, ainda sem conclusão',
        icone: 'pi pi-clock',
        linhas: r.abertas,
        graficosExtras: [graficoIdade(r.abertas, c)],
        nomeArquivo: 'solicitacoes-abertas'
      });
    case 'atrasadas': {
      const atrasadas = r.abertas.filter((l) => atrasada(l, r.hoje)).sort((a, b) => diasDeAtraso(b, r.hoje) - diasDeAtraso(a, r.hoje));
      return base({
        eyebrow: 'Indicador',
        titulo: 'Atrasadas na data prevista',
        descricao: 'Abertas cuja data prevista de retirada ou devolução já passou',
        icone: 'pi pi-exclamation-triangle',
        linhas: atrasadas,
        destaques: destaquesDoSubconjunto(atrasadas, c),
        nomeArquivo: 'solicitacoes-atrasadas'
      });
    }
    case 'backlog':
      return base({
        eyebrow: 'Indicador',
        titulo: 'Abertas de antes do período',
        descricao: 'Backlog herdado: criadas antes do início do período e ainda sem conclusão',
        icone: 'pi pi-history',
        linhas: [...r.backlog].sort((a, b) => idadeDias(b, r.agora) - idadeDias(a, r.agora)),
        graficosExtras: [graficoIdade(r.backlog, c)],
        nomeArquivo: 'solicitacoes-backlog'
      });
    case 'conclusao':
    case 'ciclo':
      return base({
        eyebrow: 'Indicador',
        titulo: alvo.value === 'ciclo' ? 'Ciclo até a entrega' : 'Conclusão do período',
        descricao: 'Concluídas no período: tempo da abertura à entrega ou devolução final',
        icone: 'pi pi-stopwatch',
        linhas: r.periodo.filter((l) => situacao(l) === 'concluida').sort((a, b) => (cicloHoras(b) ?? 0) - (cicloHoras(a) ?? 0)),
        semGraficoEtapas: true,
        kpisExtras: [
          { rotulo: 'Ciclo médio', valor: tempo(ind.cicloMedia), detalhe: r.anterior?.cicloMedioHoras != null ? `anterior: ${tempo(r.anterior.cicloMedioHoras)}` : 'sem período anterior', icone: 'pi pi-history' },
          { rotulo: 'Aprovação mediana', valor: tempo(ind.aprovacaoMediana), detalhe: 'abertura → aprovação', icone: 'pi pi-check' }
        ],
        nomeArquivo: 'solicitacoes-ciclo'
      });
    case 'aprovacao':
      return base({
        eyebrow: 'Indicador',
        titulo: 'Tempo de aprovação',
        descricao: 'Da abertura à aprovação ou verificação, das mais lentas para as mais rápidas',
        icone: 'pi pi-check',
        linhas: r.periodo.filter((l) => aprovacaoHoras(l) !== null).sort((a, b) => (aprovacaoHoras(b) ?? 0) - (aprovacaoHoras(a) ?? 0)),
        semGraficoEtapas: true,
        nomeArquivo: 'solicitacoes-aprovacao'
      });
    case 'separacao':
      return base({
        eyebrow: 'Indicador',
        titulo: 'Separação técnica',
        descricao: 'Solicitações com separação iniciada: tempo de separação e espera pela retirada',
        icone: 'pi pi-box',
        linhas: todas.filter((l) => !!l.separacaoIniciadaEm).sort((a, b) => (separacaoHoras(b) ?? 0) - (separacaoHoras(a) ?? 0)),
        kpisExtras: [
          { rotulo: 'Separação mediana', valor: tempo(ind.separacaoMediana), detalhe: 'início → fim da separação', icone: 'pi pi-box' },
          { rotulo: 'Espera pela retirada', valor: tempo(ind.esperaRetiradaMediana), detalhe: 'separado → entregue (mediana)', icone: 'pi pi-truck' }
        ],
        nomeArquivo: 'solicitacoes-separacao'
      });
    case 'recusas': {
      const recusadas = r.periodo.filter((l) => situacao(l) === 'recusada');
      const motivos = contar(recusadas.map((l) => l.motivoRejeicao), 'Sem motivo informado');
      return base({
        eyebrow: 'Indicador',
        titulo: 'Recusas',
        descricao: 'Rejeitadas e canceladas no período, por motivo',
        icone: 'pi pi-times-circle',
        linhas: recusadas,
        semGraficoEtapas: true,
        graficosExtras: motivos.length
          ? [{
              id: 'motivos',
              titulo: 'Motivos de rejeição',
              tipo: 'bar-horizontal',
              rotulos: motivos.map((m) => m.rotulo),
              series: [{ name: 'Solicitações', values: motivos.map((m) => m.total) }],
              formato: 'integer',
              altura: Math.max(180, motivos.length * 32 + 60),
              aoSelecionar: (i: number) => navegar('rejeicao', motivos[i].rotulo),
              rotuloSelecao: 'Analisar motivo'
            }]
          : [],
        nomeArquivo: 'solicitacoes-recusadas'
      });
    }
    case 'pendencias': {
      const comPendencia = todas.filter((l) => l.possuiPendencia);
      const motivos = motivosDePendencia(comPendencia);
      return base({
        eyebrow: 'Indicador',
        titulo: 'Pendências registradas',
        descricao: 'Solicitações com pendência na própria solicitação ou em algum item',
        icone: 'pi pi-flag',
        linhas: comPendencia,
        graficosExtras: motivos.length
          ? [{
              id: 'motivos-pendencia',
              titulo: 'Motivos de pendência',
              tipo: 'bar-horizontal',
              rotulos: motivos.map((m) => m.rotulo),
              series: [{ name: 'Solicitações', values: motivos.map((m) => m.total) }],
              formato: 'integer',
              altura: Math.max(180, motivos.length * 32 + 60),
              aoSelecionar: (i: number) => navegar('pendencia', motivos[i].rotulo),
              rotuloSelecao: 'Analisar motivo'
            }]
          : [],
        nomeArquivo: 'solicitacoes-pendencias'
      });
    }
    case 'atendimento': {
      const concluidas = r.periodo.filter((l) => situacao(l) === 'concluida' && l.quantidadeSolicitada > l.quantidadeAtendida);
      const top = materiais(r.materiais).filter((m) => m.atendimento !== null).slice(0, 15);
      return base({
        eyebrow: 'Indicador',
        titulo: 'Atendimento das quantidades',
        descricao: 'Concluídas que entregaram menos do que foi pedido, e o atendimento dos materiais mais pedidos',
        icone: 'pi pi-percentage',
        linhas: concluidas,
        semGraficoEtapas: true,
        kpisExtras: [{ rotulo: 'Atendimento', valor: percentual(ind.atendimento), detalhe: 'quantidade atendida ÷ solicitada (concluídas)', icone: 'pi pi-percentage', tom: (ind.atendimento ?? 100) < 95 ? 'warning' : 'success' }],
        graficosExtras: top.length
          ? [{
              id: 'materiais-atendimento',
              titulo: 'Atendimento dos materiais mais pedidos',
              descricao: 'Quantidade solicitada × atendida no período.',
              tipo: 'bar-horizontal',
              rotulos: top.map((m) => `${m.codigo} · ${m.descricao}`.slice(0, 48)),
              series: [
                { name: 'Solicitada', values: top.map((m) => m.quantidadeSolicitada) },
                { name: 'Atendida', values: top.map((m) => m.quantidadeAtendida) }
              ],
              formato: 'number',
              altura: Math.max(220, top.length * 34 + 60),
              inteiro: true
            }]
          : [],
        nomeArquivo: 'solicitacoes-atendimento-parcial'
      });
    }
    case 'fora-prazo':
      return base({
        eyebrow: 'Indicador',
        titulo: 'Prazo curto de saque',
        descricao: 'Pedidos com retirada prevista para menos de 2 dias após a abertura',
        icone: 'pi pi-calendar-times',
        linhas: r.periodo.filter((l) => l.foraPrazoSaque),
        nomeArquivo: 'solicitacoes-fora-prazo'
      });
    default:
      return null;
  }
}

function destaquesDoSubconjunto(linhas: readonly SolicitacaoDoPainel[], c: ContextoAnalise): DestaqueAnalise[] {
  const destaques: DestaqueAnalise[] = [];
  const abertas = linhas.filter((l) => situacao(l) === 'aberta');
  const atrasadas = abertas.filter((l) => atrasada(l, c.r.hoje));
  if (atrasadas.length) {
    destaques.push({ tom: 'danger', icone: 'pi pi-exclamation-triangle', texto: `${plural(atrasadas.length, 'solicitação atrasada', 'solicitações atrasadas')} — a pior há ${plural(Math.max(...atrasadas.map((l) => diasDeAtraso(l, c.r.hoje))), 'dia', 'dias')} da data prevista.` });
  }
  const fluxo = fluxoDasAbertas(abertas, c.modo, c.r.hoje, c.r.agora).filter((e) => e.total > 0).sort((a, b) => b.total - a.total)[0];
  if (fluxo) destaques.push({ tom: 'info', icone: fluxo.icone, texto: `Maior parte das abertas em "${fluxo.rotulo.toLowerCase()}" (${percentual(fluxo.fatia)}).` });
  const ind = indicadores(linhas.filter((l) => !l.anteriorAoPeriodo));
  const geral = indicadores(c.r.periodo);
  if (ind.cicloMediana !== null && geral.cicloMediana !== null && ind.total !== geral.total) {
    const pior = ind.cicloMediana > geral.cicloMediana;
    destaques.push({ tom: pior ? 'warning' : 'success', icone: pior ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right', texto: `Ciclo mediano de ${tempo(ind.cicloMediana)}, ${pior ? 'acima' : 'abaixo'} dos ${tempo(geral.cicloMediana)} do recorte todo.` });
  }
  if (ind.comPendencia) destaques.push({ tom: 'warning', icone: 'pi pi-flag', texto: `${plural(ind.comPendencia, 'solicitação com pendência registrada', 'solicitações com pendência registrada')}.` });
  return destaques;
}

function graficoPorDia(linhas: readonly SolicitacaoDoPainel[], c: ContextoAnalise): GraficoAnalise {
  const s = serieDiaria(linhas, c.inicio, c.fim);
  return {
    id: 'por-dia',
    titulo: 'Criadas por dia e situação atual',
    descricao: 'Clique num dia para abrir as solicitações dele.',
    tipo: 'bar-stacked',
    rotulos: s.rotulos,
    series: [
      { name: 'Concluídas', values: s.concluidas },
      { name: 'Em aberto', values: s.abertas },
      { name: 'Recusadas', values: s.recusadas }
    ],
    formato: 'integer',
    altura: 240,
    inteiro: true,
    aoSelecionar: (i) => navegar('dia', s.dias[i]),
    rotuloSelecao: 'Analisar dia'
  };
}

function graficoIdade(linhas: readonly SolicitacaoDoPainel[], c: ContextoAnalise): GraficoAnalise {
  const abertas = linhas.filter((l) => situacao(l) === 'aberta');
  const faixas = FAIXAS_IDADE.map((f) => abertas.filter((l) => idadeDias(l, c.r.agora) >= f.de && idadeDias(l, c.r.agora) < f.ate));
  return {
    id: 'idade',
    titulo: 'Há quanto tempo estão abertas',
    descricao: 'Clique numa faixa para filtrar.',
    tipo: 'bar',
    rotulos: FAIXAS_IDADE.map((f) => f.rotulo),
    series: [
      { name: 'No prazo', values: faixas.map((f) => f.filter((l) => !atrasada(l, c.r.hoje)).length) },
      { name: 'Atrasadas', values: faixas.map((f) => f.filter((l) => atrasada(l, c.r.hoje)).length) }
    ],
    formato: 'integer',
    altura: 220,
    aoSelecionar: (i) => ({
      tipo: 'filtrar',
      filtro: {
        chave: `idade-${FAIXAS_IDADE[i].id}`,
        rotulo: `Abertas há ${FAIXAS_IDADE[i].rotulo.toLowerCase()}`,
        aceita: (l: SolicitacaoDoPainel) => situacao(l) === 'aberta' && idadeDias(l, c.r.agora) >= FAIXAS_IDADE[i].de && idadeDias(l, c.r.agora) < FAIXAS_IDADE[i].ate
      }
    }),
    rotuloSelecao: 'Ver solicitações da faixa'
  };
}
