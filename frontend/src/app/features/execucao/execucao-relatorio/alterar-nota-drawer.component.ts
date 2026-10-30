import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { FormsModule } from '@angular/forms';
import {
  CesAutocompleteComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesDatepickerComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesMultiSelectComponent,
  CesNumberInputComponent,
  CesRowActionsDirective,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService
} from '@ces/ui';

import { LookupStore } from '../../../core/lookup/store/lookup.store';
import { RemoteLookup } from '../../../core/lookup/store/remote-lookup';
import {
  ExecucaoComercialResponse,
  ORIGENS_MATERIAL,
  OperacaoMaterial,
  OrigemMaterial,
  TIPOS_NOTA,
  TipoNota,
  rotuloNota
} from '../../../core/models/execucao.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { MaterialResponse } from '../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ContratoService } from '../../../core/services/contrato.service';
import { ExecucaoService } from '../../../core/services/execucao.service';
import { RhService } from '../../../core/services/rh.service';
import { LinhaAcao, LinhaMaterial, diferencaDaNota, materiaisDaNota, resumoDaDiferenca } from './alterar-nota.utils';

const OPERACOES: CesSelectOption<OperacaoMaterial>[] = [
  { label: 'Aplicado no cliente', value: 'APLICADO' },
  { label: 'Retirado do cliente', value: 'RETIRADO' }
];
const ORIGENS: CesSelectOption<OrigemMaterial>[] = [
  { label: 'Equipe', value: 'EQUIPE' },
  { label: 'Concessionária', value: 'CONCESSIONARIA' }
];
const TIPOS: CesSelectOption<TipoNota>[] = (Object.keys(TIPOS_NOTA) as TipoNota[]).map((tipo) => ({
  label: tipo === 'OCORRENCIA' ? 'Ocorrência comercial' : TIPOS_NOTA[tipo].rotulo,
  value: tipo
}));
const TAMANHO_MOTIVO = 500;
const DATA_HORA = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

/**
 * Corrige uma nota já sincronizada: dados (número, tipo, tipo de serviço, município e
 * data), serviços e materiais. Mostra o que muda antes de salvar; o motivo é
 * obrigatório e vai para a linha do tempo da nota.
 */
