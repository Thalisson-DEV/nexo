import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { SyncErrorCasesComponent } from './sync-error-cases.component';
import { SyncErrorCasesService } from '../../../core/services/sync-error-cases.service';
import { AuthService } from '../../../core/services/auth.service';
import { UsuarioLookupService } from '../../../core/services/usuario-lookup.service';
import { RhService } from '../../../core/services/rh.service';
import { SyncErrorCaseDetail, SyncErrorCaseSummary } from '../../../core/models/sync-error.models';
import { CesConfirmationService } from '@ces/ui';

describe('SyncErrorCasesComponent', () => {
  let component: SyncErrorCasesComponent;
  let fixture: ComponentFixture<SyncErrorCasesComponent>;
  let syncService: any;
  let authService: any;
  let messageService: any;
  let usuarioLookup: any;
  let rhService: any;

  const mockSummary: SyncErrorCaseSummary = {
    id: 'case-12345678-abcd',
    operationId: 'op-1',
    localUuid: 'uuid-1',
    operationType: 'TURNO',
    status: 'ABERTO',
    step: 'ANALYSIS',
    sourceUserName: 'Carlos Silva',
    errorTitle: 'Erro informado pelo aplicativo: Veículo não encontrado no cadastro',
    errorDetail: 'Erro informado pelo aplicativo: Veículo não encontrado no cadastro',
    attempts: 1,
    receivedAt: '2026-09-25T10:00:00Z',
    updatedAt: '2026-09-25T10:00:00Z',
    tipo: 'TURNO'
  };

  const mockDetail: SyncErrorCaseDetail = {
    ...mockSummary,
    originalPayload: {
      body: {
        idEquipe: 'EQ-01',
        placa: 'ABC-1234',
        kmInicial: 50000,
        observacao: 'Início de rota na base norte'
      }
    },
    attachments: [
      {
        id: 'att-1',
        fileName: 'painel_veiculo.jpg',
        contentType: 'image/jpeg',
        size: 1048576,
        sha256: 'abc123hash',
        receivedAt: '2026-09-25T10:00:00Z'
      }
    ],
    history: []
  };

  beforeEach(async () => {
    syncService = {
      list: vi.fn(() => of({ content: [mockSummary], page: { number: 0, size: 10, totalElements: 1, totalPages: 1 } })),
      get: () => of(mockDetail),
      assign: () => of({ ...mockDetail, assignedToName: 'analista-1' }),
      saveCorrection: () => of({ ...mockDetail, status: 'AGUARDANDO_REPROCESSAMENTO', correction: { version: 1, payload: mockDetail.originalPayload, justification: 'Placa corrigida', createdByName: 'analista-1', createdAt: '2026-09-25T10:30:00Z' } }),
      reprocess: () => of({ ...mockDetail, status: 'RESOLVIDO' }),
      discard: () => of({ ...mockDetail, status: 'REJEITADO' }),
      downloadAttachment: () => of(new Blob(['test'], { type: 'image/jpeg' }))
    };

    authService = {
      reautenticacaoNecessaria: () => false,
      isAuthenticated: () => true,
      token: () => null,
      currentUser: () => ({
        id: 'analista-1',
        nome: 'Analista Backoffice',
        permissoes: ['sincronizacao:read', 'sincronizacao:manage']
      })
    };

    messageService = {
      add: () => {}
    };

    usuarioLookup = {
      resolverNome: (val: any) => (val === 'user-123' ? 'Maria Silva' : val)
    };

    rhService = {
      listarEquipes: () => of({ content: [{ id: 'eq-uuid', codigo: 'LV-01' }] }),
      listarVeiculosAtivos: () => of({ content: [{ id: 'veic-uuid', placa: 'XYZ-9876', modelo: 'Strada' }] })
    };

    await TestBed.configureTestingModule({
      imports: [SyncErrorCasesComponent],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideNoopAnimations(),
        { provide: SyncErrorCasesService, useValue: syncService },
        { provide: AuthService, useValue: authService },
        { provide: MessageService, useValue: messageService },
        { provide: UsuarioLookupService, useValue: usuarioLookup },
        { provide: RhService, useValue: rhService },
        { provide: CesConfirmationService, useValue: { confirm: () => Promise.resolve(true) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SyncErrorCasesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('deve criar o componente com configurações iniciais humanizadas', () => {
    expect(component).toBeTruthy();
    expect(component['breadcrumbItems'][1].label).toBe('Sincronismo');
    expect(component['activeStep']()).toBe(1);
  });

  it('abre já nos dados, mais antigos primeiro, com situações no vocabulário de sincronização', async () => {
    const listar = syncService.list;
    await fixture.whenStable();
    fixture.detectChanges();
    expect(listar).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 25, sort: 'receivedAt,asc' }));
    const texto = (fixture.nativeElement as HTMLElement).textContent!;
    expect(texto).not.toMatch(/Pendentes?/);
    expect(component['statusLabel']('ABERTO')).toBe('Não foi possível sincronizar');
    expect(component['statusLabel']('AGUARDANDO_REPROCESSAMENTO')).toBe('Aguardando sincronização');
    expect(component['statusTone']('RESOLVIDO')).toBe('success');
  });

  it('deve traduzir tipos de operação técnicos para linguagem de negócio acessível', () => {
    expect(component['getOperationLabel']('TURNO')).toBe('Turno de Trabalho');
    expect(component['getOperationLabel']('EXECUCAO')).toBe('Execução de Serviço');
    expect(component['getOperationLabel']('SOLICITACAO')).toBe('Solicitação de Material');
    expect(component['getOperationLabel']('TRANSFERENCIA')).toBe('Transferência de Estoque');
    expect(component['getOperationLabel']('INSPECAO')).toBe('Inspeção de Segurança');
    expect(component['getOperationLabel']('ANEXO')).toBe('Evidência Fotográfica');
  });

  it('deve traduzir erros técnicos para explicações e orientações amigáveis', () => {
    const semFoto = component['getFriendlyErrorInfo']('Esta inspeção não tem foto de evidência');
    expect(semFoto.title).toBe('Inspeção sem foto obrigatória');
    expect(semFoto.suggestion).toContain('Verifique se há fotos');

    const naoEncontrado = component['getFriendlyErrorInfo']('Equipe não encontrada no banco');
    expect(naoEncontrado.title).toBe('Cadastro relacionado não localizado');

    const saldo = component['getFriendlyErrorInfo']('Saldo de estoque insuficiente para o item');
    expect(saldo.title).toBe('Saldo de estoque insuficiente');
  });

  it('deve abrir a pendência sempre iniciando no Passo 1 (Diagnóstico)', () => {
    component['open'](mockSummary);
    expect(component['displayDetail']()).toBe(true);
    expect(component['activeStep']()).toBe(1);
    expect(component['detail']()?.id).toBe(mockDetail.id);
  });

  it('deve mapear os campos do snapshot original em campos editáveis legíveis', () => {
    component['open'](mockSummary);
    const fields = component['editableFields']();
    expect(fields.length).toBeGreaterThan(0);

    const placaField = fields.find(f => f.key === 'placa');
    expect(placaField).toBeDefined();
    expect(placaField?.label).toBe('Placa do Veículo');
    expect(placaField?.value).toBe('ABC-1234');
  });

  it('deve exigir justificativa antes de avançar para revisão', () => {
    component['open'](mockSummary);
    component['activeStep'].set(2);
    component['justification'] = '   ';

    component['onSaveCorrection'](mockDetail);
    expect(component['showJustificationError']()).toBe(true);
    expect(component['activeStep']()).toBe(2);
  });

  it('deve salvar a correção e avançar automaticamente para o Passo 3 (Revisão)', () => {
    component['open'](mockSummary);
    component['activeStep'].set(2);
    component['justification'] = 'Placa corrigida para veículo reserva';

    component['onSaveCorrection'](mockDetail);
    expect(component['showJustificationError']()).toBe(false);
    expect(component['activeStep']()).toBe(3);
  });

  it('deve formatar o tamanho de fotos e anexos em KB e MB legíveis', () => {
    expect(component['formatFileSize'](1024)).toBe('1 KB');
    expect(component['formatFileSize'](1048576)).toBe('1 MB');
    expect(component['formatFileSize'](0)).toBe('0 B');
  });

  it('deve resolver nomes de usuários humanizados e evitar exibição de UUIDs brutos', () => {
    // Usuário atual autenticado
    expect(component['getUserDisplayName']('analista-1', true)).toBe('Analista Backoffice');

    // Usuário resolvido no lookup
    expect(component['getUserDisplayName']('user-123', false)).toBe('Maria Silva');

    // UUID desconhecido como atendente/responsável
    expect(component['getUserDisplayName']('8fa62a56-d354-442b-bbab-55945528faf9', true)).toBe('Atendente de Suporte');

    // UUID desconhecido como emissor/origem
    expect(component['getUserDisplayName']('3cda4132-fa99-4663-a5a3-3923867bd8f6', false)).toBe('Técnico de Campo');

    // Nome textual normal
    expect(component['getUserDisplayName']('João da Silva', false)).toBe('João da Silva');
  });

  it('deve extrair a identificação de negócio legível do registro', () => {
    const execDetail: any = {
      originalPayload: { body: { referenciaNota: '981240' } }
    };
    expect(component['getRecordBusinessLabel'](execDetail)).toBe('OS / Nota: 981240');

    const turnoDetail: any = {
      originalPayload: { body: { placa: 'ABC-1234' } }
    };
    expect(component['getRecordBusinessLabel'](turnoDetail)).toBe('Veículo: ABC-1234');

    const anexoDetail: any = {
      originalPayload: { body: { nomeArquivo: 'simulacao.png' } }
    };
    expect(component['getRecordBusinessLabel'](anexoDetail)).toBe('Arquivo: simulacao.png');
  });

  it('deve formatar referências relacionais sem expor hashes de banco', () => {
    expect(component['formatSummaryValue']('idInspecao', 'd1e1b0a1-577b-4499-a400-2a48e7dd7678')).toBe('Inspeção #D1E1B0A1');
    expect(component['formatSummaryValue']('idDesvio', '6074ffd2-e6f0-4868-af86-7db9c42e6817')).toBe('Desvio #6074FFD2');
    expect(component['formatSummaryValue']('simulacao', true)).toBe('Simulação de Teste');
  });
  it('corrige a quantidade sem perder o envelope ou outros campos do material', () => {
    const detail: SyncErrorCaseDetail = {
      ...mockDetail,
      operationType: 'SOLICITACAO',
      originalPayload: { body: { idEquipe: 'EQ-01', itens: [{ idMaterial: 'mat-1', quantidade: 1e49, numSerie: 'SER-1' }] }, origem: 'mobile' }
    };
    syncService.get = () => of(detail);
    const save = vi.fn(() => of({ ...detail, status: 'AGUARDANDO_REPROCESSAMENTO' }));
    syncService.saveCorrection = save;
    component['open'](mockSummary);
    component['editableMaterials']()[0].fields.find(field => field.key === 'quantidade')!.value = '2.5';
    component['justification'] = 'Quantidade conferida com a equipe';
    component['onSaveCorrection'](detail);
    expect(save).toHaveBeenCalledWith(detail.id, {
      justification: 'Quantidade conferida com a equipe',
      payload: { body: { idEquipe: 'EQ-01', itens: [{ idMaterial: 'mat-1', quantidade: 2.5, numSerie: 'SER-1' }] }, origem: 'mobile' }
    });
  });

  it('salva os IDs escolhidos nos seletores de equipe e material', () => {
    const detail: SyncErrorCaseDetail = {
      ...mockDetail,
      operationType: 'SOLICITACAO',
      originalPayload: { body: { idEquipe: 'team-old', itens: [{ idMaterial: 'mat-old', quantidade: 2 }] } }
    };
    syncService.get = () => of(detail);
    const save = vi.fn(() => of({ ...detail, status: 'AGUARDANDO_REPROCESSAMENTO' }));
    syncService.saveCorrection = save;
    component['open'](mockSummary);
    component['setFieldValue'](component['editableFields']().find(field => field.key === 'idEquipe')!, 'team-new');
    component['setFieldValue'](component['editableMaterials']()[0].fields.find(field => field.key === 'idMaterial')!, 'mat-new');
    component['justification'] = 'Vínculos conferidos nos cadastros';
    component['onSaveCorrection'](detail);
    expect(save).toHaveBeenCalledWith(detail.id, {
      justification: 'Vínculos conferidos nos cadastros',
      payload: { body: { idEquipe: 'team-new', itens: [{ idMaterial: 'mat-new', quantidade: 2 }] } }
    });
  });

  it('reenvia sem alterar com a justificativa do diagnóstico, depois de confirmar', async () => {
    const reprocess = vi.fn(() => of({ ...mockDetail, status: 'RESOLVIDO' as const }));
    syncService.reprocess = reprocess;
    const detail: SyncErrorCaseDetail = {
      ...mockDetail,
      diagnostico: { codigo: 'SEM_TURNO_ABERTO', titulo: 'Nota lançada antes de abrir o turno', explicacao: 'x', orientacao: 'Reenvie sem alterar.', acao: 'REENVIAR' }
    };
    await component['reenviarSemAlterar'](detail);
    expect(reprocess).toHaveBeenCalledWith(detail.id, 'Reenviado sem alteração. Nota lançada antes de abrir o turno: Reenvie sem alterar.');
    expect(component['podeReenviarSemAlterar'](detail)).toBe(true);
    expect(component['podeReenviarSemAlterar']({ ...detail, diagnostico: { ...detail.diagnostico!, acao: 'CORRIGIR' } })).toBe(false);
  });

  it('mostra cadastros pelo nome e nunca o identificador interno', () => {
    const detail: SyncErrorCaseDetail = {
      ...mockDetail,
      operationType: 'EXECUCAO',
      originalPayload: { idTurno: '6c6b1d7e-1f0a-4a9e-9a52-2d1f7a3c0b11', idTipoServico: '7e500000-0000-4000-8000-00000000000d', latitude: -9.4, longitude: -40.5 },
      referencias: { '7e500000-0000-4000-8000-00000000000d': 'CM · ENLACE' }
    };
    syncService.get = () => of(detail);
    component['open'](mockSummary);
    const entradas = component['getFriendlySummaryEntries'](detail.originalPayload);
    expect(entradas.find(e => e.label === 'Tipo de serviço')?.value).toBe('CM · ENLACE');
    expect(entradas.find(e => e.label === 'Turno de Trabalho')?.value).toBe('Turno não localizado');
    expect(entradas.find(e => e.label === 'Localização')?.href).toContain('google.com/maps');
    expect(JSON.stringify(entradas)).not.toMatch(/[0-9a-f]{8}-[0-9a-f]{4}/i);
  });

  it('mantém a série como texto e barra série repetida na correção', () => {
    const detail: SyncErrorCaseDetail = {
      ...mockDetail,
      operationType: 'EXECUCAO',
      originalPayload: { referenciaNota: '5014788318', materiais: [
        { idMaterial: 'med', quantidade: 1, operacao: 'APLICADO', numSerie: '0101' },
        { idMaterial: 'med', quantidade: 1, operacao: 'RETIRADO', numSerie: '0101' }
      ] }
    };
    syncService.get = () => of(detail);
    const save = vi.fn(() => of({ ...detail, status: 'AGUARDANDO_REPROCESSAMENTO' as const }));
    syncService.saveCorrection = save;
    component['open'](mockSummary);
    component['justification'] = 'Série do medidor retirado conferida com a equipe';
    expect(component['seriesRepetidasNaCorrecao']().has('0101')).toBe(true);
    component['onSaveCorrection'](detail);
    expect(save).not.toHaveBeenCalled();

    component['editableMaterials']()[1].fields.find(f => f.key === 'numSerie')!.value = '0202';
    component['revalidarSeries']();
    component['onSaveCorrection'](detail);
    expect(save).toHaveBeenCalledWith(detail.id, expect.objectContaining({
      payload: expect.objectContaining({ referenciaNota: '5014788318', materiais: [
        { idMaterial: 'med', quantidade: 1, operacao: 'APLICADO', numSerie: '0101' },
        { idMaterial: 'med', quantidade: 1, operacao: 'RETIRADO', numSerie: '0202' }
      ] })
    }));
  });
});
