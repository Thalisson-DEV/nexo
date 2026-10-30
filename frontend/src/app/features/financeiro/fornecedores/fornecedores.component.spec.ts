import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { abrirListagem, chipsDoPainel, paginaTeste, tecla } from '../../../shared/listagem/listagem-teste';
import { FornecedoresComponent } from './fornecedores.component';

const pendente = { id: 'f1', nomeFantasia: 'ELÉTRICA BOA', razaoSocial: null, documento: '12345678000190', status: 'PENDENTE', utilizavel: true, ativo: true };

describe('FornecedoresComponent', () => {
  let servico: Record<string, ReturnType<typeof vi.fn>>;
  let permissoes: string[];
  const abrir = (url = '/lista') =>
    abrirListagem(FornecedoresComponent, 'lista', url, [
      { provide: FinanceiroService, useValue: servico },
      { provide: AuthService, useValue: { currentUser: () => ({ permissoes }) } }
    ]);

  beforeEach(() => {
    TestBed.resetTestingModule();
    permissoes = ['financeiro:fornecedor:write', 'financeiro:fornecedor:homologar'];
    servico = {
      listarFornecedores: vi.fn(() => paginaTeste([pendente])),
      criarFornecedor: vi.fn(() => of(pendente)),
      atualizarFornecedor: vi.fn(() => of(pendente)),
      acaoFornecedor: vi.fn(() => of(undefined)),
      bloquearFornecedor: vi.fn(() => of(undefined)),
      desativarFornecedor: vi.fn(() => of(undefined))
    };
  });

  afterEach(() => localStorage.clear());

  it('abre nos ativos (chip do padrão) com o CNPJ formatado', async () => {
    const { el } = await abrir();
    expect(servico['listarFornecedores']).toHaveBeenCalledWith(expect.objectContaining({ ativo: true, page: 0, size: 25, sort: 'nomeFantasia,asc' }));
    expect(el.textContent).toContain('12.345.678/0001-90');
    expect(chipsDoPainel(el)).toEqual(['Status: Ativos']);
  });

  it('a busca não vai para a URL', async () => {
    const { componente } = await abrir('/lista?status=PENDENTE');
    expect(servico['listarFornecedores']).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'PENDENTE' }));
    (componente as unknown as { lista: { query: { set(v: string): void } } }).lista.query.set('12345678000190');
    await new Promise((r) => setTimeout(r));
    expect(TestBed.inject(Router).url).not.toContain('12345678000190');
  });

  it('homologar sem razão social abre a edição em vez de chamar a API', async () => {
    const { el, componente } = await abrir();
    (el.querySelector('button[aria-label="Homologar ELÉTRICA BOA"]') as HTMLButtonElement).click();
    expect(servico['acaoFornecedor']).not.toHaveBeenCalled();
    expect((componente as unknown as { formularioAberto(): boolean }).formularioAberto()).toBe(true);
  });

  it('bloquear exige motivo', async () => {
    const { componente } = await abrir();
    const tela = componente as unknown as { abrirBloqueio(f: unknown): void; motivo: { set(v: string): void }; bloquear(): void };
    tela.abrirBloqueio(pendente);
    tela.bloquear();
    expect(servico['bloquearFornecedor']).not.toHaveBeenCalled();
    tela.motivo.set('Nota fiscal irregular');
    tela.bloquear();
    expect(servico['bloquearFornecedor']).toHaveBeenCalledWith('f1', 'Nota fiscal irregular');
  });

  it('sem permissão de escrita, "n" não abre o cadastro', async () => {
    permissoes = [];
    const { componente } = await abrir();
    tecla('n');
    expect((componente as unknown as { formularioAberto(): boolean }).formularioAberto()).toBe(false);
  });
});
