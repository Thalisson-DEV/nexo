import { CesTone } from '@ces/ui';
import {
  EtapaSolicitacao,
  MaterialDoPainel,
  PainelSolicitacoesResponse,
  ProcessoSolicitacao,
  ResumoPeriodoAnterior,
  SolicitacaoDoPainel
} from '../../../../core/models/painel-solicitacoes.models';

/**
 * Cálculos do painel de solicitações de material, sem dependência de Angular: indicadores,
 * séries, pipeline das abertas, aging, rankings e comparações. Toda regra aqui opera sobre as
 * linhas devolvidas pelo backend, para que cada número possa ser aberto até as solicitações
 * que o compõem.
 */

export type ModoPainel = 'GERAL' | 'COMERCIAL' | 'TECNICO';
export type Situacao = 'concluida' | 'aberta' | 'recusada';

const FUSO = 'America/Sao_Paulo';
const HORA_MS = 3_600_000;
const DIA_MS = 24 * HORA_MS;
const DIA_ISO = new Intl.DateTimeFormat('en-CA', { timeZone: FUSO, year: 'numeric', month: '2-digit', day: '2-digit' });
const DIA_MES = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, day: '2-digit', month: '2-digit' });
const DIA_HORA = new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

// ------------------------------------------------------------------ rótulos

export const ROTULO_PROCESSO: Record<ProcessoSolicitacao, string> = { COMERCIAL: 'Comercial', TECNICO: 'Técnico' };

export const ETAPAS: Record<EtapaSolicitacao, { rotulo: string; icone: string; curto: string }> = {
  PENDENTE_APROVACAO: { rotulo: 'Pendente de aprovação', curto: 'Aprovação', icone: 'pi pi-inbox' },
  PENDENTE_PLANEJAMENTO: { rotulo: 'Pendente de planejamento', curto: 'Planejamento', icone: 'pi pi-calendar-plus' },
  PLANEJADO: { rotulo: 'Planejado', curto: 'Planejado', icone: 'pi pi-calendar' },
  PRONTO_PARA_SEPARACAO: { rotulo: 'Pronto para separação', curto: 'Fila de separação', icone: 'pi pi-list-check' },
  EM_SEPARACAO: { rotulo: 'Em separação', curto: 'Em separação', icone: 'pi pi-box' },
  SEPARADO: { rotulo: 'Separado', curto: 'Separado', icone: 'pi pi-check-square' },
  PENDENTE_RETIRADA: { rotulo: 'Pendente de retirada', curto: 'Retirada', icone: 'pi pi-truck' },
  PENDENTE_DEVOLUCAO: { rotulo: 'Pendente de devolução', curto: 'Devolução', icone: 'pi pi-replay' },
  CONCLUIDO: { rotulo: 'Concluído', curto: 'Concluído', icone: 'pi pi-check-circle' },
  REJEITADO: { rotulo: 'Rejeitado', curto: 'Rejeitado', icone: 'pi pi-times-circle' },
  CANCELADO: { rotulo: 'Cancelado', curto: 'Cancelado', icone: 'pi pi-ban' }
};

/** Etapas em aberto, na ordem do fluxo, por modo. */
export const ETAPAS_ABERTAS: Record<ModoPainel, EtapaSolicitacao[]> = {
  COMERCIAL: ['PENDENTE_APROVACAO', 'PENDENTE_RETIRADA'],
  TECNICO: ['PENDENTE_APROVACAO', 'PENDENTE_PLANEJAMENTO', 'PLANEJADO', 'PRONTO_PARA_SEPARACAO', 'EM_SEPARACAO', 'SEPARADO', 'PENDENTE_RETIRADA', 'PENDENTE_DEVOLUCAO'],
  GERAL: ['PENDENTE_APROVACAO', 'PENDENTE_PLANEJAMENTO', 'PLANEJADO', 'PRONTO_PARA_SEPARACAO', 'EM_SEPARACAO', 'SEPARADO', 'PENDENTE_RETIRADA', 'PENDENTE_DEVOLUCAO']
};

