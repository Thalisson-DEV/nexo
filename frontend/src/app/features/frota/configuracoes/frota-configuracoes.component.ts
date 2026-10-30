import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  CesButtonComponent,
  CesCellDirective,
  CesRowActionsDirective,
  CesConfirmationService,
  CesDataTableColumn,
  CesDataTableComponent,
  CesFormDrawerComponent,
  CesIconButtonComponent,
  CesNumberInputComponent,
  CesPageHeaderComponent,
  CesSelectComponent,
  CesSelectOption,
  CesStatusComponent,
  CesSurfaceComponent,
  CesTabItem,
  CesTabPanelDirective,
  CesTabsComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  NotificationService,
  cesUrlParam
} from '@ces/ui';
import { Observable } from 'rxjs';
import { CategoriaCnh, OpcoesFrota, TipoImplemento, TipoVeiculo } from '../../../core/models/frota.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FrotaService } from '../../../core/services/frota.service';
import { acessoFrota } from '../shared/frota-acesso';

type Catalogo = 'veiculo' | 'implemento';
type ItemCatalogo = { id: string; nome: string; descricao: string | null; categoriaCnhMinima?: CategoriaCnh | null; ativo: boolean };

@Component({
  selector: 'app-frota-configuracoes',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCellDirective,
    CesRowActionsDirective,
    CesDataTableComponent,
    CesFormDrawerComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesPageHeaderComponent,
    CesSelectComponent,
    CesStatusComponent,
    CesSurfaceComponent,
    CesTabPanelDirective,
    CesTabsComponent,
    CesTextInputComponent,
    CesTextareaComponent
  ],
  template: `
    <div class="ces-page">
      <ces-page-header
        title="Configurações"
        icon="pi pi-cog"
        [breadcrumb]="[{ label: 'Frota' }, { label: 'Configurações' }]"
        description="Tipos de veículo e de implemento (base dos planos de manutenção e dos checklists) e os limites dos avisos e da conferência de KM."
      ></ces-page-header>

      <ces-tabs [tabs]="abas" [(value)]="aba" ariaLabel="Configurações da frota">
        <ng-template cesTabPanel="veiculo">
          <ces-data-table title="Tipos de veículo" description="Definem a CNH mínima e agrupam planos de manutenção e checklists."
            storageKey="frota-tipos-veiculo" searchPlaceholder="Filtrar nesta lista" [columns]="colunasVeiculo" [rows]="tiposVeiculo()"
            [rowKey]="chave" [state]="estado(tiposVeiculo())" stateTitle="Nenhum tipo de veículo cadastrado"
            stateDescription="Cadastre tipos como Picape, Caminhão cesto ou Moto para classificar os veículos."
            [rowClickable]="acesso.configurar()" (rowActivate)="abrir('veiculo', $event)">
            <ng-container table-actions>
              @if (acesso.configurar()) {
                <ces-button size="sm" icon="pi pi-plus" label="Novo tipo" (pressed)="abrir('veiculo', null)"></ces-button>
              }
            </ng-container>
            <ng-template cesCell="ativo" let-row>
              <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
            </ng-template>
            <ng-template cesRowActions let-row>
              @if (acesso.configurar()) {
                <span class="ces-table-actions">
                  <ces-icon-button icon="pi pi-pencil" [label]="'Editar ' + row.nome" tooltip="Editar tipo" size="icon-sm" (pressed)="abrir('veiculo', row)"></ces-icon-button>
                  <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Reativar ') + row.nome"
                    [tooltip]="row.ativo ? 'Desativar tipo' : 'Reativar tipo'" size="icon-sm" (pressed)="alternar('veiculo', row)"></ces-icon-button>
                </span>
              }
            </ng-template>
          </ces-data-table>
        </ng-template>

        <ng-template cesTabPanel="implemento">
          <ces-data-table title="Tipos de implemento" description="Classificam os equipamentos acopláveis, como cesto aéreo e guindauto."
            storageKey="frota-tipos-implemento" searchPlaceholder="Filtrar nesta lista" [columns]="colunasImplemento" [rows]="tiposImplemento()"
            [rowKey]="chave" [state]="estado(tiposImplemento())" stateTitle="Nenhum tipo de implemento cadastrado"
            stateDescription="Cadastre os tipos antes de incluir os implementos."
            [rowClickable]="acesso.configurar()" (rowActivate)="abrir('implemento', $event)">
            <ng-container table-actions>
              @if (acesso.configurar()) {
                <ces-button size="sm" icon="pi pi-plus" label="Novo tipo" (pressed)="abrir('implemento', null)"></ces-button>
              }
            </ng-container>
            <ng-template cesCell="ativo" let-row>
              <ces-status [tone]="row.ativo ? 'success' : 'neutral'" [emphasis]="row.ativo ? 'soft' : 'outline'">{{ row.ativo ? 'Ativo' : 'Inativo' }}</ces-status>
            </ng-template>
            <ng-template cesRowActions let-row>
              @if (acesso.configurar()) {
                <span class="ces-table-actions">
                  <ces-icon-button icon="pi pi-pencil" [label]="'Editar ' + row.nome" tooltip="Editar tipo" size="icon-sm" (pressed)="abrir('implemento', row)"></ces-icon-button>
                  <ces-icon-button [icon]="row.ativo ? 'pi pi-ban' : 'pi pi-replay'" [label]="(row.ativo ? 'Desativar ' : 'Reativar ') + row.nome"
                    [tooltip]="row.ativo ? 'Desativar tipo' : 'Reativar tipo'" size="icon-sm" (pressed)="alternar('implemento', row)"></ces-icon-button>
                </span>
              }
            </ng-template>
          </ces-data-table>
        </ng-template>

        <ng-template cesTabPanel="parametros">
          <ces-surface>
            <section class="ces-sheet-section" aria-labelledby="parametros-titulo">
            <h2 id="parametros-titulo"><i class="pi pi-sliders-h" aria-hidden="true"></i> Avisos e conferência de KM</h2>
            <div class="ces-form-grid">
              <ces-text-input label="Faixas de aviso de vencimento (dias)" [(value)]="faixas" [disabled]="!acesso.configurar()"
                description="Separadas por vírgula. Ex.: 30, 15, 7, 0 — 0 avisa no dia do vencimento." [error]="erroFaixas()"></ces-text-input>
              <ces-number-input label="Limite de km por dia" [(value)]="limiteKmDia" [min]="1" [max]="5000" suffix=" km"
                [disabled]="!acesso.configurar()" description="Acima disso, a média diária entre duas leituras marca a leitura para conferência."></ces-number-input>
            </div>
            @if (acesso.configurar()) {
              <div class="ces-actions-row ces-actions-row--separated">
                <ces-button icon="pi pi-check" label="Salvar parâmetros" [loading]="salvandoParametros()" (pressed)="salvarParametros()"></ces-button>
              </div>
            }
            </section>
          </ces-surface>
        </ng-template>
      </ces-tabs>
    </div>

    <ces-form-drawer [(visible)]="formularioAberto" [title]="(emEdicao() ? 'Editar ' : 'Novo ') + (catalogo() === 'veiculo' ? 'tipo de veículo' : 'tipo de implemento')"
      [description]="emEdicao() ? emEdicao()!.nome : undefined" [dirty]="!!nome().trim() && nome() !== (emEdicao()?.nome ?? '')"
      [loading]="salvando()" size="sm">
      <div class="ces-form-stack">
        <ces-text-input label="Nome" [required]="true" [(value)]="nome" [maxlength]="80" [error]="tentouSalvar() && !nome().trim() ? 'Informe o nome.' : undefined"></ces-text-input>
        <ces-textarea label="Descrição" [(value)]="descricao" [rows]="2" [maxlength]="255" [showOptional]="true"></ces-textarea>
        @if (catalogo() === 'veiculo') {
          <ces-select label="CNH mínima para conduzir" [options]="categorias()" [(value)]="categoria" [clearable]="true" [showOptional]="true"
            description="Impede vincular condutor com categoria insuficiente."></ces-select>
        }
      </div>
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Cancelar" (pressed)="formularioAberto.set(false)"></ces-button>
        <ces-button icon="pi pi-check" [label]="emEdicao() ? 'Salvar alterações' : 'Cadastrar tipo'" [loading]="salvando()" (pressed)="salvar()"></ces-button>
      </div>
    </ces-form-drawer>
  `
})
export class FrotaConfiguracoesComponent {
  private readonly frota = inject(FrotaService);
  private readonly notificacoes = inject(NotificationService);
  private readonly confirmacao = inject(CesConfirmationService);
  protected readonly acesso = acessoFrota();

