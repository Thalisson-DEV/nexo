import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CesButtonComponent, CesStatusComponent } from '@ces/ui';

import { MovimentacaoOperacaoResponse } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { agruparItensPorMaterial, rotuloCondicao, tomCondicao } from '../../utils/movimentacao.utils';
import { SeriesGradeComponent } from '../series-grade/series-grade.component';

/**
 * Linha expandida do histórico do material: o que esta operação moveu do material (quantidade,
 * condição e números de série). Busca a operação ao abrir.
 */
@Component({
  selector: 'app-movimentacao-material-itens',
  standalone: true,
  imports: [DecimalPipe, CesButtonComponent, CesStatusComponent, SeriesGradeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col gap-3 py-1" [attr.aria-busy]="carregando()">
      @if (erro()) {
        <div class="flex flex-wrap items-center gap-3 text-xs text-[var(--ces-foreground-muted)]" role="alert">
          <span>{{ erro() }}</span>
          <ces-button label="Tentar novamente" icon="pi pi-refresh" size="sm" variant="outline" (pressed)="carregar()" />
        </div>
      } @else if (carregando()) {
        <div class="h-9 rounded-lg bg-[var(--ces-surface-muted)] animate-pulse motion-reduce:animate-none" aria-hidden="true"></div>
        <span class="sr-only">Carregando os itens da operação...</span>
      } @else if (!grupos().length) {
        <p class="m-0 text-xs text-[var(--ces-foreground-muted)]">Esta operação não tem itens deste material.</p>
      } @else {
        @for (grupo of grupos(); track grupo.chave) {
          <div class="flex flex-col gap-2">
            <div class="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
              <span class="font-bold tabular-nums text-[var(--ces-foreground)]">
                {{ grupo.quantidade | number: '1.0-3' }} {{ grupo.material.unidadeMedida || '' }}
              </span>
              <ces-status [tone]="tomCondicao(grupo.condicao)">{{ rotuloCondicao(grupo.condicao) }}</ces-status>
              @if (grupo.series.length) {
                <span class="text-[var(--ces-foreground-muted)]">
                  <i class="pi pi-barcode text-[11px]" aria-hidden="true"></i>
                  {{ grupo.series.length }} {{ grupo.series.length === 1 ? 'número de série' : 'números de série' }}
                </span>
              }
            </div>
            @if (grupo.series.length) {
              <app-series-grade [series]="grupo.grade" [rotulo]="'Números de série movimentados de ' + grupo.material.descricao" />
            }
          </div>
        }
      }
    </div>
  `,
})
export class MovimentacaoMaterialItensComponent implements OnInit {
  private readonly service = inject(AlmoxarifadoService);
  private readonly destroyRef = inject(DestroyRef);

  readonly idReferenciaOperacao = input.required<string>();
  readonly idMaterial = input.required<string>();

  protected readonly rotuloCondicao = rotuloCondicao;
  protected readonly tomCondicao = tomCondicao;

  protected readonly operacao = signal<MovimentacaoOperacaoResponse | null>(null);
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);

  protected readonly grupos = computed(() =>
    agruparItensPorMaterial((this.operacao()?.itens ?? []).filter((i) => i.material?.id === this.idMaterial())).map((g) => ({
      ...g,
      grade: g.series.map((numSerie) => ({ numSerie })),
    })),
  );

  ngOnInit(): void {
    this.carregar();
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.erro.set(null);
    this.service
      .buscarOperacaoPorIdReferencia(this.idReferenciaOperacao())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.operacao.set(res);
          this.carregando.set(false);
        },
        error: (err) => {
          this.erro.set(getErrorMessage(err) || 'Não foi possível carregar os itens da operação.');
          this.carregando.set(false);
        },
      });
  }
}
