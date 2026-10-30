import '@angular/compiler';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserTestingModule, platformBrowserTesting } from '@angular/platform-browser/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MessageService } from 'primeng/api';
import { AlmoxarifadoService } from '../../../core/services/almoxarifado.service';
import { ExportarSolicitacoesDialogComponent } from './exportar-solicitacoes-dialog.component';

try {
  TestBed.initTestEnvironment(BrowserTestingModule, platformBrowserTesting());
} catch {}

describe('ExportarSolicitacoesDialogComponent', () => {
  let component: ExportarSolicitacoesDialogComponent;
  let fixture: ComponentFixture<ExportarSolicitacoesDialogComponent>;
  let mockAlmoxarifadoService: {
    exportarSolicitacoesResumo: ReturnType<typeof vi.fn>;
    exportarSolicitacoesDetalhado: ReturnType<typeof vi.fn>;
  };
  let mockMessageService: {
    add: ReturnType<typeof vi.fn>;
  };

  beforeEach(async () => {
    mockAlmoxarifadoService = {
      exportarSolicitacoesResumo: vi.fn().mockReturnValue(of(new Blob(['csv-content'], { type: 'text/csv' }))),
      exportarSolicitacoesDetalhado: vi.fn().mockReturnValue(of(new Blob(['csv-content-detailed'], { type: 'text/csv' }))),
    };

    mockMessageService = {
      add: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [ExportarSolicitacoesDialogComponent],
      providers: [
        { provide: AlmoxarifadoService, useValue: mockAlmoxarifadoService },
        { provide: MessageService, useValue: mockMessageService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ExportarSolicitacoesDialogComponent);
    component = fixture.componentInstance;
  });

  it('inicializa com valores padrões e sincroniza contexto inicial', () => {
    component.contextoInicial = 'TECNICO';
    component.visible = true;
    component.sincronizarFiltros();
    fixture.detectChanges();

    expect(component.processoSelecionado).toBe('TECNICO');
    expect(component.formatoSelecionado).toBe('resumo');
  });

  it('sincroniza filtros ativos fornecidos pelo componente pai', () => {
    const filtrosAtivos = {
      dataInicio: new Date('2026-09-01T00:00:00.000Z'),
      dataFim: new Date('2026-09-18T23:59:59.000Z'),
      status: ['PENDENTE', 'APROVADO'],
      idObra: 'obra-123',
      idEquipe: 'equipe-456',
    };

    component.filtrosAtivos = filtrosAtivos;
    component.visible = true;
    component.sincronizarFiltros();
    fixture.detectChanges();

    expect(component.statusSelecionados).toEqual(['PENDENTE', 'APROVADO']);
    expect(component.filtroObra).toBe('obra-123');
    expect(component.filtroEquipe).toBe('equipe-456');
    expect(component.dataDe).toEqual(filtrosAtivos.dataInicio);
    expect(component.dataAte).toEqual(filtrosAtivos.dataFim);
  });

  it('executa exportação de resumo e dispara download', () => {
    const originalCreateObjectURL = window.URL.createObjectURL;
    const originalRevokeObjectURL = window.URL.revokeObjectURL;
    window.URL.createObjectURL = vi.fn().mockReturnValue('blob:http://localhost/fake-uuid');
    window.URL.revokeObjectURL = vi.fn();

    let visibleEmitted: boolean | undefined;
    component.visibleChange.subscribe((val) => (visibleEmitted = val));

    component.contextoInicial = 'COMERCIAL';
    component.visible = true;
    component.sincronizarFiltros();
    fixture.detectChanges();

    component.formatoSelecionado = 'resumo';
    component.processoSelecionado = 'COMERCIAL';
    component.filtroEstoqueOrigem = 'est-origem-1';

    component.executarExportacao();

    expect(mockAlmoxarifadoService.exportarSolicitacoesResumo).toHaveBeenCalledWith(
      expect.objectContaining({
        contexto: 'COMERCIAL',
        idEstoqueOrigem: 'est-origem-1',
      })
    );
    expect(mockMessageService.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
        summary: 'Exportação concluída',
      })
    );
    expect(component.visible).toBe(false);
    expect(visibleEmitted).toBe(false);

    window.URL.createObjectURL = originalCreateObjectURL;
    window.URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('executa exportação detalhada quando selecionado', () => {
    const originalCreateObjectURL = window.URL.createObjectURL;
    const originalRevokeObjectURL = window.URL.revokeObjectURL;
    window.URL.createObjectURL = vi.fn().mockReturnValue('blob:http://localhost/fake-uuid');
    window.URL.revokeObjectURL = vi.fn();

    component.contextoInicial = 'TODOS';
    component.visible = true;
    component.sincronizarFiltros();
    fixture.detectChanges();

    component.formatoSelecionado = 'detalhado';
    component.processoSelecionado = 'TODOS';

    component.executarExportacao();

    expect(mockAlmoxarifadoService.exportarSolicitacoesDetalhado).toHaveBeenCalled();
    expect(mockMessageService.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
      })
    );

    window.URL.createObjectURL = originalCreateObjectURL;
    window.URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('executa exportação no modo ZIP quando selecionado pacote separado', () => {
    const originalCreateObjectURL = window.URL.createObjectURL;
    const originalRevokeObjectURL = window.URL.revokeObjectURL;
    window.URL.createObjectURL = vi.fn().mockReturnValue('blob:http://localhost/fake-zip-uuid');
    window.URL.revokeObjectURL = vi.fn();

    component.contextoInicial = 'TODOS';
    component.visible = true;
    component.sincronizarFiltros();
    fixture.detectChanges();

    component.processoSelecionado = 'TODOS';
    component.tipoArquivo = 'ZIP';
    component.formatoSelecionado = 'resumo';

    component.executarExportacao();

    expect(mockAlmoxarifadoService.exportarSolicitacoesResumo).toHaveBeenCalledWith(
      expect.objectContaining({
        pacoteZip: true,
      })
    );
    expect(mockMessageService.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
      })
    );

    window.URL.createObjectURL = originalCreateObjectURL;
    window.URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('executa exportação no modo consolidado CSV com nome de arquivo adequado', () => {
    const originalCreateObjectURL = window.URL.createObjectURL;
    const originalRevokeObjectURL = window.URL.revokeObjectURL;
    window.URL.createObjectURL = vi.fn().mockReturnValue('blob:http://localhost/fake-csv-uuid');
    window.URL.revokeObjectURL = vi.fn();

    component.contextoInicial = 'TODOS';
    component.visible = true;
    component.sincronizarFiltros();
    fixture.detectChanges();

    component.processoSelecionado = 'TODOS';
    component.tipoArquivo = 'CONSOLIDADO';
    component.formatoSelecionado = 'resumo';

    component.executarExportacao();

    expect(mockAlmoxarifadoService.exportarSolicitacoesResumo).toHaveBeenCalledWith(
      expect.not.objectContaining({
        pacoteZip: true,
      })
    );
    expect(mockMessageService.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'success',
      })
    );

    window.URL.createObjectURL = originalCreateObjectURL;
    window.URL.revokeObjectURL = originalRevokeObjectURL;
  });

  it('trata erro no download exibindo toast de erro', () => {
    mockAlmoxarifadoService.exportarSolicitacoesResumo.mockReturnValue(
      throwError(() => new Error('Falha na conexão com o servidor'))
    );

    component.visible = true;
    component.sincronizarFiltros();
    fixture.detectChanges();

    component.executarExportacao();

    expect(component.exportando()).toBe(false);
    expect(mockMessageService.add).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: 'Erro ao exportar',
      })
    );
  });
});
