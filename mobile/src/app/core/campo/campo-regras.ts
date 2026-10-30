import { ExecucaoCampoLocal, ExecucaoMaterialLocal, MetaDoCicloLocal, TipoNota } from '../db/models';

/**
 * Regras locais do lançamento de campo, sem dependência de Angular/IndexedDB para
 * poderem ser testadas isoladamente e reaproveitadas pelas telas.
 *
 * Aplicar material fora do saldo nunca é bloqueado: o saldo da equipe apenas fica
 * negativo. As faltas calculadas aqui servem só para avisar o encarregado.
 */

/** Quanto do material aplicado não estava no saldo da equipe (aviso, não bloqueia). */
export interface FaltaSaldo {
  idMaterial: string;
  quantidade: number;
  numSerie?: string;
}

/** Retrato do saldo da equipe no dispositivo. */
export interface SaldoEquipeSnapshot {
  /** Quantidade NOVA disponível por material. */
  novoPorMaterial: ReadonlyMap<string, number>;
  /** Materiais com qualquer saldo positivo (NOVO ou SUCATA). Informativo. */
  materiaisComSaldo: ReadonlySet<string>;
  /** Chaves `idMaterial|numSerie` das séries NOVAS no estoque da equipe. */
  seriesNovas: ReadonlySet<string>;
}

export function chaveSerie(idMaterial: string, numSerie: string): string {
  return `${idMaterial}|${numSerie.trim().toUpperCase()}`;
}

/**
 * Calcula o que o lançamento aplica além do saldo da equipe (quantidade acima do
 * saldo ou série que não está no saldo). Retirada não entra: o material vai no
 * veículo e a equipe o devolve quando puder.
 */
export function preverFaltasDeSaldo(
  materiais: readonly ExecucaoMaterialLocal[],
  saldo: SaldoEquipeSnapshot
): FaltaSaldo[] {
  const faltas: FaltaSaldo[] = [];
  const aplicadoPorMaterial = new Map<string, number>();

  for (const m of materiais) {
    if (!m.idMaterial || !(m.quantidade > 0)) continue;

    if (m.operacao !== 'APLICADO') continue;
    // Material da Concessionária é só registro: não sai do caminhão.
    if (!movimentaSaldo(m)) continue;

    if (m.numSerie) {
      if (!saldo.seriesNovas.has(chaveSerie(m.idMaterial, m.numSerie))) {
        faltas.push({ idMaterial: m.idMaterial, quantidade: m.quantidade, numSerie: m.numSerie });
      }
    } else {
      aplicadoPorMaterial.set(m.idMaterial, (aplicadoPorMaterial.get(m.idMaterial) ?? 0) + m.quantidade);
    }
  }

  for (const [idMaterial, aplicado] of aplicadoPorMaterial) {
    const disponivel = Math.max(0, saldo.novoPorMaterial.get(idMaterial) ?? 0);
    const faltante = arredondar(aplicado - disponivel);
    if (faltante > 0) faltas.push({ idMaterial, quantidade: faltante });
  }

  return faltas;
}

/** Material que mexe no saldo da equipe (o da Concessionária fica só no registro da ocorrência). */
export function movimentaSaldo(m: Pick<ExecucaoMaterialLocal, 'origem'>): boolean {
  return (m.origem ?? 'EQUIPE') === 'EQUIPE';
}

/** Como o lançamento é chamado na tela: "Nota 123", "Nota avulsa AV-…", "Ocorrência OC-12". */
export function rotuloNota(e: { referenciaNota: string; tipoNota?: TipoNota }): string {
  switch (e.tipoNota) {
    case 'AVULSA': return `Nota avulsa ${e.referenciaNota}`;
    case 'OCORRENCIA': return `Ocorrência ${e.referenciaNota}`;
    default: return `Nota ${e.referenciaNota}`;
  }
}

/**
 * Número provisório da nota avulsa: `AV-<equipe>-<AAMMDD>-<sequência do dia>`. É gerado
 * no aparelho (funciona offline) e segue a maior sequência já usada no dia.
 */
