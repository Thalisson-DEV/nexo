import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import {
  CesButtonComponent,
  CesDatepickerComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesSelectOption,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';
import { LookupStore } from '../../../core/lookup';
import {
  Combustivel,
  OpcoesFrota,
  ProvedorTelemetria,
  RegimeVeiculo,
  SituacaoOperacional,
  TipoVeiculo,
  VeiculoFrotaDetalhe,
  VeiculoFrotaRequest,
  deDataIso,
  paraDataIso
} from '../../../core/models/frota.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FrotaService } from '../../../core/services/frota.service';

/** Cadastro e edição do veículo: dados do RH (placa, modelo, base) e ficha da frota. */
@Component({
  selector: 'app-frota-veiculo-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesDatepickerComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesTextInputComponent,
    CesTextareaComponent
  ],
  template: `
    <ces-form-drawer
      [(visible)]="visivel"
      [title]="veiculo() ? 'Editar ficha' : 'Novo veículo'"
      [description]="(veiculo() ? 'Veículo ' + veiculo()!.placa + '. ' : '') + 'Placa, modelo e base ficam no cadastro geral de veículos; o restante é a ficha da frota.'"
      [dirty]="alterado()"
      [loading]="salvando()"
      size="lg"
    >
      <ces-form-section title="Identificação" icon="pi pi-car">
        <div class="ces-form-grid">
          <ces-text-input label="Placa" [required]="true" [(value)]="placa" placeholder="ABC1D23" [maxlength]="10"
            [error]="erro('placa')"></ces-text-input>
          <ces-text-input label="Marca / fabricante" [required]="true" [(value)]="fabricante" [maxlength]="50"
            [error]="erro('fabricante')"></ces-text-input>
          <ces-text-input label="Modelo" [required]="true" [(value)]="modelo" [maxlength]="100"
            [error]="erro('modelo')"></ces-text-input>
          <ces-select label="Base operacional" [required]="true" [options]="bases()" [(value)]="idBase" [filter]="true"
            [error]="erro('idBase')"></ces-select>
          <ces-select label="Tipo de veículo" [options]="tipos()" [(value)]="idTipo" [filter]="true" [clearable]="true"
            [showOptional]="true"></ces-select>
          <ces-select label="Situação" [options]="situacoes()" [(value)]="situacao"></ces-select>
        </div>
      </ces-form-section>

      <ces-form-section title="Dados do veículo" icon="pi pi-id-card">
        <div class="ces-form-grid">
          <ces-number-input label="Ano de fabricação" [(value)]="anoFabricacao" [useGrouping]="false" [min]="1950"
            [showOptional]="true"></ces-number-input>
          <ces-number-input label="Ano do modelo" [(value)]="anoModelo" [useGrouping]="false" [min]="1950"
            [showOptional]="true"></ces-number-input>
          <ces-text-input label="Chassi" [(value)]="chassi" [maxlength]="30" [showOptional]="true"></ces-text-input>
          <ces-text-input label="Renavam" [(value)]="renavam" [maxlength]="20" [showOptional]="true"></ces-text-input>
          <ces-text-input label="Cor" [(value)]="cor" [maxlength]="40" [showOptional]="true"></ces-text-input>
          <ces-select label="Combustível" [options]="combustiveis()" [(value)]="combustivel" [clearable]="true"
            [showOptional]="true"></ces-select>
          <ces-number-input label="Tanque (litros)" [(value)]="capacidadeTanque" [min]="0" [maxFractionDigits]="2"
            [showOptional]="true"></ces-number-input>
          <ces-number-input label="Consumo de referência (km/l)" [(value)]="consumoReferencia" [min]="0" [maxFractionDigits]="2"
            description="Usado para conferir se os abastecimentos estão coerentes." [showOptional]="true"></ces-number-input>
        </div>
      </ces-form-section>

      <ces-form-section title="Propriedade" icon="pi pi-briefcase">
        <div class="ces-form-grid">
          <ces-select label="Regime" [options]="regimes()" [(value)]="regime"></ces-select>
          @if (regime() === 'LOCADO') {
            <ces-text-input label="Locadora" [required]="true" [(value)]="locadora" [maxlength]="150"
              [error]="erro('locadora')"></ces-text-input>
            <ces-number-input label="Custo mensal da locação" mode="currency" [(value)]="custoLocacao" [min]="0"
              [showOptional]="true"></ces-number-input>
            <ces-datepicker label="Início da locação" [(value)]="inicioLocacao" [clearable]="true" [showOptional]="true"></ces-datepicker>
            <ces-datepicker label="Fim da locação" [(value)]="fimLocacao" [clearable]="true" [showOptional]="true"
              [error]="erro('fimLocacao')"></ces-datepicker>
          }
        </div>
      </ces-form-section>

      <ces-form-section title="Telemetria" icon="pi pi-wifi" description="Liga o veículo ao rastreador para a integração das próximas fases.">
        <div class="ces-form-grid">
          <ces-select label="Provedor" [options]="provedores()" [(value)]="provedor" [clearable]="true" [showOptional]="true"></ces-select>
          <ces-text-input label="Código do rastreador" [(value)]="idRastreador" [maxlength]="80" [showOptional]="true"
            [error]="erro('idRastreador')"></ces-text-input>
        </div>
      </ces-form-section>

      <ces-form-section title="Observação" icon="pi pi-comment">
        <ces-textarea label="Observação" [labelVisible]="false" [(value)]="observacao" [rows]="3" [maxlength]="1000"></ces-textarea>
      </ces-form-section>

      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="visivel.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="veiculo() ? 'Salvar alterações' : 'Cadastrar veículo'"
          [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>
  `
})
export class FrotaVeiculoFormComponent {
  private readonly frota = inject(FrotaService);
  private readonly lookups = inject(LookupStore);
  private readonly notificacoes = inject(NotificationService);

