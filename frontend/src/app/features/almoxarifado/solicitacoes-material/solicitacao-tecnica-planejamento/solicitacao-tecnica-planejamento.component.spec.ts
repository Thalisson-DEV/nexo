import '@angular/compiler';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationService } from '@ces/ui';
import { AlmoxarifadoService } from '../../../../core/services/almoxarifado.service';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { LookupStore } from '../../../../core/lookup/store/lookup.store';
import {
  SolicitacaoMaterialDetalheResponse,
  TipoSolicitacaoTecnica,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { SolicitacaoTecnicaPlanejamentoComponent } from './solicitacao-tecnica-planejamento.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('SolicitacaoTecnicaPlanejamentoComponent', () => {
  let fixture: ComponentFixture<SolicitacaoTecnicaPlanejamentoComponent>;
  let component: SolicitacaoTecnicaPlanejamentoComponent;
  let substituirPlanejamento: ReturnType<typeof vi.fn>;
  let listarMembrosAtivos: ReturnType<typeof vi.fn>;
  const notifications = {
    error: vi.fn(),
    warning: vi.fn(),
    success: vi.fn(),
  };

  const material = (id: string, codigo: string, descricao: string) => ({
    id,
    codigo,
    descricao,
    detalhaSerie: false,
    ativo: true,
  });

  const criarDetalhe = (
    tipoObra: 'OBRA' | 'CORRETIVA' = 'OBRA',
    tipoTecnico: TipoSolicitacaoTecnica = 'ADICAO_COMUM',
    overrides: Record<string, unknown> = {},
  ): SolicitacaoMaterialDetalheResponse =>
    ({
      id: 'sol-1',
      status: 'APROVADO',
      itens: [
        {
          id: 'item-1',
          material: material('mat-1', 'MAT-01', 'Cabo de cobre'),
          quantidade: 2,
          quantidadeEntregue: 0,
          quantidadePendente: 2,
          tipoMovimentacao: tipoTecnico,
        },
      ],
      tecnico: {
        tipoTecnico,
        tipoObra,
        idObra: 'obra-1',
        ocorrencia: null,
        idAlmoxarifadoAtendimento: 'almox-1',
        idEquipe: 'eq-1',
        idColaboradorSaque: 'colab-1',
        saqueForaEquipe: false,
        dataPrevista: null,
        observacao: null,
        statusSeparacao: 'AGUARDANDO_AGENDAMENTO',
        reserva: null,
        totais: {
          solicitada: 2,
          entregaBruta: 0,
          entregaEstornada: 0,
          entregaLiquida: 0,
          devolucaoBruta: 0,
          devolucaoEstornada: 0,
          devolucaoLiquida: 0,
          pendente: 2,
        },
        operacoes: [],
        versao: 7,
        acoesPermitidas: ['EDITAR_PLANEJAMENTO'],
        atrasada: false,
        encerradaEm: null,
        idUsuarioEncerramento: null,
        motivoEncerramento: null,
        ...overrides,
      },
      contexto: 'TECNICO',
      versao: 7,
      statusSeparacao: 'AGUARDANDO_AGENDAMENTO',
      acoesPermitidas: ['EDITAR_PLANEJAMENTO'],
    }) as SolicitacaoMaterialDetalheResponse;

  const configurar = async (detail = criarDetalhe()) => {
    TestBed.resetTestingModule();
    substituirPlanejamento = vi.fn(() => of({ solicitacaoAtualizada: detail }));
    listarMembrosAtivos = vi.fn((equipeId: string) =>
      of(
        equipeId === 'eq-2'
          ? [
              {
                id: 'member-2',
                idColaborador: 'colab-2',
                nomeColaborador: 'Colaborador Dois',
                vinculoAtivo: true,
              },
            ]
          : [
              {
                id: 'member-1',
                idColaborador: 'colab-1',
                nomeColaborador: 'Colaborador Um',
                funcao: 'RESPONSAVEL',
                vinculoAtivo: true,
              },
              {
                id: 'member-inactive',
                idColaborador: 'colab-inactive',
                nomeColaborador: 'Colaborador Inativo',
                vinculoAtivo: false,
              },
            ],
      ),
    );
    vi.clearAllMocks();

    TestBed.configureTestingModule({
      imports: [SolicitacaoTecnicaPlanejamentoComponent],
      providers: [
        {
          provide: SolicitacaoMaterialTecnicaService,
          useValue: { substituirPlanejamento },
        },
        {
          provide: AlmoxarifadoService,
          useValue: {
            buscarMaterialPorId: (id: string) =>
              of(
                material(
                  id,
                  id === 'mat-2' ? 'MAT-02' : 'MAT-01',
                  id === 'mat-2' ? 'Cabo reserva' : 'Cabo de cobre',
                ),
              ),
            buscarEstoquePorId: () =>
              of({ id: 'almox-1', nomeIdentificador: 'Almoxarifado Central' }),
            listarEstoquesAtivos: () =>
              of({ content: [{ id: 'almox-1', nomeIdentificador: 'Almoxarifado Central' }] }),
          },
        },
        {
          provide: ObrasService,
          useValue: {
            buscarObraPorId: () =>
              of({ id: 'obra-1', numero: 'OB-100', titulo: 'Obra Central', tipoObra: 'OBRA' }),
          },
        },
        {
          provide: RhService,
          useValue: {
            listarEquipes: () =>
              of({ content: [{ id: 'eq-1', codigo: 'EQ-01', tipoEquipe: { nome: 'Campo' } }] }),
            listarMembrosAtivos,
            buscarColaboradorPorId: (id: string) =>
              of({
                id,
                nomeCompleto: id === 'colab-2' ? 'Colaborador Dois' : 'Colaborador Um',
                ativo: true,
              }),
          },
        },
        {
          provide: LookupStore,
          useValue: {
            searchMaterials: () =>
              of({
                items: [
                  { value: 'mat-1', label: 'MAT-01 — Cabo de cobre' },
                  { value: 'mat-2', label: 'MAT-02 — Cabo reserva' },
                ],
                nextCursor: null,
                hasMore: false,
              }),
            searchCollaborators: () =>
              of({
                items: [
                  { value: 'colab-1', label: 'Colaborador Um' },
                  { value: 'colab-2', label: 'Colaborador Dois' },
                ],
                nextCursor: null,
                hasMore: false,
              }),
            searchStocks: () =>
              of({
                items: [{ value: 'almox-1', label: 'Almoxarifado Central' }],
                nextCursor: null,
                hasMore: false,
              }),
          },
        },
        { provide: NotificationService, useValue: notifications },
      ],
    });
    TestBed.overrideComponent(SolicitacaoTecnicaPlanejamentoComponent, {
      set: {
        imports: [],
        template: `
          <span>Defina o atendimento</span>
          <span>Confira os materiais</span>
          <span>{{ fixedTypeLabel() }}</span>
          @if (occurrenceRequired()) { <span>Ocorrência / OS</span> }
        `,
      },
    });
    await TestBed.compileComponents();

    fixture = TestBed.createComponent(SolicitacaoTecnicaPlanejamentoComponent);
    component = fixture.componentInstance;
    (component as any).detail = signal(detail);
    (component as any).visible = signal(true);
    fixture.detectChanges();
  };

  beforeEach(() => {
    notifications.error.mockClear();
    notifications.warning.mockClear();
    notifications.success.mockClear();
  });

  it('apresenta contexto, tipo numérico e uma lista única de materiais', async () => {
    await configurar();

    expect(fixture.nativeElement.textContent).toContain('Defina o atendimento');
    expect(fixture.nativeElement.textContent).toContain('Confira os materiais');
    expect(fixture.nativeElement.textContent).toContain('221');
    expect(fixture.nativeElement.textContent).not.toContain('Ocorrência / OS');
    expect(fixture.nativeElement.textContent).not.toContain('Etapa 1');
  });

  it('adiciona material novo com o tipo técnico fixo da solicitação', async () => {
    await configurar();

    component['onMaterialChange']('mat-2');
    component['itemQuantity'].set(1.5);
    component['addLine']();

    expect(component['lines']()).toHaveLength(2);
    expect(component['lines']()).toContainEqual(
      expect.objectContaining({
        idItem: null,
        idMaterial: 'mat-2',
        tipoMovimentacao: 'ADICAO_COMUM',
        quantidade: 1.5,
      }),
    );
  });

  it('lista apenas membros ativos da equipe e recarrega ao trocar a equipe', async () => {
    await configurar();

    expect(listarMembrosAtivos).toHaveBeenCalledWith('eq-1');
    expect(component['collaboratorSaqueOptions']()).toEqual([
      { label: 'Colaborador Um (Responsável da equipe)', value: 'colab-1' },
    ]);

    component['onTeamChange']('eq-2');

    expect(listarMembrosAtivos).toHaveBeenCalledWith('eq-2');
    expect(component['idColaboradorSaque']()).toBeNull();
    expect(component['collaboratorSaqueOptions']()).toEqual([
      { label: 'Colaborador Dois', value: 'colab-2' },
    ]);
  });

  it('exige colaborador ativo quando o saque fora da equipe está ativo mesmo sem equipe', async () => {
    await configurar();

    component['onTeamChange'](null);
    component['onSaqueForaEquipeChange'](true);
    component['externalCollaboratorLookup'].seedSelected([
      { value: 'colab-1', label: 'Colaborador Um' },
      { value: 'colab-2', label: 'Colaborador Dois' },
    ]);

    expect(component['saqueResponsavelRequired']()).toBe(true);
    expect(component['collaboratorSaqueOptions']()).toEqual([
      { label: 'Colaborador Um', value: 'colab-1' },
      { label: 'Colaborador Dois', value: 'colab-2' },
    ]);
    expect(component['validateStep1']()).toBe(false);
    expect(component['fieldError']('idColaboradorSaque')).toBe(
      'Selecione o responsável pelo saque.',
    );

    component['onColaboradorSaqueChange']('colab-2');

    expect(component['validateStep1']()).toBe(true);
  });

  it('revalida a seleção ao alternar entre colaborador externo e membro da equipe', async () => {
    await configurar();

    component['onSaqueForaEquipeChange'](true);
    component['externalCollaboratorLookup'].seedSelected([
      { value: 'colab-1', label: 'Colaborador Um' },
      { value: 'colab-2', label: 'Colaborador Dois' },
    ]);
    component['onColaboradorSaqueChange']('colab-2');
    expect(component['validateStep1']()).toBe(true);

    component['onSaqueForaEquipeChange'](false);

    expect(component['idColaboradorSaque']()).toBeNull();
    expect(component['validateStep1']()).toBe(true);
    expect(component['fieldError']('idColaboradorSaque')).toBeUndefined();
  });

  it('exige justificativa no replanejamento e envia o snapshot completo', async () => {
    const detail = criarDetalhe('OBRA', 'DEVOLUCAO_COMUM', {
      dataPrevista: '2026-09-15',
      statusSeparacao: 'PRONTO_PARA_SEPARACAO',
      observacao: 'Separar por lote',
      idColaboradorSaque: 'colab-1',
      saqueForaEquipe: false,
    });
    await configurar(detail);

    component['submit']();
    expect(component['fieldError']('justificativa')).toBeTruthy();
    expect(substituirPlanejamento).not.toHaveBeenCalled();

    component['justification'].set('Ajuste solicitado pela equipe de campo.');
    component['submit']();

    expect(substituirPlanejamento).toHaveBeenCalledWith('sol-1', {
      versaoEsperada: 7,
      idObra: 'obra-1',
      ocorrencia: null,
      idAlmoxarifadoAtendimento: 'almox-1',
      idEquipe: 'eq-1',
      idColaboradorSaque: 'colab-1',
      saqueForaEquipe: false,
      dataPrevista: '2026-09-15',
      observacao: 'Separar por lote',
      justificativa: 'Ajuste solicitado pela equipe de campo.',
      itens: [
        {
          idItem: 'item-1',
          idMaterial: 'mat-1',
          tipoMovimentacao: 'DEVOLUCAO_COMUM',
          quantidade: 2,
        },
      ],
    });
  });

  it('normaliza a ocorrência para o comando de planejamento', async () => {
    const detail = criarDetalhe('CORRETIVA');
    await configurar(detail);

    component['occurrence'].set('  oc   1042  ');
    component['submit']();

    expect(substituirPlanejamento).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({ ocorrencia: 'OC 1042' }),
    );
  });

  it('envia colaborador e indicador de saque fora da equipe no payload do planejamento', async () => {
    await configurar();

    component['onTeamChange'](null);
    component['onSaqueForaEquipeChange'](true);
    component['externalCollaboratorLookup'].seedSelected([
      { value: 'colab-1', label: 'Colaborador Um' },
      { value: 'colab-2', label: 'Colaborador Dois' },
    ]);
    component['onColaboradorSaqueChange']('colab-2');
    component['submit']();

    expect(substituirPlanejamento).toHaveBeenCalledWith(
      'sol-1',
      expect.objectContaining({
        idEquipe: null,
        idColaboradorSaque: 'colab-2',
        saqueForaEquipe: true,
      }),
    );
  });
});
