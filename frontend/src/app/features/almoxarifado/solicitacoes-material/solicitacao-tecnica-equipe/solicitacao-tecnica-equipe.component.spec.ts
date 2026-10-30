import '@angular/compiler';
import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '@ces/ui';
import { ObrasService } from '../../../../core/services/obras.service';
import { SolicitacaoMaterialDetalheResponse } from '../../../../core/models/solicitacao-material-tecnica.models';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { SolicitacaoTecnicaEquipeComponent } from './solicitacao-tecnica-equipe.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

const detail = {
  id: 'sol-1',
  contexto: 'TECNICO',
  status: 'APROVADO',
  versao: 3,
  statusSeparacao: 'SEPARADO',
  acoesPermitidas: ['EDITAR_PLANEJAMENTO', 'ENTREGAR'],
  itens: [
    {
      id: 'item-1',
      material: { id: 'mat-1', codigo: '001', descricao: 'Material' },
      quantidade: 2,
      quantidadeEntregue: 0,
      tipoMovimentacao: 'ADICAO_COMUM',
    },
  ],
  tecnico: {
    tipoTecnico: 'ADICAO_COMUM',
    tipoObra: 'OBRA',
    idObra: 'obra-1',
    ocorrencia: null,
    idAlmoxarifadoAtendimento: 'almox-1',
    idEquipe: 'equipe-1',
    dataPrevista: '2026-09-01',
    observacao: 'Preservar',
    statusSeparacao: 'SEPARADO',
    versao: 3,
    acoesPermitidas: ['EDITAR_PLANEJAMENTO', 'ENTREGAR'],
  },
} as SolicitacaoMaterialDetalheResponse;

describe('SolicitacaoTecnicaEquipeComponent', () => {
  let fixture: ComponentFixture<SolicitacaoTecnicaEquipeComponent>;
  let component: SolicitacaoTecnicaEquipeComponent;
  const substituirPlanejamento = vi.fn();
  const notifications = {
    error: vi.fn(),
    warning: vi.fn(),
    success: vi.fn(),
  };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    substituirPlanejamento.mockReset();
    vi.clearAllMocks();

    TestBed.configureTestingModule({
      imports: [SolicitacaoTecnicaEquipeComponent],
      providers: [
        {
          provide: SolicitacaoMaterialTecnicaService,
          useValue: { substituirPlanejamento },
        },
        {
          provide: ObrasService,
          useValue: {
            buscarObraPorId: () => of({ id: 'obra-1', numero: '1024', titulo: 'Alimentador Rural' }),
          },
        },
        { provide: NotificationService, useValue: notifications },
      ],
    });

    TestBed.overrideComponent(SolicitacaoTecnicaEquipeComponent, {
      set: {
        imports: [],
        template: `
          <span>Definir Equipe de Campo</span>
          <span>{{ currentTeamName() }}</span>
          <span>{{ selectedTeamName() }}</span>
        `,
      },
    });

    await TestBed.compileComponents();

    fixture = TestBed.createComponent(SolicitacaoTecnicaEquipeComponent);
    component = fixture.componentInstance;
    (component as any).detail = signal(detail);
    (component as any).teams = signal([
      { id: 'equipe-1', codigo: 'EQ-1' },
      { id: 'equipe-2', codigo: 'EQ-2' },
    ]);
    (component as any).visible = signal(true);
    fixture.detectChanges();
  });

  it('altera somente a equipe preservando o snapshot integral', () => {
    substituirPlanejamento.mockReturnValue(
      of({
        solicitacaoAtualizada: {
          ...detail,
          tecnico: { ...detail.tecnico!, idEquipe: 'equipe-2', versao: 4 },
        },
      }),
    );

    component['teamId'].set('equipe-2');
    component['submit']();

    expect(substituirPlanejamento).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({
        versaoEsperada: 3,
        idEquipe: 'equipe-2',
        dataPrevista: '2026-09-01',
        observacao: 'Preservar',
        itens: [
          {
            idItem: 'item-1',
            idMaterial: 'mat-1',
            tipoMovimentacao: 'ADICAO_COMUM',
            quantidade: 2,
          },
        ],
      }),
    );
  });

  it('fecha o atalho e encaminha conflito de versão para reconciliação', () => {
    substituirPlanejamento.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: {
              code: 'VERSAO_DESATUALIZADA',
              versaoEsperada: 3,
              versaoAtual: 4,
            },
          }),
      ),
    );
    const conflict = vi.fn();
    component.conflict.subscribe(conflict);

    component['teamId'].set('equipe-2');
    component['submit']();

    expect(component.visible()).toBe(false);
    expect(conflict).toHaveBeenCalledWith({
      intent: 'Definir equipe responsável',
      currentVersion: 4,
    });
  });
});
