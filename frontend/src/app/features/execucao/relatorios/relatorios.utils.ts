import { CesTone } from '@ces/ui';
import {
  ContextoExecucaoResponse,
  HistoricoEquipeTurno,
  IndicadoresTurno,
  LancamentoDoTurno
} from '../../../core/models/dashboard.models';
import { ExecucaoComercialResponse, TIPO_SERVICO_NAO_INFORMADO } from '../../../core/models/execucao.models';
import { duracao, horaCurta, moeda, percentual } from '../dashboards/execucao-dashboards.utils';

/** Um destaque do relatório: o que o gestor precisa notar, com tom e ícone (nunca só cor). */
export interface Insight {
  tom: CesTone;
  icone: string;
  titulo: string;
  texto: string;
}

/** A partir de quantos minutos sem lançar um trecho do turno merece atenção. */
export const INTERVALO_LONGO_MINUTOS = 120;
/** Tempo até a primeira nota acima do qual a saída para campo é considerada lenta. */
export const PRIMEIRA_NOTA_LENTA_MINUTOS = 90;
/** Tempo entre a última nota e o fechamento acima do qual o turno ficou ocioso no fim. */
export const FIM_OCIOSO_MINUTOS = 90;
/** Diferença, em relação à média da equipe, que já vale um destaque. */
const VARIACAO_RELEVANTE = 15;

/** Variação percentual de `atual` sobre `referencia`; nula sem referência. */
export function variacaoPercentual(atual: number, referencia: number | null | undefined): number | null {
  if (referencia === null || referencia === undefined || referencia <= 0) return null;
  return ((atual - referencia) / referencia) * 100;
}

function variacaoTexto(variacao: number): string {
  const absoluta = Math.abs(variacao).toLocaleString('pt-BR', { maximumFractionDigits: 0 });
  return `${absoluta}% ${variacao >= 0 ? 'acima' : 'abaixo'}`;
}

/** Destaques do relatório de turno, do mais grave ao informativo. */
export function insightsDoTurno(ind: IndicadoresTurno, historico: HistoricoEquipeTurno | null): Insight[] {
  const alertas: Insight[] = [];
  const positivos: Insight[] = [];
  const informativos: Insight[] = [];

  if (ind.abertoDesdeOutroDia) {
    alertas.push({
      tom: 'danger',
      icone: 'pi pi-exclamation-circle',
      titulo: 'Turno aberto desde outro dia',
      texto: `Está aberto há ${duracao(ind.duracaoMinutos)}. Provavelmente a equipe esqueceu de fechar; confira com o encarregado.`
    });
  }

  if (ind.notas === 0) {
    alertas.push({
      tom: ind.emAndamento ? 'warning' : 'danger',
      icone: 'pi pi-ban',
      titulo: 'Nenhuma nota lançada',
      texto: ind.emAndamento
        ? `O turno está aberto há ${duracao(ind.duracaoMinutos)} sem nenhuma nota concluída.`
        : `O turno durou ${duracao(ind.duracaoMinutos)} e não teve nenhuma nota concluída.`
    });
  }

  if (ind.percentualMetaDiaria !== null) {
    if (ind.percentualMetaDiaria >= 100) {
      positivos.push({
        tom: 'success',
        icone: 'pi pi-flag-fill',
        titulo: 'Meta do dia atingida',
        texto: `${percentual(ind.percentualMetaDiaria)} da meta diária de ${moeda(ind.metaDiaria)}.`
      });
    } else if (!ind.emAndamento) {
      const falta = (ind.metaDiaria ?? 0) - ind.producao;
      alertas.push({
        tom: ind.percentualMetaDiaria >= 80 ? 'warning' : 'danger',
        icone: 'pi pi-flag',
        titulo: 'Meta do dia não atingida',
        texto: `Ficou em ${percentual(ind.percentualMetaDiaria)}; faltaram ${moeda(falta)} para a meta de ${moeda(ind.metaDiaria)}.`
      });
    }
  }

  if (historico && historico.turnos > 0 && historico.mediaProducaoPorTurno !== null && !ind.emAndamento) {
    const variacao = variacaoPercentual(ind.producao, historico.mediaProducaoPorTurno);
    if (variacao !== null && Math.abs(variacao) >= VARIACAO_RELEVANTE) {
      (variacao > 0 ? positivos : alertas).push({
        tom: variacao > 0 ? 'success' : 'warning',
        icone: variacao > 0 ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right',
        titulo: `Produção ${variacaoTexto(variacao)} da média da equipe`,
        texto: `A média dos últimos ${historico.turnos} turnos (${historico.dias} dias) é ${moeda(historico.mediaProducaoPorTurno)} por turno.`
      });
    }
  }

  if (ind.maiorIntervaloSemLancamentoMinutos !== null && ind.maiorIntervaloSemLancamentoMinutos >= INTERVALO_LONGO_MINUTOS) {
    alertas.push({
      tom: 'warning',
      icone: 'pi pi-hourglass',
      titulo: `${duracao(ind.maiorIntervaloSemLancamentoMinutos)} sem lançar`,
      texto: 'Maior trecho do turno sem nenhuma nota concluída. Vale entender o deslocamento ou a pendência do período.'
    });
  }

  if (ind.minutosAtePrimeiroLancamento !== null && ind.minutosAtePrimeiroLancamento >= PRIMEIRA_NOTA_LENTA_MINUTOS) {
    alertas.push({
      tom: 'warning',
      icone: 'pi pi-stopwatch',
      titulo: 'Primeira nota demorou',
      texto: `A primeira nota saiu ${duracao(ind.minutosAtePrimeiroLancamento)} depois da abertura do turno.`
    });
  }

  if (!ind.emAndamento && ind.minutosAposUltimoLancamento !== null && ind.minutosAposUltimoLancamento >= FIM_OCIOSO_MINUTOS) {
    alertas.push({
      tom: 'warning',
      icone: 'pi pi-sign-out',
      titulo: 'Fim de turno sem produção',
      texto: `O turno foi fechado ${duracao(ind.minutosAposUltimoLancamento)} depois da última nota.`
    });
  }

  if (ind.lancamentosSemLocalizacao > 0) {
    alertas.push({
      tom: 'warning',
      icone: 'pi pi-map-marker',
      titulo: `${ind.lancamentosSemLocalizacao} ${ind.lancamentosSemLocalizacao === 1 ? 'nota sem localização' : 'notas sem localização'}`,
      texto: 'Lançamentos sem GPS não aparecem no mapa e não permitem conferir onde o serviço foi feito.'
    });
  }

  if (ind.producaoPorHora !== null && ind.notas > 0) {
    informativos.push({
      tom: 'info',
      icone: 'pi pi-bolt',
      titulo: `${moeda(ind.producaoPorHora)} por hora de turno`,
      texto: `${ind.notas} ${ind.notas === 1 ? 'nota' : 'notas'} com valor médio de ${moeda(ind.valorMedioNota)}.`
    });
  }

  return [...alertas.sort(porGravidade), ...positivos, ...informativos];
}