@Component({
  selector: 'app-alterar-nota-drawer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    CesAutocompleteComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesCellDirective,
    CesDataTableComponent,
    CesDatepickerComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesMultiSelectComponent,
    CesNumberInputComponent,
    CesRowActionsDirective,
    CesSelectComponent,
    CesStatusComponent,
    CesTextInputComponent,
    CesTextareaComponent
  ],
  template: `
    <ces-form-drawer [(visible)]="visible" [title]="'Alterar ' + titulo()" size="lg" [dirty]="alterado() || !!motivo().trim()"
      description="Corrija qualquer dado da nota. Ela fica marcada como alterada e a mudança entra na linha do tempo."
      discardMessage="A correção desta nota ainda não foi salva e será perdida.">
      <ces-form-section title="Dados da nota" icon="pi pi-file-edit"
        description="Mudar a data recalcula o valor pela tabela do novo dia e leva a nota para o dia certo nas metas.">
        <div class="ces-form-grid">
          <ces-text-input [label]="tipoNota() === 'OCORRENCIA' ? 'Número da ocorrência' : 'Número da nota'" [required]="true"
            [value]="numero()" (valueChange)="numero.set($event)" [maxlength]="100" [error]="erroNumero()"></ces-text-input>
          <ces-select label="Tipo da nota" [options]="tipos" [value]="tipoNota()" (valueChange)="tipoNota.set($event ?? 'COMUM')"
            [description]="descricaoTipo()"></ces-select>
          <ces-select label="Tipo de serviço" [options]="opcoesTiposServico()" [(value)]="idTipoServico" [filter]="true"
            [loading]="carregandoDados()" placeholder="Sem tipo de serviço"></ces-select>
          <ces-select label="Município" [options]="opcoesMunicipios()" [(value)]="idMunicipio" [filter]="true"
            [loading]="carregandoDados()" placeholder="Sem município"></ces-select>
          <ces-datepicker label="Data e hora do lançamento" [showTime]="true" [required]="true" [value]="dataLancamento()"
            (valueChange)="dataLancamento.set($event)" [maxDate]="agora()" [error]="erroData()"></ces-datepicker>
        </div>
        @if (concessionariaForaDaOcorrencia()) {
          <ces-callout tone="danger" title="Material da Concessionária na nota">
            Só a ocorrência comercial aceita material da Concessionária. Remova esses materiais ou mantenha o tipo ocorrência.
          </ces-callout>
        }
      </ces-form-section>

      <ces-form-section title="Serviços" icon="pi pi-wrench" description="Os serviços definem o valor faturado, recalculado pela tabela do dia do lançamento.">
        <div class="ces-form-grid">
          <ces-select label="Serviço" [options]="acoesDisponiveis()" [(value)]="novaAcao" [filter]="true" [clearable]="true"
            [loading]="carregandoAcoes()" placeholder="Escolha o serviço"></ces-select>
        </div>
        <div class="ces-actions-row">
          <ces-button variant="outline" icon="pi pi-plus" label="Adicionar serviço" [disabled]="!novaAcao()" (pressed)="adicionarAcao()"></ces-button>
        </div>
        <ces-data-table caption="Serviços da nota" [searchable]="false" [columns]="colunasAcoes" [rows]="acoes()" [rowKey]="chaveAcao"
          [state]="acoes().length ? 'ready' : 'empty'" stateTitle="Nenhum serviço" stateDescription="A nota fica sem valor faturado.">
          <ng-template cesCell="nome" let-row>
            <span class="linha-nome">
              <span>{{ row.nome }}</span>
              @if (row.nova) { <ces-status tone="info" icon="pi-plus">Novo</ces-status> }
            </span>
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              <ces-icon-button icon="pi pi-trash" [label]="'Remover o serviço ' + row.nome" size="icon-sm" (pressed)="removerAcao(row)"></ces-icon-button>
            </span>
          </ng-template>
        </ces-data-table>
      </ces-form-section>

      <ces-form-section title="Materiais" icon="pi pi-box" description="O saldo do caminhão é ajustado: o que sair da nota volta ao saldo, o que entrar é baixado.">
        @if (substituindo(); as antigo) {
          <ces-callout tone="info" title="Substituindo material">
            Escolha o material certo no lugar de {{ antigo.rotulo }} ({{ antigo.operacao === 'APLICADO' ? 'aplicado' : 'retirado' }}).
            <div class="ces-actions-row"><ces-button variant="ghost" size="sm" label="Desistir da troca" (pressed)="cancelarSubstituicao()"></ces-button></div>
          </ces-callout>
        }
        <div class="ces-form-grid">
          <ces-autocomplete label="Material" placeholder="Código ou descrição" [options]="materiaisBusca.items()" [ngModel]="novoMaterial()"
            (ngModelChange)="escolherMaterial($event)" [loading]="materiaisBusca.loading()" [status]="materiaisBusca.status()" [clearable]="true"
            (search)="materiaisBusca.search($event)" (loadMore)="materiaisBusca.loadNext()" (retry)="materiaisBusca.retry()"></ces-autocomplete>
          <ces-select label="Operação" [options]="operacoes" [value]="novaOperacao()" (valueChange)="escolherOperacao($event)"></ces-select>
          @if (ocorrencia()) {
            <ces-select label="Origem" [options]="origens" [value]="novaOrigem()" (valueChange)="escolherOrigem($event)"
              description="Material da Concessionária não mexe no saldo da equipe."></ces-select>
          }
          @if (materialEscolhido(); as mat) {
            @if (!mat.detalhaSerie) {
              <ces-number-input label="Quantidade" [(value)]="novaQuantidade" [min]="0" [maxFractionDigits]="3" [required]="true"
                [suffix]="mat.unidadeMedida ? ' ' + mat.unidadeMedida : undefined"></ces-number-input>
            } @else if (aplicaDoCaminhao()) {
              <ces-multiselect label="Séries no caminhão" [required]="true" display="chip" [options]="seriesDisponiveis()" [(value)]="seriesEscolhidas"
                [loading]="carregandoSeries()" [placeholder]="seriesDisponiveis().length ? 'Marque as séries aplicadas' : 'Nenhuma série deste material no caminhão'"
                description="Uma linha por série. São as séries novas no saldo da equipe."></ces-multiselect>
            } @else {
              <ces-text-input label="Série retirada" [required]="true" [value]="novaSerie()" (valueChange)="novaSerie.set($event.toUpperCase())"
                placeholder="Digite a série do cliente" description="Material com controle de série: uma por linha."></ces-text-input>
            }
          }
        </div>
        @if (materialEscolhido()?.detalhaSerie && aplicaDoCaminhao()) {
          @if (digitarForaDoSaldo()) {
            <div class="fora-saldo">
              <ces-text-input label="Série fora do saldo" [value]="novaSerie()" (valueChange)="novaSerie.set($event.toUpperCase())"
                placeholder="Digite a série" description="Só se a série aplicada não aparece no caminhão. O saldo da equipe fica negativo e o almoxarifado é avisado."></ces-text-input>
            </div>
          } @else {
            <div class="ces-actions-row">
              <ces-button variant="ghost" size="sm" icon="pi pi-pencil" label="A série não está na lista" (pressed)="digitarForaDoSaldo.set(true)"></ces-button>
            </div>
          }
        }
        @if (erroMaterial(); as mensagem) {
          <ces-callout tone="danger">{{ mensagem }}</ces-callout>
        }
        <div class="ces-actions-row">
          <ces-button variant="outline" icon="pi pi-plus" [label]="substituindo() ? 'Substituir material' : 'Adicionar material'"
            [disabled]="!podeAdicionarMaterial()" (pressed)="adicionarMaterial()"></ces-button>
        </div>
        <ces-data-table caption="Materiais da nota" [searchable]="false" [columns]="colunasMateriais()" [rows]="materiais()" [rowKey]="chaveMaterial"
          [state]="materiais().length ? 'ready' : 'empty'" stateTitle="Nenhum material" stateDescription="A nota fica só com os serviços.">
          <ng-template cesCell="material" let-row>
            <span class="linha-nome">
              <span>{{ row.rotulo }}</span>
              @if (row.nova) { <ces-status tone="info" icon="pi-plus">Novo</ces-status> }
              @if (row.foraDoSaldo) { <ces-status tone="warning" icon="pi-exclamation-triangle">Fora do saldo</ces-status> }
            </span>
          </ng-template>
          <ng-template cesCell="operacao" let-row>
            <ces-status [tone]="row.operacao === 'APLICADO' ? 'brand' : 'neutral'" [icon]="row.operacao === 'APLICADO' ? 'pi-arrow-down' : 'pi-arrow-up'">
              {{ row.operacao === 'APLICADO' ? 'Aplicado' : 'Retirado' }}
            </ces-status>
          </ng-template>
          <ng-template cesCell="quantidade" let-row>
            @if (row.numSerie) {
              {{ row.quantidade }} {{ row.unidade }}
            } @else {
              <ces-number-input [label]="'Quantidade de ' + row.rotulo" [labelVisible]="false" [value]="row.quantidade" [min]="0" [maxFractionDigits]="3"
                [suffix]="row.unidade ? ' ' + row.unidade : undefined" (valueChange)="mudarQuantidade(row, $event)"></ces-number-input>
            }
          </ng-template>
          <ng-template cesRowActions let-row>
            <span class="ces-table-actions">
              <ces-icon-button icon="pi pi-arrow-right-arrow-left" size="icon-sm" (pressed)="inverterOperacao(row)"
                [label]="'Trocar ' + row.rotulo + ' para ' + (row.operacao === 'APLICADO' ? 'retirado' : 'aplicado')"></ces-icon-button>
              <ces-icon-button icon="pi pi-sync" [label]="'Substituir ' + row.rotulo + ' por outro material'" size="icon-sm" (pressed)="substituir(row)"></ces-icon-button>
              <ces-icon-button icon="pi pi-trash" [label]="'Remover ' + row.rotulo" size="icon-sm" (pressed)="removerMaterial(row)"></ces-icon-button>
            </span>
          </ng-template>
        </ces-data-table>
      </ces-form-section>

      <ces-form-section title="Motivo" icon="pi pi-comment" description="Aparece na linha do tempo e no relatório da nota.">
        <ces-textarea label="Motivo da alteração" [required]="true" [value]="motivo()" (valueChange)="motivo.set($event)"
          [maxlength]="tamanhoMotivo" [rows]="3" placeholder="Ex.: material lançado errado; o correto é o cabo 16 mm." [error]="erroMotivo()"></ces-textarea>
      </ces-form-section>

      @if (resumo(); as linhas) {
        <ces-callout [tone]="linhas.length ? 'warning' : 'info'" [title]="linhas.length ? 'O que muda na nota' : 'Nada mudou ainda'">
          @if (linhas.length) {
            <ul class="resumo">
              @for (linha of linhas; track linha) { <li>{{ linha }}</li> }
            </ul>
          } @else {
            Ajuste os dados, os serviços ou os materiais para salvar a alteração.
          }
        </ces-callout>
      }

      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="visible.set(false)"></ces-button>
        <ces-button icon="pi pi-check" label="Salvar alteração" [loading]="salvando()" [disabled]="!alterado()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>
  `,
  styles: `
    .linha-nome { display: inline-flex; align-items: center; gap: 0.5rem; flex-wrap: wrap; }
    .fora-saldo { max-width: 28rem; }
    .resumo { margin: 0; padding-left: 1.1rem; display: grid; gap: 0.25rem; }
  `
})
export class AlterarNotaDrawerComponent {
  private readonly execucoes = inject(ExecucaoService);
  private readonly contratos = inject(ContratoService);
  private readonly almoxarifado = inject(AlmoxarifadoService);
  private readonly lookupStore = inject(LookupStore);
  private readonly notificacoes = inject(NotificationService);
  private readonly rh = inject(RhService);

