import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { CesAsyncStateComponent, CesCalloutComponent, CesSelectComponent, CesSelectOption } from '@ces/ui';
import { ConflitoEquipe, EquipeProjetada, TurnoProgramacao } from '../../../core/models/obras-programacao.models';
import { formatarMoeda } from '../obras-ui';
import { ROTULO_TURNO, formatarMinutos, formatarNumero, turnosCruzam } from './programacao.logic';

function opcoes(equipes: readonly EquipeProjetada[], id: (e: EquipeProjetada) => string | null | undefined, nome: (e: EquipeProjetada) => string | null | undefined): CesSelectOption<string>[] {
  const mapa = new Map<string, string>();
  for (const equipe of equipes) {
    const chave = id(equipe);
    if (chave) mapa.set(chave, nome(equipe) ?? chave);
  }
  return [...mapa].map(([value, label]) => ({ value, label })).sort((a, b) => a.label.localeCompare(b.label, 'pt-BR'));
}

/** Escolha da equipe com filtros por base e tipo, ocupação da meta no período e projeção de capacidade e meta. */
@Component({
  selector: 'app-equipes-projecao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesAsyncStateComponent, CesCalloutComponent, CesSelectComponent],
  template: `
    <div class="ces-form-grid">
      <ces-select label="Base operacional" [options]="bases()" [(value)]="filtroBase" [clearable]="true" [filter]="true" placeholder="Todas"></ces-select>
      <ces-select label="Tipo de equipe" [options]="tipos()" [(value)]="filtroTipo" [clearable]="true" [filter]="true" placeholder="Todos"></ces-select>
    </div>

    @if (carregando()) {
      <ces-async-state state="loading" title="Calculando a projeção das equipes" [compact]="true"></ces-async-state>
    } @else if (!visiveis().length) {
      <ces-async-state state="empty" icon="pi pi-users" title="Nenhuma equipe encontrada" [compact]="true"
        description="Só aparecem equipes ativas do mesmo processo da obra."></ces-async-state>
    } @else {
      <div class="equipes" role="radiogroup" aria-label="Equipe">
        @for (e of visiveis(); track e.id) {
          <button type="button" class="equipe" role="radio" [attr.aria-checked]="selecionada() === e.id" [class.equipe--ativa]="selecionada() === e.id" (click)="selecionada.set(e.id)">
            <span class="equipe__topo">
              <i class="pi" [class.pi-check-circle]="selecionada() === e.id" [class.pi-circle]="selecionada() !== e.id" aria-hidden="true"></i>
              <strong>{{ e.codigo }}</strong>
              <span class="equipe__tipo">{{ e.tipoEquipe }} · {{ e.base }}</span>
              <span class="equipe__distancia">
                @if (e.distanciaKm != null) {
                  {{ numero(e.distanciaKm) }} km · {{ minutos(e.tempoDeslocamentoMin) }}{{ e.deslocamentoEstimado ? ' (estimado)' : '' }}
                } @else {
                  {{ e.semDeslocamentoPorque }}
                }
              </span>
            </span>
            @if (conflitosDe(e).length) {
              <span class="equipe__conflito"><i class="pi pi-exclamation-triangle" aria-hidden="true"></i> Ocupada: {{ textoConflitos(e) }}</span>
            }
            <span class="equipe__ocupacao">
              @if (e.percentualOcupacao != null) {
                <span class="equipe__barra" aria-hidden="true">
                  <span class="equipe__preenchido" [attr.data-acima]="e.percentualOcupacao > 100" [style.width.%]="largura(e.percentualOcupacao)"></span>
                </span>
                <small>{{ numero(e.percentualOcupacao) }}% da meta ocupada</small>
              } @else {
                <small>Sem meta vigente no período</small>
              }
            </span>
          </button>
        }
      </div>
    }

    @if (atual(); as e) {
      @if ((e.capacidadeMinutos ?? 1) <= 0) {
        <ces-callout tone="danger" [dense]="true">
          Inviável em um dia: {{ minutos(e.jornadaMinutos) }} de jornada − {{ minutos(e.plataformaMinutos) }} de plataforma − {{ minutos((e.tempoDeslocamentoMin ?? 0) * 2) }} de deslocamento ida e volta deixam {{ minutos(e.capacidadeMinutos) }}. Ainda é possível programar.
        </ces-callout>
      }
      @if (conflitosDe(e).length) {
        <ces-callout tone="warning" [dense]="true">{{ e.codigo }} já está em {{ textoConflitos(e) }} nesses dias. Ainda é possível programar, mas confira se a equipe dá conta.</ces-callout>
      }
      @if ((e.percentualMeta ?? 0) > 100) {
        <ces-callout tone="warning" [dense]="true">Com esta programação, a equipe fica acima da meta do período: {{ numero(e.percentualMeta) }}%.</ces-callout>
      }
      <dl class="projecao">
        <div><dt>Capacidade por dia</dt><dd [attr.data-negativo]="(e.capacidadeMinutos ?? 1) <= 0">{{ minutos(e.capacidadeMinutos) }}</dd></div>
        <div><dt>Dias úteis</dt><dd>{{ e.diasUteis }}</dd></div>
        <div><dt>Meta por dia útil</dt><dd>{{ moeda(e.metaDia) }}</dd></div>
        <div><dt>Meta do período</dt><dd>{{ moeda(e.metaPeriodo) }}</dd></div>
        <div><dt>Já programado</dt><dd>{{ moeda(e.valorProgramadoPeriodo) }}</dd></div>
        <div><dt>Esta programação</dt><dd>{{ moeda(e.valorSelecao) }}</dd></div>
        <div><dt>% da meta</dt><dd [attr.data-acima]="(e.percentualMeta ?? 0) > 100">{{ e.percentualMeta != null ? numero(e.percentualMeta) + '%' : '—' }}</dd></div>
      </dl>
    }
  `,
  styles: [`
    :host { display: flex; flex-direction: column; gap: 0.75rem; }
    .equipes { display: flex; flex-direction: column; gap: 0.375rem; max-height: 18rem; overflow-y: auto; }
    .equipe { display: flex; flex-direction: column; gap: 0.375rem; width: 100%; padding: 0.5rem 0.75rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); color: var(--ces-foreground); font: inherit; font-size: 0.8125rem; text-align: left; cursor: pointer; }
    .equipe:hover { background: var(--ces-surface-muted); }
    .equipe:focus-visible { outline: 2px solid var(--ces-brand); outline-offset: 1px; }
    .equipe--ativa { border-color: var(--ces-brand); box-shadow: inset 0 0 0 1px var(--ces-brand); }
    .equipe__topo { display: flex; flex-wrap: wrap; gap: 0.25rem 0.5rem; align-items: center; }
    .equipe--ativa .pi { color: var(--ces-brand); }
    .equipe__tipo, .equipe__distancia, .equipe__ocupacao small { color: var(--ces-foreground-muted); font-size: 0.75rem; }
    .equipe__distancia { margin-left: auto; }
    .equipe__conflito { display: flex; gap: 0.375rem; align-items: center; color: var(--ces-warning-text); font-size: 0.75rem; font-weight: 600; }
    .equipe__ocupacao { display: flex; gap: 0.5rem; align-items: center; }
    .equipe__barra { flex: 1; overflow: hidden; height: 0.375rem; border-radius: 999px; background: var(--ces-surface-muted); }
    .equipe__preenchido { display: block; height: 100%; border-radius: inherit; background: var(--ces-brand); }
    .equipe__preenchido[data-acima='true'] { background: var(--ces-warning); }
    .projecao { display: grid; grid-template-columns: repeat(auto-fit, minmax(8rem, 1fr)); gap: 0.5rem 1rem; margin: 0; padding: 0.75rem; border-radius: var(--ces-radius-md); background: var(--ces-surface-muted); }
    .projecao dt { color: var(--ces-foreground-muted); font-size: 0.6875rem; }
    .projecao dd { margin: 0; font-size: 0.875rem; font-weight: 600; font-variant-numeric: tabular-nums; }
    .projecao dd[data-negativo='true'] { color: var(--ces-danger-text); }
    .projecao dd[data-acima='true'] { color: var(--ces-warning-text); }
  `]
})
export class EquipesProjecaoComponent {
  readonly equipes = input<readonly EquipeProjetada[]>([]);
  readonly carregando = input(false);
  readonly selecionada = model<string | null>(null);
  /** Turno escolhido, para só avisar do conflito que existe de verdade. */
  readonly turno = input<TurnoProgramacao | null>(null);