  readonly visivel = model(false);
  readonly veiculo = input<VeiculoFrotaDetalhe | null>(null);
  readonly opcoes = input<OpcoesFrota | null>(null);
  readonly tiposVeiculo = input<TipoVeiculo[]>([]);
  readonly salvo = output<VeiculoFrotaDetalhe>();

  protected readonly placa = signal('');
  protected readonly modelo = signal('');
  protected readonly fabricante = signal('');
  protected readonly idBase = signal<string | null>(null);
  protected readonly idTipo = signal<string | null>(null);
  protected readonly situacao = signal<SituacaoOperacional | null>('ATIVO');
  protected readonly anoFabricacao = signal<number | null>(null);
  protected readonly anoModelo = signal<number | null>(null);
  protected readonly chassi = signal('');
  protected readonly renavam = signal('');
  protected readonly cor = signal('');
  protected readonly combustivel = signal<Combustivel | null>(null);
  protected readonly capacidadeTanque = signal<number | null>(null);
  protected readonly consumoReferencia = signal<number | null>(null);
  protected readonly regime = signal<RegimeVeiculo | null>('PROPRIO');
  protected readonly locadora = signal('');
  protected readonly custoLocacao = signal<number | null>(null);
  protected readonly inicioLocacao = signal<Date | null>(null);
  protected readonly fimLocacao = signal<Date | null>(null);
  protected readonly provedor = signal<ProvedorTelemetria | null>(null);
  protected readonly idRastreador = signal('');
  protected readonly observacao = signal('');

  protected readonly salvando = signal(false);
  protected readonly tentouSalvar = signal(false);
  private readonly snapshot = signal('');

