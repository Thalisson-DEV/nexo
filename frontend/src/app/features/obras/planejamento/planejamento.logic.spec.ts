import { describe, expect, it } from 'vitest';
import { KitAplicado, PlanejamentoObra, PontoPlanejamento } from '../../../core/models/obras-planejamento.models';
import { compararNumero, estadoDoLocal, janelaVisivel, kitBloqueado, montarLinhas, proximoNumero } from './planejamento.logic';

function ponto(id: string, numero: string, pagina = 1): PontoPlanejamento {
  return { id, numero, pagina, x: 0, y: 0, tipo: 'POSTE', tipoExecucao: 'NAO_DEFINIDO', temPoda: false, temAterramento: false, origem: 'MANUAL', versao: 0 };
}

function kit(id: string, local: { idPonto?: string; idVao?: string }, status: 'PENDENTE' | 'PROGRAMADO' = 'PENDENTE'): KitAplicado {
  return {
    id, ...local, idKit: 'k', codigo: 'LMI-0001', descricao: 'CAVA NORMAL', operacao: 'INSTALAR', multiplicador: 1, executadoAntes: false, versao: 0,
    itens: [
      { id: `${id}-s`, tipo: 'SERVICO', descricao: 'CAVA EM ROCHA', operacao: 'INSTALAR', unidade: 'CDA', quantidadePrevista: 1, quantidadeExecutada: 0, status, versao: 0 },
      { id: `${id}-m`, tipo: 'MATERIAL', descricao: 'ARRUELA', operacao: 'INSTALAR', unidade: 'CDA', quantidadePrevista: 4, quantidadeExecutada: 0, status: 'PENDENTE', versao: 0 }
    ]
  };
}

function plano(parcial: Partial<PlanejamentoObra>): PlanejamentoObra {
  return {
    idObra: 'o', numeroObra: '1', tituloObra: 't', rotacoes: {}, pontos: [], vaos: [], kits: [], anotacoes: [], avancoKits: [],
    indicadores: { percentualExecutado: 0, valorExecutado: 0, valorProgramado: 0, valorPendente: 0, valorTotal: 0, servicosSemValor: 0, pontosSemKit: 0, vaosSemKit: 0 },
    ...parcial
  };
}

const opcoes = { busca: '', soServicos: false, recolhidos: new Set<string>() };

