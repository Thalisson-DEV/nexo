import { CesTone } from '@ces/ui';
import { FormularioSchema, SolicitacaoFinanceira } from '../../../core/models/financeiro.models';

/** Algo que quem aprova deveria olhar antes de decidir (mesmo formato dos relatórios de execução). */
export interface PontoAtencao {
  tom: CesTone;
  icone: string;
  titulo: string;
  texto: string;
  /** Continua valendo depois da decisão (os demais só pesam antes dela). */
  aposDecisao?: boolean;
}

const DIAS_VENCIMENTO_PROXIMO = 3;
const UM_DIA = 24 * 60 * 60 * 1000;

/**
 * Levanta os pontos que costumam mudar uma decisão: documento devolvido, nada
 * anexado, vencimento estourado ou colado, fornecedor fora do cadastro, falta de
 * nota e pagamento acima do que a compra autorizou.
 *
 * Só aponta; não bloqueia nada. Quem decide é a pessoa, e o servidor continua sendo
 * a garantia das regras.
 */
export function pontosDeAtencao(
  item: SolicitacaoFinanceira,
  schema: FormularioSchema | null,
  hoje: Date = new Date()
): PontoAtencao[] {
  const pontos: PontoAtencao[] = [];
  const documentos = item.documentos ?? [];

  const comPendencia = documentos.filter((doc) => doc.situacao === 'COM_PENDENCIA').length;
  if (comPendencia) {
    pontos.push({
      tom: 'danger',
      icone: 'pi pi-file-excel',
      titulo: comPendencia === 1 ? 'Documento devolvido' : `${comPendencia} documentos devolvidos`,
      texto: 'A conferência apontou pendência. Veja o motivo em Documentos & Comprovantes.',
      aposDecisao: true
    });
  }
  if (!documentos.length) {
    pontos.push({
      tom: 'warning',
      icone: 'pi pi-paperclip',
      titulo: 'Sem documentos anexados',
      texto: 'Nenhum orçamento, nota ou boleto sustenta o valor pedido.'
    });
  }

  const vencimento = valorCanonico(item, schema, 'VENCIMENTO');
  if (typeof vencimento === 'string' && /^\d{4}-\d{2}-\d{2}/.test(vencimento)) {
    const dias = diasAte(vencimento, hoje);
    const exibicao = dataLocal(vencimento).toLocaleDateString('pt-BR');
    if (dias < 0) {
      pontos.push({
        tom: 'danger',
        icone: 'pi pi-calendar-times',
        titulo: 'Vencimento já passou',
        texto: `Venceu em ${exibicao}. Confira juros e multa antes de autorizar.`
      });
    } else if (dias <= DIAS_VENCIMENTO_PROXIMO) {
      pontos.push({
        tom: 'warning',
        icone: 'pi pi-calendar',
        titulo: dias === 0 ? 'Vence hoje' : `Vence em ${dias} ${dias === 1 ? 'dia' : 'dias'}`,
        texto: `Vencimento em ${exibicao}: a decisão precisa sair a tempo do pagamento.`
      });
    }
  }

  if (item.fornecedor && !item.fornecedor.cadastrado) {
    pontos.push({
      tom: 'warning',
      icone: 'pi pi-building',
      titulo: 'Fornecedor fora do cadastro',
      texto: `"${item.fornecedor.nome}" foi digitado na solicitação e não tem histórico no cadastro.`
    });
  }

  if (valorCanonico(item, schema, 'TEM_NF') === false) {
    const justificativa = valorCanonico(item, schema, 'JUSTIFICATIVA_SEM_NF');
    pontos.push({
      tom: 'info',
      icone: 'pi pi-receipt',
      titulo: 'Sem nota fiscal',
      texto: typeof justificativa === 'string' && justificativa.trim()
        ? `Justificativa: ${justificativa.trim()}`
        : 'Não há justificativa registrada para a falta da nota.'
    });
  }

  const compra = item.compraVinculada;
  if (compra?.valorAprovado != null && item.valor - compra.valorAprovado > 0.005) {
    pontos.push({
      tom: 'warning',
      icone: 'pi pi-arrow-up-right',
      titulo: 'Acima do autorizado na compra',
      texto: `${moeda(item.valor - compra.valorAprovado)} a mais que o aprovado na compra nº ${compra.numero} (${moeda(compra.valorAprovado)}).`
    });
  }

  return pontos;
}

/** Dias de hoje até a data (negativo quando já passou), contados em dias de calendário. */
export function diasAte(dataIso: string, hoje: Date = new Date()): number {
  const inicioHoje = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  return Math.round((dataLocal(dataIso).getTime() - inicioHoje.getTime()) / UM_DIA);
}

/** Valor bruto do campo que o formulário liga ao campo canônico, se a variante o tiver. */
export function valorCanonico(
  item: SolicitacaoFinanceira,
  schema: FormularioSchema | null,
  canonico: string
): unknown {
  const campo = schema?.campos.find((c) => c.campoCanonico?.toUpperCase() === canonico);
  return campo ? item.valores[campo.chave]?.valor : undefined;
}

function dataLocal(dataIso: string): Date {
  const [ano, mes, dia] = dataIso.slice(0, 10).split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

function moeda(valor: number): string {
  return valor.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}
