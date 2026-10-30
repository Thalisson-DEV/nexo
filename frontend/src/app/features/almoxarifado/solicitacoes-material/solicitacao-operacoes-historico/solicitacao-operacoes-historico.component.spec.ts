import { TestBed } from '@angular/core/testing';
import { NotificationService } from '@ces/ui';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { OperacaoSolicitacaoComercialResponse } from '../../../../core/models/almoxarifado.models';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { SolicitacaoOperacoesHistoricoComponent, agruparItensDaOperacao } from './solicitacao-operacoes-historico.component';

const operacoes = [
  { id: 'op-antiga-1', tipo: 'ENTREGA', realizadaEm: '2026-10-01T10:00:00Z', idUsuarioOperador: 'u1', itens: [{ id: 'x', codigoMaterial: 'CABO', descricaoMaterial: 'Cabo', quantidade: 2 }] },
  { id: 'op-recente', tipo: 'ESTORNO', realizadaEm: '2026-10-05T10:00:00Z', idUsuarioOperador: 'u1', itens: [{ id: 'y', codigoMaterial: 'TRAFO', descricaoMaterial: 'Trafo', quantidade: 1, numeroSerie: 'TR-9' }] }
] as unknown as OperacaoSolicitacaoComercialResponse[];

describe('SolicitacaoOperacoesHistoricoComponent', () => {
  function abrir(recolhivel?: boolean) {
    TestBed.configureTestingModule({
      providers: [
        { provide: UsuarioLookupService, useValue: { resolverNome: () => 'Operador' } },
        { provide: NotificationService, useValue: { success: vi.fn() } }
      ]
    });
    const fixture = TestBed.createComponent(SolicitacaoOperacoesHistoricoComponent);
    fixture.componentInstance.operacoes = operacoes;
    if (recolhivel !== undefined) fixture.componentInstance.recolhivel = recolhivel;
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => TestBed.resetTestingModule());

  it('no relatório começa recolhido atrás do cabeçalho', () => {
    const el = abrir();
    expect(el.querySelector('[aria-controls="historico-operacoes-comerciais-painel"]')).not.toBeNull();
    expect(el.querySelector('#historico-operacoes-comerciais-painel')?.classList).toContain('hidden');
  });

  it('no detalhe rápido mostra a lista direto, com a movimentação mais recente aberta', () => {
    const el = abrir(false);
    expect(el.querySelector('[aria-controls="historico-operacoes-comerciais-painel"]')).toBeNull();
    expect(el.querySelector('#historico-operacoes-comerciais-painel')?.classList).not.toContain('hidden');
    const abertas = Array.from(el.querySelectorAll('[aria-expanded="true"]')).map((b) => b.textContent);
    expect(abertas).toHaveLength(1);
    expect(abertas[0]).toContain('#op-recen');
    expect(el.textContent).toContain('TR-9');
  });

  it('agrupa as séries do mesmo material numa linha que abre e recolhe', () => {
    const seriado = [
      { id: 'op-seriada', tipo: 'ENTREGA', realizadaEm: '2026-10-06T10:00:00Z', idUsuarioOperador: 'u1', itens: [
        { id: 'a', idMaterial: 'm-med', codigoMaterial: 'MED', descricaoMaterial: 'Medidor', quantidade: 1, numeroSerie: 'MD-10' },
        { id: 'b', idMaterial: 'm-med', codigoMaterial: 'MED', descricaoMaterial: 'Medidor', quantidade: 1, numeroSerie: 'MD-2' },
        { id: 'c', idMaterial: 'm-cabo', codigoMaterial: 'CABO', descricaoMaterial: 'Cabo', quantidade: 30 }
      ] }
    ] as unknown as OperacaoSolicitacaoComercialResponse[];
    TestBed.configureTestingModule({
      providers: [
        { provide: UsuarioLookupService, useValue: { resolverNome: () => 'Operador' } },
        { provide: NotificationService, useValue: { success: vi.fn() } }
      ]
    });
    const fixture = TestBed.createComponent(SolicitacaoOperacoesHistoricoComponent);
    fixture.componentInstance.operacoes = seriado;
    fixture.componentInstance.recolhivel = false;
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    expect(el.querySelectorAll('tbody > tr:not([id])')).toHaveLength(2);
    expect(el.textContent).toContain('2 materiais');
    const botao = el.querySelector<HTMLButtonElement>('tbody button[aria-expanded]')!;
    expect(botao.textContent).toContain('2 séries');
    const linhaSeries = el.querySelector<HTMLElement>('#series-op-seriada-m-med')!;
    expect(linhaSeries.classList).toContain('hidden');
    // Na impressão as séries saem abertas.
    expect(linhaSeries.classList).toContain('print:table-row');

    botao.click();
    fixture.detectChanges();

    expect(botao.getAttribute('aria-expanded')).toBe('true');
    expect(linhaSeries.classList).not.toContain('hidden');
    expect(Array.from(linhaSeries.querySelectorAll('li')).map((li) => li.textContent?.trim())).toEqual(['MD-2', 'MD-10']);
  });

  it('agruparItensDaOperacao soma a quantidade e ordena as séries', () => {
    const [grupo] = agruparItensDaOperacao([
      { id: 'a', idMaterial: 'm', codigoMaterial: 'MED', descricaoMaterial: 'Medidor', quantidade: 1, numeroSerie: 'B-3' },
      { id: 'b', idMaterial: 'm', codigoMaterial: 'MED', descricaoMaterial: 'Medidor', quantidade: 1, numeroSerie: 'B-1' }
    ]);
    expect(grupo).toMatchObject({ codigo: 'MED', quantidade: 2, series: ['B-1', 'B-3'] });
  });
});
