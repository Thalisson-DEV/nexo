import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CesConfirmationService, NotificationService } from '@ces/ui';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SaldoSerieResponse } from '../../../../core/models/almoxarifado.models';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { AuthService } from '../../../../core/services/auth.service';
import { paginaTeste } from '../../../../shared/listagem/listagem-teste';
import { SeriesSelecaoComponent } from './series-selecao.component';

type Tela = {
  digitado: { set(v: string): void };
  adicionarDigitadas(): void;
  problemas(): { numSerie: string; situacao: string; mensagem: string }[];
  darEntrada(numSerie: string): Promise<void>;
  escolherNaLista(v: string[]): void;
  opcoes(): { value: string }[];
  podeDarEntrada(): boolean;
};

const naOrigem = (numSerie: string, extra: Partial<SaldoSerieResponse> = {}) =>
  ({ id: numSerie, numSerie, ativo: true, condicao: 'NOVO', estoque: { id: 'origem', nomeIdentificador: 'CENTRAL' }, material: { id: 'm-med', codigo: 'MED' }, ...extra }) as SaldoSerieResponse;

describe('SeriesSelecaoComponent', () => {
  let almox: Record<string, ReturnType<typeof vi.fn>>;
  let avisos: Record<string, ReturnType<typeof vi.fn>>;
  let confirmar: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    (globalThis as { ResizeObserver?: unknown }).ResizeObserver ??= class {
      observe(): void {}
      unobserve(): void {}
      disconnect(): void {}
    };
    avisos = { success: vi.fn(), warning: vi.fn(), error: vi.fn() };
    confirmar = vi.fn(() => Promise.resolve(true));
    almox = {
      listarSaldosSeriais: vi.fn(() =>
        paginaTeste([naOrigem('MD-1'), naOrigem('MD-2'), naOrigem('MD-3', { reservadaParaSolicitacao: 'outra' }), naOrigem('MD-4', { reservadaParaSolicitacao: 'sol-1' })])
      ),
      verificarSeries: vi.fn(() => of([naOrigem('MD-1'), naOrigem('MD-9', { estoque: { id: 'eq', nomeIdentificador: 'EQUIPE 7' } as never })])),
      registrarEntradaLote: vi.fn(() => of(null))
    };
  });

  afterEach(() => TestBed.resetTestingModule());

  function abrir(permissoes = ['estoque:movimentacao:write'], maximo = 2) {
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        { provide: AlmoxarifadoService, useValue: almox },
        { provide: NotificationService, useValue: avisos },
        { provide: CesConfirmationService, useValue: { confirm: confirmar } },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
      ]
    });
    const fixture = TestBed.createComponent(SeriesSelecaoComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-1');
    fixture.componentRef.setInput('origem', { id: 'origem', nome: 'CENTRAL' });
    fixture.componentRef.setInput('material', { id: 'm-med', codigo: 'MED', descricao: 'Medidor' });
    fixture.componentRef.setInput('maximo', maximo);
    fixture.detectChanges();
    return { fixture, tela: fixture.componentInstance as unknown as Tela, componente: fixture.componentInstance };
  }

  it('a lista traz as séries novas livres da origem e as reservadas para esta solicitação', () => {
    const { tela } = abrir();
    expect(almox['listarSaldosSeriais']).toHaveBeenCalledWith(expect.objectContaining({ idEstoque: 'origem', idMaterial: 'm-med', condicao: 'NOVO', ativo: true }));
    expect(tela.opcoes().map((o) => o.value)).toEqual(['MD-1', 'MD-2', 'MD-4']);
  });

  it('digitar confere cada série: a da origem entra, a de outro estoque fica com o motivo', () => {
    const { tela, componente } = abrir();
    tela.digitado.set('MD-1 MD-9 NOVA-7');
    tela.adicionarDigitadas();

    expect(almox['verificarSeries']).toHaveBeenCalledWith(['MD-1', 'MD-9', 'NOVA-7']);
    expect(componente.selecionadas()).toEqual(['MD-1']);
    expect(tela.problemas()).toEqual([
      { numSerie: 'MD-9', situacao: 'outro-estoque', mensagem: 'Está em EQUIPE 7, não em CENTRAL.' },
      expect.objectContaining({ numSerie: 'NOVA-7', situacao: 'fora-do-sistema' })
    ]);
  });

  it('série fora do sistema ganha entrada avulsa na origem e já fica escolhida', async () => {
    const { tela, componente } = abrir();
    tela.digitado.set('NOVA-7');
    tela.adicionarDigitadas();

    await tela.darEntrada('NOVA-7');

    expect(confirmar).toHaveBeenCalled();
    expect(almox['registrarEntradaLote']).toHaveBeenCalledWith({
      idEstoqueDestino: 'origem',
      itens: [{ idMaterial: 'm-med', quantidade: 1, numSerie: 'NOVA-7', condicao: 'NOVO' }]
    });
    expect(componente.selecionadas()).toEqual(['NOVA-7']);
    expect(tela.problemas()).toEqual([]);
    expect(avisos['success']).toHaveBeenCalledWith('Entrada da série NOVA-7 registrada em CENTRAL.');
  });

  it('sem permissão de movimentar o estoque, não oferece a entrada e explica', () => {
    const { tela, fixture } = abrir([]);
    tela.digitado.set('NOVA-7');
    tela.adicionarDigitadas();
    fixture.detectChanges();

    expect(tela.podeDarEntrada()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('peça a quem cuida das movimentações do estoque');
  });

  it('não passa do total do item', () => {
    const { tela, componente } = abrir(['estoque:movimentacao:write'], 1);
    tela.escolherNaLista(['MD-1', 'MD-2']);
    expect(componente.selecionadas()).toEqual(['MD-1']);
    expect(avisos['warning']).toHaveBeenCalledWith('Este item leva no máximo 1 série.');

    tela.digitado.set('MD-1');
    tela.adicionarDigitadas();
    expect(almox['verificarSeries']).not.toHaveBeenCalled();
  });
});
