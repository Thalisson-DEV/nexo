import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesIconButtonComponent,
  CesPageHeaderComponent,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService
} from '@ces/ui';
import { HelpDialogComponent } from '../../shared/components/help-dialog/help-dialog.component';
import { ManualTreinamentoDocumentoComponent } from './manual-treinamento-documento.component';
import { iconeAlerta, iconeContexto, rotuloAlerta, tomAlerta } from './central-treinamento-ui';
import { MODULOS_TREINAMENTO } from './central-treinamento.data';
import { AlertaManual, ContextoTag, ManualModulo } from './central-treinamento.models';

@Component({
  selector: 'app-central-treinamento',
  standalone: true,
  imports: [
    RouterLink,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesIconButtonComponent,
    CesStatusComponent,
    CesPageHeaderComponent,
    CesTextInputComponent,
    HelpDialogComponent,
    ManualTreinamentoDocumentoComponent
  ],
  templateUrl: './central-treinamento.component.html',
  styleUrl: './central-treinamento.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class CentralTreinamentoComponent {
  private readonly sanitizer = inject(DomSanitizer);
  private readonly notifications = inject(NotificationService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly router = inject(Router);

  protected readonly breadcrumb = [
    { label: 'Treinamento' },
    { label: 'Central de treinamento' }
  ];

  readonly termoBusca = signal<string>('');

  /** Todo módulo começa recolhido: o manual abre como um sumário navegável. */
  readonly modulosAbertos = signal<readonly string[]>([]);

  protected readonly rotuloAlerta = rotuloAlerta;
  protected readonly iconeAlerta = iconeAlerta;

  readonly modulos = computed<ManualModulo[]>(() =>
    MODULOS_TREINAMENTO.map((modulo) => ({
      ...modulo,
      safeEmbedUrl: this.sanitizer.bypassSecurityTrustResourceUrl(
        `https://www.youtube-nocookie.com/embed/${modulo.videoId}?rel=0`
      )
    }))
  );

  readonly modulosFiltrados = computed<ManualModulo[]>(() => {
    const termo = this.termoBusca().trim().toLowerCase();
    if (!termo) return this.modulos();
    return this.modulos().filter((modulo) => this.moduloAtendeTermo(modulo, termo));
  });

  readonly totalModulos = computed(() => this.modulos().length);
  readonly totalFiltrados = computed(() => this.modulosFiltrados().length);
  readonly temBusca = computed(() => !!this.termoBusca().trim());

  readonly escopoImpressao = computed(() => {
    const termo = this.termoBusca().trim();
    return termo ? `Busca "${termo}"` : 'Manual completo';
  });

  readonly todosAbertos = computed(() => {
    const visiveis = this.modulosFiltrados();
    return visiveis.length > 0 && visiveis.every((modulo) => this.estaAberto(modulo.id));
  });

  estaAberto(id: string): boolean {
    return this.modulosAbertos().includes(id);
  }

  alternarModulo(id: string): void {
    const abertos = this.modulosAbertos();
    this.modulosAbertos.set(
      abertos.includes(id) ? abertos.filter((item) => item !== id) : [...abertos, id]
    );
  }

  /** Sumário: abre o módulo escolhido e leva a leitura até ele. */
  abrirModulo(id: string): void {
    if (!this.estaAberto(id)) {
      this.modulosAbertos.set([...this.modulosAbertos(), id]);
    }
    this.rolarAteModulo(id);
  }

  alternarTodos(): void {
    if (this.todosAbertos()) {
      this.modulosAbertos.set([]);
      return;
    }
    this.modulosAbertos.set(this.modulosFiltrados().map((modulo) => modulo.id));
  }

  digitarBusca(termo: string): void {
    this.termoBusca.set(termo);
    // Com busca ativa, abrir o que casou evita que o usuário tenha de expandir na mão.
    if (termo.trim().length >= 2) {
      this.modulosAbertos.set(this.modulosFiltrados().map((modulo) => modulo.id));
    }
  }

  limparBusca(): void {
    this.termoBusca.set('');
    this.modulosAbertos.set([]);
  }

  async copiarLink(): Promise<void> {
    const endereco = typeof window === 'undefined' ? '' : window.location.href;
    try {
      await navigator.clipboard.writeText(endereco);
      this.notifications.success('O endereço do manual está na área de transferência.', {
        title: 'Link copiado'
      });
    } catch {
      this.notifications.warning('Copie o endereço diretamente da barra do navegador.', {
        title: 'Não foi possível copiar'
      });
    }
  }

  imprimir(): void {
    window.print();
  }

  /** Caminho oficial de dúvida: o chamado fica registrado e tem acompanhamento. */
  abrirChamado(): void {
    void this.router.navigate(['/chamados/novo']);
  }

  protected tomAlerta(alerta: AlertaManual) {
    return tomAlerta(alerta.tipo);
  }

  protected iconeContexto(contexto: ContextoTag): string {
    return iconeContexto(contexto);
  }

  /**
   * Navegação interna por rolagem, e não por fragmento de URL: `href="#id"` altera a URL e
   * entra no processamento de rotas do shell, que cai na rota curinga e volta para a home.
   */
  private rolarAteModulo(id: string): void {
    const elemento = Array.from(
      this.host.nativeElement.querySelectorAll<HTMLElement>('[data-modulo]')
    ).find((candidato) => candidato.dataset['modulo'] === id);

    if (!elemento || typeof elemento.scrollIntoView !== 'function') return;

    const preferReduzido =
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    elemento.scrollIntoView({ block: 'start', behavior: preferReduzido ? 'auto' : 'smooth' });
  }

  private moduloAtendeTermo(modulo: ManualModulo, termo: string): boolean {
    const camposBasicos = [
      modulo.numeroOficial,
      modulo.id,
      modulo.titulo,
      modulo.tituloCurto,
      modulo.subtitulo,
      modulo.objetivo,
      modulo.contextoTag,
      modulo.categoriaRotulo,
      ...modulo.palavrasChave
    ];

    if (camposBasicos.some((campo) => campo.toLowerCase().includes(termo))) return true;

    const nasSecoes = modulo.secoes.some((secao) => {
      if (secao.titulo.toLowerCase().includes(termo)) return true;
      if (secao.subtitulo?.toLowerCase().includes(termo)) return true;

      return !!secao.itens?.some((item) =>
        [item.rotulo, item.descricao, item.destaque, item.atalho, item.comando]
          .filter((valor): valor is string => !!valor)
          .some((valor) => valor.toLowerCase().includes(termo))
      );
    });

    if (nasSecoes) return true;

    return !!modulo.alertasPrincipais?.some(
      (alerta) =>
        alerta.titulo.toLowerCase().includes(termo) ||
        alerta.mensagem.toLowerCase().includes(termo)
    );
  }
}
