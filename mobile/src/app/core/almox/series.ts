/**
 * Números de série: normalização, lote colado e sequência. Regras puras (sem Angular)
 * para a captura em massa e os testes.
 */

export const TAMANHO_MAXIMO_SERIE = 40;
/** Teto de uma sequência gerada de uma vez (evita travar o aparelho com um erro de digitação). */
export const LIMITE_SEQUENCIA = 2000;

/** Série como o backend guarda: maiúscula, sem espaços nas pontas nem caracteres de controle. */
export function normalizarSerie(valor: string): string {
  return (valor ?? '').replace(/[\u0000-\u001f\u007f]/g, '').trim().toUpperCase();
}

/** Quebra um texto colado (linhas, vírgula, ponto e vírgula, tabulação) em séries normalizadas. */
export function separarLote(texto: string): string[] {
  return (texto ?? '').split(/[\n\r,;\t]+/).map(normalizarSerie).filter(Boolean);
}

export interface ResultadoAdicao {
  /** Lista final, na ordem de chegada. */
  series: string[];
  novas: string[];
  repetidas: string[];
  recusadas: { serie: string; motivo: string }[];
}

/**
 * Acumula séries sem repetir. `validar` devolve o motivo de recusa (ou null) — ex.: a série
 * não está no saldo da origem. `limite` corta o que passar da quantidade esperada.
 */
export function acumular(
  atuais: readonly string[],
  entrada: readonly string[],
  opts: { validar?: (serie: string) => string | null; limite?: number | null } = {},
): ResultadoAdicao {
  const series = [...atuais];
  const vistas = new Set(atuais);
  const novas: string[] = [];
  const repetidas: string[] = [];
  const recusadas: { serie: string; motivo: string }[] = [];
  for (const bruta of entrada) {
    const serie = normalizarSerie(bruta);
    if (!serie) continue;
    if (vistas.has(serie)) { repetidas.push(serie); continue; }
    if (serie.length > TAMANHO_MAXIMO_SERIE) { recusadas.push({ serie, motivo: `passa de ${TAMANHO_MAXIMO_SERIE} caracteres` }); continue; }
    const motivo = opts.validar?.(serie) ?? null;
    if (motivo) { recusadas.push({ serie, motivo }); continue; }
    if (opts.limite != null && series.length >= opts.limite) { recusadas.push({ serie, motivo: 'passa da quantidade' }); continue; }
    vistas.add(serie);
    series.push(serie);
    novas.push(serie);
  }
  return { series, novas, repetidas, recusadas };
}

/**
 * Sequência entre duas séries com o mesmo prefixo e a parte final numérica:
 * `ABC0098` → `ABC0102` gera ABC0098, ABC0099, ABC0100, ABC0101, ABC0102 (mantém os zeros).
 */
export function gerarSequencia(inicio: string, fim: string): { series: string[]; erro: string | null } {
  const a = normalizarSerie(inicio);
  const b = normalizarSerie(fim);
  if (!a || !b) return { series: [], erro: 'Informe a primeira e a última série.' };
  const ma = /^(.*?)(\d+)$/.exec(a);
  const mb = /^(.*?)(\d+)$/.exec(b);
  if (!ma || !mb) return { series: [], erro: 'As duas séries precisam terminar em número.' };
  if (ma[1] !== mb[1]) return { series: [], erro: `O começo das séries é diferente (${ma[1] || 'vazio'} e ${mb[1] || 'vazio'}).` };
  const ini = BigInt(ma[2]);
  const fi = BigInt(mb[2]);
  if (fi < ini) return { series: [], erro: 'A última série precisa ser maior que a primeira.' };
  const total = fi - ini + 1n;
  if (total > BigInt(LIMITE_SEQUENCIA)) return { series: [], erro: `São ${total} séries; o máximo de uma vez é ${LIMITE_SEQUENCIA}.` };
  const largura = Math.max(ma[2].length, mb[2].length);
  const series: string[] = [];
  for (let n = ini; n <= fi; n++) series.push(ma[1] + n.toString().padStart(largura, '0'));
  return { series, erro: null };
}

/** "1 série" / "12 séries". */
export function contarSeries(n: number): string {
  return `${n} ${n === 1 ? 'série' : 'séries'}`;
}

// ---------- leitura de etiquetas ----------