export function gerarNumeroAvulsa(codigoEquipe: string | null | undefined, usados: readonly string[], agora: Date = new Date()): string {
  const equipe = (codigoEquipe || 'EQ').replace(/[^0-9A-Za-z]/g, '').toUpperCase() || 'EQ';
  const dia = `${String(agora.getFullYear()).slice(2)}${String(agora.getMonth() + 1).padStart(2, '0')}${String(agora.getDate()).padStart(2, '0')}`;
  const prefixo = `AV-${equipe}-${dia}-`;
  const maior = usados
    .filter(n => n?.startsWith(prefixo))
    .map(n => Number(n.slice(prefixo.length)))
    .filter(n => Number.isFinite(n))
    .reduce((a, b) => Math.max(a, b), 0);
  return `${prefixo}${String(maior + 1).padStart(2, '0')}`;
}

/** Horário de corte diário do servidor: turno ainda aberto nele é encerrado sozinho. */
const HORA_CORTE_TURNO = 22;
/** Horário de Brasília (as bases não têm horário de verão). */
const FUSO_BASES_MS = -3 * 60 * 60 * 1000;
const UM_DIA_MS = 24 * 60 * 60 * 1000;

/** Primeiro corte depois da abertura: o turno vale até ele (mesma conta do servidor). */
export function corteDoTurno(dataInicio: string): Date {
  const abertura = new Date(dataInicio).getTime();
  const local = new Date(abertura + FUSO_BASES_MS);
  let corte = Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate(), HORA_CORTE_TURNO) - FUSO_BASES_MS;
  if (corte <= abertura) corte += UM_DIA_MS;
  return new Date(corte);
}

/**
 * O turno passou do corte: o servidor já o encerrou e recusa lançamentos feitos
 * depois dele. Vale mesmo sem sinal, porque o corte é o mesmo todo dia.
 */
export function turnoVencido(dataInicio: string, agora: Date = new Date()): boolean {
  return agora.getTime() > corteDoTurno(dataInicio).getTime();
}

/**
 * Linha da nota que já usa a série. O servidor recusa a mesma série duas vezes na
 * execução, em qualquer material ou operação (ex.: aplicado e retirado); quem chama
 * passa as outras linhas da nota.
 */
export function linhaComSerie<T extends { numSeries: readonly string[] }>(linhas: readonly T[], numSerie: string): T | null {
  const serie = numSerie.trim().toUpperCase();
  return linhas.find(l => l.numSeries.some(s => s.trim().toUpperCase() === serie)) ?? null;
}

/** Feriados nacionais de data fixa, em `MM-DD` (mesma lista do backend). */
const FERIADOS_FIXOS = new Set(['01-01', '04-21', '05-01', '09-07', '10-12', '11-02', '11-15', '11-20', '12-25']);

/** Domingo de Páscoa (algoritmo de Meeus/Jones/Butcher). */
function pascoa(ano: number): Date {
  const a = ano % 19, b = Math.floor(ano / 100), c = ano % 100;
  const d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25), g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4), k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const n = h + l - 7 * m + 114;
  return new Date(ano, Math.floor(n / 31) - 1, (n % 31) + 1);
}

