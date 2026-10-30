import { CesTone } from '@ces/ui';
import {
  CodigoTipoTecnico,
  ContextoSolicitacaoMaterial,
  obterCodigoTecnico,
  SolicitacaoMaterialDetalheResponse,
  StatusSeparacao,
  StatusSolicitacaoMaterial,
  TipoSolicitacaoTecnica,
} from '../../../core/models/solicitacao-material-tecnica.models';

const STATUS_LABELS: Record<string, string> = {
  PENDENTE: 'Pendente de decisão',
  APROVADO: 'Aprovada',
  REJEITADO: 'Rejeitada',
  ENTREGUE_PARCIAL: 'Entrega parcial',
  ENTREGUE: 'Entregue',
  DEVOLVIDO_PARCIAL: 'Devolução parcial',
  DEVOLVIDO: 'Devolvido',
  ESTORNADO: 'Estornado',
  CANCELADO: 'Cancelada',
  ENCERRADO: 'Encerrada',
  ENCERRADA: 'Encerrada',
};

export function statusLabel(status?: StatusSolicitacaoMaterial | string | null): string {
  if (!status) return '—';
  return (STATUS_LABELS as Record<string, string>)[status] ?? status;
}

export function statusTone(status?: StatusSolicitacaoMaterial | string | null): CesTone {
  if (!status) return 'neutral';
  if (status === 'ENTREGUE' || status === 'DEVOLVIDO') return 'success';
  if (status === 'REJEITADO' || status === 'CANCELADO' || status === 'ENCERRADO' || status === 'ENCERRADA') return 'danger';
  if (status === 'PENDENTE' || status.endsWith('_PARCIAL')) return 'warning';
  if (status === 'APROVADO') return 'info';
  return 'neutral';
}

export function statusTagClass(status?: StatusSolicitacaoMaterial | string | null): string {
  if (status === 'ENTREGUE' || status === 'DEVOLVIDO') {
    return 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800/80';
  }
  if (status === 'REJEITADO' || status === 'CANCELADO' || status === 'ENCERRADO' || status === 'ENCERRADA') {
    return 'bg-rose-50 dark:bg-rose-950/60 text-rose-700 dark:text-rose-300 border-rose-300 dark:border-rose-800/80';
  }
  if (status === 'PENDENTE' || (typeof status === 'string' && status.endsWith('_PARCIAL'))) {
    return 'bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border-amber-300 dark:border-amber-800/80';
  }
  if (status === 'APROVADO') {
    return 'bg-sky-50 dark:bg-sky-950/60 text-sky-700 dark:text-sky-300 border-sky-300 dark:border-sky-800/80';
  }
  return 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-300 dark:border-slate-700';
}

const SEPARACAO_LABELS: Record<StatusSeparacao, string> = {
  AGUARDANDO_AGENDAMENTO: 'Aguardando agendamento',
  PRONTO_PARA_SEPARACAO: 'Pronta para separação',
  EM_SEPARACAO: 'Em separação',
  SEPARADO: 'Separada'
};

export function separacaoLabel(status: StatusSeparacao | null | undefined): string {
  return status ? SEPARACAO_LABELS[status] : 'Não se aplica';
}

export function separacaoTone(status: StatusSeparacao | null | undefined): CesTone {
  if (status === 'SEPARADO') return 'success';
  if (status === 'EM_SEPARACAO') return 'info';
  if (status) return 'warning';
  return 'neutral';
}

export function separacaoTagClass(status?: StatusSeparacao | string | null): string {
  if (status === 'SEPARADO') {
    return 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-700 dark:text-emerald-300 border-emerald-300 dark:border-emerald-800/80';
  }
  if (status === 'EM_SEPARACAO') {
    return 'bg-sky-50 dark:bg-sky-950/60 text-sky-700 dark:text-sky-300 border-sky-300 dark:border-sky-800/80';
  }
  if (status === 'PRONTO_PARA_SEPARACAO' || status === 'AGUARDANDO_AGENDAMENTO') {
    return 'bg-amber-50 dark:bg-amber-950/60 text-amber-700 dark:text-amber-300 border-amber-300 dark:border-amber-800/80';
  }
  return 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-300 dark:border-slate-700';
}

