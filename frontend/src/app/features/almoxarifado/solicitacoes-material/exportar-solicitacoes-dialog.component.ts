import { CommonModule } from '@angular/common';
import {
  Component,
  EventEmitter,
  inject,
  Input,
  OnChanges,
  OnInit,
  Output,
  signal,
  SimpleChanges,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { MultiSelectModule } from 'primeng/multiselect';
import { DatePickerModule } from 'primeng/datepicker';
import { ToggleButtonModule } from 'primeng/togglebutton';
import { InputTextModule } from 'primeng/inputtext';
import { MessageService } from 'primeng/api';
import { CesButtonComponent } from '@ces/ui';
import { DateMaskDirective } from '../../../shared/directives/date-mask.directive';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { getErrorMessage } from '../../../core/models/shared.models';
import { BaseOperacionalResponse } from '../../../core/models/rh.models';
import { EstoqueResponse } from '../../../core/models/almoxarifado.models';

export type ProcessoExportacao = 'TODOS' | 'COMERCIAL' | 'TECNICO';
export type FormatoExportacao = 'resumo' | 'detalhado';
export type TipoArquivoExportacao = 'CONSOLIDADO' | 'ZIP';

@Component({
  selector: 'app-exportar-solicitacoes-dialog',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    DialogModule,
    SelectModule,
    MultiSelectModule,
    DatePickerModule,
    ToggleButtonModule,
    InputTextModule,
    DateMaskDirective,
    CesButtonComponent,
  ],
  template: `
    <p-dialog
      [header]="processoSelecionado === 'TODOS' && tipoArquivo === 'ZIP' ? 'Exportar Solicitações de Material (.ZIP)' : 'Exportar Solicitações de Material (CSV)'"
      [visible]="visible"
      (visibleChange)="onVisibleChange($event)"
      [modal]="true"
      [style]="{ width: '680px' }"
      [breakpoints]="{ '768px': '100vw' }"
      [draggable]="false"
      [resizable]="false"
    >
      <div class="flex flex-col gap-4 mt-2">
        <!-- SELEÇÃO DE TIPO E FORMATO -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div class="flex flex-col gap-1">
            <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
              <i class="pi pi-database text-primary-700"></i> O que deseja exportar? (Processo)
            </label>
            <p-select
              [options]="processoOptions"
              [(ngModel)]="processoSelecionado"
              optionLabel="label"
              optionValue="value"
              class="custom-select w-full"
              appendTo="body"
              size="small"
            ></p-select>
          </div>
          <div class="flex flex-col gap-1">
            <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
              <i class="pi pi-file-excel text-primary-700"></i> Formato de Exportação
            </label>
            <p-select
              [options]="formatoOptions"
              [(ngModel)]="formatoSelecionado"
              optionLabel="label"
              optionValue="value"
              class="custom-select w-full"
              appendTo="body"
              size="small"
            ></p-select>
          </div>
        </div>

        @if (processoSelecionado === 'TODOS') {
          <div class="flex flex-col gap-1.5 p-3 rounded-lg bg-slate-50 border border-slate-200">
            <label class="!text-[11px] font-semibold text-slate-800 flex items-center gap-1.5">
              <i class="pi pi-sliders-h text-primary-700"></i> Estrutura do Arquivo de Saída
            </label>
            <p-select
              [options]="tipoArquivoOptions"
              [(ngModel)]="tipoArquivo"
              optionLabel="label"
              optionValue="value"
              class="custom-select w-full"
              appendTo="body"
              size="small"
            ></p-select>
            <span class="text-[11px] text-slate-500">
              @if (tipoArquivo === 'CONSOLIDADO') {
                <i class="pi pi-check-circle text-emerald-600 mr-1"></i>
                <strong>Arquivo Consolidado Único:</strong> Visão canônica com colunas padronizadas unificando as informações (Origem, Destino/Aplicação, Equipe/Solicitante) sem colunas vazias.
              } @else {
                <i class="pi pi-folder text-blue-600 mr-1"></i>
                <strong>Pacote Separado (.ZIP):</strong> Arquivo compactado contendo 2 planilhas individuais (Comercial e Técnico), cada uma com todos os seus campos e especificidades operacionais.
              }
            </span>
          </div>
        }

        <div class="h-px bg-slate-100 my-1"></div>

        <!-- INFORMATIVO -->
        <div class="flex flex-col gap-1 -mt-2">
          <p class="text-xs text-slate-500 font-medium">Filtros da Exportação</p>
          <span
            class="text-[10px] text-blue-700 bg-blue-50 px-2.5 py-1.5 rounded-md w-fit border border-blue-100 flex items-center gap-1.5"
          >
            <i class="pi pi-info-circle text-[11px]"></i>
            Exportação completa sem limite de linhas. O download do arquivo será iniciado em tempo real.
          </span>
        </div>

        <!-- FILTROS -->
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-1">
          <!-- Período -->
          <div class="flex flex-col gap-1">
            <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
              <i class="pi pi-calendar text-slate-400"></i> Solicitada de
            </label>
            <p-datepicker
              [(ngModel)]="dataDe"
              dateFormat="dd/mm/yy"
              dateMask
              [showIcon]="true"
              iconDisplay="input"
              [showButtonBar]="true"
              placeholder="dd/mm/aaaa"
              appendTo="body"
              class="w-full"
              size="small"
            ></p-datepicker>
          </div>

          <div class="flex flex-col gap-1">
            <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
              <i class="pi pi-calendar text-slate-400"></i> Solicitada até
            </label>
            <p-datepicker
              [(ngModel)]="dataAte"
              dateFormat="dd/mm/yy"
              dateMask
              [showIcon]="true"
              iconDisplay="input"
              [showButtonBar]="true"
              placeholder="dd/mm/aaaa"
              appendTo="body"
              class="w-full"
              size="small"
            ></p-datepicker>
          </div>

          <!-- Status -->
          <div class="flex flex-col gap-1 sm:col-span-2">
            <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
              <i class="pi pi-flag text-slate-400"></i> Status
            </label>
            <p-multiSelect
              [options]="statusOptions"
              [(ngModel)]="statusSelecionados"
              optionLabel="label"
              optionValue="value"
              placeholder="Todos os status"
              [showClear]="true"
              [maxSelectedLabels]="2"
              selectedItemsLabel="{0} status selecionados"
              appendTo="body"
              class="custom-multiselect w-full"
              size="small"
            ></p-multiSelect>
          </div>

          <!-- Filtros de Obra e Almoxarifado (Técnico / Todos) -->
          @if (processoSelecionado !== 'COMERCIAL') {
            @if (obraOptions.length > 0) {
              <div class="flex flex-col gap-1">
                <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                  <i class="pi pi-briefcase text-slate-400"></i> Obra
                </label>
                <p-select
                  [options]="obraOptions"
                  [(ngModel)]="filtroObra"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Todas as obras"
                  [showClear]="true"
                  [filter]="true"
                  appendTo="body"
                  class="custom-select w-full"
                  size="small"
                ></p-select>
              </div>
            }

            @if (almoxarifadoOptions.length > 0) {
              <div class="flex flex-col gap-1">
                <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                  <i class="pi pi-building text-slate-400"></i> Almoxarifado Atendimento
                </label>
                <p-select
                  [options]="almoxarifadoOptions"
                  [(ngModel)]="filtroAlmoxarifado"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Todos os almoxarifados"
                  [showClear]="true"
                  [filter]="true"
                  appendTo="body"
                  class="custom-select w-full"
                  size="small"
                ></p-select>
              </div>
            }

            @if (equipeOptions.length > 0) {
              <div class="flex flex-col gap-1">
                <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                  <i class="pi pi-users text-slate-400"></i> Equipe de Saque
                </label>
                <p-select
                  [options]="equipeOptions"
                  [(ngModel)]="filtroEquipe"
                  optionLabel="label"
                  optionValue="value"
                  placeholder="Todas as equipes"
                  [showClear]="true"
                  [filter]="true"
                  appendTo="body"
                  class="custom-select w-full"
                  size="small"
                ></p-select>
              </div>
            }

            <div class="flex flex-col gap-1">
              <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                <i class="pi pi-tags text-slate-400"></i> Tipo Técnico
              </label>
              <p-select
                [options]="tipoTecnicoOptions"
                [(ngModel)]="filtroTipoTecnico"
                optionLabel="label"
                optionValue="value"
                placeholder="Todos os tipos"
                [showClear]="true"
                appendTo="body"
                class="custom-select w-full"
                size="small"
              ></p-select>
            </div>

            <div class="flex flex-col gap-1">
              <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                <i class="pi pi-box text-slate-400"></i> Status de Separação
              </label>
              <p-select
                [options]="statusSeparacaoOptions"
                [(ngModel)]="filtroStatusSeparacao"
                optionLabel="label"
                optionValue="value"
                placeholder="Todos"
                [showClear]="true"
                appendTo="body"
                class="custom-select w-full"
                size="small"
              ></p-select>
            </div>

            <div class="flex flex-col gap-1">
              <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                <i class="pi pi-hashtag text-slate-400"></i> Ocorrência / Reserva
              </label>
              <input
                pInputText
                [(ngModel)]="filtroOcorrencia"
                placeholder="Código OC/OS ou Reserva"
                class="custom-input w-full"
                size="small"
              />
            </div>
          }

          <!-- Filtros de Estoques Comerciais (Comercial / Todos) -->
          @if (processoSelecionado !== 'TECNICO') {
            @if (estoqueOptions.length > 0) {
              <div class="flex flex-col gap-1">
                <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                  <i class="pi pi-arrow-up-right text-slate-400"></i> Estoque Origem
                </label>
                <p-select
                  [options]="estoqueOptions"
                  [(ngModel)]="filtroEstoqueOrigem"
                  optionLabel="nomeIdentificador"
                  optionValue="id"
                  placeholder="Todos"
                  [showClear]="true"
                  [filter]="true"
                  appendTo="body"
                  class="custom-select w-full"
                  size="small"
                ></p-select>
              </div>

              <div class="flex flex-col gap-1">
                <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                  <i class="pi pi-arrow-down-left text-slate-400"></i> Estoque Destino
                </label>
                <p-select
                  [options]="estoqueOptions"
                  [(ngModel)]="filtroEstoqueDestino"
                  optionLabel="nomeIdentificador"
                  optionValue="id"
                  placeholder="Todos"
                  [showClear]="true"
                  [filter]="true"
                  appendTo="body"
                  class="custom-select w-full"
                  size="small"
                ></p-select>
              </div>
            }

            @if (baseOptions.length > 0) {
              <div class="flex flex-col gap-1">
                <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                  <i class="pi pi-map-marker text-slate-400"></i> Base Operacional
                </label>
                <p-select
                  [options]="baseOptions"
                  [(ngModel)]="filtroBase"
                  optionLabel="nome"
                  optionValue="id"
                  placeholder="Todas as bases"
                  [showClear]="true"
                  [filter]="true"
                  appendTo="body"
                  class="custom-select w-full"
                  size="small"
                ></p-select>
              </div>
            }

            <div class="flex flex-col gap-1">
              <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
                <i class="pi pi-barcode text-slate-400"></i> Nº Série
              </label>
              <input
                pInputText
                [(ngModel)]="filtroNumSerie"
                placeholder="Número de série do material"
                class="custom-input w-full"
                size="small"
              />
            </div>
          }

          <!-- Busca Rápida -->
          <div class="flex flex-col gap-1 sm:col-span-2">
            <label class="!text-[11px] font-medium text-slate-700 flex items-center gap-1">
              <i class="pi pi-search text-slate-400"></i> Busca Rápida
            </label>
            <input
              pInputText
              [(ngModel)]="filtroBuscaGlobal"
              placeholder="Buscar por obra, ocorrência, reserva ou número de série"
              class="custom-input w-full"
              size="small"
            />
          </div>

          <!-- Apenas Minhas -->
          <div class="flex flex-col gap-1 sm:col-span-2 pt-2 border-t border-slate-100">
            <p-toggleButton
              [(ngModel)]="apenasMinhas"
              onLabel="Apenas minhas solicitações"
              offLabel="Apenas minhas solicitações"
              onIcon="pi pi-check"
              offIcon="pi pi-minus"
              styleClass="!text-[11px] !font-medium w-fit"
            ></p-toggleButton>
          </div>
        </div>
      </div>

      <ng-template pTemplate="footer">
        <ces-button
          label="Cancelar"
          icon="pi pi-times"
          variant="secondary"
          size="sm"
          (pressed)="fechar()"
        ></ces-button>
        <ces-button
          [label]="processoSelecionado === 'TODOS' && tipoArquivo === 'ZIP' ? 'Baixar Pacote (.ZIP)' : 'Baixar CSV'"
          [icon]="processoSelecionado === 'TODOS' && tipoArquivo === 'ZIP' ? 'pi pi-folder' : 'pi pi-download'"
          variant="success"
          size="sm"
          (pressed)="executarExportacao()"
          [loading]="exportando()"
        ></ces-button>
      </ng-template>
    </p-dialog>
  `,
})
export class ExportarSolicitacoesDialogComponent implements OnInit, OnChanges {
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly messageService = inject(MessageService);

  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();
  @Input() contextoInicial: ProcessoExportacao = 'TODOS';
  @Input() filtrosAtivos: any = null;

