import { CesTone } from '@ces/ui';
/**
 * Apoio de apresentação do módulo de notificações.
 *
 * Os rótulos aqui existem **apenas** para montar filtros e formulários — enums que o usuário
 * escolhe antes de haver um DTO na mão. Ao renderizar dados vindos do servidor, use sempre o
 * par `campoRotulo` do próprio DTO; caso contrário o front duplica o catálogo do backend.
 */
import {
  ApresentacaoComunicado,
  CategoriaNotificacao,
  OperadorCondicao,
  PrioridadeNotificacao,
  PoliticaRoteamentoResponse,
  StatusComunicado,
  TipoAlvoSegmento,
  TipoComunicado,
  TipoDestinatarioRegra
} from '../../core/models/notificacao.models';

export interface OpcaoSelecao<T> {
  label: string;
  value: T;
}

export const CATEGORIA_OPTIONS: OpcaoSelecao<CategoriaNotificacao>[] = [
  { label: 'Aprovações pendentes', value: 'APROVACAO' },
  { label: 'Segurança do trabalho', value: 'SEGURANCA' },
  { label: 'Operação', value: 'OPERACIONAL' },
  { label: 'Chamados', value: 'CHAMADO' },
  { label: 'Marcações', value: 'MENCAO' },
  { label: 'Comunicados', value: 'COMUNICADO' },
  { label: 'Sistema', value: 'SISTEMA' }
];

export const PRIORIDADE_OPTIONS: OpcaoSelecao<PrioridadeNotificacao>[] = [
  { label: 'Baixa', value: 'BAIXA' },
  { label: 'Normal', value: 'NORMAL' },
  { label: 'Alta', value: 'ALTA' },
  { label: 'Crítica', value: 'CRITICA' }
];

export const TIPO_COMUNICADO_OPTIONS: OpcaoSelecao<TipoComunicado>[] = [
  { label: 'Dica de uso', value: 'DICA' },
  { label: 'Novidades da versão', value: 'CHANGELOG' },
  { label: 'Aviso', value: 'AVISO' },
  { label: 'Manutenção programada', value: 'MANUTENCAO' },
  { label: 'Urgente', value: 'URGENTE' }
];

export const APRESENTACAO_OPTIONS: OpcaoSelecao<ApresentacaoComunicado>[] = [
  { label: 'Modal ao entrar', value: 'MODAL' },
  { label: 'Faixa no topo', value: 'BANNER' },
  { label: 'Card no mural', value: 'CARD' }
];

export const STATUS_COMUNICADO_OPTIONS: OpcaoSelecao<StatusComunicado>[] = [
  { label: 'Rascunho', value: 'RASCUNHO' },
  { label: 'Publicado', value: 'PUBLICADO' },
  { label: 'Arquivado', value: 'ARQUIVADO' }
];

export const TIPO_ALVO_OPTIONS: OpcaoSelecao<TipoAlvoSegmento>[] = [
  { label: 'Todos os usuários', value: 'TODOS' },
  { label: 'Perfil de acesso', value: 'PERFIL' },
  { label: 'Usuário específico', value: 'USUARIO' },
  { label: 'Tipo de usuário', value: 'TIPO_USUARIO' }
];

export const TIPO_USUARIO_OPTIONS: OpcaoSelecao<string>[] = [
  { label: 'Administrativo', value: 'ADMINISTRATIVO' },
  { label: 'Campo', value: 'CAMPO' }
];

export const TIPO_DESTINATARIO_OPTIONS: OpcaoSelecao<TipoDestinatarioRegra>[] = [
  { label: 'Quem tem a permissão (recomendado)', value: 'PERMISSAO' },
  { label: 'Todos de um perfil de acesso', value: 'PERFIL' },
  { label: 'Usuário específico', value: 'USUARIO' },
  { label: 'Papel no próprio registro', value: 'PAPEL_DINAMICO' },
  { label: 'Política de roteamento', value: 'POLITICA' }
];

export const OPERADOR_OPTIONS: OpcaoSelecao<OperadorCondicao>[] = [
  { label: 'é igual a', value: 'IGUAL' },
  { label: 'é diferente de', value: 'DIFERENTE' },
  { label: 'é maior que', value: 'MAIOR_QUE' },
  { label: 'é maior ou igual a', value: 'MAIOR_OU_IGUAL' },
  { label: 'é menor que', value: 'MENOR_QUE' },
  { label: 'é menor ou igual a', value: 'MENOR_OU_IGUAL' },
  { label: 'contém', value: 'CONTEM' },
  { label: 'está entre os valores', value: 'EM' }
];

/**
 * Opções válidas para uma regra. A única exceção ao filtro de ativas é a política já associada:
 * ela permanece visível para explicar o estado histórico, mas o formulário ainda bloqueia o save.
 */
