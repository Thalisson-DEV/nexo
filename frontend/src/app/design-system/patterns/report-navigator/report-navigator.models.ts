import { Observable } from 'rxjs';
import { CesTone } from '../../core';

/** Identificador de item no navegador */
export type CesReportNavigatorItemId = string | number;

/** Opção de seletor segmentado (escopo/tipo) ou ordenação */
export interface CesReportNavigatorOption<T = unknown> {
  label: string;
  value: T;
  icon?: string;
}

/** Chip de filtro rápido de status */
export interface CesReportNavigatorChip<T = unknown> {
  label: string;
  value: T | null;
  tone?: CesTone;
  icon?: string;
}

/** Configuração visual e comportamental do navegador */
export interface CesReportNavigatorConfig {
  /** Título do navegador quando expandido (string ou função reativa aos critérios) */
  title: string | ((criteria: CesReportNavigatorCriteria) => string);
  /** Eyebrow no header (padrão: "Navegador") */
  eyebrow?: string;
  /** Ícone do cabeçalho (classe CSS PrimeIcons ou função reativa) */
  icon: string | ((criteria: CesReportNavigatorCriteria) => string);
  /** Opções de seletor de tipo / escopo (ex: Ambos, Compras, Pagamentos) */
  scopeOptions?: readonly CesReportNavigatorOption[];
  /** Placeholder do campo de busca */
  searchPlaceholder?: string;
  /** Label de acessibilidade do campo de busca */
  searchLabel?: string;
  /** Chips de filtro rápido (status) */
  statusChips?: readonly CesReportNavigatorChip[];
  /** Opções de ordenação */
  sortOptions?: readonly CesReportNavigatorOption<string>[];
  /** Habilitar toggle "Minhas" */
  showMineToggle?: boolean;
  /** Label do toggle "Minhas" (default: "Minhas") */
  mineToggleLabel?: string;
  /** Título/Tooltip do toggle "Minhas" */
  mineToggleTitle?: string;
  /** Label do botão de limpar filtros (default: "Limpar") */
  clearFiltersLabel?: string;
  /** Label do link para listagem completa (default: "Ver todas") */
  listAllLabel?: string;
  /** Tooltip do link para listagem completa (default: "Abrir página completa com tabela") */
  listAllTooltip?: string;
  /** Função de formatação do total de registros */
  totalLabel?: (total: number) => string;
}

/** Critérios de busca e filtros passados ao data source */
export interface CesReportNavigatorCriteria {
  search: string;
  scope?: unknown;
  status?: unknown;
  sort?: string;
  mine?: boolean;
}

/** Página de itens retornada pelo data source */
export interface CesReportNavigatorPage<T = unknown> {
  items: T[];
  totalElements: number;
}

/** Estrutura normalizada para renderizar um card no navegador */
export interface CesReportNavigatorItem {
  id: CesReportNavigatorItemId;
  /** Identificador curto / número (Linha 1, ex: "#1024", "OB-001") */
  number?: string;
  /** Pill opcional no topo (Linha 1, ex: "Compra", "Adição", "Entrada") */
  typePill?: {
    label: string;
    icon?: string;
    variant?: 'compra' | 'pagamento' | 'info' | 'brand' | 'neutral' | 'success' | 'warning' | 'danger' | string;
  };
  /** Valor monetário ou destaque à direita (Linha 1, ex: "R$ 1.500,00", "45 itens") */
  value?: string | number | null;
  /** Título principal / Descrição (Linha 2) */
  title: string;
  /** Status exibido com CesStatus (Linha 3) */
  status?: {
    label: string;
    tone?: CesTone;
  };
  /** Autor / Solicitante / Equipe (Linha 3) */
  metaAuthor?: {
    label: string;
    icon?: string;
    title?: string;
  };
  /** Data / Hora (Linha 3) */
  metaDate?: {
    date: string | Date | null | undefined;
    format?: string;
    title?: string;
  };
  /** Metadado adicional opcional (Linha 3) */
  metaExtra?: {
    label: string;
    icon?: string;
    title?: string;
  };
  /** Objeto bruto do domínio original */
  raw?: unknown;
}

/** Contrato que todo data source de relatório implementa */
export interface CesReportNavigatorDataSource<T = unknown> {
  /** Configuração declarativa do cabeçalho, filtros e botões */
  readonly config: CesReportNavigatorConfig;
  /** Valores iniciais opcionais dos critérios */
  readonly initialCriteria?: Partial<CesReportNavigatorCriteria>;
  /** Carrega uma página de dados baseada nos critérios informados */
  load(criteria: CesReportNavigatorCriteria): Observable<CesReportNavigatorPage<T>>;
  /** Converte a entidade de domínio para a representação do card */
  toItem(raw: T): CesReportNavigatorItem;
  /** Retorna os comandos do Router para navegar ao item selecionado */
  getRouteForItem(item: CesReportNavigatorItem | T): any[] | string;
  /** Retorna os comandos do Router para o botão "Ver todas" */
  getListRoute(): any[] | string;
  /** Busca item específico por ID caso precise ser injetado fora da 1ª página (opcional) */
  loadById?(id: CesReportNavigatorItemId): Observable<T | null>;
}
