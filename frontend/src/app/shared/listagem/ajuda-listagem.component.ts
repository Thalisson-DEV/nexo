import { ChangeDetectionStrategy, Component, computed, inject, input, viewChild } from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { HelpDialogComponent } from '../components/help-dialog/help-dialog.component';

/**
 * Ajuda padrão das telas de listagem: como encontrar (busca única, filtros recolhidos, atalhos),
 * cadastrar/editar em painel lateral, ações da linha, exportação e, quando houver, importação e
 * vídeo. Conteúdo específico da tela entra por projeção.
 */
@Component({
  selector: 'app-ajuda-listagem',
  standalone: true,
  imports: [HelpDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-help-dialog #dialogo [title]="'Ajuda: ' + titulo()">
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-search"></i> Encontrar {{ artigoPlural() }} {{ entidadePlural() }}</h4>
        <p class="help-text-desc">
          A lista já abre com os dados. Digite na busca da tabela ({{ busca() }}); para outros recortes, abra
          <strong>Filtros</strong>. Os filtros aplicados aparecem como etiquetas e saem no "x". Clique no título de
          uma coluna para ordenar e use o rodapé para mudar de página.
        </p>
      </div>
      @if (podeCriar()) {
        <div class="help-section">
          <h4 class="help-section-title"><i class="pi pi-plus-circle"></i> Cadastrar e editar</h4>
          <p class="help-text-desc">
            <strong>{{ rotuloNovo() }}</strong> abre o formulário ao lado da lista; a ação da linha abre a edição.
            Campos obrigatórios têm asterisco. Fechar com alterações pede confirmação.
          </p>
        </div>
      }
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-table"></i> Ações da linha e exportação</h4>
        <p class="help-text-desc">
          Os ícones da linha executam as demais ações (ativar/desativar pede confirmação; o relógio mostra o
          histórico de alterações). <strong>Exportar CSV</strong> baixa o recorte atual.
        </p>
      </div>
      @if (importacao()) {
        <div class="help-section">
          <h4 class="help-section-title"><i class="pi pi-upload"></i> Importar planilha</h4>
          <p class="help-text-desc">
            <strong>Importar</strong> abre o painel de importação: baixe o modelo, escolha o arquivo, corrija as linhas
            com erro e processe. A lista continua visível.
          </p>
        </div>
      }
      <ng-content></ng-content>
      <div class="help-section">
        <h4 class="help-section-title"><i class="pi pi-bolt"></i> Atalhos</h4>
        <p class="help-text-desc">
          <strong>/</strong> busca, <strong>F</strong> filtros{{ podeCriar() ? ', N novo registro' : '' }}. Funcionam fora dos campos
          e podem ser desligados em "Atalhos de uma tecla", no menu do usuário.
        </p>
      </div>
      @if (videoSeguro(); as url) {
        <div class="help-section">
          <h4 class="help-section-title"><i class="pi pi-video"></i> Vídeo tutorial</h4>
          <iframe width="100%" height="215" [src]="url" [title]="'Vídeo tutorial: ' + titulo()" frameborder="0"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
            referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
        </div>
      }
    </app-help-dialog>
  `
})
export class AjudaListagemComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly dialogo = viewChild.required<HelpDialogComponent>('dialogo');

  readonly titulo = input.required<string>();
  /** Ex.: "municípios". */
  readonly entidadePlural = input.required<string>();
  readonly artigoPlural = input<'os' | 'as'>('os');
  /** O que a busca procura, ex.: "nome do município". */
  readonly busca = input('nome');
  readonly podeCriar = input(true);
  readonly rotuloNovo = input('Novo');
  readonly importacao = input(false);
  /** Endereço de incorporação do YouTube (`https://www.youtube.com/embed/...`). */
  readonly video = input<string | undefined>();

  protected readonly videoSeguro = computed<SafeResourceUrl | null>(() => {
    const url = this.video();
    return url && url.startsWith('https://www.youtube.com/embed/') ? this.sanitizer.bypassSecurityTrustResourceUrl(url) : null;
  });

  show(): void {
    this.dialogo().show();
  }
}