  readonly visible = model(false);
  readonly execucao = input<ExecucaoComercialResponse | null>(null);
  readonly alterada = output<ExecucaoComercialResponse>();

  protected readonly operacoes = OPERACOES;
  protected readonly origens = ORIGENS;
  protected readonly tipos = TIPOS;
  protected readonly tamanhoMotivo = TAMANHO_MOTIVO;

  protected readonly acoes = signal<LinhaAcao[]>([]);
  protected readonly materiais = signal<LinhaMaterial[]>([]);
  protected readonly motivo = signal('');
  protected readonly numero = signal('');
  protected readonly tipoNota = signal<TipoNota>('COMUM');
  protected readonly idTipoServico = signal<string | null>(null);
  protected readonly idMunicipio = signal<string | null>(null);
  protected readonly dataLancamento = signal<Date | null>(null);
  protected readonly agora = signal(new Date());
  private readonly tiposServico = signal<{ id: string; codigo: string; descricao: string; ativo: boolean }[]>([]);
  private readonly municipios = signal<{ id: string; nome: string }[]>([]);
  protected readonly carregandoDados = signal(false);
  protected readonly tentou = signal(false);
  protected readonly salvando = signal(false);
  protected readonly erroMaterial = signal<string | null>(null);

  protected readonly novaAcao = signal<string | null>(null);
  protected readonly novoMaterial = signal<string | null>(null);
  protected readonly novaOperacao = signal<OperacaoMaterial>('APLICADO');
  protected readonly novaQuantidade = signal<number | null>(1);
  protected readonly novaSerie = signal('');
  protected readonly novaOrigem = signal<OrigemMaterial>('EQUIPE');
  /** Cadastro do material escolhido: diz se tem série e a unidade. */
  protected readonly materialEscolhido = signal<MaterialResponse | null>(null);
  protected readonly seriesDoCaminhao = signal<string[]>([]);
  protected readonly seriesEscolhidas = signal<string[]>([]);
  protected readonly carregandoSeries = signal(false);
  protected readonly digitarForaDoSaldo = signal(false);
  /** Linha que está sendo trocada por outro material (volta se a troca for desfeita). */
  protected readonly substituindo = signal<LinhaMaterial | null>(null);
  /** Séries novas no caminhão por material, para avisar quando uma aplicação fica fora do saldo. */
  private readonly seriesPorMaterial = new Map<string, Promise<string[]>>();

