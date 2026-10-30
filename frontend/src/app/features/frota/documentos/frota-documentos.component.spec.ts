import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { DocumentoFrota } from '../../../core/models/frota.models';
import { OPCOES_VAZIAS, abrirTelaFrota, chipsDoResumo, paginaDe } from '../shared/frota-teste';
import { FrotaDocumentosComponent } from './frota-documentos.component';

const documento = {
  id: 'd1',
  idVeiculo: 'v1',
  placa: 'ABC1234',
  idImplemento: null,
  implemento: null,
  tipo: 'CRLV',
  tipoRotulo: 'CRLV',
  descricao: null,
  numero: '123',
  dataEmissao: null,
  dataVencimento: '2026-10-01',
  situacaoVencimento: 'VENCIDO',
  diasParaVencer: -8,
  observacao: null,
  possuiArquivo: false,
  arquivoNome: null,
  arquivoContentType: null,
  arquivoTamanhoBytes: null
} as unknown as DocumentoFrota;

function daquiA(dias: number): string {
  const data = new Date();
  data.setDate(data.getDate() + dias);
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

describe('FrotaDocumentosComponent', () => {
  let frota: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/frota/documentos') => abrirTelaFrota(FrotaDocumentosComponent, 'frota/documentos', url, frota, permissoes);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['frota:documento:read'];
    frota = {
      opcoes: vi.fn(() => of(OPCOES_VAZIAS)),
      parametros: vi.fn(() => of({ faixasAlertaDias: [15, 7, 0], limiteKmDia: 800 })),
      listarVeiculos: vi.fn(() => paginaDe([])),
      listarImplementos: vi.fn(() => paginaDe([])),
      listarDocumentos: vi.fn(() => paginaDe([documento]))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre em "Vencidos e a vencer" numa única consulta paginada, sem contar como filtro ativo', async () => {
    const { el } = await abrir();
    expect(frota['listarDocumentos']).toHaveBeenCalledOnce();
    expect(frota['listarDocumentos']).toHaveBeenCalledWith(
      expect.objectContaining({ situacao: null, vencimentoAte: daquiA(15) }),
      { page: 0, size: 25, sort: ['dataVencimento,asc'] }
    );
    expect(chipsDoResumo(el)).toEqual(['Situação: Vencidos e a vencer']);
    expect(el.querySelector('button[aria-label="Remover filtro Situação: Vencidos e a vencer"]')).toBeNull();
    expect(el.textContent).not.toContain('filtro ativo');
    expect(el.textContent).toContain('CRLV');
  });

  it('link direto filtrado abre a lista no recorte e mostra o chip', async () => {
    const { el } = await abrir('/frota/documentos?situacao=VENCIDO');
    expect(frota['listarDocumentos']).toHaveBeenLastCalledWith(
      expect.objectContaining({ situacao: 'VENCIDO', vencimentoAte: null }),
      expect.anything()
    );
    expect(chipsDoResumo(el)).toEqual(['Situação: Vencido']);
  });

  it('filtro rápido equivale ao campo do painel e "Limpar" volta ao padrão', async () => {
    const { el, harness, componente } = await abrir();
    const rapido = Array.from(el.querySelectorAll('.ces-quick-filter')).find((b) => b.textContent!.includes('A vencer')) as HTMLButtonElement;
    rapido.click();
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(rapido.getAttribute('aria-pressed')).toBe('true');
    expect(chipsDoResumo(el)).toEqual(['Situação: A vencer']);
    expect(TestBed.inject(Router).url).toBe('/frota/documentos?situacao=A_VENCER');
    expect((componente as unknown as { lista: { filters: { situacao(): string } } }).lista.filters.situacao()).toBe('A_VENCER');

    (componente as unknown as { lista: { reset(): void } }).lista.reset();
    expect(frota['listarDocumentos']).toHaveBeenLastCalledWith(expect.objectContaining({ situacao: null, vencimentoAte: daquiA(15) }), expect.anything());
  });

  it('"n" abre o cadastro para quem edita documentos', async () => {
    permissoes = ['frota:documento:read', 'frota:documento:write'];
    const { componente } = await abrir();
    document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'n', bubbles: true }));
    expect((componente as unknown as { formularioAberto(): boolean }).formularioAberto()).toBe(true);
  });
});
