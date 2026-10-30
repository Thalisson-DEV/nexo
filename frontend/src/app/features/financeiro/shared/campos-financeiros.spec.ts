import '@angular/compiler';
import { FormControl, FormGroup } from '@angular/forms';
import { CampoFormulario } from '../../../core/models/financeiro.models';
import {
  agruparCampos,
  aplicarValidacaoCondicional,
  campoVisivel,
  camposVisiveis,
  CHAVE_DADOS_PAGAMENTO,
  CHAVE_FORMA_PAGAMENTO,
  ehBoleto,
  ehCampoDadosPagamento,
  ehCampoFormaPagamento,
  ehFormaPagamentoBoleto,
  filtrarPorModoFornecedor,
  limparCamposOcultos,
  limparFornecedorForaDoModo,
  modoFornecedorDe,
  ofereceEscolhaDeFornecedor,
  regrasCruzadas,
  serializarValores,
  textoDoValor
} from './campos-financeiros';

/**
 * Estas regras existem em duplicidade com o backend por conveniência de tela. O
 * que não pode acontecer é discordarem — e o caso do campo booleano é o motivo:
 * uma caixa de marcação reporta "não" sem que ninguém tenha respondido, e isso
 * fazia dois campos condicionais nunca aparecerem.
 */
describe('campos-financeiros', () => {
  function campo(parcial: Partial<CampoFormulario> & Pick<CampoFormulario, 'chave'>): CampoFormulario {
    return {
      id: parcial.chave,
      rotulo: parcial.chave,
      tipoCampo: 'TEXTO',
      campoCanonico: parcial.chave.toUpperCase(),
      obrigatorio: false,
      ordem: 0,
      grupo: 'NOTA_FISCAL',
      grupoRotulo: 'Nota fiscal',
      opcoes: [],
      ...parcial
    } as CampoFormulario;
  }

  const temNf = campo({ chave: 'temNf', tipoCampo: 'BOOLEANO', obrigatorio: true, ordem: 0 });
  const numeroNf = campo({
    chave: 'numeroNf',
    ordem: 1,
    obrigatorio: true,
    exibirQuando: { campoChave: 'temNf', valores: ['true'] }
  });
  const justificativa = campo({
    chave: 'justificativaSemNf',
    tipoCampo: 'TEXTO_LONGO',
    ordem: 2,
    obrigatorio: true,
    exibirQuando: { campoChave: 'temNf', valores: ['false'] }
  });
  const campos = [temNf, numeroNf, justificativa];

  function formulario(valores: Record<string, unknown> = {}): FormGroup {
    const grupo = new FormGroup({});
    for (const item of campos) {
      grupo.addControl(item.chave, new FormControl(valores[item.chave] ?? null));
    }
    return grupo;
  }

  describe('visibilidade condicional', () => {
    it('esconde os dois dependentes enquanto o booleano não for respondido', () => {
      const valorDe = () => null;

      expect(campoVisivel(temNf, campos, valorDe)).toBe(true);
      expect(campoVisivel(numeroNf, campos, valorDe)).toBe(false);
      expect(campoVisivel(justificativa, campos, valorDe)).toBe(false);
    });

    it('distingue "não respondido" de "respondido Não"', () => {
      const respondidoNao = (chave: string) => (chave === 'temNf' ? false : null);

      expect(campoVisivel(justificativa, campos, respondidoNao)).toBe(true);
      expect(campoVisivel(numeroNf, campos, respondidoNao)).toBe(false);
    });

    it('exibe o número da nota quando a resposta é Sim', () => {
      const respondidoSim = (chave: string) => (chave === 'temNf' ? true : null);

      expect(campoVisivel(numeroNf, campos, respondidoSim)).toBe(true);
      expect(campoVisivel(justificativa, campos, respondidoSim)).toBe(false);
    });

    it('trata condição sem governante no formulário como incondicional', () => {
      const orfao = [numeroNf];
      expect(campoVisivel(numeroNf, orfao, () => null)).toBe(true);
    });

    it('propaga a ocultação do governante para o dependente', () => {
      const encadeado = [
        campo({ chave: 'a', tipoCampo: 'BOOLEANO' }),
        campo({ chave: 'b', exibirQuando: { campoChave: 'a', valores: ['true'] } }),
        campo({ chave: 'c', exibirQuando: { campoChave: 'b', valores: ['x'] } })
      ];
      // "b" está oculto (a = false), então "c" não pode aparecer mesmo com b = 'x'.
      const valores: Record<string, unknown> = { a: false, b: 'x' };
      expect(campoVisivel(encadeado[2], encadeado, (chave) => valores[chave])).toBe(false);
    });

    it('lista apenas os campos visíveis, na ordem do formulário', () => {
      const valores: Record<string, unknown> = { temNf: false, justificativaSemNf: 'Feira livre' };
      expect(camposVisiveis(campos, (chave) => valores[chave]).map((item) => item.chave))
        .toEqual(['temNf', 'justificativaSemNf']);
    });
  });

  describe('agrupamento em blocos', () => {
    const comBlocos = [
      campo({ chave: 'baseId', grupo: 'IDENTIFICACAO', grupoRotulo: 'Identificação', ordem: 0 }),
      campo({ chave: 'processoId', grupo: 'IDENTIFICACAO', grupoRotulo: 'Identificação', ordem: 1 }),
      campo({ chave: 'valor', tipoCampo: 'MOEDA', grupo: 'VALORES', grupoRotulo: 'Valores e condições', ordem: 2 })
    ];

    it('junta os campos do mesmo assunto sob um título', () => {
      const blocos = agruparCampos(comBlocos);

      expect(blocos.map((bloco) => bloco.rotulo)).toEqual(['Identificação', 'Valores e condições']);
      expect(blocos[0].campos.map((c) => c.chave)).toEqual(['baseId', 'processoId']);
    });

    it('não devolve bloco vazio quando todos os seus campos estão ocultos', () => {
      // O catálogo garante que um campo condicionado mora no mesmo bloco do seu
      // governante, então na prática um bloco raramente some inteiro. A guarda
      // existe porque um bloco sem campo visível não deve render um título solto.
      const governanteNoutroBloco = [
        campo({ chave: 'temNf', tipoCampo: 'BOOLEANO' }),
        campo({
          chave: 'motivoSemCompra',
          grupo: 'ORIGEM',
          grupoRotulo: 'Origem do pagamento',
          exibirQuando: { campoChave: 'temNf', valores: ['false'] }
        })
      ];

      const blocos = agruparCampos(governanteNoutroBloco, () => null);

      expect(blocos.map((bloco) => bloco.grupo)).toEqual(['NOTA_FISCAL']);
    });

    it('preserva a ordem publicada pelo servidor', () => {
      const blocos = agruparCampos([...comBlocos].reverse());

      expect(blocos.map((bloco) => bloco.grupo)).toEqual(['VALORES', 'IDENTIFICACAO']);
    });
  });

  describe('escolha do fornecedor', () => {
    const trio = [
      campo({ chave: 'fornecedorId', tipoCampo: 'REFERENCIA', grupo: 'FORNECEDOR', grupoRotulo: 'Fornecedor' }),
      campo({ chave: 'fornecedorNome', grupo: 'FORNECEDOR', grupoRotulo: 'Fornecedor' }),
      campo({ chave: 'fornecedorRazaoSocial', grupo: 'FORNECEDOR', grupoRotulo: 'Fornecedor' })
    ];

    function formFornecedor(valores: Record<string, unknown> = {}): FormGroup {
      const grupo = new FormGroup({});
      for (const item of trio) {
        grupo.addControl(item.chave, new FormControl(valores[item.chave] ?? null));
      }
      return grupo;
    }

    it('reconhece que a variante oferece as duas formas', () => {
      expect(ofereceEscolhaDeFornecedor(trio)).toBe(true);
      expect(ofereceEscolhaDeFornecedor([trio[0]])).toBe(false);
    });

    it('mostra apenas o caminho escolhido', () => {
      expect(filtrarPorModoFornecedor(trio, 'CADASTRADO').map((c) => c.chave))
        .toEqual(['fornecedorId']);
      expect(filtrarPorModoFornecedor(trio, 'NOVO').map((c) => c.chave))
        .toEqual(['fornecedorNome', 'fornecedorRazaoSocial']);
    });

    it('deduz o modo do que já está preenchido, ao reabrir a solicitação', () => {
      expect(modoFornecedorDe(formFornecedor({ fornecedorId: 'uuid-1' }))).toBe('CADASTRADO');
      expect(modoFornecedorDe(formFornecedor({ fornecedorNome: 'Auto Peças' }))).toBe('NOVO');
      // Nada preenchido: o caminho do cadastro é o esperado na maioria dos casos.
      expect(modoFornecedorDe(formFornecedor())).toBe('CADASTRADO');
    });

    it('apaga o caminho abandonado, para nunca enviar as duas formas', () => {
      const form = formFornecedor({ fornecedorNome: 'Auto Peças', fornecedorRazaoSocial: 'Auto Peças Ltda' });

      limparFornecedorForaDoModo(form, 'CADASTRADO');

      expect(form.get('fornecedorNome')!.value).toBeNull();
      expect(form.get('fornecedorRazaoSocial')!.value).toBeNull();
      expect(serializarValores(trio, form)).toEqual({});
    });
  });

  describe('validação condicional', () => {
    it('não cobra obrigatório de campo oculto e volta a cobrar quando ele aparece', () => {
      const form = formulario();
      aplicarValidacaoCondicional(form, campos);
      // Sem resposta em "Tem NF", nenhum dos dois dependentes é exigido.
      expect(form.get('numeroNf')!.valid).toBe(true);
      expect(form.get('justificativaSemNf')!.valid).toBe(true);
      expect(form.get('temNf')!.valid).toBe(false);

      form.get('temNf')!.setValue(false);
      aplicarValidacaoCondicional(form, campos);
      expect(form.get('justificativaSemNf')!.valid).toBe(false);
      expect(form.get('numeroNf')!.valid).toBe(true);
    });

    it('limpa o resíduo de um campo que deixou de aparecer', () => {
      const form = formulario({ temNf: false, justificativaSemNf: 'Sem nota' });
      form.get('temNf')!.setValue(true);

      limparCamposOcultos(form, campos);

      expect(form.get('justificativaSemNf')!.value).toBeNull();
    });
  });

  describe('serialização', () => {
    it('envia só os campos visíveis e preenchidos', () => {
      const form = formulario({ temNf: true, numeroNf: '123', justificativaSemNf: 'resíduo' });

      expect(serializarValores(campos, form)).toEqual({ temNf: true, numeroNf: '123' });
    });

    it('envia o booleano falso, que é resposta e não ausência', () => {
      const form = formulario({ temNf: false, justificativaSemNf: 'Compra em feira livre' });

      expect(serializarValores(campos, form)).toEqual({
        temNf: false,
        justificativaSemNf: 'Compra em feira livre'
      });
    });

    it('converte data para o formato que o servidor espera', () => {
      const comData = [campo({ chave: 'vencimento', tipoCampo: 'DATA' })];
      const form = new FormGroup({ vencimento: new FormControl(new Date(2026, 8, 15)) });

      expect(serializarValores(comData, form)).toEqual({ vencimento: '2026-09-15' });
    });
  });

  describe('regras cruzadas', () => {
    const comFornecedor = [
      campo({ chave: 'fornecedorId', tipoCampo: 'REFERENCIA' }),
      campo({ chave: 'fornecedorNome' })
    ];

    it('cobra um dos dois fornecedores quando o formulário oferece ambos', () => {
      const form = new FormGroup({
        fornecedorId: new FormControl(null),
        fornecedorNome: new FormControl('  ')
      });

      expect(regrasCruzadas(comFornecedor, form)?.chaves).toEqual(['fornecedorId', 'fornecedorNome']);
    });

    it('aceita quando um dos dois está preenchido', () => {
      const form = new FormGroup({
        fornecedorId: new FormControl(null),
        fornecedorNome: new FormControl('Auto Peças do Zé')
      });

      expect(regrasCruzadas(comFornecedor, form)).toBeNull();
    });

    it('exige vencimento ao parcelar, quando a variante oferece o campo', () => {
      const comParcelas = [
        campo({ chave: 'quantidadeParcelas', tipoCampo: 'INTEIRO' }),
        campo({ chave: 'vencimento', tipoCampo: 'DATA' })
      ];
      const form = new FormGroup({
        quantidadeParcelas: new FormControl(3),
        vencimento: new FormControl(null)
      });

      expect(regrasCruzadas(comParcelas, form)?.chaves).toEqual(['vencimento']);
    });

    it('não replica a regra de qual forma de pagamento admite parcelamento', () => {
      // É regra derivada de enum no servidor; a cópia local foi o que fez a tela
      // discordar do backend. Aqui a combinação passa e o 422 é quem decide.
      const comForma = [
        campo({ chave: 'quantidadeParcelas', tipoCampo: 'INTEIRO' }),
        campo({ chave: 'formaPagamento', tipoCampo: 'SELECAO_UNICA' })
      ];
      const form = new FormGroup({
        quantidadeParcelas: new FormControl(3),
        formaPagamento: new FormControl('CARTAO_DEBITO')
      });

      expect(regrasCruzadas(comForma, form)).toBeNull();
    });
  });

  describe('texto de exibição', () => {
    it('traduz booleano para Sim/Não em vez de true/false', () => {
      expect(textoDoValor(temNf, true)).toBe('Sim');
      expect(textoDoValor(temNf, false)).toBe('Não');
      expect(textoDoValor(temNf, null)).toBeNull();
    });

    it('prefere a exibição já resolvida pelo servidor', () => {
      const referencia = campo({ chave: 'baseId', tipoCampo: 'REFERENCIA' });
      const exibicoes = { baseId: { valor: 'uuid-1', exibicao: 'Base Feira de Santana' } };

      expect(textoDoValor(referencia, 'uuid-1', exibicoes)).toBe('Base Feira de Santana');
    });

    it('usa o rótulo da opção escolhida', () => {
      const escolha = campo({
        chave: 'prioridade',
        tipoCampo: 'SELECAO_UNICA',
        opcoes: [{ valor: 'ALTA', rotulo: 'Alta' }]
      });

      expect(textoDoValor(escolha, 'ALTA')).toBe('Alta');
    });
  });

  describe('forma de pagamento boleto e dados bancários', () => {
    const campoForma = campo({
      chave: 'formaPagamento',
      tipoCampo: 'SELECAO_UNICA',
      opcoes: [
        { valor: 'BOLETO', rotulo: 'Boleto' },
        { valor: 'PIX_TRANSFERENCIA', rotulo: 'Pix ou Transferência' }
      ]
    });
    const campoDados = campo({
      chave: 'dadosPagamento',
      rotulo: 'Dados Bancários (Banco, AG, Conta, Pix)',
      tipoCampo: 'TEXTO_LONGO'
    });

    it('identifica corretamente os campos de forma de pagamento e dados bancários', () => {
      expect(ehCampoFormaPagamento(campoForma)).toBe(true);
      expect(ehCampoDadosPagamento(campoDados)).toBe(true);
      expect(ehCampoFormaPagamento(campoDados)).toBe(false);
      expect(ehCampoDadosPagamento(campoForma)).toBe(false);
    });

    it('identifica quando o valor selecionado é boleto', () => {
      expect(ehBoleto('BOLETO', campoForma)).toBe(true);
      expect(ehBoleto('boleto', campoForma)).toBe(true);
      expect(ehBoleto('PIX_TRANSFERENCIA', campoForma)).toBe(false);
      expect(ehBoleto(null, campoForma)).toBe(false);
    });

    it('detecta se o formulário está com forma de pagamento boleto', () => {
      const form = new FormGroup({
        formaPagamento: new FormControl('BOLETO'),
        dadosPagamento: new FormControl('boleto')
      });
      expect(ehFormaPagamentoBoleto(form, [campoForma, campoDados])).toBe(true);

      form.get('formaPagamento')!.setValue('PIX_TRANSFERENCIA');
      expect(ehFormaPagamentoBoleto(form, [campoForma, campoDados])).toBe(false);
    });
  });
});