  /** Aplicação de material da equipe com série: escolhe entre as séries do caminhão. */
  protected readonly aplicaDoCaminhao = computed(() => this.novaOperacao() === 'APLICADO' && (!this.ocorrencia() || this.novaOrigem() === 'EQUIPE'));

  /** Séries do caminhão que ainda não estão na nota, mais as que esta alteração tirou (voltam ao saldo). */
  protected readonly seriesDisponiveis = computed<CesSelectOption<string>[]>(() => {
    const id = this.materialEscolhido()?.id;
    const naNota = new Set(this.materiais().filter((m) => m.numSerie).map((m) => m.numSerie!.toUpperCase()));
    const devolvidas = (this.execucao()?.materiais ?? [])
      .filter((m) => m.material?.id === id && m.operacao === 'APLICADO' && m.numSerie && !naNota.has(m.numSerie.toUpperCase()))
      .map((m) => m.numSerie!);
    return [...new Set([...this.seriesDoCaminhao(), ...devolvidas])]
      .filter((sr) => !naNota.has(sr.toUpperCase()))
      .map((sr) => ({ label: sr, value: sr }));
  });

  protected readonly podeAdicionarMaterial = computed(() => {
    const mat = this.materialEscolhido();
    if (!mat || mat.id !== this.novoMaterial()) return false;
    if (!mat.detalhaSerie) return (this.novaQuantidade() ?? 0) > 0;
    return this.aplicaDoCaminhao() ? this.seriesEscolhidas().length > 0 || !!this.novaSerie().trim() : !!this.novaSerie().trim();
  });

  private readonly catalogoAcoes = signal<{ id: string; codigo: string; nome: string }[]>([]);
  protected readonly carregandoAcoes = signal(false);

  protected readonly materiaisBusca = new RemoteLookup<string>({
    loader: ({ query, cursor }) => this.lookupStore.searchMaterials({ query, cursor }),
    selectedValues: () => (this.novoMaterial() ? [this.novoMaterial()!] : []),
    destroyRef: inject(DestroyRef)
  });

  protected readonly titulo = computed(() => {
    const e = this.execucao();
    // "Nota 123" vira "nota 123": o título fica "Alterar nota 123".
    return e ? rotuloNota(e).replace(/^\S+/, (p) => p.toLowerCase()) : 'nota';
  });
  protected readonly ocorrencia = computed(() => this.tipoNota() === 'OCORRENCIA');

