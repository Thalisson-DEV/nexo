import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { CesButtonComponent, CesIconButtonComponent, CesStatusComponent } from '@ces/ui';
import { CesCalloutComponent, NotificationService } from '@ces/ui/feedback';
import { ArquivoAssistente, ArtefatoAssistente, AssistenteAcao } from '../../../core/models/assistente.models';
import { AuthService } from '../../../core/services/auth.service';
import { MensagemView, CopilotoStore, formatarTamanho } from '../copiloto.store';
import { PropostaAcaoView, observacoes, textoFinal } from '../copiloto-pacotes';
import { ICONE_ARTEFATO, ROTULO_ARTEFATO, formatarDuracao } from '../copiloto-formatos';
import { resolveChatNavigation } from '../navegacao/copiloto-navegacao';
import { resumoRecorte } from '../contexto/copiloto-contexto';
import { FiltroSugerido, resolverFiltroSugerido } from '../contexto/copiloto-filtro';
import { CopilotoPainelService } from '../painel/copiloto-painel.service';
import { CopilotoCartaoCadastroComponent } from './copiloto-cartao-cadastro.component';
import { CopilotoEtapasComponent } from './copiloto-etapas.component';
import { CopilotoMarkdownComponent } from './copiloto-markdown.component';

@Component({
  selector: 'app-copiloto-mensagem',
  standalone: true,
  imports: [
    CesButtonComponent,
    CesIconButtonComponent,
    CesStatusComponent,
    CesCalloutComponent,
    CopilotoCartaoCadastroComponent,
    CopilotoEtapasComponent,
    CopilotoMarkdownComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './copiloto-mensagem.component.html',
  styleUrl: './copiloto-mensagem.component.css',
})
export class CopilotoMensagemComponent {
  private readonly store = inject(CopilotoStore);
  private readonly router = inject(Router);
  private readonly auth = inject(AuthService);
  private readonly notificacao = inject(NotificationService);
  private readonly painel = inject(CopilotoPainelService);

  readonly mensagem = input.required<MensagemView>();
  readonly abrirArtefato = output<string>();

  protected readonly imagens = signal<Record<string, string>>({});

  protected readonly resposta = computed(() => this.mensagem().resposta ?? null);
  /** Recorte do painel usado nesta pergunta (período, filtros, foco) — igual no painel e no workspace. */
  protected readonly recorte = computed(() => {
    const painel = this.mensagem().contexto?.painel;
    return painel ? { painel: painel.nome, resumo: resumoRecorte(painel) } : null;
  });
  protected readonly texto = computed(() => {
    const resposta = this.resposta();
    return resposta ? textoFinal(resposta) : '';
  });
  protected readonly notas = computed(() => {
    const resposta = this.resposta();
    return resposta ? observacoes(resposta) : [];
  });
  protected readonly emAndamento = computed(() => {
    const resposta = this.resposta();
    return !!resposta && !resposta.concluida;
  });
  protected readonly pensando = computed(() => this.emAndamento() && !this.texto() && !this.resposta()?.etapas.length);
  protected readonly imagensGeradas = computed(() =>
    (this.resposta()?.arquivos ?? []).filter((arquivo) => arquivo.categoria === 'IMAGEM'),
  );
  protected readonly demaisArquivos = computed(() =>
    (this.resposta()?.arquivos ?? []).filter((arquivo) => arquivo.categoria !== 'IMAGEM'),
  );
  protected readonly acoes = computed(() => {
    const permissoes = this.auth.currentUser()?.permissoes ?? [];
    return (this.resposta()?.acoes ?? [])
      .map((acao) => ({ acao, destino: resolveChatNavigation(acao, permissoes) }))
      .filter((item) => item.destino !== null);
  });
  /**
   * Filtros sugeridos (`APPLY_FILTER`) que valem para a tabela em tela agora. Fora do painel de origem
   * (workspace, outro dashboard) não aparecem: o filtro só faz sentido onde a tabela existe.
   */
  protected readonly filtrosSugeridos = computed(() => {
    const tabelas = this.painel.contextoAtivo()?.tabelas;
    return (this.resposta()?.acoes ?? [])
      .map((acao) => resolverFiltroSugerido(acao, tabelas))
      .filter((filtro): filtro is FiltroSugerido => filtro !== null)
      .map((filtro) => ({
        filtro,
        resumo: filtro.filtros.map((item) => item.label ?? item.key).join(' e '),
      }));
  });

  protected readonly rodape = computed(() => {
    const resposta = this.resposta();
    if (!resposta?.concluida) return '';
    const partes: string[] = [];
    const duracao = formatarDuracao(resposta.duracaoMs ?? this.mensagem().metricas?.duracaoMs);
    if (resposta.status === 'CONCLUIDA' && duracao) partes.push(`Concluída em ${duracao}`);
    const ferramentas = resposta.etapas.length;
    if (ferramentas) partes.push(`${ferramentas} ${ferramentas === 1 ? 'ferramenta' : 'ferramentas'}`);
    return partes.join(' · ');
  });

  protected readonly iconeArtefato = ICONE_ARTEFATO;
  protected readonly rotuloArtefato = ROTULO_ARTEFATO;
  protected readonly formatarTamanho = formatarTamanho;

  constructor() {
    effect(() => {
      for (const imagem of [...this.imagensGeradas(), ...this.mensagem().anexos.filter((a) => a.categoria === 'IMAGEM')]) {
        if (this.imagens()[imagem.id]) continue;
        this.store
          .urlImagem(imagem.id)
          .then((url) => this.imagens.update((atual) => ({ ...atual, [imagem.id]: url })))
          .catch(() => undefined);
      }
    });
  }

  protected baixar(arquivo: ArquivoAssistente): void {
    void this.store.baixar(arquivo);
  }

  protected abrir(artefato: ArtefatoAssistente): void {
    this.abrirArtefato.emit(artefato.codigo);
  }

  /** Cartões refeitos na mesma resposta: mostra só o último de cada cadastro, sem a fila de "sem efeito". */
  protected readonly propostas = computed(() => {
    const lista = this.resposta()?.propostas ?? [];
    const estados = this.store.acoesCartao();
    return lista.filter((proposta, i) => {
      if (estados[proposta.acaoId]?.status !== 'EXPIRADA') return true;
      const chave = (proposta.previa as { chave?: string } | null)?.chave;
      return !chave || !lista.slice(i + 1).some((p) => (p.previa as { chave?: string } | null)?.chave === chave);
    });
  });

  protected ehCadastro(proposta: PropostaAcaoView): boolean {
    const previa = proposta.previa as { tipo?: string } | null | undefined;
    return previa?.tipo === 'cadastro' || !!this.store.acoesCartao()[proposta.acaoId]?.previa;
  }

  protected navegar(acao: AssistenteAcao): void {
    const destino = resolveChatNavigation(acao, this.auth.currentUser()?.permissoes ?? []);
    if (destino) void this.router.navigate([destino.route], { queryParams: destino.queryParams });
  }

  protected aplicarFiltro(filtro: FiltroSugerido): void {
    this.painel.aplicarFiltro(filtro);
    this.notificacao.success('Filtro aplicado na tabela. Remova o chip para desfazer.');
  }

  protected async copiar(): Promise<void> {
    try {
      await navigator.clipboard.writeText(this.texto());
      this.notificacao.success('Resposta copiada.');
    } catch {
      this.notificacao.warning('Não foi possível copiar automaticamente. Selecione o texto e copie.');
    }
  }

  protected iconeArquivo(arquivo: ArquivoAssistente): string {
    switch (arquivo.categoria) {
      case 'PDF':
        return 'pi pi-file-pdf';
      case 'PLANILHA':
      case 'CSV':
        return 'pi pi-file-excel';
      case 'IMAGEM':
        return 'pi pi-image';
      default:
        return 'pi pi-file';
    }
  }
}
