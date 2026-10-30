import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { HistoricoImportacaoObraResponse } from '../../../core/models/obras-kit.models';
import { ObrasKitService } from '../../../core/services/obras-kit.service';
import { paginaTeste } from '../../../shared/listagem/listagem-teste';
import { ImportacaoObrasDrawerComponent, INTERVALO_ACOMPANHAMENTO_MS, situacaoImportacao } from './importacao-obras-drawer.component';

const base: HistoricoImportacaoObraResponse = {
  id: 'imp1',
  tipo: 'KITS',
  idContrato: 'c1',
  nomeArquivo: 'kits.xlsx',
  status: 'PROCESSANDO',
  totalLinhas: 0,
  inseridos: 0,
  atualizados: 0,
  erros: 0,
  detalheErros: []
};

type Drawer = {
  arquivo: { set(f: File | null): void };
  idContrato: { set(v: string | null): void };
  enviar(): void;
  atual(): HistoricoImportacaoObraResponse | null;
  erroContrato(): string | undefined;
};

describe('ImportacaoObrasDrawerComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    vi.useFakeTimers();
    servico = {
      importarKits: vi.fn(() => of(base)),
      importarOrcamento: vi.fn(() => of({ ...base, tipo: 'ORCAMENTO' })),
      buscarImportacao: vi.fn(() =>
        of({ ...base, status: 'CONCLUIDA', totalLinhas: 3, inseridos: 2, erros: 1, detalheErros: [{ linha: 4, referencia: 'K9', motivo: 'Material não encontrado' }] })
      ),
      listarImportacoes: vi.fn(() => paginaTeste([])),
      baixarModelo: vi.fn()
    };
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations(), MessageService, ConfirmationService, { provide: ObrasKitService, useValue: servico }]
    });
  });

  afterEach(() => vi.useRealTimers());

  function criar(tipo: 'KITS' | 'ORCAMENTO', exigeContrato: boolean) {
    const fixture = TestBed.createComponent(ImportacaoObrasDrawerComponent);
    fixture.componentRef.setInput('tipo', tipo);
    fixture.componentRef.setInput('exigeContrato', exigeContrato);
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();
    return { fixture, drawer: fixture.componentInstance as unknown as Drawer };
  }

  it('exige contrato na importação de kits e acompanha até concluir', async () => {
    const { fixture, drawer } = criar('KITS', true);
    const concluida = vi.fn();
    fixture.componentInstance.concluida.subscribe(concluida);
    expect(servico['listarImportacoes']).toHaveBeenCalledWith('KITS');

    drawer.arquivo.set(new File(['x'], 'kits.xlsx'));
    drawer.enviar();
    expect(drawer.erroContrato()).toBe('Selecione o contrato.');
    expect(servico['importarKits']).not.toHaveBeenCalled();

    drawer.idContrato.set('c1');
    drawer.enviar();
    expect(servico['importarKits']).toHaveBeenCalledWith('c1', expect.any(File));
    expect(drawer.atual()?.status).toBe('PROCESSANDO');

    await vi.advanceTimersByTimeAsync(INTERVALO_ACOMPANHAMENTO_MS);
    expect(servico['buscarImportacao']).toHaveBeenCalledWith('imp1');
    expect(drawer.atual()?.status).toBe('CONCLUIDA');
    expect(concluida).toHaveBeenCalledOnce();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).ownerDocument.body.textContent).toContain('Material não encontrado');
  });

  it('orçamento não pede contrato', () => {
    const { drawer } = criar('ORCAMENTO', false);
    drawer.arquivo.set(new File(['x'], 'orcamento.csv'));
    drawer.enviar();
    expect(servico['importarOrcamento']).toHaveBeenCalled();
  });

  it('situação diferencia conclusão com erros sem depender só da cor', () => {
    expect(situacaoImportacao('CONCLUIDA', 0).label).toBe('Concluída');
    expect(situacaoImportacao('CONCLUIDA', 2)).toEqual(expect.objectContaining({ label: 'Concluída com erros', tone: 'warning' }));
    expect(situacaoImportacao('FALHOU').icon).toContain('pi-times');
  });
});
