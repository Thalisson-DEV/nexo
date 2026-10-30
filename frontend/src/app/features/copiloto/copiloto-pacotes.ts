import {
  ArquivoAssistente,
  ArtefatoAssistente,
  AssistenteAcao,
  AssistenteStatus,
  MensagemAssistente,
  PacoteAssistente,
} from '../../core/models/assistente.models';

/**
 * Processador de pacotes do Copiloto (porta do "packet processor" do Onyx). Converte o stream
 * NDJSON — ou os pacotes persistidos no histórico — numa visão da resposta: etapas das ferramentas
 * (aba 1..n de cada ciclo), texto do modelo (aba 0), artefatos, arquivos, fontes e ações. O mesmo
 * processamento é usado ao vivo e ao reabrir uma conversa, então a linha do tempo é idêntica.
 */

export type StatusEtapa = 'executando' | 'ok' | 'erro';

export interface FonteView {
  numero: number;
  titulo: string;
  url: string;
  dominio: string;
  trecho?: string;
  publicadoEm?: string;
}

export interface SaidaEtapa {
  fluxo: 'stdout' | 'stderr';
  texto: string;
}

export interface PreviaEtapa {
  colunas: string[];
  linhas: unknown[][];
  total: number;
}

export interface EtapaView {
  id: string;
  turno: number;
  ferramenta: string;
  titulo: string;
  tipo: string;
  rotulo: string;
  argumentos?: Record<string, unknown>;
  status: StatusEtapa;
  resumo?: string;
  erro?: string;
  duracaoMs?: number;
  resultado?: string;
  linhas?: number;
  previa?: PreviaEtapa;
  fontes: FonteView[];
  arquivos: ArquivoAssistente[];
  saida: SaidaEtapa[];
}

export interface PropostaAcaoView {
  acaoId: string;
  ferramenta: string;
  titulo: string;
  risco?: string;
  previa?: unknown;
}

export interface RespostaView {
  etapas: EtapaView[];
  /** Texto por ciclo; o último ciclo com texto é a resposta, os anteriores são observações. */
  textos: Record<number, string>;
  artefatos: ArtefatoAssistente[];
  arquivos: ArquivoAssistente[];
  acoes: AssistenteAcao[];
  fontes: FonteView[];
  propostas: PropostaAcaoView[];
  status: AssistenteStatus;
  erro?: string;
  titulo?: string;
  mensagemId?: string;
  mensagemUsuarioId?: string;
  duracaoMs?: number;
  concluida: boolean;
  anexos: ArquivoAssistente[];
  /** Pacotes recebidos (sem heartbeat): cursor para retomar o stream. */
  recebidos: number;
}

const LIMITE_SAIDA = 40_000;

export function novaResposta(): RespostaView {
  return {
    etapas: [],
    textos: {},
    artefatos: [],
    arquivos: [],
    acoes: [],
    fontes: [],
    propostas: [],
    status: 'PROCESSANDO',
    concluida: false,
    anexos: [],
    recebidos: 0,
  };
}