/**
 * Verifica se a solicitação técnica já possui alguma entrega de material realizada.
 * Quando já houve entrega:
 * - O replanejamento (edição de datas, equipe e materiais) passa a ser estritamente proibido.
 * - A definição/alteração rápida de equipe passa a ser proibida.
 * - O registro ou alteração de reserva externa passa a ser proibido.
 */
export function possuiEntregaRealizada(
  detail?: SolicitacaoMaterialDetalheResponse | null,
): boolean {
  if (!detail) return false;
  const tech = detail.tecnico;

  // 1. Operações técnicas registradas com tipo 'ENTREGA'
  if (tech?.operacoes && Array.isArray(tech.operacoes)) {
    if (tech.operacoes.some((op) => op.tipo === 'ENTREGA')) {
      return true;
    }
  }

  // 2. Totais com entrega registrada (> 0)
  const totais = tech?.totais;
  if (totais) {
    if ((totais.entregaBruta ?? 0) > 0 || (totais.entregaLiquida ?? 0) > 0) {
      return true;
    }
  }

  // 3. Quantidades entregues registradas nos itens
  if (detail.itens && Array.isArray(detail.itens)) {
    if (
      detail.itens.some(
        (item) =>
          (item.quantidadeEntregue ?? 0) > 0 ||
          (item.quantidadeEntregueBruta ?? 0) > 0 ||
          (item.quantidadeEntregaLiquida ?? 0) > 0,
      )
    ) {
      return true;
    }
  }

  // 4. Status de entrega da solicitação
  const status = detail.status;
  if (status === 'ENTREGUE' || status === 'ENTREGUE_PARCIAL') {
    return true;
  }

  return false;
}

/**
 * Verifica se a solicitação técnica de devolução já possui alguma devolução realizada.
 */
export function possuiDevolucaoRealizada(
  detail?: SolicitacaoMaterialDetalheResponse | null,
): boolean {
  if (!detail) return false;
  const tech = detail.tecnico;

  if (tech?.operacoes && Array.isArray(tech.operacoes)) {
    if (tech.operacoes.some((op) => op.tipo === 'DEVOLUCAO')) {
      return true;
    }
  }

  const totais = tech?.totais;
  if (totais) {
    if ((totais.devolucaoBruta ?? 0) > 0 || (totais.devolucaoLiquida ?? 0) > 0) {
      return true;
    }
  }

  if (detail.itens && Array.isArray(detail.itens)) {
    if (
      detail.itens.some(
        (item) =>
          (item.quantidadeDevolvidaBruta ?? 0) > 0 ||
          (item.quantidadeDevolucaoLiquida ?? 0) > 0,
      )
    ) {
      return true;
    }
  }

  const status = detail.status;
  if (status === 'DEVOLVIDO' || status === 'DEVOLVIDO_PARCIAL') {
    return true;
  }

  return false;
}

/**
 * Verifica se já houve qualquer movimentação de atendimento (entrega ou devolução) na solicitação.
 */
export function possuiMovimentacaoAtendimento(
  detail?: SolicitacaoMaterialDetalheResponse | null,
): boolean {
  return possuiEntregaRealizada(detail) || possuiDevolucaoRealizada(detail);
}

/**
 * Verifica se a solicitação técnica possui algum item com saldo pendente de entrega.
 * Se todo o saldo já foi entregue (ou não há itens pendentes), não faz sentido exibir
 * nem permitir a ação de confirmar entrega.
 */
