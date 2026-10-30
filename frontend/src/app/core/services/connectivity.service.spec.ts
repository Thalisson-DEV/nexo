import { TestBed } from '@angular/core/testing';
import { ConnectivityService } from './connectivity.service';
import { AuthService } from './auth.service';
import { signal } from '@angular/core';
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';

describe('ConnectivityService', () => {
  let service: ConnectivityService;
  let mockAuth: { currentUser: ReturnType<typeof signal>; updateUserSession: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    localStorage.clear();
    mockAuth = {
      currentUser: signal({
        id: 'user-1',
        login: 'operador.campo',
        tipoUsuario: 'CAMPO',
        token: 'token-jwt',
        tema: 'light',
        corPrimaria: 'blue',
        animacoesAtivadas: true,
      } as any),
      updateUserSession: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        ConnectivityService,
        { provide: AuthService, useValue: mockAuth },
      ],
    });

    service = TestBed.inject(ConnectivityService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('deve inicializar online quando o hardware reporta online e não for rede móvel restrita', () => {
    service.setSimulatedHardwareOnline(true);
    service.setSimulatedCellular(false);
    expect(service.isOnline()).toBe(true);
    expect(service.statusConexao()).toBe('online');
  });

  it('deve ficar offline e marcar isBloqueadoPorDadosMoveis quando estiver em dados móveis e a preferência for desligada', () => {
    service.setSimulatedHardwareOnline(true);
    service.setUsarRedesMoveis(false);
    service.setSimulatedCellular(true);

    expect(service.isOnline()).toBe(false);
    expect(service.isBloqueadoPorDadosMoveis()).toBe(true);
    expect(service.statusConexao()).toBe('dados_moveis_bloqueados');
  });

  it('deve voltar a ficar online em rede móvel se o usuário habilitar o uso de redes móveis', () => {
    service.setSimulatedHardwareOnline(true);
    service.setSimulatedCellular(true);
    service.setUsarRedesMoveis(false);
    expect(service.isOnline()).toBe(false);

    service.setUsarRedesMoveis(true);
    expect(service.isOnline()).toBe(true);
    expect(service.isBloqueadoPorDadosMoveis()).toBe(false);
    expect(service.statusConexao()).toBe('online');
    expect(mockAuth.updateUserSession).toHaveBeenCalledWith({ usarRedesMoveis: true });
    expect(localStorage.getItem('ces_usar_redes_moveis')).toBe('true');
  });

  it('deve cortar conectividade quando o usuário ativar o modo offline manual', () => {
    service.setSimulatedHardwareOnline(true);
    service.setSimulatedCellular(false);
    expect(service.isOnline()).toBe(true);

    service.setForcarOffline(true);
    expect(service.isOnline()).toBe(false);
    expect(service.statusConexao()).toBe('offline_manual');

    service.setForcarOffline(false);
    expect(service.isOnline()).toBe(true);
  });

  it('deve ficar offline quando o hardware perder sinal completamente', () => {
    service.setSimulatedCellular(false);
    service.setSimulatedHardwareOnline(false);

    expect(service.isOnline()).toBe(false);
    expect(service.statusConexao()).toBe('offline_sem_rede');
  });

  it('permite sobrescrita direta de isOnline em testes mantendo retrocompatibilidade', () => {
    service.isOnline.set(false);
    expect(service.isOnline()).toBe(false);

    service.isOnline.set(true);
    expect(service.isOnline()).toBe(true);
  });
});
