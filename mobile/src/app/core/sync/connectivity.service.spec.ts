import { TestBed } from '@angular/core/testing';
import { ConnectivityService } from './connectivity.service';
import { TokenService } from '../auth/token.service';
import { SyncService } from './sync.service';

describe('ConnectivityService (Mobile)', () => {
  let service: ConnectivityService;
  let tokenServiceSpy: jasmine.SpyObj<TokenService>;
  let syncServiceSpy: jasmine.SpyObj<SyncService>;

  beforeEach(() => {
    localStorage.removeItem('ces_mobile_usar_redes_moveis');

    tokenServiceSpy = jasmine.createSpyObj('TokenService', ['token', 'isTokenExpired', 'getDecodedPayload']);
    tokenServiceSpy.token.and.returnValue('valid-token');
    tokenServiceSpy.isTokenExpired.and.returnValue(false);
    tokenServiceSpy.getDecodedPayload.and.returnValue({
      sub: 'colab-1',
      nome: 'Encarregado Campo',
      roles: ['ENCARREGADO'],
      tipoUsuario: 'CAMPO',
      exp: 9999999999,
    });

    syncServiceSpy = jasmine.createSpyObj('SyncService', ['syncOutbox']);
    syncServiceSpy.syncOutbox.and.returnValue(Promise.resolve({ sincronizados: 0, erros: 0, falhas: [] }));

    TestBed.configureTestingModule({
      providers: [
        ConnectivityService,
        { provide: TokenService, useValue: tokenServiceSpy },
        { provide: SyncService, useValue: syncServiceSpy },
      ],
    });

    service = TestBed.inject(ConnectivityService);
  });

  afterEach(() => {
    localStorage.removeItem('ces_mobile_usar_redes_moveis');
  });

  it('deve ser instanciado corretamente', () => {
    expect(service).toBeTruthy();
  });

  it('deve inicializar com usarRedesMoveis false por padrão para usuários do tipo CAMPO', () => {
    expect(service.usarRedesMoveis()).toBe(false);
  });

  it('deve ficar offline e marcar isBloqueadoPorDadosMoveis quando estiver em rede celular e usarRedesMoveis for false', () => {
    service.setSimulatedHardwareOnline(true);
    service.setUsarRedesMoveis(false);
    service.setSimulatedCellular(true);

    expect(service.isOnline()).toBe(false);
    expect(service.isBloqueadoPorDadosMoveis()).toBe(true);
    expect(service.statusConexao()).toBe('dados_moveis_bloqueados');
  });

  it('deve voltar a ficar online se o usuário ativar usarRedesMoveis', () => {
    service.setSimulatedHardwareOnline(true);
    service.setSimulatedCellular(true);
    service.setUsarRedesMoveis(false);
    expect(service.isOnline()).toBe(false);

    service.setUsarRedesMoveis(true);
    expect(service.isOnline()).toBe(true);
    expect(service.isBloqueadoPorDadosMoveis()).toBe(false);
    expect(service.statusConexao()).toBe('online');
    expect(localStorage.getItem('ces_mobile_usar_redes_moveis')).toBe('true');
  });

  it('deve ficar offline imediatamente quando o modo forcarOffline for ativado', () => {
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
});
