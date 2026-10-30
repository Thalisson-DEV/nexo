import { TestBed } from '@angular/core/testing';
import { Confirmation, ConfirmationService } from 'primeng/api';

import { CesConfirmationService } from './confirmation.service';

describe('CesConfirmationService', () => {
  it('resolve true e configura uma ação destrutiva de forma semântica', async () => {
    let request: Confirmation | undefined;
    const confirmations = {
      confirm: vi.fn((confirmation: Confirmation) => {
        request = confirmation;
        return confirmations;
      })
    };
    TestBed.configureTestingModule({
      providers: [
        CesConfirmationService,
        { provide: ConfirmationService, useValue: confirmations }
      ]
    });

    const result = TestBed.inject(CesConfirmationService).confirm({
      title: 'Excluir material',
      message: 'Esta ação não pode ser desfeita.',
      variant: 'destructive'
    });
    request?.accept?.();

    await expect(result).resolves.toBe(true);
    expect(confirmations.confirm).toHaveBeenCalledWith(
      expect.objectContaining({
        header: 'Excluir material',
        acceptLabel: 'Excluir',
        rejectLabel: 'Cancelar',
        acceptButtonProps: expect.objectContaining({ severity: 'danger' })
      })
    );
  });

  it('resolve false quando a confirmação é rejeitada', async () => {
    let request: Confirmation | undefined;
    const confirmations = {
      confirm: (confirmation: Confirmation) => {
        request = confirmation;
        return confirmations;
      }
    };
    TestBed.configureTestingModule({
      providers: [
        CesConfirmationService,
        { provide: ConfirmationService, useValue: confirmations }
      ]
    });

    const result = TestBed.inject(CesConfirmationService).confirm({
      title: 'Continuar operação',
      message: 'Deseja continuar?'
    });
    request?.reject?.();

    await expect(result).resolves.toBe(false);
  });
});
