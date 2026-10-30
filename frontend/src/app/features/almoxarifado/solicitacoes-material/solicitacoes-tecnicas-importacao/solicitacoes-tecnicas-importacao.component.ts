import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, model, output, signal } from '@angular/core';
import {
  CesBooleanControlComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesIconButtonComponent,
  CesImportDrawerComponent,
  CesNumberInputComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  NotificationService
} from '@ces/ui';

import { EstoqueResponse, MaterialResponse } from '../../../../core/models/almoxarifado.models';
import { AgenteResponsavelResponse, ObraResumoResponse } from '../../../../core/models/obras.models';
import { EquipeResponse } from '../../../../core/models/rh.models';
import { getErrorMessage } from '../../../../core/models/shared.models';
import {
  CodigoTipoTecnico,
  ConfirmarImportacaoSolicitacaoTecnicaRequest,
  ImportacaoSolicitacaoTecnicaResponse,
  ParChaveTipoImportacao,
  SolicitacaoTecnicaImportRow
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { FileDownloadUtil } from '../../../../core/utils/file-download.util';
import { toLocalDate } from '../../../../core/utils/local-date.util';
import { parseImportDate } from '../import-date.util';
import { contarSolicitacoesTecnicasPrevistas } from '../solicitacao-tecnica-importacao-contagem.util';
import { normalizarTipoTecnico } from '../solicitacao-tecnica-tipo.util';

const TIPOS_MOVIMENTACAO: CesSelectOption<CodigoTipoTecnico>[] = [
  { label: '221 — Saída direta / obra', value: '221' },
  { label: '921 — Saída indireta / concreto', value: '921' },
  { label: '222 — Devolução / obra', value: '222' },
  { label: '922 — Devolução indireta / concreto', value: '922' }
];

/**
 * Importação de solicitações técnicas por planilha (`ces-import-drawer`): o backend valida e
 * agrupa por obra + tipo; as linhas podem ser corrigidas aqui e são revalidadas no servidor
 * (o CSV corrigido é reenviado) antes de processar as combinações.
 */
@Component({
  selector: 'app-solicitacoes-tecnicas-importacao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DecimalPipe,
    NgTemplateOutlet,
    CesBooleanControlComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesNumberInputComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent
  ],
  template: `
    <ces-import-drawer [(visible)]="visible" title="Importar solicitações técnicas"
      description="Planilha CSV com obra, ocorrência, almoxarifado, equipe, data prevista, tipo (221/222/921/922), material, quantidade e reserva."
      [rows]="registrosImport()" [uploading]="enviando()" [processing]="processando() || sincronizando()"
      (templateRequested)="downloadTemplate()" (fileSelected)="onFileSelected($event)"
      (processed)="onProcessImport()" (cleared)="onClearImport()">
      @if (previewData() && registrosImport().length) {
        <p class="ces-cell-muted">
          {{ contagemTecnica().totalValidos }} de {{ contagemTecnica().totalItens }} itens válidos formarão
          {{ contagemTecnica().totalSolicitacoes }} {{ contagemTecnica().totalSolicitacoes === 1 ? 'solicitação técnica' : 'solicitações técnicas' }}
          ({{ contagemTecnica().totalGrupos }} {{ contagemTecnica().totalGrupos === 1 ? 'grupo' : 'grupos' }} de movimentação).
          Confira obra, almoxarifado, equipe e data antes de processar.
        </p>
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunas"
          [rows]="registrosExibidosImport()" [rowKey]="chaveLinha" [pageSizeOptions]="[25, 50, 100]">
          <div table-filters class="ces-quick-filters">
          @if (contagemTecnica().totalComErro > 0) {
            <button type="button" class="ces-quick-filter" [attr.aria-pressed]="filtroApenasErros()" (click)="filtroApenasErros.set(!filtroApenasErros())">
              <i class="pi pi-filter" aria-hidden="true"></i> Só as {{ contagemTecnica().totalComErro }} com erro
            </button>
          }
          </div>
          <ng-template cesCell="obra" let-row>
            @if (row.editing) {
              <ces-select label="Obra" [labelVisible]="false" [options]="opcoesObras()" [value]="row.numeroObra || null" (valueChange)="onObraChange(row, $event)" [filter]="true" [clearable]="true"></ces-select>
              <ces-text-input label="Ocorrência" placeholder="Ocorrência (OC/OS)" [labelVisible]="false" [(value)]="row.ocorrencia"></ces-text-input>
            } @else {
              <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.numeroObra, erro: temErroNoCampo(row, 'numeroObra') }"></ng-container>
              @if (row.ocorrencia) { <span class="ces-cell-muted">OC/OS {{ row.ocorrencia }}</span> }
            }
          </ng-template>
          <ng-template cesCell="almoxarifado" let-row>
            @if (row.editing) {
              <ces-select label="Almoxarifado" [labelVisible]="false" [options]="opcoesAlmoxarifados()" [value]="row.almoxarifado || null" (valueChange)="onAlmoxarifadoChange(row, $event)" [filter]="true" [clearable]="true"></ces-select>
            } @else {
              <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.almoxarifado, erro: temErroNoCampo(row, 'almoxarifado') }"></ng-container>
            }
          </ng-template>
          <ng-template cesCell="equipe" let-row>
            @if (row.editing) {
              <ces-select label="Equipe" [labelVisible]="false" [options]="opcoesEquipes()" [value]="row.codigoEquipe || null" (valueChange)="onEquipeChange(row, $event)" [filter]="true" [clearable]="true"></ces-select>
            } @else {
              <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.codigoEquipe, erro: temErroNoCampo(row, 'codigoEquipe') }"></ng-container>
            }
          </ng-template>
          <ng-template cesCell="dataPrevista" let-row>
            @if (row.editing) {
              <ces-datepicker label="Data prevista" [labelVisible]="false" [value]="parseImportDate(row.dataPrevista)"
                (valueChange)="row.dataPrevista = toLocalDate($event) ?? ''; recalcularParesETotaisImport()"></ces-datepicker>
            } @else {
              <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.dataPrevista, erro: temErroNoCampo(row, 'dataPrevista') }"></ng-container>
            }
          </ng-template>
          <ng-template cesCell="tipo" let-row>
            @if (row.editing) {
              <ces-select label="Tipo de movimentação" [labelVisible]="false" [options]="tiposMovimentacao" [(value)]="row.tipoMovimentacao" (valueChange)="recalcularParesETotaisImport()"></ces-select>
            } @else {
              <ng-container *ngTemplateOutlet="valor; context: { $implicit: formatCodigoTipoTecnico(row.tipoMovimentacao), erro: temErroNoCampo(row, 'tipoMovimentacao') }"></ng-container>
            }
          </ng-template>
          <ng-template cesCell="material" let-row>
            @if (row.editing) {
              <ces-select label="Material" [labelVisible]="false" [options]="opcoesMateriais()" [value]="row.codigoMaterial || null" (valueChange)="onMaterialChange(row, $event)" [filter]="true" [clearable]="true"></ces-select>
            } @else {
              <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.codigoMaterial, erro: temErroNoCampo(row, 'codigoMaterial') }"></ng-container>
              @if (row.nomeMaterial) { <span class="ces-cell-muted" [title]="row.nomeMaterial">{{ row.nomeMaterial }}</span> }
            }
          </ng-template>
          <ng-template cesCell="quantidade" let-row>
            @if (row.editing) {
              <ces-number-input label="Quantidade" [labelVisible]="false" mode="decimal" [min]="0.001" [maxFractionDigits]="3" [(value)]="row.quantidade"></ces-number-input>
            } @else {
              <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.quantidade != null ? (row.quantidade | number: '1.3-3') : '', erro: temErroNoCampo(row, 'quantidade') }"></ng-container>
            }
          </ng-template>
          <ng-template cesCell="reserva" let-row>
            @if (row.editing) {
              <ces-text-input label="Número da reserva" placeholder="Nº da reserva" [labelVisible]="false" [(value)]="row.numeroReserva"></ces-text-input>
              <ces-datepicker label="Data de criação da reserva" [labelVisible]="false" [value]="parseImportDate(row.dataCriacaoReserva)"
                (valueChange)="row.dataCriacaoReserva = toLocalDate($event) ?? ''; recalcularParesETotaisImport()"></ces-datepicker>
              <ces-select label="Agente responsável" placeholder="Agente" [labelVisible]="false" [options]="opcoesAgentes()" [value]="row.agenteResponsavel || null" (valueChange)="onAgenteChange(row, $event)" [filter]="true" [clearable]="true"></ces-select>
            } @else {
              <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.numeroReserva, erro: temErroNoCampo(row, 'numeroReserva') }"></ng-container>
              @if (row.dataCriacaoReserva || temErroNoCampo(row, 'dataCriacaoReserva')) {
                <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.dataCriacaoReserva, erro: temErroNoCampo(row, 'dataCriacaoReserva') }"></ng-container>
              }
              @if (row.agenteResponsavel || temErroNoCampo(row, 'agenteResponsavel')) {
                <ng-container *ngTemplateOutlet="valor; context: { $implicit: row.agenteResponsavel, erro: temErroNoCampo(row, 'agenteResponsavel') }"></ng-container>
              }
            }
          </ng-template>
          <ng-template cesCell="situacao" let-row>
            @if (row.status === 'COM_ERRO') {
              <ces-status tone="danger" icon="pi-times-circle">Erro em {{ obterNomeCampoAmigavel(row.campoErro, row.mensagemErro) }}</ces-status>
              <span class="ces-cell-muted" [title]="row.mensagemErro || ''">{{ row.mensagemErro }}</span>
            } @else if (row.status === 'CONCLUIDO') {
              <ces-status tone="success" icon="pi-check">Processado</ces-status>
            } @else if (row.mensagemErro) {
              <ces-status tone="warning" icon="pi-info-circle">Aviso</ces-status>
              <span class="ces-cell-muted" [title]="row.mensagemErro">{{ row.mensagemErro }}</span>
            } @else {
              <ces-status tone="info" icon="pi-check-circle">Pronta</ces-status>
              @if (row.observacao) { <span class="ces-cell-muted" [title]="row.observacao">{{ row.observacao }}</span> }
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              @if (row.editing) {
                <ces-icon-button icon="pi pi-check" [label]="'Concluir correção da linha ' + row.linha" size="icon-sm" (pressed)="finishImportEdit(row)"></ces-icon-button>
              } @else {
                <ces-icon-button icon="pi pi-pencil" [label]="'Corrigir linha ' + row.linha" size="icon-sm" (pressed)="startImportEdit(row)"></ces-icon-button>
                <ces-icon-button icon="pi pi-trash" [label]="'Remover linha ' + row.linha" size="icon-sm" (pressed)="removeImportRecord(row)"></ces-icon-button>
              }
            </span>
          </ng-template>
        </ces-data-table>

        @if (hasSelectedWarnings()) {
          <ces-callout tone="warning" title="A planilha tem avisos">
            <ces-boolean-control label="Reconheço os avisos das combinações selecionadas para prosseguir com a criação." [(value)]="reconhecerAvisos"></ces-boolean-control>
          </ces-callout>
        }
      }
    </ces-import-drawer>

    <ng-template #valor let-texto let-erro="erro">
      @if (erro) {
        <ces-status tone="danger" emphasis="outline" icon="pi-exclamation-circle">{{ texto || 'vazio' }}</ces-status>
      } @else {
        <span>{{ texto || '—' }}</span>
      }
    </ng-template>
  `
})
export class SolicitacoesTecnicasImportacaoComponent {
  protected readonly parseImportDate = parseImportDate;
  protected readonly toLocalDate = toLocalDate;
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly notifications = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);

  readonly visible = model(false);
  readonly obras = input<ObraResumoResponse[]>([]);
  readonly equipes = input<EquipeResponse[]>([]);
  readonly almoxarifados = input<EstoqueResponse[]>([]);
  readonly materiaisList = input<MaterialResponse[]>([]);
  readonly agentesList = input<AgenteResponsavelResponse[]>([]);
  /** Ao menos uma solicitação foi criada (a lista recarrega). */
  readonly processado = output<void>();

  protected readonly enviando = signal(false);
  protected readonly processando = signal(false);
  protected readonly sincronizando = signal(false);
  protected readonly previewData = signal<ImportacaoSolicitacaoTecnicaResponse | null>(null);
  protected readonly selectedPares = signal<ParChaveTipoImportacao[]>([]);
  protected readonly reconhecerAvisos = signal(false);
  protected readonly registrosImport = signal<SolicitacaoTecnicaImportRow[]>([]);
  protected readonly filtroApenasErros = signal(false);
  protected readonly contagemTecnica = computed(() => contarSolicitacoesTecnicasPrevistas(this.registrosImport()));
  protected readonly tiposMovimentacao = TIPOS_MOVIMENTACAO;
  protected readonly chaveLinha = (r: SolicitacaoTecnicaImportRow, i: number) => String(r.linha ?? i);

  protected readonly opcoesObras = computed<CesSelectOption<string>[]>(() => this.obras().map((o) => ({ label: `${o.numero} — ${o.titulo}`, value: o.numero })));
  protected readonly opcoesAlmoxarifados = computed<CesSelectOption<string>[]>(() => this.almoxarifados().map((e) => ({ label: e.nomeIdentificador, value: e.nomeIdentificador })));
  protected readonly opcoesEquipes = computed<CesSelectOption<string>[]>(() => this.equipes().map((e) => ({ label: e.codigo, value: e.codigo })));
  protected readonly opcoesMateriais = computed<CesSelectOption<string>[]>(() => this.materiaisList().map((m) => ({ label: `${m.codigo} - ${m.descricao}`, value: m.codigo })));
  protected readonly opcoesAgentes = computed<CesSelectOption<string>[]>(() => this.agentesList().map((a) => ({ label: a.nome, value: a.nome })));

  protected readonly colunas: CesDataTableColumn<SolicitacaoTecnicaImportRow>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'obra', header: 'Obra', type: 'custom', priority: 1, value: (r) => r.numeroObra },
    { key: 'almoxarifado', header: 'Almoxarifado', type: 'custom', value: (r) => r.almoxarifado },
    { key: 'equipe', header: 'Equipe', type: 'custom', value: (r) => r.codigoEquipe },
    { key: 'dataPrevista', header: 'Data prevista', type: 'custom', value: (r) => r.dataPrevista },
    { key: 'tipo', header: 'Tipo', type: 'custom', value: (r) => r.tipoMovimentacao },
    { key: 'material', header: 'Material', type: 'custom', priority: 1, value: (r) => r.codigoMaterial },
    { key: 'quantidade', header: 'Qtd.', type: 'custom', value: (r) => r.quantidade },
    { key: 'reserva', header: 'Reserva', type: 'custom', value: (r) => r.numeroReserva },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  protected downloadTemplate(): void {
    this.service.baixarModelo().subscribe({
      next: (res) => {
        if (res.body) FileDownloadUtil.downloadBlob(res.body, 'modelo_importacao_solicitacoes_tecnicas.csv');
      },
      error: (err) => this.notifications.error(getErrorMessage(err), { title: 'Falha ao baixar modelo' })
    });
  }

  protected async onFileSelected(file: File): Promise<void> {
    this.enviando.set(true);

    let rawRows: string[][] = [];
    try {
      rawRows = await this.lerLinhasCsv(file);
    } catch {
      // continua para a validação backend
    }

    this.service.validarImportacao(file).subscribe({
      next: (res) => {
        this.enviando.set(false);
        this.previewData.set(res);
        this.selectedPares.set([...(res.paresDisponiveis || [])]);
        this.reconhecerAvisos.set(false);
        this.filtroApenasErros.set(false);

        const linhaParaChave = new Map<number, string>();
        ((res as any).solicitacoesPrevistas || []).forEach((sol: any) => {
          (sol.subgrupos || []).forEach((sub: any) => {
            (sub.linhasOrigem || []).forEach((linha: number) => {
              linhaParaChave.set(linha, sol.chave);
            });
          });
        });

        const errosMap = new Map<number, { mensagem: string; campo?: string | null }>();
        (res.errosPorLinha || []).forEach((e) => {
          if (e.linha) errosMap.set(e.linha, { mensagem: e.mensagem, campo: e.campo });
        });

        const rows: SolicitacaoTecnicaImportRow[] = rawRows.map((cols, idx) => {
          const linhaNum = idx + 2;
          const erroInfo = errosMap.get(linhaNum);
          const msg = erroInfo?.mensagem;
          let campoErro = erroInfo?.campo ?? undefined;
          if (msg && !campoErro) {
            const msgLower = msg.toLowerCase();
            if (msgLower.includes('obra')) campoErro = 'numeroObra';
            else if (msgLower.includes('almoxarifado')) campoErro = 'almoxarifado';
            else if (msgLower.includes('equipe')) campoErro = 'codigoEquipe';
            else if (msgLower.includes('material')) campoErro = 'codigoMaterial';
            else if (msgLower.includes('quantidade')) campoErro = 'quantidade';
            else if (msgLower.includes('data')) campoErro = 'dataPrevista';
            else if (msgLower.includes('tipo')) campoErro = 'tipoMovimentacao';
            else if (msgLower.includes('reserva')) campoErro = 'numeroReserva';
            else if (msgLower.includes('agente')) campoErro = 'agenteResponsavel';
          }
          const numObra = cols[0]?.trim() || '';
          const almox = cols[2]?.trim() || '';
          const codEquipe = cols[3]?.trim() || '';
          const codMat = cols[7]?.trim() || '';
          const nomeAgente = cols[11]?.trim() || '';

          const o = this.obras().find((x) => x.numero === numObra);
          const e = this.almoxarifados().find((x) => x.nomeIdentificador === almox);
          const eq = this.equipes().find((x) => x.codigo === codEquipe);
          const m = this.materiaisList().find((x) => x.codigo === codMat);
          const ag = this.agentesList().find((x) => x.nome === nomeAgente);

          const row: SolicitacaoTecnicaImportRow = {
            linha: linhaNum,
            chave: linhaParaChave.get(linhaNum) || numObra,
            idObra: o?.id,
            numeroObra: numObra,
            ocorrencia: cols[1]?.trim() || '',
            idAlmoxarifado: e?.id,
            almoxarifado: almox,
            idEquipe: eq?.id,
            codigoEquipe: codEquipe,
            dataPrevista: cols[4]?.trim() || '',
            observacao: cols[5]?.trim() || '',
            tipoMovimentacao: normalizarTipoTecnico(cols[6]),
            idMaterial: m?.id,
            codigoMaterial: codMat,
            nomeMaterial: m?.descricao || '',
            quantidade: parseFloat((cols[8]?.trim() || '0').replace(',', '.')) || 0,
            numeroReserva: cols[9]?.trim() || '',
            dataCriacaoReserva: cols[10]?.trim() || '',
            idAgenteResponsavel: ag?.id,
            agenteResponsavel: nomeAgente,
            status: msg ? 'COM_ERRO' : 'AGUARDANDO_PROCESSAMENTO',
            campoErro: campoErro,
            mensagemErro: msg,
            editing: false
          };

          this.editSnapshots.set(row, {
            values: this.serializeTecnicaRow(row),
            previousStatus: row.status,
            previousError: row.mensagemErro
          });

          return row;
        });
        this.registrosImport.set(rows);
        if (rows.length > 0) {
          this.recalcularParesETotaisImport();
        }
        const contagemInicial = contarSolicitacoesTecnicasPrevistas(rows);

        if (contagemInicial.totalComErro > 0 || res.totalAvisos > 0) {
          this.notifications.warning(
            `${contagemInicial.totalValidos} item(ns) válido(s) (${contagemInicial.totalGrupos} grupo(s)), ${contagemInicial.totalComErro} com inconsistência.`,
            { title: 'Validação concluída com alertas' }
          );
        } else {
          this.notifications.success(
            `${contagemInicial.totalValidos} item(ns) pronto(s) para importação (${contagemInicial.totalSolicitacoes} solicitação(ões)).`,
            { title: 'Arquivo validado' }
          );
        }
      },
      error: (err) => {
        this.enviando.set(false);
        this.notifications.error(getErrorMessage(err), { title: 'Erro ao validar arquivo' });
      }
    });
  }

  private readonly editSnapshots = new WeakMap<SolicitacaoTecnicaImportRow, {
    values: string;
    previousStatus: string;
    previousError?: string;
  }>();

  protected startImportEdit(item: SolicitacaoTecnicaImportRow): void {
    if (!this.editSnapshots.has(item)) {
      this.editSnapshots.set(item, {
        values: this.serializeTecnicaRow(item),
        previousStatus: item.status || 'AGUARDANDO_PROCESSAMENTO',
        previousError: item.mensagemErro,
      });
    }
    item.editing = true;
  }

  private serializeTecnicaRow(item: SolicitacaoTecnicaImportRow): string {
    return JSON.stringify({
      obra: (item.numeroObra || '').trim().toLowerCase(),
      almox: (item.almoxarifado || '').trim().toLowerCase(),
      equipe: (item.codigoEquipe || '').trim().toLowerCase(),
      tipo: normalizarTipoTecnico(item.tipoMovimentacao),
      mat: (item.codigoMaterial || '').trim().toLowerCase(),
      qtd: Number(item.quantidade) || 0,
      reserva: (item.numeroReserva || '').trim().toLowerCase(),
      dataReserva: (item.dataCriacaoReserva || '').trim(),
      agente: (item.agenteResponsavel || '').trim().toLowerCase(),
      dataPrev: (item.dataPrevista || '').trim(),
      obs: (item.observacao || '').trim(),
      oco: (item.ocorrencia || '').trim(),
    });
  }

  protected onObraChange(item: SolicitacaoTecnicaImportRow, numero: string | null): void {
    const o = this.obras().find((x) => x.numero === numero);
    item.idObra = o?.id;
    item.numeroObra = numero || '';
  }

  protected onAlmoxarifadoChange(item: SolicitacaoTecnicaImportRow, nomeIdentificador: string | null): void {
    const e = this.almoxarifados().find((x) => x.nomeIdentificador === nomeIdentificador);
    item.idAlmoxarifado = e?.id;
    item.almoxarifado = nomeIdentificador || '';
  }

  protected onEquipeChange(item: SolicitacaoTecnicaImportRow, codigo: string | null): void {
    const eq = this.equipes().find((x) => x.codigo === codigo);
    item.idEquipe = eq?.id;
    item.codigoEquipe = codigo || '';
  }

  protected onMaterialChange(item: SolicitacaoTecnicaImportRow, codigo: string | null): void {
    const mat = this.materiaisList().find((x) => x.codigo === codigo);
    item.idMaterial = mat?.id;
    item.codigoMaterial = codigo || '';
    item.nomeMaterial = mat?.descricao || '';
  }

  protected onAgenteChange(item: SolicitacaoTecnicaImportRow, nome: string | null): void {
    const ag = this.agentesList().find((x) => x.nome === nome);
    item.idAgenteResponsavel = ag?.id;
    item.agenteResponsavel = nome || '';
  }

  protected finishImportEdit(item: SolicitacaoTecnicaImportRow): void {
    item.editing = false;

    item.numeroObra = item.numeroObra?.trim() || '';
    item.ocorrencia = item.ocorrencia?.trim() || '';
    item.almoxarifado = item.almoxarifado?.trim() || '';
    item.codigoEquipe = item.codigoEquipe?.trim() || '';
    item.dataPrevista = item.dataPrevista?.trim() || '';
    if (item.dataPrevista && /^\d{2}\/\d{2}\/\d{4}$/.test(item.dataPrevista)) {
      const [dia, mes, ano] = item.dataPrevista.split('/');
      item.dataPrevista = `${ano}-${mes}-${dia}`;
    }
    item.observacao = item.observacao?.trim() || '';
    item.codigoMaterial = item.codigoMaterial?.trim() || '';
    item.numeroReserva = item.numeroReserva?.trim() || '';
    item.dataCriacaoReserva = item.dataCriacaoReserva?.trim() || '';
    if (item.dataCriacaoReserva && /^\d{2}\/\d{2}\/\d{4}$/.test(item.dataCriacaoReserva)) {
      const [dia, mes, ano] = item.dataCriacaoReserva.split('/');
      item.dataCriacaoReserva = `${ano}-${mes}-${dia}`;
    }
    item.agenteResponsavel = item.agenteResponsavel?.trim() || '';

    const qtdNum = Number(item.quantidade);
    item.quantidade = !isNaN(qtdNum) ? qtdNum : (item.quantidade as any);

    const obs = this.obras();
    const almoxs = this.almoxarifados();
    const eqs = this.equipes();
    const mats = this.materiaisList();
    const ags = this.agentesList();

    const o = obs.find(
      (x) => (item.idObra && x.id === item.idObra) ||
             (x.numero && x.numero.trim().toLowerCase() === item.numeroObra.toLowerCase())
    );
    if (o) {
      item.idObra = o.id;
      item.numeroObra = o.numero;
    } else if (obs.length > 0) {
      item.idObra = undefined;
    }

    const e = almoxs.find(
      (x) => (item.idAlmoxarifado && x.id === item.idAlmoxarifado) ||
             (x.nomeIdentificador && x.nomeIdentificador.trim().toLowerCase() === item.almoxarifado.toLowerCase())
    );
    if (e) {
      item.idAlmoxarifado = e.id;
      item.almoxarifado = e.nomeIdentificador;
    } else if (almoxs.length > 0) {
      item.idAlmoxarifado = undefined;
    }

    const eq = eqs.find(
      (x) => (item.idEquipe && x.id === item.idEquipe) ||
             (x.codigo && x.codigo.trim().toLowerCase() === item.codigoEquipe.toLowerCase())
    );
    if (eq) {
      item.idEquipe = eq.id;
      item.codigoEquipe = eq.codigo;
    } else if (eqs.length > 0) {
      item.idEquipe = undefined;
    }

    const m = mats.find(
      (x) => (item.idMaterial && x.id === item.idMaterial) ||
             (x.codigo && x.codigo.trim().toLowerCase() === item.codigoMaterial.toLowerCase())
    );
    if (m) {
      item.idMaterial = m.id;
      item.codigoMaterial = m.codigo;
      item.nomeMaterial = m.descricao;
    } else if (mats.length > 0) {
      item.idMaterial = undefined;
    }

    if (item.agenteResponsavel) {
      const ag = ags.find(
        (x) => (item.idAgenteResponsavel && x.id === item.idAgenteResponsavel) ||
               (x.nome && x.nome.trim().toLowerCase() === item.agenteResponsavel.toLowerCase())
      );
      if (ag) {
        item.idAgenteResponsavel = ag.id;
        item.agenteResponsavel = ag.nome;
      } else if (ags.length > 0) {
        item.idAgenteResponsavel = undefined;
      }
    }

    // Se nada mudou e a linha já estava inválida, permanece com erro
    const snapshot = this.editSnapshots.get(item);
    const currentValues = this.serializeTecnicaRow(item);
    if (snapshot && snapshot.values === currentValues && snapshot.previousStatus === 'COM_ERRO') {
      item.status = 'COM_ERRO';
      item.mensagemErro = snapshot.previousError || 'A linha permanece com dados inválidos após a edição.';
      this.recalcularParesETotaisImport();
      return;
    }

    let erro: string | undefined;
    let campoErro: string | undefined;

    const obraObj = obs.find((x) => (item.idObra && x.id === item.idObra) || x.numero === item.numeroObra);
    const almoxObj = almoxs.find((x) => (item.idAlmoxarifado && x.id === item.idAlmoxarifado) || x.nomeIdentificador === item.almoxarifado);
    const eqObj = eqs.find((x) => (item.idEquipe && x.id === item.idEquipe) || x.codigo === item.codigoEquipe);
    const matObj = mats.find((x) => (item.idMaterial && x.id === item.idMaterial) || x.codigo === item.codigoMaterial);

    if (!item.numeroObra) {
      campoErro = 'numeroObra';
      erro = 'Obra: número da obra é obrigatório.';
    } else if (obs.length > 0 && !item.idObra) {
      campoErro = 'numeroObra';
      erro = `Obra: obra '${item.numeroObra}' não encontrada no cadastro.`;
    } else if (obraObj && (obraObj as any).ativo === false) {
      campoErro = 'numeroObra';
      erro = `Obra: a obra '${item.numeroObra}' está inativa.`;
    } else if (obraObj && obraObj.processoNome && obraObj.processoNome.trim().toUpperCase() !== 'OBRAS') {
      campoErro = 'numeroObra';
      erro = `Obra: a obra '${item.numeroObra}' não pertence a um processo técnico elegível (OBRAS).`;
    } else if (obraObj && ((obraObj as any).tipoObra === 'CORRETIVA' || (obraObj as any).tipoObra === 'PREVENTIVA') && !item.ocorrencia?.trim()) {
      campoErro = 'ocorrencia';
      erro = `Ocorrência: obrigatória para obras do tipo ${(obraObj as any).tipoObra}.`;
    } else if (obraObj && ((obraObj as any).tipoObra === 'OBRA' || (obraObj as any).tipoObra === 'LPT') && item.ocorrencia?.trim()) {
      campoErro = 'ocorrencia';
      erro = `Ocorrência: deve ficar vazia para obras do tipo ${(obraObj as any).tipoObra}.`;
    } else if (!item.almoxarifado) {
      campoErro = 'almoxarifado';
      erro = 'Almoxarifado: almoxarifado de atendimento é obrigatório.';
    } else if (almoxs.length > 0 && !item.idAlmoxarifado) {
      campoErro = 'almoxarifado';
      erro = `Almoxarifado: almoxarifado '${item.almoxarifado}' não encontrado no cadastro.`;
    } else if (
      almoxObj &&
      ((typeof almoxObj.tipoEstoque === 'object' &&
        almoxObj.tipoEstoque?.nome &&
        almoxObj.tipoEstoque.nome.toUpperCase() !== 'FISICO') ||
        (typeof almoxObj.tipoEstoque === 'string' &&
          (almoxObj.tipoEstoque as string).toUpperCase() !== 'FISICO'))
    ) {
      campoErro = 'almoxarifado';
      erro = `Almoxarifado: o almoxarifado '${item.almoxarifado}' deve ser do tipo FÍSICO.`;
    } else if (almoxObj && almoxObj.ativo === false) {
      campoErro = 'almoxarifado';
      erro = `Almoxarifado: o almoxarifado '${item.almoxarifado}' está inativo.`;
    } else if (!item.codigoEquipe) {
      campoErro = 'codigoEquipe';
      erro = 'Equipe: código da equipe é obrigatório.';
    } else if (eqs.length > 0 && !item.idEquipe) {
      campoErro = 'codigoEquipe';
      erro = `Equipe: equipe '${item.codigoEquipe}' não encontrada no cadastro.`;
    } else if (eqObj && eqObj.ativo === false) {
      campoErro = 'codigoEquipe';
      erro = `Equipe: a equipe '${item.codigoEquipe}' está inativa.`;
    } else if (!item.tipoMovimentacao) {
      campoErro = 'tipoMovimentacao';
      erro = 'Tipo de movimentação: campo obrigatório.';
    } else if (!['221', '222', '921', '922'].includes((item.tipoMovimentacao = normalizarTipoTecnico(item.tipoMovimentacao)))) {
      campoErro = 'tipoMovimentacao';
      erro = 'Tipo de movimentação: valor inválido (deve ser 221, 222, 921 ou 922).';
    } else if (!item.codigoMaterial) {
      campoErro = 'codigoMaterial';
      erro = 'Material: código do material é obrigatório.';
    } else if (mats.length > 0 && !item.idMaterial) {
      campoErro = 'codigoMaterial';
      erro = `Material: material '${item.codigoMaterial}' não encontrado no cadastro.`;
    } else if (matObj && matObj.ativo === false) {
      campoErro = 'codigoMaterial';
      erro = `Material: o material '${item.codigoMaterial}' está inativo.`;
    } else if (
      item.quantidade === undefined ||
      item.quantidade === null ||
      isNaN(item.quantidade) ||
      item.quantidade < 0.001
    ) {
      campoErro = 'quantidade';
      erro = 'Quantidade: quantidade deve ser maior ou igual a 0,001.';
    } else if (obs.length > 0 && (!item.idObra || !item.idAlmoxarifado || !item.idEquipe || !item.idMaterial)) {
      campoErro = 'numeroObra';
      erro = 'Cadastro: um ou mais campos não foram encontrados no cadastro.';
    }

    item.chave = item.chave || item.numeroObra;
    if (erro) {
      item.status = 'COM_ERRO';
      item.campoErro = campoErro;
      item.mensagemErro = erro;
      this.recalcularParesETotaisImport();
    } else {
      item.status = 'AGUARDANDO_PROCESSAMENTO';
      item.campoErro = undefined;
      item.mensagemErro = undefined;
      this.sincronizarComBackend();
    }
  }

  protected removeImportRecord(linha: SolicitacaoTecnicaImportRow): void {
    this.registrosImport.set(this.registrosImport().filter((r) => r !== linha));
    this.sincronizarComBackend();
  }

  protected sincronizarComBackend(): void {
    const current = this.registrosImport();
    if (current.length === 0) {
      this.previewData.set(null);
      this.selectedPares.set([]);
      return;
    }

    const blob = this.gerarCsvBlob(current);
    const file = new File([blob], 'solicitacoes_editadas.csv', { type: 'text/csv' });

    this.sincronizando.set(true);
    this.service.validarImportacao(file).subscribe({
      next: (preview) => {
        this.sincronizando.set(false);
        this.previewData.set(preview);
        this.selectedPares.set([...(preview.paresDisponiveis || [])]);

        const linhaParaChave = new Map<number, string>();
        ((preview as any).solicitacoesPrevistas || []).forEach((sol: any) => {
          (sol.subgrupos || []).forEach((sub: any) => {
            (sub.linhasOrigem || []).forEach((linha: number) => {
              linhaParaChave.set(linha, sol.chave);
            });
          });
        });

        const errosMap = new Map<number, { mensagem: string; campo?: string | null }>();
        (preview.errosPorLinha || []).forEach((e) => {
          if (e.linha) {
            const current = errosMap.get(e.linha);
            errosMap.set(e.linha, {
              mensagem: [current?.mensagem, e.mensagem].filter(Boolean).join(' '),
              campo: current?.campo || e.campo || undefined,
            });
          }
        });

        this.registrosImport.update((items) =>
          items.map((it, idx) => {
            const linhaCsv = idx + 2;
            const erroBackend = errosMap.get(linhaCsv);
            const msgFinal = erroBackend?.mensagem;
            const campoFinal = this.normalizarCampoErro(erroBackend?.campo, msgFinal);
            const chaveAtualizada = linhaParaChave.get(linhaCsv) || it.chave || it.numeroObra;

            return {
              ...it,
              linha: linhaCsv,
              chave: chaveAtualizada,
              status: msgFinal ? 'COM_ERRO' : 'AGUARDANDO_PROCESSAMENTO',
              campoErro: msgFinal ? campoFinal : undefined,
              mensagemErro: msgFinal,
            };
          }),
        );
        this.recalcularParesETotaisImport();
      },
      error: (error) => {
        this.sincronizando.set(false);
        this.notifications.error(
          `Falha ao revalidar alterações no servidor: ${getErrorMessage(error)}`,
          { title: 'Validação' },
        );
        this.recalcularParesETotaisImport();
      },
    });
  }

  private normalizarCampoErro(campo?: string | null, mensagem?: string): string | undefined {
    const c = (campo || '').toLowerCase();
    const m = (mensagem || '').toLowerCase();
    if (c.includes('obra') || (m.includes('obra') && !m.includes('tipo de obra'))) return 'numeroObra';
    if (c.includes('ocorrencia') || c.includes('ocorrência') || m.includes('ocorrencia') || m.includes('ocorrência')) return 'ocorrencia';
    if (c.includes('almoxarifado') || m.includes('almoxarifado')) return 'almoxarifado';
    if (c.includes('equipe') || m.includes('equipe')) return 'codigoEquipe';
    if (c.includes('material') || m.includes('material')) return 'codigoMaterial';
    if (c.includes('quantidade') || m.includes('quantidade')) return 'quantidade';
    if (c.includes('dataprevista') || (c.includes('data') && !c.includes('reserva')) || m.includes('data prevista')) return 'dataPrevista';
    if (
      c.includes('tipomovimentacao') ||
      c.includes('tipo movimentacao') ||
      c.includes('tipo de movimento') ||
      c.includes('tipo de movimentação') ||
      c.includes('tipo de solicitacao') ||
      c.includes('tipo de solicitação') ||
      c === 'tipo' ||
      m.includes('tipo deve ser') ||
      m.includes('tipo de movimentação') ||
      m.includes('tipo de movimento') ||
      m.includes('tipo de solicitação') ||
      m.includes('tipo técnico') ||
      m.includes('tipo tecnico') ||
      m.includes('tipo movimentacao') ||
      m.includes('tipo inválido') ||
      m.includes('tipo invalido')
    ) {
      return 'tipoMovimentacao';
    }
    if (c.includes('reserva') || m.includes('reserva')) return 'numeroReserva';
    if (c.includes('agente') || m.includes('agente')) return 'agenteResponsavel';
    return campo || undefined;
  }

  private gerarCsvBlob(linhas: SolicitacaoTecnicaImportRow[]): Blob {
    const header = [
      'Numero Obra', 'Ocorrencia OC OS', 'Almoxarifado Atendimento',
      'Codigo Equipe', 'Data Prevista', 'Observacao', 'Tipo Movimentacao', 'Codigo Material',
      'Quantidade', 'Numero Reserva', 'Data Criacao Reserva', 'Agente Responsavel',
    ].join(';');
    const dataRows = linhas.map((r) =>
      [
        r.numeroObra || '',
        r.ocorrencia || '',
        r.almoxarifado || '',
        r.codigoEquipe || '',
        r.dataPrevista || '',
        r.observacao || '',
        r.tipoMovimentacao || '221',
        r.codigoMaterial || '',
        r.quantidade !== undefined && r.quantidade !== null ? String(r.quantidade) : '',
        r.numeroReserva || '',
        r.dataCriacaoReserva || '',
        r.agenteResponsavel || '',
      ]
        .map((val) => `"${(val || '').replace(/"/g, '""')}"`)
        .join(';'),
    );
    const csvContent = [header, ...dataRows].join('\r\n');
    return new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
  }

  protected recalcularParesETotaisImport(): void {
    const rows = this.registrosImport();
    const contagem = contarSolicitacoesTecnicasPrevistas(rows);
    const paresMap = new Map<string, ParChaveTipoImportacao>();

    rows
      .filter((r) => r.status !== 'COM_ERRO' && r.numeroObra && r.tipoMovimentacao)
      .forEach((r) => {
        const chave = (r.chave || r.numeroObra || '').trim();
        if (!chave) return;
        const key = `${chave}\u0000${r.tipoMovimentacao}`;
        if (!paresMap.has(key)) {
          paresMap.set(key, { chave, tipo: r.tipoMovimentacao });
        }
      });

    const pares = Array.from(paresMap.values());
    const currentPrev = this.previewData();
    if (currentPrev) {
      this.previewData.set({
        ...currentPrev,
        totalLinhas: contagem.totalItens,
        totalConfirmaveis: contagem.totalGrupos,
        totalErros: contagem.totalComErro,
        paresDisponiveis: pares,
      });
      this.selectedPares.set(pares);
    }
  }

  protected registrosExibidosImport(): SolicitacaoTecnicaImportRow[] {
    const todos = this.registrosImport();
    if (this.filtroApenasErros()) {
      return todos.filter((r) => r.status === 'COM_ERRO');
    }
    return todos;
  }

  protected temErroNoCampo(item: SolicitacaoTecnicaImportRow, campo: string): boolean {
    if (item.status !== 'COM_ERRO') return false;
    const campoAlvo = campo.toLowerCase();
    const campoNormalizado = (item.campoErro || '').toLowerCase().trim();
    const erroNormalizado = (item.mensagemErro || '').toLowerCase().trim();

    if (campoNormalizado) {
      switch (campoAlvo) {
        case 'obra':
        case 'numeroobra':
          return campoNormalizado === 'numeroobra' || campoNormalizado === 'obra' || campoNormalizado.includes('obra');
        case 'ocorrencia':
          return campoNormalizado.includes('ocorrencia');
        case 'almoxarifado':
          return campoNormalizado.includes('almoxarifado');
        case 'equipe':
        case 'codigoequipe':
          return campoNormalizado === 'codigoequipe' || campoNormalizado === 'equipe' || campoNormalizado.includes('equipe');
        case 'dataprevista':
          return campoNormalizado === 'dataprevista' || (campoNormalizado.includes('prevista') && !campoNormalizado.includes('reserva'));
        case 'tipomovimentacao':
          return campoNormalizado.includes('tipomovimentacao') ||
                 campoNormalizado.includes('tipo movimentacao') ||
                 campoNormalizado.includes('tipo de movimentação') ||
                 campoNormalizado.includes('tipo de movimento') ||
                 campoNormalizado.includes('tipo de solicitacao') ||
                 campoNormalizado.includes('tipo de solicitação') ||
                 campoNormalizado === 'tipo';
        case 'material':
        case 'codigomaterial':
          return campoNormalizado === 'codigomaterial' || campoNormalizado === 'material' || campoNormalizado.includes('material');
        case 'quantidade':
          return campoNormalizado.includes('quantidade');
        case 'reserva':
        case 'numeroreserva':
          return campoNormalizado === 'numeroreserva' || (campoNormalizado.includes('reserva') && !campoNormalizado.includes('data'));
        case 'datacriacaoreserva':
          return campoNormalizado.includes('data') && campoNormalizado.includes('reserva');
        case 'agente':
        case 'agenteresponsavel':
          return campoNormalizado.includes('agente');
        default:
          return campoNormalizado === campoAlvo;
      }
    }

    // Fallback apenas se campoErro não estiver preenchido
    switch (campoAlvo) {
      case 'obra':
      case 'numeroobra':
        return erroNormalizado.includes('obra') && !erroNormalizado.includes('tipo de obra');
      case 'ocorrencia':
        return erroNormalizado.includes('ocorrência') || erroNormalizado.includes('ocorrencia') || erroNormalizado.includes('oc / os');
      case 'almoxarifado':
        return erroNormalizado.includes('almoxarifado');
      case 'equipe':
      case 'codigoequipe':
        return erroNormalizado.includes('equipe');
      case 'dataprevista':
        return erroNormalizado.includes('data prevista') || erroNormalizado.includes('data_prevista') || erroNormalizado.includes('data para o saque');
      case 'tipomovimentacao':
        return erroNormalizado.includes('tipo deve ser') ||
               erroNormalizado.includes('tipo de movimentação') ||
               erroNormalizado.includes('tipo de movimentacao') ||
               erroNormalizado.includes('tipo de movimento') ||
               erroNormalizado.includes('tipo de solicitação') ||
               erroNormalizado.includes('tipo de solicitacao') ||
               erroNormalizado.includes('tipo técnico') ||
               erroNormalizado.includes('tipo tecnico') ||
               erroNormalizado.includes('tipo movimentacao') ||
               erroNormalizado.includes('tipo inválido') ||
               erroNormalizado.includes('tipo invalido');
      case 'material':
      case 'codigomaterial':
        return erroNormalizado.includes('material');
      case 'quantidade':
        return erroNormalizado.includes('quantidade');
      case 'reserva':
      case 'numeroreserva':
        return (erroNormalizado.includes('número reserva') || erroNormalizado.includes('numero reserva') || erroNormalizado.includes('reserva'))
               && !erroNormalizado.includes('data real de criacao')
               && !erroNormalizado.includes('data criacao reserva');
      case 'datacriacaoreserva':
        return erroNormalizado.includes('data criação reserva') || erroNormalizado.includes('data da reserva') || erroNormalizado.includes('data real de criacao');
      case 'agente':
      case 'agenteresponsavel':
        return erroNormalizado.includes('agente');
      default:
        return false;
    }
  }

  protected obterNomeCampoAmigavel(campo?: string, mensagem?: string): string {
    const c = (campo || '').toLowerCase();
    const m = (mensagem || '').toLowerCase();
    if (c.includes('obra') || m.includes('obra')) return 'Obra';
    if (c.includes('ocorrencia') || m.includes('ocorrência') || m.includes('ocorrencia')) return 'Ocorrência';
    if (c.includes('almoxarifado') || m.includes('almoxarifado')) return 'Almoxarifado';
    if (c.includes('equipe') || m.includes('equipe')) return 'Equipe';
    if ((c.includes('data') && c.includes('reserva')) || m.includes('data reserva') || m.includes('data criação reserva')) return 'Data Reserva';
    if (c.includes('data') || m.includes('data prevista')) return 'Data Prevista';
    if (c.includes('material') || m.includes('material')) return 'Material';
    if (c.includes('quantidade') || m.includes('quantidade')) return 'Quantidade';
    if (
      c.includes('tipomovimentacao') ||
      c.includes('tipo movimentacao') ||
      c.includes('tipo de movimentação') ||
      c === 'tipo' ||
      m.includes('tipo deve ser') ||
      m.includes('tipo de movimentação') ||
      m.includes('tipo de solicitação') ||
      m.includes('tipo de solicitacao') ||
      m.includes('tipo técnico') ||
      m.includes('tipo tecnico') ||
      m.includes('tipo movimentacao')
    ) {
      return 'Tipo de Solicitação';
    }
    if (c.includes('reserva') || m.includes('reserva')) return 'Reserva';
    if (c.includes('agente') || m.includes('agente')) return 'Agente Resp.';
    return campo || 'Inconsistência';
  }

  private async lerLinhasCsv(file: File): Promise<string[][]> {
    const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer());
    const parsed = this.parseCsv(text.replace(/^\uFEFF/, ''));
    return parsed.slice(1);
  }

  private parseCsv(content: string): string[][] {
    const rows: string[][] = [];
    let row: string[] = [];
    let field = '';
    let quoted = false;

    for (let index = 0; index < content.length; index += 1) {
      const character = content[index];
      if (character === '"') {
        if (quoted && content[index + 1] === '"') {
          field += '"';
          index += 1;
        } else {
          quoted = !quoted;
        }
      } else if (character === ';' && !quoted) {
        row.push(field);
        field = '';
      } else if ((character === '\n' || character === '\r') && !quoted) {
        if (character === '\r' && content[index + 1] === '\n') index += 1;
        row.push(field);
        if (row.some((value) => value.length > 0)) {
          const firstCell = row[0]?.trim() ?? '';
          if (!firstCell.startsWith('#')) {
            rows.push(row);
          }
        }
        row = [];
        field = '';
      } else {
        field += character;
      }
    }
    row.push(field);
    if (row.some((value) => value.length > 0)) {
      const firstCell = row[0]?.trim() ?? '';
      if (!firstCell.startsWith('#')) {
        rows.push(row);
      }
    }
    return rows;
  }

  protected hasSelectedWarnings(): boolean {
    const data = this.previewData();
    if (!data) return false;
    return data.totalAvisos > 0 && this.selectedPares().length > 0;
  }

  protected formatCodigoTipoTecnico(tipo: CodigoTipoTecnico | string): string {
    return TIPOS_MOVIMENTACAO.find((t) => t.value === tipo)?.label ?? tipo;
  }
  protected async onProcessImport(): Promise<void> {
    if (this.sincronizando()) {
      this.notifications.warning('Aguarde a validação das alterações ser concluída antes de processar.');
      return;
    }
    const data = this.previewData();
    const selecionados = this.selectedPares();
    if (!data || selecionados.length === 0) return;

    if (this.hasSelectedWarnings() && !this.reconhecerAvisos()) {
      this.notifications.warning('Confirme o reconhecimento dos avisos para prosseguir.');
      return;
    }

    const ok = await this.confirmacao.confirm({
      title: 'Processar importação',
      message: `Criar as solicitações técnicas das ${selecionados.length} ${selecionados.length === 1 ? 'combinação' : 'combinações'} de obra e tipo?`,
      confirmLabel: 'Processar'
    });
    if (!ok) return;

    this.processando.set(true);
    const request: ConfirmarImportacaoSolicitacaoTecnicaRequest = {
      token: data.token,
      combinacoes: selecionados,
      reconhecerAvisos: this.reconhecerAvisos()
    };

    this.service.confirmarImportacao(request).subscribe({
      next: (res) => {
        this.processando.set(false);
        const totalSucesso = (res.resultados || []).filter((r) => !r.erro).length;
        const falhas = (res.resultados || []).filter((r) => !!r.erro);

        if (falhas.length > 0) {
          const falhasMap = new Map<string, string>();
          falhas.forEach((f) => {
            falhasMap.set(`${f.chave}\u0000${f.tipo}`, f.erro!);
            falhasMap.set(f.chave, f.erro!);
            falhasMap.set(f.tipo, f.erro!);
          });

          this.registrosImport.update((rows) =>
            rows.map((row) => {
              const erroMsg =
                falhasMap.get(`${row.chave}\u0000${row.tipoMovimentacao}`) ||
                falhasMap.get(`${row.numeroObra}\u0000${row.tipoMovimentacao}`) ||
                falhasMap.get(row.chave || '') ||
                falhasMap.get(row.numeroObra) ||
                falhasMap.get(row.tipoMovimentacao);

              if (erroMsg) {
                let campoErro = row.campoErro;
                const msgLower = erroMsg.toLowerCase();
                if (msgLower.includes('obra')) campoErro = 'numeroObra';
                else if (msgLower.includes('almoxarifado')) campoErro = 'almoxarifado';
                else if (msgLower.includes('equipe')) campoErro = 'codigoEquipe';
                else if (msgLower.includes('material')) campoErro = 'codigoMaterial';
                else if (msgLower.includes('quantidade')) campoErro = 'quantidade';
                else if (msgLower.includes('data')) campoErro = 'dataPrevista';
                else if (msgLower.includes('tipo')) campoErro = 'tipoMovimentacao';
                else if (msgLower.includes('reserva')) campoErro = 'numeroReserva';
                else if (msgLower.includes('chave')) campoErro = 'numeroObra';
                return { ...row, status: 'COM_ERRO' as const, campoErro, mensagemErro: erroMsg };
              } else if (totalSucesso > 0 && !falhasMap.has(row.tipoMovimentacao) && !falhasMap.has(row.numeroObra)) {
                return { ...row, status: 'CONCLUIDO' as const };
              }
              return row;
            })
          );

          this.recalcularParesETotaisImport();

          if (totalSucesso === 0) {
            this.notifications.error(falhas[0]?.erro || 'Falha ao processar solicitações.', { title: 'Erro ao processar importação' });
          } else {
            this.notifications.warning(
              `${totalSucesso} solicitação(ões) criada(s), mas ${falhas.length} combinação(ões) falharam. Veja os erros na tabela.`,
              { title: 'Importação parcial' }
            );
            this.processado.emit();
          }
        } else {
          this.notifications.success(`${totalSucesso} solicitação(ões) técnica(s) criada(s).`, { title: 'Importação concluída' });
          this.limpar();
          this.visible.set(false);
          this.processado.emit();
        }
      },
      error: (err) => {
        this.processando.set(false);
        this.notifications.error(getErrorMessage(err), { title: 'Erro ao confirmar importação' });
      }
    });
  }

  protected onClearImport(): void {
    this.limpar();
  }

  private limpar(): void {
    this.previewData.set(null);
    this.selectedPares.set([]);
    this.registrosImport.set([]);
    this.reconhecerAvisos.set(false);
  }
}
