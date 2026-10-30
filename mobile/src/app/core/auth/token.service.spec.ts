import { TestBed } from '@angular/core/testing';
import { TokenService } from './token.service';

describe('TokenService', () => {
  let service: TokenService;
  
  // Um token JWT falso (mas válido na estrutura) para testes.
  // Payload decodificado esperado: {"sub":"123","nome":"Usuario Teste","equipeId":"eq-1","estoqueId":"est-1","roles":["ENCARREGADO"],"exp": 9999999999}
  const mockValidToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMiLCJub21lIjoiVXN1YXJpbyBUZXN0ZSIsImVxdWlwZUlkIjoiZXEtMSIsImVzdG9xdWVJZCI6ImVzdC0xIiwicm9sZXMiOlsiRU5DQVJSRUdBRE8iXSwiZXhwIjo5OTk5OTk5OTk5fQ.invalid-signature';
  
  // Payload decodificado esperado: {"sub":"123","exp": 0} (Expirado na época UNIX 0)
  const mockExpiredToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMiLCJleHAiOjB9.invalid-signature';

  beforeEach(() => {
    // Limpa o localStorage antes de cada teste
    localStorage.removeItem('ces_auth_token');
    localStorage.removeItem('ces_device_user_id');
    
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenService);
  });

  afterEach(() => {
    localStorage.removeItem('ces_auth_token');
    localStorage.removeItem('ces_device_user_id');
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should start with null token when localStorage is empty', () => {
    expect(service.token()).toBeNull();
    expect(service.decodedPayload()).toBeNull();
  });

  it('should set and get token correctly', () => {
    service.setToken(mockValidToken);
    
    expect(service.token()).toBe(mockValidToken);
    expect(localStorage.getItem('ces_auth_token')).toBe(mockValidToken);
  });

  it('should clear token correctly', () => {
    service.setToken(mockValidToken);
    expect(service.token()).not.toBeNull();

    service.clearToken();
    expect(service.token()).toBeNull();
    expect(localStorage.getItem('ces_auth_token')).toBeNull();
  });

  it('should decode valid JWT payload correctly', () => {
    service.setToken(mockValidToken);
    
    const payload = service.decodedPayload();
    expect(payload).toBeTruthy();
    expect(payload?.sub).toBe('123');
    expect(payload?.nome).toBe('Usuario Teste');
    expect(payload?.equipeId).toBe('eq-1');
    expect(payload?.estoqueId).toBe('est-1');
    expect(payload?.roles).toContain('ENCARREGADO');
    expect(payload?.exp).toBe(9999999999);
  });

  it('rejeita token sem usuário identificado', () => {
    expect(() => service.setToken('invalid.jwt.token')).toThrowError(/sem usuário identificado/);
    expect(service.token()).toBeNull();
  });

  it('should evaluate isTokenExpired as true when no token exists', () => {
    expect(service.isTokenExpired()).toBeTrue();
  });

  it('should evaluate isTokenExpired as false when token expiration is in the future', () => {
    service.setToken(mockValidToken);
    expect(service.isTokenExpired()).toBeFalse();
  });

  it('should evaluate isTokenExpired as true when token expiration is in the past', () => {
    service.setToken(mockExpiredToken);
    expect(service.isTokenExpired()).toBeTrue();
  });

  it('preserva a identidade local ao reiniciar com token expirado', () => {
    localStorage.setItem('ces_auth_token', mockExpiredToken);
    const restored = new TokenService();

    expect(restored.token()).toBe(mockExpiredToken);
    expect(restored.ownerId()).toBe('123');
    expect(restored.isTokenExpired()).toBeTrue();
  });

  it('should expose computed signals for specific payload properties', () => {
    service.setToken(mockValidToken);
    
    expect(service.usuarioId()).toBe('123');
    expect(service.nomeUsuario()).toBe('Usuario Teste');
    expect(service.equipeId()).toBe('eq-1');
    expect(service.estoqueId()).toBe('est-1');
  });
});
