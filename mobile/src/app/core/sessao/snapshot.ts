/**
 * Retrato da sessão do app: onde o usuário está e o estado de tela que não é
 * rascunho de formulário (rolagem, filtros, aba, folha aberta).
 *
 * Funções puras, sem Angular nem IndexedDB, para serem testadas à exaustão:
 * serialização com soma de verificação, migração entre versões do schema e a
 * regra que decide entre retomar e começar do início.
 */

export const SNAPSHOT_SCHEMA = 2;

/** Estado de UI de uma rota. Livre por tela, mas sempre serializável em JSON. */
export type EstadoTela = Record<string, unknown>;

export interface SessaoSnapshot {
  schema: typeof SNAPSHOT_SCHEMA;
  /** Epoch (ms) da gravação. */
  salvoEm: number;
  /** Dono do aparelho (`sub` do JWT) — nunca restaurar a sessão de outra conta. */
  idUsuario: string;
  /** URL completa do Angular Router, com query string. Ex.: `/campo/nova?editId=x`. */
  rota: string;
  /** Pilha de navegação (mais antiga primeiro), sem a rota atual. */
  pilha: string[];
  /** Estado de tela por rota: `{ '/campo': { rolagem: 420, filtro: 'hoje' } }`. */
  telas: Record<string, EstadoTela>;
  /** Aba/janela que gravou — diagnóstico de concorrência. */
  idAba: string;
  /** Contador monotônico por aba. */
  seq: number;
}

export interface SnapshotGravado extends SessaoSnapshot {
  checksum: string;
}

/** FNV-1a 32 bits: rápido, síncrono e suficiente para detectar gravação truncada ou corrompida. */
export function fnv1a(texto: string): string {
  let h = 0x811c9dc5;
  for (let i = 0; i < texto.length; i++) {
    h ^= texto.charCodeAt(i);
    h = Math.imul(h, 0x01000193);
  }
  return (h >>> 0).toString(16).padStart(8, '0');
}

/** JSON com chaves ordenadas: o checksum não pode depender da ordem de inserção. */
export function jsonCanonico(valor: unknown): string {
  return JSON.stringify(valor, (_chave, v) => {
    if (v && typeof v === 'object' && !Array.isArray(v)) {
      return Object.keys(v).sort().reduce<Record<string, unknown>>((acc, k) => {
        acc[k] = (v as Record<string, unknown>)[k];
        return acc;
      }, {});
    }
    return v;
  });
}

export function selar(snapshot: SessaoSnapshot): SnapshotGravado {
  return { ...snapshot, checksum: fnv1a(jsonCanonico(snapshot)) };
}

// ---------------------------------------------------------------------------
// Migração
// ---------------------------------------------------------------------------

/**
 * Cada migração recebe o objeto na versão N e devolve na N+1. Nunca apaga
 * informação que a versão seguinte ainda saiba usar.
 *
 * v1 (protótipo interno, nunca publicado): `{ versao: 1, ts, usuario, url, ui }`.
 * Mantida para provar o mecanismo e porque builds de teste a gravaram.
 */
const MIGRACOES: Record<number, (antigo: any) => any> = {
  1: (v1) => ({
    schema: 2,
    salvoEm: v1.ts,
    idUsuario: v1.usuario,
    rota: v1.url,
    pilha: [],
    telas: v1.ui ?? {},
    idAba: 'migrado',
    seq: 0,
  }),
};

export type FalhaLeitura = 'vazio' | 'formato' | 'checksum' | 'schema-futuro';

export type ResultadoLeitura =
  | { ok: true; snapshot: SessaoSnapshot; migradoDe?: number }
  | { ok: false; motivo: FalhaLeitura };

function versaoDe(bruto: any): number | null {
  if (typeof bruto?.schema === 'number') return bruto.schema;
  if (typeof bruto?.versao === 'number') return bruto.versao;
  return null;
}

function formatoValido(s: any): s is SessaoSnapshot {
  return s
    && s.schema === SNAPSHOT_SCHEMA
    && typeof s.salvoEm === 'number' && Number.isFinite(s.salvoEm)
    && typeof s.idUsuario === 'string' && s.idUsuario.length > 0
    && typeof s.rota === 'string' && s.rota.startsWith('/')
    && Array.isArray(s.pilha) && s.pilha.every((r: unknown) => typeof r === 'string')
    && s.telas !== null && typeof s.telas === 'object' && !Array.isArray(s.telas);
}

