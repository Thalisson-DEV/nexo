import '@angular/compiler';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of } from 'rxjs';
import { NotificationService } from '@ces/ui';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import { SstService } from '../../../../core/services/sst.service';
import {
  HistoricoInspecaoEvento,
  HistoricoInspecaoResponse,
} from '../../../../core/models/sst.models';
import { InspecaoSegurancaHistoricoComponent } from './inspecao-seguranca-historico.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('InspecaoSegurancaHistoricoComponent', () => {
  let component: InspecaoSegurancaHistoricoComponent;
  let fixture: ComponentFixture<InspecaoSegurancaHistoricoComponent>;
  let sstServiceMock: { obterHistoricoInspecao: ReturnType<typeof vi.fn> };
  let notificationMock: { error: ReturnType<typeof vi.fn> };
  let userLookupMock: { resolverNome: ReturnType<typeof vi.fn> };

  const makeEvent = (
    sequence: number,
    type: string,
    occurredAt: string,
    payload: HistoricoInspecaoEvento['payload'],
    extra: Partial<HistoricoInspecaoEvento> = {},
  ): HistoricoInspecaoEvento => ({
    id: `evt-${sequence}`,
    idInspecao: 'insp-123',
    sequencia: sequence,
    tipo: type,
    origem: 'UI',
    autor: { id: 'usr-1', nome: 'Inspetor Carlos' },
    ocorridoEm: occurredAt,
    payload,
    ...extra,
  });

  const mockHistoricoResponse: HistoricoInspecaoResponse = {
    page: { number: 0, size: 10, totalElements: 2, totalPages: 1 },
    integridade: [{ escopo: 'INSPECAO', idCadeia: 'insp-123', status: 'INTEGRO' }],
    eventos: [
      makeEvent(1, 'REGISTRO_INICIAL', '2026-09-28T09:00:00Z', {
        comando: 'REGISTRAR_INSPECAO',
        alteracoes: [],
        snapshots: {
          tipoInspecao: 'Inspeção de Campo',
          processo: 'Manutenção de Rede',
          baseOperacional: 'Base Central',
        },
      }),
      makeEvent(2, 'EDICAO_CABECALHO', '2026-09-28T10:00:00Z', {
        comando: 'ATUALIZAR_CABECALHO_INSPECAO',
        alteracoes: [
          {
            caminho: 'observacoes',
            valorAnterior: 'Observação preliminar',
            valorNovo: 'Observação corrigida',
          },
          {
            caminho: 'colaboradoresInspecionados',
            valorAnterior: 'João Silva',
            valorNovo: 'João Silva, Maria Santos',
          },
        ],
      }, { justificativa: 'Inclusão de colaboradora inspecionada' }),
    ],
  };

  beforeEach(async () => {
    sstServiceMock = {
      obterHistoricoInspecao: vi.fn().mockReturnValue(of(mockHistoricoResponse)),
    };
    notificationMock = {
      error: vi.fn(),
    };
    userLookupMock = {
      resolverNome: vi.fn().mockImplementation((autor: any) => autor?.nome ?? 'Usuário'),
    };

    await TestBed.configureTestingModule({
      imports: [InspecaoSegurancaHistoricoComponent],
      providers: [
        { provide: SstService, useValue: sstServiceMock },
        { provide: NotificationService, useValue: notificationMock },
        { provide: UsuarioLookupService, useValue: userLookupMock },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(InspecaoSegurancaHistoricoComponent);
    component = fixture.componentInstance;
    component.idInspecao = 'insp-123';
    fixture.detectChanges();
  });

  it('deve ser instanciado e carregar o histórico da inspeção', () => {
    expect(component).toBeTruthy();
    expect(sstServiceMock.obterHistoricoInspecao).toHaveBeenCalledWith('insp-123', 0);
  });

  it('deve ordenar eventos pela sequência', () => {
    const events = (component as any).sortedEvents();
    expect(events.length).toBe(2);
    expect(events[0].sequencia).toBe(1);
    expect(events[1].sequencia).toBe(2);
  });

  it('deve identificar cadeia íntegra', () => {
    const info = (component as any).integrityInfo();
    expect(info?.status).toBe('INTEGRO');
  });

  it('deve exibir diffs de alteração de cabeçalho', () => {
    const events = (component as any).sortedEvents();
    const edicaoEvent = events[1];
    const diffs = (component as any).alterations(edicaoEvent);
    expect(diffs.length).toBe(2);
    expect(diffs[0].caminho).toBe('observacoes');
  });

  it('deve formatar labels e autores adequadamente', () => {
    expect((component as any).fieldLabel('observacoes')).toBe('Observações');
    expect((component as any).fieldLabel('colaboradoresInspecionados')).toBe('Colaboradores inspecionados');
    expect((component as any).formatAuthor({ id: '1', nome: 'Carlos' })).toBe('Carlos');
  });
});
