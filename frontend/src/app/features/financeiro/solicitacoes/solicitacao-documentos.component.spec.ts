import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CesConfirmationService, NotificationService } from '@ces/ui';
import {
  DocumentoFinanceiro,
  DocumentoResumo,
  SolicitacaoFinanceira
} from '../../../core/models/financeiro.models';
import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { FileDownloadUtil } from '../../../core/utils/file-download.util';
import { SolicitacaoDocumentosComponent } from './solicitacao-documentos.component';

describe('SolicitacaoDocumentosComponent', () => {
  let fixture: ComponentFixture<SolicitacaoDocumentosComponent>;
  let component: SolicitacaoDocumentosComponent;

  let financeiroServiceMock: {
    anexarDocumento: ReturnType<typeof vi.fn>;
    baixarDocumentoDaSolicitacao: ReturnType<typeof vi.fn>;
    removerDocumentoDaSolicitacao: ReturnType<typeof vi.fn>;
    buscarDocumento: ReturnType<typeof vi.fn>;
    atualizarDocumento: ReturnType<typeof vi.fn>;
    conferirDocumento: ReturnType<typeof vi.fn>;
    apontarPendenciaDocumento: ReturnType<typeof vi.fn>;
    arquivarDocumento: ReturnType<typeof vi.fn>;
    reabrirDocumento: ReturnType<typeof vi.fn>;
    desvincularDocumento: ReturnType<typeof vi.fn>;
    buscarSolicitacao: ReturnType<typeof vi.fn>;
  };

  let notificationServiceMock: {
    success: ReturnType<typeof vi.fn>;
    error: ReturnType<typeof vi.fn>;
    warning: ReturnType<typeof vi.fn>;
    info: ReturnType<typeof vi.fn>;
  };

  let confirmationServiceMock: {
    confirm: ReturnType<typeof vi.fn>;
  };

  const mockSolicitacao: SolicitacaoFinanceira = {
    id: 'sol-001',
    numero: 1042,
    status: 'SOLICITADO',
    formulario: {
      id: 'form-001',
      tipoSolicitacao: 'COMPRA',
      codigo: 'COMPRA_PADRAO',
      titulo: 'Solicitação de Compra',
      padrao: true,
      ativo: true
    },
    valores: {
      descricao: {
        valor: 'Compra de material de escritório',
        exibicao: 'Compra de material de escritório'
      }
    },
    valor: 1580.75,
    usuarioSolicitanteId: 'user-001',
    usuarioSolicitante: 'joao.silva',
    nomeColaboradorSolicitante: 'João Silva',
    dataSubmissao: '2026-08-20T10:00:00Z',
    desfecho: {},
    documentos: [],
    acoes: {
      podeEditar: true,
      podeSubmeter: false,
      podeDecidir: true,
      podeCancelar: true,
      podeDesfazerDecisao: false
    },
    ativo: true,
    dataCriacao: '2026-08-20T09:00:00Z'
  };

  const mockDocBoleto: DocumentoResumo = {
    id: 'doc-001',
    nomeArquivo: 'boleto-fornecedor.pdf',
    contentType: 'application/pdf',
    tamanhoBytes: 184231,
    tipo: 'BOLETO',
    tipoRotulo: 'Boleto',
    situacao: 'PENDENTE',
    situacaoRotulo: 'Aguardando conferência',
    numeroDocumento: '123456',
    chaveAcesso: null,
    codigoPagamento: '34191.79001 01043.510047 91020.150008 9 98760000158075',
    possuiCodigoPagamento: true,
    duplicado: false,
    identificacaoPendente: false,
    valor: 1580.75,
    usuarioConferenciaId: null,
    dataConferencia: null,
    observacaoConferencia: null,
    motivoPendencia: null,
    acoes: {
      baixar: true,
      editarMetadados: true,
      remover: true,
      conferir: true,
      apontarPendencia: true,
      arquivar: false,
      reabrir: false,
      desvincular: true
    },
    dataCriacao: '2026-08-20T10:35:00-03:00',
    usuarioCriacao: 'joao.silva'
  };

  const mockDocDuplicado: DocumentoResumo = {
    id: 'doc-002',
    nomeArquivo: 'nota-fiscal-duplicada.pdf',
    contentType: 'application/pdf',
    tamanhoBytes: 524288,
    tipo: 'NOTA_FISCAL',
    tipoRotulo: 'Nota fiscal',
    situacao: 'PENDENTE',
    situacaoRotulo: 'Aguardando conferência',
    numeroDocumento: '98765',
    chaveAcesso: '35260812345678000190550010000987651000987654',
    codigoPagamento: null,
    possuiCodigoPagamento: false,
    duplicado: true,
    identificacaoPendente: false,
    valor: 2500,
    usuarioConferenciaId: null,
    dataConferencia: null,
    observacaoConferencia: null,
    motivoPendencia: null,
    acoes: {
      baixar: true,
      editarMetadados: true,
      remover: true,
      conferir: true,
      apontarPendencia: true,
      arquivar: false,
      reabrir: false,
      desvincular: false
    },
    dataCriacao: '2026-08-21T11:00:00-03:00',
    usuarioCriacao: 'joao.silva'
  };

  const mockDocIdentificacaoPendente: DocumentoResumo = {
    id: 'doc-003',
    nomeArquivo: 'recibo-sem-numero.png',
    contentType: 'image/png',
    tamanhoBytes: 204800,
    tipo: 'RECIBO',
    tipoRotulo: 'Recibo',
    situacao: 'PENDENTE',
    situacaoRotulo: 'Aguardando conferência',
    numeroDocumento: null,
    chaveAcesso: null,
    codigoPagamento: null,
    possuiCodigoPagamento: false,
    duplicado: false,
    identificacaoPendente: true,
    valor: 350,
    usuarioConferenciaId: null,
    dataConferencia: null,
    observacaoConferencia: null,
    motivoPendencia: null,
    acoes: {
      baixar: true,
      editarMetadados: true,
      remover: true,
      conferir: false,
      apontarPendencia: true,
      arquivar: false,
      reabrir: false,
      desvincular: false
    },
    dataCriacao: '2026-08-22T14:00:00-03:00',
    usuarioCriacao: 'joao.silva'
  };

  const mockDocComPendencia: DocumentoResumo = {
    id: 'doc-004',
    nomeArquivo: 'comprovante.xml',
    contentType: 'text/xml',
    tamanhoBytes: 15360,
    tipo: 'OUTRO',
    tipoRotulo: 'Outro',
    situacao: 'COM_PENDENCIA',
    situacaoRotulo: 'Com pendência',
    numeroDocumento: '778899',
    chaveAcesso: null,
    codigoPagamento: null,
    possuiCodigoPagamento: false,
    duplicado: false,
    identificacaoPendente: false,
    valor: 100,
    usuarioConferenciaId: 'user-002',
    dataConferencia: '2026-08-23T15:00:00-03:00',
    observacaoConferencia: null,
    motivoPendencia: 'A linha digitável não corresponde ao boleto anexado.',
    acoes: {
      baixar: true,
      editarMetadados: true,
      remover: false,
      conferir: true,
      apontarPendencia: true,
      arquivar: false,
      reabrir: false,
      desvincular: false
    },
    dataCriacao: '2026-08-22T16:00:00-03:00',
    usuarioCriacao: 'joao.silva'
  };

  const mockDocArquivado: DocumentoResumo = {
    id: 'doc-005',
    nomeArquivo: 'contrato.docx',
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    tamanhoBytes: 2500000,
    tipo: 'CONTRATO',
    tipoRotulo: 'Contrato',
    situacao: 'ARQUIVADO',
    situacaoRotulo: 'Arquivado',
    numeroDocumento: 'CTR-2026/01',
    chaveAcesso: null,
    codigoPagamento: null,
    possuiCodigoPagamento: false,
    duplicado: false,
    identificacaoPendente: false,
    valor: 15000,
    usuarioConferenciaId: 'user-002',
    dataConferencia: '2026-08-24T09:00:00-03:00',
    observacaoConferencia: 'Contrato final assinado',
    motivoPendencia: null,
    acoes: {
      baixar: true,
      editarMetadados: false,
      remover: false,
      conferir: false,
      apontarPendencia: false,
      arquivar: false,
      reabrir: true,
      desvincular: false
    },
    dataCriacao: '2026-08-23T08:00:00-03:00',
    usuarioCriacao: 'joao.silva'
  };

  beforeEach(async () => {
    financeiroServiceMock = {
      anexarDocumento: vi.fn(() => of(mockSolicitacao)),
      baixarDocumentoDaSolicitacao: vi.fn(() => of(new Blob(['conteudo']))),
      removerDocumentoDaSolicitacao: vi.fn(() => of(mockSolicitacao)),
      buscarDocumento: vi.fn(() =>
        of({
          id: 'doc-001',
          nomeArquivo: 'boleto-fornecedor.pdf',
          contentType: 'application/pdf',
          tamanhoBytes: 184231,
          tipo: 'BOLETO',
          tipoRotulo: 'Boleto',
          situacao: 'PENDENTE',
          situacaoRotulo: 'Aguardando conferência',
          numeroDocumento: '123456',
          chaveAcesso: null,
          codigoPagamento: '34191.79001 01043.510047 91020.150008 9 98760000158075',
          possuiCodigoPagamento: true,
          duplicado: false,
          identificacaoPendente: false,
          valor: 1580.75,
          vencido: false,
          diasNaSituacao: 1,
          usuarioEnvioId: 'user-001',
          ativo: true,
          dataCriacao: '2026-08-20T10:35:00-03:00'
        } as DocumentoFinanceiro)
      ),
      atualizarDocumento: vi.fn(() => of({} as DocumentoFinanceiro)),
      conferirDocumento: vi.fn(() => of({} as DocumentoFinanceiro)),
      apontarPendenciaDocumento: vi.fn(() => of({} as DocumentoFinanceiro)),
      arquivarDocumento: vi.fn(() => of({} as DocumentoFinanceiro)),
      reabrirDocumento: vi.fn(() => of({} as DocumentoFinanceiro)),
      desvincularDocumento: vi.fn(() => of({} as DocumentoFinanceiro)),
      buscarSolicitacao: vi.fn(() => of(mockSolicitacao))
    };

    notificationServiceMock = {
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
      info: vi.fn()
    };

    confirmationServiceMock = {
      confirm: vi.fn(() => Promise.resolve(true))
    };

    await TestBed.configureTestingModule({
      imports: [SolicitacaoDocumentosComponent],
      providers: [
        { provide: FinanceiroService, useValue: financeiroServiceMock },
        {
          provide: AuthService,
          useValue: {
            currentUser: vi.fn(() => ({ permissoes: ['financeiro:documento:write'] }))
          }
        },
        { provide: NotificationService, useValue: notificationServiceMock },
        { provide: CesConfirmationService, useValue: confirmationServiceMock }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SolicitacaoDocumentosComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('tipo', 'COMPRA');
    fixture.componentRef.setInput('solicitacao', mockSolicitacao);
    fixture.detectChanges();
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.clearAllMocks();
  });

  describe('Renderização e estado inicial', () => {
    it('configura o seletor para os formatos aceitos e múltiplos arquivos', () => {
      const input = fixture.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;

      expect(input.multiple).toBe(true);
      expect(input.accept).toBe(
        '.jpg,.jpeg,.png,.webp,.heic,.heif,.pdf,.xml,.doc,.docx,.xls,.xlsx'
      );
      expect((component as any).podeEnviar).toBe(true);
    });

    it('renderiza o estado vazio quando a solicitação não possui anexos', () => {
      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Nenhum documento anexado ainda.');
      expect(financeiroServiceMock.buscarDocumento).not.toHaveBeenCalled();
    });

    it('renderiza os anexos a partir de solicitacao.documentos sem fazer requisições individuais', () => {
      fixture.componentRef.setInput('solicitacao', {
        ...mockSolicitacao,
        documentos: [mockDocBoleto, mockDocComPendencia]
      });
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('boleto-fornecedor.pdf');
      expect(compiled.textContent).toContain('Boleto');
      expect(compiled.textContent).toContain('comprovante.xml');
      expect(financeiroServiceMock.buscarDocumento).not.toHaveBeenCalled();
    });

    it('exibe número do documento e valor quando informados', () => {
      fixture.componentRef.setInput('solicitacao', {
        ...mockSolicitacao,
        documentos: [mockDocBoleto]
      });
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('123456');
      expect(compiled.textContent).toContain('R$');
    });

    it('aplica recuo condicional conforme input comTitulo', () => {
      expect((component as any).recuo).toBe('md:ml-5 md:w-[calc(100%-1.25rem)]');

      fixture.componentRef.setInput('comTitulo', false);
      fixture.detectChanges();

      expect((component as any).recuo).toBe('');
    });
  });

  describe('Código de pagamento e indicadores de negócio', () => {
    it('exibe código de pagamento quando possuiCodigoPagamento for true', () => {
      fixture.componentRef.setInput('solicitacao', {
        ...mockSolicitacao,
        documentos: [mockDocBoleto]
      });
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Código de pagamento');
      expect(compiled.textContent).toContain('34191.79001 01043.510047 91020.150008 9 98760000158075');
    });

    it('copia código de pagamento integralmente e notifica sucesso', async () => {
      const writeTextMock = vi.fn().mockResolvedValue(undefined);
      Object.assign(navigator, {
        clipboard: { writeText: writeTextMock }
      });

      (component as any).copiarCodigo(mockDocBoleto);
      await Promise.resolve();
      await Promise.resolve();

      expect(writeTextMock).toHaveBeenCalledWith(mockDocBoleto.codigoPagamento);
      expect(notificationServiceMock.success).toHaveBeenCalledWith('Código copiado integralmente.');
    });

    it('notifica erro caso falhe ao copiar código de pagamento', async () => {
      const writeTextMock = vi.fn().mockRejectedValue(new Error('Erro de permissão'));
      Object.assign(navigator, {
        clipboard: { writeText: writeTextMock }
      });

      (component as any).copiarCodigo(mockDocBoleto);
      await Promise.resolve();
      await Promise.resolve();

      expect(notificationServiceMock.error).toHaveBeenCalledWith('Não foi possível copiar o código.');
    });

    it('exibe alerta de documento duplicado quando duplicado for true', () => {
      fixture.componentRef.setInput('solicitacao', {
        ...mockSolicitacao,
        documentos: [mockDocDuplicado]
      });
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain(
        'Este arquivo também está anexado em outro documento. A conferência continua disponível.'
      );
    });

    it('exibe alerta de identificação pendente quando identificacaoPendente for true', () => {
      fixture.componentRef.setInput('solicitacao', {
        ...mockSolicitacao,
        documentos: [mockDocIdentificacaoPendente]
      });
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain(
        'Falta informar a identificação obrigatória deste documento.'
      );
    });

    it('exibe motivo da pendência e observação de conferência quando presentes', () => {
      fixture.componentRef.setInput('solicitacao', {
        ...mockSolicitacao,
        documentos: [mockDocComPendencia, mockDocArquivado]
      });
      fixture.detectChanges();

      const compiled = fixture.nativeElement as HTMLElement;
      expect(compiled.textContent).toContain('Motivo da pendência:');
      expect(compiled.textContent).toContain(
        'A linha digitável não corresponde ao boleto anexado.'
      );
      expect(compiled.textContent).toContain('Observação:');
      expect(compiled.textContent).toContain('Contrato final assinado');
    });
  });

  describe('Matriz de ações documentais estritas (documento.acoes)', () => {
    it('usuário com apenas leitura recebe somente botão de download habilitado', () => {
      const docLeitura: DocumentoResumo = {
        ...mockDocBoleto,
        acoes: {
          baixar: true,
          editarMetadados: false,
          remover: false,
          conferir: false,
          apontarPendencia: false,
          arquivar: false,
          reabrir: false,
          desvincular: false
        }
      };

      const acoes = (component as any).acoes(docLeitura);
      expect(acoes.baixar).toBe(true);
      expect(acoes.editarMetadados).toBe(false);
      expect(acoes.remover).toBe(false);
      expect(acoes.conferir).toBe(false);
      expect(acoes.apontarPendencia).toBe(false);
      expect(acoes.arquivar).toBe(false);
      expect(acoes.reabrir).toBe(false);
    });

    it('trata documento sem objeto acoes como todas as ações fechadas por segurança', () => {
      const docSemAcoes: DocumentoResumo = {
        ...mockDocBoleto,
        acoes: undefined as any
      };

      const acoes = (component as any).acoes(docSemAcoes);
      expect(acoes.baixar).toBe(false);
      expect(acoes.editarMetadados).toBe(false);
      expect(acoes.remover).toBe(false);
      expect(acoes.conferir).toBe(false);
      expect(acoes.apontarPendencia).toBe(false);
    });

    it('documento arquivado permite reabertura mas não edição nem remoção', () => {
      const acoes = (component as any).acoes(mockDocArquivado);
      expect(acoes.reabrir).toBe(true);
      expect(acoes.editarMetadados).toBe(false);
      expect(acoes.remover).toBe(false);
      expect(acoes.conferir).toBe(false);
    });

    it('documento conferido permite apenas download e visualização', () => {
      const docConferido: DocumentoResumo = {
        ...mockDocBoleto,
        situacao: 'CONFERIDO',
        acoes: {
          baixar: true,
          editarMetadados: true,
          remover: true,
          conferir: false,
          apontarPendencia: true,
          arquivar: true,
          reabrir: true,
          desvincular: true
        }
      };

      const acoes = (component as any).acoes(docConferido);
      expect(acoes.baixar).toBe(true);
      expect(acoes.editarMetadados).toBe(false);
      expect(acoes.remover).toBe(false);
      expect(acoes.conferir).toBe(false);
      expect(acoes.apontarPendencia).toBe(false);
      expect(acoes.arquivar).toBe(false);
      expect(acoes.reabrir).toBe(false);
      expect(acoes.desvincular).toBe(false);
    });
  });

  describe('Execução de ações e mutações documentais', () => {
    it('executa download do documento via rota aninhada da solicitação', () => {
      const spyDownloadBlob = vi.spyOn(FileDownloadUtil, 'downloadBlob').mockImplementation(() => {});

      (component as any).baixar(mockDocBoleto);

      expect(financeiroServiceMock.baixarDocumentoDaSolicitacao).toHaveBeenCalledWith(
        'COMPRA',
        'sol-001',
        'doc-001'
      );
      expect(spyDownloadBlob).toHaveBeenCalled();
    });

    it('aponta pendência com motivo obrigatório e recarrega a solicitação', () => {
      (component as any).abrirPendencia(mockDocBoleto);
      expect((component as any).dialogVisivel()).toBe(true);
      expect((component as any).acaoDialog()).toBe('PENDENCIA');

      // Tentar sem motivo deve validar e abortar
      (component as any).textoPendencia.set('   ');
      (component as any).confirmarDialogo();

      expect((component as any).motivoPendenciaErro()).toBe('Informe o motivo da pendência.');
      expect(financeiroServiceMock.apontarPendenciaDocumento).not.toHaveBeenCalled();

      // Com motivo preenchido
      (component as any).textoPendencia.set('Linha digitável ilegível');
      (component as any).confirmarDialogo();

      expect(financeiroServiceMock.apontarPendenciaDocumento).toHaveBeenCalledWith(
        'doc-001',
        { motivo: 'Linha digitável ilegível' },
        expect.any(String)
      );
      expect(notificationServiceMock.success).toHaveBeenCalledWith('Pendência registrada.');
    });

    it('executa visualização do anexo em nova aba', () => {
      const spyCreateObjectURL = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:http://localhost/teste');
      const spyWindowOpen = vi.spyOn(window, 'open').mockImplementation(() => null);

      (component as any).visualizar(mockDocBoleto);

      expect(financeiroServiceMock.baixarDocumentoDaSolicitacao).toHaveBeenCalledWith(
        'COMPRA',
        'sol-001',
        'doc-001'
      );
      expect(spyCreateObjectURL).toHaveBeenCalled();
      expect(spyWindowOpen).toHaveBeenCalledWith('blob:http://localhost/teste', '_blank', 'noopener,noreferrer');
    });

    it('confirma desvinculação de documento antes de executar a mutação', async () => {
      (component as any).confirmarDesvinculacao(mockDocBoleto);
      await Promise.resolve();

      expect(confirmationServiceMock.confirm).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Desvincular documento',
          variant: 'destructive'
        })
      );
      expect(financeiroServiceMock.desvincularDocumento).toHaveBeenCalledWith('doc-001', expect.any(String));
    });

    it('não desvincula documento quando confirmação for cancelada', async () => {
      confirmationServiceMock.confirm.mockResolvedValueOnce(false);

      (component as any).confirmarDesvinculacao(mockDocBoleto);
      await Promise.resolve();

      expect(financeiroServiceMock.desvincularDocumento).not.toHaveBeenCalled();
    });

    it('arquiva e reabre documento conforme acoes autorizadas', () => {
      (component as any).arquivar(mockDocBoleto);
      expect(financeiroServiceMock.arquivarDocumento).not.toHaveBeenCalled(); // acoes.arquivar é false em mockDocBoleto

      const docParaArquivar: DocumentoResumo = {
        ...mockDocBoleto,
        acoes: { ...mockDocBoleto.acoes!, arquivar: true }
      };
      (component as any).arquivar(docParaArquivar);
      expect(financeiroServiceMock.arquivarDocumento).toHaveBeenCalledWith('doc-001', expect.any(String));

      (component as any).reabrir(mockDocArquivado);
      expect(financeiroServiceMock.reabrirDocumento).toHaveBeenCalledWith('doc-005', expect.any(String));
    });

    it('confirma e remove documento via rota aninhada da solicitação', async () => {
      const spyAtualizada = vi.spyOn(component.atualizada, 'emit');

      (component as any).confirmarRemocao(mockDocBoleto);
      await Promise.resolve();

      expect(confirmationServiceMock.confirm).toHaveBeenCalledWith(
        expect.objectContaining({
          title: 'Remover documento',
          variant: 'destructive'
        })
      );
      expect(financeiroServiceMock.removerDocumentoDaSolicitacao).toHaveBeenCalledWith(
        'COMPRA',
        'sol-001',
        'doc-001',
        expect.any(String)
      );
      expect(spyAtualizada).toHaveBeenCalled();
      expect(notificationServiceMock.success).toHaveBeenCalledWith('Documento removido.');
    });

    it('não remove documento quando confirmação for cancelada', async () => {
      confirmationServiceMock.confirm.mockResolvedValueOnce(false);

      (component as any).confirmarRemocao(mockDocBoleto);
      await Promise.resolve();

      expect(financeiroServiceMock.removerDocumentoDaSolicitacao).not.toHaveBeenCalled();
    });

    it('abre edição tardia buscando documento completo, navega pelas etapas e salva metadados', () => {
      (component as any).abrirEdicao(mockDocBoleto);

      expect(financeiroServiceMock.buscarDocumento).toHaveBeenCalledWith('doc-001');
      expect((component as any).editorVisivel()).toBe(true);
      expect((component as any).etapaEditor()).toBe(1);
      expect((component as any).editor.id).toBe('doc-001');
      expect((component as any).editor.numeroDocumento).toBe('123456');

      // Avança etapas
      (component as any).etapaEditor.set(2);
      expect((component as any).etapaEditor()).toBe(2);

      (component as any).etapaEditor.set(3);
      expect((component as any).etapaEditor()).toBe(3);

      (component as any).editor.numeroDocumento = '654321';
      (component as any).salvarEdicao();

      expect(financeiroServiceMock.atualizarDocumento).toHaveBeenCalledWith(
        'doc-001',
        expect.objectContaining({
          numeroDocumento: '654321',
          tipo: 'BOLETO'
        }),
        expect.any(String)
      );
      expect((component as any).editorVisivel()).toBe(false);
      expect(notificationServiceMock.success).toHaveBeenCalledWith('Metadados atualizados.');
      expect(financeiroServiceMock.buscarSolicitacao).toHaveBeenCalled();
    });

    it('trata erro de concorrência ou autorização ao apontar pendência invalidando e recarregando a solicitação', () => {
      financeiroServiceMock.apontarPendenciaDocumento.mockReturnValueOnce(
        throwError(() => ({ status: 409, message: 'Conflito concorrente' }))
      );

      (component as any).abrirPendencia(mockDocBoleto);
      (component as any).textoPendencia.set('Documento com valor divergente');
      (component as any).confirmarDialogo();

      expect(notificationServiceMock.error).toHaveBeenCalled();
      expect(financeiroServiceMock.buscarSolicitacao).toHaveBeenCalledWith('COMPRA', 'sol-001');
    });
  });

  describe('Upload e validações de arquivo', () => {
    it('valida tamanho máximo de arquivo de 15 MB', () => {
      const arquivoGrande = new File(['x'.repeat(100)], 'grande.pdf', { type: 'application/pdf' });
      Object.defineProperty(arquivoGrande, 'size', { value: 16 * 1024 * 1024 });

      const event = { target: { files: [arquivoGrande], value: 'grande.pdf' } } as unknown as Event;
      (component as any).selecionarArquivos(event);

      expect(notificationServiceMock.warning).toHaveBeenCalledWith(
        'grande.pdf ultrapassa 15 MB.',
        expect.objectContaining({ title: 'Revise os arquivos selecionados' })
      );
      expect((component as any).arquivosPendentes()).toEqual([]);
    });

    it('valida extensão permitida', () => {
      const arquivoInvalido = new File(['x'], 'executavel.exe', { type: 'application/x-msdownload' });
      const event = { target: { files: [arquivoInvalido], value: 'executavel.exe' } } as unknown as Event;

      (component as any).selecionarArquivos(event);

      expect(notificationServiceMock.warning).toHaveBeenCalledWith(
        'executavel.exe: formato não aceito.',
        expect.objectContaining({ title: 'Revise os arquivos selecionados' })
      );
      expect((component as any).arquivosPendentes()).toEqual([]);
    });

    it('valida o limite total de 20 MB sem aceitar parte do novo lote', () => {
      const existente = new File(['a'], 'existente.pdf', { type: 'application/pdf' });
      const novo = new File(['b'], 'novo.pdf', { type: 'application/pdf' });
      Object.defineProperty(existente, 'size', { value: 10 * 1024 * 1024 });
      Object.defineProperty(novo, 'size', { value: 11 * 1024 * 1024 });
      (component as any).arquivosPendentes.set([existente]);

      const event = { target: { files: [novo], value: 'novo.pdf' } } as unknown as Event;
      (component as any).selecionarArquivos(event);

      expect(notificationServiceMock.warning).toHaveBeenCalledWith(
        'O conjunto de arquivos ultrapassa 20 MB.',
        expect.objectContaining({ title: 'Revise os arquivos selecionados' })
      );
      expect((component as any).arquivosPendentes()).toEqual([existente]);
    });

    it('seleciona vários arquivos e executa um único envio com sucesso', () => {
      const spyAtualizada = vi.spyOn(component.atualizada, 'emit');
      const nota = new File(['nota'], 'nota.pdf', { type: 'application/pdf' });
      const xml = new File(['xml'], 'nota.xml', { type: 'application/xml' });
      Object.defineProperty(nota, 'size', { value: 1024 * 50 });
      Object.defineProperty(xml, 'size', { value: 1024 * 10 });

      const event = { target: { files: [nota, xml], value: 'nota.pdf' } } as unknown as Event;
      (component as any).selecionarArquivos(event);

      expect((component as any).arquivosPendentes()).toEqual([nota, xml]);

      (component as any).tipoDocumento = 'NOTA_FISCAL';
      (component as any).enviar();

      expect(financeiroServiceMock.anexarDocumento).toHaveBeenCalledWith(
        'COMPRA',
        'sol-001',
        [nota, xml],
        'NOTA_FISCAL',
        expect.any(String)
      );
      expect((component as any).arquivosPendentes()).toEqual([]);
      expect(spyAtualizada).toHaveBeenCalledWith(mockSolicitacao);
      expect(notificationServiceMock.success).toHaveBeenCalledWith(
        '2 anexos enviados com sucesso.'
      );
    });

    it('mantém a seleção completa quando o lote falha e traduz o status HTTP', () => {
      const arquivos = [
        new File(['nota'], 'nota.pdf', { type: 'application/pdf' }),
        new File(['boleto'], 'boleto.pdf', { type: 'application/pdf' })
      ];
      (component as any).arquivosPendentes.set(arquivos);
      financeiroServiceMock.anexarDocumento.mockReturnValueOnce(
        throwError(() => ({ status: 413 }))
      );

      (component as any).enviar();

      expect((component as any).arquivosPendentes()).toEqual(arquivos);
      expect(notificationServiceMock.error).toHaveBeenCalledWith(
        'Os arquivos ultrapassam o limite permitido.'
      );
    });

    it.each([
      [400, 'Um ou mais arquivos são inválidos. Revise o formato e o tamanho.'],
      [422, 'Um ou mais arquivos são inválidos. Revise o formato e o tamanho.'],
      [403, 'Você não possui permissão para anexar documentos.'],
      [500, 'Não foi possível enviar os anexos.']
    ])('trata erro de upload HTTP %s', (status, mensagem) => {
      (component as any).arquivosPendentes.set([
        new File(['nota'], 'nota.pdf', { type: 'application/pdf' })
      ]);
      financeiroServiceMock.anexarDocumento.mockReturnValueOnce(
        throwError(() => ({ status }))
      );

      (component as any).enviar();

      expect(notificationServiceMock.error).toHaveBeenCalledWith(mensagem);
    });
  });

  describe('Funções utilitárias e de apresentação', () => {
    it('determina o ícone correto por tipo de conteúdo', () => {
      expect((component as any).icone({ contentType: 'application/pdf' } as any)).toBe('pi pi-file-pdf');
      expect((component as any).icone({ contentType: 'image/jpeg' } as any)).toBe('pi pi-image');
      expect((component as any).icone({ contentType: 'text/xml' } as any)).toBe('pi pi-code');
      expect((component as any).icone({ contentType: 'application/vnd.ms-excel' } as any)).toBe(
        'pi pi-file-excel'
      );
      expect((component as any).icone({ contentType: 'application/octet-stream' } as any)).toBe('pi pi-file');
    });

    it('formata bytes em KB e MB', () => {
      expect((component as any).tamanho(512)).toBe('1 KB');
      expect((component as any).tamanho(500 * 1024)).toBe('500 KB');
      expect((component as any).tamanho(1.5 * 1024 * 1024)).toBe('1.5 MB');
    });

    it('determina tom de situação', () => {
      expect((component as any).tomSituacao('CONFERIDO')).toBe('success');
      expect((component as any).tomSituacao('COM_PENDENCIA')).toBe('danger');
      expect((component as any).tomSituacao('PENDENTE')).toBe('warning');
      expect((component as any).tomSituacao('ARQUIVADO')).toBe('neutral');
    });
  });
});
