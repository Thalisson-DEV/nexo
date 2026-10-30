import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { FinanceiroService } from '../../../core/services/financeiro.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { FormulariosFinanceirosComponent } from './formularios-financeiros.component';

const formulario = { id: 'f1', tipoSolicitacao: 'COMPRA', codigo: 'SERVICOS', titulo: 'Serviços', descricao: null, padrao: true, ativo: true };

describe('FormulariosFinanceirosComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  const abrir = (url = '/lista') => abrirListagem(FormulariosFinanceirosComponent, 'lista', url, [{ provide: FinanceiroService, useValue: servico }]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    servico = {
      listarFormularios: vi.fn(() => paginaTeste([formulario])),
      criarFormulario: vi.fn(() => of({ id: 'novo' })),
      duplicarFormulario: vi.fn(() => of({ id: 'copia' })),
      acaoFormulario: vi.fn(() => of(null))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos formulários ativos, sem etapa de pesquisa', async () => {
    const { el } = await abrir();
    expect(servico['listarFormularios']).toHaveBeenCalledWith({ buscaGlobal: '', tipoSolicitacao: null, ativo: true, page: 0, size: 25, sort: 'tipoSolicitacao,asc' });
    expect(el.textContent).toContain('Serviços');
    expect(chipsDoPainel(el)).toEqual(['Status: Ativos']);
  });

  it('mantém os parâmetros antigos da URL (tipo e ativo)', async () => {
    const { el } = await abrir('/lista?tipo=PAGAMENTO&ativo=false');
    expect(servico['listarFormularios']).toHaveBeenLastCalledWith(expect.objectContaining({ tipoSolicitacao: 'PAGAMENTO', ativo: false }));
    expect(chipsDoPainel(el)).toEqual(['Tipo: Pagamento', 'Status: Inativos']);
  });

  it('"n" abre o cadastro e, ao criar, vai para o construtor', async () => {
    const { componente } = await abrir();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    tecla('n');
    const tela = componente as unknown as { formularioAberto(): boolean; codigo: { set(v: string): void }; titulo: { set(v: string): void }; salvar(): void };
    expect(tela.formularioAberto()).toBe(true);
    tela.codigo.set('1X');
    tela.titulo.set('Obras');
    tela.salvar();
    expect(servico['criarFormulario']).not.toHaveBeenCalled();
    tela.codigo.set('OBRAS');
    tela.salvar();
    expect(servico['criarFormulario']).toHaveBeenCalledWith(expect.objectContaining({ codigo: 'OBRAS', titulo: 'Obras', tipoSolicitacao: 'COMPRA' }));
    expect(navegar).toHaveBeenCalledWith(['/financeiro/admin/formularios', 'novo']);
  });
});
