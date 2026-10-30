import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { MovimentacoesMaterialDialogComponent } from './movimentacoes-material-dialog.component';

const operacao = (id: string, tipo: string, origem?: string, destino?: string) => ({
  idReferenciaOperacao: id,
  tipo,
  dataHora: '2026-10-10T12:00:00Z',
  operadorNome: 'Fulano',
  estoqueOrigem: origem ? { id: origem, nomeIdentificador: `Estoque ${origem}` } : undefined,
  estoqueDestino: destino ? { id: destino, nomeIdentificador: `Estoque ${destino}` } : undefined,
});

describe('MovimentacoesMaterialDialogComponent', () => {
  const listarMovimentacoes = vi.fn();
  const buscarOperacaoPorIdReferencia = vi.fn();

  beforeEach(async () => {
    listarMovimentacoes.mockReset();
    buscarOperacaoPorIdReferencia.mockReset();
    listarMovimentacoes.mockReturnValue(
      of({
        content: [operacao('op-entrada-123', 'TRANSFERENCIA', 'central', 'eq'), operacao('op-saida-456', 'ENTREGA', 'eq', 'cliente')],
        page: { number: 0, size: 25, totalElements: 2, totalPages: 1 },
      }),
    );
    buscarOperacaoPorIdReferencia.mockReturnValue(
      of({
        idReferenciaOperacao: 'op-entrada-123',
        itens: [
          { id: 'i1', quantidade: 1, numSerie: 'SN-2', material: { id: 'mat-1', descricao: 'Medidor', unidadeMedida: 'UN' } },
          { id: 'i2', quantidade: 1, numSerie: 'SN-1', material: { id: 'mat-1', descricao: 'Medidor', unidadeMedida: 'UN' } },
          { id: 'i3', quantidade: 5, material: { id: 'mat-2', descricao: 'Outro' } },
        ],
      }),
    );

    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MovimentacoesMaterialDialogComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        MessageService,
        { provide: AlmoxarifadoService, useValue: { listarMovimentacoes, buscarOperacaoPorIdReferencia } },
      ],
    }).compileComponents();
  });

  async function abrir() {
    const fixture = TestBed.createComponent(MovimentacoesMaterialDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.show('eq', 'mat-1', 'Medidor');
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('consulta o histórico do material no estoque, do mais recente para o mais antigo', async () => {
    await abrir();
    expect(listarMovimentacoes).toHaveBeenCalledWith(
      expect.objectContaining({ idEstoque: 'eq', idMaterial: 'mat-1', page: 0, sort: 'dataHora,desc' }),
    );
  });

  it('indica o sentido da operação para o estoque consultado', async () => {
    const fixture = await abrir();
    const tela = fixture.componentInstance as any;
    expect(tela.sentido(operacao('a', 'TRANSFERENCIA', 'central', 'eq'))).toEqual(
      expect.objectContaining({ rotulo: 'Entrada', contraparte: 'de Estoque central' }),
    );
    expect(tela.sentido(operacao('b', 'ENTREGA', 'eq', 'cliente'))).toEqual(
      expect.objectContaining({ rotulo: 'Saída', contraparte: 'para Estoque cliente' }),
    );
  });

  it('filtra por tipo e período voltando para a primeira página', async () => {
    const fixture = await abrir();
    const tela = fixture.componentInstance as any;
    listarMovimentacoes.mockClear();

    tela.tipo.set('ENTREGA');
    tela.dataFim.set(new Date(2026, 9, 10));
    fixture.detectChanges();
    await fixture.whenStable();

    const params = listarMovimentacoes.mock.calls.at(-1)![0];
    expect(params).toEqual(expect.objectContaining({ tipo: 'ENTREGA', page: 0 }));
    expect(new Date(params.dataFim).getHours()).toBe(23);
  });

  it('expande a operação com a quantidade e as séries movidas do material', async () => {
    const fixture = await abrir();
    document.body.querySelector<HTMLButtonElement>('.ces-data-table__expander')!.click();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(buscarOperacaoPorIdReferencia).toHaveBeenCalledWith('op-entrada-123');
    const detalhe = document.body.querySelector('app-movimentacao-material-itens')!;
    expect(detalhe.textContent).toContain('2 números de série');
    expect(Array.from(detalhe.querySelectorAll('.grade__numero')).map((e) => e.textContent)).toEqual(['SN-1', 'SN-2']);
    expect(detalhe.textContent).not.toContain('Outro');
  });
});