  @Input() estoqueOptions: EstoqueResponse[] = [];
  @Input() baseOptions: BaseOperacionalResponse[] = [];
  @Input() obraOptions: { label: string; value: string }[] = [];
  @Input() almoxarifadoOptions: { label: string; value: string }[] = [];
  @Input() equipeOptions: { label: string; value: string }[] = [];

  exportando = signal<boolean>(false);

  processoSelecionado: ProcessoExportacao = 'TODOS';
  formatoSelecionado: FormatoExportacao = 'resumo';
  tipoArquivo: TipoArquivoExportacao = 'CONSOLIDADO';

  readonly tipoArquivoOptions = [
    { label: 'Arquivo Único Consolidado (.CSV)', value: 'CONSOLIDADO' },
    { label: 'Pacote com Arquivos Separados (.ZIP)', value: 'ZIP' },
  ];

  dataDe: Date | null = null;
  dataAte: Date | null = null;
  statusSelecionados: string[] = [];
  filtroObra: string | null = null;
  filtroAlmoxarifado: string | null = null;
  filtroEquipe: string | null = null;
  filtroTipoTecnico: string | null = null;
  filtroStatusSeparacao: string | null = null;
  filtroOcorrencia: string = '';
  filtroEstoqueOrigem: string | null = null;
  filtroEstoqueDestino: string | null = null;
  filtroBase: string | null = null;
  filtroNumSerie: string = '';
  filtroBuscaGlobal: string = '';
  apenasMinhas: boolean = false;

