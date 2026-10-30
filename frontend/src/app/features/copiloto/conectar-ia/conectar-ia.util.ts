import { CesTone } from '@ces/ui';
import { ConexaoIa, SituacaoConexaoIa } from '../../../core/models/conexoes-ia.models';

/** Textos e apoios da tela "Conectar sua IA", escritos para quem nunca conectou nada. */

export type ProvedorIa = 'claude' | 'chatgpt' | 'outros';

/** Trecho de um passo: texto comum ou o nome exato de um botão/menu do aplicativo. */
export interface TrechoPasso {
  texto: string;
  botao: boolean;
}

export interface PassoProvedor {
  trechos: TrechoPasso[];
  dica?: string;
}

export interface Provedor {
  id: ProvedorIa;
  nome: string;
  icone: string;
  onde: string;
  passos: PassoProvedor[];
}

/** "Clique em [Configurações]" vira texto + nome do botão em destaque. */
export function trechos(texto: string): TrechoPasso[] {
  return texto
    .split(/(\[[^\]]+\])/)
    .filter(Boolean)
    .map((parte) => (parte.startsWith('[') ? { texto: parte.slice(1, -1), botao: true } : { texto: parte, botao: false }));
}

function passo(texto: string, dica?: string): PassoProvedor {
  return { trechos: trechos(texto), dica };
}

export const PROVEDORES: Provedor[] = [
  {
    id: 'claude',
    nome: 'Claude',
    icone: 'pi pi-sparkles',
    onde: 'No site claude.ai ou no aplicativo do Claude no computador.',
    passos: [
      passo('Abra o Claude, clique no seu nome no canto da tela e depois em [Configurações].'),
      passo('No menu da esquerda, clique em [Conectores] e depois em [Adicionar conector personalizado].',
        'Se essa opção não aparecer, o plano da sua conta do Claude ainda não permite conectores.'),
      passo('Em nome, escreva [Nexo]. Em URL, cole o endereço que você copiou e clique em [Adicionar].'),
      passo('Clique em [Conectar]. A tela do Nexo vai abrir: entre com o seu login e clique em [Autorizar].'),
      passo('Numa conversa nova, clique no botão de ferramentas, ligue o [Nexo] e faça a sua pergunta.')
    ]
  },
  {
    id: 'chatgpt',
    nome: 'ChatGPT',
    icone: 'pi pi-comments',
    onde: 'No site chatgpt.com, com uma conta que permite conectar aplicativos.',
    passos: [
      passo('Abra o ChatGPT, clique no seu nome e depois em [Configurações].'),
      passo('Clique em [Aplicativos e conectores], depois em [Configurações avançadas], e ligue o [Modo desenvolvedor].',
        'Se essa opção não aparecer, o seu plano do ChatGPT ainda não permite conectar outros sistemas.'),
      passo('Volte e clique em [Criar]. Em nome, escreva [Nexo]; em URL, cole o endereço que você copiou; em autenticação, escolha [OAuth]. Clique em [Criar].'),
      passo('A tela do Nexo vai abrir: entre com o seu login e clique em [Autorizar].'),
      passo('Numa conversa nova, clique em [+], depois em [Mais], escolha o [Nexo] e faça a sua pergunta.')
    ]
  },
  {
    id: 'outros',
    nome: 'Outros',
    icone: 'pi pi-th-large',
    onde: 'Claude Code, Cursor, VS Code e outros assistentes que aceitam conectar sistemas.',
    passos: [
      passo('No assistente, procure a opção de adicionar um conector e cole o endereço que você copiou.'),
      passo('Se abrir a tela do Nexo, entre com o seu login e clique em [Autorizar]. Pronto.'),
      passo('Se o assistente pedir uma chave em vez do login, clique em [Gerar chave de acesso], no alto desta tela, e cole a chave nele.')
    ]
  }
];

