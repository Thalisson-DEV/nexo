import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesDatepickerComponent,
  CesFormDrawerComponent,
  CesNumberInputComponent,
  CesTextInputComponent,
  NotificationService
} from '@ces/ui';
import { LeituraOdometro, paraInstanteIso } from '../../../core/models/frota.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FrotaService } from '../../../core/services/frota.service';

/** Lançamento manual de quilometragem. O servidor recusa valor incoerente com o histórico. */
@Component({
  selector: 'app-frota-lancar-km',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    CesButtonComponent,
    CesCalloutComponent,
    CesDatepickerComponent,
    CesFormDrawerComponent,
    CesNumberInputComponent,
    CesTextInputComponent
  ],
  template: `
    <ces-form-drawer [(visible)]="visivel" title="Lançar KM"
      [description]="'Veículo ' + placa() + '. Informe a quilometragem do painel; valor incoerente com o histórico é recusado.'"
      [dirty]="km() !== null" [loading]="salvando()" size="sm">
      <div class="ces-form-stack">
        @if (kmAtual() != null) {
          <ces-callout tone="info" [dense]="true">KM atual registrado: {{ kmAtual() | number: '1.0-0' }} km.</ces-callout>
        }
        <ces-number-input label="Quilometragem" [required]="true" [(value)]="km" [min]="0" suffix=" km" [error]="erroKm()"></ces-number-input>
        <div class="ces-form-grid">
          <ces-datepicker label="Data da leitura" [required]="true" [(value)]="data" [maxDate]="hoje"></ces-datepicker>
          <ces-text-input label="Hora" [required]="true" [(value)]="hora" placeholder="HH:mm" [maxlength]="5" [error]="erroHora()"></ces-text-input>
        </div>
      </div>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="visivel.set(false)"></ces-button>
        <ces-button icon="pi pi-check" label="Registrar leitura" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>
  `
})
export class FrotaLancarKmComponent {
  private readonly frota = inject(FrotaService);
  private readonly notificacoes = inject(NotificationService);

  readonly visivel = model(false);
  readonly idVeiculo = input.required<string>();
  readonly placa = input('');
  readonly kmAtual = input<number | null>(null);
  readonly registrado = output<LeituraOdometro>();

  protected readonly hoje = new Date();
  protected readonly km = signal<number | null>(null);
  protected readonly data = signal<Date | null>(new Date());
  protected readonly hora = signal('');
  protected readonly erroKm = signal<string | undefined>(undefined);
  protected readonly erroHora = signal<string | undefined>(undefined);
  protected readonly salvando = signal(false);

  constructor() {
    effect(() => {
      if (this.visivel()) {
        untracked(() => {
          const agora = new Date();
          this.km.set(null);
          this.data.set(agora);
          this.hora.set(`${String(agora.getHours()).padStart(2, '0')}:${String(agora.getMinutes()).padStart(2, '0')}`);
          this.erroKm.set(undefined);
          this.erroHora.set(undefined);
        });
      }
    });
  }

  protected salvar(): void {
    const km = this.km();
    const data = this.data();
    const hora = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(this.hora().trim());
    this.erroKm.set(km === null ? 'Informe a quilometragem.' : undefined);
    this.erroHora.set(hora ? undefined : 'Use o formato HH:mm.');
    if (km === null || !data || !hora) return;
    const instante = new Date(data);
    instante.setHours(Number(hora[1]), Number(hora[2]), 0, 0);
    this.salvando.set(true);
    this.frota.registrarLeitura(this.idVeiculo(), { km, lidoEm: paraInstanteIso(instante) }).subscribe({
      next: (leitura) => {
        this.salvando.set(false);
        this.notificacoes.success('Leitura registrada.');
        this.km.set(null);
        this.visivel.set(false);
        this.registrado.emit(leitura);
      },
      error: (erro) => {
        this.salvando.set(false);
        this.erroKm.set(getErrorMessage(erro));
      }
    });
  }
}
