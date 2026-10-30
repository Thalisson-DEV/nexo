import { HttpEventType } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { NotificationService } from '@ces/ui/feedback';
import {
  AcaoCartao,
  ArquivoAssistente,
  ArtefatoAssistente,
  AssistenteContexto,
  CapacidadesAssistente,
  ConversaDetalhe,
  ConversaResumo,
  MensagemAssistente,
  MetricasMensagem,
  PacoteAssistente,
} from '../../core/models/assistente.models';
import { getErrorMessage } from '../../core/models/shared.models';
import { AssistenteService, AssistenteStreamError } from '../../core/services/assistente.service';
import { AuthService } from '../../core/services/auth.service';
import { RespostaView, aplicarPacote, novaResposta, respostaDaMensagem } from './copiloto-pacotes';

export interface MensagemView {
  chave: string;
  papel: 'USUARIO' | 'ASSISTENTE';
  texto?: string;
  anexos: ArquivoAssistente[];
  resposta?: RespostaView;
  dataCriacao?: string;
  metricas?: MetricasMensagem;
  /** Snapshot do contexto de tela desta pergunta (dashboards): mantém o histórico semanticamente correto. */
  contexto?: AssistenteContexto | null;
}

export interface AnexoPendente {
  idLocal: string;
  nome: string;
  tamanho: number;
  progresso: number;
  estado: 'enviando' | 'pronto' | 'erro';
  arquivo?: ArquivoAssistente;
  erro?: string;
}

export interface GrupoConversas {
  rotulo: string;
  conversas: ConversaResumo[];
}

const TENTATIVAS_RETOMADA = 4;
const EXTENSOES_ACEITAS = ['pdf', 'xlsx', 'xlsm', 'xls', 'csv', 'tsv', 'json', 'txt', 'md', 'log', 'xml', 'yml', 'yaml',
  'sql', 'html', 'htm', 'docx', 'png', 'jpg', 'jpeg', 'gif', 'webp'];

/** Agrupa conversas por última atividade (Hoje, Ontem, Últimos 7 dias, Últimos 30 dias, Mais antigas). */
export function agruparConversas(conversas: readonly ConversaResumo[], agora = new Date()): GrupoConversas[] {
  const inicioHoje = new Date(agora.getFullYear(), agora.getMonth(), agora.getDate()).getTime();
  const dia = 86_400_000;
  const limites: { rotulo: string; desde: number }[] = [
    { rotulo: 'Hoje', desde: inicioHoje },
    { rotulo: 'Ontem', desde: inicioHoje - dia },
    { rotulo: 'Últimos 7 dias', desde: inicioHoje - 7 * dia },
    { rotulo: 'Últimos 30 dias', desde: inicioHoje - 30 * dia },
    { rotulo: 'Mais antigas', desde: Number.NEGATIVE_INFINITY },
  ];
  const grupos = limites.map((limite) => ({ rotulo: limite.rotulo, conversas: [] as ConversaResumo[] }));
  const ordenadas = [...conversas].sort(
    (a, b) => Date.parse(b.ultimaAtividadeEm || b.dataCriacao) - Date.parse(a.ultimaAtividadeEm || a.dataCriacao),
  );
  for (const conversa of ordenadas) {
    const quando = Date.parse(conversa.ultimaAtividadeEm || conversa.dataCriacao);
    const indice = limites.findIndex((limite) => quando >= limite.desde);
    grupos[indice < 0 ? grupos.length - 1 : indice].conversas.push(conversa);
  }
  return grupos.filter((grupo) => grupo.conversas.length > 0);
}

