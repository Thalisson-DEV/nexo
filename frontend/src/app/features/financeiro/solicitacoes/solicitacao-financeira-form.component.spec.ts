import { describe, it, expect, beforeEach, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { of } from 'rxjs';

import { SolicitacaoFinanceiraFormComponent } from './solicitacao-financeira-form.component';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { LookupStore } from '../../../core/lookup/store/lookup.store';
import {
  FormularioResumo,
  CampoFormulario,
  FormularioSchema,
  SolicitacaoFinanceira,
} from '../../../core/models/financeiro.models';

describe('SolicitacaoFinanceiraFormComponent', () => {
  let component: SolicitacaoFinanceiraFormComponent;
  let fixture: ComponentFixture<SolicitacaoFinanceiraFormComponent>;
  let router: { navigate: ReturnType<typeof vi.fn> };
  let messages: { add: ReturnType<typeof vi.fn> };
  let financeiroService: {
    listarVariantes: ReturnType<typeof vi.fn>;
    buscarSchema: ReturnType<typeof vi.fn>;
    criarSolicitacao: ReturnType<typeof vi.fn>;
    atualizarSolicitacao: ReturnType<typeof vi.fn>;
    submeter: ReturnType<typeof vi.fn>;
    buscarSolicitacao: ReturnType<typeof vi.fn>;
    buscarPrePreenchimento: ReturnType<typeof vi.fn>;
  };

  const mockVariantes: FormularioResumo[] = [
    {
      id: 'var-1',
      tipoSolicitacao: 'COMPRA',
      codigo: 'PADRAO',
      titulo: 'Compra Padrão',
      descricao: 'Solicitação comum',
      padrao: true,
      ativo: true,
    },
    {
      id: 'var-2',
      tipoSolicitacao: 'COMPRA',
      codigo: 'URGENTE',
      titulo: 'Compra Urgente',
      descricao: 'Solicitação emergencial',
      padrao: false,
      ativo: true,
    },
  ];

  const mockSolicitacao: SolicitacaoFinanceira = {
    id: 'sol-123',
    numero: 42,
    status: 'SOLICITADO',
    formulario: mockVariantes[0],
    valores: {},
    valor: 1500.5,
    usuarioSolicitanteId: 'usr-1',
    usuarioSolicitante: 'Operador Teste',
    nomeColaboradorSolicitante: 'João Silva',
    desfecho: {},
    documentos: [],
    acoes: {
      podeEditar: false,
      podeSubmeter: false,
      podeDecidir: false,
      podeCancelar: true,
      podeDesfazerDecisao: false,
    },
    ativo: true,
    dataCriacao: '2026-03-10T10:00:00Z',
  };

  let queryParams: Record<string, string> = {};
  const campoDescricao = {
    id: 'c-1',
    chave: 'descricao',
    rotulo: 'Descrição',
    tipoCampo: 'TEXTO',
    campoCanonico: 'DESCRICAO',
    obrigatorio: false,
    ordem: 1,
    grupo: 'IDENTIFICACAO',
    grupoRotulo: 'Identificação',
    opcoes: [],
  } as CampoFormulario;

  const criar = () => {
    fixture = TestBed.createComponent(SolicitacaoFinanceiraFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  beforeEach(async () => {
    queryParams = {};
    messages = {
      add: vi.fn(),
    };
    financeiroService = {
      listarVariantes: vi.fn().mockReturnValue(of(mockVariantes)),
      buscarSchema: vi.fn((id: string) =>
        of({
          ...(mockVariantes.find((v) => v.id === id) ?? mockVariantes[0]),
          campos: id === 'var-2' ? [campoDescricao] : [],
        } as FormularioSchema),
      ),
      criarSolicitacao: vi.fn().mockReturnValue(of(mockSolicitacao)),
      atualizarSolicitacao: vi.fn().mockReturnValue(of(mockSolicitacao)),
      submeter: vi.fn().mockReturnValue(of(mockSolicitacao)),
      buscarSolicitacao: vi.fn().mockReturnValue(of(mockSolicitacao)),
      buscarPrePreenchimento: vi.fn().mockReturnValue(of({ valores: {}, camposHerdados: [], compra: null })),
    };

    await TestBed.configureTestingModule({
      imports: [SolicitacaoFinanceiraFormComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        providePrimeNG(),
        provideRouter([]),
        { provide: MessageService, useValue: messages },
        { provide: FinanceiroService, useValue: financeiroService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: { tipo: 'COMPRA' },
              paramMap: {
                get: vi.fn().mockReturnValue(null),
              },
              get queryParamMap() {
                return convertToParamMap(queryParams);
              },
            },
          },
        },
        {
          provide: LookupStore,
          useValue: {
            searchCollaborators: vi.fn().mockReturnValue(of({ items: [], nextCursor: null, hasMore: false })),
          },
        },
      ],
    }).compileComponents();

    router = TestBed.inject(Router) as unknown as { navigate: ReturnType<typeof vi.fn> };
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    criar();
  });

  it('deve inicializar no passo 1 com a variante padrão selecionada', () => {
    expect(component['activeStep']()).toBe(1);
    expect(component['variantes']().length).toBe(2);
    expect(component['varianteSelecionada']()).toBe('var-1');
  });

  it('ao chamar submeter(), deve avançar para o passo 5 (Conclusão) e exibir toast em vez de navegar direto', () => {
    // Simula rascunho salvo pronto para submissão
    component['solicitacao'] = { ...mockSolicitacao, status: 'RASCUNHO' };
    component['activeStep'].set(4);

    component['submeter']();

    expect(financeiroService.submeter).toHaveBeenCalledWith('COMPRA', 'sol-123');
    expect(component['activeStep']()).toBe(5);
    expect(router.navigate).not.toHaveBeenCalled();
    expect(messages.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
        summary: 'Solicitação enviada para aprovação',
      })
    );
  });

  it('no passo 5, abrirDetalhe() deve navegar para os detalhes da solicitação criada', () => {
    component['solicitacao'] = mockSolicitacao;
    component['activeStep'].set(5);

    component['abrirDetalhe']();

    expect(router.navigate).toHaveBeenCalledWith(['/financeiro', 'compras', 'sol-123']);
  });

  it('no passo 5, voltar() deve navegar para a lista de solicitações', () => {
    component['voltar']();

    expect(router.navigate).toHaveBeenCalledWith(['/financeiro', 'compras']);
  });

  it('no passo 5, novaSolicitacao() deve resetar o formulário e retornar ao passo 1', () => {
    component['solicitacao'] = mockSolicitacao;
    component['activeStep'].set(5);
    component['form'].addControl('teste', new FormControl('teste'));

    component['novaSolicitacao']();

    expect(component['solicitacao']).toBeUndefined();
    expect(component['activeStep']()).toBe(1);
    expect(component['varianteSelecionada']()).toBe('var-1');
    expect(component['editando']()).toBe(false);
  });

  it('restaura o preenchimento (só em memória) ao voltar para o passo 2', () => {
    component['selecionarVariante'](mockVariantes[1]);
    fixture.detectChanges();
    component['form'].get('descricao')!.setValue('Cadeiras para a base');
    component['form'].markAsDirty();
    fixture.destroy();

    queryParams = { passo: '2' };
    criar();
    fixture.detectChanges();
    expect(financeiroService.buscarSchema).toHaveBeenLastCalledWith('var-2');

    expect(component['activeStep']()).toBe(2);
    expect(component['varianteSelecionada']()).toBe('var-2');
    expect(component['form'].get('descricao')!.value).toBe('Cadeiras para a base');
    expect(sessionStorage.length).toBe(0);
  });

  it('não restaura rascunho depois de cancelar pelo Voltar', () => {
    component['selecionarVariante'](mockVariantes[1]);
    component['form'].addControl('descricao', new FormControl('x'));
    component['form'].markAsDirty();
    component['voltar']();
    fixture.destroy();

    queryParams = { passo: '2' };
    criar();
    expect(component['activeStep']()).toBe(1);
    expect(component['varianteSelecionada']()).toBe('var-1');
  });

  it('deve truncar o identificador nos primeiros 8 caracteres', () => {
    expect(component['idCurto']('3fa85f64-5717-4562-b3fc-2c963f66afa6')).toBe('3fa85f64');
    expect(component['idCurto']('abc')).toBe('abc');
    expect(component['idCurto'](null)).toBe('');
  });
});
