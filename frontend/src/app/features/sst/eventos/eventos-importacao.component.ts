import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, model, output, signal } from '@angular/core';
import {
  CesButtonComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesImportDrawerComponent,
  CesMultiSelectComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  cesImportStatus
} from '@ces/ui';
import { map } from 'rxjs';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup as BuscaColaboradores } from '../../../core/lookup/store/remote-lookup';
import { EquipeResponse, VeiculoResponse } from '../../../core/models/rh.models';
import {
  EventoSstImportacaoDTO,
  PAPEL_ENVOLVIDO_LABELS,
  REGISTRO_EVIDENCIA_LABELS,
  TIPO_CAT_LABELS,
  TIPO_ENVOLVIDO_LABELS,
  TIPO_EVENTO_LABELS,
  TipoEventoSst,
  opcoesDe
} from '../../../core/models/sst-evento.models';
import { ImportacaoService } from '../../../core/services/importacao.service';
import { RhService } from '../../../core/services/rh.service';
import { SstService } from '../../../core/services/sst.service';
import { RemoteLookup } from '../../../core/utils/remote-lookup.util';
import { CHAVE_LINHA_IMPORTACAO, importacaoListagem } from '../../../shared/listagem/importacao-listagem';
import { EventosLookups } from './eventos-lookups.service';

interface Envolvido {
  tipo: string;
  papel: string;
  identificacao: string;
  funcao?: string;
  empresa?: string;
}

const SIM_NAO: CesSelectOption<string>[] = [
  { label: 'Sim', value: 'Sim' },
  { label: 'Não', value: 'Não' }
];

/** `dd/MM/yyyy` ou `dd/MM/yyyy HH:mm` (formato da planilha) → `Date`. */
export function dataDaPlanilha(texto: string | null | undefined): Date | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})(?:\s+(\d{2}):(\d{2}))?/.exec((texto ?? '').trim());
  if (!m) return null;
  const data = new Date(+m[3], +m[2] - 1, +m[1], m[4] ? +m[4] : 0, m[5] ? +m[5] : 0);
  return isNaN(data.getTime()) ? null : data;
}

/** `Date` → texto no formato da planilha. */
export function dataParaPlanilha(data: Date | null, comHora: boolean): string {
  if (!data) return '';
  const dois = (n: number) => String(n).padStart(2, '0');
  const dia = `${dois(data.getDate())}/${dois(data.getMonth() + 1)}/${data.getFullYear()}`;
  return comHora ? `${dia} ${dois(data.getHours())}:${dois(data.getMinutes())}` : dia;
}

/**
 * Importação de eventos de SST por planilha: pré-visualização no `ces-import-drawer` e correção
 * de uma linha por vez num drawer com todos os campos (a planilha casa os cadastros pelo nome).
 */
