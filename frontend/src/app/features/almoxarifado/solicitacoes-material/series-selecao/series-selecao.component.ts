import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, signal, untracked } from '@angular/core';
import {
  CesButtonComponent,
  CesConfirmationService,
  CesIconButtonComponent,
  CesMultiSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTabItem,
  CesTabPanelDirective,
  CesTabsComponent,
  CesTextInputComponent,
  NotificationService
} from '@ces/ui';
import { catchError, of } from 'rxjs';

import { getErrorMessage } from '../../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { AuthService } from '../../../../core/services/auth.service';
import { LeituraSerie, lerSerie, separarSeries } from './series-selecao.util';

export interface MaterialSerie {
  readonly id: string;
  readonly codigo: string;
  readonly descricao: string;
}

/** Série digitada que não pode sair como está: fica à vista com o motivo e, quando dá, a saída. */
interface SerieComProblema extends LeituraSerie {
  readonly numSerie: string;
  readonly registrando?: boolean;
}

/**
 * Escolha das séries que saem num item: digitando ou bipando (cada série é conferida na hora
 * contra a origem; a que não existe pode ganhar entrada avulsa ali mesmo) ou escolhendo na lista
 * do que está disponível na origem.
 */
@Component({
  selector: 'app-series-selecao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesIconButtonComponent,
    CesMultiSelectComponent,
    CesStatusComponent,
    CesTabPanelDirective,
    CesTabsComponent,
    CesTextInputComponent
  ],
  template: `
    <ces-tabs [(value)]="modo" [tabs]="abas" [lazy]="false" [ariaLabel]="'Como informar as séries de ' + material().descricao">
      <ng-template cesTabPanel="digitar">
        <div class="ss-digitar">
          <ces-text-input
            label="Número de série"
            placeholder="Digite ou bipe e tecle Enter"
            [description]="'Pode colar várias separadas por espaço ou vírgula. Cada uma é conferida em ' + origem().nome + '.'"
            [disabled]="completo()"
            [(value)]="digitado"
            (keydown.enter)="$event.preventDefault(); adicionarDigitadas()"
          />
          <ces-button label="Adicionar" icon="pi pi-plus" variant="secondary" size="sm" [disabled]="completo() || !digitado().trim()" [loading]="verificando()" (pressed)="adicionarDigitadas()" />
        </div>
        @if (problemas().length) {
          <ul class="ss-problemas" aria-label="Séries que não podem sair como estão">
            @for (p of problemas(); track p.numSerie) {
              <li class="ss-problema">
                <span class="ss-numero">{{ p.numSerie }}</span>
                <ces-status [tone]="p.situacao === 'fora-do-sistema' ? 'info' : 'warning'" [icon]="p.situacao === 'fora-do-sistema' ? 'pi pi-info-circle' : 'pi pi-exclamation-triangle'">{{ p.mensagem }}</ces-status>
                <span class="ss-problema__acoes">
                  @if (p.situacao === 'fora-do-sistema' && podeDarEntrada()) {
                    <ces-button label="Dar entrada" icon="pi pi-arrow-down-left" variant="ghost" size="sm" [loading]="!!p.registrando" (pressed)="darEntrada(p.numSerie)" />
                  }
                  <ces-icon-button icon="pi pi-times" [label]="'Descartar a série ' + p.numSerie" size="icon-sm" (pressed)="descartar(p.numSerie)" />
                </span>
              </li>
            }
          </ul>
          @if (semPermissaoDeEntrada()) {
            <p class="ss-nota">Para registrar a entrada de uma série que não consta no sistema, peça a quem cuida das movimentações do estoque.</p>
          }
        }
      </ng-template>

      <ng-template cesTabPanel="lista">
        <ces-multiselect
          label="Séries disponíveis"
          placeholder="Escolha as séries"
          display="chip"
          [options]="opcoes()"
          [loading]="carregandoLista()"
          [value]="selecionadas()"
          (valueChange)="escolherNaLista($event)"
          [description]="descricaoLista()"
          [maxSelectedLabels]="50"
        />
      </ng-template>
    </ces-tabs>

    <div class="ss-escolhidas">
      <div class="ss-escolhidas__topo">
        <span><strong>{{ selecionadas().length }}</strong> de {{ maximo() }} {{ maximo() === 1 ? 'série' : 'séries' }}</span>
        @if (selecionadas().length) {
          <ces-button label="Limpar" icon="pi pi-eraser" variant="ghost" size="sm" (pressed)="selecionadas.set([])" />
        }
      </div>
      @if (selecionadas().length) {
        <ul class="ss-chips" [attr.aria-label]="'Séries escolhidas de ' + material().descricao">
          @for (s of selecionadas(); track s) {
            <li class="ss-chip">
              <i class="pi pi-barcode" aria-hidden="true"></i><span class="ss-numero">{{ s }}</span>
              <button type="button" class="ss-chip__remover" [attr.aria-label]="'Remover a série ' + s" (click)="remover(s)"><i class="pi pi-times" aria-hidden="true"></i></button>
            </li>
          }
        </ul>
      } @else {
        <p class="ss-nota">Nenhuma série escolhida ainda.</p>
      }
    </div>
  `,
  styles: [`
    :host { display: grid; gap: 0.75rem; }
    .ss-digitar { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 0.5rem; align-items: start; padding-top: 0.5rem; }
    .ss-digitar ces-button { margin-top: 1.4rem; }
    .ss-nota { margin: 0; font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); }
    .ss-numero { font-family: var(--ces-font-mono, ui-monospace, monospace); font-weight: 600; font-variant-numeric: tabular-nums; }
    .ss-problemas { list-style: none; margin: 0.5rem 0 0; padding: 0; display: grid; gap: 0.375rem; }
    .ss-problema { display: flex; flex-wrap: wrap; align-items: center; gap: 0.375rem 0.625rem; padding: 0.5rem 0.625rem;
      border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); font-size: var(--ces-font-caption); }
    .ss-problema__acoes { display: inline-flex; align-items: center; gap: 0.25rem; margin-left: auto; }
    .ss-escolhidas { display: grid; gap: 0.5rem; padding: 0.625rem 0.75rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface-subtle); }
    .ss-escolhidas__topo { display: flex; align-items: center; justify-content: space-between; gap: 0.5rem; font-size: var(--ces-font-caption); color: var(--ces-foreground-muted); }
    .ss-escolhidas__topo strong { color: var(--ces-foreground); }
    .ss-chips { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 0.375rem; }
    .ss-chip { display: inline-flex; align-items: center; gap: 0.35rem; padding: 0.2rem 0.25rem 0.2rem 0.5rem; border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-md); background: var(--ces-surface); font-size: var(--ces-font-caption); color: var(--ces-foreground); }
    .ss-chip > i { color: var(--ces-foreground-muted); font-size: 0.7rem; }
    .ss-chip__remover { display: inline-grid; place-items: center; width: 1.25rem; height: 1.25rem; padding: 0; border: 0; border-radius: var(--ces-radius-sm);
      background: none; color: var(--ces-foreground-muted); cursor: pointer; }
    .ss-chip__remover:hover { background: var(--ces-surface-muted); color: var(--ces-foreground); }
    .ss-chip__remover:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 1px; }
    .ss-chip__remover i { font-size: 0.6rem; }
  `]
})
export class SeriesSelecaoComponent {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly auth = inject(AuthService);
  private readonly confirmacoes = inject(CesConfirmationService);
  private readonly avisos = inject(NotificationService);

