import { describe, expect, it } from 'vitest';
import {
  destacarMarcacoes,
  extrairMarcacoes,
  inserirMarcacao,
  localizarMarcacaoEmDigitacao
} from './mencoes.util';

describe('extrairMarcacoes', () => {
  it('reconhece de 3 a 9 caracteres depois do @', () => {
    expect(extrairMarcacoes('oi @tha')).toEqual(['THA']);
    expect(extrairMarcacoes('oi @thalisson')).toEqual(['THALISSON']);
    expect(extrairMarcacoes('oi @th')).toEqual([]);
    expect(extrairMarcacoes('oi @thalissonn')).toEqual([]);
  });

  it('ignora caixa e não repete o mesmo login', () => {
    expect(extrairMarcacoes('@thalisson e @THALISSON')).toEqual(['THALISSON']);
  });

  it('não trata e-mail como marcação', () => {
    expect(extrairMarcacoes('escreva para suporte@fornecedor.com')).toEqual([]);
  });

  it('encontra várias marcações na mesma frase', () => {
    expect(extrairMarcacoes('@maria, @joao e @pedro789 vejam isto')).toEqual([
      'MARIA',
      'JOAO',
      'PEDRO789'
    ]);
  });
});

describe('destacarMarcacoes', () => {
  it('escapa o HTML antes de destacar', () => {
    expect(destacarMarcacoes('<script>alert(1)</script> @maria')).toBe(
      '&lt;script&gt;alert(1)&lt;/script&gt; <span class="marcacao">@maria</span>'
    );
  });

  it('realça mais forte a marcação do próprio usuário, sem depender da caixa', () => {
    expect(destacarMarcacoes('@maria e @joao', 'maria')).toBe(
      '<span class="marcacao marcacao-voce">@maria</span> e <span class="marcacao">@joao</span>'
    );
  });

  it('deixa e-mail intacto', () => {
    expect(destacarMarcacoes('suporte@fornecedor.com')).toBe('suporte@fornecedor.com');
  });
});

describe('localizarMarcacaoEmDigitacao', () => {
  it('devolve o trecho digitado depois do @', () => {
    expect(localizarMarcacaoEmDigitacao('bom dia @tha', 12)).toEqual({
      termo: 'tha',
      inicio: 8,
      fim: 12
    });
  });

  it('não abre autocomplete dentro de um e-mail', () => {
    expect(localizarMarcacaoEmDigitacao('suporte@forn', 12)).toBeNull();
  });

  it('não abre quando o cursor está fora do trecho', () => {
    expect(localizarMarcacaoEmDigitacao('@maria já respondeu', 19)).toBeNull();
  });
});

describe('inserirMarcacao', () => {
  it('substitui o trecho pelo login em maiúsculas e deixa um espaço', () => {
    const alvo = localizarMarcacaoEmDigitacao('bom dia @tha', 12)!;
    expect(inserirMarcacao('bom dia @tha', alvo, 'thalisson')).toEqual({
      texto: 'bom dia @THALISSON ',
      cursor: 19
    });
  });

  it('não duplica o espaço quando já existe um depois do trecho', () => {
    const alvo = localizarMarcacaoEmDigitacao('oi @ma tudo bem', 6)!;
    expect(inserirMarcacao('oi @ma tudo bem', alvo, 'MARIA').texto).toBe('oi @MARIA tudo bem');
  });
});