@Component({
  selector: 'app-eventos-importacao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesImportDrawerComponent,
    CesMultiSelectComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent
  ],
  template: `
    <ces-import-drawer [(visible)]="visible" title="Importar eventos de segurança"
      description="Planilha CSV com tipo, datas de ocorrência e comunicação, atividade, gestor, base, equipe, veículo, lesão, CAT, técnicos e envolvidos."
      [rows]="importacao.linhas()" [uploading]="importacao.enviando()" [processing]="importacao.processando()"
      (templateRequested)="importacao.baixarModelo()" (fileSelected)="importacao.enviarArquivo($event)"
      (processed)="importacao.processar()" (cleared)="importacao.limpar()">
      @if (importacao.linhas().length) {
        <ces-data-table caption="Pré-visualização da importação" [searchable]="false" [columns]="colunas"
          [rows]="importacao.linhas()" [rowKey]="chaveLinha" [pageSizeOptions]="[25, 50, 100]">
          <ng-template cesCell="situacao" let-row>
            <ces-status [tone]="situacao(row.status).tone" [icon]="situacao(row.status).icon">{{ situacao(row.status).label }}</ces-status>
            @if (row.mensagemErro) { <span class="ces-cell-muted">{{ row.mensagemErro }}</span> }
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              <ces-icon-button icon="pi pi-pencil" [label]="'Corrigir linha ' + row.linha" size="icon-sm" (pressed)="corrigir(row)"></ces-icon-button>
              <ces-icon-button icon="pi pi-trash" [label]="'Remover linha ' + row.linha" size="icon-sm" (pressed)="importacao.remover(row)"></ces-icon-button>
            </span>
          </ng-template>
        </ces-data-table>
      }
    </ces-import-drawer>

    <ces-form-drawer [visible]="!!editando()" (visibleChange)="!$event && concluir()" [title]="'Corrigir linha ' + (editando()?.linha ?? '')"
      [description]="editando()?.mensagemErro ?? undefined" size="lg">
      @if (editando(); as row) {
        <ces-form-section title="Ocorrência" icon="pi pi-exclamation-triangle">
          <div class="ces-form-grid">
            <ces-select label="Tipo" [options]="tipos" [(value)]="row.tipo"></ces-select>
            <ces-select label="Atividade" [options]="porNome('atividades', row.atividade)" [(value)]="row.atividade" [filter]="true" [clearable]="true"></ces-select>
            <ces-datepicker label="Data da ocorrência" [showTime]="true" [value]="data(row.dataOcorrencia)" (valueChange)="row.dataOcorrencia = paraTexto($event, true)"></ces-datepicker>
            <ces-datepicker label="Data da comunicação" [showTime]="true" [value]="data(row.dataComunicacao)" (valueChange)="row.dataComunicacao = paraTexto($event, true)"></ces-datepicker>
            <ces-select label="Classificação" [options]="porNome('classificacoes', row.classificacao)" [(value)]="row.classificacao" [filter]="true" [clearable]="true"></ces-select>
            <ces-select label="Processo" [options]="porNome('processos', row.processo)" [(value)]="row.processo" [filter]="true" [clearable]="true"></ces-select>
          </div>
          <ces-text-input label="Descrição" [(value)]="row.descricao"></ces-text-input>
        </ces-form-section>
        <ces-form-section title="Local e responsáveis" icon="pi pi-map-marker">
          <div class="ces-form-grid">
            <ces-select label="Gestor" [options]="comAtual(colaboradores.items(), row.gestor)" [(value)]="row.gestor" [filter]="true" [clearable]="true"
              [loading]="colaboradores.loading()" (search)="colaboradores.search($event)"></ces-select>
            <ces-select label="Base operacional" [options]="porNome('bases', row.baseOperacional)" [(value)]="row.baseOperacional" [filter]="true" [clearable]="true"></ces-select>
            <ces-select label="Equipe" [options]="comAtual(opcoesEquipes(), row.equipe)" [(value)]="row.equipe" [filter]="true" [clearable]="true"
              [loading]="equipes.loading()" (opened)="equipes.open()" (search)="equipes.search($event)"></ces-select>
            <ces-select label="Veículo" [options]="comAtual(opcoesVeiculos(), row.veiculo)" [(value)]="row.veiculo" [filter]="true" [clearable]="true"
              [loading]="veiculos.loading()" (opened)="veiculos.open()" (search)="veiculos.search($event)"></ces-select>
            <ces-text-input label="Local da ocorrência" [(value)]="row.localOcorrencia"></ces-text-input>
            <ces-select label="Telemetria" [options]="evidencias" [(value)]="row.telemetria" [clearable]="true"></ces-select>
            <ces-text-input label="Latitude" [(value)]="row.latitude"></ces-text-input>
            <ces-text-input label="Longitude" [(value)]="row.longitude"></ces-text-input>
            <ces-select label="Filmagem" [options]="evidencias" [(value)]="row.filmagem" [clearable]="true"></ces-select>
          </div>
          <ces-multiselect label="Técnicos de segurança" [options]="comAtuais(colaboradores.items(), row._tecnicosArray ?? [])" [value]="row._tecnicosArray ?? []" (valueChange)="row._tecnicosArray = $event"
            [loading]="colaboradores.loading()" (search)="colaboradores.search($event)" placeholder="Nenhum"></ces-multiselect>
        </ces-form-section>
        <ces-form-section title="Lesão e CAT" icon="pi pi-heart">
          <div class="ces-form-grid">
            <ces-select label="Local da lesão" [options]="porNome('locaisLesao', row.localLesao)" [(value)]="row.localLesao" [filter]="true" [clearable]="true"></ces-select>
            <ces-select label="Natureza da lesão" [options]="porNome('naturezasLesao', row.naturezaLesao)" [(value)]="row.naturezaLesao" [filter]="true" [clearable]="true"></ces-select>
            <ces-select label="Agente causador" [options]="porNome('agentesCausadores', row.agenteCausador)" [(value)]="row.agenteCausador" [filter]="true" [clearable]="true"></ces-select>
            <ces-text-input label="Dias perdidos" [(value)]="row.diasPerdidos"></ces-text-input>
            <ces-select label="Possui CAT" [options]="simNao" [(value)]="row.possuiCat" [clearable]="true"></ces-select>
            <ces-select label="Tipo de CAT" [options]="tiposCat" [(value)]="row.tipoCat" [clearable]="true"></ces-select>
            <ces-text-input label="Número da CAT" [(value)]="row.numeroCat"></ces-text-input>
            <ces-datepicker label="Emissão da CAT" [value]="data(row.dataEmissaoCat)" (valueChange)="row.dataEmissaoCat = paraTexto($event, false)"></ces-datepicker>
          </div>
        </ces-form-section>
        <ces-form-section title="Envolvidos" icon="pi pi-users">
          @for (env of row._envolvidosArray ?? []; track $index) {
            <div class="ces-form-grid">
              <ces-select label="Tipo" [options]="tiposEnvolvido" [(value)]="env.tipo"></ces-select>
              <ces-select label="Papel" [options]="papeis" [(value)]="env.papel"></ces-select>
              @if (env.tipo === 'COLABORADOR') {
                <ces-select label="Colaborador" [options]="comAtual(colaboradores.items(), env.identificacao)" [(value)]="env.identificacao" [filter]="true"
                  [loading]="colaboradores.loading()" (search)="colaboradores.search($event)"></ces-select>
              } @else {
                <ces-text-input label="Nome" [(value)]="env.identificacao"></ces-text-input>
                <ces-text-input label="Função" [(value)]="env.funcao"></ces-text-input>
                <ces-text-input label="Empresa" [(value)]="env.empresa"></ces-text-input>
              }
              <ces-button variant="ghost" size="sm" icon="pi pi-trash" [label]="'Remover envolvido ' + ($index + 1)" (pressed)="removerEnvolvido(row, $index)"></ces-button>
            </div>
          }
          <ces-button variant="ghost" size="sm" icon="pi pi-plus" label="Adicionar envolvido" (pressed)="adicionarEnvolvido(row)"></ces-button>
        </ces-form-section>
      }
      <div drawer-footer class="ces-actions-row">
        <ces-button icon="pi pi-check" label="Concluir correção" (pressed)="concluir()"></ces-button>
      </div>
    </ces-form-drawer>
  `
})
export class EventosImportacaoComponent {
  private readonly sst = inject(SstService);
  private readonly rh = inject(RhService);
  private readonly lookupStore = inject(LookupStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly importacaoService = inject(ImportacaoService);

  readonly visible = model(false);
  readonly lookups = input<EventosLookups | null>(null);
  readonly processado = output<void>();

  protected readonly tipos = opcoesDe(TIPO_EVENTO_LABELS);
  protected readonly evidencias = opcoesDe(REGISTRO_EVIDENCIA_LABELS);
  protected readonly tiposCat = opcoesDe(TIPO_CAT_LABELS);
  protected readonly papeis = opcoesDe(PAPEL_ENVOLVIDO_LABELS);
  protected readonly tiposEnvolvido = opcoesDe(TIPO_ENVOLVIDO_LABELS);
  protected readonly simNao = SIM_NAO;
  protected readonly situacao = cesImportStatus;
  protected readonly chaveLinha = CHAVE_LINHA_IMPORTACAO;
  /** Linha em correção (editada no lugar; `equal` sempre falso para redesenhar após mudar as listas). */
  protected readonly editando = signal<EventoSstImportacaoDTO | null>(null, { equal: () => false });

  protected readonly importacao = importacaoListagem<EventoSstImportacaoDTO>({
    enviar: (arquivo) => this.sst.importarEventos(arquivo),
    confirmar: (eventos) => this.sst.confirmarImportacaoEventos({ eventos }),
    modelo: () => this.importacaoService.baixarModeloCsv('/v1/sst/eventos/importar/modelo', 'modelo-importacao-eventos-sst.csv'),
    aoProcessar: () => this.processado.emit()
  });

  // A planilha usa códigos e nomes: as buscas devolvem o texto que vai para a linha.
  protected readonly colaboradores = new BuscaColaboradores<string>({
    loader: ({ query, cursor }) =>
      this.lookupStore.searchCollaborators({ query, cursor }).pipe(map((p) => ({ ...p, items: p.items.map((i) => ({ value: i.label, label: i.label })) }))),
    destroyRef: this.destroyRef
  });
  protected readonly equipes = new RemoteLookup<EquipeResponse, string>(
    {
      identify: (e) => e.codigo,
      selectedKeys: () => [],
      load: ({ query, page, size }) => this.rh.listarEquipes({ ativo: true, buscaGlobal: query, page, size, sort: 'codigo,asc' })
    },
    this.destroyRef
  );
  protected readonly veiculos = new RemoteLookup<VeiculoResponse, string>(
    {
      identify: (v) => v.placa,
      selectedKeys: () => [],
      load: ({ query, page, size }) => this.rh.listarVeiculos({ ativo: true, buscaGlobal: query, page, size, sort: 'placa,asc' })
    },
    this.destroyRef
  );
  protected readonly opcoesEquipes = computed<CesSelectOption<string>[]>(() => this.equipes.items().map((e) => ({ label: e.codigo, value: e.codigo })));
  protected readonly opcoesVeiculos = computed<CesSelectOption<string>[]>(() => this.veiculos.items().map((v) => ({ label: v.placa, value: v.placa })));

  protected readonly colunas: CesDataTableColumn<EventoSstImportacaoDTO>[] = [
    { key: 'linha', header: 'Linha', type: 'integer', width: '4.5rem' },
    { key: 'tipo', header: 'Tipo', priority: 1, value: (r) => r.tipo, display: (r) => TIPO_EVENTO_LABELS[r.tipo as TipoEventoSst] ?? r.tipo ?? '—' },
    { key: 'ocorrencia', header: 'Ocorrência', value: (r) => r.dataOcorrencia, display: (r) => r.dataOcorrencia || '—' },
    { key: 'atividade', header: 'Atividade', value: (r) => r.atividade, display: (r) => r.atividade || '—' },
    { key: 'local', header: 'Base / equipe / veículo', value: (r) => r.baseOperacional, display: (r) => [r.baseOperacional, r.equipe, r.veiculo].filter(Boolean).join(' · ') || '—' },
    { key: 'gestor', header: 'Gestor', value: (r) => r.gestor, display: (r) => r.gestor || '—' },
    { key: 'descricao', header: 'Descrição', value: (r) => r.descricao, display: (r) => r.descricao || '—' },
    { key: 'situacao', header: 'Situação', type: 'custom', priority: 1, value: (r) => r.status }
  ];

  protected data(texto: string): Date | null {
    return dataDaPlanilha(texto);
  }

  protected paraTexto(data: Date | null, comHora: boolean): string {
    return dataParaPlanilha(data, comHora);
  }

  /** Opções de um catálogo por nome (a planilha casa pelo nome), mantendo o valor atual visível. */
  protected porNome(catalogo: 'atividades' | 'classificacoes' | 'processos' | 'bases' | 'locaisLesao' | 'naturezasLesao' | 'agentesCausadores', atual: string): CesSelectOption<string>[] {
    const itens = (this.lookups()?.[catalogo] ?? []) as { nome: string }[];
    return this.comAtual(itens.map((i) => ({ label: i.nome, value: i.nome })), atual);
  }

  protected comAtual(opcoes: readonly CesSelectOption<string>[], atual: string | null | undefined): CesSelectOption<string>[] {
    return atual && !opcoes.some((o) => o.value === atual) ? [{ label: atual, value: atual }, ...opcoes] : [...opcoes];
  }

  protected comAtuais(opcoes: readonly CesSelectOption<string>[], atuais: readonly string[]): CesSelectOption<string>[] {
    return atuais.reduce((acc, nome) => this.comAtual(acc, nome), [...opcoes]);
  }

  protected corrigir(row: EventoSstImportacaoDTO): void {
    this.startImportEdit(row);
    this.editando.set(row);
  }

  protected concluir(): void {
    const row = this.editando();
    if (!row) return;
    this.finishImportEdit(row);
    this.editando.set(null);
    this.importacao.concluirCorrecao(row);
  }

  protected adicionarEnvolvido(row: EventoSstImportacaoDTO): void {
    row._envolvidosArray = [...(row._envolvidosArray ?? []), { tipo: 'COLABORADOR', papel: 'ACIDENTADO', identificacao: '', funcao: '', empresa: '' }];
    this.editando.set(row);
  }

  protected removerEnvolvido(row: EventoSstImportacaoDTO, indice: number): void {
    row._envolvidosArray = (row._envolvidosArray ?? []).filter((_, i) => i !== indice);
    this.editando.set(row);
  }

  /** Rótulos da planilha → valores dos selects; técnicos e envolvidos viram listas editáveis. */
  startImportEdit(item: EventoSstImportacaoDTO): void {
    const valor = (texto: string, opcoes: { label: string; value: string }[]) =>
      !texto ? texto : (opcoes.find((o) => o.label.toUpperCase() === texto.toUpperCase())?.value ?? texto);
    item.tipo = valor(item.tipo, this.tipos);
    item.telemetria = valor(item.telemetria, this.evidencias);
    item.filmagem = valor(item.filmagem, this.evidencias);
    item.tipoCat = valor(item.tipoCat, this.tiposCat);

    item._tecnicosArray = item.tecnicos ? item.tecnicos.split(',').map((nome) => nome.trim()).filter(Boolean) : [];
    item._envolvidosArray = item.envolvidos
      ? item.envolvidos.split(/\||,/).map((e): Envolvido => {
          const p = e.split('~').map((s) => s.trim());
          const tipo = (p[0] || '').toUpperCase();
          const papel = (p[1] || '').toUpperCase();
          return {
            tipo: this.tiposEnvolvido.find((t) => t.label.toUpperCase() === tipo)?.value || (tipo.includes('TERCEIRO') ? 'TERCEIRO' : 'COLABORADOR'),
            papel: this.papeis.find((o) => o.label.toUpperCase() === papel)?.value || 'ACIDENTADO',
            identificacao: p[2] || '',
            funcao: p[3] || '',
            empresa: p[4] || ''
          };
        })
      : [];
    item.editing = true;
  }

  /** Valores dos selects → rótulos da planilha; listas voltam ao texto da planilha. */
  finishImportEdit(item: EventoSstImportacaoDTO): void {
    const rotulo = (valor: string, opcoes: { label: string; value: string }[]) => (!valor ? valor : (opcoes.find((o) => o.value === valor)?.label ?? valor));
    item.tipo = rotulo(item.tipo, this.tipos);
    item.telemetria = rotulo(item.telemetria, this.evidencias);
    item.filmagem = rotulo(item.filmagem, this.evidencias);
    item.tipoCat = rotulo(item.tipoCat, this.tiposCat);
    item.tecnicos = Array.isArray(item._tecnicosArray) ? item._tecnicosArray.join(', ') : '';
    item.envolvidos = (item._envolvidosArray ?? [])
      .map((e: Envolvido) => {
        const tipo = this.tiposEnvolvido.find((t) => t.value === e.tipo)?.label || e.tipo;
        const papel = this.papeis.find((p) => p.value === e.papel)?.label || e.papel;
        const base = `${tipo}~${papel}~${e.identificacao || ''}`;
        return e.tipo === 'TERCEIRO' ? `${base}~${e.funcao || ''}~${e.empresa || ''}` : base;
      })
      .join('|');
  }
}
