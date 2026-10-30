import { CesTone } from '../core/design-system.types';

export interface CesKanbanColumn<T = unknown> {
  id: string;
  label: string;
  icon: string;
  tone: CesTone;
  items: T[];
  /** Total real de registros nesta coluna (pode ser > items.length se houver truncamento) */
  totalCount: number;
  metricas?: CesKanbanMetrica[];
  subGroups?: CesKanbanSubGroup<T>[];
}

export interface CesKanbanSubGroupStyle {
  /** Background sólido do cabeçalho (tema claro e escuro) */
  headerBg: string;
  /** Hover no cabeçalho */
  headerHoverBg: string;
  /** Cor do texto do título (branco de alto contraste para fundo sólido) */
  textClass: string;
  /** Cor do ícone chevron e detalhes */
  iconClass: string;
  /** Badge quando houver itens > 0 (máximo contraste sobre fundo sólido) */
  badgeClass: string;
  /** Fundo da área de cards quando expandido */
  bodyBg?: string;
}

export interface CesKanbanSubGroup<T = unknown> {
  id: string;
  label: string;
  items: T[];
  totalCount?: number;
  collapsed?: boolean;
  style?: CesKanbanSubGroupStyle;
}

export interface CesKanbanMetrica {
  label: string;
  valor: number | string;
  /** Se true, destaca visualmente como alerta (ex: contagem de atrasadas) */
  alerta?: boolean;
  formato?: 'inteiro' | 'dias' | 'percentual';
}

export interface CesKanbanItemMovedEvent<T = unknown> {
  item: T;
  fromColumnId: string;
  toColumnId: string;
  fromSubGroupId?: string;
  toSubGroupId?: string;
}

export interface CesKanbanSubGroupToggleEvent {
  columnId: string;
  subGroupId: string;
  collapsed: boolean;
}

/**
 * Paleta de cores sólidas, harmoniosas e com contraste perfeito para temas claro e escuro.
 * Cada macro-coluna possui sua família cromática, e os sub-status são blocos sólidos distintos:
 * - Cabeçalhos sólidos bem definidos sem bordas pretas
 * - Texto branco e ícones nítidos (WCAG AAA)
 * - Badges com pílula branca para itens ativos
 * - Área de cards limpa e neutra integrada ao Design System Nexo
 */
export const SUB_STATUS_THEMES: Record<string, CesKanbanSubGroupStyle> = {
  // 1. Planejamento — Família Azul Royal (#005cdb)
  PENDENTE_PLANEJAMENTO: {
    headerBg: 'bg-[#005cdb] hover:bg-[#0051c2]',
    headerHoverBg: 'hover:bg-[#0051c2]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },
  PLANEJADO: {
    headerBg: 'bg-[#005cdb] hover:bg-[#0051c2]',
    headerHoverBg: 'hover:bg-[#0051c2]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },
  PENDENTE_APROVACAO: {
    headerBg: 'bg-[#d97706] hover:bg-[#b45309]',
    headerHoverBg: 'hover:bg-[#b45309]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },

  // 2. Separação — Família Laranja / Vermelho / Âmbar
  PRONTO_PARA_SEPARACAO: {
    headerBg: 'bg-[#d9480f] hover:bg-[#c23e0c]',
    headerHoverBg: 'hover:bg-[#c23e0c]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },
  EM_SEPARACAO: {
    headerBg: 'bg-[#c92a2a] hover:bg-[#b02525]',
    headerHoverBg: 'hover:bg-[#b02525]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },
  SEPARADO: {
    headerBg: 'bg-[#b05a00] hover:bg-[#9a4e00]',
    headerHoverBg: 'hover:bg-[#9a4e00]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },

  // 3. Entrega & Campo — Família Verde / Ciano / Esmeralda
  PENDENTE_RETIRADA: {
    headerBg: 'bg-[#087f5b] hover:bg-[#076e4f]',
    headerHoverBg: 'hover:bg-[#076e4f]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },
  PENDENTE_DEVOLUCAO: {
    headerBg: 'bg-[#0c6b9e] hover:bg-[#0a5c88]',
    headerHoverBg: 'hover:bg-[#0a5c88]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },
  CONCLUIDO: {
    headerBg: 'bg-[#088c42] hover:bg-[#077a39]',
    headerHoverBg: 'hover:bg-[#077a39]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },

  // 4. Recusado — Família Crimson / Aço
  REJEITADO: {
    headerBg: 'bg-[#c20e4d] hover:bg-[#a90c43]',
    headerHoverBg: 'hover:bg-[#a90c43]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },
  CANCELADO: {
    headerBg: 'bg-[#3b4b5e] hover:bg-[#324050]',
    headerHoverBg: 'hover:bg-[#324050]',
    textClass: 'text-white font-semibold',
    iconClass: 'text-white/90',
    badgeClass: 'bg-black/25 text-white font-bold',
    bodyBg: 'bg-transparent',
  },
};

export function getSubGroupTheme(id: string): CesKanbanSubGroupStyle {
  return (
    SUB_STATUS_THEMES[id] ?? {
      headerBg: 'bg-slate-600 dark:bg-slate-700',
      headerHoverBg: 'hover:bg-slate-700 dark:hover:bg-slate-600',
      textClass: 'text-white',
      iconClass: 'text-white/90',
      badgeClass: 'bg-white text-slate-900',
      bodyBg: 'bg-slate-50/50 dark:bg-slate-900/40',
    }
  );
}
