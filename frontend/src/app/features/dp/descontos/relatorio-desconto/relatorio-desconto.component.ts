import { DateMaskDirective } from '../../../../shared/directives/date-mask.directive';
import { Component, DestroyRef, OnInit, signal, inject, computed, effect } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TableModule } from 'primeng/table';
import { ProgressSpinnerModule } from 'primeng/progressspinner';
import { MessageModule } from 'primeng/message';
import { ButtonModule } from 'primeng/button';
import { TooltipModule } from 'primeng/tooltip';
import { ToastModule } from 'primeng/toast';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { DialogModule } from 'primeng/dialog';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageService, ConfirmationService } from 'primeng/api';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';

import { RhDescontosService } from '../../../../core/services/rh-descontos.service';
import { RhService } from '../../../../core/services/rh.service';
import { UsuariosService } from '../../../../core/services/usuarios.service';
import { AuthService } from '../../../../core/services/auth.service';
import { NavigationHistoryService } from '../../../../core/navigation/navigation-history.service';
import {
  DescontoResponse,
  DescontoItemRequest,
  DescontoAnexoResponse,
  TipoEquipamentoResponse,
  MotivoDescontoResponse,
  DescontoRequest,
  getValorEfetivo,
  getEtapaValorEfetivo,
  DescontoAnexoCategoria,
  getDescontoAnexoCategoria,
  getAnexoIcon,
  getAnexoLabel,
  getAnexoContainerClass,
  inferMimeType,
  DESCONTO_ANEXO_ACCEPT,
  MAX_DESCONTO_ANEXO_SIZE_BYTES
} from '../../../../core/models/desconto.models';
import { ColaboradorResponse } from '../../../../core/models/rh.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import { CesAutocompleteComponent } from '@ces/ui/forms';

interface EtapaDesconto {
  nome: string;
  status: 'CONCLUIDO' | 'EM_ANDAMENTO' | 'PENDENTE' | 'REJEITADO';
  responsavel?: string;
  dataConclusao?: string;
  valor?: number | null;
  icon: string;
}