const GRAVIDADE: Record<CesTone, number> = {
  danger: 0,
  warning: 1,
  info: 2,
  success: 3,
  brand: 4,
  neutral: 5
};

function porGravidade(a: Insight, b: Insight): number {
  return GRAVIDADE[a.tom] - GRAVIDADE[b.tom];
}

/** Posição de uma nota entre as notas do turno, com a anterior e a seguinte. */
export interface PosicaoNoTurno {
  posicao: number;
  total: number;
  anterior: LancamentoDoTurno | null;
  proxima: LancamentoDoTurno | null;
  minutosDesdeAnterior: number | null;
  distanciaDaAnteriorKm: number | null;
}

export function posicaoNoTurno(lancamentos: readonly LancamentoDoTurno[], idExecucao: string): PosicaoNoTurno | null {
  const indice = lancamentos.findIndex((l) => l.idExecucao === idExecucao);
  if (indice < 0) return null;
  const atual = lancamentos[indice];
  const anterior = indice > 0 ? lancamentos[indice - 1] : null;
  return {
    posicao: indice + 1,
    total: lancamentos.length,
    anterior,
    proxima: lancamentos[indice + 1] ?? null,
    minutosDesdeAnterior: anterior ? minutosEntre(anterior.dataLancamento, atual.dataLancamento) : null,
    distanciaDaAnteriorKm: anterior ? distanciaKm(anterior, atual) : null
  };
}

