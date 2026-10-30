import { CesTone } from '@ces/ui';

import { SolicitacaoStatus } from '../../../core/models/almoxarifado.models';

/** Vocabulário único de status das solicitações comerciais (tabela, quadro, calendário e detalhe). */
export interface StatusComercialDef {
  value: SolicitacaoStatus;
  label: string;
  /** Rótulo do atalho: diz o que falta fazer, não só o estado. */
  atalho: string;
  tone: CesTone;
  icon: string;
}

export const STATUS_COMERCIAIS: readonly StatusComercialDef[] = [
  { value: 'PENDENTE', label: 'Pendente', atalho: 'Aguardando aprovação', tone: 'warning', icon: 'pi-clock' },
  { value: 'APROVADO', label: 'Aprovado', atalho: 'Aguardando retirada', tone: 'info', icon: 'pi-check' },
  { value: 'ENTREGUE_PARCIAL', label: 'Entregue parcial', atalho: 'Entregue parcial', tone: 'warning', icon: 'pi-truck' },
  { value: 'ENTREGUE', label: 'Entregue', atalho: 'Entregue', tone: 'success', icon: 'pi-check-circle' },
  { value: 'REJEITADO', label: 'Rejeitado', atalho: 'Rejeitado', tone: 'danger', icon: 'pi-times-circle' },
  { value: 'ESTORNADO', label: 'Estornado', atalho: 'Estornado', tone: 'neutral', icon: 'pi-replay' }
];

const POR_VALOR = new Map(STATUS_COMERCIAIS.map((s) => [s.value as string, s]));

export function statusComercial(status?: string | null): StatusComercialDef {
  return POR_VALOR.get(status ?? '') ?? { value: status as SolicitacaoStatus, label: status || '—', atalho: status || '—', tone: 'neutral', icon: 'pi-circle' };
}

/** Status em que ainda há trabalho do almoxarifado: aprovar ou entregar. */
export const STATUS_EM_ABERTO: readonly SolicitacaoStatus[] = ['PENDENTE', 'APROVADO', 'ENTREGUE_PARCIAL'];

/** `yyyy-mm-dd` no fuso local (datas civis, sem deslocamento de UTC). */
export function isoCivil(data: Date): string {
  const m = String(data.getMonth() + 1).padStart(2, '0');
  const d = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${m}-${d}`;
}

/** Converte `yyyy-mm-dd` (ou ISO com hora) em data civil local à meia-noite. */
export function dataCivil(iso: string): Date {
  const [a, m, d] = iso.substring(0, 10).split('-').map(Number);
  return new Date(a, m - 1, d);
}

/** Campos que decidem se o saque está atrasado. */
export interface SaqueComercial {
  dataPrevista?: string | null;
  status?: string | null;
  /** Saldo pendente encerrado (ex.: parcial expirada): não há mais o que retirar. */
  encerrada?: boolean | null;
}

/** Saque previsto vencido com trabalho em aberto (saldo encerrado não conta). */
export function saqueAtrasado(s: SaqueComercial | null | undefined, hoje = new Date()): boolean {
  if (!s?.dataPrevista || s.encerrada || !STATUS_EM_ABERTO.includes(s.status as SolicitacaoStatus)) return false;
  return s.dataPrevista.substring(0, 10) < isoCivil(hoje);
}

/** "hoje", "amanhã", "em 3 dias", "há 2 dias" para a data prevista de saque. */
export function prazoRelativo(dataPrevista: string, hoje = new Date()): string {
  const base = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate());
  const dias = Math.round((dataCivil(dataPrevista).getTime() - base.getTime()) / 86_400_000);
  if (dias === 0) return 'hoje';
  if (dias === 1) return 'amanhã';
  if (dias === -1) return 'ontem';
  return dias > 0 ? `em ${dias} dias` : `há ${-dias} dias`;
}
