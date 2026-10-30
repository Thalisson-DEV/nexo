import '@angular/compiler';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { ChamadoDetalheComponent } from './chamado-detalhe.component';
import { ChamadosService } from '../../../core/services/chamados.service';
import { UsuariosService } from '../../../core/services/usuarios.service';
import { Chamado, InteracaoChamado } from '../../../core/models/chamado.models';
import { UsuarioResponse } from '../../../core/models/iam.models';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('ChamadoDetalheComponent', () => {
  let fixture: ComponentFixture<ChamadoDetalheComponent>;
  let component: ChamadoDetalheComponent;
  let chamadosService: any;
  let usuariosService: any;

  const mockChamado: Chamado = {
    id: 'chamado-1',
    protocolo: 'CHM-2026-0001',
    assunto: 'Problema na impressora',
    descricao: 'Não está imprimindo',
    prioridade: 'NORMAL',
    status: 'EM_ATENDIMENTO',
    usuarioSolicitanteId: 'user-solicitante',
    usuarioResponsavelId: 'user-responsavel',
    solucao: null,
    topico: {
      id: 'topico-1',
      codigo: 'TI',
      nome: 'Tecnologia da Informação',
      prioridadePadrao: 'NORMAL',
      equipePadrao: { id: 'eq-1', codigo: 'TI-SUP', nome: 'Suporte TI' },
      slaPrimeiraRespostaHoras: 4,
      slaResolucaoHoras: 24,
      pausarSlaAguardandoSolicitante: true,
      prazoReaberturaHoras: 48,
      ordemExibicao: 1,
      ativo: true
    },
    equipeAtendimento: {
      id: 'eq-1',
      codigo: 'TI-SUP',
      nome: 'Suporte TI'
    },
    dataAbertura: '2026-09-10T10:00:00Z',
    dataLimitePrimeiraResposta: '2026-09-10T14:00:00Z',
    dataLimiteResolucao: '2026-09-11T10:00:00Z',
    pausarSlaAguardandoSolicitante: true,
    prazoReaberturaHoras: 48,
    totalSegundosPausaSla: 0,
    dataUltimaInteracao: '2026-09-10T10:30:00Z',
    slaPrimeiraRespostaVencido: false,
    slaResolucaoVencido: false,
    slaPausado: false,
    anexos: [],
    dataCriacao: '2026-09-10T10:00:00Z'
  };

  const mockInteracoes: InteracaoChamado[] = [
    {
      id: 'int-1',
      tipo: 'ABERTURA',
      mensagem: 'Chamado aberto',
      interno: false,
      usuarioAutorId: 'user-solicitante',
      dataEvento: '2026-09-10T10:00:00Z'
    }
  ];

  const mockUsuario: UsuarioResponse = {
    id: 'user-responsavel',
    login: 'tecnico.ti',
    email: 'tecnico@empresa.com',
    tipo: 'ADMINISTRATIVO',
    perfil: { id: 'p1', nome: 'Admin' } as any,
    tema: 'light',
    corPrimaria: 'emerald',
    animacoesAtivadas: true,
    ativo: true
  };

  beforeEach(async () => {
    TestBed.resetTestingModule();
    chamadosService = {
      buscar: vi.fn().mockReturnValue(of(mockChamado)),
      listarInteracoes: vi.fn().mockReturnValue(of(mockInteracoes)),
      listarEquipes: vi.fn().mockReturnValue(of({ content: [], page: { number: 0, totalPages: 1, totalElements: 0, size: 30 } })),
      buscarEquipe: vi.fn().mockReturnValue(of({
        id: 'eq-1',
        codigo: 'TI-SUP',
        nome: 'Suporte TI',
        membros: ['user-responsavel'],
        ativo: true,
        dataCriacao: '2026-01-01T00:00:00Z'
      })),
      atualizarAtendimento: vi.fn().mockReturnValue(of({
        ...mockChamado,
        status: 'RESOLVIDO',
        solucao: 'Configuração restabelecida'
      }))
    };

    usuariosService = {
      listarUsuarios: vi.fn().mockReturnValue(of({ content: [mockUsuario], page: { number: 0, totalPages: 1, totalElements: 1, size: 30 } })),
      buscarUsuarioPorId: vi.fn().mockReturnValue(of(mockUsuario))
    };

    await TestBed.configureTestingModule({
      imports: [ChamadoDetalheComponent],
      providers: [
        provideRouter([]),
        { provide: ChamadosService, useValue: chamadosService },
        { provide: UsuariosService, useValue: usuariosService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: { get: () => 'chamado-1' },
              data: { modo: 'central' }
            }
          }
        },
        { provide: MessageService, useValue: { add: vi.fn() } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ChamadoDetalheComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve carregar os dados do chamado e renderizar detalhes', () => {
    expect(chamadosService.buscar).toHaveBeenCalledWith('chamado-1');
    expect(chamadosService.listarInteracoes).toHaveBeenCalledWith('chamado-1');
    expect(component['chamado']()?.assunto).toBe('Problema na impressora');
  });

  it('não deve lançar erro ao invocar responsaveisDisponiveis quando equipe não possui membros definidos', () => {
    component['equipeAtendimentoId'] = 'eq-1';
    component['equipeLookup'].setSeed([{
      id: 'eq-1',
      codigo: 'TI-SUP',
      nome: 'Suporte TI'
    } as any]);

    expect(() => {
      const responsaveis = component['responsaveisDisponiveis']();
      expect(responsaveis).toBeDefined();
    }).not.toThrow();
  });

  it('deve finalizar/atualizar o atendimento e recarregar os dados sem quebrar a tela', () => {
    component['abrirAtendimento']('RESOLVIDO');
    component['solucao'] = 'Problema resolvido com sucesso.';

    chamadosService.buscar.mockReturnValue(of({
      ...mockChamado,
      status: 'RESOLVIDO',
      solucao: 'Problema resolvido com sucesso.'
    }));

    component['salvarAtendimento']();

    expect(chamadosService.atualizarAtendimento).toHaveBeenCalledWith('chamado-1', {
      status: 'RESOLVIDO',
      solucao: 'Problema resolvido com sucesso.'
    });

    fixture.detectChanges();

    expect(component['chamado']()?.status).toBe('RESOLVIDO');
    expect(component['dialogAtendimento']).toBe(false);
  });
});
