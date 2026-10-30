import '@angular/compiler';
import { CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router, ActivatedRoute } from '@angular/router';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '@ces/ui';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { CriacaoSolicitacaoTecnicaResponse } from '../../../../core/models/solicitacao-material-tecnica.models';
import { SolicitacaoTecnicaFormComponent } from './solicitacao-tecnica-form.component';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoTecnicaFormComponent', () => {
  let fixture: ComponentFixture<SolicitacaoTecnicaFormComponent>;
  let component: SolicitacaoTecnicaFormComponent;
  let router: Router;

  const criarMock = vi.fn(() =>
    of<CriacaoSolicitacaoTecnicaResponse>({
      solicitacoesCriadas: [{ id: 'sol-1', tipo: 'ADICAO_COMUM' as const, codigo: '221' as const }],
    }),
  );

  const notificationsMock = {
    error: vi.fn(),
    warning: vi.fn(),
    success: vi.fn(),
  };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    criarMock.mockClear();
    notificationsMock.error.mockClear();
    notificationsMock.warning.mockClear();
    notificationsMock.success.mockClear();

    await TestBed.configureTestingModule({
      imports: [SolicitacaoTecnicaFormComponent],
      schemas: [CUSTOM_ELEMENTS_SCHEMA, NO_ERRORS_SCHEMA],
      providers: [
        provideRouter([]),
        {
          provide: SolicitacaoMaterialTecnicaService,
          useValue: {
            criar: criarMock,
            projetosRelacionados: () => of([]),
            obterProjecaoObra: () =>
              of({
                idObra: 'obra-corretiva',
                tipoObra: 'CORRETIVA' as const,
                ocorrencias: [
                  {
                    ocorrencia: { id: 'occ-1', codigo: 'OC 1042' },
                  },
                ],
                solicitacoes: [],
              }),
          },
        },
        {
          provide: AlmoxarifadoService,
          useValue: {
            listarEstoquesAtivos: () =>
              of({ content: [{ id: 'almox-1', nomeIdentificador: 'Central' }] }),
            buscarMaterialPorId: (id: string) =>
              of(
                id === 'mat-concreto'
                  ? { id, codigo: 'POSTE01', descricao: 'Poste Concreto 11m' }
                  : { id, codigo: 'MAT01', descricao: 'Cabo 10mm' },
              ),
          },
        },
        {
          provide: ObrasService,
          useValue: {
            listarObras: () =>
              of({
                content: [
                  {
                    id: 'obra-comum',
                    numero: 'OB01',
                    titulo: 'Obra Convencional',
                    tipoObra: 'OBRA',
                  },
                  {
                    id: 'obra-corretiva',
                    numero: 'OC01',
                    titulo: 'Obra Corretiva',
                    tipoObra: 'CORRETIVA',
                  },
                ],
              }),
            buscarObraPorId: (id: string) => {
              if (id === 'obra-corretiva') {
                return of({
                  id: 'obra-corretiva',
                  numero: 'OC01',
                  titulo: 'Obra Corretiva',
                  tipoObra: 'CORRETIVA',
                  equipes: ['eq-1'],
                });
              }
              return of({
                id: 'obra-comum',
                numero: 'OB01',
                titulo: 'Obra Convencional',
                tipoObra: 'OBRA',
                equipes: ['eq-1'],
              });
            },
          },
        },
        {
          provide: RhService,
          useValue: {
            listarEquipes: () =>
              of({
                content: [
                  {
                    id: 'eq-1',
                    codigo: 'EQ01',
                    membros: [
                      {
                        id: 'membro-1',
                        idColaborador: 'colab-equipe',
                        nomeColaborador: 'Membro da Equipe',
                        funcao: 'RESPONSAVEL',
                        vinculoAtivo: true,
                      },
                    ],
                  },
                ],
              }),
            listarColaboradores: () =>
              of({
                content: [
                  {
                    id: 'colab-equipe',
                    primeiroNome: 'Membro',
                    ultimoNome: 'Equipe',
                    nomeCompleto: 'Membro da Equipe',
                  },
                  {
                    id: 'colab-fora',
                    primeiroNome: 'Colaborador',
                    ultimoNome: 'Externo',
                    nomeCompleto: 'Colaborador Externo',
                  },
                ],
              }),
          },
        },
        {
          provide: NotificationService,
          useValue: notificationsMock,
        },
        {
          provide: LookupStore,
          useValue: {
            searchCollaborators: () =>
              of({
                items: [
                  { value: 'colab-equipe', label: 'Membro da Equipe' },
                  { value: 'colab-fora', label: 'Colaborador Externo' },
                ],
                nextCursor: null,
                hasMore: false,
              }),
            searchMaterials: () => of({ items: [], nextCursor: null, hasMore: false }),
          },
        },
      ],
    });

    TestBed.overrideComponent(SolicitacaoTecnicaFormComponent, {
      set: {
        imports: [],
        template: '<span>Obra e Atendimento</span>',
      },
    });

    await TestBed.compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(SolicitacaoTecnicaFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('inicia na etapa 1 (Obra e Atendimento)', () => {
    expect(component['activeStep']()).toBe(1);
    expect(fixture.nativeElement.textContent).toContain('Obra e Atendimento');
  });

  it('exibe somente o código nas labels dos tipos técnicos', () => {
    expect(component['tipoDescricao']('ADICAO_COMUM')).toBe('221');
    expect(component['tipoDescricao']('ADICAO_CONCRETO')).toBe('921');
    expect(component['tipoDescricao']('DEVOLUCAO_COMUM')).toBe('222');
    expect(component['tipoDescricao']('DEVOLUCAO_CONCRETO')).toBe('922');
  });

  it('impede avanço para a etapa 2 sem preencher campos obrigatórios', () => {
    component['nextStep']();
    expect(component['activeStep']()).toBe(1);
    expect(component['fieldErrors']()['idObra']).toBeTruthy();
    expect(component['fieldErrors']()['idAlmoxarifadoAtendimento']).toBeTruthy();
  });

  it('permite avançar para a etapa 2 após selecionar obra comum e almoxarifado', () => {
    component['selectObra']('obra-comum');
    component['idAlmoxarifado'].set('almox-1');
    fixture.detectChanges();

    component['nextStep']();
    expect(component['activeStep']()).toBe(2);
  });

  it('exige ocorrência normalizada para obra CORRETIVA', () => {
    component['selectObra']('obra-corretiva');
    component['idAlmoxarifado'].set('almox-1');
    fixture.detectChanges();

    expect(component['ocorrenciaObrigatoria']()).toBe(true);

    // Tentar avançar sem ocorrência
    component['nextStep']();
    expect(component['activeStep']()).toBe(1);
    expect(component['fieldErrors']()['ocorrencia']).toBeTruthy();

    // Preencher ocorrência com espaços múltiplos
    component['ocorrencia'].set('   oc   1042 ');
    component['nextStep']();
    expect(component['activeStep']()).toBe(2);
  });

  it('seleciona sugestão de ocorrência normalizando o valor', () => {
    component['selectObra']('obra-corretiva');
    fixture.detectChanges();

    component['selecionarSugestaoOcorrencia']('  oc 1042 ');
    expect(component['ocorrencia']()).toBe('OC 1042');
  });

  it('mantém equipe e responsável pelo saque opcionais', () => {
    component['selectObra']('obra-comum');
    component['idAlmoxarifado'].set('almox-1');
    component['itens'].set([
      {
        idMaterial: 'mat-comum',
        tipoMovimentacao: 'ADICAO_COMUM',
        codigoOperacao: '221',
        quantidade: 1,
        codigo: 'MAT01',
        descricao: 'Cabo 10mm',
      },
    ]);

    component['submit']();

    const request = (criarMock.mock.calls as any)[0][0];
    expect(request.idEquipe).toBeNull();
    expect(request.idColaboradorSaque).toBeNull();
    expect(request.saqueForaEquipe).toBe(false);
  });

  it('envia responsável da equipe e permite responsável fora da equipe', () => {
    component['selectObra']('obra-comum');
    component['idAlmoxarifado'].set('almox-1');
    component['idEquipe'].set('eq-1');
    component['idColaboradorSaque'].set('colab-equipe');
    component['itens'].set([
      {
        idMaterial: 'mat-comum',
        tipoMovimentacao: 'ADICAO_COMUM',
        codigoOperacao: '221',
        quantidade: 1,
        codigo: 'MAT01',
        descricao: 'Cabo 10mm',
      },
    ]);

    component['onSaqueForaEquipeChange'](true);
    component['collaboratorLookup'].seedSelected([
      { value: 'colab-equipe', label: 'Membro da Equipe' },
      { value: 'colab-fora', label: 'Colaborador Externo' },
    ]);
    expect(component['colaboradorSaqueOptions']()).toEqual([
      { label: 'Membro da Equipe', value: 'colab-equipe' },
      { label: 'Colaborador Externo', value: 'colab-fora' },
    ]);
    component['idColaboradorSaque'].set('colab-fora');
    component['submit']();

    const request = (criarMock.mock.calls as any)[0][0];
    expect(request.idEquipe).toBe('eq-1');
    expect(request.idColaboradorSaque).toBe('colab-fora');
    expect(request.saqueForaEquipe).toBe(true);
  });

  it('adiciona múltiplos materiais de tipos técnicos diferentes (221, 921, 222)', () => {
    component['selectObra']('obra-comum');
    component['idAlmoxarifado'].set('almox-1');
    component['goToStep'](2);

    // 1. Adicionar material comum com direção ADICAO -> 221
    component['idMaterial'].set('mat-comum');
    component['onMaterialChange']('mat-comum');
    component['tipoMovimentacao'].set('ADICAO_COMUM');
    component['quantidade'].set(10);
    component['addItem']();

    // 2. Adicionar material concreto com direção ADICAO -> 921
    component['idMaterial'].set('mat-concreto');
    component['onMaterialChange']('mat-concreto');
    component['tipoMovimentacao'].set('ADICAO_CONCRETO');
    component['quantidade'].set(2);
    component['addItem']();

    // 3. Adicionar material comum com direção DEVOLUCAO -> 222
    component['idMaterial'].set('mat-comum');
    component['onMaterialChange']('mat-comum');
    component['tipoMovimentacao'].set('DEVOLUCAO_COMUM');
    component['quantidade'].set(3);
    component['addItem']();

    expect(component['itens']()).toHaveLength(3);
    expect(component['itens']()[0].codigoOperacao).toBe('221');
    expect(component['itens']()[0].tipoMovimentacao).toBe('ADICAO_COMUM');
    expect(component['itens']()[1].codigoOperacao).toBe('921');
    expect(component['itens']()[1].tipoMovimentacao).toBe('ADICAO_CONCRETO');
    expect(component['itens']()[2].codigoOperacao).toBe('222');
    expect(component['itens']()[2].tipoMovimentacao).toBe('DEVOLUCAO_COMUM');

    // Avançar para a etapa 3 de revisão
    component['nextStep']();
    fixture.detectChanges();
    expect(component['activeStep']()).toBe(3);
    expect(component['tiposPresentes']()).toHaveLength(3);
  });

  it('submete criação única e navega diretamente para o detalhe', () => {
    criarMock.mockReturnValue(
      of<CriacaoSolicitacaoTecnicaResponse>({
        solicitacoesCriadas: [
          { id: 'sol-1', tipo: 'ADICAO_COMUM' as const, codigo: '221' as const },
        ],
      }),
    );

    component['selectObra']('obra-comum');
    component['idAlmoxarifado'].set('almox-1');
    component['itens'].set([
      {
        idMaterial: 'mat-comum',
        tipoMovimentacao: 'ADICAO_COMUM',
        codigoOperacao: '221',
        quantidade: 5,
        codigo: 'MAT01',
        descricao: 'Cabo 10mm',
      },
    ]);

    component['submit']();

    expect(criarMock).toHaveBeenCalledTimes(1);
    const callArgs = (criarMock.mock.calls as any)[0];
    expect(callArgs[0].idObra).toBe('obra-comum');
    expect(callArgs[0].ocorrencia).toBeUndefined();
    expect(callArgs[0].itens[0].tipoMovimentacao).toBe('ADICAO_COMUM');
    expect(router.navigate).toHaveBeenCalledWith(['/almoxarifado/solicitacoes-tecnicas', 'sol-1']);
  });

  it('submete criação com múltiplos tipos e avança para a etapa 4 com listagem independente', () => {
    criarMock.mockReturnValue(
      of<CriacaoSolicitacaoTecnicaResponse>({
        solicitacoesCriadas: [
          { id: 'sol-221', tipo: 'ADICAO_COMUM' as const, codigo: '221' as const },
          { id: 'sol-222', tipo: 'DEVOLUCAO_COMUM' as const, codigo: '222' as const },
        ],
      }),
    );

    component['selectObra']('obra-comum');
    component['idAlmoxarifado'].set('almox-1');
    component['itens'].set([
      {
        idMaterial: 'mat-comum',
        tipoMovimentacao: 'ADICAO_COMUM',
        codigoOperacao: '221',
        quantidade: 5,
        codigo: 'MAT01',
        descricao: 'Cabo 10mm',
      },
      {
        idMaterial: 'mat-comum',
        tipoMovimentacao: 'DEVOLUCAO_COMUM',
        codigoOperacao: '222',
        quantidade: 2,
        codigo: 'MAT01',
        descricao: 'Cabo 10mm',
      },
    ]);

    component['submit']();

    expect(criarMock).toHaveBeenCalledTimes(1);
    expect(component['activeStep']()).toBe(4);
    expect(component['solicitacoesCriadas']()).toHaveLength(2);
    expect(notificationsMock.success).toHaveBeenCalled();
  });

  it('serializa data civil e omite ocorrencia para OBRA', () => {
    component['selectObra']('obra-comum');
    component['idAlmoxarifado'].set('almox-1');
    component['dataPrevista'].set(new Date(2026, 8, 1));
    component['itens'].set([
      {
        idMaterial: 'mat-comum',
        tipoMovimentacao: 'ADICAO_COMUM',
        codigoOperacao: '221',
        quantidade: 1,
        codigo: 'MAT01',
        descricao: 'Cabo 10mm',
      },
    ]);

    component['submit']();

    const request = (criarMock.mock.calls as any)[0][0];
    expect(request.dataPrevista).toBe('2026-09-01');
    expect(Object.prototype.hasOwnProperty.call(request, 'ocorrencia')).toBe(false);
  });

  it('inicializa no modo devolucao com tipo DEVOLUCAO_COMUM quando snapshot indica modo devolucao', () => {
    const route = TestBed.inject(ActivatedRoute);
    (route.snapshot as any).data = { modo: 'devolucao' };

    const compFixture = TestBed.createComponent(SolicitacaoTecnicaFormComponent);
    const comp = compFixture.componentInstance;
    comp.ngOnInit();

    expect(comp['modo']()).toBe('devolucao');
    expect(comp['tipoMovimentacao']()).toBe('DEVOLUCAO_COMUM');
    expect(comp['breadcrumb']()[2].label).toBe('Nova Devolução');
  });
});