export function aplicarPacote(estado: RespostaView, pacote: PacoteAssistente): RespostaView {
  const obj = pacote.obj;
  if (obj.type === 'heartbeat') return estado;
  const r: RespostaView = { ...estado, recebidos: estado.recebidos + 1 };
  const turno = pacote.placement?.turnIndex ?? 0;
  const idChamada = texto(obj['toolCallId']);

  switch (obj.type) {
    case 'run_info':
      r.mensagemId = texto(obj['mensagemId']) ?? r.mensagemId;
      r.mensagemUsuarioId = texto(obj['mensagemUsuarioId']) ?? r.mensagemUsuarioId;
      r.anexos = lista<ArquivoAssistente>(obj['anexos']);
      return r;

    case 'message_start':
      if (!(turno in r.textos)) r.textos = { ...r.textos, [turno]: '' };
      return r;

    case 'message_delta': {
      const delta = texto(obj['content']) ?? '';
      if (!delta) return r;
      r.textos = { ...r.textos, [turno]: (r.textos[turno] ?? '') + delta };
      return r;
    }

    case 'tool_start': {
      if (!idChamada) return r;
      const etapa: EtapaView = {
        id: idChamada,
        turno,
        ferramenta: texto(obj['ferramenta']) ?? '',
        titulo: texto(obj['titulo']) ?? texto(obj['ferramenta']) ?? 'Ferramenta',
        tipo: texto(obj['tipo']) ?? 'DATA',
        rotulo: texto(obj['rotulo']) ?? texto(obj['titulo']) ?? 'Executando',
        argumentos: objeto(obj['argumentos']),
        status: 'executando',
        fontes: [],
        arquivos: [],
        saida: [],
      };
      const existente = r.etapas.findIndex((item) => item.id === idChamada);
      r.etapas = existente >= 0
        ? r.etapas.map((item, indice) => (indice === existente ? { ...item, ...etapa } : item))
        : [...r.etapas, etapa];
      return r;
    }

    case 'tool_output': {
      if (!idChamada) return r;
      const fluxo = obj['stream'] === 'stderr' ? 'stderr' : 'stdout';
      const dados = texto(obj['data']) ?? '';
      r.etapas = atualizarEtapa(r.etapas, idChamada, (etapa) => {
        const saida = [...etapa.saida];
        const ultima = saida[saida.length - 1];
        if (ultima && ultima.fluxo === fluxo) {
          const unido = ultima.texto + dados;
          saida[saida.length - 1] = { fluxo, texto: unido.length > LIMITE_SAIDA ? unido.slice(0, LIMITE_SAIDA) : unido };
        } else {
          saida.push({ fluxo, texto: dados });
        }
        return { ...etapa, saida };
      });
      return r;
    }

    case 'tool_result': {
      if (!idChamada) return r;
      const fontes = lista<Record<string, unknown>>(obj['fontes']).map(paraFonte).filter(temUrl);
      const arquivos = lista<ArquivoAssistente>(obj['arquivos']);
      r.etapas = atualizarEtapa(r.etapas, idChamada, (etapa) => ({
        ...etapa,
        status: obj['status'] === 'ok' ? 'ok' : 'erro',
        resumo: texto(obj['resumo']),
        erro: texto(obj['erro']),
        duracaoMs: numero(obj['duracaoMs']),
        resultado: texto(obj['resultado']),
        linhas: numero(obj['linhas']),
        previa: paraPrevia(obj['previa']),
        fontes: fontes.length ? fontes : etapa.fontes,
        arquivos: juntarPorId(etapa.arquivos, arquivos),
      }));
      if (fontes.length) r.fontes = juntarFontes(r.fontes, fontes);
      if (arquivos.length) r.arquivos = juntarPorId(r.arquivos, arquivos);
      return r;
    }

    case 'file': {
      const arquivo = objeto(obj['arquivo']) as ArquivoAssistente | undefined;
      if (!arquivo?.id) return r;
      r.arquivos = juntarPorId(r.arquivos, [arquivo]);
      if (idChamada) {
        r.etapas = atualizarEtapa(r.etapas, idChamada, (etapa) => ({
          ...etapa,
          arquivos: juntarPorId(etapa.arquivos, [arquivo]),
        }));
      }
      return r;
    }

    case 'artifact': {
      const artefato = objeto(obj['artefato']) as ArtefatoAssistente | undefined;
      if (!artefato?.codigo) return r;
      const sem = r.artefatos.filter((item) => item.codigo !== artefato.codigo);
      r.artefatos = [...sem, artefato];
      return r;
    }

    case 'navigation': {
      const acao = objeto(obj['acao']) as AssistenteAcao | undefined;
      if (!acao?.rotulo) return r;
      const chave = chaveAcao(acao);
      if (!r.acoes.some((item) => chaveAcao(item) === chave)) r.acoes = [...r.acoes, acao];
      return r;
    }

    case 'action_proposal':
      r.propostas = [
        ...r.propostas,
        {
          acaoId: texto(obj['acaoId']) ?? '',
          ferramenta: texto(obj['ferramenta']) ?? '',
          titulo: texto(obj['titulo']) ?? 'Ação proposta',
          risco: texto(obj['risco']),
          previa: obj['previa'],
        },
      ];
      return r;

    case 'title':
      r.titulo = texto(obj['titulo']);
      return r;

    case 'error':
      r.erro = texto(obj['mensagem']) ?? 'Não foi possível concluir a resposta.';
      return r;

    case 'stop': {
      r.status = (texto(obj['status']) as AssistenteStatus | undefined) ?? 'CONCLUIDA';
      r.duracaoMs = numero(obj['duracaoMs']);
      r.mensagemId = texto(obj['mensagemId']) ?? r.mensagemId;
      r.concluida = true;
      // Etapas que ficaram abertas (cancelamento) não aparecem como "executando" para sempre.
      r.etapas = r.etapas.map((etapa): EtapaView =>
        etapa.status === 'executando'
          ? { ...etapa, status: 'erro', erro: etapa.erro ?? 'Interrompida antes de terminar.' }
          : etapa,
      );
      return r;
    }

    default:
      return r;
  }
}

export function aplicarPacotes(estado: RespostaView, pacotes: readonly PacoteAssistente[]): RespostaView {
  return Array.isArray(pacotes) ? pacotes.reduce(aplicarPacote, estado) : estado;
}

