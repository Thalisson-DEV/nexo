import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { signal } from '@angular/core';
import { NovaTransferenciaComponent } from './nova-transferencia.component';
import { DbService } from '../../../core/db/db.service';
import { TokenService } from '../../../core/auth/token.service';
import { ToastService } from '../../../core/ui/toast.service';
import { TurnoService } from '../../../core/turno/turno.service';
import { OutboxService } from '../../../core/sync/outbox.service';
import { SyncService } from '../../../core/sync/sync.service';
import { ConnectivityService } from '../../../core/sync/connectivity.service';

describe('NovaTransferenciaComponent - Assinaturas Digitais', () => {
  let component: NovaTransferenciaComponent;
  let fixture: ComponentFixture<NovaTransferenciaComponent>;

  let dbServiceSpy: jasmine.SpyObj<DbService>;
  let tokenServiceSpy: jasmine.SpyObj<TokenService>;
  let toastServiceSpy: jasmine.SpyObj<ToastService>;
  let turnoServiceSpy: jasmine.SpyObj<TurnoService>;
  let routerSpy: jasmine.SpyObj<Router>;
  let outboxServiceSpy: jasmine.SpyObj<OutboxService>;
  let syncServiceSpy: jasmine.SpyObj<SyncService>;
  let connectivityServiceSpy: jasmine.SpyObj<ConnectivityService>;

  beforeEach(async () => {
    dbServiceSpy = jasmine.createSpyObj('DbService', [
      'getEstoques',
      'getSaldosComMateriais',
      'registrarTransferenciaOffline',
    ]);
    dbServiceSpy.getEstoques.and.resolveTo([
      { id: 'est-dest-1', nomeIdentificador: 'Base Sul', tipoEstoque: 'BASE' } as any,
    ]);
    dbServiceSpy.getSaldosComMateriais.and.resolveTo([]);
    dbServiceSpy.registrarTransferenciaOffline.and.resolveTo();

    tokenServiceSpy = jasmine.createSpyObj('TokenService', ['estoqueId', 'nomeUsuario', 'ownerId']);
    tokenServiceSpy.ownerId.and.returnValue('u-1');
    tokenServiceSpy.estoqueId.and.returnValue('est-origem-1');
    tokenServiceSpy.nomeUsuario.and.returnValue('Equipe Teste');

    toastServiceSpy = jasmine.createSpyObj('ToastService', ['success', 'warning', 'error']);
    turnoServiceSpy = jasmine.createSpyObj('TurnoService', ['loadTurnoAtivo'], {
      turnoAtivo: signal({ id: 'turno-1' }),
    });
    turnoServiceSpy.loadTurnoAtivo.and.resolveTo();

    routerSpy = jasmine.createSpyObj('Router', ['navigate']);
    outboxServiceSpy = jasmine.createSpyObj('OutboxService', ['updateStatus', 'discard']);
    syncServiceSpy = jasmine.createSpyObj('SyncService', ['syncOutbox']);
    connectivityServiceSpy = jasmine.createSpyObj('ConnectivityService', ['isOnline', 'statusConexao']);
    connectivityServiceSpy.statusConexao.and.returnValue('online');
    connectivityServiceSpy.isOnline.and.returnValue(true);

    await TestBed.configureTestingModule({
      imports: [NovaTransferenciaComponent],
      providers: [
        { provide: DbService, useValue: dbServiceSpy },
        { provide: TokenService, useValue: tokenServiceSpy },
        { provide: ToastService, useValue: toastServiceSpy },
        { provide: TurnoService, useValue: turnoServiceSpy },
        { provide: Router, useValue: routerSpy },
        { provide: OutboxService, useValue: outboxServiceSpy },
        { provide: SyncService, useValue: syncServiceSpy },
        { provide: ConnectivityService, useValue: connectivityServiceSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(NovaTransferenciaComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve ter 4 etapas no wizard e iniciar na etapa 1', () => {
    expect(component.currentStep()).toBe(1);
    expect(component.stepTitle()).toBe('Estoque de destino');
  });

  it('deve avançar para a etapa 4 de assinatura digital e validar obrigatoriedade das assinaturas', () => {
    component.idEstoqueDestino.set('est-dest-1');
    component.selectedItens.set([
      {
        idMaterial: 'mat-1',
        codigo: '1001',
        descricao: 'Cabo',
        unidadeMedida: 'M',
        quantidade: 10,
        condicao: 'NOVO',
        detalhaSerie: false,
        saldoDisponivel: 50,
      },
    ]);

    component.currentStep.set(3);
    expect(component.stepTitle()).toBe('Revisão dos itens');
    expect(component.canAdvance()).toBeTrue();

    // Avança para a Etapa 4
    component.nextStep();
    expect(component.currentStep()).toBe(4);
    expect(component.stepTitle()).toBe('Assinatura digital');

    // Na etapa 4, não pode avançar sem ambas as assinaturas
    expect(component.canAdvance()).toBeFalse();

    component.assinaturaEntregador.set('data:image/png;base64,assinatura1');
    expect(component.canAdvance()).toBeFalse();

    component.assinaturaRecebedor.set('data:image/png;base64,assinatura2');
    expect(component.canAdvance()).toBeTrue();
  });

  it('deve abrir e fechar o modal de assinatura para entregador e recebedor', () => {
    component.openSignaturePad('entregador');
    expect(component.activeSignatureModal()).toBe('entregador');

    component.closeSignaturePad();
    expect(component.activeSignatureModal()).toBeNull();

    component.openSignaturePad('recebedor');
    expect(component.activeSignatureModal()).toBe('recebedor');
  });

  it('deve submeter a transferência enviando ambas as assinaturas digitais', async () => {
    component.idEstoqueDestino.set('est-dest-1');
    component.selectedItens.set([
      {
        idMaterial: 'mat-1',
        codigo: '1001',
        descricao: 'Cabo',
        unidadeMedida: 'M',
        quantidade: 5,
        condicao: 'NOVO',
        detalhaSerie: false,
        saldoDisponivel: 20,
      },
    ]);
    component.assinaturaEntregador.set('data:image/png;base64,entregador-ok');
    component.assinaturaRecebedor.set('data:image/png;base64,recebedor-ok');
    component.currentStep.set(4);

    await component.confirm();

    expect(dbServiceSpy.registrarTransferenciaOffline).toHaveBeenCalledWith(
      'est-origem-1',
      'est-dest-1',
      [
        {
          idMaterial: 'mat-1',
          quantidade: 5,
          condicao: 'NOVO',
          numSerie: null,
        },
      ],
      'data:image/png;base64,entregador-ok',
      'data:image/png;base64,recebedor-ok'
    );
    expect(syncServiceSpy.syncOutbox).toHaveBeenCalled();
    expect(toastServiceSpy.success).toHaveBeenCalled();
    expect(routerSpy.navigate).toHaveBeenCalledWith(['/transferencias']);
  });
});
