import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { CesCalloutComponent } from '@ces/ui';
import { SaldoResponse } from '../../../core/models/almoxarifado.models';

/** Saldo negativo de um material no estoque da equipe que vai receber a entrega. */
export interface SaldoNegativoMaterial {
  idMaterial: string;
  codigo: string;
  descricao: string;
  unidadeMedida: string;
  /** Quantidade negativa (ex.: -2). */
  quantidade: number;
}

/**
 * Agrupa por material os saldos negativos do estoque da equipe. O saldo fica
 * negativo quando o campo aplica material além do que a equipe tinha.
 */
export function agruparSaldosNegativos(saldos: readonly SaldoResponse[]): Map<string, SaldoNegativoMaterial> {
  const porMaterial = new Map<string, SaldoNegativoMaterial>();
  for (const saldo of saldos) {
    if (!saldo.material?.id || !(saldo.quantidade < 0)) continue;
    const atual = porMaterial.get(saldo.material.id);
    porMaterial.set(saldo.material.id, {
      idMaterial: saldo.material.id,
      codigo: saldo.material.codigo,
      descricao: saldo.material.descricao,
      unidadeMedida: saldo.material.unidadeMedida ?? '',
      quantidade: (atual?.quantidade ?? 0) + saldo.quantidade,
    });
  }
  return porMaterial;
}

/**
 * Aviso informativo exibido na entrega de material para uma equipe: lista os
 * materiais desta entrega cujo saldo na equipe está negativo e orienta informar
 * o gestor. Não bloqueia nem cria tarefa — a entrega segue normalmente.
 */
@Component({
  selector: 'app-aviso-saldo-negativo-equipe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe, CesCalloutComponent],
  template: `
    @if (itens().length > 0) {
      <ces-callout tone="warning" title="Saldo negativo na equipe" [dense]="true">
        <p>
          {{ itens().length === 1 ? 'Este material está' : 'Estes materiais estão' }} com saldo negativo
          {{ equipe() ? 'na equipe ' + equipe() : 'nesta equipe' }}:
        </p>
        <ul class="mt-1 list-disc pl-5">
          @for (item of itens(); track item.idMaterial) {
            <li>
              <strong>{{ item.codigo }} — {{ item.descricao }}</strong>:
              saldo {{ item.quantidade | number: '1.0-3' }} {{ item.unidadeMedida }}
            </li>
          }
        </ul>
        <p class="mt-1">
          O ideal é informar ao gestor da equipe que {{ itens().length === 1 ? 'esse material está negativo' : 'esses materiais estão negativos' }}.
          A entrega pode seguir normalmente.
        </p>
      </ces-callout>
    }
  `,
})
export class AvisoSaldoNegativoEquipeComponent {
  /** Saldos negativos da equipe, já agrupados por material. */
  readonly saldosNegativos = input<ReadonlyMap<string, SaldoNegativoMaterial>>(new Map());
  /** Materiais desta entrega; o aviso considera apenas eles. */
  readonly idsMateriais = input<readonly string[]>([]);
  /** Código da equipe, quando conhecido. */
  readonly equipe = input<string | null | undefined>(null);

  protected readonly itens = computed(() => {
    const mapa = this.saldosNegativos();
    const vistos = new Set<string>();
    return this.idsMateriais()
      .filter((id) => !vistos.has(id) && vistos.add(id))
      .map((id) => mapa.get(id))
      .filter((item): item is SaldoNegativoMaterial => !!item);
  });
}