  protected readonly abas: CesTabItem[] = [
    { value: 'veiculo', label: 'Tipos de veículo', icon: 'pi pi-car' },
    { value: 'implemento', label: 'Tipos de implemento', icon: 'pi pi-link' },
    { value: 'parametros', label: 'Parâmetros', icon: 'pi pi-sliders-h' }
  ];
  /** Aba na URL (`?aba=implemento`, `?aba=parametros`); catálogos pequenos ficam em modo cliente. */
  protected readonly aba = cesUrlParam({ values: ['veiculo', 'implemento', 'parametros'], default: 'veiculo' });

  protected readonly tiposVeiculo = signal<TipoVeiculo[]>([]);
  protected readonly tiposImplemento = signal<TipoImplemento[]>([]);
  protected readonly carregando = signal(false);
  protected readonly opcoes = signal<OpcoesFrota | null>(null);

  protected readonly formularioAberto = signal(false);
  protected readonly catalogo = signal<Catalogo>('veiculo');
  protected readonly emEdicao = signal<ItemCatalogo | null>(null);
  protected readonly nome = signal('');
  protected readonly descricao = signal('');
  protected readonly categoria = signal<CategoriaCnh | null>(null);
  protected readonly salvando = signal(false);
  protected readonly tentouSalvar = signal(false);

