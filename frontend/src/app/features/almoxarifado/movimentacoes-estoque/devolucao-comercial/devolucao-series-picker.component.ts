import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import {
  CesBooleanControlComponent,
  CesButtonComponent,
  CesTextInputComponent,
} from '@ces/ui';

/** Lista de números de série com busca e seleção múltipla. */
@Component({
  selector: 'app-devolucao-series-picker',
  standalone: true,
  imports: [CesBooleanControlComponent, CesButtonComponent, CesTextInputComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-2">
      <div class="flex flex-wrap items-end gap-2">
        <ces-text-input
          class="flex-1 min-w-48"
          label="Buscar série"
          [labelVisible]="false"
          placeholder="Buscar série..."
          [(value)]="busca"
        ></ces-text-input>
        <ces-button
          size="sm"
          variant="secondary"
          [label]="'Selecionar todas (' + visiveis().length + ')'"
          [disabled]="loading() || visiveis().length === 0 || todasVisiveisSelecionadas()"
          (pressed)="selecionarVisiveis()"
        ></ces-button>
        <ces-button
          size="sm"
          variant="ghost"
          label="Limpar"
          [disabled]="selecionadas().length === 0"
          (pressed)="selecionadas.set([])"
        ></ces-button>
      </div>

      <div
        class="max-h-56 overflow-y-auto rounded-xl border border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900"
      >
        @if (loading()) {
          <p class="px-3 py-4 text-xs text-slate-500 dark:text-slate-400">Carregando séries...</p>
        } @else if (series().length === 0) {
          <p class="px-3 py-4 text-xs text-slate-500 dark:text-slate-400">
            Nenhuma série {{ condicaoLabel() }} disponível no estoque da equipe.
          </p>
        } @else if (visiveis().length === 0) {
          <p class="px-3 py-4 text-xs text-slate-500 dark:text-slate-400">
            Nenhuma série encontrada para "{{ busca() }}".
          </p>
        } @else {
          <ul class="grid grid-cols-1 sm:grid-cols-2 gap-x-3 px-3 py-1">
            @for (sn of visiveis(); track sn) {
              <li class="py-1 font-mono text-xs">
                <ces-boolean-control
                  [inline]="true"
                  [label]="sn"
                  [value]="selecionadasSet().has(sn)"
                  (valueChange)="alternar(sn, $event)"
                ></ces-boolean-control>
              </li>
            }
          </ul>
        }
      </div>

      <span class="text-[11px] text-slate-500 dark:text-slate-400">
        {{ selecionadas().length }} de {{ series().length }} série(s) selecionada(s)
      </span>
    </div>
  `,
})
export class DevolucaoSeriesPickerComponent {
  readonly series = input<string[]>([]);
  readonly loading = input(false);
  readonly condicao = input<'NOVO' | 'SUCATA'>('NOVO');
  readonly selecionadas = model<string[]>([]);

  protected readonly busca = signal('');

  protected readonly condicaoLabel = computed(() =>
    this.condicao() === 'SUCATA' ? 'como sucata' : 'como nova',
  );

  protected readonly selecionadasSet = computed(() => new Set(this.selecionadas()));

  protected readonly visiveis = computed(() => {
    const termo = this.busca().trim().toLowerCase();
    return termo ? this.series().filter((sn) => sn.toLowerCase().includes(termo)) : this.series();
  });

  protected readonly todasVisiveisSelecionadas = computed(() => {
    const selecionadas = this.selecionadasSet();
    return this.visiveis().every((sn) => selecionadas.has(sn));
  });

  protected alternar(sn: string, marcado: boolean): void {
    this.selecionadas.update((atual) =>
      marcado ? (atual.includes(sn) ? atual : [...atual, sn]) : atual.filter((s) => s !== sn),
    );
  }

  protected selecionarVisiveis(): void {
    const atual = this.selecionadasSet();
    this.selecionadas.set([...atual, ...this.visiveis().filter((sn) => !atual.has(sn))]);
  }
}
