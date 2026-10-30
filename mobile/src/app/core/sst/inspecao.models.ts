export type StatusInspecao = 'EM_ANDAMENTO' | 'CONCLUIDA' | 'CANCELADA';
export type StatusDesvio = 'ABERTO' | 'EM_TRATAMENTO' | 'RESOLVIDO';
export type GravidadeDesvio = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';
export type TipoResposta =
  | 'MULTIPLA_ESCOLHA'
  | 'MULTIPLA_SELECAO'
  | 'TEXTO'
  | 'NUMERO'
  | 'DATA';
export type ContextoAnexo = 'DESVIO' | 'ACAO_CORRETIVA';
export type TipoAnexo = 'FOTO' | 'DOCUMENTO';

export interface ApiProblem {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  erros?: Record<string, string | string[]>;
}

export interface PageResponse<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

export interface OpcaoResposta {
  id: string;
  texto: string;
  geraDesvio: boolean;
  gravidade: GravidadeDesvio | null;
  padrao?: boolean;
  ordem: number;
}

export interface PerguntaInspecao {
  id: string;
  enunciado: string;
  tipoResposta: TipoResposta;
  obrigatoria: boolean;
  ordem: number;
  opcoes: OpcaoResposta[];
}

export interface GrupoPergunta {
  id: string;
  titulo: string;
  ordem: number;
  perguntas: PerguntaInspecao[];
}

export interface FormularioInspecaoResumo {
  id: string;
  titulo: string;
  descricao: string | null;
  ativo: boolean;
  totalPerguntas: number;
}

export interface FormularioInspecao extends Omit<FormularioInspecaoResumo, 'totalPerguntas'> {
  emUso: boolean;
  grupos: GrupoPergunta[];
}

export interface RespostaInspecaoRequest {
  idPergunta: string;
  opcoesSelecionadas: string[];
  valorTexto?: string | null;
  valorNumero?: number | null;
  valorData?: string | null;
}

export interface DesvioInspecaoRequest {
  idPergunta: string;
  idOpcaoResposta: string;
  descricao?: string | null;
  acaoCorretiva?: string | null;
  prazo?: string | null;
  colaboradores: string[];
  status?: StatusDesvio;
}

export interface TipoInspecao {
  id: string;
  nome: string;
  descricao?: string | null;
  ordem: number;
  ativo: boolean;
  dataCriacao?: string;
  dataUltimaAlteracao?: string;
}

export interface RegistrarInspecaoRequest {
  idFormulario: string;
  idTipoInspecao: string;
  idProcesso: string;
  idVeiculo?: string | null;
  idEquipe?: string | null;
  idBaseOperacional: string;
  dataRealizacao: string;
  observacoes?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  colaboradoresInspecionados: string[];
  respostas: RespostaInspecaoRequest[];
  desvios: DesvioInspecaoRequest[];
  /** IDs devolvidos por `POST /v1/sst/inspecoes/anexos`. O backend exige ao menos um. */
  evidencias: string[];
}

export interface EvidenciaInspecaoResponse {
  id: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  /** ISO 8601 com offset, instante do servidor. */
  dataCriacao: string;
}

export interface AnexoDesvioResponse {
  id: string;
  contexto: ContextoAnexo;
  tipo: TipoAnexo;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes: number;
  dataCriacao: string;
}

export interface DesvioSegurancaResponse {
  id: string;
  idPergunta: string;
  idOpcaoResposta: string | null;
  gravidade: GravidadeDesvio;
  descricao: string | null;
  acaoCorretiva: string | null;
  prazo: string | null;
  status: StatusDesvio;
  dataResolucao: string | null;
  colaboradores: unknown[];
  anexos: AnexoDesvioResponse[];
}

export interface InspecaoSegurancaResponse {
  id: string;
  formulario: FormularioInspecaoResumo | null;
  tipoInspecao?: TipoInspecao | null;
  processo: unknown | null;
  idInspetor: string;
  nomeInspetor: string | null;
  veiculo: unknown | null;
  equipe: unknown | null;
  baseOperacional: unknown | null;
  dataRealizacao: string;
  status: StatusInspecao;
  observacoes: string | null;
  latitude: number | null;
  longitude: number | null;
  colaboradoresInspecionados: unknown[];
  respostas: RespostaInspecaoResponse[];
  desvios: DesvioSegurancaResponse[];
  /** Inspeções anteriores à exigência de evidência fotográfica vêm com lista vazia. */
  evidencias: EvidenciaInspecaoResponse[];
}

