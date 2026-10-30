import '@angular/compiler';
import { TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { describe, beforeEach, it, expect, vi } from 'vitest';
import { NotificationService } from '@ces/ui';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { SolicitacaoPendenciasComponent } from './solicitacao-pendencias.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoPendenciasComponent', () => {
  const service = {
    removerPendencia: vi.fn(() => of(undefined)),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [SolicitacaoPendenciasComponent],
      providers: [
        { provide: AlmoxarifadoService, useValue: service },
        { provide: NotificationService, useValue: { success: vi.fn(), error: vi.fn() } },
      ],
    });
  });

  it('não renderiza conteúdo se não houver pendência geral ativa', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciasComponent);
    fixture.componentRef.setInput('detalhe', {
      id: 'sol-1',
      status: 'APROVADO',
      possuiPendencia: false,
      itens: [{ id: 'item-1', material: { id: 'mat-1', codigo: 'MAT', descricao: 'Material' } }],
    });
    fixture.componentRef.setInput('podeEditar', true);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('section')).toBeNull();
  });

  it('exibe pendência da solicitação e permite resolver', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciasComponent);
    fixture.componentRef.setInput('detalhe', {
      id: 'sol-1',
      status: 'APROVADO',
      possuiPendencia: true,
      motivoPendencia: { id: 'mot-1', nome: 'Falta Estoque', descricao: 'Sem estoque', ativo: true },
      justificativaPendencia: 'Aguardando compra urgente',
      itens: [],
    });
    fixture.componentRef.setInput('podeEditar', true);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Pendência Geral da Solicitação');
    expect(text).toContain('Falta Estoque');
    expect(text).toContain('Aguardando compra urgente');

    fixture.componentInstance.remover();
    expect(service.removerPendencia).toHaveBeenCalledWith('sol-1');
  });

  it('não renderiza pendências individuais de itens no banner geral', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciasComponent);
    fixture.componentRef.setInput('detalhe', {
      id: 'sol-1',
      status: 'APROVADO',
      possuiPendencia: true,
      itens: [
        {
          id: 'item-1',
          material: { id: 'mat-1', codigo: 'CABO-01', descricao: 'Cabo' },
          motivoPendencia: { id: 'mot-2', nome: 'Defeito', descricao: 'Material com defeito', ativo: true },
          justificativaPendencia: 'Isolamento rompido',
        },
      ],
    });
    fixture.componentRef.setInput('podeEditar', true);
    fixture.detectChanges();

    // Since motivoPendencia on the solicitation itself is null, the general banner is not rendered
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('section')).toBeNull();
  });

  it('não renderiza banner geral se a solicitação possui motivo mas todos os itens estão sem pendência', () => {
    const fixture = TestBed.createComponent(SolicitacaoPendenciasComponent);
    fixture.componentRef.setInput('detalhe', {
      id: 'sol-1',
      status: 'APROVADO',
      possuiPendencia: false,
      motivoPendencia: { id: 'mot-1', nome: 'Falta Estoque', descricao: 'Sem estoque', ativo: true },
      justificativaPendencia: 'Aguardando compra',
      itens: [
        {
          id: 'item-1',
          material: { id: 'mat-1', codigo: 'CABO-01', descricao: 'Cabo' },
          motivoPendencia: null,
          justificativaPendencia: null,
        },
      ],
    });
    fixture.componentRef.setInput('podeEditar', true);
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('section')).toBeNull();
  });
});
