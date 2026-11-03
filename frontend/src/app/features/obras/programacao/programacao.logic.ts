import { CesSelectOption, CesTone } from '@ces/ui';
import {
  BarraGantt,
  SituacaoIntervencao,
  SituacaoProgramacao,
  TipoEventoProgramacao,
  TurnoProgramacao
} from '../../../core/models/obras-programacao.models';
import { parseDataBrParaDate } from '../obras-ui';

export const TURNOS: CesSelectOption<TurnoProgramacao>[] = [
  { label: 'Dia todo', value: 'DIA_TODO' },
  { label: 'Manhã', value: 'MANHA' },
  { label: 'Tarde', value: 'TARDE' }
];

export const SITUACOES_INTERVENCAO: CesSelectOption<SituacaoIntervencao>[] = [
  { label: 'Criado', value: 'CRIADO' },
  { label: 'Pendente', value: 'PENDENTE' },
  { label: 'Não se aplica', value: 'NAO_SE_APLICA' }
];

export const SIM_NAO: CesSelectOption<boolean>[] = [
  { label: 'Sim', value: true },
  { label: 'Não', value: false }
];

export const ROTULO_TURNO: Record<TurnoProgramacao, string> = { DIA_TODO: 'Dia todo', MANHA: 'Manhã', TARDE: 'Tarde' };

export const ROTULO_INTERVENCAO: Record<SituacaoIntervencao, string> = {
  CRIADO: 'Criado',
  PENDENTE: 'Pendente',
  NAO_SE_APLICA: 'Não se aplica'
};

/** A situação sempre tem ícone além da cor. */
export const SITUACAO_PROGRAMACAO: Record<SituacaoProgramacao, { rotulo: string; tom: CesTone; icone: string }> = {
  PROGRAMADA: { rotulo: 'Programada', tom: 'info', icone: 'pi pi-calendar' },
  ATRASADA: { rotulo: 'Atrasada', tom: 'danger', icone: 'pi pi-exclamation-triangle' },
  EM_ANDAMENTO: { rotulo: 'Em execução', tom: 'warning', icone: 'pi pi-play' },
  CONCLUIDA: { rotulo: 'Concluída', tom: 'success', icone: 'pi pi-check-circle' },
  CANCELADA: { rotulo: 'Cancelada', tom: 'neutral', icone: 'pi pi-ban' }
};

export const TIPO_EVENTO: Record<TipoEventoProgramacao, { rotulo: string; tom: CesTone; icone: string }> = {
  EDITADA: { rotulo: 'Editada', tom: 'info', icone: 'pi pi-pencil' },
  REPROGRAMADA: { rotulo: 'Reprogramada', tom: 'warning', icone: 'pi pi-calendar-clock' },
  CANCELADA: { rotulo: 'Cancelada', tom: 'neutral', icone: 'pi pi-ban' },
  EXCLUIDA: { rotulo: 'Excluída', tom: 'danger', icone: 'pi pi-trash' }
};

/** 330 → "5h30"; −106 → "−1h46". */
export function formatarMinutos(minutos: number | null | undefined): string {
  if (minutos == null) return '—';
  const absoluto = Math.abs(minutos);
  const texto = `${Math.floor(absoluto / 60)}h${String(absoluto % 60).padStart(2, '0')}`;
  return minutos < 0 ? `−${texto}` : texto;
}

export function formatarNumero(valor: number | null | undefined): string {
  return valor == null ? '—' : valor.toLocaleString('pt-BR', { maximumFractionDigits: 2 });
}

/** Situações que ainda pedem ação: o atalho "Em aberto". */
export const SITUACOES_EM_ABERTO: readonly SituacaoProgramacao[] = ['PROGRAMADA', 'EM_ANDAMENTO', 'ATRASADA'];

/** Filtros que olham a programação, não a equipe. Nulo ou vazio não restringe. */
export interface FiltrosBarra {
  idObra?: string | null;
  municipio?: string | null;
  prioridade?: string | null;
  turno?: TurnoProgramacao | null;
  linhaViva?: boolean | null;
  desligamento?: boolean | null;
  finalizaObra?: boolean | null;
  intervencao?: SituacaoIntervencao | null;
}

