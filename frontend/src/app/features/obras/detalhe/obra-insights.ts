import { CesTone } from '@ces/ui';

import { ObraResponse, OrcamentoObraResponse } from '../../../core/models/obras.models';
import { formatarMoeda, parseDataBrParaDate } from '../obras-ui';

const DIA_MS = 86_400_000;
const PRAZO_CURTO_DIAS = 15;
const INICIO_PROXIMO_DIAS = 15;
const STATUS_PARADO_DIAS = 30;
const DESLOCAMENTO_LONGO_MIN = 90;
const DIVERGENCIA_RELEVANTE = 0.1;

/** O que a tela faz ao acionar o insight. */
export type AcaoInsightObra = 'carteira' | 'cadastro' | 'anexo' | 'solicitar' | 'status';

/** Um ponto de atenção da obra: tom e ícone (nunca só cor) e, quando há, a ação que resolve. */
export interface InsightObra {
  tom: CesTone;
  icone: string;
  titulo: string;
  texto: string;
  acao?: { tipo: AcaoInsightObra; rotulo: string };
}

/** Dados que chegam em cargas separadas: `null` = ainda não carregado, e o insight dependente não aparece. */
export interface ContextoInsightsObra {
  obra: ObraResponse;
  hoje: Date;
  orcamento: OrcamentoObraResponse | null;
  totalSolicitacoes: number | null;
  totalProximas: number | null;
  raioKm: number;
  dataUltimoStatus: string | null;
}

const dias = (n: number) => `${n} ${n === 1 ? 'dia' : 'dias'}`;

function diferencaDias(de: Date, ate: Date): number {
  return Math.round((ate.getTime() - de.getTime()) / DIA_MS);
}

