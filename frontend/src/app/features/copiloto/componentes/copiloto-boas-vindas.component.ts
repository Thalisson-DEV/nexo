import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { CesStatusComponent } from '@ces/ui';
import { CesAsyncStateComponent, CesCalloutComponent } from '@ces/ui/feedback';
import { AssistenteContexto, CapacidadesAssistente } from '../../../core/models/assistente.models';

@Component({
  selector: 'app-copiloto-boas-vindas',
  standalone: true,
  imports: [CesStatusComponent, CesCalloutComponent, CesAsyncStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="boas-vindas" aria-labelledby="copiloto-boas-vindas-titulo">
      <img class="boas-vindas__avatar" src="icons/copiloto-2.png" alt="" aria-hidden="true" />
      <h1 id="copiloto-boas-vindas-titulo" class="boas-vindas__titulo">O que vamos explorar hoje?</h1>
      <p class="boas-vindas__texto">
        Converse com os dados do Nexo, investigue resultados e crie análises.
      </p>

      @if (contexto(); as ctx) {
        <ces-callout tone="info" icon="pi-map-marker" title="Contexto desta conversa" [dense]="true">
          Você abriu o Copiloto a partir de {{ ctx.rotulo || ctx.pagina || ctx.entidadeTipo }}. Perguntas como
          “analise isto” usam esse contexto.
        </ces-callout>
      }

      @if (capacidades(); as cap) {
        @if (!cap.disponivel) {
          <ces-callout tone="warning" icon="pi-exclamation-triangle" title="Copiloto indisponível" [dense]="true">
            {{ cap.motivoIndisponibilidade || 'O provedor de IA não está configurado neste ambiente.' }}
          </ces-callout>
        }
        <div class="boas-vindas__recursos" aria-label="Capacidades do Copiloto">
          <h2 class="boas-vindas__subtitulo">O que posso fazer</h2>
          @if (capacidadesDisponiveis().length) {
            <p class="boas-vindas__estado">Disponível agora</p>
            <ul class="boas-vindas__capacidades" aria-label="Capacidades disponíveis agora">
              @for (capacidade of capacidadesDisponiveis(); track capacidade.label) {
                <li><ces-status tone="brand" [icon]="capacidade.icon">{{ capacidade.label }}</ces-status></li>
              }
            </ul>
          }
          @if (capacidadesIndisponiveis().length) {
            <p class="boas-vindas__estado">Indisponível nesta sessão</p>
            <ul class="boas-vindas__capacidades" aria-label="Capacidades indisponíveis nesta sessão">
              @for (capacidade of capacidadesIndisponiveis(); track capacidade.label) {
                <li><ces-status tone="neutral" [icon]="capacidade.icon">{{ capacidade.label }}</ces-status></li>
              }
            </ul>
          }
        </div>
        @if (cap.sugestoes.length) {
          <h2 class="boas-vindas__subtitulo">Experimente uma pergunta</h2>
          <ul class="sugestoes">
            @for (sugestao of cap.sugestoes.slice(0, 4); track sugestao.titulo) {
              <li>
                <button type="button" class="sugestao" [disabled]="!cap.disponivel" (click)="escolher.emit(sugestao.pergunta)">
                  <i [class]="sugestao.icone + ' sugestao__icone'" aria-hidden="true"></i>
                  <span class="sugestao__texto">
                    <strong>{{ sugestao.titulo }}</strong>
                  </span>
                  <i class="pi pi-arrow-up-right sugestao__seta" aria-hidden="true"></i>
                </button>
              </li>
            }
          </ul>
        }
      } @else if (erro()) {
        <ces-async-state state="error" [compact]="true" title="Não foi possível carregar as capacidades" [description]="erro() || undefined"></ces-async-state>
      } @else {
        <ces-async-state state="loading" [compact]="true" title="Preparando o Copiloto"></ces-async-state>
      }
    </section>
  `,
  styles: `
    :host { display: block; }
    .boas-vindas {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.65rem;
      width: 100%;
      max-width: 44rem;
      margin: 0 auto;
      padding: 1.25rem 0.5rem 2rem;
      text-align: center;
    }
    .boas-vindas__avatar { width: 3.25rem; height: 3.25rem; border-radius: 999px; object-fit: cover; }
    .boas-vindas__titulo { margin: 0.3rem 0 0; font-size: clamp(1.35rem, 2vw, 1.7rem); font-weight: 650; color: var(--ces-foreground); line-height: 1.25; }
    .boas-vindas__texto { margin: 0; max-width: 34rem; color: var(--ces-foreground-muted); font-size: var(--ces-font-body); line-height: 1.5; }
    .boas-vindas__subtitulo {
      align-self: stretch;
      margin: 1.1rem 0 0;
      font-size: var(--ces-font-caption);
      font-weight: 600;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      color: var(--ces-foreground-subtle);
      text-align: left;
    }
    ces-callout { align-self: stretch; text-align: left; }
    .boas-vindas__recursos { align-self: stretch; margin-top: 0.4rem; }
    .boas-vindas__recursos .boas-vindas__subtitulo { margin: 0 0 0.35rem; }
    .boas-vindas__estado {
      margin: 0.7rem 0 0.4rem;
      color: var(--ces-foreground-muted);
      font-size: var(--ces-font-caption);
      text-align: left;
    }
    .boas-vindas__capacidades {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-start;
      gap: 0.4rem;
      list-style: none;
      margin: 0;
      padding: 0;
    }
    .sugestoes {
      list-style: none;
      margin: 0;
      padding: 0;
      align-self: stretch;
      display: grid;
      grid-template-columns: repeat(2, minmax(0, 1fr));
      gap: 0.6rem;
    }
    .sugestao {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      width: 100%;
      height: 100%;
      min-height: 3.7rem;
      padding: 0.7rem 0.85rem;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-md);
      background: var(--ces-surface);
      color: var(--ces-foreground);
      font: inherit;
      text-align: left;
      cursor: pointer;
      transition: border-color var(--ces-duration-fast) var(--ces-ease-standard), box-shadow var(--ces-duration-fast) var(--ces-ease-standard);
    }
    .sugestao:hover:not(:disabled) { border-color: var(--ces-brand); box-shadow: var(--ces-shadow-sm); }
    .sugestao:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; }
    .sugestao:disabled { cursor: not-allowed; opacity: 0.6; }
    .sugestao__icone { color: var(--ces-brand); }
    .sugestao__texto { flex: 1; font-size: var(--ces-font-body); line-height: 1.35; }
    .sugestao__texto strong { font-weight: 550; }
    .sugestao__seta { font-size: 0.7rem; color: var(--ces-foreground-subtle); }
    @media (max-width: 640px) {
      .boas-vindas { padding: 0.75rem 0 1.5rem; }
      .sugestoes { grid-template-columns: 1fr; }
      .sugestao { min-height: 3.2rem; }
    }
  `,
})
export class CopilotoBoasVindasComponent {
  readonly capacidades = input<CapacidadesAssistente | null>(null);
  readonly erro = input<string | null>(null);
  readonly contexto = input<AssistenteContexto | null>(null);
  readonly escolher = output<string>();

  private readonly catalogoCapacidades = computed(() => {
    const cap = this.capacidades();
    const tipos = new Set((cap?.ferramentas ?? []).map((ferramenta) => ferramenta.tipo));
    const disponivel = (habilitada: boolean) => !!cap?.disponivel && habilitada;
    return [
      { label: 'Consultar dados do Nexo', icon: 'pi pi-database', disponivel: disponivel(['CATALOG', 'DATA', 'SQL'].some((tipo) => tipos.has(tipo))) },
      { label: 'Analisar resultados', icon: 'pi pi-chart-line', disponivel: disponivel(tipos.has('ANALYSIS')) },
      { label: 'Indicar caminhos', icon: 'pi pi-compass', disponivel: disponivel(tipos.has('NAVIGATION')) },
      { label: 'Dashboards', icon: 'pi pi-objects-column', disponivel: disponivel(tipos.has('ARTIFACT')) },
      { label: 'Relatórios PDF', icon: 'pi pi-file-pdf', disponivel: disponivel(tipos.has('REPORT')) },
      { label: 'Planilhas', icon: 'pi pi-file-excel', disponivel: disponivel(tipos.has('EXPORT')) },
      { label: 'Leitura de arquivos', icon: 'pi pi-paperclip', disponivel: disponivel(tipos.has('FILE')) },
      { label: 'Python', icon: 'pi pi-microchip-ai', disponivel: disponivel(!!cap?.python) },
      { label: 'Pesquisa web', icon: 'pi pi-globe', disponivel: disponivel(!!cap?.pesquisaWeb) },
      { label: 'Leitura de páginas', icon: 'pi pi-external-link', disponivel: disponivel(!!cap?.leituraWeb) },
      { label: 'Competências', icon: 'pi pi-sparkles', disponivel: disponivel(!!cap?.competencias?.length) },
      { label: 'Alterar dados do Nexo', icon: 'pi pi-pencil', disponivel: disponivel(tipos.has('WRITE')) },
    ];
  });

  protected readonly capacidadesDisponiveis = computed(() => this.catalogoCapacidades().filter((capacidade) => capacidade.disponivel));
  protected readonly capacidadesIndisponiveis = computed(() => this.catalogoCapacidades().filter((capacidade) => !capacidade.disponivel));
}