  readonly processoOptions = [
    { label: 'Todos os Processos (Comercial e Técnico)', value: 'TODOS' },
    { label: 'Apenas Processo Técnico', value: 'TECNICO' },
    { label: 'Apenas Processo Comercial', value: 'COMERCIAL' },
  ];

  readonly formatoOptions = [
    { label: 'Resumo (1 linha por solicitação)', value: 'resumo' },
    { label: 'Completo / Detalhado (1 linha por item)', value: 'detalhado' },
  ];

  readonly statusOptions = [
    { label: 'Pendente', value: 'PENDENTE' },
    { label: 'Aprovado', value: 'APROVADO' },
    { label: 'Entregue Parcial', value: 'ENTREGUE_PARCIAL' },
    { label: 'Entregue', value: 'ENTREGUE' },
    { label: 'Devolvido Parcial', value: 'DEVOLVIDO_PARCIAL' },
    { label: 'Devolvido', value: 'DEVOLVIDO' },
    { label: 'Rejeitado', value: 'REJEITADO' },
    { label: 'Estornado', value: 'ESTORNADO' },
    { label: 'Cancelado', value: 'CANCELADO' },
  ];

  readonly tipoTecnicoOptions = [
    { label: '221 - Adição Comum', value: 'ADICAO_COMUM' },
    { label: '222 - Devolução Comum', value: 'DEVOLUCAO_COMUM' },
    { label: '921 - Adição Concreto', value: 'ADICAO_CONCRETO' },
    { label: '922 - Devolução Concreto', value: 'DEVOLUCAO_CONCRETO' },
  ];

