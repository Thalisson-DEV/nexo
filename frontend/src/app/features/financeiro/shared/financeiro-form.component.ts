import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal
} from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { DatePickerModule } from 'primeng/datepicker';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TextareaModule } from 'primeng/textarea';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  CampoFormulario,
  FormularioSchema,
  ICONE_GRUPO,
  ReferenciaOpcao,
  ValorSolicitacao
} from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup as CursorRemoteLookup } from '../../../core/lookup/store/remote-lookup';
import { CesAutocompleteComponent } from '@ces/ui/forms';
import {
  agruparCampos,
  aplicarValidacaoCondicional,
  campoVisivel,
  ehBoleto,
  ehCampoDadosPagamento,
  ehCampoFormaPagamento,
  ehFormaPagamentoBoleto,
  filtrarPorModoFornecedor,
  limparCamposOcultos,
  limparFornecedorForaDoModo,
  mensagemDeErro,
  modoFornecedorDe,
  ofereceEscolhaDeFornecedor,
  validadoresDoCampo,
  GrupoDeCampos,
  ModoFornecedor
} from './campos-financeiros';

@Component({
  selector: 'app-financeiro-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    DatePickerModule,
    InputNumberModule,
    InputTextModule,
    SelectModule,
    SelectButtonModule,
    TextareaModule,
    DateMaskDirective,
    CesAutocompleteComponent
  ],
  template: `
    <div class="flex flex-col gap-6">
      @for (grupo of gruposVisiveis(); track grupo.grupo; let indice = $index) {
        <section class="flex flex-col">
          <div class="section-marker">
            <i [class]="icone(grupo.grupo) + ' text-primary-600 dark:text-primary-400 text-sm mr-1'"></i>
            <span class="text-slate-800 dark:text-slate-100 font-bold text-xs md:text-sm tracking-tight">{{ indice + 1 }}. {{ grupo.rotulo }}</span>
          </div>

          @if (grupo.grupo === 'FORNECEDOR' && temEscolhaDeFornecedor()) {
            <!--
              O domínio aceita o fornecedor de duas formas e exige exatamente uma.
              Mostrar os dois campos lado a lado fazia parecer que ambos eram
              necessários; aqui a pessoa escolhe o caminho e só ele aparece.
            -->
            <div class="md:pl-5 mb-4">
              <div class="p-3.5 bg-slate-50/80 dark:bg-slate-800/40 rounded-xl border border-slate-100 dark:border-slate-800 flex flex-col gap-2">
                <span class="text-xs font-semibold text-slate-700 dark:text-slate-200">
                  Este fornecedor já está cadastrado?
                </span>
                <p-selectbutton [ngModel]="modoFornecedor()" [ngModelOptions]="{ standalone: true }"
                  (ngModelChange)="trocarModoFornecedor($event)" [options]="opcoesFornecedor"
                  optionLabel="rotulo" optionValue="valor" [allowEmpty]="false"
                  size="small" styleClass="w-full booleano-toggle" />
                <span class="field-help text-[9px] text-slate-400 dark:text-slate-400 italic">
                  {{ modoFornecedor() === 'CADASTRADO'
                    ? 'Escolha na lista de fornecedores já homologados pelo financeiro.'
                    : 'Digite o nome. O financeiro completa o cadastro e homologa depois.' }}
                </span>
              </div>
            </div>
          }

          <div class="grid grid-cols-1 md:grid-cols-12 gap-4 md:pl-5">
            @for (campo of grupo.campos; track campo.chave) {
              <div class="field" [ngClass]="larguraDe(campo)">
                <label [for]="campo.chave" class="text-[11px] font-medium text-slate-700 dark:text-slate-200 leading-snug break-words">
                  {{ campo.rotulo }}
                  @if (campo.obrigatorio) {
                    <span class="text-red-500 font-bold">*</span>
                  }
                </label>

                @switch (campo.tipoCampo) {
                  @case ('TEXTO') {
                    <input pInputText [id]="campo.chave" [formControl]="controle(campo)"
                      [placeholder]="campo.placeholder || ''" [attr.maxlength]="campo.restricoes?.tamanhoMaximo"
                      class="w-full" size="small" />
                  }
                  @case ('TEXTO_LONGO') {
                    <textarea pTextarea [id]="campo.chave" [formControl]="controle(campo)"
                      [placeholder]="campo.placeholder || ''" [attr.maxlength]="campo.restricoes?.tamanhoMaximo"
                      rows="3" class="w-full resize-y !text-xs min-h-[60px]"></textarea>
                  }
                  @case ('INTEIRO') {
                    <p-inputnumber [inputId]="campo.chave" [formControl]="controle(campo)"
                      [useGrouping]="false" [minFractionDigits]="0" [maxFractionDigits]="0"
                      [min]="campo.restricoes?.valorMinimo ?? undefined"
                      [max]="campo.restricoes?.valorMaximo ?? undefined"
                      size="small" class="w-full" styleClass="w-full" [fluid]="true"></p-inputnumber>
                  }
                  @case ('DECIMAL') {
                    <p-inputnumber [inputId]="campo.chave" [formControl]="controle(campo)"
                      mode="decimal" locale="pt-BR" [minFractionDigits]="0" [maxFractionDigits]="4"
                      [min]="campo.restricoes?.valorMinimo ?? undefined"
                      [max]="campo.restricoes?.valorMaximo ?? undefined"
                      size="small" class="w-full" styleClass="w-full" [fluid]="true"></p-inputnumber>
                  }
                  @case ('MOEDA') {
                    <p-inputnumber [inputId]="campo.chave" [formControl]="controle(campo)"
                      mode="currency" currency="BRL" locale="pt-BR"
                      [min]="campo.restricoes?.valorMinimo ?? undefined"
                      [max]="campo.restricoes?.valorMaximo ?? undefined"
                      size="small" class="w-full" styleClass="w-full" [fluid]="true"></p-inputnumber>
                  }
                  @case ('DATA') {
                    <p-datepicker [inputId]="campo.chave" [formControl]="controle(campo)"
                      dateFormat="dd/mm/yy" [showIcon]="true" iconDisplay="input" [showClear]="true"
                      [showButtonBar]="true" placeholder="DD/MM/AAAA"
                      size="small" class="w-full" styleClass="w-full" [fluid]="true" appendTo="body"></p-datepicker>
                  }
                  @case ('BOOLEANO') {
                    <p-selectbutton [formControl]="controle(campo)" [options]="opcoesSimNao"
                      optionLabel="rotulo" optionValue="valor" [allowEmpty]="false"
                      size="small" styleClass="w-full booleano-toggle" [ariaLabelledBy]="campo.chave"></p-selectbutton>
                  }
                  @case ('SELECAO_UNICA') {
                    <p-select [inputId]="campo.chave" [formControl]="controle(campo)"
                      [options]="campo.opcoes" optionLabel="rotulo" optionValue="valor"
                      [placeholder]="campo.placeholder || 'Selecione...'"
                      [showClear]="!campo.obrigatorio" [filter]="campo.opcoes.length > 8"
                      filterBy="rotulo" size="small" class="w-full" styleClass="w-full" [fluid]="true" appendTo="body"></p-select>
                  }
                  @case ('REFERENCIA') {
                    @if (ehReferenciaColaborador(campo)) {
                      <ces-autocomplete
                        [label]="campo.rotulo"
                        [labelVisible]="false"
                        [inputId]="campo.chave"
                        [formControl]="controle(campo)"
                        [options]="opcoesColaborador()"
                        [loading]="colaboradorLoading()"
                        [status]="colaboradorStatus()"
                        [error]="colaboradorError()"
                        [placeholder]="campo.placeholder || 'Pesquise e selecione...'"
                        [clearable]="!campo.obrigatorio"
                        [virtualScroll]="true"
                        [virtualScrollItemSize]="38"
                        appendTo="body"
                        (search)="pesquisarColaboradores($event)"
                        (loadMore)="carregarMaisColaboradores()"
                        (retry)="tentarNovamenteColaboradores()"
                      ></ces-autocomplete>
                    } @else {
                      <p-select [inputId]="campo.chave" [formControl]="controle(campo)"
                        [options]="opcoesReferencia(campo)"
                        optionLabel="label" optionValue="id"
                        [placeholder]="campo.placeholder || 'Pesquise e selecione...'"
                        [showClear]="!campo.obrigatorio" [filter]="true" filterBy="label"
                        [loading]="carregandoReferencia(campo)"
                        [virtualScroll]="true" [virtualScrollItemSize]="38" [lazy]="true"
                        emptyMessage="Nenhum cadastro encontrado"
                        emptyFilterMessage="Nenhum cadastro encontrado"
                        size="small" class="w-full" styleClass="w-full" [fluid]="true" appendTo="body"
                        (onShow)="carregarReferencia(campo)"
                        (onFilter)="filtrarReferencia(campo, $event)"
                        (onLazyLoad)="carregarMaisReferencia(campo)"></p-select>
                    }
                  }
                }

                <div class="field-meta">
                  <span class="form-error">{{ erroDoCampo(campo) }}</span>
                  @if (contador(campo); as texto) { <span>{{ texto }}</span> }
                </div>

                @if (campo.ajuda && !erroDoCampo(campo)) {
                  <span class="field-help">{{ campo.ajuda }}</span>
                }
                @if (somenteLeitura().has(campo.chave)) {
                  <span class="field-help">
                    <i class="pi pi-lock text-[8px] mr-1"></i>Herdado da compra vinculada.
                  </span>
                }
              </div>
            }
          </div>
        </section>
      }
    </div>
  `,
  styles: [`
    .section-marker {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      border-left: 2px solid var(--p-primary-500);
      padding-left: 0.75rem;
      margin-bottom: 0.875rem;
    }
    :host-context(.app-dark) .section-marker,
    :host-context(.dark) .section-marker {
      border-left-color: var(--p-primary-400) !important;
    }
    .section-marker span {
      color: #374151;
      font-size: 13px;
      font-weight: 700;
      letter-spacing: -0.01em;
      text-transform: none;
    }
    :host-context(.app-dark) .section-marker span,
    :host-context(.dark) .section-marker span {
      color: #f1f5f9 !important;
    }
    .field { display: flex; flex-direction: column; gap: 0.375rem; }
    .field > label { color: #475569; font-size: 11px; font-weight: 500; letter-spacing: normal; text-transform: none; line-height: 1.35; }
    :host-context(.app-dark) .field > label,
    :host-context(.dark) .field > label {
      color: #cbd5e1 !important;
    }
    .field-meta { display: flex; justify-content: space-between; gap: 0.5rem; color: #94a3b8; font-size: 9px; min-height: 0.75rem; }
    :host-context(.app-dark) .field-meta,
    :host-context(.dark) .field-meta {
      color: #64748b !important;
    }
    .field-meta:empty { display: none; }
    .field-help { color: #94a3b8; font-size: 9px; font-style: italic; line-height: 1.3; }
    :host-context(.app-dark) .field-help,
    :host-context(.dark) .field-help {
      color: #94a3b8 !important;
    }
    .form-error { color: #ef4444; font-size: 9px; }
    :host-context(.app-dark) .form-error,
    :host-context(.dark) .form-error {
      color: #f87171 !important;
    }
    :host ::ng-deep {
      .field .p-inputtext,
      .field .p-select,
      .field .p-inputnumber,
      .field .p-datepicker {
        width: 100%;
        font-size: 11px;
      }
      .booleano-toggle.p-selectbutton {
        display: flex;
        width: 100%;
        gap: 0.25rem;
        background: transparent !important;
        border: none !important;
        padding: 0 !important;
      }
      .booleano-toggle .p-togglebutton {
        flex: 1 1 0;
        font-size: 10px;
        font-weight: 700;
        letter-spacing: 0.05em;
        text-transform: uppercase;
        border-radius: 0.5rem !important;
        padding: 0.5rem 0.75rem !important;
        min-height: 2.25rem !important;
        transition: all 0.2s ease-in-out;
        border: 1px solid #e2e8f0 !important;
        background: #f8fafc !important;
        color: #64748b !important;
      }
      .booleano-toggle .p-togglebutton:not(.p-togglebutton-checked):hover {
        background: #f1f5f9 !important;
        color: #1e293b !important;
        border-color: #cbd5e1 !important;
      }
      .booleano-toggle .p-togglebutton.p-togglebutton-checked,
      .booleano-toggle .p-togglebutton[data-p-checked="true"],
      .booleano-toggle .p-togglebutton[aria-pressed="true"] {
        background: var(--p-primary-600, #2563eb) !important;
        border-color: var(--p-primary-600, #2563eb) !important;
        color: #ffffff !important;
        box-shadow: none !important;
      }
      .booleano-toggle .p-togglebutton.p-togglebutton-checked *,
      .booleano-toggle .p-togglebutton[data-p-checked="true"] *,
      .booleano-toggle .p-togglebutton[aria-pressed="true"] * {
        color: #ffffff !important;
      }
      .ng-invalid.ng-touched .p-inputtext,
      input.ng-invalid.ng-touched,
      textarea.ng-invalid.ng-touched {
        border-color: var(--p-red-400);
      }
    }
    :host-context(.app-dark) :host ::ng-deep,
    :host-context(.dark) :host ::ng-deep {
      .booleano-toggle .p-togglebutton {
        background: #1e293b !important;
        border-color: #334155 !important;
        color: #94a3b8 !important;
      }
      .booleano-toggle .p-togglebutton:not(.p-togglebutton-checked):hover {
        background: #334155 !important;
        color: #f8fafc !important;
        border-color: #475569 !important;
      }
      .booleano-toggle .p-togglebutton.p-togglebutton-checked,
      .booleano-toggle .p-togglebutton[data-p-checked="true"],
      .booleano-toggle .p-togglebutton[aria-pressed="true"] {
        background: var(--p-primary-600, #2563eb) !important;
        border-color: var(--p-primary-500, #3b82f6) !important;
        color: #ffffff !important;
        box-shadow: none !important;
      }
      .booleano-toggle .p-togglebutton.p-togglebutton-checked *,
      .booleano-toggle .p-togglebutton[data-p-checked="true"] *,
      .booleano-toggle .p-togglebutton[aria-pressed="true"] * {
        color: #ffffff !important;
      }
    }
  `]
})
export class FinanceiroFormComponent {
  private readonly service = inject(FinanceiroService);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly referencias = new Map<string, RemoteLookup<ReferenciaOpcao, string>>();
  private readonly colaboradorLookup = new CursorRemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchCollaborators({ query, cursor }),
    selectedValues: () =>
      this.campos()
        .filter((campo) => this.ehReferenciaColaborador(campo))
        .map((campo) => this.form().get(campo.chave)?.value)
        .filter((value): value is string => typeof value === 'string' && value.length > 0),
    destroyRef: this.destroyRef,
  });
  private schemaConfigurado: string | null = null;
  /**
   * Enquanto a pessoa não escolher, o modo acompanha o que chega de fora —
   * carregar uma solicitação existente ou herdar os dados de uma compra. Depois
   * da primeira escolha, quem manda é ela.
   */
  private modoEscolhidoPeloUsuario = false;
  private ultimasExibicoes = '';
  private ultimaFormaPagamento: string | null = null;

  readonly schema = input.required<FormularioSchema>();
  readonly form = input.required<FormGroup>();
  readonly errosServidor = input<Record<string, string>>({});
  readonly somenteLeitura = input<Set<string>>(new Set());
  readonly valoresExibicao = input<Record<string, ValorSolicitacao>>({});
  readonly pronto = output<void>();
  /**
   * Rótulos das referências escolhidas agora na tela.
   *
   * Quem sabe que `bbf42503-…` é "João da Silva" é este componente, que carregou a
   * lista do cadastro. Sem isto, a revisão exibiria o UUID cru — o valor bruto é
   * tudo o que o formulário guarda.
   */
  readonly exibicoesResolvidas = output<Record<string, ValorSolicitacao>>();

  /** Força a reavaliação das condições a cada mudança de valor. */
  protected readonly tick = signal(0);
  protected readonly opcoesSimNao = [
    { rotulo: 'Sim', valor: true },
    { rotulo: 'Não', valor: false }
  ];
  protected readonly icone = (grupo: keyof typeof ICONE_GRUPO) => ICONE_GRUPO[grupo];

  protected readonly modoFornecedor = signal<ModoFornecedor>('CADASTRADO');
  protected readonly opcoesFornecedor = [
    { rotulo: 'Sim, está cadastrado', valor: 'CADASTRADO' },
    { rotulo: 'Não, vou digitar o nome', valor: 'NOVO' }
  ];

  /** Blocos que têm ao menos um campo visível agora, já filtrados pelo modo escolhido e regras de negócio. */
  protected readonly gruposVisiveis = computed<GrupoDeCampos[]>(() => {
    this.tick();
    const modo = this.modoFornecedor();
    const isBoleto = ehFormaPagamentoBoleto(this.form(), this.campos());
    const blocos = agruparCampos(this.campos(), (chave) => this.form().get(chave)?.value);

    return blocos
      .map((bloco) => {
        let campos = bloco.campos;
        if (bloco.grupo === 'FORNECEDOR' && this.temEscolhaDeFornecedor()) {
          campos = filtrarPorModoFornecedor(campos, modo);
        }
        if (isBoleto) {
          campos = campos.filter((c) => !ehCampoDadosPagamento(c));
        }
        return { ...bloco, campos };
      })
      .filter((bloco) => bloco.campos.length > 0);
  });

  constructor() {
    effect(() => {
      const schema = this.schema();
      if (schema.id === this.schemaConfigurado) return;
      this.schemaConfigurado = schema.id;
      this.configurarControles(schema);
    });
    effect(() => {
      this.somenteLeitura();
      if (this.schemaConfigurado) this.aplicarEstadoControles();
    });
    // Semeia a lista de um campo de referência com a opção já escolhida, para que
    // o rótulo apareça antes de o usuário abrir o seletor.
    effect(() => {
      const valores = this.valoresExibicao();
      for (const campo of this.campos().filter((item) => item.tipoCampo === 'REFERENCIA')) {
        const item = valores[campo.chave];
        if (typeof item?.valor === 'string' && item.exibicao) {
          if (this.ehReferenciaColaborador(campo)) {
            this.colaboradorLookup.seedSelected({ value: item.valor, label: item.exibicao });
          } else {
            this.referencias
              .get(campo.chave)
              ?.setSeed([{ id: item.valor, label: item.exibicao, raw: {} }]);
          }
        }
      }
    });
  }

  protected campos(): CampoFormulario[] {
    return this.schema().campos;
  }

  protected temEscolhaDeFornecedor(): boolean {
    return ofereceEscolhaDeFornecedor(this.campos());
  }

  protected ehReferenciaColaborador(campo: CampoFormulario): boolean {
    return campo.tipoCampo === 'REFERENCIA' &&
      campo.referencia?.fonte?.trim().toUpperCase() === 'COLABORADOR';
  }

  protected readonly opcoesColaborador = this.colaboradorLookup.items;
  protected readonly colaboradorLoading = this.colaboradorLookup.loading;
  protected readonly colaboradorStatus = this.colaboradorLookup.status;
  protected readonly colaboradorError = computed(() => {
    const error = this.colaboradorLookup.error();
    return error ? getErrorMessage(error) : undefined;
  });

  /** Troca o caminho e apaga o que ficou para trás, para nunca enviar os dois. */
  protected trocarModoFornecedor(modo: ModoFornecedor): void {
    this.modoEscolhidoPeloUsuario = true;
    this.modoFornecedor.set(modo);
    limparFornecedorForaDoModo(this.form(), modo);
    this.tick.update((valor) => valor + 1);
  }

  protected controle(campo: CampoFormulario): FormControl {
    return this.form().get(campo.chave) as FormControl;
  }

  /** Larguras que deixam o formulário parecer um documento, e não uma coluna. */
  protected larguraDe(campo: CampoFormulario): string {
    switch (campo.tipoCampo) {
      case 'TEXTO_LONGO':
        return 'col-span-1 md:col-span-12';
      case 'BOOLEANO':
      case 'INTEIRO':
      case 'DATA':
        return 'col-span-1 md:col-span-6 lg:col-span-3';
      case 'MOEDA':
      case 'DECIMAL':
      case 'SELECAO_UNICA':
        return 'col-span-1 md:col-span-6 lg:col-span-4';
      default:
        return 'col-span-1 md:col-span-12 lg:col-span-6';
    }
  }

  protected erroDoCampo(campo: CampoFormulario): string | null {
    return this.errosServidor()[campo.chave] ?? mensagemDeErro(campo, this.form().get(campo.chave));
  }

  /** Contador de caracteres nos campos com limite, como nas demais telas. */
  protected contador(campo: CampoFormulario): string | null {
    const maximo = campo.restricoes?.tamanhoMaximo;
    if (maximo == null) return null;
    const valor = this.form().get(campo.chave)?.value;
    return `${typeof valor === 'string' ? valor.length : 0}/${maximo}`;
  }

  protected carregarReferencia(campo: CampoFormulario): void {
    this.referencias.get(campo.chave)?.open();
  }

  protected filtrarReferencia(campo: CampoFormulario, event: { filter?: string }): void {
    this.referencias.get(campo.chave)?.search(event.filter ?? '');
  }

  protected carregarMaisReferencia(campo: CampoFormulario): void {
    this.referencias.get(campo.chave)?.loadNext();
  }

  protected pesquisarColaboradores(query: string): void {
    this.colaboradorLookup.search(query);
  }

  protected carregarMaisColaboradores(): void {
    this.colaboradorLookup.loadNext();
  }

  protected tentarNovamenteColaboradores(): void {
    this.colaboradorLookup.retry();
  }

  protected opcoesReferencia(campo: CampoFormulario): ReferenciaOpcao[] {
    return this.referencias.get(campo.chave)?.items() ?? [];
  }

  protected carregandoReferencia(campo: CampoFormulario): boolean {
    return this.referencias.get(campo.chave)?.loading() ?? false;
  }

  private configurarControles(schema: FormularioSchema): void {
    for (const referencia of this.referencias.values()) referencia.destroy();
    this.referencias.clear();

    for (const campo of schema.campos) {
      if (!this.form().contains(campo.chave)) {
        const valorInicial = campo.chave === 'quantidadeParcelas' ? 1 : null;
        this.form().addControl(campo.chave, new FormControl(valorInicial, validadoresDoCampo(campo)));
      }
      this.configurarReferencia(campo);
    }

    this.aplicarEstadoControles();
    this.pronto.emit();
    // Depois que o pai carregou os valores (edição ou herança de uma compra), o
    // modo precisa refletir o que já está lá — senão a tela abriria no caminho
    // errado e esconderia o fornecedor que a pessoa havia informado.
    this.modoFornecedor.set(modoFornecedorDe(this.form()));

    const campoPagamentoInicial = this.campos().find(ehCampoFormaPagamento);
    const campoDadosInicial = this.campos().find(ehCampoDadosPagamento);
    const ctrlPagamentoInicial = campoPagamentoInicial ? this.form().get(campoPagamentoInicial.chave) : null;
    const ctrlDadosInicial = campoDadosInicial ? this.form().get(campoDadosInicial.chave) : null;
    this.ultimaFormaPagamento = ctrlPagamentoInicial?.value != null ? String(ctrlPagamentoInicial.value) : null;
    if (campoPagamentoInicial && campoDadosInicial && ctrlPagamentoInicial && ctrlDadosInicial) {
      if (ehBoleto(ctrlPagamentoInicial.value, campoPagamentoInicial)) {
        if (!ctrlDadosInicial.value || ctrlDadosInicial.value !== 'boleto') {
          ctrlDadosInicial.setValue('boleto', { emitEvent: false });
        }
      }
    }

    this.form()
      .valueChanges.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        limparCamposOcultos(this.form(), this.campos());
        aplicarValidacaoCondicional(this.form(), this.campos());
        this.aplicarEstadoControles();
        
        // Tratamento da Forma de Pagamento = Boleto e Dados Bancários
        const campoPagamento = this.campos().find(ehCampoFormaPagamento);
        const campoDados = this.campos().find(ehCampoDadosPagamento);
        if (campoPagamento && campoDados) {
          const ctrlPagamento = this.form().get(campoPagamento.chave);
          const ctrlDados = this.form().get(campoDados.chave);
          if (ctrlPagamento && ctrlDados) {
            const formaAtual = ctrlPagamento.value != null ? String(ctrlPagamento.value) : null;
            const formaMudou = formaAtual !== this.ultimaFormaPagamento;
            const ehBoletoAtual = ehBoleto(formaAtual, campoPagamento);

            if (ehBoletoAtual) {
              if (ctrlDados.value !== 'boleto') {
                ctrlDados.setValue('boleto', { emitEvent: false });
              }
            } else if (formaMudou) {
              ctrlDados.setValue(null, { emitEvent: false });
            }
            this.ultimaFormaPagamento = formaAtual;
          }
        }

        // Bloqueio condicional de Parcelas para A Vista / Débito
        const ctrlParcelas = this.form().get('quantidadeParcelas');
        if (campoPagamento && ctrlParcelas) {
          const ctrlPagamento = this.form().get(campoPagamento.chave);
          if (ctrlPagamento) {
            const valor = String(ctrlPagamento.value || '');
            const opcao = campoPagamento.opcoes?.find(o => String(o.valor) === valor);
            const texto = opcao ? String(opcao.rotulo).toUpperCase() : valor.toUpperCase();
            const isVistaOuDebito = texto.includes('VISTA') || texto.includes('DÉBITO') || texto.includes('DEBITO');
            
            if (isVistaOuDebito) {
              if (ctrlParcelas.value !== 1) ctrlParcelas.setValue(1, { emitEvent: false });
              if (ctrlParcelas.enabled && !this.somenteLeitura().has('quantidadeParcelas')) ctrlParcelas.disable({ emitEvent: false });
            } else {
              if (ctrlParcelas.disabled && !this.somenteLeitura().has('quantidadeParcelas')) ctrlParcelas.enable({ emitEvent: false });
            }
          }
        }

        if (!this.modoEscolhidoPeloUsuario) {
          this.modoFornecedor.set(modoFornecedorDe(this.form()));
        }
        this.publicarExibicoes();
        this.tick.update((valor) => valor + 1);
      });
  }

  /**
   * Publica ao pai o texto legível de cada referência escolhida, para que a
   * revisão e o resumo mostrem o nome em vez do identificador.
   */
  private publicarExibicoes(): void {
    const mapa: Record<string, ValorSolicitacao> = {};
    for (const campo of this.campos()) {
      if (campo.tipoCampo !== 'REFERENCIA') continue;
      const valor = this.form().get(campo.chave)?.value;
      if (typeof valor !== 'string' || !valor) continue;
      const opcao = this.ehReferenciaColaborador(campo)
        ? this.colaboradorLookup.items().find((item) => item.value === valor)
        : (this.referencias.get(campo.chave)?.items() ?? []).find((item) => item.id === valor);
      if (opcao) mapa[campo.chave] = { valor, exibicao: opcao.label };
    }

    // Só emite quando algo mudou de fato: o pai devolve isto como entrada, e
    // reemitir o mesmo mapa a cada ciclo criaria um vaivém sem fim.
    const assinatura = JSON.stringify(mapa);
    if (assinatura === this.ultimasExibicoes) return;
    this.ultimasExibicoes = assinatura;
    this.exibicoesResolvidas.emit(mapa);
  }

  private aplicarEstadoControles(): void {
    for (const campo of this.campos()) {
      const control = this.form().get(campo.chave);
      const bloquear = this.somenteLeitura().has(campo.chave);
      if (bloquear && control?.enabled) control.disable({ emitEvent: false });
      if (!bloquear && control?.disabled) control.enable({ emitEvent: false });
    }
  }

  private configurarReferencia(campo: CampoFormulario): void {
    if (campo.tipoCampo !== 'REFERENCIA' || !campo.referencia || this.ehReferenciaColaborador(campo)) return;
    const referencia = campo.referencia;
    const lookup = new RemoteLookup<ReferenciaOpcao, string>(
      {
        identify: (item) => item.id,
        selectedKeys: () => {
          const valor = this.form().get(campo.chave)?.value;
          return typeof valor === 'string' && valor ? [valor] : [];
        },
        load: ({ query, page, size }) =>
          this.service.buscarOpcoesReferencia(
            referencia.endpointOpcoes,
            query,
            referencia.fonte === 'FORNECEDOR',
            page,
            size
          )
      },
      this.destroyRef
    );
    this.referencias.set(campo.chave, lookup);
  }

  /** Exposto para os testes que cobrem a visibilidade condicional. */
  protected visivel(campo: CampoFormulario): boolean {
    this.tick();
    return campoVisivel(campo, this.campos(), (chave) => this.form().get(chave)?.value);
  }
}