/** Destaques do relatório de uma nota. */
export function insightsDaExecucao(
  execucao: ExecucaoComercialResponse,
  contexto: ContextoExecucaoResponse | null
): Insight[] {
  const insights: Insight[] = [];

  if (execucao.statusExecucao === 'EDITADO') {
    insights.push({
      tom: 'info',
      icone: 'pi pi-pencil',
      titulo: 'Nota editada depois do lançamento',
      texto: 'Notas editadas não entram na produção dos dashboards. Confira o histórico de alterações.'
    });
  }

  if (execucao.latitude == null || execucao.longitude == null) {
    insights.push({
      tom: 'warning',
      icone: 'pi pi-map-marker',
      titulo: 'Lançada sem localização',
      texto: 'Não é possível conferir no mapa onde o serviço foi feito.'
    });
  }

  if (!execucao.acoes?.length) {
    insights.push({
      tom: 'warning',
      icone: 'pi pi-list',
      titulo: 'Nota sem ações',
      texto: 'Sem ações a nota não gera faturamento.'
    });
  } else if (execucao.valorTotalFaturado === 0) {
    insights.push({
      tom: 'warning',
      icone: 'pi pi-wallet',
      titulo: 'Nota sem valor faturado',
      texto: 'As ações não tinham valor vigente no contrato da equipe na data do lançamento.'
    });
  }

  if (!contexto) return insights;

  const media = contexto.referenciaEquipe?.valorMedioNota ?? null;
  const variacao = variacaoPercentual(execucao.valorTotalFaturado, media);
  if (variacao !== null && Math.abs(variacao) >= VARIACAO_RELEVANTE * 2) {
    insights.push({
      tom: variacao > 0 ? 'success' : 'info',
      icone: variacao > 0 ? 'pi pi-arrow-up-right' : 'pi pi-arrow-down-right',
      titulo: `Valor ${variacaoTexto(variacao)} da média da equipe`,
      texto: `A nota média da equipe nos ${contexto.referenciaEquipe!.dias} dias anteriores foi ${moeda(media)} (${contexto.referenciaEquipe!.notas} notas).`
    });
  }

  if (contexto.turno) {
    const lancamento = new Date(execucao.dataLancamento).getTime();
    const inicio = new Date(contexto.turno.dataInicio).getTime();
    const fim = contexto.turno.dataFim ? new Date(contexto.turno.dataFim).getTime() : null;
    if (lancamento < inicio || (fim !== null && lancamento > fim)) {
      insights.push({
        tom: 'warning',
        icone: 'pi pi-clock',
        titulo: 'Lançada fora do horário do turno',
        texto: `O turno foi de ${horaCurta(contexto.turno.dataInicio)} a ${contexto.turno.dataFim ? horaCurta(contexto.turno.dataFim) : 'agora'}. O lançamento pode ter sido sincronizado com atraso.`
      });
    }
  }

  const posicao = posicaoNoTurno(contexto.lancamentosDoTurno, execucao.id);
  if (posicao?.minutosDesdeAnterior !== null && posicao?.minutosDesdeAnterior !== undefined && posicao.minutosDesdeAnterior >= INTERVALO_LONGO_MINUTOS) {
    insights.push({
      tom: 'info',
      icone: 'pi pi-hourglass',
      titulo: `${duracao(posicao.minutosDesdeAnterior)} desde a nota anterior`,
      texto: posicao.distanciaDaAnteriorKm !== null
        ? `A nota anterior foi a ${distanciaTexto(posicao.distanciaDaAnteriorKm)} daqui.`
        : 'Intervalo longo entre notas do mesmo turno.'
    });
  }

  return insights.sort(porGravidade);
}

/** Notas de um tipo de serviço, com a produção das concluídas e a fatia das notas. */
export interface TipoServicoDasNotas {
  /** `null` agrupa as notas sem tipo informado. */
  id: string | null;
  codigo: string | null;
  descricao: string;
  notas: number;
  producao: number;
  percentualNotas: number;
}

/** Distribui as notas pelos tipos de serviço: mais notas primeiro, "não informado" por último. */
export function tiposServicoDasNotas(execucoes: readonly ExecucaoComercialResponse[]): TipoServicoDasNotas[] {
  const porTipo = new Map<string | null, TipoServicoDasNotas>();
  for (const execucao of execucoes) {
    const tipo = execucao.tipoServico ?? null;
    const chave = tipo?.id ?? null;
    const atual = porTipo.get(chave) ?? {
      id: chave,
      codigo: tipo?.codigo ?? null,
      descricao: tipo?.descricao ?? TIPO_SERVICO_NAO_INFORMADO,
      notas: 0,
      producao: 0,
      percentualNotas: 0
    };
    atual.notas++;
    if (execucao.statusExecucao === 'CONCLUIDO') atual.producao += Number(execucao.valorTotalFaturado) || 0;
    porTipo.set(chave, atual);
  }
  const total = execucoes.length || 1;
  return [...porTipo.values()]
    .map((t) => ({ ...t, producao: Math.round(t.producao * 100) / 100, percentualNotas: (t.notas / total) * 100 }))
    .sort(
      (a, b) =>
        Number(a.id === null) - Number(b.id === null) ||
        b.notas - a.notas ||
        b.producao - a.producao ||
        a.descricao.localeCompare(b.descricao, 'pt-BR')
    );
}

/** Ações mais frequentes nas notas, com a fatia das notas em que aparecem. */
export interface AcaoFrequente {
  codigo: string;
  nome: string;
  quantidade: number;
  percentualNotas: number;
}

