import { DateMaskDirective } from '../../../../shared/directives/date-mask.directive';
import { Component, DestroyRef, OnInit, OnDestroy, signal, inject, output, ViewChild, effect, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { SelectModule } from 'primeng/select';
import { InputNumberModule } from 'primeng/inputnumber';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { DividerModule } from 'primeng/divider';
import { MessageService } from 'primeng/api';
import { InputTextModule } from 'primeng/inputtext';
import { TextareaModule } from 'primeng/textarea';
import { DatePickerModule } from 'primeng/datepicker';
import { firstValueFrom } from 'rxjs';
import { toSignal } from '@angular/core/rxjs-interop';

import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { DialogModule } from 'primeng/dialog';
import { TooltipModule } from 'primeng/tooltip';
import { ProgressSpinnerModule } from 'primeng/progressspinner';

import { HelpDialogComponent } from '../../../../shared/components/help-dialog/help-dialog.component';
import { RhDescontosService } from '../../../../core/services/rh-descontos.service';
import { RhService } from '../../../../core/services/rh.service';
import {
  DescontoRequest,
  DescontoItemRequest,
  MotivoDescontoResponse,
  TipoEquipamentoResponse,
  DescontoAnexoCategoria,
  getDescontoAnexoCategoria,
  getAnexoIcon,
  getAnexoLabel,
  getAnexoContainerClass,
  DESCONTO_ANEXO_ACCEPT,
  MAX_DESCONTO_ANEXO_SIZE_BYTES
} from '../../../../core/models/desconto.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import { novaChaveIdempotencia } from '../../../../core/utils/idempotencia.util';
import { ColaboradorResponse } from '../../../../core/models/rh.models';
import { ImageModule } from 'primeng/image';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import { RemoteLookup } from '../../../../core/lookup/store/remote-lookup';
import { CesAutocompleteComponent } from '@ces/ui/forms';
import { NavigationHistoryService, createFormDraft } from '../../../../core/navigation';

interface EtapaDesconto {
  nome: string;
  status: 'CONCLUIDO' | 'EM_ANDAMENTO' | 'PENDENTE';
  icon: string;
}

interface AnexoPreview {
  file: File;
  previewUrl?: string;
  /** Estável desde a seleção do arquivo: identifica ESTE anexo em todas as tentativas. */
  chaveIdempotencia: string;
}

@Component({
  selector: 'app-desconto-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    ButtonModule,
    SelectModule,
    InputNumberModule,
    InputTextModule,
    TextareaModule,
    TableModule,
    TagModule,
    DividerModule,
    ImageModule,
    DatePickerModule,
    DialogModule,
    TooltipModule,
    ProgressSpinnerModule,
    HelpDialogComponent,
    DateMaskDirective,
    CesAutocompleteComponent
  ],
  templateUrl: './desconto-form.component.html',
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
export class DescontoFormComponent implements OnInit, OnDestroy {
  @ViewChild('helpDialog') helpDialog!: HelpDialogComponent;

  private descontosService = inject(RhDescontosService);
  private rhService = inject(RhService);
  private fb = inject(FormBuilder);
  private messageService = inject(MessageService);
  private router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private sanitizer = inject(DomSanitizer);
  private destroyRef = inject(DestroyRef);
  private readonly lookupStore = inject(LookupStore);
  private readonly colaboradorLookup = new RemoteLookup<string>({
    loader: ({ query, cursor }) =>
      this.lookupStore.searchCollaborators({ query, cursor, limit: 50 }),
    selectedValues: () => {
      const id = this.form?.get('colaboradorId')?.value;
      return typeof id === 'string' && id ? [id] : [];
    },
    destroyRef: this.destroyRef
  });

  onSuccess = output<void>();
  onCancel = output<void>();

  protected saving = signal(false);

  // Preview de Anexos
  protected anexoAccept = DESCONTO_ANEXO_ACCEPT;
  protected showPreviewModal = signal(false);
  protected previewUrl = signal<SafeResourceUrl | null>(null);
  protected previewRawUrl = signal<string | null>(null);
  protected previewNomeArquivo = signal<string>('');
  protected previewTipo = signal<DescontoAnexoCategoria>('other');

  // ── Progresso do salvamento ────────────────────────────────────────────────────
  // Este bloco é o que torna a retentativa segura. Sem ele, cada clique em "Salvar"
  // recomeçava do zero e abria um novo desconto — a origem dos três lançamentos
  // duplicados de 06/08/2026.

  /** Id da raiz já criada nesta sessão do formulário. Preenchido, o passo 1 é pulado. */
  protected descontoCriadoId = signal<string | null>(null);

  /**
   * Chave da SOLICITAÇÃO, estável enquanto o formulário estiver aberto.
   *
   * Não é um signal porque não muda: é justamente a sua imutabilidade que faz o backend
   * reconhecer a segunda tentativa como a mesma intenção da primeira.
   */
  private readonly chaveSolicitacao = novaChaveIdempotencia();

  /** Chaves dos itens/anexos que o servidor já confirmou — não são reenviados. */
  protected itensEnviados = signal<Set<string>>(new Set());
  protected anexosEnviados = signal<Set<string>>(new Set());

  protected readonly colaboradores = this.colaboradorLookup.items;
  protected readonly colaboradoresLoading = this.colaboradorLookup.loading;
  protected readonly colaboradoresStatus = this.colaboradorLookup.status;
  private readonly colaboradorDetalhe = signal<ColaboradorResponse | null>(null);
  protected motivos = signal<MotivoDescontoResponse[]>([]);
  protected categorias = signal<TipoEquipamentoResponse[]>([]);

  protected items = signal<
    (DescontoItemRequest & { id?: string; categoriaNome: string; chaveIdempotencia: string })[]
  >([]);
  protected editingItemIndex = signal<number | null>(null);

  protected etapas = signal<EtapaDesconto[]>([
    { nome: 'Solicitado', status: 'EM_ANDAMENTO', icon: 'pi pi-file-edit' },
    { nome: 'Validado', status: 'PENDENTE', icon: 'pi pi-check-square' },
    { nome: 'Baixado', status: 'PENDENTE', icon: 'pi pi-wallet' }
  ]);

  protected form: FormGroup = this.fb.group({
    colaboradorId: [null, Validators.required],
    motivoId: [null, Validators.required],
    dataOcorrencia: [null, Validators.required],
    quantidadeParcelas: [1, [Validators.required, Validators.min(1)]],
    observacoes: ['']
  });

  protected itemForm: FormGroup = this.fb.group({
    descricaoMaterial: ['', Validators.required],
    tipoEquipamentoId: [null, Validators.required],
    valor: [null, [Validators.required, Validators.min(0.01)]]
  });

  protected inlineEditDraft = {
    descricaoMaterial: '',
    tipoEquipamentoId: null as string | null,
    valor: null as number | null
  };

  protected parcelas = toSignal(this.form.controls['quantidadeParcelas'].valueChanges, { initialValue: 1 });
  protected motivoSelecionado = toSignal(this.form.controls['motivoId'].valueChanges);
  protected colaboradorSelecionadoId = toSignal(this.form.controls['colaboradorId'].valueChanges);

  protected colaboradorSelecionado = computed(() => {
    const id = this.colaboradorSelecionadoId();
    const colaborador = this.colaboradorDetalhe();
    return colaborador?.id === id ? colaborador : undefined;
  });

  protected totalGeral = computed(() => this.items().reduce((acc, item) => acc + (item.valor || 0), 0));
  protected totalPorMes = computed(() => {
    const p = this.parcelas() || 1;
    return this.totalGeral() / p;
  });

  protected anexos = signal<AnexoPreview[]>([]);

  constructor() {
    effect(() => {
      const id = this.colaboradorSelecionadoId();
      if (!id || this.colaboradorDetalhe()?.id === id) return;
      this.rhService.buscarColaboradorPorId(id).subscribe({
        next: (colaborador) => {
          if (this.colaboradorSelecionadoId() !== id) return;
          this.colaboradorDetalhe.set(colaborador);
          this.colaboradorLookup.seedSelected({
            value: colaborador.id,
            label: colaborador.nomeCompleto
          });
        },
        error: () => {
          if (this.colaboradorSelecionadoId() === id) this.colaboradorDetalhe.set(null);
        }
      });
    });

    effect(() => {
      // Regra de Validação de Teto de Parcelas
      const motivoId = this.motivoSelecionado();
      const motivo = this.motivos().find(m => m.id === motivoId);

      const parcelasCtrl = this.form.controls['quantidadeParcelas'];
      if (motivo && parcelasCtrl) {
        parcelasCtrl.setValidators([Validators.required, Validators.min(1), Validators.max(motivo.tetoParcelas)]);
        if (parcelasCtrl.value > motivo.tetoParcelas) {
          parcelasCtrl.setValue(motivo.tetoParcelas);
        }
        parcelasCtrl.updateValueAndValidity();
      }
    });
  }

  /**
   * Rascunho só em memória (ver docs/state-restoration/GUIA.md): colaborador e itens são
   * dados pessoais/de folha. Anexos (arquivos) não entram. Descartado ao salvar e no Cancelar.
   */
  private readonly rascunho = createFormDraft({
    route: '/dp/descontos/novo',
    persist: false,
    read: () => ({ form: this.form.getRawValue(), items: this.items() }),
    write: (valor) => {
      this.form.patchValue(valor.form);
      this.items.set(valor.items ?? []);
    },
  });

  ngOnInit() {
    this.rascunho.restore();
    this.loadLookups();
  }

  ngOnDestroy() {
    // Release object URLs to avoid memory leaks
    for (const anexo of this.anexos()) {
      if (anexo.previewUrl) URL.revokeObjectURL(anexo.previewUrl);
    }
  }

  private loadLookups() {
    this.descontosService.listarMotivos({ page: 0, size: 200, ativo: true }).subscribe({
      next: (res) => this.motivos.set(res.content)
    });
    this.descontosService.listarTiposEquipamento({ page: 0, size: 200, ativo: true }).subscribe({
      next: (res) => this.categorias.set(res.content)
    });
  }

  protected searchColaboradores(query: string): void {
    this.colaboradorLookup.search(query);
  }

  protected loadMoreColaboradores(): void { this.colaboradorLookup.loadNext(); }

  protected retryColaboradores(): void { this.colaboradorLookup.retry(); }

  onHelp() {
    this.helpDialog.show();
  }

  /** Aberto em nova aba pela lista: fecha a aba; senão volta pelo histórico. */
  protected cancel(): void {
    this.rascunho.clear();
    this.navegacao.back(['/dp/descontos/lancamento']);
  }

  protected addItem() {
    if (this.itemForm.invalid) return;

    const values = this.itemForm.getRawValue();
    const categoria = this.categorias().find(c => c.id === values.tipoEquipamentoId);

    this.items.update(prev => [...prev, {
      descricaoMaterial: values.descricaoMaterial,
      tipoEquipamentoId: values.tipoEquipamentoId,
      valor: values.valor,
      categoriaNome: categoria ? categoria.descricao : 'Desconhecido',
      chaveIdempotencia: novaChaveIdempotencia()
    }]);

    this.itemForm.reset();
  }

  protected editItem(index: number) {
    const item = this.items()[index];
    this.editingItemIndex.set(index);
    this.inlineEditDraft = {
      descricaoMaterial: item.descricaoMaterial,
      tipoEquipamentoId: item.tipoEquipamentoId,
      valor: item.valor
    };
  }

  protected saveEditItem() {
    const editIndex = this.editingItemIndex();
    if (editIndex === null || !this.inlineEditDraft.descricaoMaterial || !this.inlineEditDraft.tipoEquipamentoId || !this.inlineEditDraft.valor) return;

    const categoria = this.categorias().find(c => c.id === this.inlineEditDraft.tipoEquipamentoId);

    this.items.update(prev => {
      const copy = [...prev];
      copy[editIndex] = {
        ...copy[editIndex],
        descricaoMaterial: this.inlineEditDraft.descricaoMaterial,
        tipoEquipamentoId: this.inlineEditDraft.tipoEquipamentoId!,
        valor: this.inlineEditDraft.valor!,
        categoriaNome: categoria ? categoria.descricao : 'Desconhecido'
      };
      return copy;
    });
    this.editingItemIndex.set(null);
  }

  protected cancelEditItem() {
    this.editingItemIndex.set(null);
  }

  protected removeItem(index: number) {
    this.items.update(prev => {
      const copy = [...prev];
      copy.splice(index, 1);
      return copy;
    });
  }

  protected onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const files = Array.from(input.files);
      const validFiles = files.filter(f => f.size <= MAX_DESCONTO_ANEXO_SIZE_BYTES);

      if (validFiles.length < files.length) {
        this.messageService.add({ severity: 'warn', summary: 'Atenção', detail: 'Alguns arquivos foram ignorados pois excedem o limite de 15MB.' });
      }

      const novosAnexos: AnexoPreview[] = validFiles.map(file => {
        return {
          file,
          previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined,
          chaveIdempotencia: novaChaveIdempotencia()
        };
      });

      this.anexos.update(prev => [...prev, ...novosAnexos]);
      input.value = ''; // Reset input so same file can be selected again if removed
    }
  }

  protected removeAnexo(index: number) {
    this.anexos.update(prev => {
      const copy = [...prev];
      const removed = copy.splice(index, 1)[0];
      if (removed.previewUrl) URL.revokeObjectURL(removed.previewUrl);
      return copy;
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

  protected visualizarAnexo(anexo: AnexoPreview) {
    const categoria = getDescontoAnexoCategoria(anexo.file.name, anexo.file.type);
    this.previewNomeArquivo.set(anexo.file.name);
    this.previewTipo.set(categoria);

    const rawUrl = URL.createObjectURL(anexo.file);
    this.previewRawUrl.set(rawUrl);
    this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(rawUrl));
    this.showPreviewModal.set(true);
  }

  protected getAnexoCategoria(nomeArquivo: string, contentType?: string): DescontoAnexoCategoria {
    return getDescontoAnexoCategoria(nomeArquivo, contentType);
  }

  protected getAnexoIcon(nomeArquivo: string, contentType?: string): string {
    return getAnexoIcon(getDescontoAnexoCategoria(nomeArquivo, contentType));
  }

  protected getAnexoLabel(nomeArquivo: string, contentType?: string): string {
    return getAnexoLabel(getDescontoAnexoCategoria(nomeArquivo, contentType));
  }

  protected getAnexoContainerClass(nomeArquivo: string, contentType?: string): string {
    return getAnexoContainerClass(getDescontoAnexoCategoria(nomeArquivo, contentType));
  }

  protected fecharPreviewModal() {
    this.showPreviewModal.set(false);
    const raw = this.previewRawUrl();
    if (raw) {
      URL.revokeObjectURL(raw);
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

  protected formatSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  protected getColaboradorNome(id: string): string {
    return this.colaboradorDetalhe()?.id === id
      ? this.colaboradorDetalhe()!.nomeCompleto
      : this.colaboradores().find(c => c.value === id)?.label || id || '-';
  }

  protected getSelectedMotivoTeto(): number | string {
    const id = this.form.get('motivoId')?.value;
    return this.motivos().find(m => m.id === id)?.tetoParcelas ?? '-';
  }

  protected getMaxParcelas(): number {
    const teto = this.getSelectedMotivoTeto();
    return typeof teto === 'number' ? teto : 999;
  }

  protected getMotivoNome(id: string): string {
    return this.motivos().find(m => m.id === id)?.descricao || id || '-';
  }

  /**
   * Salva a solicitação em três passos, de forma RETOMÁVEL.
   *
   * <p>A versão anterior era uma cadeia única: qualquer falha em itens ou anexos abortava
   * tudo e <b>descartava o id do desconto já criado</b>. Ao apertar "Salvar" de novo — o
   * reflexo natural de quem vê um erro — o passo 1 rodava outra vez e abria um segundo
   * desconto. Em 06/08/2026 isso produziu três lançamentos do mesmo desconto.</p>
   *
   * <p>Agora cada passo concluído fica registrado no componente e a retentativa retoma de
   * onde parou. Além disso, cada requisição carrega uma chave de idempotência estável, de
   * modo que mesmo o passo cuja resposta se perdeu no caminho de volta não é reexecutado
   * pelo servidor. As duas defesas são independentes de propósito: a primeira evita o
   * reenvio, a segunda o torna inofensivo quando ele acontece assim mesmo.</p>
   */
  protected async onSave() {
    if (this.form.invalid || this.items().length === 0 || this.saving()) return;

    this.saving.set(true);
    // A partir daqui o progresso (e a retentativa) é do servidor, não do rascunho.
    this.rascunho.clear();
    try {
      const descontoId = await this.garantirDescontoCriado();
      await this.enviarItensPendentes(descontoId);
      await this.enviarAnexosPendentes(descontoId);

      this.messageService.add({
        severity: 'success',
        summary: 'Sucesso',
        detail: 'Solicitação de Desconto criada com anexos!'
      });
      this.router.navigate(['/dp/descontos/lancamento']);
    } catch (err) {
      // O progresso permanece nos signals: a próxima tentativa continua do ponto de parada
      // em vez de recomeçar. É por isso que a mensagem fala em "continuar", não em "refazer".
      this.messageService.add({
        severity: 'error',
        summary: 'Erro',
        detail: getErrorMessage(err)
      });
    } finally {
      this.saving.set(false);
    }
  }

  /** Passo 1 — cria a raiz apenas se ela ainda não existir nesta sessão do formulário. */
  private async garantirDescontoCriado(): Promise<string> {
    const jaCriado = this.descontoCriadoId();
    if (jaCriado) return jaCriado;

    const formValue = this.form.getRawValue();
    const request: DescontoRequest = {
      colaboradorId: formValue.colaboradorId,
      motivoId: formValue.motivoId,
      quantidadeParcelas: formValue.quantidadeParcelas,
      dataOcorrencia:
        formValue.dataOcorrencia instanceof Date
          ? formValue.dataOcorrencia.toISOString()
          : formValue.dataOcorrencia
    };

    if (formValue.observacoes && formValue.observacoes.trim() !== '') {
      request.observacoes = formValue.observacoes.trim();
    }

    const desconto = await firstValueFrom(
      this.descontosService.iniciarSolicitacao(request, this.chaveSolicitacao)
    );

    // `null` aqui significa "sem conexão, foi para a fila offline" — não "falhou". Seguir
    // para os itens sem o id não é possível, e insistir só enfileiraria mais uma cópia.
    if (!desconto) {
      throw new Error(
        'Sem conexão com o servidor. A solicitação foi guardada e será enviada automaticamente ' +
          'quando a rede voltar — não é preciso lançar de novo.'
      );
    }

    this.descontoCriadoId.set(desconto.id);
    return desconto.id;
  }

  /** Passo 2 — envia só os itens que ainda não foram confirmados pelo servidor. */
  private async enviarItensPendentes(descontoId: string): Promise<void> {
    for (const item of this.items()) {
      if (this.itensEnviados().has(item.chaveIdempotencia)) continue;

      const payload: DescontoItemRequest = {
        descricaoMaterial: item.descricaoMaterial,
        tipoEquipamentoId: item.tipoEquipamentoId,
        valor: item.valor
      };
      await firstValueFrom(
        this.descontosService.adicionarItem(descontoId, payload, item.chaveIdempotencia)
      );
      this.itensEnviados.update((enviados) => new Set(enviados).add(item.chaveIdempotencia));
    }
  }

  /** Passo 3 — mesma lógica dos itens, aplicada aos binários. */
  private async enviarAnexosPendentes(descontoId: string): Promise<void> {
    for (const anexo of this.anexos()) {
      if (this.anexosEnviados().has(anexo.chaveIdempotencia)) continue;

      await firstValueFrom(
        this.descontosService.enviarAnexo(descontoId, anexo.file, anexo.chaveIdempotencia)
      );
      this.anexosEnviados.update((enviados) => new Set(enviados).add(anexo.chaveIdempotencia));
    }
  }
}
