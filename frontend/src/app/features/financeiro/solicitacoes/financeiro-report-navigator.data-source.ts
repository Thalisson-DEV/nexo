import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  CesReportNavigatorConfig,
  CesReportNavigatorCriteria,
  CesReportNavigatorDataSource,
  CesReportNavigatorItem,
  CesReportNavigatorPage
} from '../../../design-system/patterns/report-navigator/report-navigator.models';
import {
  SolicitacaoFinanceira,
  StatusSolicitacao,
  TipoSolicitacao
} from '../../../core/models/financeiro.models';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { nomeSolicitante } from './solicitacao-financeira.utils';
import { CesTone } from '../../../design-system/core';

const STATUS_LABELS: Record<StatusSolicitacao, string> = {
  RASCUNHO: 'Rascunho',
  SOLICITADO: 'Aguardando',
  APROVADO: 'Aprovado',
  REJEITADO: 'Rejeitado',
  CANCELADO: 'Cancelado'
};

const STATUS_TONES: Record<StatusSolicitacao, CesTone> = {
  RASCUNHO: 'neutral',
  SOLICITADO: 'warning',
  APROVADO: 'success',
  REJEITADO: 'danger',
  CANCELADO: 'neutral'
};

@Injectable({ providedIn: 'root' })
export class FinanceiroReportNavigatorDataSource
  implements CesReportNavigatorDataSource<SolicitacaoFinanceira>
{
  private readonly financeiroService = inject(FinanceiroService);

  readonly initialCriteria: Partial<CesReportNavigatorCriteria> = {
    scope: 'AMBOS',
    status: null,
    sort: 'dataCriacao,desc',
    mine: false
  };

  readonly config: CesReportNavigatorConfig = {
    title: (criteria) => {
      if (criteria.scope === 'COMPRA') return 'Solicitações de compra';
      if (criteria.scope === 'PAGAMENTO') return 'Solicitações de pagamento';
      return 'Todas as solicitações';
    },
    icon: (criteria) => {
      if (criteria.scope === 'COMPRA') return 'pi pi-shopping-cart';
      if (criteria.scope === 'PAGAMENTO') return 'pi pi-credit-card';
      return 'pi pi-wallet';
    },
    eyebrow: 'Navegador',
    scopeOptions: [
      { label: 'Ambos', value: 'AMBOS', icon: 'pi pi-wallet' },
      { label: 'Compras', value: 'COMPRA', icon: 'pi pi-shopping-cart' },
      { label: 'Pagamentos', value: 'PAGAMENTO', icon: 'pi pi-credit-card' }
    ],
    statusChips: [
      { label: 'Todas', value: null, icon: 'pi pi-list' },
      { label: 'Aguardando', value: 'SOLICITADO', tone: 'warning', icon: 'pi pi-clock' },
      { label: 'Aprovadas', value: 'APROVADO', tone: 'success', icon: 'pi pi-check-circle' },
      { label: 'Rascunhos', value: 'RASCUNHO', tone: 'neutral', icon: 'pi pi-file-edit' },
      { label: 'Rejeitadas', value: 'REJEITADO', tone: 'danger', icon: 'pi pi-times-circle' }
    ],
    sortOptions: [
      { label: 'Recentes', value: 'dataCriacao,desc' },
      { label: 'Antigas', value: 'dataCriacao,asc' },
      { label: 'Maior valor', value: 'valor,desc' }
    ],
    showMineToggle: true,
    mineToggleLabel: 'Minhas',
    mineToggleTitle: 'Filtrar apenas solicitações abertas por mim',
    searchPlaceholder: 'Busca rápida...',
    searchLabel: 'Buscar solicitações',
    clearFiltersLabel: 'Limpar',
    listAllLabel: 'Ver todas',
    listAllTooltip: 'Abrir página completa com tabela',
    totalLabel: (total) => `${total} ${total === 1 ? 'solicitação' : 'solicitações'}`
  };

  load(criteria: CesReportNavigatorCriteria): Observable<CesReportNavigatorPage<SolicitacaoFinanceira>> {
    const scope = (criteria.scope as TipoSolicitacao | 'AMBOS') || 'AMBOS';
    return this.financeiroService
      .listarSolicitacoes(scope, {
        buscaGlobal: criteria.search.trim() || null,
        status: (criteria.status as StatusSolicitacao) || null,
        apenasMinhas: criteria.mine || null,
        page: 0,
        size: 30,
        sort: criteria.sort || 'dataCriacao,desc'
      })
      .pipe(
        map((res) => ({
          items: res.content,
          totalElements: res.page.totalElements
        }))
      );
  }

  toItem(raw: SolicitacaoFinanceira): CesReportNavigatorItem {
    const isCompra = raw.formulario?.tipoSolicitacao === 'COMPRA';
    return {
      id: raw.id,
      number: `#${raw.numero}`,
      typePill: {
        label: isCompra ? 'Compra' : 'Pagamento',
        variant: isCompra ? 'compra' : 'pagamento',
        icon: isCompra ? 'pi pi-shopping-cart' : 'pi pi-credit-card'
      },
      value: raw.valor,
      title: raw.valores?.['descricao']?.exibicao || raw.formulario?.titulo || 'Solicitação sem descrição',
      status: {
        label: STATUS_LABELS[raw.status] || raw.status,
        tone: STATUS_TONES[raw.status] || 'neutral'
      },
      metaAuthor: {
        label: nomeSolicitante(raw),
        icon: 'pi pi-user',
        title: `Solicitante: ${nomeSolicitante(raw)}`
      },
      metaDate: {
        date: raw.dataCriacao,
        format: 'dd/MM/yy',
        title: `Criado em: ${raw.dataCriacao}`
      },
      raw
    };
  }

  getRouteForItem(item: CesReportNavigatorItem | SolicitacaoFinanceira): string[] {
    const raw = (item as CesReportNavigatorItem).raw as SolicitacaoFinanceira | undefined ?? (item as SolicitacaoFinanceira);
    const tipo = raw?.formulario?.tipoSolicitacao === 'COMPRA' ? 'compras' : 'pagamentos';
    return ['/financeiro', tipo, String(raw.id || item.id)];
  }

  getListRoute(): string[] {
    return ['/financeiro', 'gestao', 'solicitacoes'];
  }
}
