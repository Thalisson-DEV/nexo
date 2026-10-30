import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { providePrimeNG } from 'primeng/config';
import { of } from 'rxjs';
import { describe, expect, it, vi, beforeEach } from 'vitest';

import { SolicitacaoFinanceiraInspecaoComponent } from './solicitacao-financeira-inspecao.component';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import {
  InspecaoContexto,
  SolicitacaoFinanceira
} from '../../../core/models/financeiro.models';

const mockSolicitacao: SolicitacaoFinanceira = {
  id: 'sol-001',
  numero: 1042,
  status: 'SOLICITADO',
  formulario: {
    id: 'form-001',
    codigo: 'COMPRA_PADRAO',
    titulo: 'Compra Padrão',
    tipoSolicitacao: 'COMPRA',
    padrao: true,
    ativo: true
  },
  valores: {
    descricao: { valor: 'Compra de cabos elétricos', exibicao: 'Compra de cabos elétricos' }
  },
  valor: 1500,
  usuarioSolicitanteId: 'user-001',
  usuarioSolicitante: 'carlos.eduardo',
  nomeColaboradorSolicitante: 'Carlos Eduardo',
  desfecho: {},
  documentos: [],
  acoes: {
    podeEditar: true,
    podeSubmeter: true,
    podeDecidir: true,
    podeCancelar: true,
    podeDesfazerDecisao: false
  },
  ativo: true,
  dataCriacao: '2026-08-20T09:00:00Z'
};

const mockContexto: InspecaoContexto = {
  tipo: 'SOLICITANTE',
  id: 'user-001',
  label: 'Carlos Eduardo',
  subtitulo: 'Colaborador Solicitante',
  icone: 'pi pi-user',
  filtros: {
    usuarioSolicitanteId: 'user-001'
  }
};

describe('SolicitacaoFinanceiraInspecaoComponent', () => {
  let fixture: ComponentFixture<SolicitacaoFinanceiraInspecaoComponent>;
  let component: SolicitacaoFinanceiraInspecaoComponent;

  let financeiroServiceMock: {
    listarSolicitacoes: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    financeiroServiceMock = {
      listarSolicitacoes: vi.fn(() =>
        of({
          content: [mockSolicitacao],
          page: { totalElements: 1, number: 0, size: 40, totalPages: 1 }
        })
      )
    };

    await TestBed.configureTestingModule({
      imports: [SolicitacaoFinanceiraInspecaoComponent],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        providePrimeNG(),
        { provide: FinanceiroService, useValue: financeiroServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SolicitacaoFinanceiraInspecaoComponent);
    component = fixture.componentInstance;
  });

  it('deve ser instanciado com sucesso', () => {
    expect(component).toBeTruthy();
  });

  it('não deve exibir a sidebar se aberta for false', () => {
    component.aberta.set(false);
    fixture.detectChanges();

    const aside = fixture.nativeElement.querySelector('.finance-inspect-sidebar');
    expect(aside).toBeNull();
  });

  it('deve carregar solicitações e exibir cabeçalho ao abrir com contexto', async () => {
    component.contexto.set(mockContexto);
    component.aberta.set(true);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(financeiroServiceMock.listarSolicitacoes).toHaveBeenCalledWith(
      'AMBOS',
      expect.objectContaining({
        usuarioSolicitanteId: 'user-001'
      })
    );

    const titleEl = fixture.nativeElement.querySelector('.finance-inspect-header h2');
    expect(titleEl?.textContent).toContain('Carlos Eduardo');

    const itemEl = fixture.nativeElement.querySelector('.finance-inspect-item');
    expect(itemEl).toBeTruthy();
    expect(itemEl.textContent).toContain('#1042');
  });

  it('deve emitir selecionada ao clicar em um card', async () => {
    component.contexto.set(mockContexto);
    component.aberta.set(true);
    fixture.detectChanges();
    await fixture.whenStable();

    let emitido: SolicitacaoFinanceira | null = null;
    component.selecionada.subscribe((item) => {
      emitido = item;
    });

    const btn = fixture.nativeElement.querySelector('.finance-inspect-item') as HTMLButtonElement;
    btn.click();

    expect(emitido).toEqual(mockSolicitacao);
  });

  it('deve fechar ao chamar fechar()', () => {
    component.aberta.set(true);
    component.contexto.set(mockContexto);
    fixture.detectChanges();

    component['fechar']();
    expect(component.aberta()).toBe(false);
  });
});