  protected readonly filtroBase = signal<string | null>(null);
  protected readonly filtroTipo = signal<string | null>(null);

  protected readonly bases = computed(() => opcoes(this.equipes(), (e) => e.idBase, (e) => e.base));
  protected readonly tipos = computed(() => opcoes(this.equipes(), (e) => e.idTipoEquipe, (e) => e.tipoEquipe));
  protected readonly visiveis = computed(() => this.equipes()
    .filter((e) => !this.filtroBase() || e.idBase === this.filtroBase())
    .filter((e) => !this.filtroTipo() || e.idTipoEquipe === this.filtroTipo()));
  protected readonly atual = computed(() => this.equipes().find((e) => e.id === this.selecionada()) ?? null);

  protected conflitosDe(equipe: EquipeProjetada): ConflitoEquipe[] {
    return (equipe.conflitos ?? []).filter((c) => turnosCruzam(c.turno, this.turno()));
  }

  protected textoConflitos(equipe: EquipeProjetada): string {
    return this.conflitosDe(equipe)
      .map((c) => `#${c.numero} da obra ${c.numeroObra} (${c.inicio} a ${c.fim}, ${ROTULO_TURNO[c.turno].toLowerCase()})`)
      .join('; ');
  }

  protected readonly minutos = formatarMinutos;
  protected readonly numero = formatarNumero;
  protected moeda(valor: number | null | undefined): string {
    return valor == null ? '—' : formatarMoeda(valor);
  }
  protected largura(percentual: number | null | undefined): number {
    return Math.min(100, Math.max(0, percentual ?? 0));
  }
}