  /** Solicitação que está entregando: a série reservada para ela continua disponível. */
  readonly idSolicitacao = input<string | null>(null);
  readonly origem = input.required<{ id: string; nome: string }>();
  readonly material = input.required<MaterialSerie>();
  readonly maximo = input.required<number>();
  readonly selecionadas = model<string[]>([]);

  protected readonly abas: readonly CesTabItem[] = [
    { value: 'digitar', label: 'Digitar ou bipar', icon: 'pi pi-pencil' },
    { value: 'lista', label: 'Escolher na lista', icon: 'pi pi-list' }
  ];
  protected readonly modo = signal<string>('digitar');
  protected readonly digitado = signal('');
  protected readonly verificando = signal(false);
  protected readonly problemas = signal<SerieComProblema[]>([]);
  protected readonly carregandoLista = signal(false);
  private readonly disponiveis = signal<string[]>([]);

  protected readonly completo = computed(() => this.selecionadas().length >= this.maximo());
  protected readonly podeDarEntrada = computed(() => !!this.auth.currentUser()?.permissoes?.includes('estoque:movimentacao:write'));
  protected readonly semPermissaoDeEntrada = computed(
    () => !this.podeDarEntrada() && this.problemas().some((p) => p.situacao === 'fora-do-sistema')
  );
  protected readonly opcoes = computed<CesSelectOption<string>[]>(() =>
    [...new Set([...this.selecionadas(), ...this.disponiveis()])]
      .sort((a, b) => a.localeCompare(b, 'pt-BR', { numeric: true }))
      .map((s) => ({ label: s, value: s }))
  );

  /** Só recarrega a lista quando muda a origem ou o material, não a cada novo objeto de entrada. */
  private readonly chaveLista = computed(() => `${this.origem().id}|${this.material().id}`);

  constructor() {
    effect(() => {
      this.chaveLista();
      untracked(() => this.carregarDisponiveis(this.origem().id, this.material().id));
    });
  }

