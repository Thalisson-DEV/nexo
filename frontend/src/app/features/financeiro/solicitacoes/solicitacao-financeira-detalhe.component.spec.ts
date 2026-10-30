import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { providePrimeNG } from 'primeng/config';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ConfirmationService, MessageService } from 'primeng/api';
import {
  CampoFormulario,
  EventoSolicitacao,
  FormularioSchema,
  SolicitacaoFinanceira
} from '../../../core/models/financeiro.models';
import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { SolicitacaoFinanceiraDetalheComponent } from './solicitacao-financeira-detalhe.component';

function campo(chave: string, campoCanonico: string, grupo: string, tipoCampo = 'TEXTO', ordem = 1): CampoFormulario {
  return {
    id: chave,
    chave,
    rotulo: chave,
    tipoCampo,
    campoCanonico,
    obrigatorio: false,
    ordem,
    grupo,
    grupoRotulo: grupo,
    opcoes: []
  } as unknown as CampoFormulario;
}

const schema: FormularioSchema = {
  id: 'form-001',
  tipoSolicitacao: 'COMPRA',
  codigo: 'COMPRA_PADRAO',
  titulo: 'Solicitação de Compra',
  padrao: true,
  ativo: true,
  campos: [
    campo('base', 'BASE', 'IDENTIFICACAO', 'REFERENCIA', 1),
    campo('quantidade', 'QUANTIDADE', 'VALORES', 'DECIMAL', 2),
    campo('descricao', 'DESCRICAO', 'DESCRICAO', 'TEXTO_LONGO', 3)
  ]
} as unknown as FormularioSchema;

const base: SolicitacaoFinanceira = {
  id: 'sol-001',
  numero: 8,
  status: 'SOLICITADO',
  formulario: { id: 'form-001', tipoSolicitacao: 'COMPRA', codigo: 'COMPRA_PADRAO', titulo: 'Solicitação de Compra', padrao: true, ativo: true },
  valores: {
    base: { valor: 'base-1', exibicao: 'JUAZEIRO' },
    quantidade: { valor: 2, exibicao: '2' },
    descricao: { valor: 'Pneus', exibicao: 'Pneus' }
  },
  valor: 123,
  fornecedor: { id: 'f-1', nome: 'HC PNEUS', razaoSocial: null, cadastrado: true },
  usuarioSolicitanteId: 'user-001',
  usuarioSolicitante: 'joao',
  nomeColaboradorSolicitante: 'João Silva',
  dataSubmissao: '2026-10-01T10:00:00Z',
  desfecho: {},
  documentos: [],
  acoes: { podeEditar: true, podeSubmeter: false, podeDecidir: true, podeCancelar: true, podeDesfazerDecisao: false },
  ativo: true,
  dataCriacao: '2026-10-01T09:00:00Z'
} as unknown as SolicitacaoFinanceira;

const aprovada: SolicitacaoFinanceira = {
  ...base,
  status: 'APROVADO',
  desfecho: { usuarioDecisaoId: 'user-002', usuarioDecisao: 'diretor', dataDecisao: '2026-10-02T10:00:00Z', valorAprovado: 100 },
  acoes: { podeEditar: false, podeSubmeter: false, podeDecidir: false, podeCancelar: false, podeDesfazerDecisao: true }
};

const desfeita: EventoSolicitacao = {
  id: 'ev-3',
  tipo: 'DECISAO_DESFEITA',
  rotulo: 'Decisão desfeita',
  statusAnterior: 'APROVADO',
  statusNovo: 'SOLICITADO',
  usuarioId: 'user-002',
  usuario: 'Carla Diretora',
  valor: 100,
  texto: 'Valor digitado errado',
  ocorridoEm: '2026-10-03T10:00:00Z'
};