  protected readonly faixas = signal('');
  protected readonly limiteKmDia = signal<number | null>(null);
  protected readonly salvandoParametros = signal(false);

  protected readonly categorias = computed<CesSelectOption<CategoriaCnh>[]>(() =>
    (this.opcoes()?.categoriasCnh ?? []).map((c) => ({ label: c.rotulo, value: c.valor }))
  );
  protected readonly faixasConvertidas = computed(() =>
    this.faixas()
      .split(',')
      .map((f) => f.trim())
      .filter((f) => f !== '')
      .map(Number)
  );
  protected readonly erroFaixas = computed(() => {
    const lista = this.faixasConvertidas();
    if (!lista.length) return 'Informe ao menos uma faixa.';
    if (lista.some((n) => !Number.isInteger(n) || n < 0 || n > 365)) return 'Use números inteiros de 0 a 365.';
    return undefined;
  });

  protected readonly colunasVeiculo: CesDataTableColumn<TipoVeiculo>[] = [
    { key: 'nome', header: 'Nome', priority: 1 },
    { key: 'descricao', header: 'Descrição', display: (t) => t.descricao ?? '' },
    { key: 'categoriaCnhMinima', header: 'CNH mínima', display: (t) => t.categoriaCnhMinima ?? '—' },
    { key: 'ativo', header: 'Situação', type: 'custom', value: (t) => t.ativo, display: (t) => (t.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly colunasImplemento: CesDataTableColumn<TipoImplemento>[] = [
    { key: 'nome', header: 'Nome', priority: 1 },
    { key: 'descricao', header: 'Descrição', display: (t) => t.descricao ?? '' },
    { key: 'ativo', header: 'Situação', type: 'custom', value: (t) => t.ativo, display: (t) => (t.ativo ? 'Ativo' : 'Inativo') }
  ];
  protected readonly chave = (t: { id: string }) => t.id;

  constructor() {
    this.frota.opcoes().subscribe({ next: (o) => this.opcoes.set(o), error: () => undefined });
    this.frota.parametros().subscribe({
      next: (p) => {
        this.faixas.set(p.faixasAlertaDias.join(', '));
        this.limiteKmDia.set(p.limiteKmDia);
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
    this.carregar();
  }

  protected estado(lista: unknown[]): 'loading' | 'ready' | 'empty' {
    return this.carregando() ? 'loading' : lista.length ? 'ready' : 'empty';
  }

  protected carregar(): void {
    this.carregando.set(true);
    this.frota.tiposVeiculo().subscribe({
      next: (p) => this.tiposVeiculo.set(p.content),
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
    this.frota.tiposImplemento().subscribe({
      next: (p) => {
        this.tiposImplemento.set(p.content);
        this.carregando.set(false);
      },
      error: (e) => {
        this.carregando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected abrir(catalogo: Catalogo, item: ItemCatalogo | null): void {
    this.catalogo.set(catalogo);
    this.emEdicao.set(item);
    this.nome.set(item?.nome ?? '');
    this.descricao.set(item?.descricao ?? '');
    this.categoria.set(item?.categoriaCnhMinima ?? null);
    this.tentouSalvar.set(false);
    this.formularioAberto.set(true);
  }

  protected salvar(): void {
    this.tentouSalvar.set(true);
    if (!this.nome().trim()) return;
    const descricao = this.descricao().trim() || null;
    const id = this.emEdicao()?.id;
    const operacao: Observable<unknown> =
      this.catalogo() === 'veiculo'
        ? this.frota.salvarTipoVeiculo({ nome: this.nome().trim(), descricao, categoriaCnhMinima: this.categoria() }, id)
        : this.frota.salvarTipoImplemento({ nome: this.nome().trim(), descricao }, id);
    this.salvando.set(true);
    operacao.subscribe({
      next: () => {
        this.salvando.set(false);
        this.formularioAberto.set(false);
        this.notificacoes.success(`Tipo ${this.nome().trim()} salvo.`);
        this.carregar();
      },
      error: (e) => {
        this.salvando.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }

  protected async alternar(catalogo: Catalogo, item: ItemCatalogo): Promise<void> {
    const ok = await this.confirmacao.confirm({
      title: item.ativo ? 'Desativar tipo' : 'Reativar tipo',
      message: item.ativo
        ? `${item.nome} deixa de aparecer para novos cadastros. Os registros que já usam o tipo continuam com ele.`
        : `${item.nome} volta a aparecer para novos cadastros.`,
      confirmLabel: item.ativo ? 'Desativar' : 'Reativar',
      variant: item.ativo ? 'destructive' : 'default'
    });
    if (!ok) return;
    const operacao: Observable<void> =
      catalogo === 'veiculo' ? this.frota.alternarTipoVeiculo(item as TipoVeiculo) : this.frota.alternarTipoImplemento(item as TipoImplemento);
    operacao.subscribe({
      next: () => {
        this.notificacoes.success(item.ativo ? `${item.nome} desativado.` : `${item.nome} reativado.`);
        this.carregar();
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected salvarParametros(): void {
    const limite = this.limiteKmDia();
    if (this.erroFaixas() || !limite) {
      this.notificacoes.warning('Revise as faixas de aviso e o limite de km por dia.');
      return;
    }
    this.salvandoParametros.set(true);
    this.frota.atualizarParametros({ faixasAlertaDias: this.faixasConvertidas(), limiteKmDia: limite }).subscribe({
      next: (p) => {
        this.salvandoParametros.set(false);
        this.faixas.set(p.faixasAlertaDias.join(', '));
        this.notificacoes.success('Parâmetros salvos.');
      },
      error: (e) => {
        this.salvandoParametros.set(false);
        this.notificacoes.error(getErrorMessage(e));
      }
    });
  }
}
