import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { CesConfirmationService } from '@ces/ui';
import { ConfirmationService, MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ObrasKitService } from '../../../core/services/obras-kit.service';
import { FonteImportacao, ImportacaoPlanilhaObrasComponent, LinhaPlanilha } from './importacao-planilha-obras.component';
import { COLUNAS_KITS } from './importacoes-obras';

const linhas: LinhaPlanilha[] = [
  { linha: 2, codigoKit: 'K1', codigoItem: 'ACAO-1', status: 'AGUARDANDO_PROCESSAMENTO' },
  { linha: 3, codigoKit: 'K1', codigoItem: 'XYZ', status: 'COM_ERRO', mensagemErro: 'Material XYZ não encontrado ou inativo.' }
];

type Tela = {
  idContrato: { set(v: string | null): void };
  enviar(arquivo: File): void;
  revalidar(linha: LinhaPlanilha): void;
  importacao: { linhas(): LinhaPlanilha[]; processar(): Promise<void> };
  apenasErros: { set(v: boolean): void };
  exibidas(): LinhaPlanilha[];
};

describe('ImportacaoPlanilhaObrasComponent', () => {
  let fonte: { [K in keyof FonteImportacao]: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    TestBed.resetTestingModule();
    fonte = {
      validar: vi.fn(() => of({ registros: linhas.map((l) => ({ ...l })), totalProcessados: 2, totalSucesso: 1, totalFalha: 1 })),
      revalidar: vi.fn((registros: LinhaPlanilha[]) =>
        of({ registros: registros.map((r) => ({ ...r, status: 'AGUARDANDO_PROCESSAMENTO', mensagemErro: null })), totalProcessados: 2, totalSucesso: 2, totalFalha: 0 })),
      confirmar: vi.fn((registros: LinhaPlanilha[]) =>
        of({ registros: registros.map((r) => ({ ...r, status: 'EM_PROCESSAMENTO' })), totalProcessados: 2, totalSucesso: 2, totalFalha: 0, idImportacao: 'i1' }))
    };
    TestBed.configureTestingModule({
      providers: [provideNoopAnimations(), MessageService, ConfirmationService, { provide: ObrasKitService, useValue: { baixarModelo: vi.fn(() => of(undefined)) } }]
    });
  });

  function criar(exigeContrato = true) {
    const fixture = TestBed.createComponent(ImportacaoPlanilhaObrasComponent);
    fixture.componentRef.setInput('tipo', 'KITS');
    fixture.componentRef.setInput('colunas', COLUNAS_KITS);
    fixture.componentRef.setInput('fonte', fonte);
    fixture.componentRef.setInput('exigeContrato', exigeContrato);
    fixture.componentInstance.visible.set(true);
    fixture.detectChanges();
    return { fixture, tela: fixture.componentInstance as unknown as Tela };
  }

  it('exige o contrato antes de validar a planilha', () => {
    const { tela } = criar();
    tela.enviar(new File(['x'], 'kits.csv'));
    expect(fonte.validar).not.toHaveBeenCalled();

    tela.idContrato.set('c1');
    tela.enviar(new File(['x'], 'kits.csv'));
    expect(fonte.validar).toHaveBeenCalledWith(expect.any(File), 'c1');
    expect(tela.importacao.linhas()).toHaveLength(2);
  });

  it('filtra as linhas com erro e revalida no servidor depois da correção', () => {
    const { tela } = criar();
    tela.idContrato.set('c1');
    tela.enviar(new File(['x'], 'kits.csv'));
    tela.apenasErros.set(true);
    expect(tela.exibidas()).toHaveLength(1);

    const comErro = tela.importacao.linhas()[1];
    comErro.editing = true;
    comErro['codigoItem'] = 'MAT-1';
    tela.revalidar(comErro);

    expect(fonte.revalidar).toHaveBeenCalledWith([expect.not.objectContaining({ editing: expect.anything() }), expect.objectContaining({ codigoItem: 'MAT-1' })], 'c1');
    expect(tela.importacao.linhas().every((l) => l.status === 'AGUARDANDO_PROCESSAMENTO')).toBe(true);
  });

  it('confirma com o contrato e o nome do arquivo', async () => {
    const { fixture, tela } = criar();
    const processada = vi.fn();
    fixture.componentInstance.processada.subscribe(processada);
    tela.idContrato.set('c1');
    tela.enviar(new File(['x'], 'kits.csv'));
    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);

    await tela.importacao.processar();

    expect(fonte.confirmar).toHaveBeenCalledWith(expect.any(Array), 'c1', 'kits.csv');
    expect(processada).toHaveBeenCalledOnce();
  });
});