export function possuiSaldoPendenteEntrega(
  detail?: SolicitacaoMaterialDetalheResponse | null,
): boolean {
  if (!detail) return false;

  // Status terminais/concluídos onde a entrega não tem mais pendência
  if (
    detail.status === 'ENTREGUE' ||
    detail.status === 'DEVOLVIDO' ||
    detail.status === 'CANCELADO' ||
    detail.status === 'REJEITADO'
  ) {
    return false;
  }

  // 1. Verificação detalhada por itens
  if (detail.itens && Array.isArray(detail.itens) && detail.itens.length > 0) {
    return detail.itens.some((item) => {
      if (item.quantidadePendente !== undefined && item.quantidadePendente !== null) {
        return item.quantidadePendente > 0;
      }
      const solicitada = item.quantidade ?? 0;
      const entregue =
        item.quantidadeEntregaLiquida ??
        item.quantidadeEntregue ??
        item.quantidadeEntregueBruta ??
        0;
      return solicitada - entregue > 0;
    });
  }

  // 2. Verificação pelos totais agregados técnicos
  const totais = detail.tecnico?.totais;
  if (totais) {
    if (totais.pendente !== undefined && totais.pendente !== null) {
      return totais.pendente > 0;
    }
    const solicitada = totais.solicitada ?? 0;
    const entregue = totais.entregaLiquida ?? totais.entregaBruta ?? 0;
    return solicitada - entregue > 0;
  }

  return true;
}

/**
 * Verifica se a solicitação técnica de devolução possui algum item com saldo pendente de devolução.
 */
export function possuiSaldoPendenteDevolucao(
  detail?: SolicitacaoMaterialDetalheResponse | null,
): boolean {
  if (!detail) return false;

  if (
    detail.status === 'DEVOLVIDO' ||
    detail.status === 'ENTREGUE' ||
    detail.status === 'CANCELADO' ||
    detail.status === 'REJEITADO'
  ) {
    return false;
  }

  if (detail.itens && Array.isArray(detail.itens) && detail.itens.length > 0) {
    return detail.itens.some((item) => {
      if (item.quantidadePendente !== undefined && item.quantidadePendente !== null) {
        return item.quantidadePendente > 0;
      }
      const solicitada = item.quantidade ?? 0;
      const devolvido =
        item.quantidadeDevolucaoLiquida ??
        item.quantidadeDevolvidaBruta ??
        0;
      return solicitada - devolvido > 0;
    });
  }

  const totais = detail.tecnico?.totais;
  if (totais) {
    if (totais.pendente !== undefined && totais.pendente !== null) {
      return totais.pendente > 0;
    }
    const solicitada = totais.solicitada ?? 0;
    const devolvido = totais.devolucaoLiquida ?? totais.devolucaoBruta ?? 0;
    return solicitada - devolvido > 0;
  }

  return true;
}

/**
 * Verifica se a solicitação técnica possui saldo pendente de atendimento (entrega ou devolução conforme o tipo).
 */
export function possuiSaldoPendenteAtendimento(
  detail?: SolicitacaoMaterialDetalheResponse | null,
): boolean {
  if (!detail) return false;
  const tech = detail.tecnico;
  const isReturn =
    tech?.tipoTecnico === 'DEVOLUCAO_COMUM' || tech?.tipoTecnico === 'DEVOLUCAO_CONCRETO';
  return isReturn ? possuiSaldoPendenteDevolucao(detail) : possuiSaldoPendenteEntrega(detail);
}

export function tipoTecnicoLabel(tipo?: TipoSolicitacaoTecnica | null): string {
  return tipo ? obterCodigoTecnico(tipo) : 'Comercial';
}

/** Ícone: carrinho de saque (adições - 221/921) ou lixeira de retirada (devoluções - 222/922). */
export function tipoTecnicoIcon(tipo?: TipoSolicitacaoTecnica | CodigoTipoTecnico | string | null): string {
  if (!tipo) return 'pi pi-box';
  return tipoTecnicoEhAdicao(tipo) ? 'pi pi-shopping-cart' : 'pi pi-trash';
}

/**
 * Retorna true para Adição (221, 921, ADICAO_COMUM, ADICAO_CONCRETO, final 1) -> Verde
 * Retorna false para Devolução (222, 922, DEVOLUCAO_COMUM, DEVOLUCAO_CONCRETO, final 2) -> Vermelho
 */