/** Pontos de atenção da obra, do mais grave ao positivo. Obra concluída só mantém o que ainda importa. */
export function insightsDaObra(ctx: ContextoInsightsObra): InsightObra[] {
  const { obra, orcamento, totalSolicitacoes, totalProximas, raioKm } = ctx;
  const carteira = obra.carteira ?? null;
  const hoje = new Date(ctx.hoje);
  hoje.setHours(0, 0, 0, 0);
  const inicio = parseDataBrParaDate(obra.dataInicio);
  const previsao = parseDataBrParaDate(obra.dataPrevisaoFim);
  const regulado = parseDataBrParaDate(carteira?.prazoRegulado);
  const concluida = obra.statusConcluida || !!obra.dataFimEfetiva;
  const semEquipe = !obra.equipes?.length;

  const alertas: InsightObra[] = [];
  const avisos: InsightObra[] = [];
  const informativos: InsightObra[] = [];
  const positivos: InsightObra[] = [];

  // Orçamento e valores valem mesmo depois de concluída: explicam o resultado da obra.
  const itensSemAcao = orcamento?.itens.filter((i) => !i.idAcao).length ?? 0;
  if (itensSemAcao) {
    avisos.push({
      tom: 'warning',
      icone: 'pi pi-link',
      titulo: `${itensSemAcao} ${itensSemAcao === 1 ? 'serviço' : 'serviços'} fora do contrato`,
      texto: 'Estão no orçamento importado, mas o código não bate com nenhuma Ação e não terão valor pelo contrato.'
    });
  }
  const servicoPrevisto = carteira?.valorServicoPrevisto ?? null;
  if (orcamento?.importado && servicoPrevisto) {
    const total = orcamento.totalServicosImportados;
    if (Math.abs(total - servicoPrevisto) / servicoPrevisto >= DIVERGENCIA_RELEVANTE) {
      const percentual = Math.round((total / servicoPrevisto) * 100);
      avisos.push({
        tom: 'warning',
        icone: 'pi pi-calculator',
        titulo: `Orçamento cobre ${percentual}% do serviço previsto`,
        texto: `Importado ${formatarMoeda(total)} para ${formatarMoeda(servicoPrevisto)} previstos na carteira. Confira se a planilha veio completa.`
      });
    } else {
      positivos.push({
        tom: 'success',
        icone: 'pi pi-check-circle',
        titulo: 'Orçamento coerente com a carteira',
        texto: `Os serviços importados somam ${formatarMoeda(total)}, próximo dos ${formatarMoeda(servicoPrevisto)} previstos.`
      });
    }
  }
  const material = carteira?.valorMaterialPrevisto ?? null;
  if (obra.valorProjeto && material != null && servicoPrevisto != null) {
    const soma = material + servicoPrevisto;
    if (Math.abs(soma - obra.valorProjeto) / obra.valorProjeto >= DIVERGENCIA_RELEVANTE) {
      avisos.push({
        tom: 'warning',
        icone: 'pi pi-wallet',
        titulo: soma > obra.valorProjeto ? 'Previsto acima do valor de projeto' : 'Previsto abaixo do valor de projeto',
        texto: `Material e serviço somam ${formatarMoeda(soma)}; o valor de projeto é ${formatarMoeda(obra.valorProjeto)}.`,
        acao: { tipo: 'carteira', rotulo: 'Revisar valores' }
      });
    }
  }

  if (concluida) {
    return [...alertas, ...avisos, ...informativos, ...positivos];
  }

  // Prazo
  const limite = regulado && previsao ? (regulado < previsao ? regulado : previsao) : (regulado ?? previsao);
  if (limite) {
    const restante = diferencaDias(hoje, limite);
    const nome = limite === regulado ? 'prazo regulado' : 'previsão de fim';
    if (restante < 0) {
      alertas.push({
        tom: 'danger',
        icone: 'pi pi-exclamation-circle',
        titulo: `${dias(-restante)} de atraso`,
        texto: `A ${nome} já passou e a obra não foi concluída. Atualize o status ou renegocie o prazo.`,
        acao: { tipo: 'status', rotulo: 'Registrar status' }
      });
    } else if (restante <= PRAZO_CURTO_DIAS) {
      avisos.push({
        tom: 'warning',
        icone: 'pi pi-clock',
        titulo: restante === 0 ? 'O prazo vence hoje' : `Prazo vence em ${dias(restante)}`,
        texto: `Faltam poucos dias para a ${nome}.`
      });
    }
  }
  if (regulado && previsao && previsao > regulado) {
    const excesso = diferencaDias(regulado, previsao);
    avisos.push({
      tom: 'warning',
      icone: 'pi pi-calendar-times',
      titulo: 'Previsão depois do prazo regulado',
      texto: `A previsão de fim passa ${dias(excesso)} do prazo regulado (${carteira!.prazoRegulado}).`,
      acao: { tipo: 'cadastro', rotulo: 'Ajustar previsão' }
    });
  }

  // Equipe e materiais, olhando o início
  const faltamInicio = inicio ? diferencaDias(hoje, inicio) : null;
  const iniciouOuProximo = faltamInicio !== null && faltamInicio <= INICIO_PROXIMO_DIAS;
  const quando = faltamInicio === null ? '' : faltamInicio > 0 ? `O início é em ${dias(faltamInicio)}` : faltamInicio === 0 ? 'O início é hoje' : 'A obra já começou';
  if (semEquipe && (iniciouOuProximo || carteira?.prioridade === 'URGENTE')) {
    const urgente = carteira?.prioridade === 'URGENTE';
    (urgente ? alertas : avisos).push({
      tom: urgente ? 'danger' : 'warning',
      icone: 'pi pi-users',
      titulo: urgente ? 'Obra urgente sem equipe' : 'Sem equipe alocada',
      texto: iniciouOuProximo ? `${quando} e nenhuma equipe está vinculada.` : 'A prioridade é urgente e nenhuma equipe está vinculada.',
      acao: { tipo: 'cadastro', rotulo: 'Alocar equipe' }
    });
  }
  if (totalSolicitacoes === 0 && iniciouOuProximo) {
    avisos.push({
      tom: 'warning',
      icone: 'pi pi-cart-plus',
      titulo: 'Nenhum material solicitado',
      texto: `${quando} e não há solicitação de material para a obra.`,
      acao: { tipo: 'solicitar', rotulo: 'Solicitar materiais' }
    });
  }

  // Dados que travam as próximas etapas
  if (!carteira?.classeTensao) {
    avisos.push({
      tom: 'warning',
      icone: 'pi pi-bolt',
      titulo: 'Classe de tensão não informada',
      texto: 'Sem ela, os kits do planejamento não são filtrados pela tensão da obra.',
      acao: { tipo: 'carteira', rotulo: 'Informar tensão' }
    });
  }
  if (!carteira?.idContrato) {
    avisos.push({
      tom: 'warning',
      icone: 'pi pi-briefcase',
      titulo: 'Obra sem contrato',
      texto: 'O contrato define os kits disponíveis e os valores dos serviços.',
      acao: { tipo: 'carteira', rotulo: 'Informar contrato' }
    });
  }
  if (!obra.anexos?.length) {
    informativos.push({
      tom: 'info',
      icone: 'pi pi-paperclip',
      titulo: 'Nenhum anexo na obra',
      texto: 'O projeto em PDF é a base do planejamento dos pontos e kits.',
      acao: { tipo: 'anexo', rotulo: 'Adicionar anexo' }
    });
  }
  if (orcamento && !orcamento.importado) {
    informativos.push({
      tom: 'info',
      icone: 'pi pi-calculator',
      titulo: 'Sem orçamento importado',
      texto: 'Sem os serviços previstos, não há com o que comparar o planejamento.'
    });
  }

  // Localização
  if (obra.latitude == null || obra.longitude == null) {
    informativos.push({
      tom: 'info',
      icone: 'pi pi-map-marker',
      titulo: 'Sem coordenadas',
      texto: 'Sem elas não há mapa, deslocamento da base nem obras próximas.',
      acao: { tipo: 'cadastro', rotulo: 'Informar coordenadas' }
    });
  } else {
    const tempo = carteira?.tempoDeslocamentoMin;
    if (tempo != null && tempo >= DESLOCAMENTO_LONGO_MIN) {
      informativos.push({
        tom: 'info',
        icone: 'pi pi-car',
        titulo: `${tempo} min de deslocamento da base`,
        texto: `São cerca de ${Math.round((tempo * 2) / 6) / 10} h por dia de ida e volta, descontadas da jornada da equipe.`
      });
    }
    if (carteira?.deslocamentoEstimado) {
      informativos.push({
        tom: 'info',
        icone: 'pi pi-compass',
        titulo: 'Deslocamento estimado',
        texto: 'Calculado em linha reta com fator de sinuosidade: o provedor de rotas não respondeu.'
      });
    }
    if (totalProximas) {
      positivos.push({
        tom: 'success',
        icone: 'pi pi-th-large',
        titulo: `${totalProximas} ${totalProximas === 1 ? 'obra próxima' : 'obras próximas'}`,
        texto: `Em até ${raioKm} km: dá para agrupar a programação e reduzir o deslocamento.`
      });
    }
  }

  // Status parado
  if (ctx.dataUltimoStatus) {
    const parado = diferencaDias(new Date(ctx.dataUltimoStatus), hoje);
    if (parado >= STATUS_PARADO_DIAS) {
      informativos.push({
        tom: 'info',
        icone: 'pi pi-history',
        titulo: `Status sem atualização há ${dias(parado)}`,
        texto: `A obra segue em "${obra.statusNome}". Vale confirmar a situação.`,
        acao: { tipo: 'status', rotulo: 'Registrar status' }
      });
    }
  }

  return [...alertas, ...avisos, ...informativos, ...positivos];
}