  /** Inativos só aparecem se já são os da nota: não dá para escolher um novo. */
  protected readonly opcoesTiposServico = computed<CesSelectOption<string>[]>(() => {
    const atual = this.execucao()?.tipoServico;
    const lista = this.tiposServico().filter((t) => t.ativo || t.id === atual?.id);
    if (atual && !lista.some((t) => t.id === atual.id)) lista.push({ ...atual });
    return lista.map((t) => ({ label: t.codigo ? `${t.codigo} · ${t.descricao}` : t.descricao, value: t.id }));
  });
  protected readonly opcoesMunicipios = computed<CesSelectOption<string>[]>(() => {
    const atual = this.execucao()?.municipio;
    const lista = [...this.municipios()];
    if (atual && !lista.some((m) => m.id === atual.id)) lista.push({ id: atual.id, nome: atual.nome });
    return lista.map((m) => ({ label: m.nome, value: m.id }));
  });

  protected readonly descricaoTipo = computed(() => {
    const e = this.execucao();
    if (!e || this.tipoNota() === e.tipoNota) return undefined;
    if (this.tipoNota() === 'AVULSA') return 'O número atual vira o provisório, à espera do número oficial.';
    if (e.tipoNota === 'AVULSA' && e.aguardandoNumeroOficial) return 'Deixa de esperar o número oficial.';
    return undefined;
  });

  protected readonly concessionariaForaDaOcorrencia = computed(() =>
    !this.ocorrencia() && this.materiais().some((m) => m.origem === 'CONCESSIONARIA'));

  /** Dados da nota que mudaram em relação ao que está gravado: só esses vão ao servidor. */
  private readonly dadosMudados = computed(() => {
    const e = this.execucao();
    if (!e) return null;
    const numero = this.numero().trim();
    const data = this.dataLancamento();
    return {
      referenciaNota: numero && numero !== e.referenciaNota ? numero : undefined,
      tipoNota: this.tipoNota() !== e.tipoNota ? this.tipoNota() : undefined,
      idTipoServico: this.idTipoServico() && this.idTipoServico() !== e.tipoServico?.id ? this.idTipoServico()! : undefined,
      idMunicipio: this.idMunicipio() && this.idMunicipio() !== e.municipio?.id ? this.idMunicipio()! : undefined,
      dataLancamento: data && minutos(data) !== minutos(new Date(e.dataLancamento)) ? data : undefined
    };
  });

  /** Frases do que muda nos dados, no topo da caixa "O que muda na nota". */
  private readonly resumoDados = computed(() => {
    const e = this.execucao();
    const d = this.dadosMudados();
    if (!e || !d) return [];
    const linhas: string[] = [];
    if (d.referenciaNota) linhas.push(`Número: ${e.referenciaNota} → ${d.referenciaNota}`);
    if (d.tipoNota) linhas.push(`Tipo da nota: ${rotuloTipo(e.tipoNota)} → ${rotuloTipo(d.tipoNota)}`);
    if (d.idTipoServico) {
      const novo = this.opcoesTiposServico().find((o) => o.value === d.idTipoServico)?.label;
      const antigo = e.tipoServico ? `${e.tipoServico.codigo} · ${e.tipoServico.descricao}` : 'sem tipo';
      linhas.push(`Tipo de serviço: ${antigo} → ${novo}`);
    }
    if (d.idMunicipio) {
      const novo = this.opcoesMunicipios().find((o) => o.value === d.idMunicipio)?.label;
      linhas.push(`Município: ${e.municipio?.nome ?? 'sem município'} → ${novo}`);
    }
    if (d.dataLancamento) {
      linhas.push(`Data do lançamento: ${DATA_HORA.format(new Date(e.dataLancamento))} → ${DATA_HORA.format(d.dataLancamento)} (valor recalculado)`);
    }
    return linhas;
  });

  /** Serviços que ainda não estão na nota. */
  protected readonly acoesDisponiveis = computed<CesSelectOption<string>[]>(() => {
    const naNota = new Set(this.acoes().map((a) => a.id));
    return this.catalogoAcoes()
      .filter((a) => !naNota.has(a.id))
      .map((a) => ({ label: a.codigo ? `${a.codigo} · ${a.nome}` : a.nome, value: a.id }));
  });

  private readonly diferenca = computed(() => {
    const e = this.execucao();
    return e ? diferencaDaNota(e, this.acoes(), this.materiais()) : null;
  });
  protected readonly alterado = computed(() => {
    const d = this.diferenca();
    const lancamentos = !!d && (d.acoesAdicionadas.length + d.acoesRemovidas.length + d.materiaisAdicionados.length + d.materiaisRemovidos.length) > 0;
    return lancamentos || this.resumoDados().length > 0;
  });
  protected readonly resumo = computed(() => {
    const d = this.diferenca();
    return d ? [...this.resumoDados(), ...resumoDaDiferenca(d)] : null;
  });