export function formatarTamanho(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${(bytes / (1024 * 1024)).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`;
}

/**
 * Estado do workspace do Copiloto. A execução do agente roda no servidor desacoplada da conexão:
 * se o stream cair, o store retoma do último pacote recebido; se o usuário trocar de conversa, a
 * resposta continua e pode ser acompanhada ao voltar.
 */
@Injectable({ providedIn: 'root' })
export class CopilotoStore {
  private readonly api = inject(AssistenteService);
  private readonly auth = inject(AuthService);
  private readonly notificacao = inject(NotificationService);

  readonly capacidades = signal<CapacidadesAssistente | null>(null);
  readonly erroCapacidades = signal<string | null>(null);
  readonly conversas = signal<ConversaResumo[]>([]);
  readonly carregandoConversas = signal(false);
  readonly erroConversas = signal<string | null>(null);
  readonly busca = signal('');

  readonly conversaId = signal<string | null>(null);
  readonly titulo = signal('Nova conversa');
  readonly contexto = signal<AssistenteContexto | null>(null);
  readonly mensagens = signal<MensagemView[]>([]);
  readonly artefatos = signal<ArtefatoAssistente[]>([]);
  readonly artefatoAberto = signal<string | null>(null);
  readonly carregandoConversa = signal(false);
  readonly erroConversa = signal<string | null>(null);
  /** Estado atual dos cartões de confirmação da conversa, por acaoId (o stream só traz a prévia inicial). */
  readonly acoesCartao = signal<Record<string, AcaoCartao>>({});

  readonly anexos = signal<AnexoPendente[]>([]);
  readonly transmitindo = signal(false);
  readonly parando = signal(false);
  readonly reconectando = signal(false);
  readonly anuncio = signal('');

  readonly vazia = computed(() => this.mensagens().length === 0);
  readonly grupos = computed(() => agruparConversas(this.conversas()));
  readonly enviandoAnexos = computed(() => this.anexos().some((anexo) => anexo.estado === 'enviando'));
  readonly artefatoSelecionado = computed(() => {
    const codigo = this.artefatoAberto();
    return codigo ? (this.artefatos().find((item) => item.codigo === codigo) ?? null) : null;
  });
  readonly disponivel = computed(() => this.capacidades()?.disponivel ?? true);

  /**
   * Fonte do contexto de cada pergunta. O workspace envia o contexto de origem só na primeira mensagem; o
   * painel contextual de um dashboard registra uma fonte que devolve o recorte **atual** da tela a cada envio.
   */
  private fonteContexto: (() => AssistenteContexto | null) | null = null;
  private controlador: AbortController | null = null;
  private geracao = 0;
  private readonly imagens = new Map<string, Promise<string>>();
  private usuarioAnterior: string | null | undefined;

  constructor() {
    effect(() => {
      const usuario = this.auth.currentUser()?.id ?? null;
      if (this.usuarioAnterior !== undefined && this.usuarioAnterior !== usuario) {
        untracked(() => this.redefinir());
      }
      this.usuarioAnterior = usuario;
    });
  }

  private redefinir(): void {
    this.controlador?.abort();
    this.controlador = null;
    this.geracao++;
    this.capacidades.set(null);
    this.erroCapacidades.set(null);
    this.conversas.set([]);
    this.carregandoConversas.set(false);
    this.erroConversas.set(null);
    this.busca.set('');
    this.conversaId.set(null);
    this.titulo.set('Nova conversa');
    this.contexto.set(null);
    this.mensagens.set([]);
    this.artefatos.set([]);
    this.artefatoAberto.set(null);
    this.acoesCartao.set({});
    this.carregandoConversa.set(false);
    this.erroConversa.set(null);
    this.anexos.set([]);
    this.transmitindo.set(false);
    this.parando.set(false);
    this.reconectando.set(false);
    this.anuncio.set('');
    for (const url of this.imagens.values()) url.then((valor) => URL.revokeObjectURL(valor)).catch(() => undefined);
    this.imagens.clear();
  }

  definirFonteContexto(fonte: (() => AssistenteContexto | null) | null): void {
    this.fonteContexto = fonte;
  }

  /**
   * Solta leituras de stream pendentes quando a superfície que usa esta instância é destruída (ex.: sair do
   * dashboard). A execução continua no servidor e pode ser retomada em qualquer superfície.
   */
  encerrar(): void {
    this.desanexarStream();
    this.geracao++;
    this.fonteContexto = null;
  }

  // ---------------------------------------------------------------------------------- conversas

  async iniciar(): Promise<void> {
    await Promise.all([this.carregarCapacidades(), this.carregarConversas()]);
  }

  async carregarCapacidades(): Promise<void> {
    try {
      this.capacidades.set(await firstValueFrom(this.api.capacidades()));
      this.erroCapacidades.set(null);
    } catch (erro) {
      this.erroCapacidades.set(getErrorMessage(erro));
    }
  }

  async carregarConversas(): Promise<void> {
    this.carregandoConversas.set(true);
    try {
      const pagina = await firstValueFrom(this.api.listarConversas(0, 80, this.busca()));
      this.conversas.set(pagina.content);
      this.erroConversas.set(null);
    } catch (erro) {
      this.erroConversas.set(getErrorMessage(erro));
    } finally {
      this.carregandoConversas.set(false);
    }
  }

  buscar(termo: string): void {
    this.busca.set(termo);
    void this.carregarConversas();
  }

  novaConversa(contexto: AssistenteContexto | null = null): void {
    this.desanexarStream();
    this.geracao++;
    this.conversaId.set(null);
    this.titulo.set('Nova conversa');
    this.contexto.set(contexto);
    this.mensagens.set([]);
    this.artefatos.set([]);
    this.artefatoAberto.set(null);
    this.acoesCartao.set({});
    this.erroConversa.set(null);
    this.carregandoConversa.set(false);
  }

  async abrirConversa(id: string): Promise<void> {
    if (this.conversaId() === id && !this.erroConversa() && this.mensagens().length) return;
    this.desanexarStream();
    const geracao = ++this.geracao;
    this.conversaId.set(id);
    this.carregandoConversa.set(true);
    this.erroConversa.set(null);
    this.artefatoAberto.set(null);
    try {
      const detalhe = await firstValueFrom(this.api.buscarConversa(id));
      if (geracao !== this.geracao) return;
      this.aplicarDetalhe(detalhe);
      void this.carregarAcoes(id, geracao);
      if (detalhe.execucaoEmAndamento) {
        void this.acompanhar(id, detalhe.execucaoEmAndamento, geracao);
      }
    } catch (erro) {
      if (geracao !== this.geracao) return;
      this.mensagens.set([]);
      this.erroConversa.set(getErrorMessage(erro));
    } finally {
      if (geracao === this.geracao) this.carregandoConversa.set(false);
    }
  }

  /** Atualiza o estado de um cartão depois de revisar, confirmar ou cancelar. */
  atualizarAcao(acao: AcaoCartao): void {
    this.acoesCartao.update((atual) => ({ ...atual, [acao.acaoId]: acao }));
  }

  private async carregarAcoes(conversaId: string, geracao: number): Promise<void> {
    try {
      const acoes = await firstValueFrom(this.api.listarAcoes(conversaId));
      if (geracao !== this.geracao) return;
      const mapa: Record<string, AcaoCartao> = {};
      for (const acao of acoes) mapa[acao.acaoId] = acao;
      this.acoesCartao.set(mapa);
    } catch {
      // Sem o estado atual, o cartão mostra a prévia do histórico; a confirmação revalida no servidor.
    }
  }

  async renomear(id: string, titulo: string): Promise<boolean> {
    const limpo = titulo.trim();
    if (!limpo) return false;
    try {
      const atualizada = await firstValueFrom(this.api.renomearConversa(id, limpo));
      this.conversas.update((lista) => lista.map((item) => (item.id === id ? { ...item, titulo: atualizada.titulo } : item)));
      if (this.conversaId() === id) this.titulo.set(atualizada.titulo);
      return true;
    } catch (erro) {
      this.notificacao.error(getErrorMessage(erro));
      return false;
    }
  }

  async excluir(id: string): Promise<boolean> {
    try {
      await firstValueFrom(this.api.excluirConversa(id));
      this.conversas.update((lista) => lista.filter((item) => item.id !== id));
      if (this.conversaId() === id) this.novaConversa();
      this.notificacao.success('Conversa excluída.');
      return true;
    } catch (erro) {
      this.notificacao.error(getErrorMessage(erro));
      return false;
    }
  }

  // ---------------------------------------------------------------------------------- mensagens

  /** Envia a pergunta. Cria a conversa na primeira mensagem (evita conversas vazias no histórico). */
  async enviar(texto: string): Promise<boolean> {
    const conteudo = texto.trim();
    if (!conteudo || this.transmitindo() || this.enviandoAnexos()) return false;
    const prontos = this.anexos().filter((anexo) => anexo.estado === 'pronto' && anexo.arquivo);
    const geracao = this.geracao;
    let conversaId = this.conversaId();
    const primeira = this.mensagens().length === 0;
    const contextoEnvio = this.fonteContexto ? this.fonteContexto() : primeira ? this.contexto() : null;
    if (this.fonteContexto && contextoEnvio) this.contexto.set(contextoEnvio);
    this.transmitindo.set(true);
    try {
      if (!conversaId) {
        const criada = await firstValueFrom(this.api.criarConversa({ contexto: this.contexto() ?? undefined }));
        if (geracao !== this.geracao) return false;
        conversaId = criada.id;
        this.conversaId.set(criada.id);
        this.conversas.update((lista) => [criada, ...lista.filter((item) => item.id !== criada.id)]);
      }
    } catch (erro) {
      this.transmitindo.set(false);
      this.notificacao.error(getErrorMessage(erro));
      return false;
    }

    const clientMessageId = crypto.randomUUID();
    const chave = `a-${clientMessageId}`;
    this.mensagens.update((lista) => [
      ...lista,
      {
        chave: `u-${clientMessageId}`,
        papel: 'USUARIO',
        texto: conteudo,
        anexos: prontos.map((anexo) => anexo.arquivo!),
        dataCriacao: new Date().toISOString(),
        contexto: contextoEnvio,
      },
      { chave, papel: 'ASSISTENTE', anexos: [], resposta: novaResposta(), dataCriacao: new Date().toISOString() },
    ]);
    this.anexos.set([]);
    this.anuncio.set('Pergunta enviada. O Copiloto está trabalhando.');
    this.tocarConversa(conversaId);

    const controlador = new AbortController();
    this.controlador = controlador;
    try {
      await this.api.transmitir(
        conversaId,
        {
          clientMessageId,
          conteudo,
          arquivos: prontos.map((anexo) => anexo.arquivo!.id),
          contexto: contextoEnvio ?? undefined,
        },
        (pacote) => this.aoPacote(chave, pacote, geracao),
        controlador.signal,
      );
      await this.retomarSeNecessario(conversaId, chave, geracao, controlador);
    } catch (erro) {
      if (controlador.signal.aborted) return true;
      if (erro instanceof AssistenteStreamError) {
        this.falhar(chave, erro.message);
      } else {
        await this.retomarSeNecessario(conversaId, chave, geracao, controlador);
      }
    } finally {
      this.finalizarTransmissao(controlador);
    }
    return true;
  }

  async parar(): Promise<void> {
    const id = this.conversaId();
    if (!id || !this.transmitindo() || this.parando()) return;
    this.parando.set(true);
    try {
      await firstValueFrom(this.api.parar(id));
      this.anuncio.set('Interrompendo a geração.');
    } catch (erro) {
      this.notificacao.error(getErrorMessage(erro));
      this.parando.set(false);
    }
  }

  // ---------------------------------------------------------------------------------- anexos

  anexar(arquivos: readonly File[]): void {
    const limites = this.capacidades()?.limites;
    const maximoBytes = limites?.arquivoMaxBytes ?? 15 * 1024 * 1024;
    const maximoArquivos = limites?.anexosPorMensagem ?? 10;
    for (const arquivo of arquivos) {
      if (this.anexos().length >= maximoArquivos) {
        this.notificacao.warning(`Envie no máximo ${maximoArquivos} arquivos por mensagem.`);
        break;
      }
      const extensao = arquivo.name.includes('.') ? arquivo.name.split('.').pop()!.toLowerCase() : '';
      if (!EXTENSOES_ACEITAS.includes(extensao)) {
        this.notificacao.warning(`"${arquivo.name}" não é um formato aceito. Use PDF, planilhas, CSV, JSON, texto, DOCX ou imagens.`);
        continue;
      }
      if (arquivo.size > maximoBytes) {
        this.notificacao.warning(`"${arquivo.name}" excede o limite de ${formatarTamanho(maximoBytes)}.`);
        continue;
      }
      const idLocal = crypto.randomUUID();
      this.anexos.update((lista) => [
        ...lista,
        { idLocal, nome: arquivo.name, tamanho: arquivo.size, progresso: 0, estado: 'enviando' },
      ]);
      this.api.enviarArquivo(arquivo).subscribe({
        next: (evento) => {
          if (evento.type === HttpEventType.UploadProgress && evento.total) {
            const progresso = Math.round((evento.loaded / evento.total) * 100);
            this.atualizarAnexo(idLocal, { progresso });
          } else if (evento.type === HttpEventType.Response && evento.body) {
            const enviado = evento.body;
            this.atualizarAnexo(idLocal, {
              estado: 'pronto',
              progresso: 100,
              arquivo: enviado,
              erro: enviado.status === 'FALHOU' ? enviado.erro : undefined,
            });
          }
        },
        error: (erro) => this.atualizarAnexo(idLocal, { estado: 'erro', erro: getErrorMessage(erro) }),
      });
    }
  }

  removerAnexo(idLocal: string): void {
    const anexo = this.anexos().find((item) => item.idLocal === idLocal);
    this.anexos.update((lista) => lista.filter((item) => item.idLocal !== idLocal));
    if (anexo?.arquivo) this.api.removerArquivo(anexo.arquivo.id).subscribe({ error: () => undefined });
  }

  // ---------------------------------------------------------------------------------- arquivos

  async baixar(arquivo: Pick<ArquivoAssistente, 'id' | 'nome'>): Promise<void> {
    try {
      const blob = await firstValueFrom(this.api.baixarArquivo(arquivo.id, true));
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = arquivo.nome;
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 30_000);
    } catch (erro) {
      this.notificacao.error(getErrorMessage(erro));
    }
  }

  async abrirEmNovaAba(arquivo: Pick<ArquivoAssistente, 'id' | 'nome'>): Promise<void> {
    const janela = window.open('', '_blank');
    try {
      const blob = await firstValueFrom(this.api.baixarArquivo(arquivo.id, false));
      const url = URL.createObjectURL(blob);
      if (janela) janela.location.href = url;
      else window.open(url, '_blank', 'noopener');
      setTimeout(() => URL.revokeObjectURL(url), 120_000);
    } catch (erro) {
      janela?.close();
      this.notificacao.error(getErrorMessage(erro));
    }
  }

  /** URL local (blob) de uma imagem gerada ou anexada, com cache por arquivo. */
  urlImagem(arquivoId: string): Promise<string> {
    let url = this.imagens.get(arquivoId);
    if (!url) {
      url = firstValueFrom(this.api.baixarArquivo(arquivoId, false)).then((blob) => URL.createObjectURL(blob));
      url.catch(() => this.imagens.delete(arquivoId));
      this.imagens.set(arquivoId, url);
    }
    return url;
  }

  abrirArtefato(codigo: string | null): void {
    this.artefatoAberto.set(codigo);
  }

  // ---------------------------------------------------------------------------------- interno

  private aplicarDetalhe(detalhe: ConversaDetalhe): void {
    this.titulo.set(detalhe.titulo);
    this.contexto.set(detalhe.contexto ?? null);
    this.artefatos.set(detalhe.artefatos ?? []);
    this.mensagens.set((detalhe.mensagens ?? []).map((mensagem) => this.paraView(mensagem)));
  }

  private paraView(mensagem: MensagemAssistente): MensagemView {
    if (mensagem.papel === 'USUARIO') {
      return {
        chave: `u-${mensagem.clientMessageId}`,
        papel: 'USUARIO',
        texto: mensagem.conteudo,
        anexos: mensagem.arquivos ?? [],
        dataCriacao: mensagem.dataCriacao,
        contexto: mensagem.contexto ?? null,
      };
    }
    return {
      chave: `a-${mensagem.clientMessageId}`,
      papel: 'ASSISTENTE',
      anexos: [],
      resposta: mensagem.status === 'PROCESSANDO' ? novaResposta() : respostaDaMensagem(mensagem),
      dataCriacao: mensagem.dataCriacao,
      metricas: mensagem.metricas,
    };
  }

  /** Acompanha uma execução em andamento ao abrir a conversa (ex.: após recarregar a página). */
  private async acompanhar(conversaId: string, mensagemId: string, geracao: number): Promise<void> {
    const alvo = this.mensagens().find((mensagem) => mensagem.papel === 'ASSISTENTE' && !mensagem.resposta?.concluida
      && (mensagem.resposta?.mensagemId === undefined || mensagem.resposta.mensagemId === mensagemId));
    const chave = alvo?.chave ?? `a-${mensagemId}`;
    if (!alvo) {
      this.mensagens.update((lista) => [...lista, { chave, papel: 'ASSISTENTE', anexos: [], resposta: novaResposta() }]);
    }
    const controlador = new AbortController();
    this.controlador = controlador;
    this.transmitindo.set(true);
    try {
      await this.api.retomar(conversaId, 0, (pacote) => this.aoPacote(chave, pacote, geracao), controlador.signal);
      await this.retomarSeNecessario(conversaId, chave, geracao, controlador);
    } catch {
      if (!controlador.signal.aborted) await this.retomarSeNecessario(conversaId, chave, geracao, controlador);
    } finally {
      this.finalizarTransmissao(controlador);
    }
  }

  private async retomarSeNecessario(conversaId: string, chave: string, geracao: number, controlador: AbortController) {
    for (let tentativa = 1; tentativa <= TENTATIVAS_RETOMADA; tentativa++) {
      if (controlador.signal.aborted || geracao !== this.geracao) return;
      const resposta = this.resposta(chave);
      if (!resposta || resposta.concluida) return;
      this.reconectando.set(true);
      await new Promise((resolver) => setTimeout(resolver, 600 * tentativa));
      try {
        await this.api.retomar(conversaId, resposta.recebidos, (pacote) => this.aoPacote(chave, pacote, geracao),
          controlador.signal);
      } catch (erro) {
        if (erro instanceof AssistenteStreamError && erro.status === 404) break;
      }
    }
    this.reconectando.set(false);
    const resposta = this.resposta(chave);
    if (resposta && !resposta.concluida && !controlador.signal.aborted && geracao === this.geracao) {
      // A execução terminou sem que o stream chegasse ao fim: recarrega o que foi persistido.
      try {
        const detalhe = await firstValueFrom(this.api.buscarConversa(conversaId));
        if (geracao === this.geracao) this.aplicarDetalhe(detalhe);
      } catch {
        this.falhar(chave, 'A conexão com o Copiloto caiu. Recarregue a conversa para ver a resposta.');
      }
    }
  }

  private aoPacote(chave: string, pacote: PacoteAssistente, geracao: number): void {
    if (geracao !== this.geracao) return;
    this.reconectando.set(false);
    this.mensagens.update((lista) => lista.map((mensagem) =>
      mensagem.chave === chave && mensagem.resposta
        ? { ...mensagem, resposta: aplicarPacote(mensagem.resposta, pacote) }
        : mensagem));
    const obj = pacote.obj;
    switch (obj.type) {
      case 'action_proposal': {
        // Um cartão novo pode substituir outro da mesma operação: atualiza o estado dos anteriores.
        const id = this.conversaId();
        if (id) void this.carregarAcoes(id, geracao);
        break;
      }
      case 'title': {
        const titulo = typeof obj['titulo'] === 'string' ? obj['titulo'] : null;
        const id = this.conversaId();
        if (titulo && id) {
          this.titulo.set(titulo);
          this.conversas.update((lista) => lista.map((item) => (item.id === id ? { ...item, titulo } : item)));
        }
        break;
      }
      case 'artifact': {
        const artefato = obj['artefato'] as ArtefatoAssistente | undefined;
        if (artefato?.codigo) {
          this.artefatos.update((lista) => [...lista.filter((item) => item.codigo !== artefato.codigo), artefato]);
          this.artefatoAberto.set(artefato.codigo);
          this.anuncio.set(`Artefato criado: ${artefato.titulo}.`);
        }
        break;
      }
      case 'stop': {
        const status = obj['status'];
        this.anuncio.set(status === 'CONCLUIDA' ? 'Resposta concluída.'
          : status === 'CANCELADA' ? 'Geração interrompida.' : 'A resposta não pôde ser concluída.');
        break;
      }
      default:
        break;
    }
  }

  private resposta(chave: string): RespostaView | undefined {
    return this.mensagens().find((mensagem) => mensagem.chave === chave)?.resposta;
  }

  private falhar(chave: string, mensagem: string): void {
    this.mensagens.update((lista) => lista.map((item): MensagemView =>
      item.chave === chave && item.resposta
        ? { ...item, resposta: { ...item.resposta, status: 'FALHOU', erro: mensagem, concluida: true } }
        : item));
    this.anuncio.set(mensagem);
  }

  private finalizarTransmissao(controlador: AbortController): void {
    if (this.controlador !== controlador) return;
    this.controlador = null;
    this.transmitindo.set(false);
    this.parando.set(false);
    this.reconectando.set(false);
    const id = this.conversaId();
    if (id) this.tocarConversa(id, false);
  }

  /** Solta a leitura do stream sem interromper a execução no servidor. */
  private desanexarStream(): void {
    this.controlador?.abort();
    this.controlador = null;
    this.transmitindo.set(false);
    this.parando.set(false);
    this.reconectando.set(false);
    this.anexos.set([]);
  }

  private tocarConversa(id: string, emAndamento = true): void {
    const agora = new Date().toISOString();
    this.conversas.update((lista) => {
      const atual = lista.find((item) => item.id === id);
      if (!atual) return lista;
      return [{ ...atual, ultimaAtividadeEm: agora, emAndamento }, ...lista.filter((item) => item.id !== id)];
    });
  }

  private atualizarAnexo(idLocal: string, alteracao: Partial<AnexoPendente>): void {
    this.anexos.update((lista) => lista.map((anexo) => (anexo.idLocal === idLocal ? { ...anexo, ...alteracao } : anexo)));
  }
}