describe('SolicitacaoFinanceiraDetalheComponent', () => {
  let fixture: ComponentFixture<SolicitacaoFinanceiraDetalheComponent>;
  let component: any;
  let service: Record<string, ReturnType<typeof vi.fn>>;

  const montar = async (
    solicitacao: SolicitacaoFinanceira = base,
    historico: EventoSolicitacao[] = [],
    permissoes = ['financeiro:compra:aprovar'],
    query: Record<string, string> = {}
  ) => {
    service = {
      buscarSolicitacao: vi.fn(() => of(solicitacao)),
      buscarSchema: vi.fn(() => of(schema)),
      historico: vi.fn(() => of(historico)),
      submeter: vi.fn(),
      aprovar: vi.fn(() => of(aprovada)),
      rejeitar: vi.fn(() => of({ ...base, status: 'REJEITADO' })),
      cancelar: vi.fn(() => of({ ...base, status: 'CANCELADO' })),
      desfazerDecisao: vi.fn(() => of(base)),
      listarSolicitacoes: vi.fn(() => of({ content: [], page: { totalElements: 0, number: 0, size: 30, totalPages: 0 } }))
    };

    await TestBed.configureTestingModule({
      imports: [SolicitacaoFinanceiraDetalheComponent],
      providers: [
        provideNoopAnimations(),
        providePrimeNG(),
        provideRouter([]),
        ConfirmationService,
        MessageService,
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { data: { tipo: 'COMPRA' }, queryParamMap: convertToParamMap(query) },
            paramMap: of(convertToParamMap({ id: 'sol-001' })),
            queryParamMap: of(convertToParamMap({}))
          }
        },
        { provide: FinanceiroService, useValue: { ...service, navegadorAberto: signal(false) } },
        { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
      ]
    }).compileComponents();

    vi.spyOn(TestBed.inject(Router), 'navigate').mockImplementation(() => Promise.resolve(true));
    fixture = TestBed.createComponent(SolicitacaoFinanceiraDetalheComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  };

  const texto = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  it('carrega a solicitação, o formulário e o histórico e monta o relatório', async () => {
    await montar();

    expect(service['buscarSolicitacao']).toHaveBeenCalledWith('COMPRA', 'sol-001');
    expect(service['buscarSchema']).toHaveBeenCalledWith('form-001');
    expect(service['historico']).toHaveBeenCalledWith('COMPRA', 'sol-001');
    expect(texto()).toContain('Solicitação de Compra nº 8');
    expect(texto()).toContain('Aguardando decisão');
  });

  it('mostra o erro de carregamento com a opção de tentar de novo', async () => {
    await montar();
    service['buscarSolicitacao'].mockReturnValueOnce(throwError(() => new Error('falhou')));

    component.carregar();
    fixture.detectChanges();

    expect(texto()).toContain('Solicitação indisponível');
  });

  it('anuncia a decisão desfeita, com quem desfez e o motivo', async () => {
    await montar(base, [desfeita]);

    expect(component.decisaoDesfeita()).toEqual(desfeita);
    expect(texto()).toContain('Decisão desfeita · aguardando nova decisão');
    expect(texto()).toContain('Carla Diretora');
    expect(texto()).toContain('Valor digitado errado');
  });

  it('oferece aprovar e rejeitar só a quem tem alçada', async () => {
    await montar(base, [], []);

    expect(texto()).not.toContain('APROVAR');
    expect(texto()).toContain('Sem permissão para decidir');
  });

  it('oferece desfazer a decisão de uma aprovada e chama o serviço com o motivo', async () => {
    await montar(aprovada);
    expect(texto()).toContain('DESFAZER DECISÃO');

    component.abrirAcao('DESFAZER');
    component.confirmar({ acao: 'DESFAZER', texto: 'Fornecedor trocou a proposta', valorAprovado: null });

    expect(service['desfazerDecisao']).toHaveBeenCalledWith('COMPRA', 'sol-001', 'Fornecedor trocou a proposta');
    expect(component.solicitacao().status).toBe('SOLICITADO');
    expect(service['historico']).toHaveBeenCalledTimes(2);
  });

  it('aprova com o valor e a observação vindos do drawer', async () => {
    await montar();

    component.confirmar({ acao: 'APROVAR', texto: 'Desconto negociado', valorAprovado: 100 });

    expect(service['aprovar']).toHaveBeenCalledWith('COMPRA', 'sol-001', 100, 'Desconto negociado');
    expect(component.solicitacao().status).toBe('APROVADO');
  });

  it('estica o bloco que ficaria sozinho na linha', async () => {
    await montar();

    // Identificação sai para os dados gerais; Valores fica sem par antes da Descrição longa.
    const blocos = component.blocos();
    expect(blocos.map((b: { grupo: string; largo: boolean }) => [b.grupo, b.largo])).toEqual([
      ['VALORES', true],
      ['DESCRICAO', true]
    ]);
  });

  it('depois da decisão só mantém os pontos de atenção que ainda pedem ação', async () => {
    await montar(aprovada);

    expect(component.atencao()).toEqual([]);
  });

  it('abre a aprovação já pronta quando chega pelo botão do e-mail', async () => {
    await montar(base, [], ['financeiro:compra:aprovar'], { acao: 'aprovar' });

    expect(component.acao()).toBe('APROVAR');
    expect(component.drawerAberto()).toBe(true);
  });

  it('não abre decisão pelo e-mail quando a solicitação já foi decidida', async () => {
    await montar(aprovada, [], ['financeiro:compra:aprovar'], { acao: 'rejeitar' });

    expect(component.drawerAberto()).toBe(false);
  });

  it('não abre decisão pelo e-mail para quem não tem alçada', async () => {
    await montar(base, [], [], { acao: 'aprovar' });

    expect(component.drawerAberto()).toBe(false);
  });
});