  protected readonly colunasAcoes: CesDataTableColumn<LinhaAcao>[] = [
    { key: 'codigo', header: 'Código', priority: 2, value: (a) => a.codigo, display: (a) => a.codigo || '—' },
    { key: 'nome', header: 'Serviço', type: 'custom', priority: 1, value: (a) => a.nome }
  ];
  // Origem (Equipe ou Concessionária) só existe na ocorrência.
  protected readonly colunasMateriais = computed<CesDataTableColumn<LinhaMaterial>[]>(() => [
    { key: 'material', header: 'Material', type: 'custom', priority: 1, value: (m) => m.rotulo },
    { key: 'operacao', header: 'Operação', type: 'custom', priority: 1, value: (m) => m.operacao },
    { key: 'quantidade', header: 'Quantidade', type: 'custom', priority: 1, value: (m) => m.quantidade },
    { key: 'serie', header: 'Série', priority: 2, value: (m) => m.numSerie, display: (m) => m.numSerie || '—' },
    ...(this.ocorrencia()
      ? [{ key: 'origem', header: 'Origem', priority: 3 as const, value: (m: LinhaMaterial) => m.origem, display: (m: LinhaMaterial) => ORIGENS_MATERIAL[m.origem] }]
      : [])
  ]);
  protected readonly chaveAcao = (a: LinhaAcao) => a.id;
  protected readonly chaveMaterial = (m: LinhaMaterial) => m.chave;

  constructor() {
    effect(() => {
      if (!this.visible()) return;
      untracked(() => this.preencher());
    });
  }

  private preencher(): void {
    const e = this.execucao();
    if (!e) return;
    this.acoes.set(e.acoes.filter((a) => a.acao).map((a) => ({ id: a.acao.id, codigo: a.acao.codigo, nome: a.acao.nome, nova: false })));
    this.materiais.set(materiaisDaNota(e));
    this.numero.set(e.referenciaNota);
    this.tipoNota.set(e.tipoNota);
    this.idTipoServico.set(e.tipoServico?.id ?? null);
    this.idMunicipio.set(e.municipio?.id ?? null);
    this.dataLancamento.set(new Date(e.dataLancamento));
    this.agora.set(new Date());
    this.motivo.set('');
    this.tentou.set(false);
    this.erroMaterial.set(null);
    this.substituindo.set(null);
    this.seriesPorMaterial.clear();
    this.limparMaterial();
    this.novaAcao.set(null);
    if (!this.tiposServico().length || !this.municipios().length) {
      this.carregandoDados.set(true);
      Promise.all([
        firstValueFrom(this.execucoes.listarOpcoesTiposServico()).then((tipos) => this.tiposServico.set(tipos)).catch(() => undefined),
        firstValueFrom(this.rh.listarMunicipiosAtivos())
          .then((r) => this.municipios.set(r.content.map((m) => ({ id: m.id, nome: m.nome }))))
          .catch(() => undefined)
      ]).finally(() => this.carregandoDados.set(false));
    }
    if (!this.catalogoAcoes().length) {
      this.carregandoAcoes.set(true);
      this.contratos.listarAcoesAtivas().subscribe({
        next: (r) => {
          this.catalogoAcoes.set(r.content.map((a) => ({ id: a.id, codigo: a.codigo, nome: a.nome })));
          this.carregandoAcoes.set(false);
        },
        error: () => this.carregandoAcoes.set(false)
      });
    }
  }

  protected adicionarAcao(): void {
    const acao = this.catalogoAcoes().find((a) => a.id === this.novaAcao());
    if (!acao) return;
    this.acoes.update((lista) => [...lista, { ...acao, nova: true }]);
    this.novaAcao.set(null);
  }

  protected removerAcao(linha: LinhaAcao): void {
    this.acoes.update((lista) => lista.filter((a) => a.id !== linha.id));
  }

  protected escolherMaterial(id: string | null): void {
    this.novoMaterial.set(id);
    this.materialEscolhido.set(null);
    this.seriesEscolhidas.set([]);
    this.novaSerie.set('');
    this.digitarForaDoSaldo.set(false);
    this.erroMaterial.set(null);
    if (!id) return;
    this.almoxarifado.buscarMaterialPorId(id).subscribe({
      next: (mat) => {
        if (this.novoMaterial() !== id) return;
        this.materialEscolhido.set(mat);
        if (mat.detalhaSerie) void this.carregarSeriesDoCaminhao(id);
      },
      error: (err) => this.erroMaterial.set(getErrorMessage(err))
    });
  }