export function acoesMaisExecutadas(execucoes: readonly ExecucaoComercialResponse[]): AcaoFrequente[] {
  const contagem = new Map<string, AcaoFrequente>();
  for (const execucao of execucoes) {
    for (const item of execucao.acoes ?? []) {
      if (!item.acao) continue;
      const atual = contagem.get(item.acao.id) ?? {
        codigo: item.acao.codigo,
        nome: item.acao.nome,
        quantidade: 0,
        percentualNotas: 0
      };
      atual.quantidade++;
      contagem.set(item.acao.id, atual);
    }
  }
  const total = execucoes.length || 1;
  return [...contagem.values()]
    .map((a) => ({ ...a, percentualNotas: (a.quantidade / total) * 100 }))
    .sort((a, b) => b.quantidade - a.quantidade || a.codigo.localeCompare(b.codigo, 'pt-BR'));
}

/** Materiais movimentados nas notas, somando aplicado e retirado por material. */
export interface MaterialMovimentado {
  codigo: string;
  descricao: string;
  unidade: string;
  aplicado: number;
  retirado: number;
}

export function materiaisMovimentados(execucoes: readonly ExecucaoComercialResponse[]): MaterialMovimentado[] {
  const porMaterial = new Map<string, MaterialMovimentado>();
  for (const execucao of execucoes) {
    for (const item of execucao.materiais ?? []) {
      const material = item.material;
      if (!material) continue;
      const atual = porMaterial.get(material.id) ?? {
        codigo: material.codigo,
        descricao: material.descricao,
        unidade: material.unidadeMedida ?? '',
        aplicado: 0,
        retirado: 0
      };
      if (item.operacao === 'APLICADO') atual.aplicado += item.quantidade;
      else atual.retirado += item.quantidade;
      porMaterial.set(material.id, atual);
    }
  }
  return [...porMaterial.values()].sort((a, b) => a.descricao.localeCompare(b.descricao, 'pt-BR'));
}

/** Notas do turno em ordem, com o intervalo desde a anterior (ou desde a abertura). */
export function notasComIntervalo<T extends { dataLancamento: string }>(
  notas: readonly T[],
  abertura: string
): (T & { minutosDesdeAnterior: number })[] {
  const ordenadas = [...notas].sort(
    (a, b) => new Date(a.dataLancamento).getTime() - new Date(b.dataLancamento).getTime()
  );
  return ordenadas.map((nota, i) => ({
    ...nota,
    minutosDesdeAnterior: minutosEntre(i === 0 ? abertura : ordenadas[i - 1].dataLancamento, nota.dataLancamento)
  }));
}

/**
 * Produção acumulada ao longo do turno, um ponto por nota concluída, e a meta do dia
 * como linha de referência (mesma escala, mesmo eixo).
 */
export function serieDoTurno(
  notas: readonly { dataLancamento: string; valorTotalFaturado: number; statusExecucao: string }[],
  abertura: string,
  metaDiaria: number | null
): { rotulos: string[]; series: { name: string; values: number[] }[] } {
  const concluidas = notas
    .filter((n) => n.statusExecucao === 'CONCLUIDO')
    .sort((a, b) => new Date(a.dataLancamento).getTime() - new Date(b.dataLancamento).getTime());
  const rotulos = [`Abertura ${horaCurta(abertura)}`];
  const acumulado = [0];
  let soma = 0;
  for (const nota of concluidas) {
    soma += nota.valorTotalFaturado ?? 0;
    rotulos.push(horaCurta(nota.dataLancamento));
    acumulado.push(Math.round(soma * 100) / 100);
  }
  return {
    rotulos,
    series: [
      { name: 'Produção acumulada', values: acumulado },
      ...(metaDiaria !== null ? [{ name: 'Meta do dia', values: rotulos.map(() => metaDiaria) }] : [])
    ]
  };
}

export function minutosEntre(de: string, ate: string): number {
  return Math.max(0, Math.round((new Date(ate).getTime() - new Date(de).getTime()) / 60000));
}

/** Distância em linha reta (haversine), em km; nula sem coordenadas. */
export function distanciaKm(
  a: { latitude?: number | null; longitude?: number | null },
  b: { latitude?: number | null; longitude?: number | null }
): number | null {
  if (a.latitude == null || a.longitude == null || b.latitude == null || b.longitude == null) return null;
  const raio = 6371;
  const rad = (graus: number) => (graus * Math.PI) / 180;
  const dLat = rad(b.latitude - a.latitude);
  const dLon = rad(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2;
  return 2 * raio * Math.asin(Math.sqrt(h));
}

export function distanciaTexto(km: number | null): string {
  if (km === null) return '—';
  if (km < 1) return `${Math.round(km * 1000)} m`;
  return `${km.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} km`;
}

/** "3ª" */
export function ordinal(n: number): string {
  return `${n}ª`;
}
