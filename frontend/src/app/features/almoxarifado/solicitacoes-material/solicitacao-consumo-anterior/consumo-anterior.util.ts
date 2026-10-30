import { CesTone } from '@ces/ui';

import { ConsumoAnteriorItem, ConsumoAnteriorMovimentacao } from '../../../../core/models/almoxarifado.models';

/** Abaixo disso de uso do que pegou, com saldo sobrando, o novo pedido merece um olhar. */
export const APROVEITAMENTO_BAIXO = 0.5;
/** A partir daqui a equipe usou praticamente tudo o que pegou. */
export const APROVEITAMENTO_ALTO = 0.8;

export interface LeituraConsumo {
  /** Aplicado ÷ recebido na última solicitação (null quando não houve). */
  aproveitamento: number | null;
  tom: CesTone;
  /** Frase curta para quem aprova. */
  dica: string;
}

/**
 * Lê o consumo de um material desde a última solicitação: a IA não decide, só aponta onde o
 * pedido destoa do uso (pegou muito, usou pouco e ainda tem saldo).
 */
export function lerConsumo(item: ConsumoAnteriorItem | null | undefined): LeituraConsumo {
  const ultima = item?.ultimaSolicitacao;
  if (!item || !ultima) return { aproveitamento: null, tom: 'neutral', dica: 'Primeiro pedido deste material para o estoque.' };
  const recebida = Number(ultima.quantidadeRecebida) || 0;
  const aplicada = Number(item.quantidadeAplicada) || 0;
  const saldo = Number(item.saldoAtual) || 0;
  const aproveitamento = recebida > 0 ? aplicada / recebida : null;
  const pct = aproveitamento == null ? null : Math.round(aproveitamento * 100);
  if (aproveitamento != null && aproveitamento < APROVEITAMENTO_BAIXO && saldo > 0) {
    return { aproveitamento, tom: 'warning', dica: `Usou ${pct}% do que pegou e ainda tem saldo.` };
  }
  // Gastar mais do que pegou não é mérito: o restante saiu do saldo que a equipe já tinha.
  if (aproveitamento != null && aproveitamento > 1) {
    return { aproveitamento, tom: 'info', dica: 'Gastou mais do que pegou: o restante saiu do saldo que já tinha.' };
  }
  if (aproveitamento != null && aproveitamento >= APROVEITAMENTO_ALTO) {
    return { aproveitamento, tom: 'success', dica: `Usou ${pct}% do que pegou.` };
  }
  return { aproveitamento, tom: 'neutral', dica: pct == null ? '' : `Usou ${pct}% do que pegou.` };
}

/** Seções do detalhamento, na ordem em que aparecem. */
export type GrupoConsumo = 'recebido' | 'aplicado' | 'devolvido' | 'transferido' | 'avulso' | 'sucata';

/** Movimentações da mesma operação viram uma linha só, com as séries juntas. */
export interface LinhaOperacaoConsumo {
  readonly chave: string;
  readonly dataHora: string;
  /** Positiva quando entrou no estoque, negativa quando saiu. */
  readonly quantidade: number;
  readonly titulo: string;
  readonly detalhe: string | null;
  /** Comandos do router para abrir a origem (solicitação, nota ou movimentação). */
  readonly link: readonly string[] | null;
  readonly series: readonly string[];
}

export interface SecaoOperacoesConsumo {
  readonly grupo: GrupoConsumo;
  readonly titulo: string;
  readonly icone: string;
  readonly total: number;
  /** Sucata não muda o saldo novo, que é o que a conta acompanha. */
  readonly foraDaConta: boolean;
  readonly linhas: readonly LinhaOperacaoConsumo[];
}

const SECOES: Record<GrupoConsumo, { titulo: string; icone: string }> = {
  recebido: { titulo: 'Recebido em solicitações', icone: 'pi pi-box' },
  aplicado: { titulo: 'Aplicado em serviços', icone: 'pi pi-wrench' },
  devolvido: { titulo: 'Devolvido', icone: 'pi pi-undo' },
  transferido: { titulo: 'Transferências', icone: 'pi pi-arrow-right-arrow-left' },
  avulso: { titulo: 'Entradas e baixas avulsas', icone: 'pi pi-pencil' },
  sucata: { titulo: 'Sucata e retiradas', icone: 'pi pi-trash' }
};

const ORDEM: readonly GrupoConsumo[] = ['recebido', 'aplicado', 'devolvido', 'transferido', 'avulso', 'sucata'];

function grupoDe(m: ConsumoAnteriorMovimentacao): GrupoConsumo {
  if (m.condicao && m.condicao !== 'NOVO') return 'sucata';
  switch (m.tipo) {
    case 'ENTREGA':
    case 'ESTORNO':
      return 'recebido';
    case 'APLICACAO':
      return 'aplicado';
    case 'DEVOLUCAO':
      return 'devolvido';
    case 'TRANSFERENCIA':
      return 'transferido';
    case 'RETIRADA':
      return 'sucata';
    default:
      return 'avulso';
  }
}