export function tipoTecnicoEhAdicao(tipo?: TipoSolicitacaoTecnica | CodigoTipoTecnico | string | null): boolean {
  if (!tipo) return false;
  const str = String(tipo).trim().toUpperCase();
  if (str === '221' || str === '921' || str.endsWith('1') || str.startsWith('ADICAO')) {
    return true;
  }
  if (str === '222' || str === '922' || str.endsWith('2') || str.startsWith('DEVOLUCAO')) {
    return false;
  }
  return false;
}

export function tipoTecnicoColorClass(tipo?: TipoSolicitacaoTecnica | CodigoTipoTecnico | string | null): string {
  return tipoTecnicoEhAdicao(tipo)
    ? 'text-emerald-600 dark:text-emerald-400'
    : 'text-rose-600 dark:text-rose-400';
}

export function tipoTecnicoBadgeClass(tipo?: TipoSolicitacaoTecnica | CodigoTipoTecnico | string | null): string {
  return tipoTecnicoEhAdicao(tipo)
    ? 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-950/70 dark:text-emerald-300 dark:border-emerald-800/50'
    : 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-950/70 dark:text-rose-300 dark:border-rose-800/50';
}

export function tipoTecnicoContainerClass(tipo?: TipoSolicitacaoTecnica | CodigoTipoTecnico | string | null): string {
  return tipoTecnicoEhAdicao(tipo)
    ? 'bg-emerald-50 dark:bg-emerald-950/60 text-emerald-600 dark:text-emerald-400 border-emerald-200/60 dark:border-emerald-800/60'
    : 'bg-rose-50 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400 border-rose-200/60 dark:border-rose-800/60';
}

export function contextoLabel(contexto: ContextoSolicitacaoMaterial): string {
  return contexto === 'TECNICO' ? 'Técnica' : 'Comercial';
}

/** Mapa de rótulos amigáveis para os tipos de evento do histórico verificável. */
const EVENTO_LABELS: Record<string, string> = {
  SUBMISSAO_CRIADA: 'Submissão criada',
  SUBMISSAO_DESMEMBRADA: 'Submissão desmembrada',
  SOLICITACAO_CRIADA: 'Solicitação criada',
  PLANEJAMENTO_ATUALIZADO: 'Planejamento atualizado',
  REAGRUPAMENTO: 'Reagrupamento de itens',
  RESERVA_REGISTRADA: 'Reserva registrada',
  RESERVA_ALTERADA: 'Reserva alterada',
  SEPARACAO_INICIADA: 'Separação iniciada',
  SEPARACAO_CONCLUIDA: 'Separação concluída',
  ENTREGA_CONFIRMADA: 'Entrega confirmada',
  DEVOLUCAO_CONFIRMADA: 'Devolução confirmada',
  ESTORNO_CONFIRMADO: 'Estorno realizado',
  SALDO_ENCERRADO: 'Saldo encerrado',
  APROVACAO: 'Aprovação registrada',
  REJEICAO: 'Rejeição registrada',
  CANCELAMENTO: 'Cancelamento',
};

