import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AppShellComponent } from './app-shell.component';
import { AuthService } from '../../core/auth/auth.service';
import { OutboxService } from '../../core/sync/outbox.service';
import { SessaoService } from '../../core/sessao/sessao.service';

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;
  let admin = false;
  let almox = false;

  beforeEach(async () => {
    admin = false;
    almox = false;
    await TestBed.configureTestingModule({
      imports: [AppShellComponent],
      providers: [
        provideRouter([]),
        { provide: AuthService, useValue: { isAdministrativo: () => admin, podeAcessarAlmoxarifado: () => almox, reautenticacaoNecessaria: () => false, entrarNovamente: () => {} } },
        { provide: OutboxService, useValue: { pendingCount: signal(3) } },
        { provide: SessaoService, useValue: { estadoTela: () => ({}), definirEstadoTela: () => {} } },
      ],
    }).compileComponents();
  });

  function criar() {
    fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('encarregado: Hoje · Serviços · Material · Conta (sem inspeções)', () => {
    const c = criar();
    expect(c.rotas()).toEqual(['/hoje', '/campo', '/material', '/perfil']);
    expect(c.rotas()).not.toContain('/inspecoes');
  });

  it('técnico de SST: Hoje · Inspeções · Desvios · Conta (sem logística)', () => {
    admin = true;
    const c = criar();
    expect(c.rotas()).toEqual(['/hoje', '/inspecoes', '/desvios', '/perfil']);
    expect(c.rotas()).not.toContain('/material');
    expect(c.rotas()).not.toContain('/campo');
  });

  it('administrativo com almoxarifado: Hoje · Almoxarifado · Segurança · Conta', () => {
    admin = true;
    almox = true;
    const c = criar();
    expect(c.rotas()).toEqual(['/hoje', '/almox', '/inspecoes', '/perfil']);
    const seguranca = c.abas().find(a => a.rota === '/inspecoes');
    expect(seguranca?.rotulo).toBe('Segurança');
    expect(seguranca?.ativaEm).toEqual(['/desvios']);
  });

  it('pendências da fila aparecem no marcador da aba Hoje', () => {
    expect(criar().abas()[0].marcador).toBe(3);
  });

  it('Material também fica ativa em pedidos e transferências', () => {
    const material = criar().abas().find(a => a.rota === '/material');
    expect(material?.ativaEm).toEqual(['/solicitacoes', '/transferencias']);
  });
});
