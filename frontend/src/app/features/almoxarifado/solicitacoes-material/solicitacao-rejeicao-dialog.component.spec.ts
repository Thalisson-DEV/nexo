import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { NotificationService } from '@ces/ui';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { paginaTeste } from '../../../shared/listagem/listagem-teste';
import { SolicitacaoRejeicaoDialogComponent } from './solicitacao-rejeicao-dialog.component';

type Tela = {
  idMotivo: { set(v: string | null): void };
  justificativa: { set(v: string): void };
  opcoes(): { label: string; value: string }[];
  confirmar(): void;
  tentou(): boolean;
};

describe('SolicitacaoRejeicaoDialogComponent', () => {
  let almox: Record<string, ReturnType<typeof vi.fn>>;

  async function abrir() {
    TestBed.configureTestingModule({
      providers: [
        provideNoopAnimations(),
        { provide: AlmoxarifadoService, useValue: almox },
        { provide: NotificationService, useValue: { success: vi.fn(), error: vi.fn() } }
      ]
    });
    const fixture = TestBed.createComponent(SolicitacaoRejeicaoDialogComponent);
    fixture.componentRef.setInput('idSolicitacao', 'sol-12345678');
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, tela: fixture.componentInstance as unknown as Tela };
  }

  beforeEach(() => {
    almox = {
      listarMotivosRejeicaoAtivos: vi.fn(() => paginaTeste([{ id: 'm1', nome: 'Sem saldo', descricao: 'Falta no estoque' }])),
      rejeitarSolicitacao: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => TestBed.resetTestingModule());

  it('carrega os motivos ao abrir e exige motivo e justificativa antes de enviar', async () => {
    const { tela } = await abrir();
    expect(tela.opcoes()).toEqual([{ label: 'Sem saldo — Falta no estoque', value: 'm1' }]);
    tela.confirmar();
    expect(tela.tentou()).toBe(true);
    expect(almox['rejeitarSolicitacao']).not.toHaveBeenCalled();
  });

  it('envia a rejeição, fecha e avisa quem abriu', async () => {
    const { tela, fixture } = await abrir();
    const rejeitada = vi.fn();
    fixture.componentInstance.rejeitada.subscribe(rejeitada);
    tela.idMotivo.set('m1');
    tela.justificativa.set('  Material indisponível no central  ');
    tela.confirmar();
    expect(almox['rejeitarSolicitacao']).toHaveBeenCalledWith('sol-12345678', { idMotivoRejeicao: 'm1', justificativa: 'Material indisponível no central' });
    expect(fixture.componentInstance.visible()).toBe(false);
    expect(rejeitada).toHaveBeenCalledWith('sol-12345678');
  });
});