const idCurto = (id: string) => id.slice(0, 8);

function descrever(m: ConsumoAnteriorMovimentacao): Pick<LinhaOperacaoConsumo, 'titulo' | 'detalhe' | 'link'> {
  const outro = m.contraparte?.nomeIdentificador ?? null;
  const operacao = m.idReferenciaOperacao ? ['/almoxarifado/movimentacoes', m.idReferenciaOperacao] : null;
  const nota = m.nota;
  const linkNota = nota ? ['/execucao/comercial', nota.idExecucao] : operacao;
  const tituloNota = nota?.referencia ? `Nota ${nota.referencia}` : 'Nota não localizada';
  switch (m.tipo) {
    case 'ENTREGA':
    case 'ESTORNO': {
      const acao = m.tipo === 'ENTREGA' ? 'Entrega' : 'Estorno';
      if (!m.solicitacao) return { titulo: acao, detalhe: outro, link: operacao };
      const contexto = m.solicitacao.contexto === 'TECNICO' ? 'técnica' : 'comercial';
      return {
        titulo: `${acao} da solicitação #${idCurto(m.solicitacao.id)}`,
        detalhe: [`Solicitação ${contexto}`, outro && (m.quantidade > 0 ? `de ${outro}` : `para ${outro}`)].filter(Boolean).join(' · '),
        link: ['/almoxarifado/solicitacoes', m.solicitacao.id]
      };
    }
    case 'APLICACAO':
      return { titulo: tituloNota, detalhe: nota?.tipoServico ?? null, link: linkNota };
    case 'RETIRADA':
      return { titulo: `Retirada · ${tituloNota}`, detalhe: nota?.tipoServico ?? null, link: linkNota };
    case 'DEVOLUCAO':
      return { titulo: outro ? `Devolução para ${outro}` : 'Devolução', detalhe: null, link: operacao };
    case 'TRANSFERENCIA':
      return { titulo: m.quantidade > 0 ? `Recebida de ${outro ?? 'outro estoque'}` : `Enviada para ${outro ?? 'outro estoque'}`, detalhe: null, link: operacao };
    case 'ENTRADA_AVULSA':
      return { titulo: 'Entrada avulsa', detalhe: outro, link: operacao };
    case 'BAIXA_AVULSA':
      return { titulo: 'Baixa avulsa', detalhe: outro, link: operacao };
    default:
      return { titulo: m.tipo, detalhe: outro, link: operacao };
  }
}

/**
 * Separa as movimentações do período por natureza (recebido, aplicado, devolvido...) e junta as
 * da mesma operação numa linha, somando a quantidade e reunindo as séries.
 */
export function agruparOperacoesConsumo(movimentacoes: readonly ConsumoAnteriorMovimentacao[] | null | undefined): SecaoOperacoesConsumo[] {
  const porGrupo = new Map<GrupoConsumo, Map<string, { linha: Omit<LinhaOperacaoConsumo, 'series' | 'quantidade'>; quantidade: number; series: string[] }>>();
  for (const m of movimentacoes ?? []) {
    const grupo = grupoDe(m);
    const sentido = m.quantidade >= 0 ? '+' : '-';
    const chave = `${m.tipo}|${m.idReferenciaOperacao ?? m.solicitacao?.id ?? m.id}|${sentido}|${m.condicao}`;
    const linhas = porGrupo.get(grupo) ?? new Map();
    const atual = linhas.get(chave) ?? { linha: { chave, dataHora: m.dataHora, ...descrever(m) }, quantidade: 0, series: [] };
    atual.quantidade += Number(m.quantidade) || 0;
    if (m.numSerie) atual.series.push(m.numSerie);
    linhas.set(chave, atual);
    porGrupo.set(grupo, linhas);
  }
  return ORDEM.filter((g) => porGrupo.has(g)).map((grupo) => {
    const linhas = Array.from(porGrupo.get(grupo)!.values()).map(({ linha, quantidade, series }) => ({
      ...linha,
      quantidade: arredondar(quantidade),
      series: [...series].sort((a, b) => a.localeCompare(b, 'pt-BR', { numeric: true }))
    }));
    return {
      grupo,
      ...SECOES[grupo],
      total: arredondar(linhas.reduce((t, l) => t + l.quantidade, 0)),
      foraDaConta: grupo === 'sucata',
      linhas
    };
  });
}

/** Evita 0.30000000000000004 nas somas de quantidades fracionadas. */
function arredondar(n: number): number {
  return Math.round(n * 1000) / 1000;
}