/** Feriado nacional: datas fixas e Sexta-feira Santa (Carnaval e Corpus Christi são pontos facultativos). */
export function feriadoNacional(d: Date): boolean {
  const mmdd = `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  if (FERIADOS_FIXOS.has(mmdd)) return true;
  const p = pascoa(d.getFullYear());
  const sextaSanta = new Date(p.getFullYear(), p.getMonth(), p.getDate() - 2);
  return d.getMonth() === sextaSanta.getMonth() && d.getDate() === sextaSanta.getDate();
}

/** Dia útil: segunda a sexta, fora dos feriados nacionais. */
function diaUtil(d: Date): boolean {
  return d.getDay() !== 0 && d.getDay() !== 6 && !feriadoNacional(d);
}

/**
 * Calcula a quantidade de dias úteis em determinado mês e ano,
 * excluindo sábados, domingos e feriados nacionais.
 *
 * @param ano Ano de referência (ex.: 2026)
 * @param mes Mês de referência de 1 a 12 (1 = Janeiro, 12 = Dezembro)
 */
export function calcularDiasUteisNoMes(ano: number, mes: number): number {
  const totalDias = new Date(ano, mes, 0).getDate();
  let diasUteis = 0;
  for (let dia = 1; dia <= totalDias; dia++) {
    if (diaUtil(new Date(ano, mes - 1, dia))) {
      diasUteis++;
    }
  }
  return diasUteis;
}

/**
 * Calcula a meta diária padrão dividindo a meta mensal pelo total de dias úteis
 * no mês (excluindo sábados, domingos e feriados nacionais).
 */
export function calcularMetaDiariaPadrao(
  metaMensal: number | null | undefined,
  agora: Date = new Date()
): number | null {
  if (typeof metaMensal !== 'number' || metaMensal <= 0) return null;
  const diasUteis = calcularDiasUteisNoMes(agora.getFullYear(), agora.getMonth() + 1);
  if (diasUteis === 0) return null;
  return arredondar(metaMensal / diasUteis, 2);
}

export interface ProgressoMeta {
  /** Mês de referência no formato `AAAA-MM`. */
  competencia: string;
  /** Data do dia de referência no formato `AAAA-MM-DD`. */
  dataReferencia: string;
  /** Meta diária da equipe (null se não houver meta cadastrada). */
  meta: number | null;
  /** Soma do valor faturado das execuções de hoje (tanto sincronizadas quanto locais/offline). */
  executado: number;
  /** Quanto falta para a meta do dia (0 quando atingida; null sem meta). */
  falta: number | null;
  /** Percentual atingido da meta diária, limitado a 100 para a barra (null sem meta). */
  percentual: number | null;
  atingida: boolean;
  /** Total de execuções de campo realizadas hoje. */
  quantidadeExecucoes: number;
  /** Execuções de hoje que ainda não foram enviadas ao servidor. */
  aguardandoEnvio: number;
}

/**
 * Progresso da equipe na meta diária, calculado com os dados locais do aparelho.
 * Permite que a equipe acompanhe o faturamento realizado no dia mesmo trabalhando
 * offline o dia inteiro.
 */
export function calcularProgressoMeta(
  execucoes: readonly ExecucaoCampoLocal[],
  metaDiaria: number | null | undefined,
  agora: Date = new Date(),
  idTurnoAtivo?: string | null
): ProgressoMeta {
  const ano = agora.getFullYear();
  const mes = agora.getMonth();
  const dia = agora.getDate();
  const competencia = `${ano}-${String(mes + 1).padStart(2, '0')}`;
  const dataReferencia = `${competencia}-${String(dia).padStart(2, '0')}`;

  const doDia = execucoes.filter(e => {
    if (idTurnoAtivo && e.idTurno === idTurnoAtivo) {
      return true;
    }
    const data = new Date(e.dataLancamento);
    return !Number.isNaN(data.getTime())
      && data.getFullYear() === ano
      && data.getMonth() === mes
      && data.getDate() === dia;
  });

  const sincronizadas = doDia.filter(e => e.statusSync === 'SINCRONIZADO');
  const executado = arredondar(
    doDia.reduce((total, e) => total + (Number(e.valorTotalFaturado) || 0), 0),
    2
  );
  const metaValida = typeof metaDiaria === 'number' && metaDiaria > 0 ? metaDiaria : null;

  return {
    competencia,
    dataReferencia,
    meta: metaValida,
    executado,
    falta: metaValida === null ? null : arredondar(Math.max(0, metaValida - executado), 2),
    percentual: metaValida === null ? null : Math.min(100, Math.round((executado / metaValida) * 100)),
    atingida: metaValida !== null && executado >= metaValida,
    quantidadeExecucoes: doDia.length,
    aguardandoEnvio: doDia.length - sincronizadas.length,
  };
}

export type SituacaoCiclo = 'batida' | 'no-ritmo' | 'abaixo' | 'desatualizada';

export interface ProgressoCiclo {
  periodicidade: MetaDoCicloLocal['periodicidade'];
  /** Ex.: "Ciclo de outubro" ou "Meta de outubro". */
  titulo: string;
  inicio: string;
  fim: string;
  meta: number;
  /** Servidor (até a última atualização) + lançamentos que ainda não chegaram lá. */
  realizado: number;
  /** Parte de `realizado` que só existe no aparelho. */
  pendenteEnvio: number;
  falta: number;
  /** Percentual atingido, limitado a 100 para a barra. */
  percentual: number;
  /** Quanto já deveria ter sido feito até ontem para fechar no ritmo. */
  esperadoAteOntem: number;
  diasUteis: number;
  /** Dias úteis de hoje até o fim do ciclo (hoje incluído). */
  diasUteisRestantes: number;
  /** Quanto fazer por dia útil restante para bater a meta (null sem dias restantes). */
  ritmoNecessario: number | null;
  situacao: SituacaoCiclo;
}

const NOMES_MES = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto',
  'setembro', 'outubro', 'novembro', 'dezembro'];

/** `AAAA-MM-DD` como data local (sem o deslocamento de fuso do `new Date(iso)`). */
function dataLocal(iso: string): Date {
  const [ano, mes, dia] = iso.slice(0, 10).split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

function inicioDoDia(data: Date): Date {
  return new Date(data.getFullYear(), data.getMonth(), data.getDate());
}

/** Dias úteis (segunda a sexta, menos feriados nacionais) em `[inicio, fim]`; zero quando o intervalo é vazio. */
export function contarDiasUteis(inicio: Date, fim: Date): number {
  let total = 0;
  for (let d = inicioDoDia(inicio); d <= fim; d = new Date(d.getFullYear(), d.getMonth(), d.getDate() + 1)) {
    if (diaUtil(d)) total++;
  }
  return total;
}

/**
 * Progresso da equipe na meta do ciclo (26 a 25) ou do mês. Parte do realizado que
 * o servidor apurou e soma o que ainda está só no aparelho — lançamentos não enviados
 * ou enviados depois da apuração —, para a conta seguir certa trabalhando offline.
 * Passado o fim do ciclo sem nova sincronização, a situação vira `desatualizada`.
 */
export function calcularProgressoCiclo(
  meta: MetaDoCicloLocal | null | undefined,
  execucoes: readonly ExecucaoCampoLocal[],
  agora: Date = new Date()
): ProgressoCiclo | null {
  if (!meta || !(meta.valor > 0)) return null;

  const inicio = dataLocal(meta.inicio);
  const fim = dataLocal(meta.fim);
  const fimExclusivo = new Date(fim.getFullYear(), fim.getMonth(), fim.getDate() + 1);
  const apuradoEm = new Date(meta.apuradoEm).getTime();

  const pendenteEnvio = arredondar(execucoes
    .filter(e => {
      const data = new Date(e.dataLancamento);
      if (Number.isNaN(data.getTime()) || data < inicio || data >= fimExclusivo) return false;
      if (e.statusSync === 'LOCAL' || e.statusSync === 'ERRO') return true;
      // Já chegou ao servidor, mas depois da última apuração.
      return e.statusSync === 'SINCRONIZADO' && data.getTime() > apuradoEm;
    })
    .reduce((total, e) => total + (Number(e.valorTotalFaturado) || 0), 0), 2);

  const realizado = arredondar(meta.realizado + pendenteEnvio, 2);
  const hoje = inicioDoDia(agora);
  const ontem = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() - 1);
  const diasUteis = meta.diasUteis || contarDiasUteis(inicio, fim);
  const decorridos = contarDiasUteis(inicio, ontem < fim ? ontem : fim);
  const restantes = contarDiasUteis(hoje > inicio ? hoje : inicio, fim);
  const falta = arredondar(Math.max(0, meta.valor - realizado), 2);
  const esperadoAteOntem = diasUteis ? arredondar(meta.valor * decorridos / diasUteis, 2) : 0;

  let situacao: SituacaoCiclo;
  if (hoje > fim) situacao = 'desatualizada';
  else if (realizado >= meta.valor) situacao = 'batida';
  else if (realizado >= esperadoAteOntem) situacao = 'no-ritmo';
  else situacao = 'abaixo';

  const [, mes] = meta.competencia.split('-').map(Number);
  const nomeMes = NOMES_MES[(mes || inicio.getMonth() + 1) - 1] ?? '';

  return {
    periodicidade: meta.periodicidade,
    titulo: meta.periodicidade === 'CICLO_COMERCIAL' ? `Ciclo de ${nomeMes}` : `Meta de ${nomeMes}`,
    inicio: meta.inicio,
    fim: meta.fim,
    meta: meta.valor,
    realizado,
    pendenteEnvio,
    falta,
    percentual: Math.min(100, Math.round((realizado / meta.valor) * 100)),
    esperadoAteOntem,
    diasUteis,
    diasUteisRestantes: restantes,
    ritmoNecessario: restantes > 0 && falta > 0 ? arredondar(falta / restantes, 2) : null,
    situacao,
  };
}

function arredondar(valor: number, casas = 3): number {
  const fator = 10 ** casas;
  return Math.round(valor * fator) / fator;
}


/** A equipe altera uma nota já sincronizada até 7 dias depois do lançamento; depois, só o escritório. */
export const PRAZO_ALTERACAO_CAMPO_DIAS = 7;

/** Nota já no servidor, da equipe, dentro do prazo do campo. */
export function podeAlterarNoCampo(
  exec: Pick<ExecucaoCampoLocal, 'statusSync' | 'dataLancamento' | 'idEquipe'>,
  idEquipe: string | null | undefined,
  agora = new Date(),
): boolean {
  if (exec.statusSync !== 'SINCRONIZADO') return false;
  if (idEquipe && exec.idEquipe && exec.idEquipe !== idEquipe) return false;
  return agora.getTime() - new Date(exec.dataLancamento).getTime() <= PRAZO_ALTERACAO_CAMPO_DIAS * 86_400_000;
}

/** Dias que ainda restam para a equipe alterar a nota (0 = vence hoje). */
export function diasParaAlterar(dataLancamento: string, agora = new Date()): number {
  const limite = new Date(dataLancamento).getTime() + PRAZO_ALTERACAO_CAMPO_DIAS * 86_400_000;
  return Math.max(0, Math.floor((limite - agora.getTime()) / 86_400_000));
}

function chaveMaterialNota(m: ExecucaoMaterialLocal): string {
  return `${m.idMaterial}|${m.operacao}|${(m.numSerie ?? '').toUpperCase()}|${m.origem ?? 'EQUIPE'}`;
}

/**
 * O que entrou e o que saiu da nota entre duas versões, pelo mesmo critério do
 * servidor (material, operação, série e origem; quantidade pela diferença).
 */
export function diferencaDaNota(
  antes: { acoes: string[]; materiais: ExecucaoMaterialLocal[] },
  depois: { acoes: string[]; materiais: ExecucaoMaterialLocal[] },
): { acoesAdicionadas: string[]; acoesRemovidas: string[]; materiaisAdicionados: ExecucaoMaterialLocal[]; materiaisRemovidos: ExecucaoMaterialLocal[] } {
  const agrupar = (lista: ExecucaoMaterialLocal[]) => {
    const grupos = new Map<string, ExecucaoMaterialLocal>();
    for (const m of lista) {
      const k = chaveMaterialNota(m);
      const atual = grupos.get(k);
      grupos.set(k, atual ? { ...atual, quantidade: atual.quantidade + m.quantidade } : { ...m });
    }
    return grupos;
  };
  const a = agrupar(antes.materiais);
  const d = agrupar(depois.materiais);
  const materiaisRemovidos: ExecucaoMaterialLocal[] = [];
  const materiaisAdicionados: ExecucaoMaterialLocal[] = [];
  for (const [k, m] of a) {
    const nova = d.get(k)?.quantidade ?? 0;
    if (nova < m.quantidade) materiaisRemovidos.push({ ...m, quantidade: Math.round((m.quantidade - nova) * 1000) / 1000 });
  }
  for (const [k, m] of d) {
    const anterior = a.get(k)?.quantidade ?? 0;
    if (m.quantidade > anterior) materiaisAdicionados.push({ ...m, quantidade: Math.round((m.quantidade - anterior) * 1000) / 1000 });
  }
  return {
    acoesAdicionadas: depois.acoes.filter(id => !antes.acoes.includes(id)),
    acoesRemovidas: antes.acoes.filter(id => !depois.acoes.includes(id)),
    materiaisAdicionados,
    materiaisRemovidos,
  };
}
