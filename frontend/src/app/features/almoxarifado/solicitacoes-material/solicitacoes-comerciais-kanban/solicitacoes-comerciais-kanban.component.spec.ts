import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { CesConfirmationService, NotificationService } from '@ces/ui';
import { ConfirmationService } from 'primeng/api';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SolicitacaoMaterialSummaryResponse } from '../../../../core/models/solicitacao-material-tecnica.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { paginaTeste } from '../../../../shared/listagem/listagem-teste';
import { LIMITE_ABERTAS, LIMITE_ENCERRADAS, SolicitacoesComerciaisKanbanComponent } from './solicitacoes-comerciais-kanban.component';

const card = (id: string, status: string, extra: Partial<SolicitacaoMaterialSummaryResponse> = {}) =>
  ({
    id: id.padEnd(12, '0'),
    status,
    contexto: 'COMERCIAL',
    dataSolicitacao: '2026-10-01T10:00:00Z',
    dataPrevista: '2026-10-20',
    estoqueOrigemNome: 'CENTRAL',
    estoqueDestinoNome: 'EQUIPE ' + id,
    idRequisitante: 'u1',
    totalItens: 1,
    acoesPermitidas: status === 'PENDENTE' ? ['APROVAR', 'REJEITAR'] : status === 'APROVADO' ? ['ENTREGAR'] : [],
    ...extra
  }) as unknown as SolicitacaoMaterialSummaryResponse;

type Tela = {
  colunas(): { def: { id: string }; total: number; grupos: { id: string; label: string; total: number }[] }[];
  agrupar(v: string): void;
  acaoDeSoltar(c: string): { acao: string } | null;
  iniciarArraste(item: unknown, de: string): void;
  aoSoltar(e: DragEvent, c: string): void;
  aoAgir(item: unknown, acao: string): void;
  analisar(def: unknown): void;
};

