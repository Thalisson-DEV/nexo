import { ErrorHandler, Injectable } from '@angular/core';

/**
 * Recuperação de uma aba que ficou rodando um build antigo depois de um deploy.
 *
 * <p><b>O incidente que originou este arquivo (07/08/2026).</b> Um lançamento de desconto
 * foi gravado três vezes. Nenhuma das dezoito requisições falhou — todas voltaram 201.
 * O que falhou foi o passo seguinte: ao concluir, o formulário navega para a lista, a rota
 * é lazy, e o <i>chunk</i> dela veio <b>404</b>:</p>
 *
 * <pre>
 * 10:30:33  POST .../anexos                 201   ← último anexo, tudo gravado
 * 10:30:33  GET  /chunk-27Y3BWRZ.js         404   ← referrer: main-5BENJM2M.js
 * 10:30:41  POST /api/v1/descontos          201   ← usuário clica de novo: duplicata
 * </pre>
 *
 * <p>O build vigente era o <code>main-YBHCXDWH.js</code>. A aba daquele usuário carregava
 * o <code>main-5BENJM2M.js</code>, de antes do deploy: o arquivo continuava no cache do
 * navegador (assets com hash são servidos como <code>immutable</code>), mas os chunks que
 * ele importa sob demanda já não existiam no servidor — cada deploy substitui o
 * <code>dist</code> inteiro.</p>
 *
 * <p>Do lado do usuário isso não parece um erro de versão: ele salvou, a tela não mudou,
 * e nada disse que tinha dado certo. O reflexo — apertar Salvar de novo — refazia o fluxo
 * do zero. As três defesas contra duplicidade (retomada do passo concluído, chave de
 * idempotência estável, coalescência de requisições em voo) estavam corretas e no ar, mas
 * moram justamente no bundle que aquela aba não tinha carregado.</p>
 *
 * <p><b>A correção:</b> um chunk que não existe mais é o sinal inequívoco de que esta aba
 * está velha. Em vez de deixá-la seguir meio quebrada, recarrega-se a página — o
 * <code>index.html</code> é servido com <code>no-cache</code>, então o reload traz o build
 * novo, e o usuário reencontra a tela funcionando.</p>
 *
 * @author <b>Thalisson</b>
 */

/**
 * Marca do último reload automático, para não entrar em laço.
 *
 * Fica em `sessionStorage` porque o escopo desejado é exatamente o da aba: outra aba
 * pode legitimamente precisar do seu próprio reload, e fechar a aba encerra o assunto.
 */
const CHAVE_ULTIMO_RELOAD = 'ces:reload-versao-obsoleta';

/**
 * Janela mínima entre dois reloads automáticos.
 *
 * Se depois de recarregar o chunk ainda faltar, o problema não é versão obsoleta — é um
 * arquivo realmente ausente no servidor. Recarregar de novo só trocaria uma tela quebrada
 * por um piscar infinito, que é pior: esconde o erro e impede o usuário de ler a tela.
 */
const JANELA_ENTRE_RELOADS_MS = 60_000;

/**
 * Reconhece a falha de carregar um módulo que o servidor não tem mais.
 *
 * <p>A mensagem varia entre navegadores e entre a versão do bundler, e não há um código
 * de erro padronizado — por isso a checagem é por texto, sobre a união das formas
 * conhecidas. Um falso positivo custa um reload; um falso negativo custa o incidente
 * inteiro de novo.</p>
 */
export function ehErroDeChunkAusente(erro: unknown): boolean {
  const alvo = erro as { name?: string; message?: string } | null;
  if (!alvo) return false;

  if (alvo.name === 'ChunkLoadError') return true;

  const mensagem = typeof alvo.message === 'string' ? alvo.message.toLowerCase() : '';
  if (!mensagem) return false;

  return (
    mensagem.includes('failed to fetch dynamically imported module') ||
    mensagem.includes('error loading dynamically imported module') ||
    mensagem.includes('importing a module script failed') ||
    mensagem.includes('loading chunk') ||
    mensagem.includes('dynamically imported module')
  );
}

/**
 * Recarrega a aba a partir do servidor, no máximo uma vez por janela.
 *
 * @param destino para onde ir depois de recarregar. Quando informado, a aba vai para lá
 *        em vez de recarregar onde está — é a diferença entre o usuário reencontrar a
 *        tela que ele pediu e ele voltar ao formulário vazio achando que perdeu o
 *        trabalho. Sem destino (chunk que falhou fora de uma navegação), recarrega no
 *        lugar.
 * @returns `true` quando o recarregamento foi disparado; `false` quando foi contido pelo
 *          laço, caso em que o chamador deve deixar o erro seguir para o tratamento normal.
 */
export function recarregarPorVersaoObsoleta(destino?: string): boolean {
  const agora = Date.now();

  try {
    const ultimo = Number(sessionStorage.getItem(CHAVE_ULTIMO_RELOAD) ?? 0);
    if (agora - ultimo < JANELA_ENTRE_RELOADS_MS) return false;
    sessionStorage.setItem(CHAVE_ULTIMO_RELOAD, String(agora));
  } catch {
    // sessionStorage indisponível (modo restrito, cota estourada): sem a marca não há
    // como conter o laço, então prefere-se não recarregar a arriscar o piscar infinito.
    return false;
  }

  console.warn('Versão obsoleta detectada (chunk ausente). Recarregando a aplicação...');

  // `assign` e não `reload`: o index.html é servido com no-cache, então qualquer uma das
  // duas traz o build novo — mas só o `assign` deixa o usuário no lugar para onde ele
  // estava indo. Restringido a caminho relativo para que um destino inesperado não vire
  // um redirecionamento para fora da aplicação.
  if (destino && destino.startsWith('/') && !destino.startsWith('//')) {
    location.assign(destino);
  } else {
    location.reload();
  }
  return true;
}

/**
 * Trata a falha de navegação para uma rota lazy cujo chunk sumiu.
 *
 * <p>Este é o caminho exato do incidente: {@code router.navigate} depois de salvar. O
 * roteador entrega o erro aqui em vez de deixá-lo virar uma rejeição silenciosa — a
 * navegação simplesmente não acontecer é o que fazia o usuário achar que o salvamento
 * tinha falhado e lançar tudo de novo.</p>
 */
export function tratarErroDeNavegacao(falha: { url?: string; error?: unknown }): void {
  if (ehErroDeChunkAusente(falha?.error)) {
    recarregarPorVersaoObsoleta(falha.url);
    return;
  }
  console.error('Falha ao navegar:', falha?.error ?? falha);
}

/**
 * Rede de segurança para os chunks que não passam pelo roteador — `@defer`, diálogos e
 * componentes carregados sob demanda fora de uma navegação.
 *
 * <p>Delega ao {@link ErrorHandler} padrão tudo que não for versão obsoleta: este handler
 * existe para um sintoma específico, não para virar o dono de todos os erros da aplicação.</p>
 */
@Injectable()
export class VersaoObsoletaErrorHandler extends ErrorHandler {
  override handleError(erro: unknown): void {
    if (ehErroDeChunkAusente(erro) && recarregarPorVersaoObsoleta()) return;
    super.handleError(erro);
  }
}
