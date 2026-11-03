import { describe, expect, it } from 'vitest';
import { EstruturaChecklist, ItemResposta, PontoDocumentacao } from '../../../core/models/obras-documentacao.models';
import { alternarOpcao, itensFaltando, novaPergunta, opcoesDoTexto, paraInformadas, pendentes, problemaDaEstrutura, respostasIniciais } from './checklist.logic';

const estrutura: EstruturaChecklist = {
  grupos: [{
    titulo: 'Poste',
    perguntas: [
      { id: 'a', enunciado: 'Acesso livre?', tipo: 'ESCOLHA_UNICA', obrigatoria: true, aceitaNaoSeAplica: false, opcoes: ['Sim', 'Não'] },
      { id: 'b', enunciado: 'Altura', tipo: 'NUMERO', obrigatoria: true, aceitaNaoSeAplica: true, opcoes: null },
      { id: 'c', enunciado: 'Data da foto', tipo: 'DATA', obrigatoria: false, aceitaNaoSeAplica: false, opcoes: null }
    ]
  }]
};

describe('checklist.logic', () => {
  it('aponta o primeiro problema da estrutura', () => {
    expect(problemaDaEstrutura({ grupos: [] })).toContain('grupo');
    expect(problemaDaEstrutura({ grupos: [{ titulo: '', perguntas: [] }] })).toContain('título');
    expect(problemaDaEstrutura({ grupos: [{ titulo: 'G', perguntas: [{ ...novaPergunta('VISITA_PREVIA'), enunciado: 'P', opcoes: ['Sim'] }] }] })).toContain('duas opções');
    expect(problemaDaEstrutura(estrutura)).toBeNull();
  });

  it('recusa foto pedida sem título', () => {
    expect(problemaDaEstrutura({ ...estrutura, evidencias: [{ id: 'f', titulo: ' ', obrigatoria: true }] })).toContain('fotos pedidas');
    expect(problemaDaEstrutura({ ...estrutura, evidencias: [{ id: 'f', titulo: 'Aterramento', obrigatoria: true }] })).toBeNull();
  });

  it('lista só os itens obrigatórios ainda sem foto', () => {
    const ponto = {
      itensEvidencia: [
        { id: 'a', titulo: 'Poste', obrigatoria: true },
        { id: 'b', titulo: 'Aterramento', obrigatoria: true },
        { id: 'c', titulo: 'Entorno', obrigatoria: false }
      ],
      evidencias: [{ idItem: 'a' }, { idItem: null }]
    } as unknown as PontoDocumentacao;
    expect(itensFaltando(ponto).map((item) => item.titulo)).toEqual(['Aterramento']);
  });

  it('a pergunta nova da ficha As Built aceita "Não se aplica"', () => {
    expect(novaPergunta('AS_BUILT').aceitaNaoSeAplica).toBe(true);
    expect(novaPergunta('VISITA_PREVIA').aceitaNaoSeAplica).toBe(false);
  });

  it('lê as opções digitadas uma por linha', () => {
    expect(opcoesDoTexto(' Sim \n\nNão\n')).toEqual(['Sim', 'Não']);
  });

  it('lista só as obrigatórias sem resposta e conta "Não se aplica" como resposta', () => {
    const respostas = respostasIniciais(estrutura);
    expect(pendentes(estrutura, respostas).map((p) => p.id)).toEqual(['a', 'b']);
    respostas['a'].selecionadas = ['Sim'];
    respostas['b'].naoSeAplica = true;
    expect(pendentes(estrutura, respostas)).toEqual([]);
  });

  it('volta a preencher o que já foi respondido e envia cada tipo no seu formato', () => {
    const itens = [
      { idPergunta: 'a', selecionadas: ['Não'], valor: null, naoSeAplica: false },
      { idPergunta: 'b', selecionadas: [], valor: '11.5', naoSeAplica: false },
      { idPergunta: 'c', selecionadas: [], valor: '2026-10-30', naoSeAplica: false }
    ] as ItemResposta[];
    expect(paraInformadas(estrutura, respostasIniciais(estrutura, itens))).toEqual([
      { idPergunta: 'a', valor: null, selecionadas: ['Não'], naoSeAplica: false },
      { idPergunta: 'b', valor: '11.5', selecionadas: [], naoSeAplica: false },
      { idPergunta: 'c', valor: '2026-10-30', selecionadas: [], naoSeAplica: false }
    ]);
  });

  it('a escolha única troca a opção e a múltipla acumula', () => {
    const unica = estrutura.grupos[0].perguntas[0];
    expect(alternarOpcao(unica, ['Sim'], 'Não')).toEqual(['Não']);
    expect(alternarOpcao(unica, ['Sim'], 'Sim')).toEqual([]);
    expect(alternarOpcao({ ...unica, tipo: 'MULTIPLA_ESCOLHA' }, ['Sim'], 'Não')).toEqual(['Sim', 'Não']);
  });
});