export const TIPOS_TECNICOS: Partial<Record<string, string>> = {
  '221': 'Adição comum',
  '921': 'Adição concreto',
  '222': 'Devolução comum',
  '922': 'Devolução concreto'
};

export const TIPOS_OBRA: Partial<Record<string, string>> = {
  OBRA: 'Obra',
  CORRETIVA: 'Corretiva',
  PREVENTIVA: 'Preventiva',
  LPT: 'LPT'
};

export function rotuloEtapa(etapa: EtapaSolicitacao | null): string {
  return etapa ? ETAPAS[etapa].rotulo : 'Sem etapa';
}

export function rotuloTipo(codigo: string | null): string {
  return codigo ? `${codigo} · ${TIPOS_TECNICOS[codigo] ?? 'Tipo técnico'}` : '—';
}

export function ehDevolucao(s: Pick<SolicitacaoDoPainel, 'tipoTecnico'>): boolean {
  return s.tipoTecnico === '222' || s.tipoTecnico === '922';
}

// ------------------------------------------------------------------ formatação

export function inteiro(valor: number | null | undefined): string {
  return valor === null || valor === undefined || !Number.isFinite(valor) ? '—' : Math.round(valor).toLocaleString('pt-BR');
}

export function percentual(valor: number | null | undefined, casas = 1): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—';
  return `${valor.toLocaleString('pt-BR', { maximumFractionDigits: casas })}%`;
}

export function quantidade(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—';
  return valor.toLocaleString('pt-BR', { maximumFractionDigits: 2 });
}

/** "45 min", "6h", "2,5 dias" — escolhe a unidade que se lê melhor. */
export function tempo(horas: number | null | undefined): string {
  if (horas === null || horas === undefined || !Number.isFinite(horas) || horas < 0) return '—';
  if (horas < 1) return `${Math.max(1, Math.round(horas * 60))} min`;
  if (horas < 48) return `${horas.toLocaleString('pt-BR', { maximumFractionDigits: horas < 10 ? 1 : 0 })}h`;
  return `${(horas / 24).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} dias`;
}

/** Variação com sinal explícito: "+12,5%", "−3%". */
export function variacao(valor: number | null | undefined): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return '—';
  const texto = Math.abs(valor).toLocaleString('pt-BR', { maximumFractionDigits: 1 });
  return valor > 0 ? `+${texto}%` : valor < 0 ? `−${texto}%` : '0%';
}

export function plural(n: number, um: string, varios: string): string {
  return `${n.toLocaleString('pt-BR')} ${n === 1 ? um : varios}`;
}

export function diaDe(iso: string): string {
  return DIA_ISO.format(new Date(iso));
}

export function diaMes(iso: string): string {
  // yyyy-MM-dd não passa por fuso; instantes passam pelo dia de São Paulo.
  if (/^\d{4}-\d{2}-\d{2}$/.test(iso)) return `${iso.substring(8, 10)}/${iso.substring(5, 7)}`;
  return DIA_MES.format(new Date(iso));
}

export function diaHora(iso: string | null | undefined): string {
  return iso ? DIA_HORA.format(new Date(iso)) : '—';
}