export interface InspecaoSegurancaFilter {
  idFormulario?: string;
  idTipoInspecao?: string;
  idProcesso?: string;
  idVeiculo?: string;
  idEquipe?: string;
  idBaseOperacional?: string;
  idColaborador?: string;
  status?: StatusInspecao;
  gravidadeDesvio?: GravidadeDesvio;
  apenasComDesvios?: boolean;
  dataRealizacaoDe?: string;
  dataRealizacaoAte?: string;
  buscaGlobal?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export interface TratarDesvioRequest {
  status: StatusDesvio;
  acaoCorretiva?: string | null;
  prazo?: string | null;
}

export interface RespostaInspecaoResponse extends RespostaInspecaoRequest {
  id: string;
  valorTexto: string | null;
  valorNumero: number | null;
  valorData: string | null;
}

export interface AnexoPendenteLocal {
  idLocal: string;
  chaveDesvioLocal: string;
  contexto: ContextoAnexo;
  arquivo: Blob;
  nomeArquivo: string;
  contentType: string;
}

export type EstadoEvidenciaLocal = 'LOCAL' | 'ENVIANDO' | 'ENVIADA' | 'ERRO';

/**
 * Evidência fotográfica obrigatória da inspeção, retida no rascunho local.
 *
 * O `arquivo` só pode ser liberado depois que o lançamento retornar 201: até lá,
 * o `idRemoto` sozinho não garante nada, porque uma foto pendente é expurgada
 * pelo servidor após 72 h sem uso.
 */
export interface EvidenciaLocal {
  idLocal: string;
  arquivo: Blob;
  nomeArquivo: string;
  contentType: string;
  /** Preenchido no 201 do upload de `POST /v1/sst/inspecoes/anexos`. */
  idRemoto?: string | null;
  estado: EstadoEvidenciaLocal;
  tentativas: number;
  /** Bytes do arquivo já comprimido, usado só para exibição na lista. */
  tamanhoBytes?: number;
  erroDetalhes?: string | null;
}

/** Limite por arquivo aceito pelo backend (`POST /v1/sst/inspecoes/anexos`). */
export const TAMANHO_MAXIMO_EVIDENCIA_BYTES = 15 * 1024 * 1024;

/** Somente imagens: PDF e Word são recusados no lançamento de inspeção. */
export const TIPOS_EVIDENCIA_ACEITOS = [
  'image/jpeg',
  'image/png',
  'image/webp',
  'image/heic',
  'image/heif',
] as const;

const EXTENSOES_EVIDENCIA_ACEITAS = ['jpg', 'jpeg', 'png', 'webp', 'heic', 'heif'];

/**
 * Repete no cliente a validação de borda do backend para não gastar upload em
 * campo com arquivo que seria recusado. O `422` continua tratado no envio.
 */
export function validarArquivoEvidencia(arquivo: {
  size: number;
  type?: string;
  name?: string;
}): string | null {
  if (!arquivo.size) return 'O arquivo selecionado está vazio.';
  if (arquivo.size > TAMANHO_MAXIMO_EVIDENCIA_BYTES) {
    return 'A foto ultrapassa o limite de 15 MB por arquivo.';
  }

  const tipo = (arquivo.type || '').toLowerCase();
  if (tipo) {
    return (TIPOS_EVIDENCIA_ACEITOS as readonly string[]).includes(tipo)
      ? null
      : 'A evidência precisa ser uma imagem (JPEG, PNG, WebP ou HEIC).';
  }

  // Sem `contentType` declarado, o backend cai na extensão do nome — igual aqui.
  const extensao = (arquivo.name || '').split('.').pop()?.toLowerCase() ?? '';
  return EXTENSOES_EVIDENCIA_ACEITAS.includes(extensao)
    ? null
    : 'A evidência precisa ser uma imagem (JPEG, PNG, WebP ou HEIC).';
}

export function sanitizarRegistrarInspecao(
  request: RegistrarInspecaoRequest
): RegistrarInspecaoRequest {
  return {
    idFormulario: request.idFormulario,
    idTipoInspecao: request.idTipoInspecao,
    idProcesso: request.idProcesso,
    idVeiculo: request.idVeiculo ?? null,
    idEquipe: request.idEquipe ?? null,
    idBaseOperacional: request.idBaseOperacional,
    dataRealizacao: request.dataRealizacao,
    observacoes: request.observacoes ?? null,
    latitude: request.latitude ?? null,
    longitude: request.longitude ?? null,
    colaboradoresInspecionados: [...request.colaboradoresInspecionados],
    respostas: request.respostas.map(resposta => ({
      idPergunta: resposta.idPergunta,
      opcoesSelecionadas: [...resposta.opcoesSelecionadas],
      valorTexto: resposta.valorTexto ?? null,
      valorNumero: resposta.valorNumero ?? null,
      valorData: resposta.valorData ?? null,
    })),
    desvios: request.desvios.map(desvio => ({
      idPergunta: desvio.idPergunta,
      idOpcaoResposta: desvio.idOpcaoResposta,
      descricao: desvio.descricao ?? null,
      acaoCorretiva: desvio.acaoCorretiva ?? null,
      prazo: desvio.prazo ?? null,
      colaboradores: [...desvio.colaboradores],
      status: desvio.status ?? 'ABERTO',
    })),
    // IDs repetidos são deduplicados pelo backend; deduplicar aqui mantém o
    // payload honesto e evita que a UI conte a mesma foto duas vezes.
    evidencias: [...new Set(request.evidencias ?? [])],
  };
}

export function validarRegistrarInspecao(
  formulario: FormularioInspecao,
  request: RegistrarInspecaoRequest,
  opcoes: {
    /**
     * Quantidade de evidências retidas localmente. Em campo o lançamento é
     * montado antes do upload, então `request.evidencias` ainda está vazio e a
     * contagem precisa vir do rascunho.
     */
    totalEvidencias?: number;
  } = {}
): string[] {
  const erros: string[] = [];
  const totalEvidencias = opcoes.totalEvidencias ?? (request.evidencias ?? []).length;
  if (totalEvidencias < 1) {
    erros.push('Anexe ao menos uma foto para comprovar a inspeção.');
  }
  const perguntas = new Map(
    formulario.grupos.flatMap(grupo => grupo.perguntas).map(pergunta => [pergunta.id, pergunta])
  );
  const respostas = new Map<string, RespostaInspecaoRequest>();

  if (!request.idTipoInspecao) erros.push('Selecione o tipo de inspeção.');
  if (!request.idBaseOperacional) erros.push('Informe a base operacional.');
  if (new Set(request.colaboradoresInspecionados).size !== request.colaboradoresInspecionados.length) {
    erros.push('A lista de colaboradores inspecionados contém duplicidades.');
  }
  if (request.colaboradoresInspecionados.length === 0) {
    erros.push('Informe ao menos um colaborador inspecionado.');
  }

  for (const resposta of request.respostas) {
    const pergunta = perguntas.get(resposta.idPergunta);
    if (!pergunta) {
      erros.push(`A pergunta ${resposta.idPergunta} não pertence ao formulário.`);
      continue;
    }
    if (respostas.has(resposta.idPergunta)) {
      erros.push(`A pergunta "${pergunta.enunciado}" foi respondida mais de uma vez.`);
      continue;
    }
    respostas.set(resposta.idPergunta, resposta);
    const opcoes = resposta.opcoesSelecionadas ?? [];
    if (pergunta.tipoResposta === 'MULTIPLA_ESCOLHA' && opcoes.length !== 1) {
      erros.push(`Selecione exatamente uma opção em "${pergunta.enunciado}".`);
    }
    if (pergunta.tipoResposta === 'MULTIPLA_SELECAO' && opcoes.length < 1) {
      erros.push(`Selecione ao menos uma opção em "${pergunta.enunciado}".`);
    }
    if (!['MULTIPLA_ESCOLHA', 'MULTIPLA_SELECAO'].includes(pergunta.tipoResposta) && opcoes.length) {
      erros.push(`A pergunta "${pergunta.enunciado}" não aceita opções.`);
    }
    if (opcoes.some(id => !pergunta.opcoes.some(opcao => opcao.id === id))) {
      erros.push(`Há uma opção inválida em "${pergunta.enunciado}".`);
    }
  }

  for (const pergunta of perguntas.values()) {
    if (pergunta.obrigatoria && !respostas.has(pergunta.id)) {
      erros.push(`Responda à pergunta obrigatória "${pergunta.enunciado}".`);
    }
  }

  const colaboradores = new Set(request.colaboradoresInspecionados);
  const chavesDesvio = new Set<string>();
  for (const desvio of request.desvios) {
    const chave = `${desvio.idPergunta}:${desvio.idOpcaoResposta}`;
    if (chavesDesvio.has(chave)) erros.push('Há um desvio duplicado no lançamento.');
    chavesDesvio.add(chave);
    const pergunta = perguntas.get(desvio.idPergunta);
    const opcao = pergunta?.opcoes.find(item => item.id === desvio.idOpcaoResposta);
    if (!opcao?.geraDesvio) erros.push('Há um desvio sem uma opção geradora válida.');
    if (desvio.colaboradores.length === 0) erros.push('Atribua ao menos um colaborador a cada desvio.');
    if (desvio.colaboradores.some(id => !colaboradores.has(id))) {
      erros.push('Todo colaborador atribuído a um desvio deve estar entre os inspecionados.');
    }
  }

  for (const resposta of request.respostas) {
    const pergunta = perguntas.get(resposta.idPergunta);
    for (const idOpcao of resposta.opcoesSelecionadas ?? []) {
      const opcao = pergunta?.opcoes.find(item => item.id === idOpcao);
      if (opcao?.geraDesvio && !chavesDesvio.has(`${resposta.idPergunta}:${idOpcao}`)) {
        erros.push(`A opção geradora "${opcao.texto}" precisa de um desvio correspondente.`);
      }
    }
  }

  return [...new Set(erros)];
}