/** Perguntas para a pessoa testar logo depois de conectar. */
export const EXEMPLOS_PERGUNTA = [
  'Quais solicitações de material estão pendentes nesta semana?',
  'Quanto temos em estoque de cabo e onde ele está?',
  'Resuma as obras em andamento por situação.'
];

export const EXEMPLO_CADASTRO = 'Cadastre um motivo de desconto chamado "Avaria de ferramenta", com até 3 parcelas.';

export const SITUACAO: Record<SituacaoConexaoIa, { rotulo: string; tom: CesTone; icone: string }> = {
  CONECTADA: { rotulo: 'Conectada', tom: 'success', icone: 'pi-check-circle' },
  VENCIDA: { rotulo: 'Chave vencida', tom: 'warning', icone: 'pi-exclamation-triangle' },
  DESCONECTADA: { rotulo: 'Desconectada', tom: 'neutral', icone: 'pi-minus-circle' }
};

export function rotuloPermissao(c: Pick<ConexaoIa, 'permiteEscrita'>): string {
  return c.permiteEscrita ? 'Consultar e cadastrar' : 'Só consultar';
}

/** Ícone do aplicativo: provedor conhecido pelo nome, ou chave para conexões por chave. */
export function iconeConexao(c: Pick<ConexaoIa, 'nome' | 'aplicativo' | 'tipo'>): string {
  const nome = `${c.nome} ${c.aplicativo ?? ''}`.toLowerCase();
  if (nome.includes('claude')) return 'pi pi-sparkles';
  if (nome.includes('chatgpt') || nome.includes('openai')) return 'pi pi-comments';
  return c.tipo === 'CHAVE' ? 'pi pi-key' : 'pi pi-desktop';
}

/** Como a conexão foi feita, em linguagem simples. */
export function comoConectou(c: Pick<ConexaoIa, 'tipo'>): string {
  return c.tipo === 'CHAVE' ? 'Chave de acesso' : 'Pelo login';
}

/** "Agora há pouco", "Há 5 minutos", "Há 2 horas", "Ontem", "Há 3 dias" ou a data. */
export function tempoRelativo(iso: string | null | undefined, agora = new Date()): string {
  if (!iso) return 'Ainda não usou';
  const data = new Date(iso);
  const segundos = Math.round((agora.getTime() - data.getTime()) / 1000);
  if (Number.isNaN(segundos)) return '';
  if (segundos < 60) return 'Agora há pouco';
  const minutos = Math.floor(segundos / 60);
  if (minutos < 60) return minutos === 1 ? 'Há 1 minuto' : `Há ${minutos} minutos`;
  const horas = Math.floor(minutos / 60);
  if (horas < 24) return horas === 1 ? 'Há 1 hora' : `Há ${horas} horas`;
  const dias = Math.floor(horas / 24);
  if (dias === 1) return 'Ontem';
  if (dias < 30) return `Há ${dias} dias`;
  return data.toLocaleDateString('pt-BR');
}

/** Data e hora completas, para o detalhe. */
export function dataHora(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
}

/**
 * Copia para a área de transferência. Se a permissão do navegador não responder logo, usa a cópia
 * pela seleção de texto, que funciona em qualquer navegador.
 */
export async function copiarTexto(texto: string): Promise<boolean> {
  try {
    const resultado = await Promise.race([
      navigator.clipboard?.writeText(texto).then(() => true),
      new Promise<boolean>((resolver) => setTimeout(() => resolver(false), 800))
    ]);
    if (resultado) return true;
  } catch {
    // segue para a cópia pela seleção
  }
  const campo = document.createElement('textarea');
  campo.value = texto;
  campo.setAttribute('readonly', '');
  campo.style.position = 'fixed';
  campo.style.opacity = '0';
  document.body.appendChild(campo);
  campo.select();
  try {
    return document.execCommand('copy');
  } catch {
    return false;
  } finally {
    campo.remove();
  }
}