/** Reconstrói a visão de uma resposta persistida (histórico). */
export function respostaDaMensagem(mensagem: MensagemAssistente): RespostaView {
  let r = aplicarPacotes(novaResposta(), mensagem.pacotes ?? []);
  r = { ...r, mensagemId: mensagem.id, status: mensagem.status };
  if (mensagem.status !== 'PROCESSANDO') {
    r.concluida = true;
    r.etapas = r.etapas.map((etapa): EtapaView =>
      etapa.status === 'executando' ? { ...etapa, status: 'erro', erro: 'Interrompida antes de terminar.' } : etapa,
    );
    if (!textoFinal(r) && !(mensagem.status === 'FALHOU' && r.erro)) {
      r.textos = { ...r.textos, [ultimoTurno(r) + 1]: mensagem.conteudo };
    }
  }
  if (mensagem.acoes?.length) {
    const chaves = new Set(r.acoes.map(chaveAcao));
    r.acoes = [...r.acoes, ...mensagem.acoes.filter((acao) => !chaves.has(chaveAcao(acao)))];
  }
  if (mensagem.metricas?.duracaoMs) r.duracaoMs = mensagem.metricas.duracaoMs;
  return r;
}

/** Texto da resposta: o último ciclo com conteúdo. */
export function textoFinal(r: RespostaView): string {
  const turnos = Object.keys(r.textos).map(Number).sort((a, b) => a - b);
  for (let i = turnos.length - 1; i >= 0; i--) {
    const valor = r.textos[turnos[i]];
    if (valor && valor.trim()) return valor;
  }
  return '';
}

/** Textos intermediários (ciclos anteriores ao da resposta), exibidos como observações do trabalho. */
export function observacoes(r: RespostaView): string[] {
  const turnos = Object.keys(r.textos).map(Number).sort((a, b) => a - b).filter((t) => r.textos[t]?.trim());
  return turnos.slice(0, -1).map((t) => r.textos[t].trim());
}

export function emAndamento(r: RespostaView): boolean {
  return !r.concluida;
}

export function etapasEmExecucao(r: RespostaView): EtapaView[] {
  return r.etapas.filter((etapa) => etapa.status === 'executando');
}

function ultimoTurno(r: RespostaView): number {
  const turnos = [...Object.keys(r.textos).map(Number), ...r.etapas.map((e) => e.turno)];
  return turnos.length ? Math.max(...turnos) : 0;
}

function atualizarEtapa(etapas: EtapaView[], id: string, alterar: (etapa: EtapaView) => EtapaView): EtapaView[] {
  const indice = etapas.findIndex((etapa) => etapa.id === id);
  if (indice < 0) return etapas;
  return etapas.map((etapa, i) => (i === indice ? alterar(etapa) : etapa));
}

function juntarPorId<T extends { id: string }>(atuais: T[], novos: T[]): T[] {
  if (!novos.length) return atuais;
  const mapa = new Map(atuais.map((item) => [item.id, item]));
  for (const item of novos) if (item?.id) mapa.set(item.id, { ...mapa.get(item.id), ...item });
  return [...mapa.values()];
}

function juntarFontes(atuais: FonteView[], novas: FonteView[]): FonteView[] {
  const mapa = new Map(atuais.map((fonte) => [fonte.numero, fonte]));
  for (const fonte of novas) if (!mapa.has(fonte.numero)) mapa.set(fonte.numero, fonte);
  return [...mapa.values()].sort((a, b) => a.numero - b.numero);
}

function paraFonte(bruta: Record<string, unknown>): FonteView {
  return {
    numero: numero(bruta['numero']) ?? 0,
    titulo: texto(bruta['titulo']) ?? texto(bruta['url']) ?? 'Fonte',
    url: texto(bruta['url']) ?? '',
    dominio: texto(bruta['dominio']) ?? '',
    trecho: texto(bruta['trecho']),
    publicadoEm: texto(bruta['publicadoEm']),
  };
}

function temUrl(fonte: FonteView): boolean {
  return /^https?:\/\//i.test(fonte.url);
}

function paraPrevia(valor: unknown): PreviaEtapa | undefined {
  const bruta = objeto(valor);
  if (!bruta || !Array.isArray(bruta['colunas'])) return undefined;
  return {
    colunas: (bruta['colunas'] as unknown[]).map(String),
    linhas: Array.isArray(bruta['linhas']) ? (bruta['linhas'] as unknown[][]) : [],
    total: numero(bruta['total']) ?? 0,
  };
}

function chaveAcao(acao: AssistenteAcao): string {
  return `${acao.tipo}|${acao.chaveDestino ?? acao.destino ?? acao.alvo ?? ''}|${JSON.stringify(acao.parametros ?? {})}|${JSON.stringify(acao.filtros ?? [])}`;
}

function texto(valor: unknown): string | undefined {
  return typeof valor === 'string' && valor.length ? valor : undefined;
}

function numero(valor: unknown): number | undefined {
  return typeof valor === 'number' && Number.isFinite(valor) ? valor : undefined;
}

function objeto(valor: unknown): Record<string, unknown> | undefined {
  return valor && typeof valor === 'object' && !Array.isArray(valor) ? (valor as Record<string, unknown>) : undefined;
}

function lista<T>(valor: unknown): T[] {
  return Array.isArray(valor) ? (valor.filter((item) => item && typeof item === 'object') as T[]) : [];
}
