import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';

import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  it('mapeia warning para warn e aplica título e duração padrão', () => {
    const messages = { add: vi.fn(), clear: vi.fn() };
    TestBed.configureTestingModule({
      providers: [NotificationService, { provide: MessageService, useValue: messages }]
    });

    TestBed.inject(NotificationService).warning('A conexão está instável.');

    expect(messages.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'warn',
        summary: 'Atenção',
        detail: 'A conexão está instável.',
        life: 4500
      })
    );
  });

  it('com ação, dura mais; ao acionar, fecha o aviso e executa', () => {
    const messages = { add: vi.fn(), clear: vi.fn() };
    TestBed.configureTestingModule({
      providers: [NotificationService, { provide: MessageService, useValue: messages }]
    });
    const service = TestBed.inject(NotificationService);
    const acao = { label: 'Abrir obra', run: vi.fn() };

    service.success('Obra cadastrada.', { action: acao });

    const enviada = messages.add.mock.calls[0][0];
    expect(enviada.life).toBe(9000);
    expect(enviada.data.cesAction).toBe(acao);

    service.runAction(enviada.data.cesAction);
    expect(messages.clear).toHaveBeenCalled();
    expect(acao.run).toHaveBeenCalled();
  });

  it('mantém opções tipadas e limpa apenas a chave solicitada', () => {
    const messages = { add: vi.fn(), clear: vi.fn() };
    TestBed.configureTestingModule({
      providers: [NotificationService, { provide: MessageService, useValue: messages }]
    });
    const service = TestBed.inject(NotificationService);

    service.error('Falha ao salvar.', { title: 'Registro pendente', sticky: true, key: 'form' });
    service.clear('form');

    expect(messages.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: 'Registro pendente',
        sticky: true,
        key: 'form'
      })
    );
    expect(messages.clear).toHaveBeenCalledWith('form');
  });
});