/** Identificador de simbologia AIM (`]C1`, `]d2`, `]Q3`…) que alguns leitores mandam na frente do código. */
const PREFIXO_AIM = /^\][A-Za-z][0-9A-Za-z]/;
/** AIM que indica conteúdo GS1. */
const AIM_GS1 = /^\](C1|d2|Q3|e0|J1)/;
const GS = '\u001d';
/** AIs GS1 de tamanho fixo (sem o separador GS depois). */
const AI_FIXO: Record<string, number> = {
  '00': 18, '01': 14, '02': 14, '03': 14, '04': 16, '11': 6, '12': 6, '13': 6, '14': 6, '15': 6, '16': 6, '17': 6, '18': 6, '19': 6, '20': 2,
};
const AI_SERIE = '21';
/** Chave de série em QR com "campo: valor" ou "campo=valor" (S/N, SN, SERIAL, Nº SÉRIE, NS…). */
const CHAVE_SERIE = /(?:^|[\s;,|&?{"'])(?:S\s*\/\s*N|SN|SERIAL(?:\s*(?:NO|N[º°]|NUMBER|NUMERO|NÚMERO))?|N[º°.]?\s*(?:DE\s*)?S[EÉ]RIE|NUM(?:ERO)?[_\s]?S[EÉ]RIE|NS)["']?\s*[:=#]\s*["']?([A-Z0-9][A-Z0-9\-_./]*)/i;
const PARAMETROS_URL = ['sn', 'serial', 'serie', 'ns', 'numserie', 'num_serie'];

/** Tenta ler o AI 21 (série) de um conteúdo GS1 sem parênteses. */
function serieGs1(dados: string): string | null {
  let i = 0;
  while (i < dados.length) {
    if (dados[i] === GS) { i++; continue; }
    const ai2 = dados.slice(i, i + 2);
    if (ai2 in AI_FIXO) { i += 2 + AI_FIXO[ai2]; continue; }
    // 31xx–36xx: medidas com 4 dígitos de AI e 6 de valor.
    if (/^3[1-6]\d\d$/.test(dados.slice(i, i + 4))) { i += 10; continue; }
    // Variáveis: vão até o GS ou o fim. 21 é a série; 10, 22, 240, 241, 250, 251, 30, 37, 9x… são pulados.
    const fim = dados.indexOf(GS, i);
    const ate = fim === -1 ? dados.length : fim;
    if (ai2 === AI_SERIE) return dados.slice(i + 2, ate) || null;
    const ai3 = dados.slice(i, i + 3);
    const tamanhoAi = ['240', '241', '250', '251', '253', '254'].includes(ai3) ? 3 : 2;
    if (!/^\d+$/.test(dados.slice(i, i + tamanhoAi))) return null;
    i = ate;
  }
  return null;
}

/**
 * Tira a série do que o leitor entregou: etiqueta GS1 (AI 21, com ou sem parênteses), QR com
 * "S/N: …", link com `?sn=…` ou, no caso comum, o próprio código. Sempre normalizada.
 */
export function extrairSerie(bruto: string): string {
  let texto = (bruto ?? '').replace(/[\r\n]+$/, '');
  const gs1PorAim = AIM_GS1.test(texto);
  if (PREFIXO_AIM.test(texto)) texto = texto.slice(3);

  const comParenteses = /\(21\)\s*([^()\u001d]+)/.exec(texto);
  if (comParenteses) return normalizarSerie(comParenteses[1]);

  const pareceGs1 = gs1PorAim || texto.includes(GS) || /^(?:00\d{18}|0[1-3]\d{14})/.test(texto);
  if (pareceGs1) {
    const serie = serieGs1(texto);
    if (serie) return normalizarSerie(serie);
  }

  if (/^https?:\/\//i.test(texto.trim())) {
    try {
      const url = new URL(texto.trim());
      for (const [chave, valor] of url.searchParams) {
        if (PARAMETROS_URL.includes(chave.toLowerCase()) && valor.trim()) return normalizarSerie(valor);
      }
    } catch { /* não é URL válida: segue como texto */ }
  }

  const chave = CHAVE_SERIE.exec(texto);
  if (chave) return normalizarSerie(chave[1]);

  return normalizarSerie(texto);
}

// ---------- padrão das séries do lote ----------

export interface PadraoSerie {
  minimo: number;
  maximo: number;
  prefixo: string;
  soDigitos: boolean;
}

/** Amostras necessárias para considerar que o lote tem um padrão. */
export const AMOSTRAS_PADRAO = 3;

/**
 * Padrão das séries já aceitas (tamanho, começo comum, só dígitos). Serve para segurar leituras que
 * destoam — tipicamente o código do produto (EAN) lido no lugar da série da mesma etiqueta.
 */
export function aprenderPadrao(series: readonly string[]): PadraoSerie | null {
  if (series.length < AMOSTRAS_PADRAO) return null;
  let prefixo = series[0];
  let minimo = Infinity;
  let maximo = 0;
  let soDigitos = true;
  for (const s of series) {
    while (prefixo && !s.startsWith(prefixo)) prefixo = prefixo.slice(0, -1);
    minimo = Math.min(minimo, s.length);
    maximo = Math.max(maximo, s.length);
    if (!/^\d+$/.test(s)) soDigitos = false;
  }
  // Só a parte fixa conta: dígitos do fim do começo comum mudam na sequência (TR0009 → TR0010).
  prefixo = prefixo.replace(/\d+$/, '');
  // Começo comum só conta quando é um prefixo de verdade, não a série inteira.
  if (prefixo.length < 2 || prefixo.length >= minimo) prefixo = '';
  return { minimo, maximo, prefixo: prefixo.slice(0, 8), soDigitos };
}

/** Motivo de a série destoar do padrão, ou null quando combina. */
export function foraDoPadrao(serie: string, padrao: PadraoSerie | null): string | null {
  if (!padrao) return null;
  if (serie.length < padrao.minimo || serie.length > padrao.maximo) {
    const esperado = padrao.minimo === padrao.maximo ? `${padrao.minimo}` : `${padrao.minimo} a ${padrao.maximo}`;
    return `tem ${serie.length} caracteres e as outras têm ${esperado}`;
  }
  if (padrao.prefixo && !serie.startsWith(padrao.prefixo)) return `não começa com ${padrao.prefixo} como as outras`;
  if (padrao.soDigitos && !/^\d+$/.test(serie)) return 'tem letras e as outras só números';
  return null;
}