/**
 * Valida e migra um registro lido do armazenamento. Nunca lança: qualquer coisa
 * estranha vira `{ ok: false }` e o app abre no início com os rascunhos intactos.
 */
export function lerSnapshot(bruto: unknown): ResultadoLeitura {
  if (bruto === undefined || bruto === null) return { ok: false, motivo: 'vazio' };
  if (typeof bruto !== 'object') return { ok: false, motivo: 'formato' };

  const versao = versaoDe(bruto);
  if (versao === null) return { ok: false, motivo: 'formato' };
  if (versao > SNAPSHOT_SCHEMA) return { ok: false, motivo: 'schema-futuro' };

  let atual: any = bruto;
  if (versao === SNAPSHOT_SCHEMA) {
    const { checksum, ...resto } = atual as SnapshotGravado;
    if (typeof checksum !== 'string' || checksum !== fnv1a(jsonCanonico(resto))) {
      return { ok: false, motivo: 'checksum' };
    }
    atual = resto;
  } else {
    try {
      for (let v = versao; v < SNAPSHOT_SCHEMA; v++) {
        const migrar = MIGRACOES[v];
        if (!migrar) return { ok: false, motivo: 'formato' };
        atual = migrar(atual);
      }
    } catch {
      return { ok: false, motivo: 'formato' };
    }
  }

  if (!formatoValido(atual)) return { ok: false, motivo: 'formato' };
  return versao === SNAPSHOT_SCHEMA ? { ok: true, snapshot: atual } : { ok: true, snapshot: atual, migradoDe: versao };
}

// ---------------------------------------------------------------------------
// Regra de restauração
// ---------------------------------------------------------------------------

/** Janela padrão de retomada automática: um turno inteiro (decisão P1). */
export const JANELA_RETOMADA_MS = 12 * 60 * 60 * 1000;

/** Rotas que nunca são destino de retomada. */
const ROTAS_NAO_RETOMAVEIS = [/^\/login(\?|$)/, /^\/sync(\?|$)/, /^\/instalar(\?|$)/];

export interface ContextoRetomada {
  agora: number;
  /** Conta dona do aparelho, mesmo com token expirado. */
  idUsuario: string | null;
  /** Token presente e não expirado, ou uso offline autorizado da mesma conta. */
  autenticado: boolean;
  janelaMs?: number;
}

export type DecisaoRetomada =
  | { acao: 'retomar'; rota: string }
  | { acao: 'login'; rotaDepois: string | null }
  | { acao: 'inicio'; motivo: 'sem-snapshot' | 'outra-conta' | 'expirado' | 'relogio' | 'rota-invalida' | FalhaLeitura };

/**
 * Decide para onde o app abre. A regra é deliberadamente simples para caber num
 * teste de tabela: mesma conta + recente + rota válida → retoma; sessão vencida →
 * login e depois retoma; qualquer dúvida → início (os rascunhos ficam onde estão).
 */
export function decidirRetomada(leitura: ResultadoLeitura, ctx: ContextoRetomada): DecisaoRetomada {
  if (!ctx.idUsuario) return { acao: 'login', rotaDepois: null };
  if (!leitura.ok) return { acao: 'inicio', motivo: leitura.motivo === 'vazio' ? 'sem-snapshot' : leitura.motivo };

  const s = leitura.snapshot;
  if (s.idUsuario !== ctx.idUsuario) return { acao: 'inicio', motivo: 'outra-conta' };

  const idade = ctx.agora - s.salvoEm;
  // Relógio do aparelho voltou no tempo (troca de fuso, bateria): não confiar na idade.
  if (idade < -5 * 60 * 1000) return { acao: 'inicio', motivo: 'relogio' };
  if (idade > (ctx.janelaMs ?? JANELA_RETOMADA_MS)) return { acao: 'inicio', motivo: 'expirado' };

  if (ROTAS_NAO_RETOMAVEIS.some(r => r.test(s.rota)) || s.rota === '/') {
    return { acao: 'inicio', motivo: 'rota-invalida' };
  }
  if (!ctx.autenticado) return { acao: 'login', rotaDepois: s.rota };
  return { acao: 'retomar', rota: s.rota };
}

/** Só aceita voltar para caminhos internos do app (evita open redirect via `?volta=`). */
export function rotaInternaSegura(rota: string | null | undefined): string | null {
  if (!rota || typeof rota !== 'string') return null;
  if (!rota.startsWith('/') || rota.startsWith('//') || rota.includes('\\')) return null;
  if (ROTAS_NAO_RETOMAVEIS.some(r => r.test(rota))) return null;
  return rota;
}