export function eventoLabel(tipo: string): string {
  return EVENTO_LABELS[tipo] ?? tipo.replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

/** Ícone PrimeIcons para cada tipo de evento. */
const EVENTO_ICONS: Record<string, string> = {
  SUBMISSAO_CRIADA: 'pi pi-plus-circle',
  SUBMISSAO_DESMEMBRADA: 'pi pi-objects-column',
  SOLICITACAO_CRIADA: 'pi pi-file-plus',
  PLANEJAMENTO_ATUALIZADO: 'pi pi-pencil',
  REAGRUPAMENTO: 'pi pi-objects-column',
  RESERVA_REGISTRADA: 'pi pi-bookmark',
  RESERVA_ALTERADA: 'pi pi-bookmark',
  SEPARACAO_INICIADA: 'pi pi-play',
  SEPARACAO_CONCLUIDA: 'pi pi-check-circle',
  ENTREGA_CONFIRMADA: 'pi pi-sign-out',
  DEVOLUCAO_CONFIRMADA: 'pi pi-sign-in',
  ESTORNO_CONFIRMADO: 'pi pi-undo',
  SALDO_ENCERRADO: 'pi pi-lock',
  APROVACAO: 'pi pi-thumbs-up',
  REJEICAO: 'pi pi-thumbs-down',
  CANCELAMENTO: 'pi pi-times-circle',
};

export function eventoIcon(tipo: string): string {
  return EVENTO_ICONS[tipo] ?? 'pi pi-circle';
}

export function eventoTone(tipo: string): CesTone {
  if (tipo.includes('CRIADA') || tipo.includes('SUBMISSAO')) return 'info';
  if (tipo.includes('ENTREGA') || tipo.includes('DEVOLUCAO_CONFIRMADA') || tipo.includes('APROVACAO') || tipo === 'SEPARACAO_CONCLUIDA') return 'success';
  if (tipo.includes('ESTORNO') || tipo.includes('REJEICAO') || tipo.includes('CANCELAMENTO')) return 'danger';
  if (tipo.includes('SEPARACAO_INICIADA') || tipo.includes('RESERVA') || tipo.includes('PLANEJAMENTO')) return 'warning';
  if (tipo.includes('SALDO_ENCERRADO')) return 'neutral';
  return 'info';
}

export function tipoTecnicoNome(
  tipo?: TipoSolicitacaoTecnica | CodigoTipoTecnico | string | null,
): string {
  if (!tipo) return 'Comercial';
  const str = String(tipo).trim().toUpperCase();
  if (str === 'ADICAO_COMUM' || str === '221' || str.endsWith('ADICAO_COMUM')) return 'Adição Comum';
  if (str === 'DEVOLUCAO_COMUM' || str === '222' || str.endsWith('DEVOLUCAO_COMUM')) return 'Devolução Comum';
  if (str === 'ADICAO_CONCRETO' || str === '921' || str.endsWith('ADICAO_CONCRETO')) return 'Adição Concreto';
  if (str === 'DEVOLUCAO_CONCRETO' || str === '922' || str.endsWith('DEVOLUCAO_CONCRETO')) return 'Devolução Concreto';
  return tipo;
}

export function tipoTecnicoDescricaoCompleta(
  tipo?: TipoSolicitacaoTecnica | CodigoTipoTecnico | string | null,
): string {
  if (!tipo) return 'Comercial';
  const str = String(tipo).trim().toUpperCase();
  if (str === 'ADICAO_COMUM' || str === '221' || str.endsWith('ADICAO_COMUM')) return 'Adição comum (221)';
  if (str === 'DEVOLUCAO_COMUM' || str === '222' || str.endsWith('DEVOLUCAO_COMUM')) return 'Devolução comum (222)';
  if (str === 'ADICAO_CONCRETO' || str === '921' || str.endsWith('ADICAO_CONCRETO')) return 'Adição concreto (921)';
  if (str === 'DEVOLUCAO_CONCRETO' || str === '922' || str.endsWith('DEVOLUCAO_CONCRETO')) return 'Devolução concreto (922)';
  return tipo;
}

export function tipoObraLabel(tipo?: string | null): string {
  if (!tipo) return '—';
  const map: Record<string, string> = {
    OBRA: 'Obra',
    CORRETIVA: 'Corretiva',
    PREVENTIVA: 'Preventiva',
    LPT: 'LPT',
  };
  return map[String(tipo).trim().toUpperCase()] ?? String(tipo);
}

export function formatarAlmoxarifado(nome?: string | null): string {
  if (!nome) return '—';
  if (nome.startsWith('ALMOXARIFADO_') || nome.includes('_')) {
    return nome
      .replace(/^ALMOXARIFADO_/i, 'Almoxarifado ')
      .replace(/_/g, ' ')
      .toLowerCase()
      .replace(/(?:^|\s)\S/g, (a) => a.toUpperCase());
  }
  return nome;
}

/** Rótulos amigáveis para os campos de diff do payload de alterações. */
const CAMPO_LABELS: Record<string, string> = {
  obra: 'Obra',
  idObra: 'Obra',
  equipe: 'Equipe',
  idEquipe: 'Equipe',
  idEquipeSaque: 'Equipe',
  almoxarifado: 'Almoxarifado',
  idAlmoxarifadoAtendimento: 'Almoxarifado',
  idAlmoxarifado: 'Almoxarifado',
  usuario: 'Usuário',
  idUsuario: 'Usuário',
  dataPrevista: 'Data prevista',
  dataPrevistaRetirada: 'Data prevista',
  ocorrencia: 'Ocorrência / OS',
  observacao: 'Observação',
  quantidade: 'Quantidade',
  quantidadeEntregaLiquida: 'Quantidade entregue',
  quantidadeDevolucaoLiquida: 'Quantidade devolvida',
  quantidadePendente: 'Quantidade pendente',
  material: 'Material',
  tipoMovimentacao: 'Tipo de movimento',
  tipoTecnico: 'Tipo técnico',
  tipoObra: 'Tipo de obra',
  status: 'Status',
  statusSeparacao: 'Separação',
  numero: 'Número da reserva',
  numeroReserva: 'Número da reserva',
  dataCriacao: 'Data da reserva',
  idAgenteResponsavel: 'Agente responsável',
  motivo: 'Motivo',
  justificativa: 'Justificativa',
  itens: 'Itens',
};

/** Extrai o identificador do item de caminhos como itens[ID_DO_ITEM].quantidade. */
export function extrairIdItemDoCaminho(caminho: string): string | null {
  return caminho.match(/^itens\[([^\]]+)\](?:\.|$)/)?.[1] ?? null;
}