@Component({
  selector: 'app-relatorio-desconto',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    RouterModule,
    TableModule,
    ProgressSpinnerModule,
    MessageModule,
    ButtonModule,
    TooltipModule,
    ToastModule,
    DatePipe,
    ConfirmDialogModule,
    DialogModule,
    InputNumberModule,
    InputTextModule,
    TextareaModule,
    SelectModule,
    DatePickerModule,
    DateMaskDirective,
    CesAutocompleteComponent
  ],
  templateUrl: './relatorio-desconto.component.html',
  styles: [`
    .kv { display: flex; justify-content: space-between; align-items: center; gap: 1rem; }
    .kv-l { font-size: 10px; font-weight: 600; color: #475569; }
    :host-context(.app-dark) .kv-l { color: #94a3b8; }
    .kv-v { font-size: 11px; font-weight: 600; color: #0f172a; text-align: right; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    :host-context(.app-dark) .kv-v { color: #f8fafc; }
    .kv-v--wrap { font-size: 11px; font-weight: 600; color: #0f172a; text-align: right; white-space: normal; word-break: break-word; }
    :host-context(.app-dark) .kv-v--wrap { color: #f8fafc; }
    @keyframes riseIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: none; } }
    .rise { animation: riseIn .45s cubic-bezier(.22,.61,.36,1) both; }
    @media (prefers-reduced-motion: reduce) { .rise { animation: none; } }
    @media print {
      .fixed { display: none !important; }
      .rise { animation: none !important; }
      @page { margin: 10mm; }
      .kv-l { font-size: 9px; color: #475569 !important; }
      .kv-v, .kv-v--wrap { font-size: 9px; color: #1e293b !important; }
      * { -webkit-print-color-adjust: exact !important; print-color-adjust: exact !important; }
    }
  `]
})
export class RelatorioDescontoComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private descontosService = inject(RhDescontosService);
  private rhService = inject(RhService);
  private usuariosService = inject(UsuariosService);
  private auth = inject(AuthService);
  private messageService = inject(MessageService);
  private confirmationService = inject(ConfirmationService);
  private fb = inject(FormBuilder);
  private sanitizer = inject(DomSanitizer);
  private destroyRef = inject(DestroyRef);
  private readonly lookupStore = inject(LookupStore);
  private readonly colaboradorLookup = new RemoteLookup<string>({
    loader: ({ query, cursor }) =>
      this.lookupStore.searchCollaborators({ query, cursor, limit: 50 }),
    selectedValues: () => {
      const id = this.editForm?.get('colaboradorId')?.value;
      return typeof id === 'string' && id ? [id] : [];
    },
    destroyRef: this.destroyRef
  });

  protected desconto = signal<DescontoResponse | null>(null);
  protected etapas = signal<EtapaDesconto[]>([]);
  protected loading = signal(true);
  protected saving = signal(false);
  protected error = signal<string | null>(null);

  // Preview de Anexos
  protected anexoAccept = DESCONTO_ANEXO_ACCEPT;
  protected showPreviewModal = signal(false);
  protected previewUrl = signal<SafeResourceUrl | null>(null);
  protected previewRawUrl = signal<string | null>(null);
  protected previewNomeArquivo = signal<string>('');
  protected previewTipo = signal<DescontoAnexoCategoria>('other');
  protected loadingPreview = signal(false);

  protected readonly colaboradores = this.colaboradorLookup.items;
  protected readonly colaboradoresLoading = this.colaboradorLookup.loading;
  protected readonly colaboradoresStatus = this.colaboradorLookup.status;
  private readonly colaboradorDetalhes = signal<ColaboradorResponse[]>([]);
  protected categorias = signal<TipoEquipamentoResponse[]>([]);
  protected motivos = signal<MotivoDescontoResponse[]>([]);

  /** Nome dos responsáveis pelas etapas, indexado pelo id do usuário do IAM. */
  private responsaveis = signal<Record<string, string>>({});
  /** Ids já consultados na API — evita repetir a busca a cada recarga do desconto. */
  private responsaveisConsultados = new Set<string>();
  private colaboradoresCarregados = false;

  protected showRejeitarDialog = signal(false);
  protected motivoRejeicao = signal('');
  protected rejeitando = signal(false);
  protected isPrintMode = signal(false);
  protected readonly dataEmissao = new Date();

  // Transições com valor obrigatório (guia §4.2)
  protected showValidarDialog = signal(false);
  protected valorValidadoInput = signal<number | null>(null);
  protected validando = signal(false);

  protected showBaixarDialog = signal(false);
  protected valorBaixadoInput = signal<number | null>(null);
  protected baixando = signal(false);

  protected editForm: FormGroup = this.fb.group({
    colaboradorId: [null, Validators.required],
    motivoId: [null, Validators.required],
    dataOcorrencia: [null, Validators.required],
    quantidadeParcelas: [1, [Validators.required, Validators.min(1)]],
    observacoes: ['']
  });

  protected hasChanges = signal(false);
  private initialFormSnapshot: Record<string, unknown> | null = null;

  // Espelho reativo do formulário (o form é a fonte da verdade enquanto SOLICITADO)
  private formColaboradorId = signal<string | null>(null);
  private formMotivoId = signal<string | null>(null);
  private formParcelas = signal<number>(1);

  protected isEditavel = computed(() => this.desconto()?.status === 'SOLICITADO');

  protected colaboradorSelecionado = computed(() => {
    const id = this.isEditavel() ? this.formColaboradorId() : this.desconto()?.colaboradorId;
    if (!id) return undefined;
    return this.colaboradorDetalhes().find(c => c.id === id);
  });

  protected motivoAtual = computed(() => {
    const id = this.isEditavel() ? this.formMotivoId() : this.desconto()?.motivo?.id;
    return this.motivos().find(m => m.id === id) ?? this.desconto()?.motivo;
  });

  protected selectedMotivoTeto = computed(() => this.motivoAtual()?.tetoParcelas ?? '-');

  protected maxParcelas = computed(() => {
    const teto = this.selectedMotivoTeto();
    return typeof teto === 'number' ? teto : 999;
  });

  // ---------------------------------------------------------------
  // Valores por etapa (Sugerido -> Validado -> Baixado)
  // ---------------------------------------------------------------

  /** Valor que vale agora: baixado > validado > sugerido. */
  protected valorEfetivo = computed(() => {
    const d = this.desconto();
    return d ? getValorEfetivo(d) : 0;
  });

  protected etapaValorEfetivo = computed(() => {
    const d = this.desconto();
    return d ? getEtapaValorEfetivo(d) : '-';
  });

  protected parcelasAtuais = computed(() => {
    const d = this.desconto();
    if (!d) return 1;
    return (this.isEditavel() ? this.formParcelas() : d.quantidadeParcelas) || 1;
  });

  protected valorParcela = computed(() => this.valorEfetivo() / (this.parcelasAtuais() || 1));

  /** Diferença entre o validado e o sugerido (null quando não validado ou sem divergência). */
  protected divergenciaValidacao = computed(() => {
    const d = this.desconto();
    if (!d || d.valorValidado === null || d.valorValidado === undefined) return null;
    const diff = d.valorValidado - (d.valorSugerido ?? 0);
    return Math.abs(diff) < 0.005 ? null : diff;
  });

  /** Diferença entre o baixado e o validado (null quando não baixado ou sem divergência). */
  protected divergenciaBaixa = computed(() => {
    const d = this.desconto();
    if (!d || d.valorBaixado === null || d.valorBaixado === undefined) return null;
    const base = d.valorValidado ?? d.valorSugerido ?? 0;
    const diff = d.valorBaixado - base;
    return Math.abs(diff) < 0.005 ? null : diff;
  });

  /** Prévia da divergência enquanto o usuário digita no diálogo de validação. */
  protected previewDivergenciaValidacao = computed(() => {
    const d = this.desconto();
    const v = this.valorValidadoInput();
    if (!d || v === null || v === undefined) return 0;
    return v - (d.valorSugerido ?? 0);
  });

  /** Prévia da divergência enquanto o usuário digita no diálogo de baixa. */
  protected previewDivergenciaBaixa = computed(() => {
    const d = this.desconto();
    const v = this.valorBaixadoInput();
    if (!d || v === null || v === undefined) return 0;
    return v - (d.valorValidado ?? d.valorSugerido ?? 0);
  });

  protected somaItens = computed(() =>
    (this.desconto()?.itens ?? []).reduce((acc, item) => acc + (item.valor || 0), 0)
  );

  /** Exposto para o template formatar divergências sem sinal. */
  protected readonly abs = Math.abs;

  constructor() {
    this.editForm.valueChanges.subscribe((value) => {
      this.formColaboradorId.set(value.colaboradorId ?? null);
      this.formMotivoId.set(value.motivoId ?? null);
      this.formParcelas.set(value.quantidadeParcelas || 1);
      if (!this.desconto() || this.desconto()?.status !== 'SOLICITADO') return;
      this.hasChanges.set(this.checkIfChanged());
    });

    effect(() => {
      const id = this.isEditavel() ? this.formColaboradorId() : this.desconto()?.colaboradorId;
      if (!id || this.colaboradorDetalhes().some(colaborador => colaborador.id === id)) return;
      this.rhService.buscarColaboradorPorId(id).subscribe({
        next: (colaborador) => {
          const idAtual = this.isEditavel()
            ? this.formColaboradorId()
            : this.desconto()?.colaboradorId;
          if (idAtual !== id) return;
          this.colaboradorDetalhes.update(items => [
            ...items.filter(item => item.id !== colaborador.id),
            colaborador
          ]);
          this.colaboradorLookup.seedSelected({
            value: colaborador.id,
            label: colaborador.nomeCompleto
          });
        },
        error: () => {}
      });
    });

    // Regra de teto de parcelas (guia §4 regra 1) — o motivo é editável enquanto SOLICITADO
    effect(() => {
      const motivo = this.motivoAtual();
      const parcelasCtrl = this.editForm.controls['quantidadeParcelas'];
      if (motivo && parcelasCtrl && this.desconto()?.status === 'SOLICITADO') {
        parcelasCtrl.setValidators([Validators.required, Validators.min(1), Validators.max(motivo.tetoParcelas)]);
        if (parcelasCtrl.value > motivo.tetoParcelas) {
          parcelasCtrl.setValue(motivo.tetoParcelas);
        }
        parcelasCtrl.updateValueAndValidity({ emitEvent: false });
      }
    });
  }

  // Formulário Inline de Item
  protected showItemForm = signal(false);
  protected newItem = {
    descricaoMaterial: '',
    tipoEquipamentoId: null as string | null,
    valor: null as number | null
  };
  protected editingItemId = signal<string | null>(null);
  protected inlineEditDraft = {
    id: '',
    descricaoMaterial: '',
    tipoEquipamentoId: null as string | null,
    valor: null as number | null
  };

  ngOnInit() {
    this.route.queryParams?.subscribe(params => {
      if (params['mode'] === 'print') {
        this.isPrintMode.set(true);
      }
    });
    this.loadLookups();
    const id = this.route.snapshot?.paramMap?.get('id');
    if (id) {
      this.loadDesconto(id);
    } else {
      this.error.set('ID do Desconto não informado na URL.');
      this.loading.set(false);
    }
    this.route.paramMap?.subscribe(params => {
      const pId = params.get('id');
      if (pId && pId !== id) {
        this.loadDesconto(pId);
      }
    });
  }

  private loadLookups() {
    this.colaboradoresCarregados = true;
    this.descontosService.listarTiposEquipamento({ page: 0, size: 200, ativo: true }).subscribe({
      next: (res) => this.categorias.set(res.content)
    });
    this.descontosService.listarMotivos({ page: 0, size: 200, ativo: true }).subscribe({
      next: (res) => this.motivos.set(res.content)
    });
  }

  protected loadDesconto(id: string) {
    this.loading.set(true);
    this.error.set(null);
    this.descontosService.obterDescontoPorId(id).subscribe({
      next: (res) => {
        this.desconto.set(res);
        this.buildEtapas(res);
        this.resolverResponsaveis();

        this.editForm.patchValue({
          colaboradorId: res.colaboradorId,
          motivoId: res.motivo?.id,
          dataOcorrencia: res.dataOcorrencia ? new Date(res.dataOcorrencia) : null,
          quantidadeParcelas: res.quantidadeParcelas,
          observacoes: res.observacoes ?? ''
        }, { emitEvent: false });

        this.formColaboradorId.set(res.colaboradorId ?? null);
        this.formMotivoId.set(res.motivo?.id ?? null);
        this.formParcelas.set(res.quantidadeParcelas || 1);
        this.initialFormSnapshot = this.snapshotForm();
        this.hasChanges.set(false);

        if (res.status !== 'SOLICITADO') {
          this.editForm.disable({ emitEvent: false });
        } else {
          this.editForm.enable({ emitEvent: false });
        }

        this.loading.set(false);
        this.saving.set(false);

        if (this.isPrintMode()) {
          setTimeout(() => {
            window.print();
          }, 500);
        }
        this.atenderAcaoDoEmail(res);
      },
      error: (err) => {
        this.error.set(getErrorMessage(err));
        this.loading.set(false);
        this.saving.set(false);
      }
    });
  }

  protected searchColaboradores(query: string): void {
    this.colaboradorLookup.search(query);
  }

  protected loadMoreColaboradores(): void { this.colaboradorLookup.loadNext(); }

  protected retryColaboradores(): void { this.colaboradorLookup.retry(); }

  /**
   * Compara contra o snapshot tirado no carregamento em vez de re-derivar do DTO,
   * evitando falsos positivos de fuso horário na data da ocorrência.
   */
  private checkIfChanged(): boolean {
    if (!this.initialFormSnapshot) return false;
    return JSON.stringify(this.snapshotForm()) !== JSON.stringify(this.initialFormSnapshot);
  }

  private snapshotForm() {
    const v = this.editForm.getRawValue();
    return {
      colaboradorId: v.colaboradorId ?? null,
      motivoId: v.motivoId ?? null,
      dataOcorrencia: v.dataOcorrencia instanceof Date ? this.toLocalIsoDate(v.dataOcorrencia) : (v.dataOcorrencia ?? null),
      quantidadeParcelas: v.quantidadeParcelas ?? null,
      observacoes: (v.observacoes || '').trim()
    };
  }

  /** Data local em yyyy-MM-dd, evitando o deslocamento de fuso do toISOString(). */
  private toLocalIsoDate(date: Date): string {
    const offset = date.getTimezoneOffset() * 60000;
    return new Date(date.getTime() - offset).toISOString().substring(0, 10);
  }

  private buildEtapas(d: DescontoResponse) {
    const arr: EtapaDesconto[] = [];

    // 1. Solicitado
    arr.push({
      nome: 'Solicitado',
      status: 'CONCLUIDO',
      dataConclusao: d.dataCriacao,
      responsavel: d.usuarioSolicitanteId,
      valor: d.valorSugerido,
      icon: 'pi pi-file-edit'
    });

    // 2. Validado / Rejeitado
    if (d.status === 'REJEITADO') {
      arr.push({
        nome: 'Rejeitado',
        status: 'REJEITADO',
        dataConclusao: d.dataRejeicao || d.dataUltimaAlteracao,
        responsavel: d.usuarioRejeicaoId || d.usuarioUltimaAlteracao,
        icon: 'pi pi-times-circle'
      });
      arr.push({
        nome: 'Baixado',
        status: 'PENDENTE',
        icon: 'pi pi-wallet'
      });
    } else {
      arr.push({
        nome: 'Validado',
        status: d.status === 'VALIDADO' || d.status === 'BAIXADO' ? 'CONCLUIDO' : (d.status === 'SOLICITADO' ? 'EM_ANDAMENTO' : 'PENDENTE'),
        dataConclusao: d.dataValidacao,
        responsavel: d.usuarioValidadorId,
        valor: d.valorValidado,
        icon: 'pi pi-check-square'
      });

      // 3. Baixado
      arr.push({
        nome: 'Baixado',
        status: d.status === 'BAIXADO' ? 'CONCLUIDO' : (d.status === 'VALIDADO' ? 'EM_ANDAMENTO' : 'PENDENTE'),
        dataConclusao: d.dataBaixa,
        responsavel: d.usuarioBaixaId,
        valor: d.valorBaixado,
        icon: 'pi pi-wallet'
      });
    }

    this.etapas.set(arr);
  }

  // ---------------------------------------------------------------
  // Responsáveis pelas etapas
  //
  // O backend devolve apenas o id do *usuário* (IAM) de cada transição.
  // Resolvemos o nome primeiro na lista de colaboradores já carregada
  // (via `idUsuario`, sem custo de rede) e só caímos no IAM para os ids
  // que sobrarem — colaborador inativo ou fora das 200 primeiras linhas.
  // ---------------------------------------------------------------

  private static readonly UUID_REGEX =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

  private podeConsultarUsuarios(): boolean {
    return this.auth.currentUser()?.permissoes?.includes('iam:usuario:read') ?? false;
  }

  private resolverResponsaveis() {
    const d = this.desconto();
    // Espera os colaboradores para não gastar requisições ao IAM à toa.
    if (!d || !this.colaboradoresCarregados) return;

    const ids = [
      d.usuarioSolicitanteId,
      d.usuarioValidadorId,
      d.usuarioBaixaId,
      d.usuarioRejeicaoId
    ].filter((id): id is string => !!id && RelatorioDescontoComponent.UUID_REGEX.test(id));

    for (const id of new Set(ids)) {
      if (this.responsaveis()[id]) continue;

      const colaborador = this.colaboradorDetalhes().find(c => c.idUsuario === id);
      if (colaborador) {
        this.responsaveis.update(m => ({ ...m, [id]: colaborador.nomeCompleto }));
        continue;
      }

      if (this.responsaveisConsultados.has(id) || !this.podeConsultarUsuarios()) continue;
      this.responsaveisConsultados.add(id);

      this.usuariosService.buscarUsuarioPorId(id).subscribe({
        next: (usuario) => {
          const nome =
            this.colaboradorDetalhes().find(c => c.id === usuario.idColaborador)?.nomeCompleto ||
            usuario.login;
          if (nome) this.responsaveis.update(m => ({ ...m, [id]: nome }));
        },
        // Usuário removido ou fora do alcance: o template apenas omite a linha.
        error: () => {}
      });
    }
  }

  /**
   * Nome de exibição do responsável por uma etapa.
   * Retorna null quando não há nome legível — nunca expõe um UUID cru na tela.
   */
  protected getResponsavelNome(responsavel?: string): string | null {
    if (!responsavel) return null;
    const nome = this.responsaveis()[responsavel];
    if (nome) return nome;
    // `usuarioUltimaAlteracao` chega como login, não como UUID — pode ir direto pra tela.
    return RelatorioDescontoComponent.UUID_REGEX.test(responsavel) ? null : responsavel;
  }

  protected getColaboradorName(id?: string): string {
    if (!id) return '-';
    return this.colaboradorDetalhes().find(c => c.id === id)?.nomeCompleto ||
      this.colaboradores().find(c => c.value === id)?.label || id;
  }

  protected getStatusClass(status?: string): string {
    switch (status) {
      case 'SOLICITADO': return 'bg-orange-100 dark:bg-orange-950/60 text-orange-700 dark:text-orange-300 border-orange-200 dark:border-orange-800/60';
      case 'VALIDADO': return 'bg-blue-100 dark:bg-blue-950/60 text-blue-700 dark:text-blue-300 border-blue-200 dark:border-blue-800/60';
      case 'BAIXADO': return 'bg-green-100 dark:bg-green-950/60 text-green-700 dark:text-green-300 border-green-200 dark:border-green-800/60';
      case 'REJEITADO': return 'bg-red-100 dark:bg-red-950/60 text-red-700 dark:text-red-300 border-red-200 dark:border-red-800/60';
      default: return 'bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border-slate-200 dark:border-slate-700';
    }
  }

  protected getStatusLabel(status?: string): string {
    return status || '-';
  }

  // ---------------------------------------------------------------
  // Ações de Transição
  // ---------------------------------------------------------------

  protected abrirValidarDialog() {
    const d = this.desconto();
    if (!d) return;
    if (!d.itens?.length) {
      this.messageService.add({ severity: 'warn', summary: 'Aviso', detail: 'Não é possível validar um desconto sem itens.' });
      return;
    }
    if (this.hasChanges()) {
      this.messageService.add({ severity: 'warn', summary: 'Alterações pendentes', detail: 'Salve ou descarte as alterações antes de validar.' });
      return;
    }
    // UX (guia §4.2): pré-preenche com o valor da etapa anterior
    this.valorValidadoInput.set(d.valorSugerido ?? 0);
    this.showValidarDialog.set(true);
  }

  protected confirmarValidacao() {
    const d = this.desconto();
    const valor = this.valorValidadoInput();
    if (!d || valor === null || valor === undefined || valor < 0) return;

    this.validando.set(true);
    this.descontosService.validarDesconto(d.id, { valorValidado: valor }).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Validado', detail: 'Desconto validado. Os itens estão agora bloqueados para edição.' });
        this.validando.set(false);
        this.showValidarDialog.set(false);
        this.loadDesconto(d.id);
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
        this.validando.set(false);
      }
    });
  }

  protected formatCpf(cpf?: string): string {
    if (!cpf) return '-';
    const clean = cpf.replace(/\D/g, '');
    if (clean.length === 11) {
      return `${clean.slice(0, 3)}.${clean.slice(3, 6)}.${clean.slice(6, 9)}-${clean.slice(9, 11)}`;
    }
    return cpf;
  }

  protected isBaixaValida = computed(() => {
    const valor = this.valorBaixadoInput();
    return valor !== null && valor !== undefined && valor >= 0;
  });

  protected abrirBaixarDialog() {
    const d = this.desconto();
    if (!d) return;
    // Pré-preenche com o valor validado (etapa anterior)
    this.valorBaixadoInput.set(d.valorValidado ?? d.valorSugerido ?? 0);
    this.showBaixarDialog.set(true);
  }

  protected confirmarBaixa() {
    const d = this.desconto();
    const valor = this.valorBaixadoInput();
    if (!d || !this.isBaixaValida()) return;

    this.baixando.set(true);

    this.descontosService.baixarDesconto(d.id, {
      valorBaixado: valor!
    }).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Baixa registrada',
          detail: 'Desconto baixado com sucesso. O registro foi selado e não pode mais ser alterado.'
        });
        this.baixando.set(false);
        this.showBaixarDialog.set(false);
        this.loadDesconto(d.id);
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
        this.baixando.set(false);
      }
    });
  }

  /**
   * Os botões do e-mail abrem o termo com `?acao=validar|rejeitar|baixar`: o diálogo
   * da etapa já vem aberto para confirmar. Se a etapa já passou, a pessoa fica sabendo
   * em vez de procurar um botão que não existe mais. O parâmetro sai da URL em
   * seguida, para que recarregar a página não reabra o diálogo.
   */
  private atenderAcaoDoEmail(desconto: DescontoResponse) {
    const acao = this.route.snapshot?.queryParamMap?.get('acao');
    if (!acao) return;
    this.router.navigate([], { relativeTo: this.route, queryParams: { acao: null }, queryParamsHandling: 'merge', replaceUrl: true });

    const etapa: Record<string, { status: string; abrir: () => void }> = {
      validar: { status: 'SOLICITADO', abrir: () => this.abrirValidarDialog() },
      rejeitar: { status: 'SOLICITADO', abrir: () => this.abrirRejeitarDialog() },
      baixar: { status: 'VALIDADO', abrir: () => this.abrirBaixarDialog() }
    };
    const pedida = etapa[acao];
    if (!pedida) return;
    if (desconto.status !== pedida.status) {
      this.messageService.add({
        severity: 'info',
        summary: 'Nada a fazer nesta etapa',
        detail: `Este desconto já está ${this.rotuloStatusEmail(desconto.status)}.`
      });
      return;
    }
    pedida.abrir();
  }

  private rotuloStatusEmail(status: string): string {
    return ({ SOLICITADO: 'aguardando validação', VALIDADO: 'validado', BAIXADO: 'baixado', REJEITADO: 'rejeitado' } as Record<string, string>)[status] ?? status.toLowerCase();
  }

  protected abrirRejeitarDialog() {
    this.motivoRejeicao.set('');
    this.showRejeitarDialog.set(true);
  }

  protected cancelarRejeicao() {
    this.showRejeitarDialog.set(false);
  }

  protected confirmarRejeicao() {
    const d = this.desconto();
    if (!d || !this.motivoRejeicao().trim()) return;

    this.rejeitando.set(true);
    this.descontosService.rejeitarDesconto(d.id, { motivoRejeicao: this.motivoRejeicao().trim() }).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Rejeitado', detail: 'Desconto rejeitado com sucesso.' });
        this.rejeitando.set(false);
        this.showRejeitarDialog.set(false);
        this.loadDesconto(d.id);
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
        this.rejeitando.set(false);
      }
    });
  }

  // Gestão de Itens
  protected addNewItem() {
    const d = this.desconto();
    if (!d || !this.newItem.tipoEquipamentoId || !this.newItem.valor || !this.newItem.descricaoMaterial) return;

    const payload: DescontoItemRequest = {
      descricaoMaterial: this.newItem.descricaoMaterial,
      tipoEquipamentoId: this.newItem.tipoEquipamentoId,
      valor: this.newItem.valor
    };

    this.saving.set(true);
    this.descontosService.adicionarItem(d.id, payload).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Adicionado', detail: 'Item inserido. O valor sugerido foi recalculado.' });
        this.showItemForm.set(false);
        this.newItem = { descricaoMaterial: '', tipoEquipamentoId: null, valor: null };
        this.loadDesconto(d.id);
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
        this.saving.set(false);
      }
    });
  }

  protected editItem(item: any) {
    this.editingItemId.set(item.id);
    this.inlineEditDraft = {
      id: item.id,
      descricaoMaterial: item.descricaoMaterial,
      tipoEquipamentoId: item.tipoEquipamento?.id || item.tipoEquipamentoId,
      valor: item.valor
    };
  }

  protected saveEditItem() {
    const d = this.desconto();
    if (!d || !this.inlineEditDraft.id || !this.inlineEditDraft.tipoEquipamentoId || !this.inlineEditDraft.valor || !this.inlineEditDraft.descricaoMaterial) return;

    const payload: DescontoItemRequest = {
      descricaoMaterial: this.inlineEditDraft.descricaoMaterial,
      tipoEquipamentoId: this.inlineEditDraft.tipoEquipamentoId!,
      valor: this.inlineEditDraft.valor!
    };

    this.saving.set(true);
    this.descontosService.atualizarItem(d.id, this.inlineEditDraft.id, payload).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Atualizado', detail: 'Item atualizado com sucesso. O valor sugerido foi recalculado.' });
        this.editingItemId.set(null);
        this.loadDesconto(d.id);
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
        this.saving.set(false);
      }
    });
  }

  protected cancelEditItem() {
    this.editingItemId.set(null);
  }

  protected removeItem(itemId: string) {
    const d = this.desconto();
    if (!d) return;

    this.confirmationService.confirm({
      message: 'Tem certeza que deseja remover este item? O valor sugerido será recalculado.',
      header: 'Confirmação',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.saving.set(true);
        this.descontosService.removerItem(d.id, itemId).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Removido', detail: 'Item deletado com sucesso.' });
            this.loadDesconto(d.id);
          },
          error: (err) => {
            this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
            this.saving.set(false);
          }
        });
      }
    });
  }

  // Gestão de Anexos
  protected onFileSelect(event: any) {
    const d = this.desconto();
    if (!d) return;

    const file: File = event.target.files[0];
    if (file) {
      if (file.size > MAX_DESCONTO_ANEXO_SIZE_BYTES) {
        this.messageService.add({ severity: 'warn', summary: 'Atenção', detail: 'O arquivo excede o limite de 15MB.' });
        event.target.value = '';
        return;
      }
      this.saving.set(true);
      this.descontosService.enviarAnexo(d.id, file).subscribe({
        next: () => {
          this.messageService.add({ severity: 'success', summary: 'Upload', detail: 'Anexo salvo com sucesso.' });
          this.loadDesconto(d.id);
          event.target.value = ''; // reseta input
        },
        error: (err) => {
          this.messageService.add({ severity: 'error', summary: 'Erro no Upload', detail: getErrorMessage(err) });
          this.saving.set(false);
          event.target.value = '';
        }
      });
    }
  }

  protected removerAnexo(anexoId: string) {
    const d = this.desconto();
    if (!d) return;

    this.confirmationService.confirm({
      message: 'Tem certeza que deseja remover este anexo?',
      header: 'Confirmação',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.saving.set(true);
        this.descontosService.removerAnexo(d.id, anexoId).subscribe({
          next: () => {
            this.messageService.add({ severity: 'success', summary: 'Removido', detail: 'Anexo deletado.' });
            this.loadDesconto(d.id);
          },
          error: (err) => {
            this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
            this.saving.set(false);
          }
        });
      }
    });
  }

  protected baixarAnexo(anexo: DescontoAnexoResponse) {
    const descontoId = this.desconto()?.id;
    if (!descontoId) {
      this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Desconto não encontrado.' });
      return;
    }

    this.messageService.add({ severity: 'info', summary: 'Download', detail: 'Iniciando download...' });
    this.descontosService.baixarAnexoArquivo(descontoId, anexo.id).subscribe({
      next: (blob) => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = anexo.nomeArquivo;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        window.URL.revokeObjectURL(url);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao baixar o anexo.' });
      }
    });
  }

  protected visualizarAnexo(anexo: DescontoAnexoResponse) {
    const descontoId = this.desconto()?.id;
    if (!descontoId) return;

    const categoria = getDescontoAnexoCategoria(anexo.nomeArquivo, anexo.contentType);
    this.previewNomeArquivo.set(anexo.nomeArquivo);
    this.previewTipo.set(categoria);
    this.loadingPreview.set(true);
    this.showPreviewModal.set(true);

    this.descontosService.baixarAnexoArquivo(descontoId, anexo.id).subscribe({
      next: (blob) => {
        const mimeType = inferMimeType(anexo.nomeArquivo, anexo.contentType || blob.type);
        const typedBlob = new Blob([blob], { type: mimeType });
        const objectUrl = window.URL.createObjectURL(typedBlob);
        this.previewRawUrl.set(objectUrl);
        this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
        this.loadingPreview.set(false);
      },
      error: () => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: 'Falha ao carregar a pré-visualização do anexo.' });
        this.loadingPreview.set(false);
        this.showPreviewModal.set(false);
      }
    });
  }

  protected getAnexoCategoria(nomeArquivo?: string, contentType?: string): DescontoAnexoCategoria {
    return getDescontoAnexoCategoria(nomeArquivo, contentType);
  }

  protected getAnexoIcon(nomeArquivo?: string, contentType?: string): string {
    return getAnexoIcon(getDescontoAnexoCategoria(nomeArquivo, contentType));
  }

  protected getAnexoLabel(nomeArquivo?: string, contentType?: string): string {
    return getAnexoLabel(getDescontoAnexoCategoria(nomeArquivo, contentType));
  }

  protected getAnexoContainerClass(nomeArquivo?: string, contentType?: string): string {
    return getAnexoContainerClass(getDescontoAnexoCategoria(nomeArquivo, contentType));
  }

  protected fecharPreviewModal() {
    this.showPreviewModal.set(false);
    const raw = this.previewRawUrl();
    if (raw) {
      window.URL.revokeObjectURL(raw);
      this.previewRawUrl.set(null);
    }
    this.previewUrl.set(null);
  }

  protected abrirPreviewNovaAba() {
    const raw = this.previewRawUrl();
    if (raw) {
      window.open(raw, '_blank');
    }
  }

  protected salvarAlteracoes() {
    const d = this.desconto();
    if (!d || this.editForm.invalid || !this.hasChanges()) return;

    this.saving.set(true);
    const formValue = this.editForm.getRawValue();

    const payload: DescontoRequest = {
      colaboradorId: formValue.colaboradorId,
      motivoId: formValue.motivoId,
      quantidadeParcelas: formValue.quantidadeParcelas,
      dataOcorrencia: formValue.dataOcorrencia instanceof Date
        ? formValue.dataOcorrencia.toISOString()
        : formValue.dataOcorrencia
    };

    if (formValue.observacoes && formValue.observacoes.trim() !== '') {
      payload.observacoes = formValue.observacoes.trim();
    }

    this.descontosService.atualizarDesconto(d.id, payload).subscribe({
      next: () => {
        this.messageService.add({ severity: 'success', summary: 'Sucesso', detail: 'Alterações salvas.' });
        this.loadDesconto(d.id);
      },
      error: (err) => {
        this.messageService.add({ severity: 'error', summary: 'Erro', detail: getErrorMessage(err) });
        this.saving.set(false);
      }
    });
  }

  protected descartarAlteracoes() {
    const d = this.desconto();
    if (!d) return;
    this.editForm.patchValue({
      colaboradorId: d.colaboradorId,
      motivoId: d.motivo?.id,
      dataOcorrencia: d.dataOcorrencia ? new Date(d.dataOcorrencia) : null,
      quantidadeParcelas: d.quantidadeParcelas,
      observacoes: d.observacoes ?? ''
    }, { emitEvent: false });
    this.formColaboradorId.set(d.colaboradorId ?? null);
    this.formMotivoId.set(d.motivo?.id ?? null);
    this.formParcelas.set(d.quantidadeParcelas || 1);
    this.hasChanges.set(false);
  }

  protected formatSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  /** Aberto em nova aba pela lista: fecha a aba; senão volta pelo histórico. */
  protected voltar(): void {
    this.navegacao.back(['/dp/descontos/lancamento']);
  }

  protected print() {
    window.print();
  }
}
