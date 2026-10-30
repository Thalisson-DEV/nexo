import {
  FormularioInspecao,
  RegistrarInspecaoRequest,
  sanitizarRegistrarInspecao,
  validarArquivoEvidencia,
  validarRegistrarInspecao,
} from './inspecao.models';

describe('contrato de lançamento de inspeção', () => {
  const formulario: FormularioInspecao = {
    id: 'f1', titulo: 'Checklist', descricao: null, ativo: true, emUso: true,
    grupos: [{
      id: 'g1', titulo: 'Grupo', ordem: 1,
      perguntas: [{
        id: 'p1', enunciado: 'Condição', tipoResposta: 'MULTIPLA_SELECAO',
        obrigatoria: true, ordem: 1,
        opcoes: [
          { id: 'o1', texto: 'Normal', geraDesvio: false, gravidade: null, ordem: 1 },
          { id: 'o2', texto: 'Crítica', geraDesvio: true, gravidade: 'CRITICA', ordem: 2 },
        ],
      }],
    }],
  };

  const request: RegistrarInspecaoRequest = {
    idFormulario: 'f1', idTipoInspecao: 't1', idProcesso: 'pr1', idBaseOperacional: 'b1',
    dataRealizacao: '2026-07-31T08:30:00-03:00',
    colaboradoresInspecionados: ['c1'],
    respostas: [{ idPergunta: 'p1', opcoesSelecionadas: ['o2'] }],
    desvios: [{ idPergunta: 'p1', idOpcaoResposta: 'o2', colaboradores: ['c1'] }],
    evidencias: ['ev-1'],
  };

  it('aceita uma resposta válida com exatamente um desvio gerador', () => {
    expect(validarRegistrarInspecao(formulario, request)).toEqual([]);
  });

  it('rejeita o lançamento sem tipo de inspeção selecionado', () => {
    const semTipo: RegistrarInspecaoRequest = { ...request, idTipoInspecao: '' };
    expect(validarRegistrarInspecao(formulario, semTipo)).toContain('Selecione o tipo de inspeção.');
  });

  it('rejeita opção geradora sem desvio e colaborador fora dos inspecionados', () => {
    const invalido: RegistrarInspecaoRequest = {
      ...request,
      desvios: [{ idPergunta: 'p1', idOpcaoResposta: 'o1', colaboradores: ['c2'] }],
    };
    const erros = validarRegistrarInspecao(formulario, invalido).join(' ');
    expect(erros).toContain('opção geradora válida');
    expect(erros).toContain('entre os inspecionados');
    expect(erros).toContain('precisa de um desvio correspondente');
  });

  it('remove campos somente leitura do objeto antes do POST', () => {
    const contaminado = structuredClone(request) as RegistrarInspecaoRequest & {
      idInspetor: string;
    };
    contaminado.idInspetor = 'não-enviar';
    Object.assign(contaminado.desvios[0], { gravidade: 'CRITICA', dataResolucao: 'não-enviar' });

    const limpo = sanitizarRegistrarInspecao(contaminado);
    expect('idInspetor' in limpo).toBeFalse();
    expect('gravidade' in limpo.desvios[0]).toBeFalse();
    expect('dataResolucao' in limpo.desvios[0]).toBeFalse();
    expect(limpo.desvios[0].status).toBe('ABERTO');
  });

  it('preserva status RESOLVIDO para desvio tratado in-loco ao sanitizar', () => {
    const tratado = structuredClone(request);
    tratado.desvios[0].status = 'RESOLVIDO';
    const limpo = sanitizarRegistrarInspecao(tratado);
    expect(limpo.desvios[0].status).toBe('RESOLVIDO');
  });

  it('rejeita o lançamento sem nenhuma evidência fotográfica', () => {
    const semEvidencia: RegistrarInspecaoRequest = { ...request, evidencias: [] };
    expect(validarRegistrarInspecao(formulario, semEvidencia).join(' '))
      .toContain('ao menos uma foto');
  });

  it('aceita o rascunho offline contando as evidências retidas localmente', () => {
    const semIdsRemotos: RegistrarInspecaoRequest = { ...request, evidencias: [] };
    expect(validarRegistrarInspecao(formulario, semIdsRemotos, { totalEvidencias: 2 }))
      .toEqual([]);
  });

  it('deduplica IDs de evidência repetidos no mesmo lançamento', () => {
    const repetido: RegistrarInspecaoRequest = {
      ...request,
      evidencias: ['ev-1', 'ev-2', 'ev-1'],
    };
    expect(sanitizarRegistrarInspecao(repetido).evidencias).toEqual(['ev-1', 'ev-2']);
  });

  it('recusa arquivo que não é imagem e aceita HEIC do iPhone', () => {
    expect(validarArquivoEvidencia({ size: 1024, type: 'application/pdf', name: 'laudo.pdf' }))
      .toContain('precisa ser uma imagem');
    expect(validarArquivoEvidencia({ size: 1024, type: 'image/heic', name: 'IMG_0001.HEIC' }))
      .toBeNull();
    expect(validarArquivoEvidencia({ size: 16 * 1024 * 1024, type: 'image/jpeg', name: 'a.jpg' }))
      .toContain('15 MB');
    // Sem contentType declarado, a extensão do nome decide — igual ao backend.
    expect(validarArquivoEvidencia({ size: 1024, type: '', name: 'campo.jpg' })).toBeNull();
  });
});