export function politicasCompativeisComRegra(
  politicas: readonly PoliticaRoteamentoResponse[],
  chaveGatilho: string,
  idsSelecionados: ReadonlySet<string | null>
): PoliticaRoteamentoResponse[] {
  return politicas.filter(
    (politica) =>
      politica.chaveGatilho === chaveGatilho &&
      (politica.ativo || idsSelecionados.has(politica.id))
  );
}

// ---------------------------------------------------------------------------
// Papéis dinâmicos — rótulo amigável para as chaves do catálogo de gatilhos
// ---------------------------------------------------------------------------

/**
 * Tradução dos papéis endereçáveis por `PAPEL_DINAMICO`. Assim como as variáveis, é ajuda de tela:
 * quem diz **quais** papéis existem continua sendo o gatilho. Papel fora do mapa aparece com a
 * própria chave, sem quebrar nada.
 */
const PAPEL_ROTULOS: Record<string, string> = {
  equipeAtendimento: 'Equipe responsável pelo chamado',
  responsavel: 'Quem atende o chamado',
  mencionado: 'Quem foi marcado',
  requisitante: 'Quem requisitou o material',
  solicitante: 'Quem abriu o registro'
};

/**
 * `solicitante` atravessa desconto, chamado e financeiro com sentidos diferentes. O prefixo do
 * gatilho desempata quando ele é conhecido.
 */
const PAPEL_ROTULOS_POR_DOMINIO: Record<string, Record<string, string>> = {
  chamado: { solicitante: 'Quem abriu o chamado' },
  desconto: { solicitante: 'Quem lançou o desconto' },
  financeiro: { solicitante: 'Quem pediu a solicitação' }
};

export function rotuloPapel(papel: string, chaveGatilho?: string | null): string {
  const dominio = chaveGatilho?.split('.')[0] ?? '';
  return PAPEL_ROTULOS_POR_DOMINIO[dominio]?.[papel] ?? PAPEL_ROTULOS[papel] ?? papel;
}

/** Operadores que o backend só aceita sobre valores numéricos. */
export const OPERADORES_NUMERICOS: OperadorCondicao[] = [
  'MAIOR_QUE',
  'MAIOR_OU_IGUAL',
  'MENOR_QUE',
  'MENOR_OU_IGUAL'
];

// ---------------------------------------------------------------------------
// Ícones e cores — decisão puramente visual, não é tradução de enum
// ---------------------------------------------------------------------------

export const CATEGORIA_ICONES: Record<CategoriaNotificacao, string> = {
  APROVACAO: 'pi pi-check-square',
  SEGURANCA: 'pi pi-shield',
  OPERACIONAL: 'pi pi-cog',
  CHAMADO: 'pi pi-ticket',
  MENCAO: 'pi pi-at',
  COMUNICADO: 'pi pi-megaphone',
  SISTEMA: 'pi pi-server'
};

export const PRIORIDADE_CLASSES: Record<PrioridadeNotificacao, string> = {
  BAIXA: 'bg-slate-100 text-slate-500',
  NORMAL: 'bg-primary-50 text-primary-700',
  ALTA: 'bg-amber-50 text-amber-700',
  CRITICA: 'bg-red-50 text-red-600'
};

/** Tom do DS (`ces-status`) para a prioridade. */
export const PRIORIDADE_TOM: Record<PrioridadeNotificacao, CesTone> = {
  BAIXA: 'neutral',
  NORMAL: 'info',
  ALTA: 'warning',
  CRITICA: 'danger'
};

export const PRIORIDADE_PONTO: Record<PrioridadeNotificacao, string> = {
  BAIXA: 'bg-slate-300',
  NORMAL: 'bg-primary-700',
  ALTA: 'bg-amber-500',
  CRITICA: 'bg-red-500'
};

export const TIPO_COMUNICADO_ICONES: Record<TipoComunicado, string> = {
  DICA: 'pi pi-lightbulb',
  CHANGELOG: 'pi pi-sparkles',
  AVISO: 'pi pi-info-circle',
  MANUTENCAO: 'pi pi-wrench',
  URGENTE: 'pi pi-exclamation-triangle'
};

export const TIPO_COMUNICADO_CLASSES: Record<TipoComunicado, string> = {
  DICA: 'bg-amber-50 text-amber-800 border-amber-200 dark:bg-amber-950/40 dark:text-amber-300 dark:border-amber-800/60',
  CHANGELOG: 'bg-violet-50 text-violet-800 border-violet-200 dark:bg-violet-950/40 dark:text-violet-300 dark:border-violet-800/60',
  AVISO: 'bg-primary-50 text-primary-800 border-primary-200 dark:bg-primary-950/40 dark:text-primary-300 dark:border-primary-800/60',
  MANUTENCAO: 'bg-slate-100 text-slate-700 border-slate-200 dark:!bg-slate-800/60 dark:text-slate-200 dark:border-slate-700',
  URGENTE: 'bg-red-50 text-red-800 border-red-200 dark:bg-red-950/40 dark:text-red-300 dark:border-red-800/60'
};

