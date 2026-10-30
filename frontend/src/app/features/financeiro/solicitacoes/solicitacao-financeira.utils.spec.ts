import { describe, expect, it } from 'vitest';
import {
  buscaGlobalParaConsulta,
  fornecedorEhNaoCadastrado,
  fornecedorLabel,
  fornecedorRazaoSocial,
  nomeSolicitante
} from './solicitacao-financeira.utils';

describe('nomeSolicitante', () => {
  it('prioriza o nome do colaborador vinculado', () => {
    expect(
      nomeSolicitante({
        nomeColaboradorSolicitante: 'MARIA SILVA',
        usuarioSolicitante: 'MSILVA'
      })
    ).toBe('MARIA SILVA');
  });

  it('usa o login quando o colaborador não pode ser identificado', () => {
    expect(
      nomeSolicitante({ nomeColaboradorSolicitante: null, usuarioSolicitante: 'MSILVA' })
    ).toBe('MSILVA');
  });

  it('informa quando nem colaborador nem usuário podem ser identificados', () => {
    expect(nomeSolicitante({ nomeColaboradorSolicitante: null, usuarioSolicitante: null })).toBe(
      'Solicitante não identificado'
    );
  });
});

describe('apresentação do fornecedor', () => {
  it('exibe nome, razão social e marcador de fornecedor livre', () => {
    const solicitacao = {
      fornecedor: {
        id: null,
        nome: 'Fornecedor informado',
        razaoSocial: 'Fornecedor Informado Comércio Ltda.',
        cadastrado: false
      }
    };

    expect(fornecedorLabel(solicitacao)).toBe('Fornecedor informado');
    expect(fornecedorRazaoSocial(solicitacao)).toBe('Fornecedor Informado Comércio Ltda.');
    expect(fornecedorEhNaoCadastrado(solicitacao)).toBe(true);
  });

  it('usa razão social como nome quando o nome está ausente', () => {
    const solicitacao = { fornecedor: { nome: null, razaoSocial: 'Razão Social Ltda.', cadastrado: true } };

    expect(fornecedorLabel(solicitacao)).toBe('Razão Social Ltda.');
    expect(fornecedorRazaoSocial(solicitacao)).toBeNull();
  });

  it('mantém o fallback para fornecedor ausente ou resposta legada', () => {
    expect(fornecedorLabel({ fornecedor: null })).toBe('—');
    expect(fornecedorLabel({})).toBe('—');
    expect(fornecedorEhNaoCadastrado({ fornecedor: null })).toBe(false);
  });
});

describe('buscaGlobalParaConsulta', () => {
  it('descarta busca vazia, remove bordas e preserva caracteres e espaços internos', () => {
    expect(buscaGlobalParaConsulta('   ')).toBeNull();
    expect(buscaGlobalParaConsulta(' PIX+@abc 123 ')).toBe('PIX+@abc 123');
  });
});