  readonly statusSeparacaoOptions = [
    { label: 'Aguardando Agendamento', value: 'AGUARDANDO_AGENDAMENTO' },
    { label: 'Pronto para Separação', value: 'PRONTO_PARA_SEPARACAO' },
    { label: 'Em Separação', value: 'EM_SEPARACAO' },
    { label: 'Separado', value: 'SEPARADO' },
  ];

  ngOnInit() {
    this.sincronizarFiltros();
  }

  ngOnChanges(changes: SimpleChanges) {
    if (changes['visible']?.currentValue || changes['filtrosAtivos'] || changes['contextoInicial']) {
      this.sincronizarFiltros();
    }
  }

  fechar() {
    this.visible = false;
    this.visibleChange.emit(false);
  }

  onVisibleChange(val: boolean) {
    this.visible = val;
    this.visibleChange.emit(val);
  }

  sincronizarFiltros() {
    this.processoSelecionado = this.contextoInicial || 'TODOS';
    this.tipoArquivo = 'CONSOLIDADO';
    const ativos = this.filtrosAtivos;
    if (!ativos) return;

    if (ativos.dataInicio) this.dataDe = new Date(ativos.dataInicio);
    if (ativos.dataFim) this.dataAte = new Date(ativos.dataFim);

    if (ativos.status) {
      this.statusSelecionados = Array.isArray(ativos.status) ? [...ativos.status] : [ativos.status];
    } else if (ativos.statuses && Array.isArray(ativos.statuses)) {
      this.statusSelecionados = [...ativos.statuses];
    }

    if (ativos.idObra) this.filtroObra = ativos.idObra;
    if (ativos.idAlmoxarifado) this.filtroAlmoxarifado = ativos.idAlmoxarifado;
    if (ativos.idEquipe) this.filtroEquipe = ativos.idEquipe;
    if (ativos.tipoTecnico) this.filtroTipoTecnico = ativos.tipoTecnico;
    if (ativos.statusSeparacao) this.filtroStatusSeparacao = ativos.statusSeparacao;
    if (ativos.ocorrencia) this.filtroOcorrencia = ativos.ocorrencia;
    if (ativos.idEstoqueOrigem) this.filtroEstoqueOrigem = ativos.idEstoqueOrigem;
    if (ativos.idEstoqueDestino) this.filtroEstoqueDestino = ativos.idEstoqueDestino;
    if (ativos.idBaseOrigem) this.filtroBase = ativos.idBaseOrigem;
    if (ativos.numSerie) this.filtroNumSerie = ativos.numSerie;
    if (ativos.buscaGlobal) this.filtroBuscaGlobal = ativos.buscaGlobal;
    if (ativos.apenasMinhas !== undefined) this.apenasMinhas = !!ativos.apenasMinhas;
  }

