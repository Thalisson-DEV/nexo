import { TestBed } from '@angular/core/testing';
import { CesConfirmationService } from '@ces/ui';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { abrirListagem, chipsDoPainel, paginaTeste } from '../../../shared/listagem/listagem-teste';
import { DocumentosFinanceirosComponent } from './documentos-financeiros.component';

const documento = {
  id: 'doc1',
  nomeArquivo: 'nota-123.pdf',
  contentType: 'application/pdf',
  tipo: 'NOTA_FISCAL',
  tipoRotulo: 'Nota fiscal',
  numeroDocumento: '123',
  emissorNome: 'ELÉTRICA BOA',
  competencia: '2026-08-01',
  dataVencimento: '2026-09-10',
  valor: 1200,
  situacao: 'PENDENTE',
  situacaoRotulo: 'Aguardando conferência',
  diasNaSituacao: 3,
  vencido: true,
  duplicado: false,
  identificacaoPendente: false,
  origemSolicitacaoId: null
};

describe('DocumentosFinanceirosComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(DocumentosFinanceirosComponent, 'lista', url, [
      { provide: FinanceiroService, useValue: servico },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['financeiro:documento:write', 'financeiro:documento:conferir'];
    servico = {
      listarDocumentos: vi.fn(() => paginaTeste([documento])),
      enviarDocumento: vi.fn(() => of(documento)),
      atualizarDocumento: vi.fn(() => of(documento)),
      apontarPendenciaDocumento: vi.fn(() => of(documento)),
      removerDocumento: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre já nos dados, mais recentes primeiro, com marcas e mês contábil', async () => {
    const { el } = await abrir();
    expect(servico['listarDocumentos']).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'dataCriacao,desc' });
    expect(el.textContent).toContain('nota-123.pdf');
    expect(el.textContent).toContain('ago/2026');
    expect(el.textContent).toContain('Vencido');
  });

  it('links do painel (?situacao=, ?vencidos=true) aplicam o recorte e o atalho fica marcado', async () => {
    const { el } = await abrir('/lista?vencidos=true&situacao=PENDENTE');
    expect(servico['listarDocumentos']).toHaveBeenLastCalledWith(expect.objectContaining({ apenasVencidos: true, situacao: ['PENDENTE'] }));
    const vencidos = Array.from(el.querySelectorAll('.ces-quick-filter')).find((b) => b.textContent!.includes('Vencidos'))!;
    expect(vencidos.getAttribute('aria-pressed')).toBe('true');
    expect(chipsDoPainel(el)).toContain('Atalho: vencidos');
  });

  it('um atalho por vez; "aguardando" vira situação PENDENTE', async () => {
    const { el } = await abrir('/lista?vencidos=true');
    const aguardando = Array.from(el.querySelectorAll('.ces-quick-filter')).find((b) => b.textContent!.includes('Aguardando')) as HTMLButtonElement;
    aguardando.click();
    await new Promise((r) => setTimeout(r));
    const params = servico['listarDocumentos'].mock.lastCall![0];
    expect(params).toMatchObject({ situacao: ['PENDENTE'] });
    expect(params).not.toHaveProperty('apenasVencidos');
  });

  it('XML vira nota fiscal; envio guarda datas como dia civil', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      abrirEnvio(): void;
      escolherArquivo(f: File): void;
      form: { controls: { tipo: { value: string }; dataVencimento: { setValue(d: Date): void } } };
      enviar(): void;
    };
    tela.abrirEnvio();
    tela.escolherArquivo(new File(['<nfe/>'], 'nota.xml'));
    expect(tela.form.controls.tipo.value).toBe('NOTA_FISCAL');
    tela.form.controls.dataVencimento.setValue(new Date(2026, 9, 15));
    tela.enviar();
    expect(servico['enviarDocumento']).toHaveBeenCalledWith(expect.any(File), expect.objectContaining({ tipo: 'NOTA_FISCAL', dataVencimento: '2026-10-15' }));
  });

  it('pendência exige motivo e remover pede confirmação', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as {
      abrirPendencia(d: unknown): void;
      motivo: { set(v: string): void };
      apontarPendencia(): void;
      remover(d: unknown): Promise<void>;
    };
    tela.abrirPendencia(documento);
    tela.apontarPendencia();
    expect(servico['apontarPendenciaDocumento']).not.toHaveBeenCalled();
    tela.motivo.set('Sem número');
    tela.apontarPendencia();
    expect(servico['apontarPendenciaDocumento']).toHaveBeenCalledWith('doc1', 'Sem número');
    vi.spyOn(TestBed.inject(CesConfirmationService), 'confirm').mockResolvedValue(true);
    await tela.remover(documento);
    expect(servico['removerDocumento']).toHaveBeenCalledWith('doc1');
  });
});
