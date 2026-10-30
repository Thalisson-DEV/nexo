import { CesSelectOption, CesTone } from '@ces/ui';

import { GRAVIDADE_LABELS, GravidadeDesvio, STATUS_DESVIO_LABELS, StatusDesvio } from '../../core/models/sst.models';

/** Tom do DS (`ces-status`) por gravidade e status de desvio. */
export const GRAVIDADE_TOM: Record<GravidadeDesvio, CesTone> = { BAIXA: 'neutral', MEDIA: 'warning', ALTA: 'warning', CRITICA: 'danger' };
export const GRAVIDADE_ICONE: Record<GravidadeDesvio, string> = {
  BAIXA: 'pi-info-circle',
  MEDIA: 'pi-exclamation-circle',
  ALTA: 'pi-exclamation-triangle',
  CRITICA: 'pi-times-circle'
};
export const STATUS_DESVIO_TOM: Record<StatusDesvio, CesTone> = { ABERTO: 'danger', EM_TRATAMENTO: 'warning', RESOLVIDO: 'success' };
export const STATUS_DESVIO_ICONE: Record<StatusDesvio, string> = {
  ABERTO: 'pi-exclamation-circle',
  EM_TRATAMENTO: 'pi-wrench',
  RESOLVIDO: 'pi-check-circle'
};

export const OPCOES_STATUS_DESVIO: CesSelectOption<StatusDesvio>[] = (Object.keys(STATUS_DESVIO_LABELS) as StatusDesvio[]).map((value) => ({
  label: STATUS_DESVIO_LABELS[value],
  value
}));
export const OPCOES_GRAVIDADE: CesSelectOption<GravidadeDesvio>[] = (Object.keys(GRAVIDADE_LABELS) as GravidadeDesvio[]).map((value) => ({
  label: GRAVIDADE_LABELS[value],
  value
}));

/** Situação do prazo: o servidor aceita "apenas vencidos" ou "sem prazo", nunca os dois. */
export type SituacaoPrazo = 'VENCIDOS' | 'SEM_PRAZO';
export const OPCOES_SITUACAO_PRAZO: CesSelectOption<SituacaoPrazo | null>[] = [
  { label: 'Todos', value: null },
  { label: 'Apenas vencidos', value: 'VENCIDOS' },
  { label: 'Sem prazo', value: 'SEM_PRAZO' }
];

/** Desvios ainda por tratar: o padrão das listas de desvios. */
export const DESVIOS_EM_ABERTO: StatusDesvio[] = ['ABERTO', 'EM_TRATAMENTO'];