/** yyyy-MM-dd de uma data local, sem passar por UTC. */
export function isoLocal(data: Date): string {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

export function fatia(parte: number, total: number): number {
  return total > 0 ? Math.round((parte / total) * 1000) / 10 : 0;
}

// ------------------------------------------------------------------ classificação

export function situacao(s: Pick<SolicitacaoDoPainel, 'etapa'>): Situacao {
  if (s.etapa === 'CONCLUIDO') return 'concluida';
  if (s.etapa === 'REJEITADO' || s.etapa === 'CANCELADO') return 'recusada';
  return 'aberta';
}

/** Aberta com a data prevista de retirada/devolução já vencida. */
export function atrasada(s: SolicitacaoDoPainel, hoje: string): boolean {
  return situacao(s) === 'aberta' && !!s.dataPrevista && s.dataPrevista < hoje;
}

export function diasDeAtraso(s: SolicitacaoDoPainel, hoje: string): number {
  if (!atrasada(s, hoje)) return 0;
  return Math.round((Date.parse(`${hoje}T12:00:00`) - Date.parse(`${s.dataPrevista}T12:00:00`)) / DIA_MS);
}

function horasEntre(de: string | null, ate: string | null): number | null {
  if (!de || !ate) return null;
  const h = (Date.parse(ate) - Date.parse(de)) / HORA_MS;
  return h >= 0 ? h : null;
}

/** Da abertura à entrega/devolução final (só concluídas). */
export function cicloHoras(s: SolicitacaoDoPainel): number | null {
  return situacao(s) === 'concluida' ? horasEntre(s.dataSolicitacao, s.dataEntrega) : null;
}

/** Da abertura à aprovação/verificação. */
export function aprovacaoHoras(s: SolicitacaoDoPainel): number | null {
  return horasEntre(s.dataSolicitacao, s.dataVerificacao);
}

/** Do início ao fim da separação (técnico). */
export function separacaoHoras(s: SolicitacaoDoPainel): number | null {
  return horasEntre(s.separacaoIniciadaEm, s.separacaoConcluidaEm);
}

/** Da separação concluída até a retirada: material parado no almoxarifado. */
export function esperaRetiradaHoras(s: SolicitacaoDoPainel): number | null {
  return situacao(s) === 'concluida' ? horasEntre(s.separacaoConcluidaEm, s.dataEntrega) : null;
}

export function idadeDias(s: SolicitacaoDoPainel, agora: number): number {
  return Math.max(0, (agora - Date.parse(s.dataSolicitacao)) / DIA_MS);
}

export function mediana(valores: readonly number[]): number | null {
  if (!valores.length) return null;
  const ordenados = [...valores].sort((a, b) => a - b);
  const meio = Math.floor(ordenados.length / 2);
  return ordenados.length % 2 ? ordenados[meio] : (ordenados[meio - 1] + ordenados[meio]) / 2;
}

export function percentil(valores: readonly number[], p: number): number | null {
  if (!valores.length) return null;
  const ordenados = [...valores].sort((a, b) => a - b);
  return ordenados[Math.min(ordenados.length - 1, Math.max(0, Math.ceil((p / 100) * ordenados.length) - 1))];
}

export function media(valores: readonly number[]): number | null {
  return valores.length ? valores.reduce((t, v) => t + v, 0) / valores.length : null;
}

function definidos(valores: readonly (number | null)[]): number[] {
  return valores.filter((v): v is number => v !== null);
}

// ------------------------------------------------------------------ recorte

export interface Recorte {
  /** Criadas no período (base dos indicadores de volume, conclusão e ciclo). */
  readonly periodo: SolicitacaoDoPainel[];
  /** Todas as abertas agora: do período e do backlog anterior. */
  readonly abertas: SolicitacaoDoPainel[];
  readonly backlog: SolicitacaoDoPainel[];
  readonly materiais: MaterialDoPainel[];
  readonly anterior: ResumoPeriodoAnterior | null;
  readonly hoje: string;
  readonly agora: number;
}

export function filtrarModo<T extends { contexto: ProcessoSolicitacao }>(linhas: readonly T[], modo: ModoPainel): T[] {
  return modo === 'GERAL' ? [...linhas] : linhas.filter((l) => l.contexto === modo);
}

export function recortar(d: PainelSolicitacoesResponse, modo: ModoPainel): Recorte {
  const linhas = filtrarModo(d.solicitacoes, modo);
  const agora = Date.parse(d.geradoEm);
  const anteriores = filtrarModo(d.periodoAnterior, modo);
  const anterior: ResumoPeriodoAnterior | null = anteriores.length
    ? {
        contexto: modo === 'TECNICO' ? 'TECNICO' : 'COMERCIAL',
        total: anteriores.reduce((t, a) => t + a.total, 0),
        concluidas: anteriores.reduce((t, a) => t + a.concluidas, 0),
        rejeitadas: anteriores.reduce((t, a) => t + a.rejeitadas, 0),
        cicloMedioHoras: mediaPonderada(anteriores.map((a) => [a.cicloMedioHoras, a.concluidas])),
        aprovacaoMediaHoras: mediaPonderada(anteriores.map((a) => [a.aprovacaoMediaHoras, a.total]))
      }
    : null;
  return {
    periodo: linhas.filter((l) => !l.anteriorAoPeriodo),
    abertas: linhas.filter((l) => situacao(l) === 'aberta'),
    backlog: linhas.filter((l) => l.anteriorAoPeriodo),
    materiais: filtrarModo(d.materiais, modo),
    anterior,
    hoje: diaDe(d.geradoEm),
    agora
  };
}

function mediaPonderada(pares: [number | null, number][]): number | null {
  const validos = pares.filter(([v, p]) => v !== null && p > 0) as [number, number][];
  const peso = validos.reduce((t, [, p]) => t + p, 0);
  return peso ? validos.reduce((t, [v, p]) => t + v * p, 0) / peso : null;
}

// ------------------------------------------------------------------ indicadores

export interface Indicadores {
  readonly total: number;
  readonly concluidas: number;
  readonly abertas: number;
  readonly recusadas: number;
  /** Concluídas sobre as não recusadas (0–100). */
  readonly taxaConclusao: number | null;
  readonly taxaRecusa: number | null;
  readonly cicloMediana: number | null;
  readonly cicloMedia: number | null;
  readonly cicloP90: number | null;
  readonly aprovacaoMediana: number | null;
  readonly separacaoMediana: number | null;
  readonly esperaRetiradaMediana: number | null;
  /** Quantidade atendida sobre a solicitada nas concluídas (0–100). */
  readonly atendimento: number | null;
  readonly comPendencia: number;
  readonly foraPrazoSaque: number;
  readonly saqueForaEquipe: number;
  readonly itensPorSolicitacao: number | null;
  readonly porDia: number;
}

export function indicadores(linhas: readonly SolicitacaoDoPainel[], dias = 1): Indicadores {
  const concluidas = linhas.filter((l) => situacao(l) === 'concluida');
  const recusadas = linhas.filter((l) => situacao(l) === 'recusada').length;
  const decididas = linhas.length - recusadas;
  const ciclos = definidos(concluidas.map(cicloHoras));
  const solicitada = concluidas.reduce((t, l) => t + l.quantidadeSolicitada, 0);
  const atendida = concluidas.reduce((t, l) => t + l.quantidadeAtendida, 0);
  return {
    total: linhas.length,
    concluidas: concluidas.length,
    abertas: linhas.filter((l) => situacao(l) === 'aberta').length,
    recusadas,
    taxaConclusao: decididas > 0 ? fatia(concluidas.length, decididas) : null,
    taxaRecusa: linhas.length ? fatia(recusadas, linhas.length) : null,
    cicloMediana: mediana(ciclos),
    cicloMedia: media(ciclos),
    cicloP90: percentil(ciclos, 90),
    aprovacaoMediana: mediana(definidos(linhas.map(aprovacaoHoras))),
    separacaoMediana: mediana(definidos(linhas.map(separacaoHoras))),
    esperaRetiradaMediana: mediana(definidos(linhas.map(esperaRetiradaHoras))),
    atendimento: solicitada > 0 ? Math.min(100, fatia(atendida, solicitada)) : null,
    comPendencia: linhas.filter((l) => l.possuiPendencia).length,
    foraPrazoSaque: linhas.filter((l) => l.foraPrazoSaque).length,
    saqueForaEquipe: linhas.filter((l) => l.saqueForaEquipe).length,
    itensPorSolicitacao: linhas.length ? linhas.reduce((t, l) => t + l.itens, 0) / linhas.length : null,
    porDia: linhas.length / Math.max(1, dias)
  };
}

export function diasDoPeriodo(inicio: string, fim: string): number {
  return Math.max(1, Math.round((Date.parse(fim) - Date.parse(inicio)) / DIA_MS));
}

/** Variação percentual de `atual` sobre `anterior`; nula sem base. */
export function variacaoSobre(atual: number | null, anterior: number | null | undefined): number | null {
  if (atual === null || anterior === null || anterior === undefined || anterior === 0) return null;
  return Math.round(((atual - anterior) / anterior) * 1000) / 10;
}

// ------------------------------------------------------------------ séries e distribuições

export interface SerieDiaria {
  readonly dias: string[];
  readonly rotulos: string[];
  readonly concluidas: number[];
  readonly abertas: number[];
  readonly recusadas: number[];
  readonly comercial: number[];
  readonly tecnico: number[];
}

/** Criadas por dia do período (dias vazios entram zerados), com a situação atual de cada uma. */
export function serieDiaria(linhas: readonly SolicitacaoDoPainel[], inicio: string, fim: string): SerieDiaria {
  const dias: string[] = [];
  const ultimo = diaDe(fim);
  let cursor = Date.parse(`${diaDe(inicio)}T12:00:00-03:00`);
  for (let i = 0; i < 400; i++) {
    const dia = diaDe(new Date(cursor).toISOString());
    dias.push(dia);
    if (dia >= ultimo) break;
    cursor += DIA_MS;
  }
  const indice = new Map(dias.map((d, i) => [d, i]));
  const zeros = () => dias.map(() => 0);
  const serie = { concluidas: zeros(), abertas: zeros(), recusadas: zeros(), comercial: zeros(), tecnico: zeros() };
  for (const l of linhas) {
    const i = indice.get(diaDe(l.dataSolicitacao));
    if (i === undefined) continue;
    const s = situacao(l);
    if (s === 'concluida') serie.concluidas[i]++;
    else if (s === 'recusada') serie.recusadas[i]++;
    else serie.abertas[i]++;
    if (l.contexto === 'TECNICO') serie.tecnico[i]++;
    else serie.comercial[i]++;
  }
  return { dias, rotulos: dias.map(diaMes), ...serie };
}

export interface EtapaDoFluxo {
  readonly etapa: EtapaSolicitacao;
  readonly rotulo: string;
  readonly icone: string;
  readonly total: number;
  readonly atrasadas: number;
  readonly idadeMediana: number | null;
  readonly maisAntigaDias: number | null;
  readonly fatia: number;
}

/** Abertas por etapa, na ordem do fluxo: onde a fila está parada. */
export function fluxoDasAbertas(abertas: readonly SolicitacaoDoPainel[], modo: ModoPainel, hoje: string, agora: number): EtapaDoFluxo[] {
  const total = abertas.length;
  return ETAPAS_ABERTAS[modo]
    .map((etapa) => {
      const daEtapa = abertas.filter((l) => l.etapa === etapa);
      const idades = daEtapa.map((l) => idadeDias(l, agora));
      return {
        etapa,
        rotulo: ETAPAS[etapa].rotulo,
        icone: ETAPAS[etapa].icone,
        total: daEtapa.length,
        atrasadas: daEtapa.filter((l) => atrasada(l, hoje)).length,
        idadeMediana: mediana(idades),
        maisAntigaDias: idades.length ? Math.max(...idades) : null,
        fatia: fatia(daEtapa.length, total)
      };
    })
    .filter((e) => modo !== 'COMERCIAL' || e.total > 0 || e.etapa === 'PENDENTE_APROVACAO' || e.etapa === 'PENDENTE_RETIRADA');
}

export interface FaixaIdade {
  readonly id: string;
  readonly rotulo: string;
  readonly de: number;
  readonly ate: number;
  readonly total: number;
  readonly atrasadas: number;
  readonly tom: CesTone;
}

export const FAIXAS_IDADE: readonly { id: string; rotulo: string; de: number; ate: number; tom: CesTone }[] = [
  { id: 'ate-1', rotulo: 'Até 1 dia', de: 0, ate: 1, tom: 'success' },
  { id: '1-3', rotulo: '1 a 3 dias', de: 1, ate: 3, tom: 'info' },
  { id: '3-7', rotulo: '3 a 7 dias', de: 3, ate: 7, tom: 'warning' },
  { id: '7-15', rotulo: '7 a 15 dias', de: 7, ate: 15, tom: 'warning' },
  { id: '15-30', rotulo: '15 a 30 dias', de: 15, ate: 30, tom: 'danger' },
  { id: '30+', rotulo: 'Mais de 30 dias', de: 30, ate: Infinity, tom: 'danger' }
];

/** Aging das abertas: há quanto tempo cada uma foi criada. */
export function idadeDasAbertas(abertas: readonly SolicitacaoDoPainel[], hoje: string, agora: number): FaixaIdade[] {
  return FAIXAS_IDADE.map((f) => {
    const daFaixa = abertas.filter((l) => {
      const idade = idadeDias(l, agora);
      return idade >= f.de && idade < f.ate;
    });
    return { ...f, total: daFaixa.length, atrasadas: daFaixa.filter((l) => atrasada(l, hoje)).length };
  });
}

export const FAIXAS_CICLO: readonly { id: string; rotulo: string; de: number; ate: number }[] = [
  { id: 'ate-4h', rotulo: 'Até 4h', de: 0, ate: 4 },
  { id: '4-24h', rotulo: '4h a 1 dia', de: 4, ate: 24 },
  { id: '1-2d', rotulo: '1 a 2 dias', de: 24, ate: 48 },
  { id: '2-3d', rotulo: '2 a 3 dias', de: 48, ate: 72 },
  { id: '3-7d', rotulo: '3 a 7 dias', de: 72, ate: 168 },
  { id: '7d+', rotulo: 'Mais de 7 dias', de: 168, ate: Infinity }
];

export function distribuicaoDoCiclo(linhas: readonly SolicitacaoDoPainel[]): number[] {
  const ciclos = definidos(linhas.map(cicloHoras));
  return FAIXAS_CICLO.map((f) => ciclos.filter((h) => h >= f.de && h < f.ate).length);
}

// ------------------------------------------------------------------ rankings

export interface LinhaRanking {
  readonly chave: string;
  readonly rotulo: string;
  readonly detalhe: string | null;
  readonly total: number;
  readonly concluidas: number;
  readonly abertas: number;
  readonly atrasadas: number;
  readonly recusadas: number;
  readonly comPendencia: number;
  readonly taxaConclusao: number | null;
  readonly cicloMediana: number | null;
  readonly atendimento: number | null;
  readonly fatia: number;
}

export const SEM_CHAVE = 'sem';

/**
 * Ranking por uma dimensão (equipe, almoxarifado, obra). Considera as criadas no período e, para
 * abertas e atrasadas, também o backlog — é a fila que cada um carrega hoje.
 */
export function ranking(
  periodo: readonly SolicitacaoDoPainel[],
  backlog: readonly SolicitacaoDoPainel[],
  hoje: string,
  chave: (s: SolicitacaoDoPainel) => string | null,
  rotulo: (s: SolicitacaoDoPainel) => string | null,
  detalhe: (s: SolicitacaoDoPainel) => string | null = () => null,
  rotuloVazio = 'Não informado'
): LinhaRanking[] {
  const grupos = new Map<string, { periodo: SolicitacaoDoPainel[]; backlog: SolicitacaoDoPainel[]; exemplo: SolicitacaoDoPainel }>();
  const incluir = (l: SolicitacaoDoPainel, onde: 'periodo' | 'backlog') => {
    const k = chave(l) ?? SEM_CHAVE;
    const g = grupos.get(k) ?? { periodo: [], backlog: [], exemplo: l };
    g[onde].push(l);
    grupos.set(k, g);
  };
  periodo.forEach((l) => incluir(l, 'periodo'));
  backlog.forEach((l) => incluir(l, 'backlog'));
  const total = periodo.length;
  return [...grupos.entries()]
    .map(([k, g]) => {
      const ind = indicadores(g.periodo);
      const abertasAgora = [...g.periodo, ...g.backlog].filter((l) => situacao(l) === 'aberta');
      return {
        chave: k,
        rotulo: k === SEM_CHAVE ? rotuloVazio : (rotulo(g.exemplo) ?? rotuloVazio),
        detalhe: k === SEM_CHAVE ? null : detalhe(g.exemplo),
        total: ind.total,
        concluidas: ind.concluidas,
        abertas: abertasAgora.length,
        atrasadas: abertasAgora.filter((l) => atrasada(l, hoje)).length,
        recusadas: ind.recusadas,
        comPendencia: ind.comPendencia,
        taxaConclusao: ind.taxaConclusao,
        cicloMediana: ind.cicloMediana,
        atendimento: ind.atendimento,
        fatia: fatia(ind.total, total)
      };
    })
    .sort((a, b) => b.total - a.total || b.abertas - a.abertas || a.rotulo.localeCompare(b.rotulo, 'pt-BR'));
}

export interface Contagem {
  readonly rotulo: string;
  readonly total: number;
  readonly fatia: number;
}

export function contar(valores: readonly (string | null)[], vazio?: string): Contagem[] {
  const mapa = new Map<string, number>();
  for (const v of valores) {
    const k = v ?? vazio;
    if (k) mapa.set(k, (mapa.get(k) ?? 0) + 1);
  }
  const total = [...mapa.values()].reduce((t, n) => t + n, 0);
  return [...mapa.entries()]
    .map(([rotulo, n]) => ({ rotulo, total: n, fatia: fatia(n, total) }))
    .sort((a, b) => b.total - a.total || a.rotulo.localeCompare(b.rotulo, 'pt-BR'));
}

/** Motivos de pendência: cada solicitação conta uma vez por motivo. */
export function motivosDePendencia(linhas: readonly SolicitacaoDoPainel[]): Contagem[] {
  return contar(linhas.filter((l) => l.possuiPendencia).flatMap((l) => (l.motivosPendencia ? l.motivosPendencia.split(', ') : ['Não informado'])));
}

export interface LinhaMaterial extends MaterialDoPainel {
  readonly atendimento: number | null;
  readonly saldo: number;
}

export function materiais(linhas: readonly MaterialDoPainel[], sentido: 'SAIDA' | 'DEVOLUCAO' = 'SAIDA'): LinhaMaterial[] {
  // No geral, o mesmo material pode vir dos dois processos: soma por código.
  const mapa = new Map<string, LinhaMaterial>();
  for (const m of linhas.filter((l) => l.sentido === sentido)) {
    const atual = mapa.get(m.codigo);
    const solicitacoes = (atual?.solicitacoes ?? 0) + m.solicitacoes;
    const solicitada = (atual?.quantidadeSolicitada ?? 0) + m.quantidadeSolicitada;
    const atendida = (atual?.quantidadeAtendida ?? 0) + m.quantidadeAtendida;
    mapa.set(m.codigo, {
      ...m,
      solicitacoes,
      quantidadeSolicitada: solicitada,
      quantidadeAtendida: atendida,
      atendimento: solicitada > 0 ? Math.min(100, fatia(atendida, solicitada)) : null,
      saldo: Math.max(0, solicitada - atendida)
    });
  }
  return [...mapa.values()].sort((a, b) => b.solicitacoes - a.solicitacoes || b.quantidadeSolicitada - a.quantidadeSolicitada);
}

/** Abertas que pedem ação: atrasadas primeiro (mais dias de atraso), depois as mais antigas. */
export function filaDeAtencao(abertas: readonly SolicitacaoDoPainel[], hoje: string, agora: number, limite = 8): SolicitacaoDoPainel[] {
  return [...abertas]
    .sort((a, b) => diasDeAtraso(b, hoje) - diasDeAtraso(a, hoje) || idadeDias(b, agora) - idadeDias(a, agora))
    .slice(0, limite);
}

export function rotaDaSolicitacao(s: Pick<SolicitacaoDoPainel, 'id' | 'contexto'>): string[] {
  return s.contexto === 'TECNICO' ? ['/almoxarifado/solicitacoes-tecnicas', s.id] : ['/almoxarifado/solicitacoes', s.id];
}