export function atendeFiltrosBarra(b: BarraGantt, f: FiltrosBarra): boolean {
  return (!f.idObra || b.idObra === f.idObra)
    && (!f.municipio || b.municipio === f.municipio)
    && (!f.prioridade || b.prioridade === f.prioridade)
    && (!f.turno || b.turno === f.turno)
    && (f.linhaViva == null || b.linhaViva === f.linhaViva)
    && (f.desligamento == null || b.desligamento === f.desligamento)
    && (f.finalizaObra == null || b.finalizaObra === f.finalizaObra)
    && (!f.intervencao || b.situacaoIntervencao === f.intervencao);
}

export function temFiltroBarra(f: FiltrosBarra): boolean {
  return !!(f.idObra || f.municipio || f.prioridade || f.turno || f.intervencao) || f.linhaViva != null || f.desligamento != null || f.finalizaObra != null;
}

/** Sem situação escolhida valem as ativas: cancelada só aparece quando pedida. */
export function atendeSituacao(situacao: SituacaoProgramacao, escolhidas: readonly SituacaoProgramacao[]): boolean {
  return escolhidas.length ? escolhidas.includes(situacao) : situacao !== 'CANCELADA';
}

export interface DiaGantt {
  data: Date;
  dia: number;
  semana: string;
  fimDeSemana: boolean;
  hoje: boolean;
}

const SEMANA = ['dom', 'seg', 'ter', 'qua', 'qui', 'sex', 'sáb'];
const UM_DIA_MS = 86_400_000;

function diasEntre(de: Date, ate: Date): number {
  return Math.round((Date.UTC(ate.getFullYear(), ate.getMonth(), ate.getDate()) - Date.UTC(de.getFullYear(), de.getMonth(), de.getDate())) / UM_DIA_MS);
}

export function diasDoPeriodo(inicio: Date, fim: Date, hoje = new Date()): DiaGantt[] {
  const dias: DiaGantt[] = [];
  for (let i = 0; i <= diasEntre(inicio, fim); i++) {
    const data = new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + i);
    dias.push({
      data,
      dia: data.getDate(),
      semana: SEMANA[data.getDay()],
      fimDeSemana: data.getDay() === 0 || data.getDay() === 6,
      hoje: diasEntre(data, hoje) === 0
    });
  }
  return dias;
}

export interface BarraPosicionada<T> {
  barra: T;
  /** Coluna inicial, a partir de 0. */
  coluna: number;
  /** Colunas ocupadas dentro do período. */
  largura: number;
  /** Faixa dentro da linha da equipe: programações que se cruzam ficam uma abaixo da outra. */
  faixa: number;
  cortadaNoInicio: boolean;
  cortadaNoFim: boolean;
}

/** Recorta cada barra no período e distribui em faixas, sem sobreposição. */
export function posicionarBarras<T extends { inicio: string; fim: string }>(barras: readonly T[], inicio: Date, fim: Date): BarraPosicionada<T>[] {
  const total = diasEntre(inicio, fim) + 1;
  const ordenadas = barras
    .map((barra) => ({ barra, de: diasEntre(inicio, parseDataBrParaDate(barra.inicio)!), ate: diasEntre(inicio, parseDataBrParaDate(barra.fim)!) }))
    .filter(({ de, ate }) => ate >= 0 && de < total)
    .sort((a, b) => a.de - b.de || a.ate - b.ate);

  const fimDaFaixa: number[] = [];
  return ordenadas.map(({ barra, de, ate }) => {
    let faixa = fimDaFaixa.findIndex((ultimo) => ultimo < de);
    if (faixa < 0) faixa = fimDaFaixa.length;
    fimDaFaixa[faixa] = ate;
    const coluna = Math.max(de, 0);
    return {
      barra,
      coluna,
      largura: Math.min(ate, total - 1) - coluna + 1,
      faixa,
      cortadaNoInicio: de < 0,
      cortadaNoFim: ate >= total
    };
  });
}

function somarDias(data: Date, dias: number): Date {
  return new Date(data.getFullYear(), data.getMonth(), data.getDate() + dias);
}

export type ModoArraste = 'mover' | 'inicio' | 'fim';