describe('SolicitacoesComerciaisKanbanComponent', () => {
  let almox: Record<string, ReturnType<typeof vi.fn>>;
  let porStatus: Record<string, { itens: SolicitacaoMaterialSummaryResponse[]; total?: number }>;

  async function abrir(filtros: Record<string, unknown> = { contexto: 'COMERCIAL' }) {
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        provideRouter([]),
        ConfirmationService,
        { provide: AlmoxarifadoService, useValue: almox },
        { provide: UsuarioLookupService, useValue: { resolverNome: () => 'Ana Souza' } },
        { provide: NotificationService, useValue: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }
      ]
    });
    const fixture = TestBed.createComponent(SolicitacoesComerciaisKanbanComponent);
    fixture.componentRef.setInput('filtros', filtros);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, tela: fixture.componentInstance as unknown as Tela };
  }

  beforeEach(() => {
    localStorage.clear();
    porStatus = {
      PENDENTE: { itens: [card('p1', 'PENDENTE', { possuiPendencia: true, motivosPendencia: ['Falta de material'] } as never)] },
      APROVADO: { itens: [card('a1', 'APROVADO', { dataPrevista: '2020-01-01' }), card('a2', 'APROVADO')] },
      ENTREGUE_PARCIAL: { itens: [card('e1', 'ENTREGUE_PARCIAL')] },
      ENTREGUE: { itens: [card('d1', 'ENTREGUE')], total: 40 },
      REJEITADO: { itens: [] },
      ESTORNADO: { itens: [] }
    };
    almox = {
      listarSolicitacoes: vi.fn((p: { statuses: string[] }) => {
        const faixa = porStatus[p.statuses[0]];
        return paginaTeste(faixa.itens, faixa.total ?? faixa.itens.length);
      }),
      aprovarSolicitacao: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => TestBed.resetTestingModule());

  it('busca cada status separadamente: fila aberta inteira por prazo, encerradas só as recentes', async () => {
    await abrir();
    expect(almox['listarSolicitacoes']).toHaveBeenCalledTimes(6);
    expect(almox['listarSolicitacoes']).toHaveBeenCalledWith(expect.objectContaining({ statuses: ['PENDENTE'], size: LIMITE_ABERTAS, sort: 'dataPrevistaRetirada,asc' }));
    expect(almox['listarSolicitacoes']).toHaveBeenCalledWith(expect.objectContaining({ statuses: ['ENTREGUE'], size: LIMITE_ENCERRADAS, sort: 'dataSolicitacao,desc' }));
  });

  it('atalhos de status da tela limitam as consultas e as colunas', async () => {
    const { tela } = await abrir({ contexto: 'COMERCIAL', statuses: ['APROVADO'] });
    expect(almox['listarSolicitacoes']).toHaveBeenCalledTimes(1);
    expect(tela.colunas().map((c) => c.total)).toEqual([0, 2, 0]);
  });

  it('colunas pela próxima ação, com grupos por situação e totais exatos do backend', async () => {
    const { el, tela } = await abrir();
    expect(tela.colunas().map((c) => [c.def.id, c.total])).toEqual([
      ['APROVACAO', 1],
      ['ENTREGA', 3],
      ['ENCERRADAS', 40]
    ]);
    expect(tela.colunas()[1].grupos.map((g) => g.label)).toEqual(['Aguardando retirada', 'Entregue em parte']);
    expect(el.querySelector('#kb-col-ENTREGA .kb-alerta--perigo')?.textContent).toContain('1 atrasada');
    expect(el.querySelector('.kb-mais')?.textContent).toContain('Ver todas as 40 na tabela');
  });

  it('agrupa por pendência e por prazo de saque', async () => {
    const { tela } = await abrir();
    tela.agrupar('pendencia');
    expect(tela.colunas()[0].grupos.map((g) => g.label)).toEqual(['Falta de material', 'Sem pendência']);
    tela.agrupar('prazo');
    expect(tela.colunas()[1].grupos.map((g) => g.label)).toContain('Atrasadas');
    expect(localStorage.getItem('ces_kanban_comercial_agrupamento')).toBe('prazo');
  });

  it('coluna sem nada mostra o estado vazio', async () => {
    porStatus['PENDENTE'] = { itens: [] };
    const { el } = await abrir();
    expect(el.querySelector('#kb-col-APROVACAO .kb-vazio')?.textContent).toContain('Nada para aprovar');
  });

  it('arrastar de Aprovação para Entrega aprova após confirmar; para Encerradas pede a rejeição', async () => {
    const { tela, fixture } = await abrir();
    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    const rejeitar = vi.fn();
    fixture.componentInstance.rejeitar.subscribe(rejeitar);
    const pendente = porStatus['PENDENTE'].itens[0];

    tela.iniciarArraste(pendente, 'APROVACAO');
    expect(tela.acaoDeSoltar('ENTREGA')?.acao).toBe('APROVAR');
    expect(tela.acaoDeSoltar('ENCERRADAS')?.acao).toBe('REJEITAR');
    tela.aoSoltar(new Event('drop') as DragEvent, 'ENTREGA');
    await fixture.whenStable();
    expect(almox['aprovarSolicitacao']).toHaveBeenCalledWith(pendente.id, {});

    tela.iniciarArraste(pendente, 'APROVACAO');
    tela.aoSoltar(new Event('drop') as DragEvent, 'ENCERRADAS');
    expect(rejeitar).toHaveBeenCalledWith(pendente);
  });

  it('não oferece destino quando a ação não é permitida', async () => {
    const { tela } = await abrir();
    tela.iniciarArraste(porStatus['ENTREGUE'].itens[0], 'ENCERRADAS');
    expect(tela.acaoDeSoltar('APROVACAO')).toBeNull();
    expect(tela.acaoDeSoltar('ENTREGA')).toBeNull();
  });

  it('botão Entregar do cartão abre o detalhe na aba de entrega', async () => {
    const { tela, fixture } = await abrir();
    const abrir$ = vi.fn();
    fixture.componentInstance.abrir.subscribe(abrir$);
    const aprovado = porStatus['APROVADO'].itens[1];
    tela.aoAgir(aprovado, 'ENTREGAR');
    expect(abrir$).toHaveBeenCalledWith({ item: aprovado, aba: 'entrega' });
  });

  it('"Analisar" de uma coluna avisa a tela (que abre o Copiloto) em vez de navegar', async () => {
    const { el, fixture } = await abrir();
    const analisar = vi.fn();
    fixture.componentInstance.analisarEtapa.subscribe(analisar);
    el.querySelector<HTMLButtonElement>('#kb-col-APROVACAO .kb-col__head ces-icon-button button')!.click();
    expect(analisar).toHaveBeenCalledWith(expect.objectContaining({ id: 'APROVACAO', titulo: 'Aprovação' }));
  });

  it('sem permissão de Copiloto a coluna não mostra o botão de análise', async () => {
    const { el, fixture } = await abrir();
    fixture.componentRef.setInput('copiloto', false);
    fixture.detectChanges();
    expect(el.querySelector('#kb-col-APROVACAO .kb-col__head ces-icon-button')).toBeNull();
  });

  it('parcial com saldo encerrado sai de Entrega e vai para Encerradas, sem ação de entrega', async () => {
    porStatus['ENTREGUE_PARCIAL'] = { itens: [card('e1', 'ENTREGUE_PARCIAL'), card('e2', 'ENTREGUE_PARCIAL', { encerrada: true, acoesPermitidas: [] })] };
    const { tela } = await abrir();
    const [, entrega, encerradas] = tela.colunas();
    expect(entrega.total).toBe(3);
    expect(entrega.grupos.find((g) => g.id === 'ENTREGUE_PARCIAL')!.total).toBe(1);
    expect(encerradas.total).toBe(41);
    expect(encerradas.grupos.map((g) => g.label)).toContain('Entregue em parte · saldo encerrado');
  });
});
