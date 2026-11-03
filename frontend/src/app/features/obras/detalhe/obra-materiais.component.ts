import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CesAsyncStateComponent, CesStatusComponent, CesTone } from '@ces/ui';
import {
  ItemMovimentacaoResumoObra,
  SaldoObra,
  MovimentacaoResumoObra,
  SolicitacaoResumoObra,
} from '../../../core/models/solicitacao-material-tecnica.models';
import {
  statusLabel,
  statusTone,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from '../../almoxarifado/solicitacoes-material/solicitacao-tecnica-presentation';

const MOVIMENTACOES: Record<string, { titulo: string; icone: string; tom: string }> = {
  ENTREGA: { titulo: 'Entrega', icone: 'pi pi-arrow-down', tom: 'success' },
  DEVOLUCAO: { titulo: 'Devolução', icone: 'pi pi-undo', tom: 'danger' },
  ENTRADA_AVULSA: { titulo: 'Entrada avulsa', icone: 'pi pi-plus', tom: 'neutral' },
  BAIXA_AVULSA: { titulo: 'Baixa avulsa', icone: 'pi pi-minus', tom: 'neutral' },
  TRANSFERENCIA: { titulo: 'Transferência', icone: 'pi pi-arrow-right-arrow-left', tom: 'neutral' },
  ESTORNO: { titulo: 'Estorno', icone: 'pi pi-replay', tom: 'neutral' },
  APLICACAO: { titulo: 'Aplicação', icone: 'pi pi-check', tom: 'success' },
  RETIRADA: { titulo: 'Retirada', icone: 'pi pi-arrow-up', tom: 'neutral' },
};

/** Estado do saldo da obra; `null` quando a pessoa não pode ver saldos. */
export type CargaSaldoObra = { estado: 'loading' } | { estado: 'error'; mensagem: string } | { estado: 'ready'; dados: SaldoObra } | null;

export interface MaterialMovimentado {
  chave: string;
  codigo: string;
  descricao: string;
  unidade?: string | null;
  condicao: string;
  quantidade: number;
  series: string[];
}

/** O backend manda uma linha por série; aqui cada material aparece uma vez, com as séries juntas. */
export function agruparMateriais(itens: readonly ItemMovimentacaoResumoObra[]): MaterialMovimentado[] {
  const grupos = new Map<string, MaterialMovimentado>();
  for (const item of itens) {
    const condicao = item.condicao === 'SUCATA' ? 'Sucata' : 'Novo';
    const chave = `${item.codigo}|${condicao}`;
    const grupo = grupos.get(chave) ?? { chave, codigo: item.codigo, descricao: item.descricao, unidade: item.unidade, condicao, quantidade: 0, series: [] };
    grupo.quantidade += Number(item.quantidade) || 0;
    if (item.numSerie) grupo.series.push(item.numSerie);
    grupos.set(chave, grupo);
  }
  return [...grupos.values()];
}

/**
 * Saldo, solicitações e movimentações de material da obra. O saldo já vem aberto e pode ser recolhido;
 * solicitações e movimentações são linhas com o resumo, e os materiais só aparecem ao abrir, como no
 * histórico de operações das solicitações. Na impressão vai tudo aberto.
 */
@Component({
  selector: 'app-obra-materiais',
  standalone: true,
  imports: [CesAsyncStateComponent, CesStatusComponent, DatePipe, DecimalPipe, RouterLink],
  templateUrl: './obra-materiais.component.html',
  styleUrl: './obra-materiais.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ObraMateriaisComponent {
  readonly solicitacoes = input<readonly (SolicitacaoResumoObra & { ocorrencia?: string | null })[]>([]);
  readonly movimentacoes = input<readonly MovimentacaoResumoObra[]>([]);
  readonly truncadas = input(false);
  readonly saldo = input<CargaSaldoObra>(null);
  readonly recarregarSaldo = output<void>();

  protected readonly tipoLabel = tipoTecnicoLabel;
  protected readonly tipoIcone = tipoTecnicoIcon;
  protected readonly ehAdicao = tipoTecnicoEhAdicao;
  protected readonly statusLabel = statusLabel;
  protected readonly statusTone = statusTone;

  protected readonly saldoAberto = signal(true);
  private readonly abertos = signal<ReadonlySet<string>>(new Set());
  protected readonly materiaisPorOperacao = computed(
    () => new Map(this.movimentacoes().map((m) => [m.idReferencia, agruparMateriais(m.itens)])),
  );

  protected aberto(id: string): boolean {
    return this.abertos().has(id);
  }

  protected alternar(id: string): void {
    this.abertos.update((atual) => {
      const proximo = new Set(atual);
      if (!proximo.delete(id)) proximo.add(id);
      return proximo;
    });
  }

  protected expandirSolicitacoes(): void {
    this.definir(this.solicitacoes().map((s) => s.id), true);
  }

  protected recolherSolicitacoes(): void {
    this.definir(this.solicitacoes().map((s) => s.id), false);
  }

  protected expandirMovimentacoes(): void {
    this.definir(this.movimentacoes().map((m) => m.idReferencia), true);
  }

  protected recolherMovimentacoes(): void {
    this.definir(this.movimentacoes().map((m) => m.idReferencia), false);
  }

  protected condicaoLabel(condicao: string): string {
    return condicao === 'SUCATA' ? 'Sucata' : 'Novo';
  }

  protected tomCondicao(condicao: string): CesTone {
    return condicao === 'SUCATA' ? 'warning' : 'neutral';
  }

  protected comPendencia(s: SolicitacaoResumoObra): number {
    return (s.itens ?? []).filter((i) => i.quantidadePendente > 0).length;
  }

  protected movimentacao(m: MovimentacaoResumoObra) {
    return MOVIMENTACOES[m.tipo] ?? { titulo: m.tipo, icone: 'pi pi-arrow-right-arrow-left', tom: 'neutral' };
  }

  private definir(ids: string[], abrir: boolean): void {
    this.abertos.update((atual) => {
      const proximo = new Set(atual);
      ids.forEach((id) => (abrir ? proximo.add(id) : proximo.delete(id)));
      return proximo;
    });
  }
}