/** Datas depois de arrastar: mover leva as duas; pelas pontas, o início nunca passa do fim. */
export function datasArrastadas(inicio: Date, fim: Date, modo: ModoArraste, dias: number): { inicio: Date; fim: Date } {
  if (modo === 'mover') return { inicio: somarDias(inicio, dias), fim: somarDias(fim, dias) };
  if (modo === 'inicio') {
    const novo = somarDias(inicio, dias);
    return { inicio: novo > fim ? fim : novo, fim };
  }
  const novo = somarDias(fim, dias);
  return { inicio, fim: novo < inicio ? inicio : novo };
}

export type VisaoProgramacao = 'tabela' | 'semana' | 'mes';

/** Semana de segunda a domingo, como a operação conta. */
export function semanaDe(data: Date): { inicio: Date; fim: Date } {
  const paraSegunda = (data.getDay() + 6) % 7;
  return {
    inicio: new Date(data.getFullYear(), data.getMonth(), data.getDate() - paraSegunda),
    fim: new Date(data.getFullYear(), data.getMonth(), data.getDate() - paraSegunda + 6)
  };
}

export function periodoDaVisao(visao: VisaoProgramacao, data: Date): { inicio: Date; fim: Date } {
  return visao === 'semana' ? semanaDe(data) : mesDe(data);
}

export function deslocarVisao(visao: VisaoProgramacao, data: Date, sentido: 1 | -1): Date {
  return visao === 'semana'
    ? new Date(data.getFullYear(), data.getMonth(), data.getDate() + 7 * sentido)
    : new Date(data.getFullYear(), data.getMonth() + sentido, 1);
}

const MES = new Intl.DateTimeFormat('pt-BR', { month: 'long' });

export function tituloDoPeriodo(visao: VisaoProgramacao, periodo: { inicio: Date; fim: Date }): string {
  const { inicio, fim } = periodo;
  if (visao !== 'semana') return `${MES.format(inicio)} de ${inicio.getFullYear()}`;
  const mesInicio = inicio.getMonth() === fim.getMonth() ? '' : ` de ${MES.format(inicio)}`;
  return `${inicio.getDate()}${mesInicio} a ${fim.getDate()} de ${MES.format(fim)} de ${fim.getFullYear()}`;
}

/** Próximo dia de segunda a sexta. Feriado só o servidor conhece: a tela avisa quando o período fica sem dia útil. */
export function proximoDiaDeSemana(data: Date): Date {
  const dia = new Date(data.getFullYear(), data.getMonth(), data.getDate());
  while (dia.getDay() === 0 || dia.getDay() === 6) dia.setDate(dia.getDate() + 1);
  return dia;
}

/** Dia todo cruza com qualquer turno; manhã e tarde só com o mesmo turno. */
export function turnosCruzam(a: TurnoProgramacao, b: TurnoProgramacao | null): boolean {
  return !b || a === 'DIA_TODO' || b === 'DIA_TODO' || a === b;
}

/** Duas programações abertas da mesma equipe que caem nos mesmos dias e em turnos que cruzam. */
export function temConflito(barras: readonly { inicio: string; fim: string; turno: TurnoProgramacao; situacao: SituacaoProgramacao }[]): boolean {
  const abertas = barras.filter((b) => b.situacao !== 'CANCELADA' && b.situacao !== 'CONCLUIDA');
  return abertas.some((a, i) => abertas.slice(i + 1).some((b) =>
    parseDataBrParaDate(a.inicio)! <= parseDataBrParaDate(b.fim)! && parseDataBrParaDate(b.inicio)! <= parseDataBrParaDate(a.fim)! && turnosCruzam(a.turno, b.turno)));
}

/** "P1, P2 e P3 — KIT-BT-01", para não digitar o que a seleção já diz. */
export function sugerirDescricao(locais: readonly string[], kits: readonly string[]): string {
  const mostrados = locais.slice(0, 4);
  const resto = locais.length - mostrados.length;
  const nomes = mostrados.join(', ') + (resto > 0 ? ` e mais ${resto}` : '');
  const codigos = [...new Set(kits)].slice(0, 3).join(', ');
  return [nomes, codigos].filter(Boolean).join(' — ').slice(0, 500);
}

export function mesDe(data: Date): { inicio: Date; fim: Date } {
  return {
    inicio: new Date(data.getFullYear(), data.getMonth(), 1),
    fim: new Date(data.getFullYear(), data.getMonth() + 1, 0)
  };
}
