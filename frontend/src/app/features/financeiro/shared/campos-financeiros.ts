import { AbstractControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';
import {
  CampoFormulario,
  GrupoCampo,
  ValorSolicitacao
} from '../../../core/models/financeiro.models';

/** Um bloco do formulário, com os campos que pertencem a ele. */
export interface GrupoDeCampos {
  grupo: GrupoCampo;
  rotulo: string;
  campos: CampoFormulario[];
}

/**
 * Regras de formulário do financeiro, num lugar só.
 *
 * Antes existiam duas cópias literais disto — uma no componente de renderização e
 * outra no componente de solicitação — e elas divergiram. Como o servidor reaplica
 * tudo de qualquer modo, estas funções existem apenas para a experiência de
 * preenchimento; a garantia continua sendo do backend. O que elas não podem é
 * discordar entre si, e é por isso que moram aqui.
 */

/** Chaves canônicas que participam de regras entre campos. */
const CHAVE_FORNECEDOR_CADASTRADO = 'fornecedorId';
const CHAVE_FORNECEDOR_NOME = 'fornecedorNome';
const CHAVE_FORNECEDOR_RAZAO = 'fornecedorRazaoSocial';
const CHAVE_PARCELAS = 'quantidadeParcelas';
const CHAVE_VENCIMENTO = 'vencimento';
export const CHAVE_FORMA_PAGAMENTO = 'formaPagamento';
export const CHAVE_DADOS_PAGAMENTO = 'dadosPagamento';

/** Identifica se o campo é o de Dados Bancários (ou dados para pagamento). */
export function ehCampoDadosPagamento(campo: CampoFormulario): boolean {
  return (
    campo.chave === CHAVE_DADOS_PAGAMENTO ||
    campo.campoCanonico === 'DADOS_PAGAMENTO' ||
    campo.rotulo.toLowerCase().includes('dados banc') ||
    campo.rotulo.toLowerCase().includes('dados para pagamento')
  );
}

/** Identifica se o campo é o de Forma de Pagamento. */
export function ehCampoFormaPagamento(campo: CampoFormulario): boolean {
  if (ehCampoDadosPagamento(campo)) return false;
  return (
    campo.chave === CHAVE_FORMA_PAGAMENTO ||
    campo.campoCanonico === 'FORMA_PAGAMENTO' ||
    campo.rotulo.toLowerCase().includes('forma de pagamento') ||
    campo.rotulo.toLowerCase().includes('condição de pagamento')
  );
}

/** Verifica se um determinado valor representa a forma de pagamento Boleto. */
export function ehBoleto(valor: unknown, campo?: CampoFormulario): boolean {
  if (valor === null || valor === undefined) return false;
  const texto = String(valor).trim().toUpperCase();
  if (texto === 'BOLETO') return true;
  if (campo?.opcoes) {
    const opcao = campo.opcoes.find((o) => String(o.valor).trim().toUpperCase() === texto);
    if (opcao && opcao.rotulo.trim().toUpperCase() === 'BOLETO') return true;
  }
  return false;
}

/** Verifica se o formulário está atualmente com Forma de Pagamento = Boleto. */
export function ehFormaPagamentoBoleto(form: FormGroup, campos: CampoFormulario[]): boolean {
  const campo = campos.find(ehCampoFormaPagamento);
  if (!campo) return false;
  const valor = form.get(campo.chave)?.value;
  return ehBoleto(valor, campo);
}

/**
 * Como o fornecedor está sendo informado.
 *
 * <p>O domínio aceita duas formas — um fornecedor do cadastro ou um nome digitado
 * — e exige exatamente uma delas. Mostrar os dois campos lado a lado fazia parecer
 * que ambos eram necessários, então a tela transforma isso numa escolha explícita
 * e só apresenta o caminho escolhido.</p>
 */
export type ModoFornecedor = 'CADASTRADO' | 'NOVO';

/** Campos que cada modo apresenta; o outro caminho fica fora da tela e do payload. */
const CAMPOS_POR_MODO: Record<ModoFornecedor, string[]> = {
  CADASTRADO: [CHAVE_FORNECEDOR_CADASTRADO],
  NOVO: [CHAVE_FORNECEDOR_NOME, CHAVE_FORNECEDOR_RAZAO]
};

/** Indica que a variante oferece as duas formas, e portanto cabe a escolha. */
export function ofereceEscolhaDeFornecedor(campos: CampoFormulario[]): boolean {
  const chaves = new Set(campos.map((campo) => campo.chave));
  return chaves.has(CHAVE_FORNECEDOR_CADASTRADO) && chaves.has(CHAVE_FORNECEDOR_NOME);
}

/**
 * Deduz o modo a partir do que já está preenchido — necessário ao reabrir uma
 * solicitação ou ao herdar os dados de uma compra.
 */
export function modoFornecedorDe(form: FormGroup): ModoFornecedor {
  return vazio(form.get(CHAVE_FORNECEDOR_CADASTRADO)?.value) &&
    !vazio(form.get(CHAVE_FORNECEDOR_NOME)?.value)
    ? 'NOVO'
    : 'CADASTRADO';
}

/** Esconde do bloco de fornecedor o caminho que não foi escolhido. */
export function filtrarPorModoFornecedor(
  campos: CampoFormulario[],
  modo: ModoFornecedor
): CampoFormulario[] {
  const permitidas = CAMPOS_POR_MODO[modo];
  return campos.filter(
    (campo) => !CHAVES_FORNECEDOR.includes(campo.chave) || permitidas.includes(campo.chave)
  );
}

/** Zera o caminho abandonado, para nunca enviarmos as duas formas juntas. */
export function limparFornecedorForaDoModo(form: FormGroup, modo: ModoFornecedor): void {
  const permitidas = CAMPOS_POR_MODO[modo];
  for (const chave of CHAVES_FORNECEDOR) {
    if (!permitidas.includes(chave)) {
      form.get(chave)?.reset(null, { emitEvent: false });
    }
  }
}

const CHAVES_FORNECEDOR: string[] = [
  CHAVE_FORNECEDOR_CADASTRADO,
  CHAVE_FORNECEDOR_NOME,
  CHAVE_FORNECEDOR_RAZAO
];

/** Um valor "não preenchido": nulo, indefinido ou texto em branco. */
export function vazio(valor: unknown): boolean {
  return valor === null || valor === undefined || (typeof valor === 'string' && !valor.trim());
}

/**
 * Indica se o campo deve aparecer, dado o estado atual do preenchimento.
 *
 * Espelha exatamente `ValidadorSolicitacao.visivel()` no backend: a condição é
 * transitiva (governante oculto esconde o dependente) e **resposta ausente não
 * satisfaz condição alguma** — é justamente por isso que um campo booleano precisa
 * ser respondido explicitamente, e não por uma caixa de marcação.
 */
export function campoVisivel(
  campo: CampoFormulario,
  campos: CampoFormulario[],
  valorDe: (chave: string) => unknown,
  visitados = new Set<string>()
): boolean {
  if (!campo.exibirQuando) return true;
  if (visitados.has(campo.chave)) return false;
  visitados.add(campo.chave);

  const governante = campos.find((item) => item.chave === campo.exibirQuando!.campoChave);
  // Governante ausente do formulário: o backend também trata como incondicional.
  if (!governante) return true;
  if (!campoVisivel(governante, campos, valorDe, visitados)) return false;

  const valor = valorDe(governante.chave);
  if (vazio(valor)) return false;
  return campo.exibirQuando.valores.includes(String(valor));
}

/** Campos atualmente visíveis, na ordem de exibição. */
export function camposVisiveis(
  campos: CampoFormulario[],
  valorDe: (chave: string) => unknown
): CampoFormulario[] {
  return campos.filter((campo) => campoVisivel(campo, campos, valorDe));
}

/**
 * Organiza os campos nos blocos a que pertencem, preservando a ordem em que o
 * servidor os publicou.
 *
 * <p>Um bloco só aparece se tiver ao menos um campo visível — por isso não existe
 * seção vazia na tela, e o administrador nunca precisou criar nem manter grupos:
 * o assunto é propriedade do campo.</p>
 *
 * @param valorDe quando informado, filtra pelos campos atualmente visíveis
 */
export function agruparCampos(
  campos: CampoFormulario[],
  valorDe?: (chave: string) => unknown
): GrupoDeCampos[] {
  const elegiveis = valorDe ? camposVisiveis(campos, valorDe) : campos;
  const blocos: GrupoDeCampos[] = [];

  for (const campo of elegiveis) {
    const atual = blocos[blocos.length - 1];
    if (atual?.grupo === campo.grupo) {
      atual.campos.push(campo);
    } else {
      blocos.push({ grupo: campo.grupo, rotulo: campo.grupoRotulo, campos: [campo] });
    }
  }
  return blocos;
}

/** Validadores derivados do que o schema publica para o campo. */
export function validadoresDoCampo(campo: CampoFormulario): ValidatorFn[] {
  const validadores: ValidatorFn[] = [];
  if (campo.obrigatorio) validadores.push(Validators.required);
  if (campo.restricoes?.tamanhoMaximo != null) {
    validadores.push(Validators.maxLength(campo.restricoes.tamanhoMaximo));
  }
  if (campo.restricoes?.valorMinimo != null) {
    validadores.push(Validators.min(campo.restricoes.valorMinimo));
  }
  if (campo.restricoes?.valorMaximo != null) {
    validadores.push(Validators.max(campo.restricoes.valorMaximo));
  }
  return validadores;
}

/**
 * Reaplica os validadores conforme a visibilidade corrente: campo oculto não é
 * cobrado, campo que voltou a aparecer volta a ser.
 */
export function aplicarValidacaoCondicional(form: FormGroup, campos: CampoFormulario[]): void {
  const valorDe = (chave: string) => form.get(chave)?.value;
  for (const campo of campos) {
    const control = form.get(campo.chave);
    if (!control) continue;
    control.setValidators(
      campoVisivel(campo, campos, valorDe) ? validadoresDoCampo(campo) : []
    );
    control.updateValueAndValidity({ emitEvent: false });
  }
}

/** Limpa o que ficou preenchido em campos que deixaram de aparecer. */
export function limparCamposOcultos(form: FormGroup, campos: CampoFormulario[]): void {
  const valorDe = (chave: string) => form.get(chave)?.value;
  for (const campo of campos) {
    const control = form.get(campo.chave);
    if (control && control.value != null && !campoVisivel(campo, campos, valorDe)) {
      control.reset(null, { emitEvent: false });
    }
  }
}

/**
 * Regras que envolvem mais de um campo e que valem a pena antecipar na tela.
 *
 * Ficam de fora, deliberadamente, as regras que dependem de conhecer o
 * comportamento de uma enumeração — como quais formas de pagamento admitem
 * parcelamento. Isso é regra do servidor; replicar a lista aqui foi exatamente o
 * que fez a tela discordar do backend. Nesses casos deixamos o 422 chegar e
 * mapeamos a mensagem sobre o campo.
 */
export function regrasCruzadas(
  campos: CampoFormulario[],
  form: FormGroup
): { chaves: string[]; mensagem: string } | null {
  const existe = (chave: string) => campos.some((campo) => campo.chave === chave);
  const valor = (chave: string) => form.get(chave)?.value;

  if (existe(CHAVE_FORNECEDOR_CADASTRADO) && existe(CHAVE_FORNECEDOR_NOME)) {
    if (vazio(valor(CHAVE_FORNECEDOR_CADASTRADO)) && vazio(valor(CHAVE_FORNECEDOR_NOME))) {
      return {
        chaves: [CHAVE_FORNECEDOR_CADASTRADO, CHAVE_FORNECEDOR_NOME],
        mensagem: 'Escolha um fornecedor cadastrado ou informe o nome do fornecedor.'
      };
    }
  }

  const parcelas = Number(valor(CHAVE_PARCELAS) ?? 1);
  if (parcelas > 1 && existe(CHAVE_VENCIMENTO) && vazio(valor(CHAVE_VENCIMENTO))) {
    return {
      chaves: [CHAVE_VENCIMENTO],
      mensagem: `Informe o vencimento da 1ª parcela para parcelar em ${parcelas} vezes.`
    };
  }

  return null;
}

/**
 * Monta o payload de envio: só os campos visíveis e preenchidos, com datas no
 * formato ISO que o servidor espera.
 */
export function serializarValores(
  campos: CampoFormulario[],
  form: FormGroup
): Record<string, unknown> {
  const bruto = form.getRawValue() as Record<string, unknown>;
  const valorDe = (chave: string) => bruto[chave];
  const resultado: Record<string, unknown> = {};

  for (const campo of camposVisiveis(campos, valorDe)) {
    const valor = bruto[campo.chave];
    if (vazio(valor)) continue;
    resultado[campo.chave] =
      campo.tipoCampo === 'DATA' && valor instanceof Date ? paraIso(valor) : valor;
  }
  return resultado;
}

/**
 * Texto legível de um valor ainda não enviado (etapa de revisão). Quando o
 * servidor já devolveu uma `exibicao` para aquele mesmo valor, ela tem
 * precedência — é ela que resolve nomes de referências.
 */
export function textoDoValor(
  campo: CampoFormulario,
  valor: unknown,
  exibicoes: Record<string, ValorSolicitacao> = {}
): string | null {
  if (vazio(valor)) return null;

  const doServidor = exibicoes[campo.chave];
  if (doServidor?.valor === valor && doServidor.exibicao) return doServidor.exibicao;

  switch (campo.tipoCampo) {
    case 'BOOLEANO':
      return valor ? 'Sim' : 'Não';
    case 'DATA':
      return valor instanceof Date ? valor.toLocaleDateString('pt-BR') : String(valor);
    case 'MOEDA':
      return Number(valor).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
    case 'SELECAO_UNICA':
      return campo.opcoes.find((opcao) => opcao.valor === valor)?.rotulo ?? String(valor);
    default:
      return String(valor);
  }
}

/** Converte o valor guardado no controle para o formato do payload. */
export function paraIso(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

/** Converte o que veio do servidor para o que o controle da tela espera. */
export function valorParaControle(campo: CampoFormulario | undefined, valor: unknown): unknown {
  if (campo?.tipoCampo === 'DATA' && typeof valor === 'string') {
    const [ano, mes, dia] = valor.split('-').map(Number);
    return new Date(ano, mes - 1, dia);
  }
  return valor;
}

/** Mensagem de erro local de um controle, traduzida para o rótulo do campo. */
export function mensagemDeErro(campo: CampoFormulario, control: AbstractControl | null): string | null {
  if (!control?.touched || !control.errors) return null;
  if (control.errors['required']) return `${campo.rotulo} é obrigatório.`;
  if (control.errors['maxlength']) {
    return `Use no máximo ${control.errors['maxlength'].requiredLength} caracteres.`;
  }
  if (control.errors['min']) return `O valor mínimo é ${control.errors['min'].min}.`;
  if (control.errors['max']) return `O valor máximo é ${control.errors['max'].max}.`;
  return `Confira o valor informado em ${campo.rotulo}.`;
}
