import { MensagemAssistente, PacoteAssistente } from '../../core/models/assistente.models';
import { aplicarPacote, aplicarPacotes, novaResposta, observacoes, respostaDaMensagem, textoFinal } from './copiloto-pacotes';

const pacote = (turno: number, obj: PacoteAssistente['obj'], aba = 0): PacoteAssistente => ({
  placement: { turnIndex: turno, tabIndex: aba }, obj,
});

const mensagem: MensagemAssistente = {
  id: 'mensagem-1',
  clientMessageId: 'cliente-1',
  papel: 'ASSISTENTE',
  status: 'CONCLUIDA',
  conteudo: 'Resposta salva da conversa',
  dataCriacao: '2026-09-26T12:00:00Z',
};

describe('histórico do Copiloto', () => {
  it('abre a resposta salva quando a API antiga devolve pacotes como objeto', () => {
    const resposta = respostaDaMensagem({
      ...mensagem,
      pacotes: { array: true, nodeType: 'ARRAY' } as unknown as PacoteAssistente[],
    });

    expect(textoFinal(resposta)).toBe('Resposta salva da conversa');
    expect(resposta.concluida).toBe(true);
    expect(resposta.etapas).toEqual([]);
  });

  it('reconstitui texto dos pacotes quando a API devolve uma lista', () => {
    const resposta = respostaDaMensagem({
      ...mensagem,
      pacotes: [{ placement: { turnIndex: 1, tabIndex: 0 }, obj: { type: 'message_delta', content: 'Texto da linha do tempo' } }],
    });

    expect(textoFinal(resposta)).toBe('Texto da linha do tempo');
    expect(resposta.recebidos).toBe(1);
  });
});

describe('pacotes da resposta', () => {
  it('mantém observações por turno e ignora heartbeat no cursor de retomada', () => {
    const estado = novaResposta();
    expect(aplicarPacote(estado, pacote(0, { type: 'heartbeat' }))).toBe(estado);
    const resposta = aplicarPacotes(estado, [
      pacote(0, { type: 'message_delta', content: 'Consultando os dados.' }),
      pacote(1, { type: 'heartbeat' }),
      pacote(1, { type: 'message_delta', content: 'O total foi ' }),
      pacote(1, { type: 'message_delta', content: '42.' }),
    ]);
    expect(resposta.recebidos).toBe(3);
    expect(observacoes(resposta)).toEqual(['Consultando os dados.']);
    expect(textoFinal(resposta)).toBe('O total foi 42.');
  });

  it('fecha uma ferramenta com saída, fonte, prévia e arquivo', () => {
    const arquivo = { id: 'f1', nome: 'resultado.csv', tipoConteudo: 'text/csv', categoria: 'CSV' as const, tamanhoBytes: 10, origem: 'GERADO' as const };
    const resposta = aplicarPacotes(novaResposta(), [
      pacote(0, { type: 'tool_start', toolCallId: 't1', ferramenta: 'executar_python', titulo: 'Analisar dados', argumentos: { codigo: 'print(1)' } }, 1),
      pacote(0, { type: 'tool_output', toolCallId: 't1', stream: 'stdout', data: 'linha 1' }, 1),
      pacote(0, { type: 'tool_output', toolCallId: 't1', stream: 'stdout', data: ' linha 2' }, 1),
      pacote(0, { type: 'tool_output', toolCallId: 't1', stream: 'stderr', data: 'aviso' }, 1),
      pacote(0, { type: 'tool_result', toolCallId: 't1', status: 'ok', resumo: 'Concluído', resultado: 'r1', linhas: 1,
        previa: { colunas: ['valor'], linhas: [[1]], total: 1 },
        fontes: [{ numero: 1, titulo: 'Fonte', url: 'https://example.org', dominio: 'example.org' }], arquivos: [arquivo] }, 1),
    ]);
    expect(resposta.etapas[0]).toMatchObject({ status: 'ok', resumo: 'Concluído', resultado: 'r1', linhas: 1,
      previa: { colunas: ['valor'], linhas: [[1]], total: 1 },
      saida: [{ fluxo: 'stdout', texto: 'linha 1 linha 2' }, { fluxo: 'stderr', texto: 'aviso' }] });
    expect(resposta.fontes).toHaveLength(1);
    expect(resposta.arquivos).toEqual([arquivo]);
  });

  it('substitui artefato do mesmo código, deduplica navegação e registra título e erro', () => {
    const acao = { tipo: 'NAVIGATE', chaveDestino: 'estoques', rotulo: 'Abrir estoques', autoridade: 'estoque:read' };
    const resposta = aplicarPacotes(novaResposta(), [
      pacote(0, { type: 'artifact', artefato: { id: 'a1', codigo: 'a1', tipo: 'DASHBOARD', titulo: 'Antes', especificacao: { tipo: 'dashboard' } } }),
      pacote(0, { type: 'artifact', artefato: { id: 'a1', codigo: 'a1', tipo: 'DASHBOARD', titulo: 'Depois', especificacao: { tipo: 'dashboard' } } }),
      pacote(0, { type: 'navigation', acao }), pacote(0, { type: 'navigation', acao }),
      pacote(0, { type: 'title', titulo: 'Análise do estoque' }),
      pacote(0, { type: 'error', mensagem: 'Não foi possível concluir.' }),
    ]);
    expect(resposta.artefatos).toHaveLength(1);
    expect(resposta.artefatos[0].titulo).toBe('Depois');
    expect(resposta.acoes).toEqual([acao]);
    expect(resposta.titulo).toBe('Análise do estoque');
    expect(resposta.erro).toBe('Não foi possível concluir.');
  });

  it('encerra etapas abertas no stop e reconstrói histórico sem duplicar erro ou ação', () => {
    const acao = { tipo: 'NAVIGATE', chaveDestino: 'estoques', rotulo: 'Abrir estoques', autoridade: 'estoque:read' };
    const pacotes = [
      pacote(0, { type: 'tool_start', toolCallId: 't1', ferramenta: 'consultar_estoque' }, 1),
      pacote(0, { type: 'navigation', acao }),
      pacote(0, { type: 'error', mensagem: 'Falha no provedor.' }),
      pacote(0, { type: 'stop', status: 'FALHOU', duracaoMs: 100 }),
    ];
    const resposta = respostaDaMensagem({ ...mensagem, status: 'FALHOU', conteudo: 'Falha no provedor.',
      pacotes, acoes: [acao], metricas: { duracaoMs: 250 } });
    expect(resposta.etapas[0]).toMatchObject({ status: 'erro', erro: 'Interrompida antes de terminar.' });
    expect(resposta.acoes).toHaveLength(1);
    expect(resposta.erro).toBe('Falha no provedor.');
    expect(textoFinal(resposta)).toBe('');
    expect(resposta.duracaoMs).toBe(250);
  });
});