  protected descricaoLista(): string {
    if (this.carregandoLista()) return `Buscando as séries novas em ${this.origem().nome}…`;
    const total = this.disponiveis().length;
    if (!total) return `Nenhuma série nova livre em ${this.origem().nome}. Use "Digitar ou bipar" para registrar a entrada de uma série.`;
    return `${total} ${total === 1 ? 'série nova livre' : 'séries novas livres'} em ${this.origem().nome}; escolha até ${this.maximo()}.`;
  }

  protected escolherNaLista(valores: string[] | null): void {
    const escolhidas = valores ?? [];
    if (escolhidas.length > this.maximo()) {
      this.avisos.warning(`Este item leva no máximo ${this.maximo()} ${this.maximo() === 1 ? 'série' : 'séries'}.`);
      this.selecionadas.set(escolhidas.slice(0, this.maximo()));
      return;
    }
    this.selecionadas.set(escolhidas);
  }

  protected adicionarDigitadas(): void {
    const ja = new Set([...this.selecionadas(), ...this.problemas().map((p) => p.numSerie)]);
    const novas = separarSeries(this.digitado()).filter((s) => !ja.has(s));
    this.digitado.set('');
    if (!novas.length || this.verificando()) return;
    this.verificando.set(true);
    this.almoxarifado
      .verificarSeries(novas)
      .pipe(catchError((e) => {
        this.avisos.error(`Não foi possível conferir as séries: ${getErrorMessage(e)}`);
        return of(null);
      }))
      .subscribe((achadas) => {
        this.verificando.set(false);
        if (!achadas) return;
        const ctx = { idOrigem: this.origem().id, nomeOrigem: this.origem().nome, idMaterial: this.material().id, idSolicitacao: this.idSolicitacao() };
        const problemas: SerieComProblema[] = [];
        const aceitas: string[] = [];
        for (const numSerie of novas) {
          const leitura = lerSerie(numSerie, achadas, ctx);
          if (leitura.situacao !== 'disponivel') problemas.push({ numSerie, ...leitura });
          else if (this.selecionadas().length + aceitas.length < this.maximo()) aceitas.push(numSerie);
          else problemas.push({ numSerie, situacao: 'disponivel', mensagem: `Passa do total do item (${this.maximo()}).` });
        }
        if (aceitas.length) this.selecionadas.update((atual) => [...atual, ...aceitas]);
        this.problemas.update((atual) => [...atual, ...problemas]);
      });
  }

  protected async darEntrada(numSerie: string): Promise<void> {
    const confirmou = await this.confirmacoes.confirm({
      title: 'Registrar entrada avulsa',
      message: `A série ${numSerie} de ${this.material().codigo} · ${this.material().descricao} entra como material novo em ${this.origem().nome} e já fica escolhida para este item.`,
      confirmLabel: 'Registrar entrada',
      icon: 'pi pi-arrow-down-left'
    });
    if (!confirmou) return;
    this.marcarRegistrando(numSerie, true);
    this.almoxarifado
      .registrarEntradaLote({
        idEstoqueDestino: this.origem().id,
        itens: [{ idMaterial: this.material().id, quantidade: 1, numSerie, condicao: 'NOVO' }]
      })
      .subscribe({
        next: () => {
          this.descartar(numSerie);
          this.disponiveis.update((atual) => [...atual, numSerie]);
          if (!this.completo()) this.selecionadas.update((atual) => [...atual, numSerie]);
          this.avisos.success(`Entrada da série ${numSerie} registrada em ${this.origem().nome}.`);
        },
        error: (e) => {
          this.marcarRegistrando(numSerie, false);
          this.avisos.error(`Não foi possível registrar a entrada: ${getErrorMessage(e)}`);
        }
      });
  }

  protected descartar(numSerie: string): void {
    this.problemas.update((atual) => atual.filter((p) => p.numSerie !== numSerie));
  }

  protected remover(numSerie: string): void {
    this.selecionadas.update((atual) => atual.filter((s) => s !== numSerie));
  }

  private marcarRegistrando(numSerie: string, registrando: boolean): void {
    this.problemas.update((atual) => atual.map((p) => (p.numSerie === numSerie ? { ...p, registrando } : p)));
  }

  private carregarDisponiveis(idEstoque: string, idMaterial: string): void {
    this.carregandoLista.set(true);
    this.almoxarifado
      .listarSaldosSeriais({ idEstoque, idMaterial, condicao: 'NOVO', ativo: true, page: 0, size: 500, sort: 'numSerie,asc' })
      .pipe(catchError(() => of(null)))
      .subscribe((pagina) => {
        const propria = this.idSolicitacao();
        this.disponiveis.set(
          (pagina?.content ?? [])
            .filter((s) => !s.reservadaParaSolicitacao || s.reservadaParaSolicitacao === propria)
            .map((s) => s.numSerie)
        );
        this.carregandoLista.set(false);
      });
  }
}
