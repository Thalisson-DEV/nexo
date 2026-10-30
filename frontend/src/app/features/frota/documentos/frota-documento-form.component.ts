import { ChangeDetectionStrategy, Component, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import {
  CesButtonComponent,
  CesDatepickerComponent,
  CesFileInputComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesSelectComponent,
  CesSelectOption,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';
import { Observable, switchMap, of } from 'rxjs';
import {
  DocumentoFrota,
  DocumentoFrotaRequest,
  OpcoesFrota,
  TipoDocumentoFrota,
  deDataIso,
  paraDataIso
} from '../../../core/models/frota.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FrotaService } from '../../../core/services/frota.service';

/** Opção de titular do documento quando a tela não fixa um veículo ou implemento. */
export interface TitularDocumento {
  tipo: 'VEICULO' | 'IMPLEMENTO';
  id: string;
  rotulo: string;
}

const LIMITE_ARQUIVO = 15 * 1024 * 1024;

@Component({
  selector: 'app-frota-documento-form',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesDatepickerComponent,
    CesFileInputComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesSelectComponent,
    CesTextInputComponent,
    CesTextareaComponent
  ],
  template: `
    <ces-form-drawer
      [(visible)]="visivel"
      [title]="documento() ? 'Editar documento' : 'Novo documento'"
      [description]="descricao()"
      [dirty]="alterado()"
      [loading]="salvando()"
      size="md"
    >
      <ces-form-section title="Documento" icon="pi pi-file">
        @if (!documento() && !titularFixo()) {
          <ces-select label="Veículo ou implemento" [required]="true" [options]="opcoesTitular()" [(value)]="titular"
            [filter]="true" [error]="erro('titular')"></ces-select>
        }
        <div class="ces-form-grid">
          <ces-select label="Tipo" [required]="true" [options]="tipos()" [(value)]="tipo" [error]="erro('tipo')"></ces-select>
          <ces-text-input label="Número" [(value)]="numero" [maxlength]="80" [showOptional]="true"></ces-text-input>
        </div>
        <ces-text-input label="Descrição" [(value)]="descricaoDocumento" [maxlength]="150"
          [required]="tipo() === 'OUTRO'" [showOptional]="tipo() !== 'OUTRO'" [error]="erro('descricao')"></ces-text-input>
        <div class="ces-form-grid">
          <ces-datepicker label="Emissão" [(value)]="emissao" [clearable]="true" [showOptional]="true"></ces-datepicker>
          <ces-datepicker label="Vencimento" [(value)]="vencimento" [clearable]="true" [showOptional]="true"
            description="Gera avisos automáticos conforme as faixas configuradas." [error]="erro('vencimento')"></ces-datepicker>
        </div>
        <ces-textarea label="Observação" [(value)]="observacao" [rows]="2" [maxlength]="1000" [showOptional]="true"></ces-textarea>
      </ces-form-section>

      <ces-form-section title="Arquivo" icon="pi pi-paperclip">
        @if (documento()?.possuiArquivo) {
          <p class="ces-cell-muted">Arquivo atual: {{ documento()!.arquivoNome }}. Selecione outro para substituir.</p>
        }
        <ces-file-input label="Arquivo" description="PDF ou imagem (JPG, PNG, WebP), até 15 MB." accept="application/pdf,image/jpeg,image/png,image/webp"
          [maxBytes]="limiteArquivo" [(file)]="arquivo" [showOptional]="true"></ces-file-input>
      </ces-form-section>

      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="visivel.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="documento() ? 'Salvar documento' : 'Cadastrar documento'"
          [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>
  `
})
export class FrotaDocumentoFormComponent {
  private readonly frota = inject(FrotaService);
  private readonly notificacoes = inject(NotificationService);

  readonly visivel = model(false);
  readonly documento = input<DocumentoFrota | null>(null);
  /** Quando informado, o documento nasce para este veículo (ficha do veículo). */
  readonly idVeiculo = input<string | null>(null);
  /** Quando informado, o documento nasce para este implemento. */
  readonly idImplemento = input<string | null>(null);
  readonly titulares = input<TitularDocumento[]>([]);
  readonly opcoes = input<OpcoesFrota | null>(null);
  readonly salvo = output<DocumentoFrota>();

  protected readonly limiteArquivo = LIMITE_ARQUIVO;
  protected readonly titular = signal<string | null>(null);
  protected readonly tipo = signal<TipoDocumentoFrota | null>(null);
  protected readonly numero = signal('');
  protected readonly descricaoDocumento = signal('');
  protected readonly emissao = signal<Date | null>(null);
  protected readonly vencimento = signal<Date | null>(null);
  protected readonly observacao = signal('');
  protected readonly arquivo = signal<File | null>(null);
  protected readonly salvando = signal(false);
  protected readonly tentouSalvar = signal(false);
  private readonly snapshot = signal('');

  protected readonly titularFixo = computed(() => !!this.idVeiculo() || !!this.idImplemento());
  protected readonly descricao = computed(() =>
    this.documento()?.placa
      ? `Documento do veículo ${this.documento()!.placa}.`
      : this.documento()?.implemento
        ? `Documento do implemento ${this.documento()!.implemento}.`
        : 'Documento com vencimento e arquivo anexado.'
  );
  protected readonly tipos = computed<CesSelectOption<TipoDocumentoFrota>[]>(() =>
    (this.opcoes()?.tiposDocumento ?? []).map((o) => ({ label: o.rotulo, value: o.valor }))
  );
  protected readonly opcoesTitular = computed<CesSelectOption<string>[]>(() =>
    this.titulares().map((t) => ({ label: t.rotulo, value: `${t.tipo}:${t.id}` }))
  );

  private readonly erros = computed<Record<string, string>>(() => {
    const e: Record<string, string> = {};
    if (!this.documento() && !this.titularFixo() && !this.titular()) e['titular'] = 'Escolha o veículo ou o implemento.';
    if (!this.tipo()) e['tipo'] = 'Escolha o tipo do documento.';
    if (this.tipo() === 'OUTRO' && !this.descricaoDocumento().trim()) e['descricao'] = 'Descreva o documento.';
    const emissao = this.emissao();
    const vencimento = this.vencimento();
    if (emissao && vencimento && vencimento < emissao) e['vencimento'] = 'O vencimento não pode ser antes da emissão.';
    return e;
  });

  protected readonly alterado = computed(() => !!this.arquivo() || this.snapshot() !== JSON.stringify(this.montarRequest()));

  constructor() {
    effect(() => {
      if (this.visivel()) {
        const documento = this.documento();
        untracked(() => this.preencher(documento));
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
    const request = this.montarRequest();
    const arquivo = this.arquivo();
    const existente = this.documento();
    let operacao: Observable<DocumentoFrota>;
    if (existente) {
      operacao = this.frota
        .atualizarDocumento(existente.id, request)
        .pipe(switchMap((doc) => (arquivo ? this.frota.anexarArquivoDocumento(doc.id, arquivo) : of(doc))));
    } else {
      operacao = this.frota.criarDocumento(request, arquivo);
    }
    this.salvando.set(true);
    operacao.subscribe({
      next: (doc) => {
        this.salvando.set(false);
        this.notificacoes.success(existente ? 'Documento atualizado.' : 'Documento cadastrado.');
        this.arquivo.set(null);
        this.snapshot.set(JSON.stringify(this.montarRequest()));
        this.visivel.set(false);
        this.salvo.emit(doc);
      },
      error: (erro) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(erro));
      }
    });
  }

  private preencher(doc: DocumentoFrota | null): void {
    this.tentouSalvar.set(false);
    this.titular.set(null);
    this.tipo.set(doc?.tipo ?? null);
    this.numero.set(doc?.numero ?? '');
    this.descricaoDocumento.set(doc?.descricao ?? '');
    this.emissao.set(deDataIso(doc?.dataEmissao));
    this.vencimento.set(deDataIso(doc?.dataVencimento));
    this.observacao.set(doc?.observacao ?? '');
    this.arquivo.set(null);
    this.snapshot.set(JSON.stringify(this.montarRequest()));
  }

  private montarRequest(): DocumentoFrotaRequest {
    const texto = (valor: string) => (valor.trim() ? valor.trim() : null);
    let idVeiculo = this.documento()?.idVeiculo ?? this.idVeiculo();
    let idImplemento = this.documento()?.idImplemento ?? this.idImplemento();
    const escolhido = this.titular();
    if (!this.documento() && !this.titularFixo() && escolhido) {
      const [tipo, id] = escolhido.split(':');
      idVeiculo = tipo === 'VEICULO' ? id : null;
      idImplemento = tipo === 'IMPLEMENTO' ? id : null;
    }
    return {
      idVeiculo: idVeiculo ?? null,
      idImplemento: idImplemento ?? null,
      tipo: this.tipo() ?? 'OUTRO',
      descricao: texto(this.descricaoDocumento()),
      numero: texto(this.numero()),
      dataEmissao: paraDataIso(this.emissao()),
      dataVencimento: paraDataIso(this.vencimento()),
      observacao: texto(this.observacao())
    };
  }
}