  protected escolherOperacao(operacao: OperacaoMaterial | null): void {
    this.novaOperacao.set(operacao ?? 'APLICADO');
    this.seriesEscolhidas.set([]);
    this.novaSerie.set('');
    this.digitarForaDoSaldo.set(false);
  }

  protected escolherOrigem(origem: OrigemMaterial | null): void {
    this.novaOrigem.set(origem ?? 'EQUIPE');
    this.escolherOperacao(this.novaOperacao());
  }

  private async carregarSeriesDoCaminhao(idMaterial: string): Promise<void> {
    this.carregandoSeries.set(true);
    try {
      const series = await this.seriesNovasDoCaminhao(idMaterial);
      if (this.novoMaterial() === idMaterial) this.seriesDoCaminhao.set(series);
    } finally {
      this.carregandoSeries.set(false);
    }
  }

  /** Séries NOVAS ativas no estoque da equipe da nota (cache por material durante a edição). */
  private seriesNovasDoCaminhao(idMaterial: string): Promise<string[]> {
    const idEquipe = this.execucao()?.equipe?.id;
    if (!idEquipe) return Promise.resolve([]);
    let pedido = this.seriesPorMaterial.get(idMaterial);
    if (!pedido) {
      pedido = firstValueFrom(this.almoxarifado.listarSaldosSeriais({ idEquipe, idMaterial, condicao: 'NOVO', ativo: true, size: 500 }))
        .then((r) => (r.content ?? []).map((sr) => sr.numSerie))
        .catch(() => []);
      this.seriesPorMaterial.set(idMaterial, pedido);
    }
    return pedido;
  }

  protected adicionarMaterial(): void {
    const mat = this.materialEscolhido();
    if (!mat || !this.podeAdicionarMaterial()) return;
    const origem: OrigemMaterial = this.ocorrencia() ? this.novaOrigem() : 'EQUIPE';
    const operacao = this.novaOperacao();
    const rotulo = `${mat.codigo} · ${mat.descricao}`;
    const unidade = mat.unidadeMedida ?? '';
    const naNota = new Set(this.materiais().filter((m) => m.numSerie).map((m) => m.numSerie!.toUpperCase()));

    let novas: LinhaMaterial[];
    if (mat.detalhaSerie) {
      const digitada = this.novaSerie().trim().toUpperCase();
      const series = [...this.seriesEscolhidas(), ...(digitada ? [digitada] : [])];
      const repetida = series.find((sr) => naNota.has(sr.toUpperCase()));
      if (repetida) {
        this.erroMaterial.set(`A série ${repetida} já está nesta nota.`);
        return;
      }
      const doCaminhao = new Set(this.seriesDisponiveis().map((o) => o.value.toUpperCase()));
      novas = series.map((numSerie) => ({
        chave: crypto.randomUUID(), idMaterial: mat.id, rotulo, unidade, quantidade: 1, operacao, numSerie, origem, nova: true,
        foraDoSaldo: this.aplicaDoCaminhao() && !doCaminhao.has(numSerie.toUpperCase())
      }));
    } else {
      const quantidade = this.novaQuantidade() ?? 0;
      const igual = this.materiais().find((m) => m.idMaterial === mat.id && m.operacao === operacao && m.origem === origem && !m.numSerie);
      if (igual) {
        // Mesmo material, operação e origem: soma na linha que já existe.
        this.materiais.update((lista) => lista.map((m) => (m === igual ? { ...m, quantidade: m.quantidade + quantidade } : m)));
        this.concluirInclusao();
        return;
      }
      novas = [{ chave: crypto.randomUUID(), idMaterial: mat.id, rotulo, unidade, quantidade, operacao, numSerie: null, origem, nova: true }];
    }
    this.materiais.update((lista) => [...lista, ...novas]);
    this.concluirInclusao();
  }

  private concluirInclusao(): void {
    this.erroMaterial.set(null);
    this.substituindo.set(null);
    this.limparMaterial();
  }

  /**
   * Aplicado vira retirado e vice-versa, num clique. Série que passa a ser aplicada e
   * não está no caminhão continua valendo, mas fica marcada como fora do saldo.
   */
  protected async inverterOperacao(linha: LinhaMaterial): Promise<void> {
    const operacao: OperacaoMaterial = linha.operacao === 'APLICADO' ? 'RETIRADO' : 'APLICADO';
    let foraDoSaldo = false;
    if (operacao === 'APLICADO' && linha.numSerie && linha.origem === 'EQUIPE') {
      const series = await this.seriesNovasDoCaminhao(linha.idMaterial);
      const devolvida = (this.execucao()?.materiais ?? []).some((m) => m.operacao === 'APLICADO' && m.numSerie?.toUpperCase() === linha.numSerie!.toUpperCase());
      foraDoSaldo = !devolvida && !series.some((sr) => sr.toUpperCase() === linha.numSerie!.toUpperCase());
    }
    this.materiais.update((lista) => lista.map((m) => (m.chave === linha.chave ? { ...m, operacao, foraDoSaldo } : m)));
  }

