import { Component, ViewChild, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { DialogModule } from 'primeng/dialog';
import { Subject, catchError, map, of, switchMap } from 'rxjs';
import { CesButtonComponent, CesContentState, CesIconButtonComponent, CesStatusComponent, CesTone } from '@ces/ui';
import {
  CesCellDirective,
  CesDataTableColumn,
  CesDataTableComponent,
  CesLazyLoadEvent,
  CesRowActionsDirective,
  CesRowDetailDirective,
} from '@ces/ui/data';

import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { PageResponseSaldoResponse, SaldoResponse } from '../../../core/models/almoxarifado.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { MovimentacoesMaterialDialogComponent } from '../movimentacoes-material-dialog/movimentacoes-material-dialog.component';
import { SaldoSeriaisPainelComponent } from '../saldo-seriais-painel/saldo-seriais-painel.component';

const ORDENACAO_PADRAO = 'material.descricao,asc';

/**
 * Saldo de um estoque num diálogo, no mesmo padrão do relatório de saldo: um material por linha
 * e, no material com série, os números de série na própria linha (sem abrir outro diálogo).
 */
@Component({
  selector: 'app-saldo-estoque-dialog',
  standalone: true,
  imports: [
    DecimalPipe,
    DialogModule,
    CesButtonComponent,
    CesIconButtonComponent,
    CesStatusComponent,
    CesDataTableComponent,
    CesCellDirective,
    CesRowActionsDirective,
    CesRowDetailDirective,
    MovimentacoesMaterialDialogComponent,
    SaldoSeriaisPainelComponent,
  ],
  templateUrl: './saldo-estoque-dialog.component.html',
  styleUrl: './saldo-estoque-dialog.component.css',
})
export class SaldoEstoqueDialogComponent {
  private readonly almoxarifadoService = inject(AlmoxarifadoService);
  private readonly router = inject(Router);

  @ViewChild('movimentacoesDialog') movimentacoesDialog!: MovimentacoesMaterialDialogComponent;

  visible = false;
  estoqueId: string | null = null;
  estoqueNome = '';

  readonly saldos = signal<SaldoResponse[]>([]);
  readonly totalRecords = signal(0);
  readonly negativeCount = computed(() => this.saldos().filter((saldo) => saldo.negativo).length);
  protected readonly estado = signal<CesContentState>('loading');
  protected readonly erro = signal<string | undefined>(undefined);
  protected readonly busca = signal('');
  protected readonly pagina = signal(0);
  protected readonly tamanho = signal(10);

  protected readonly colunas: CesDataTableColumn<SaldoResponse>[] = [
    { key: 'codigo', header: 'Código', priority: 1, width: '7rem', value: (s) => s.material.codigo, sortField: 'material.codigo' },
    { key: 'descricao', header: 'Material', type: 'custom', priority: 1, value: (s) => s.material.descricao, sortField: 'material.descricao' },
    { key: 'unidade', header: 'Un.', priority: 2, width: '4rem', value: (s) => s.material.unidadeMedida, sortable: false },
    { key: 'condicao', header: 'Condição', type: 'custom', priority: 2, width: '7rem', value: (s) => s.condicao, sortable: false },
    { key: 'quantidade', header: 'Quantidade', type: 'custom', priority: 1, width: '8rem', value: (s) => s.quantidade, sortField: 'quantidade' },
  ];
  protected readonly chaveSaldo = (saldo: SaldoResponse) => saldo.id;
  protected readonly rotuloSaldo = (saldo: SaldoResponse) => `${saldo.material.codigo} ${saldo.material.descricao}`;
  protected readonly ehSeriado = (saldo: SaldoResponse) => !!saldo.material.detalhaSerie;

  private readonly cargas = new Subject<CesLazyLoadEvent>();

  constructor() {
    this.cargas
      .pipe(
        switchMap((evento) =>
          this.almoxarifadoService.listarSaldos(this.parametros(evento)).pipe(
            map((res): PageResponseSaldoResponse | { erro: string } => res),
            catchError((err) => of({ erro: getErrorMessage(err) || 'Não foi possível carregar o saldo.' })),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((res) => {
        if ('erro' in res) {
          this.erro.set(res.erro);
          this.estado.set('error');
          return;
        }
        this.saldos.set(res.content);
        this.totalRecords.set(res.page.totalElements);
        this.erro.set(undefined);
        this.estado.set('ready');
      });
  }

  show(estoqueId: string, estoqueNome: string) {
    this.estoqueId = estoqueId;
    this.estoqueNome = estoqueNome;
    this.saldos.set([]);
    this.totalRecords.set(0);
    this.busca.set('');
    this.pagina.set(0);
    this.estado.set('loading');
    this.visible = true;
    // A tabela pede a primeira página (lazyLoad) assim que aparece.
  }

  hide() {
    this.visible = false;
  }

  protected onLazyLoad(evento: CesLazyLoadEvent) {
    if (!this.estoqueId) return;
    if (!this.saldos().length) this.estado.set('loading');
    this.cargas.next(evento);
  }

  protected abrirMovimentacoes(saldo: SaldoResponse) {
    if (this.estoqueId && saldo.material.id) {
      this.movimentacoesDialog.show(this.estoqueId, saldo.material.id, saldo.material.descricao);
    }
  }

  /** O relatório completo (resumo, recortes e impressão) abre em outra aba. */
  protected abrirRelatorio() {
    if (!this.estoqueId) return;
    const url = this.router.serializeUrl(this.router.createUrlTree(['/almoxarifado/saldos/relatorio'], { queryParams: { idEstoque: this.estoqueId } }));
    window.open(url, '_blank', 'noopener');
  }

  protected tomCondicao(condicao: string | undefined): CesTone {
    return condicao === 'SUCATA' ? 'warning' : 'neutral';
  }

  protected rotuloCondicao(condicao: string | undefined): string {
    if (!condicao) return '—';
    return condicao.charAt(0) + condicao.slice(1).toLowerCase();
  }

  private parametros(evento: CesLazyLoadEvent): Record<string, unknown> {
    const regra = evento.sort[0];
    return {
      idEstoque: this.estoqueId,
      page: evento.page,
      size: evento.size,
      sort: regra ? `${regra.key},${regra.direction}` : ORDENACAO_PADRAO,
      buscaGlobal: evento.query.trim() || undefined,
    };
  }
}