  protected readonly bases = computed<CesSelectOption<string>[]>(() =>
    this.lookups.operationalBases().map((b) => ({ label: b.label, value: b.value }))
  );
  protected readonly tipos = computed<CesSelectOption<string>[]>(() =>
    this.tiposVeiculo()
      .filter((t) => t.ativo || t.id === this.veiculo()?.tipoVeiculo?.id)
      .map((t) => ({ label: t.nome, value: t.id }))
  );
  protected readonly situacoes = computed(() => this.paraOpcoes(this.opcoes()?.situacoes));
  protected readonly regimes = computed(() => this.paraOpcoes(this.opcoes()?.regimes));
  protected readonly combustiveis = computed(() => this.paraOpcoes(this.opcoes()?.combustiveis));
  protected readonly provedores = computed(() => this.paraOpcoes(this.opcoes()?.provedoresTelemetria));

  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.placa().trim()) e['placa'] = 'Informe a placa.';
    if (this.modelo().trim().length < 3) e['modelo'] = 'Informe o modelo (mínimo 3 caracteres).';
    if (this.fabricante().trim().length < 3) e['fabricante'] = 'Informe a marca (mínimo 3 caracteres).';
    if (!this.idBase()) e['idBase'] = 'Escolha a base operacional.';
    if (this.regime() === 'LOCADO' && !this.locadora().trim()) e['locadora'] = 'Informe a locadora do veículo locado.';
    const inicio = this.inicioLocacao();
    const fim = this.fimLocacao();
    if (inicio && fim && fim < inicio) e['fimLocacao'] = 'O fim não pode ser antes do início.';
    if (!!this.provedor() !== !!this.idRastreador().trim()) e['idRastreador'] = 'Informe o provedor e o id do rastreador juntos.';
    return e;
  });

  protected readonly alterado = computed(() => this.snapshot() !== JSON.stringify(this.montarRequest()));

  constructor() {
    this.lookups.loadBootstrap().subscribe({ error: () => undefined });
    // Recarrega o formulário toda vez que o drawer abre.
    effect(() => {
      if (this.visivel()) {
        const veiculo = this.veiculo();
        untracked(() => this.preencher(veiculo));
      }
    });
  }

  protected erro(campo: string): string | undefined {
    return this.tentouSalvar() ? this.erros()[campo] : undefined;
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (Object.keys(this.erros()).length) {
      this.notificacoes.warning('Revise os campos destacados antes de salvar.');
      return;
    }
    this.salvando.set(true);
    this.frota.salvarVeiculo(this.montarRequest(), this.veiculo()?.idVeiculo).subscribe({
      next: (salvo) => {
        this.salvando.set(false);
        this.notificacoes.success(this.veiculo() ? 'Veículo atualizado.' : `Veículo ${salvo.placa} cadastrado.`);
        this.snapshot.set(JSON.stringify(this.montarRequest()));
        this.visivel.set(false);
        this.salvo.emit(salvo);
      },
      error: (erro) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(erro));
      }
    });
  }

  private preencher(v: VeiculoFrotaDetalhe | null): void {
    this.tentouSalvar.set(false);
    this.placa.set(v?.placa ?? '');
    this.modelo.set(v?.modelo ?? '');
    this.fabricante.set(v?.fabricante ?? '');
    this.idBase.set(v?.idBaseOperacional ?? null);
    this.idTipo.set(v?.tipoVeiculo?.id ?? null);
    this.situacao.set(v?.situacao ?? 'ATIVO');
    this.anoFabricacao.set(v?.anoFabricacao ?? null);
    this.anoModelo.set(v?.anoModelo ?? null);
    this.chassi.set(v?.chassi ?? '');
    this.renavam.set(v?.renavam ?? '');
    this.cor.set(v?.cor ?? '');
    this.combustivel.set(v?.combustivel ?? null);
    this.capacidadeTanque.set(v?.capacidadeTanqueLitros ?? null);
    this.consumoReferencia.set(v?.consumoReferenciaKmL ?? null);
    this.regime.set(v?.regime ?? 'PROPRIO');
    this.locadora.set(v?.locadora ?? '');
    this.custoLocacao.set(v?.custoMensalLocacao ?? null);
    this.inicioLocacao.set(deDataIso(v?.inicioLocacao));
    this.fimLocacao.set(deDataIso(v?.fimLocacao));
    this.provedor.set(v?.provedorTelemetria ?? null);
    this.idRastreador.set(v?.idRastreador ?? '');
    this.observacao.set(v?.observacao ?? '');
    this.snapshot.set(JSON.stringify(this.montarRequest()));
  }

  private montarRequest(): VeiculoFrotaRequest {
    const texto = (valor: string) => (valor.trim() ? valor.trim() : null);
    const locado = this.regime() === 'LOCADO';
    return {
      placa: this.placa().trim().toUpperCase().replace(/[^A-Z0-9]/g, ''),
      modelo: this.modelo().trim(),
      fabricante: this.fabricante().trim(),
      idBaseOperacional: this.idBase() ?? '',
      idTipoVeiculo: this.idTipo(),
      anoFabricacao: this.anoFabricacao(),
      anoModelo: this.anoModelo(),
      chassi: texto(this.chassi()),
      renavam: texto(this.renavam()),
      cor: texto(this.cor()),
      combustivel: this.combustivel(),
      capacidadeTanqueLitros: this.capacidadeTanque(),
      consumoReferenciaKmL: this.consumoReferencia(),
      situacao: this.situacao(),
      regime: this.regime(),
      locadora: locado ? texto(this.locadora()) : null,
      custoMensalLocacao: locado ? this.custoLocacao() : null,
      inicioLocacao: locado ? paraDataIso(this.inicioLocacao()) : null,
      fimLocacao: locado ? paraDataIso(this.fimLocacao()) : null,
      provedorTelemetria: this.provedor(),
      idRastreador: texto(this.idRastreador()),
      observacao: texto(this.observacao())
    };
  }

  private paraOpcoes<T extends string>(lista?: { valor: T; rotulo: string }[]): CesSelectOption<T>[] {
    return (lista ?? []).map((o) => ({ label: o.rotulo, value: o.valor }));
  }
}