  /** Tira a linha e prepara a inclusão do material certo com a mesma operação e quantidade. */
  protected substituir(linha: LinhaMaterial): void {
    const anterior = this.substituindo();
    this.materiais.update((lista) => [...lista.filter((m) => m.chave !== linha.chave), ...(anterior ? [anterior] : [])]);
    this.limparMaterial();
    this.substituindo.set(linha);
    this.novaOperacao.set(linha.operacao);
    this.novaOrigem.set(linha.origem);
    this.novaQuantidade.set(linha.numSerie ? 1 : linha.quantidade);
    this.erroMaterial.set(null);
  }

  protected cancelarSubstituicao(): void {
    const linha = this.substituindo();
    if (linha) this.materiais.update((lista) => [...lista, linha]);
    this.substituindo.set(null);
    this.limparMaterial();
  }

  protected mudarQuantidade(linha: LinhaMaterial, quantidade: number | null): void {
    this.materiais.update((lista) => lista.map((m) => (m.chave === linha.chave ? { ...m, quantidade: quantidade ?? 0 } : m)));
  }

  protected removerMaterial(linha: LinhaMaterial): void {
    this.materiais.update((lista) => lista.filter((m) => m.chave !== linha.chave));
  }

  protected erroNumero(): string | undefined {
    return this.tentou() && !this.numero().trim() ? 'Informe o número.' : undefined;
  }

  protected erroData(): string | undefined {
    const data = this.dataLancamento();
    if (this.tentou() && !data) return 'Informe a data do lançamento.';
    return data && data > new Date() ? 'A data do lançamento não pode ficar no futuro.' : undefined;
  }

  protected erroMotivo(): string | undefined {
    return this.tentou() && !this.motivo().trim() ? 'Escreva o motivo da alteração.' : undefined;
  }

  protected salvar(): void {
    const e = this.execucao();
    this.tentou.set(true);
    if (!e || this.salvando() || !this.alterado() || this.erroMotivo() || this.erroNumero() || this.erroData()) return;
    if (this.concessionariaForaDaOcorrencia()) return;
    if (this.substituindo()) {
      this.erroMaterial.set('Termine a troca de material ou desista dela antes de salvar.');
      return;
    }
    if (this.materiais().some((m) => !(m.quantidade > 0))) {
      this.erroMaterial.set('Há material com quantidade zerada. Remova a linha ou informe a quantidade.');
      return;
    }
    this.salvando.set(true);
    this.execucoes
      .alterarLancamentos(e.id, {
        acoes: this.acoes().map((a) => a.id),
        materiais: this.materiais().map((m) => ({
          idMaterial: m.idMaterial,
          quantidade: m.quantidade,
          operacao: m.operacao,
          numSerie: m.numSerie,
          origem: m.origem
        })),
        motivo: this.motivo().trim(),
        ...this.dadosParaEnviar()
      })
      .subscribe({
        next: (atualizada) => {
          this.salvando.set(false);
          this.visible.set(false);
          this.notificacoes.success(`${rotuloNota(atualizada)} alterada. O saldo da equipe é ajustado em seguida.`);
          this.alterada.emit(atualizada);
        },
        error: (err) => {
          this.salvando.set(false);
          this.notificacoes.error(getErrorMessage(err), { title: 'Não foi possível alterar a nota' });
        }
      });
  }

  private dadosParaEnviar() {
    const d = this.dadosMudados();
    if (!d) return {};
    return {
      ...(d.referenciaNota ? { referenciaNota: d.referenciaNota } : {}),
      ...(d.tipoNota ? { tipoNota: d.tipoNota } : {}),
      ...(d.idTipoServico ? { idTipoServico: d.idTipoServico } : {}),
      ...(d.idMunicipio ? { idMunicipio: d.idMunicipio } : {}),
      ...(d.dataLancamento ? { dataLancamento: d.dataLancamento.toISOString() } : {})
    };
  }

  private limparMaterial(): void {
    this.novoMaterial.set(null);
    this.materialEscolhido.set(null);
    this.seriesEscolhidas.set([]);
    this.digitarForaDoSaldo.set(false);
    this.novaOperacao.set('APLICADO');
    this.novaQuantidade.set(1);
    this.novaSerie.set('');
    this.novaOrigem.set('EQUIPE');
  }
}

/** O datepicker trabalha em minutos: segundos guardados na nota não contam como mudança. */
function minutos(data: Date): number {
  return Math.floor(data.getTime() / 60000);
}

function rotuloTipo(tipo: TipoNota): string {
  return TIPOS.find((t) => t.value === tipo)?.label ?? tipo;
}
