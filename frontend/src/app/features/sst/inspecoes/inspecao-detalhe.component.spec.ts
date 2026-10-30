import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ActivatedRoute } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../../../core/services/auth.service';
import { SstService } from '../../../core/services/sst.service';
import { RhService } from '../../../core/services/rh.service';
import { UsuarioLookupService } from '../../../core/services/usuario-lookup.service';
import { InspecaoSegurancaResponse, TipoInspecaoResponse } from '../../../core/models/sst.models';
import { DatabaseService } from '../../../core/services/database.service';
import { AnexoSyncService } from '../../../core/services/anexo-sync.service';
import { InspecaoDetalheComponent } from './inspecao-detalhe.component';

describe('InspecaoDetalheComponent', { timeout: 15000 }, () => {
  const mockTipoInspecao: TipoInspecaoResponse = {
    id: 'tipo-1',
    nome: 'Campo',
    descricao: 'Inspeções de campo',
    ordem: 1,
    ativo: true,
    dataCriacao: '2026-09-14T00:00:00Z'
  };

  const mockInspecao: InspecaoSegurancaResponse = {
    id: 'insp-12345678',
    tipoInspecao: mockTipoInspecao,
    formulario: { id: 'form-1', titulo: 'Checklist Veicular', ativo: true, totalPerguntas: 5 },
    processo: { id: 'proc-1', nome: 'Manutenção' } as any,
    baseOperacional: { id: 'base-1', nome: 'Base Central' } as any,
    idInspetor: 'user-1',
    nomeInspetor: 'Carlos Silva',
    dataRealizacao: '2026-09-14T10:00:00Z',
    status: 'CONCLUIDA',
    colaboradoresInspecionados: [
      { id: 'colab-1', nomeCompleto: 'João Silva' } as any,
      { id: 'colab-2', nomeCompleto: 'Maria Santos' } as any
    ],
    desvios: [
      {
        id: 'desvio-1',
        idPergunta: 'perg-1',
        idOpcaoResposta: 'op-1',
        gravidade: 'ALTA',
        status: 'ABERTO',
        colaboradores: [{ id: 'colab-1', nomeCompleto: 'João Silva' } as any]
      } as any
    ],
    respostas: [],
    evidencias: []
  };

  let buscarInspecaoPorId: ReturnType<typeof vi.fn>;
  let buscarFormularioPorId: ReturnType<typeof vi.fn>;
  let obterHistoricoInspecao: ReturnType<typeof vi.fn>;
  let atualizarCabecalhoInspecao: ReturnType<typeof vi.fn>;
  let currentUserMock: any;

  beforeEach(async () => {
    currentUserMock = signal({ id: 'user-1', nome: 'Carlos Silva', permissoes: ['sst:inspecao:read', 'sst:inspecao:write'] });
    buscarInspecaoPorId = vi.fn(() => of(mockInspecao));
    buscarFormularioPorId = vi.fn(() => of({ id: 'form-1', titulo: 'Checklist Veicular', grupos: [] }));
    obterHistoricoInspecao = vi.fn(() => of({
      eventos: [],
      integridade: [{ escopo: 'INSPECAO', idCadeia: 'insp-12345678', status: 'INTEGRO' }],
      page: { number: 0, size: 10, totalElements: 0, totalPages: 1 }
    }));
    atualizarCabecalhoInspecao = vi.fn(() => of({ ...mockInspecao, observacoes: 'Atualizado com sucesso' }));

    const rhMock = {
      listarProcessosAtivos: vi.fn(() => of({ content: [] })),
      listarBasesOperacionais: vi.fn(() => of({ content: [] })),
      listarVeiculos: vi.fn(() => of({ content: [] })),
      listarEquipes: vi.fn(() => of({ content: [] })),
      listarColaboradores: vi.fn(() => of({ content: [] })),
      buscarEquipePorId: vi.fn(() => of({ membros: [] }))
    };

    const usuarioLookupMock = {
      resolverNome: vi.fn((a) => (typeof a === 'string' ? a : a?.nome || 'Sistema'))
    };

    await TestBed.configureTestingModule({
      imports: [InspecaoDetalheComponent],
      providers: [
        provideRouter([]),
        MessageService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'id' ? 'insp-12345678' : null)
              }
            },
            paramMap: of({
              get: (key: string) => (key === 'id' ? 'insp-12345678' : null)
            })
          }
        },
        {
          provide: DatabaseService,
          useValue: {
            anexoQueue: {
              toArray: () => Promise.resolve([])
            }
          }
        },
        {
          provide: AnexoSyncService,
          useValue: {
            pendentes: signal([]),
            sincronizados: signal([]),
            refreshPendentes: vi.fn(),
            flush: vi.fn(),
            enfileirar: vi.fn(),
            remover: vi.fn(),
            pendentesDoDesvio: vi.fn().mockReturnValue([])
          }
        },
        {
          provide: AuthService,
          useValue: { currentUser: currentUserMock }
        },
        {
          provide: RhService,
          useValue: rhMock
        },
        {
          provide: UsuarioLookupService,
          useValue: usuarioLookupMock
        },
        {
          provide: SstService,
          useValue: {
            buscarInspecaoPorId,
            buscarFormularioPorId,
            obterHistoricoInspecao,
            atualizarCabecalhoInspecao,
            listarOpcoesTiposInspecao: vi.fn(() => of([])),
            tratarDesvio: vi.fn()
          }
        }
      ]
    }).compileComponents();
  });

  it('carrega a inspeção e exibe o tipo de inspeção no bloco de identificação', async () => {
    const fixture = TestBed.createComponent(InspecaoDetalheComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(buscarInspecaoPorId).toHaveBeenCalledWith('insp-12345678');

    const elementosKv = Array.from(fixture.nativeElement.querySelectorAll('.kv')) as HTMLElement[];
    const kvTipo = elementosKv.find((el) => el.querySelector('.kv-l')?.textContent?.trim() === 'Tipo');
    expect(kvTipo).toBeTruthy();
    expect(kvTipo?.querySelector('.kv-v')?.textContent?.trim()).toBe('Campo');
  });

  it('permite editar cabeçalho apenas quando o usuário logado é o criador da inspeção', async () => {
    const fixture = TestBed.createComponent(InspecaoDetalheComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    // Usuário 'user-1' é o criador da inspeção
    expect((fixture.componentInstance as any).podeEditarCabecalho()).toBe(true);

    // Se o usuário logado for outro
    currentUserMock.set({ id: 'outro-user', nome: 'Outro Usuário', permissoes: ['sst:inspecao:read', 'sst:inspecao:write'] });
    fixture.detectChanges();
    expect((fixture.componentInstance as any).podeEditarCabecalho()).toBe(false);
  });

  it('identifica corretamente colaborador que possui desvio associado impedindo remoção', async () => {
    const fixture = TestBed.createComponent(InspecaoDetalheComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const comp = fixture.componentInstance as any;
    // colab-1 tem desvio associado no mock
    expect(comp.colaboradorPossuiDesvio('colab-1')).toBe(true);
    // colab-2 não tem desvio
    expect(comp.colaboradorPossuiDesvio('colab-2')).toBe(false);
  });

  it('submete atualização de cabeçalho com sucesso ao preencher formulário válido', async () => {
    const fixture = TestBed.createComponent(InspecaoDetalheComponent);
    fixture.detectChanges();
    await fixture.whenStable();

    const comp = fixture.componentInstance as any;
    comp.abrirEdicaoCabecalho();
    expect(comp.dialogEdicaoCabecalho).toBe(true);

    comp.justificativaEdicao = 'Correção de dados do cabeçalho da inspeção';
    expect(comp.formularioEdicaoValido()).toBe(true);

    comp.salvarEdicaoCabecalho();
    expect(atualizarCabecalhoInspecao).toHaveBeenCalled();
  });
});