describe('planejamento.logic', () => {
  it('marca o local como atrasado quando a programação do item passou do fim', () => {
    const programado = kit('k1', { idPonto: 'p1' }, 'PROGRAMADO');
    programado.itens[0].idProgramacao = 'pr1';

    expect(estadoDoLocal([programado])).toBe('programado');
    expect(estadoDoLocal([programado], new Set(['pr1']))).toBe('atrasado');
  });

  it('mostra a programação no item e só deixa retirar o que não tem execução', () => {
    const programado = kit('k1', { idPonto: 'p1' }, 'PROGRAMADO');
    programado.itens[0].idProgramacao = 'pr1';
    const situacao = { id: 'pr1', numero: 1687, idEquipe: 'e', codigoEquipe: 'RET-04', inicio: '02/11/2026', fim: '03/11/2026', situacao: 'ATRASADA' as const };
    const linhas = montarLinhas(plano({ pontos: [ponto('p1', '1')], kits: [programado], programacoes: [situacao] }), opcoes);

    const item = linhas.find((l) => l.idItem === 'k1-s')!;
    expect(item.programacao?.codigoEquipe).toBe('RET-04');
    expect(item.podeRetirar).toBe(true);
    expect(linhas.find((l) => l.tipo === 'ponto')!.estado).toBe('atrasado');
  });

  it('ordena números de ponto em ordem natural e sugere o próximo', () => {
    expect(['10', '2', '1'].sort(compararNumero)).toEqual(['1', '2', '10']);
    expect(proximoNumero([ponto('a', '2'), ponto('b', '10'), ponto('c', '4A')])).toBe('11');
    expect(proximoNumero([])).toBe('1');
  });

  it('resume o estado do local e bloqueia kit com item programado', () => {
    expect(estadoDoLocal([])).toBe('sem-kit');
    expect(estadoDoLocal([kit('1', { idPonto: 'a' })])).toBe('pendente');
    expect(estadoDoLocal([kit('1', { idPonto: 'a' }, 'PROGRAMADO')])).toBe('programado');
    expect(kitBloqueado(kit('1', { idPonto: 'a' }))).toBe(false);
    expect(kitBloqueado(kit('1', { idPonto: 'a' }, 'PROGRAMADO'))).toBe(true);
  });

  it('monta página, ponto, kit, itens e vãos', () => {
    const linhas = montarLinhas(plano({
      pontos: [ponto('a', '2'), ponto('b', '4', 2)],
      vaos: [{ id: 'v', idPontoA: 'a', idPontoB: 'b', metragem: 35, origem: 'MANUAL', versao: 0 }],
      kits: [kit('1', { idPonto: 'a' })]
    }), opcoes);

    expect(linhas.map((l) => l.tipo)).toEqual(['pagina', 'ponto', 'kit', 'item', 'item', 'pagina', 'ponto', 'secao-vaos', 'vao']);
    expect(linhas.find((l) => l.tipo === 'vao')?.titulo).toBe('P2 — P4');
    expect(linhas.find((l) => l.tipo === 'vao')?.detalhe).toBe('35 m');
  });

  it('só serviços esconde materiais, e o recolhido esconde os filhos', () => {
    const dados = plano({ pontos: [ponto('a', '2')], kits: [kit('1', { idPonto: 'a' })] });

    expect(montarLinhas(dados, { ...opcoes, soServicos: true }).filter((l) => l.tipo === 'item')).toHaveLength(1);
    expect(montarLinhas(dados, { ...opcoes, recolhidos: new Set(['ponto:a']) }).map((l) => l.tipo)).toEqual(['pagina', 'ponto']);
  });

  it('a busca mostra só o que combina, sem acento e mesmo recolhido', () => {
    const dados = plano({ pontos: [ponto('a', '2'), ponto('b', '4')], kits: [kit('1', { idPonto: 'a' })] });

    const linhas = montarLinhas(dados, { ...opcoes, busca: 'arruela', recolhidos: new Set(['ponto:a']) });

    expect(linhas.filter((l) => l.tipo === 'ponto').map((l) => l.titulo)).toEqual(['P2']);
    expect(linhas.filter((l) => l.tipo === 'item').map((l) => l.titulo)).toEqual(['ARRUELA']);
  });

  it('só sem kit esconde o que já foi planejado', () => {
    const dados = plano({
      pontos: [ponto('a', '2'), ponto('b', '4')],
      vaos: [{ id: 'v', idPontoA: 'a', idPontoB: 'b', origem: 'MANUAL', versao: 0 }],
      kits: [kit('1', { idPonto: 'a' })]
    });

    const linhas = montarLinhas(dados, { ...opcoes, soSemKit: true });

    expect(linhas.filter((l) => l.tipo === 'ponto' || l.tipo === 'vao').map((l) => l.titulo)).toEqual(['P4', 'P2 — P4']);
  });

  it('item programado não pode ser selecionado', () => {
    const linhas = montarLinhas(plano({ pontos: [ponto('a', '2')], kits: [kit('1', { idPonto: 'a' }, 'PROGRAMADO')] }), opcoes);

    expect(linhas.find((l) => l.idItem === '1-s')?.selecionavel).toBe(false);
    expect(linhas.find((l) => l.idItem === '1-m')?.selecionavel).toBe(true);
  });

  it('desenha só a janela visível da lista', () => {
    expect(janelaVisivel(1000, 30, 3000, 600, 2)).toEqual({ inicio: 98, fim: 122 });
    expect(janelaVisivel(5, 30, 0, 600, 2)).toEqual({ inicio: 0, fim: 5 });
  });
});