export function campoLabel(caminho: string): string {
  if (!caminho) return 'Campo';

  // Trata caminhos no formato itens[ID].campo ou itens[ID]
  const itemMatch = caminho.match(/^itens\[([^\]]+)\](?:\.(.+))?$/);
  if (itemMatch) {
    const subCampo = itemMatch[2]?.split('.').at(-1);
    if (subCampo === 'quantidade') return 'Quantidade do item';
    if (subCampo === 'material') return 'Material do item';
    if (subCampo === 'tipoMovimentacao') return 'Tipo de movimento do item';
    if (subCampo) return CAMPO_LABELS[subCampo] ?? `Item (${subCampo})`;
    return 'Item da solicitação';
  }

  const segments = caminho.split('.');
  const last = segments[segments.length - 1];
  return CAMPO_LABELS[last] ?? last.replace(/([A-Z])/g, ' $1').trim().toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
}

/** Formata o nome do autor ou usuário de forma amigável, com fallback para USR. */
export function formatarNomeUsuario(
  autor?: { id?: string; nome?: string } | string | null,
  resolver?: { resolverNome: (val: any, fb?: string) => string } | null,
  fallback = 'Sistema'
): string {
  if (!autor) return fallback;

  if (resolver) {
    return resolver.resolverNome(autor, fallback);
  }

  if (typeof autor === 'string') {
    const str = autor.trim();
    if (str.toUpperCase().startsWith('USUARIO ') && str.length > 20) {
      const idPart = str.replace(/^USUARIO\s+/i, '').slice(0, 8);
      return `USR-${idPart.toUpperCase()}`;
    }
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str)) {
      return `USR-${str.slice(0, 8).toUpperCase()}`;
    }
    return str;
  }

  if (autor.nome) {
    if (autor.nome.toUpperCase().startsWith('USUARIO ') && autor.nome.length > 20) {
      const idPart = autor.nome.replace(/^USUARIO\s+/i, '').slice(0, 8);
      return `USR-${idPart.toUpperCase()}`;
    }
    return autor.nome;
  }

  if (autor.id) {
    return `USR-${autor.id.slice(0, 8).toUpperCase()}`;
  }

  return fallback;
}