  executarExportacao() {
    const params: Record<string, any> = {};

    if (this.processoSelecionado !== 'TODOS') {
      params['contexto'] = this.processoSelecionado;
    } else if (this.tipoArquivo === 'ZIP') {
      params['pacoteZip'] = true;
    }

    if (this.dataDe) params['dataInicio'] = this.toIso(this.dataDe, false);
    if (this.dataAte) params['dataFim'] = this.toIso(this.dataAte, true);

    if (this.statusSelecionados && this.statusSelecionados.length > 0) {
      if (this.statusSelecionados.length === 1) {
        params['status'] = this.statusSelecionados[0];
      } else {
        params['statuses'] = this.statusSelecionados;
      }
    }

    if (this.processoSelecionado !== 'COMERCIAL') {
      if (this.filtroObra) params['idObra'] = this.filtroObra;
      if (this.filtroAlmoxarifado) params['idAlmoxarifado'] = this.filtroAlmoxarifado;
      if (this.filtroEquipe) params['idEquipe'] = this.filtroEquipe;
      if (this.filtroTipoTecnico) params['tipoTecnico'] = this.filtroTipoTecnico;
      if (this.filtroStatusSeparacao) params['statusSeparacao'] = this.filtroStatusSeparacao;
      if (this.filtroOcorrencia?.trim()) params['ocorrencia'] = this.filtroOcorrencia.trim();
    }

    if (this.processoSelecionado !== 'TECNICO') {
      if (this.filtroEstoqueOrigem) params['idEstoqueOrigem'] = this.filtroEstoqueOrigem;
      if (this.filtroEstoqueDestino) params['idEstoqueDestino'] = this.filtroEstoqueDestino;
      if (this.filtroBase) params['idBaseOrigem'] = this.filtroBase;
      if (this.filtroNumSerie?.trim()) params['numSerie'] = this.filtroNumSerie.trim();
    }

    if (this.filtroBuscaGlobal?.trim()) {
      params['buscaGlobal'] = this.filtroBuscaGlobal.trim();
    }

    if (this.apenasMinhas) {
      params['apenasMinhas'] = true;
    }

    this.exportando.set(true);

    const call$ =
      this.formatoSelecionado === 'resumo'
        ? this.almoxarifado.exportarSolicitacoesResumo(params)
        : this.almoxarifado.exportarSolicitacoesDetalhado(params);

    call$.subscribe({
      next: (blob) => {
        this.exportando.set(false);
        this.fechar();

        const url = window.URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        if (this.processoSelecionado === 'TODOS' && this.tipoArquivo === 'ZIP') {
          anchor.download = `solicitacoes_material_${this.formatoSelecionado}.zip`;
        } else {
          const prefixo =
            this.processoSelecionado === 'TECNICO'
              ? 'solicitacoes_tecnicas'
              : this.processoSelecionado === 'COMERCIAL'
                ? 'solicitacoes_comerciais'
                : 'solicitacoes_consolidado';
          anchor.download = `${prefixo}_${this.formatoSelecionado}.csv`;
        }
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.URL.revokeObjectURL(url);

        this.messageService.add({
          severity: 'success',
          summary: 'Exportação concluída',
          detail: 'O download foi iniciado com sucesso.',
        });
      },
      error: (err) => {
        this.exportando.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro ao exportar',
          detail: getErrorMessage(err),
        });
      },
    });
  }

  private toIso(date: Date | null, endOfDay: boolean): string | null {
    if (!date) return null;
    const d = new Date(date);
    if (endOfDay) {
      d.setHours(23, 59, 59, 999);
    } else {
      d.setHours(0, 0, 0, 0);
    }
    return d.toISOString();
  }
}