export const STATUS_COMUNICADO_CLASSES: Record<StatusComunicado, string> = {
  RASCUNHO: 'bg-slate-100 text-slate-700 dark:!bg-slate-800 dark:!text-slate-300',
  PUBLICADO: 'bg-green-50 text-green-700 dark:bg-green-950/40 dark:text-green-300',
  ARQUIVADO: 'bg-slate-100 text-slate-500 dark:!bg-slate-800/80 dark:text-slate-400'
};

// ---------------------------------------------------------------------------
// Formatação
// ---------------------------------------------------------------------------

const FORMATADOR_DATA = new Intl.DateTimeFormat('pt-BR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit'
});

export function formatarDataHora(valor: string | null | undefined): string {
  if (!valor) return '—';
  const data = new Date(valor);
  return Number.isNaN(data.getTime()) ? '—' : FORMATADOR_DATA.format(data);
}

/** "agora", "há 12 min", "há 3 h", "há 2 d" — e data cheia acima de uma semana. */
export function tempoRelativo(valor: string | null | undefined): string {
  if (!valor) return '';
  const data = new Date(valor);
  if (Number.isNaN(data.getTime())) return '';

  const segundos = Math.floor((Date.now() - data.getTime()) / 1000);
  if (segundos < 60) return 'agora';
  if (segundos < 3600) return `há ${Math.floor(segundos / 60)} min`;
  if (segundos < 86400) return `há ${Math.floor(segundos / 3600)} h`;
  if (segundos < 604800) return `há ${Math.floor(segundos / 86400)} d`;
  return formatarDataHora(valor);
}

export function formatarTamanhoArquivo(bytes: number): string {
  if (!bytes) return '0 KB';
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/**
 * Explicação em português das variáveis mais comuns do catálogo de gatilhos.
 *
 * É ajuda de tela, não catálogo: o servidor continua sendo quem diz **quais** variáveis existem.
 * Nome fora deste mapa simplesmente não ganha explicação, sem quebrar nada.
 */
const VARIAVEL_DICAS: Record<string, string> = {
  total: 'quantos itens estão parados na fila neste momento',
  limiar: 'o número que você definir na etapa "Disparo"',
  motivo: 'o motivo informado no registro',
  parcelas: 'em quantas parcelas o desconto foi lançado',
  dataOcorrencia: 'a data em que o fato aconteceu',
  idDesconto: 'o código do desconto — útil no destino ao clicar',
  solicitante: 'quem abriu o registro',
  // Desconto
  valorValidado: 'o valor confirmado na validação',
  valorBaixado: 'o valor efetivamente baixado',
  motivoRejeicao: 'a justificativa de quem recusou',
  // Chamado
  protocolo: 'o número do chamado (CH-...)',
  assunto: 'o título informado na abertura',
  topico: 'o assunto do catálogo em que o chamado foi aberto',
  prioridade: 'a prioridade do registro no momento do evento',
  equipe: 'a equipe responsável pelo atendimento',
  autor: 'quem escreveu a mensagem',
  trecho: 'o começo do texto, truncado em 200 caracteres',
  solucao: 'a solução registrada no fechamento',
  prazoReaberturaHoras: 'por quantas horas o solicitante ainda pode reabrir',
  idChamado: 'o código do chamado — útil no destino ao clicar',
  // Financeiro
  tipo: 'COMPRA ou PAGAMENTO — use em condições para rotear ao aprovador certo',
  numero: 'o número da solicitação',
  valor: 'o valor solicitado',
  valorAprovado: 'o valor liberado na aprovação',
  descricao: 'a descrição informada na solicitação',
  observacao: 'a observação deixada por quem aprovou',
  idSolicitacao: 'o código da solicitação — útil no destino ao clicar',
  base: 'nome normalizado da base operacional',
  uf: 'sigla da UF da base operacional',
  frota: 'se a solicitação pertence à frota',
  obraCivil: 'se a solicitação pertence às obras civis',
  // Estoque
  estoqueOrigem: 'de onde o material sai',
  estoqueDestino: 'para onde o material vai',
  totalItens: 'quantos itens a requisição tem',
  justificativa: 'o complemento escrito por quem recusou',
  requisitante: 'quem pediu o material',
  // Marcação
  origem: 'a tela de onde veio a marcação',
  tipoOrigem: 'o tipo de registro que continha o texto',
  link: 'o caminho de volta ao registro, montado pelo servidor',
  mencionado: 'quem foi marcado no texto'
};

export function dicaVariavel(nome: string): string | null {
  return VARIAVEL_DICAS[nome] ?? null;
}

/** Extrai `{{placeholders}}` de um template para validar contra as variáveis do gatilho. */
export function extrairPlaceholders(template: string | null | undefined): string[] {
  if (!template) return [];
  const encontrados = template.match(/\{\{\s*([\w.]+)\s*\}\}/g) ?? [];
  return [...new Set(encontrados.map((item) => item.replace(/[{}\s]/g, '')))];
}
