import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ObraResponse } from '../../../core/models/obras.models';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { ObraFormComponent } from './obra-form.component';

describe('ObraFormComponent', () => {
  let component: ObraFormComponent;
  let fixture: ComponentFixture<ObraFormComponent>;
  let obrasService: any;
  let rhService: any;

  const mockObra: ObraResponse = {
    id: 'obra-1',
    numero: '1228694',
    titulo: 'SUBESTAÇÃO CENTRAL - REFORMA ALIMENTADOR 02',
    dataInicio: '01/03/2026',
    dataPrevisaoFim: '30/09/2026',
    statusId: 'st-1',
    statusNome: 'EM EXECUÇÃO',
    statusConcluida: false,
    supervisorId: 'sup-1',
    supervisorNome: 'CARLOS SILVA',
    coordenadorId: 'coord-1',
    coordenadorNome: 'MARCOS OLIVEIRA',
    equipes: [],
    subprojetos: [],
    anexos: [],
    ativo: true,
    dataCriacao: '2026-03-01T09:00:00-03:00'
  };

  beforeEach(async () => {
    obrasService = {
      buscarPorId: vi.fn().mockReturnValue(of(mockObra)),
      buscarObraPorId: vi.fn().mockReturnValue(of(mockObra)),
      criarObra: vi.fn().mockReturnValue(of(mockObra)),
      atualizarObra: vi.fn().mockReturnValue(of(mockObra)),
      listarStatusAtivos: vi.fn().mockReturnValue(of([{ id: 'st-1', nome: 'EM EXECUÇÃO', concluida: false, ativo: true }])),
      listarAgentesAtivos: vi.fn().mockReturnValue(of([])),
      listarObras: vi.fn().mockReturnValue(of({ content: [] }))
    };

    rhService = {
      listarBasesOperacionaisAtivas: vi.fn().mockReturnValue(of({ content: [] })),
      listarProcessosAtivos: vi.fn().mockReturnValue(of({ content: [] })),
      listarMunicipios: vi.fn().mockReturnValue(of({ content: [] })),
      listarCentrosFaturamentoAtivos: vi.fn().mockReturnValue(of({ content: [] })),
      listarSupervisoresAtivosMin: vi.fn().mockReturnValue(of([
        { id: 'sup-1', idColaborador: 'colab-1', matricula: 'SUP001', nomeColaborador: 'CARLOS SILVA' }
      ])),
      listarCoordenadoresAtivosMin: vi.fn().mockReturnValue(of([
        { id: 'coord-1', idColaborador: 'colab-2', matricula: 'CRD001', nomeColaborador: 'MARCOS OLIVEIRA' }
      ])),
      listarEquipesAtivas: vi.fn().mockReturnValue(of({ content: [] })),
      listarEquipes: vi.fn().mockReturnValue(of({ content: [] }))
    };

    await TestBed.configureTestingModule({
      imports: [ObraFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
        { provide: ObrasService, useValue: obrasService },
        { provide: RhService, useValue: rhService },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: { get: () => null } } }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ObraFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve inicializar o formulário no modo de criação com etapa 1 ativa', () => {
    expect(component).toBeTruthy();
    expect(component['isEdit']()).toBe(false);
    expect(component['activeStep']()).toBe(1);
    expect(component['form'].get('numero')).toBeTruthy();
    expect(component['form'].get('titulo')).toBeTruthy();
    expect(component['form'].get('idSupervisor')).toBeTruthy();
    expect(component['form'].get('idCoordenador')).toBeTruthy();
  });

  it('deve sanitizar o número e título da obra ao alterar', () => {
    component['form'].patchValue({ numero: 'b-1228694', titulo: '  reforma   geral  ' });
    component['onNumeroChange']();
    component['onTituloChange']();

    expect(component['form'].get('numero')?.value).toBe('1228694');
    expect(component['form'].get('titulo')?.value).toBe('REFORMA GERAL');
  });

  it('deve validar e permitir avanço entre as etapas do formulário', () => {
    // Tenta avançar sem preencher
    component['irPara'](2);
    expect(component['activeStep']()).toBe(1);

    // Preenche etapa 1 com dados cadastrais
    component['form'].patchValue({
      numero: '1228694',
      titulo: 'SUBESTAÇÃO CENTRAL'
    });
    component['irPara'](2);
    expect(component['activeStep']()).toBe(2);

    // Tenta avançar para etapa 3 sem preencher planejamento completo
    component['irPara'](3);
    expect(component['activeStep']()).toBe(2);

    // Preenche etapa 2 completa (incluindo supervisor e coordenador)
    component['form'].patchValue({
      idProcesso: 'proc-1',
      idBaseOperacional: 'base-1',
      idMunicipio: 'mun-1',
      idCentroCusto: 'cc-1',
      idSupervisor: 'sup-1',
      idCoordenador: 'coord-1',
      idStatusObra: 'st-1',
      dataInicio: new Date(2026, 2, 1),
      dataPrevisaoFim: new Date(2026, 8, 30)
    });
    component['irPara'](3);
    expect(component['activeStep']()).toBe(3);

    // Avança para revisão (etapa 4)
    component['irPara'](4);
    expect(component['activeStep']()).toBe(4);
  });

  it('deve bloquear etapa 2 se Supervisor ou Coordenador não forem selecionados', () => {
    component['form'].patchValue({
      numero: '1228694',
      titulo: 'SUBESTAÇÃO CENTRAL',
      idProcesso: 'proc-1',
      idBaseOperacional: 'base-1',
      idMunicipio: 'mun-1',
      idCentroCusto: 'cc-1',
      idStatusObra: 'st-1',
      dataInicio: new Date(2026, 2, 1),
      dataPrevisaoFim: new Date(2026, 8, 30),
      idSupervisor: null,
      idCoordenador: null
    });

    expect(component['isStep2Valid']()).toBe(false);

    component['form'].patchValue({ idSupervisor: 'sup-1' });
    expect(component['isStep2Valid']()).toBe(false);

    component['form'].patchValue({ idCoordenador: 'coord-1' });
    expect(component['isStep2Valid']()).toBe(true);
  });

  it('deve resolver os nomes de Supervisor e Coordenador para os cards de revisão', () => {
    component['form'].patchValue({
      idSupervisor: 'sup-1',
      idCoordenador: 'coord-1'
    });

    expect(component['getSupervisorNome']()).toBe('CARLOS SILVA');
    expect(component['getCoordenadorNome']()).toBe('MARCOS OLIVEIRA');
  });

  it('deve submeter o cadastro da obra com sucesso contendo supervisor e coordenador', () => {
    component['form'].patchValue({
      numero: '1228694',
      titulo: 'SUBESTAÇÃO CENTRAL',
      idProcesso: 'proc-1',
      idBaseOperacional: 'base-1',
      idMunicipio: 'mun-1',
      idCentroCusto: 'cc-1',
      idSupervisor: 'sup-1',
      idCoordenador: 'coord-1',
      idStatusObra: 'st-1',
      dataInicio: new Date(2026, 2, 1),
      dataPrevisaoFim: new Date(2026, 8, 30)
    });

    component['salvar']();
    expect(obrasService.criarObra).toHaveBeenCalledWith(
      expect.objectContaining({
        idSupervisor: 'sup-1',
        idCoordenador: 'coord-1'
      })
    );
  });

  it('deve validar vínculo de subprojeto exigindo seleção da obra principal e tipo de vínculo', () => {
    component['form'].patchValue({
      ehSubprojeto: true,
      idObraPai: null,
      tipoVinculoPai: null
    });

    expect(component['isStep3Valid']()).toBe(false);

    component['form'].patchValue({
      idObraPai: 'obra-pai-1',
      tipoVinculoPai: 'KIT'
    });

    expect(component['isStep3Valid']()).toBe(true);
  });

  it('deve formatar corretamente a exibição da obra pai em getObraPaiDisplay', () => {
    component['obrasPrincipaisList'].set([
      { id: 'obra-pai-1', numero: '1228600', titulo: 'SUBESTAÇÃO PRINCIPAL' } as any
    ]);
    component['form'].patchValue({ idObraPai: 'obra-pai-1' });

    expect(component['getObraPaiDisplay']()).toBe('1228600 — SUBESTAÇÃO PRINCIPAL');
  });
});
