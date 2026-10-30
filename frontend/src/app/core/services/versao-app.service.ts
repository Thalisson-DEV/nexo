import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { ConnectivityService } from './connectivity.service';

/**
 * Reconhece que o servidor passou a servir um build diferente do que esta aba está rodando.
 *
 * <p><b>Por que isso precisa existir.</b> Cada deploy substitui o <code>dist</code> inteiro
 * do nginx. Uma aba aberta desde antes continua funcionando — o bundle dela está no cache
 * do navegador, servido como <code>immutable</code> — até pedir uma rota lazy, cujo chunk
 * já não existe mais: 404, navegação morta, tela que não muda. Em 07/08/2026 isso fez um
 * desconto salvo com sucesso parecer não ter salvo, e o usuário lançou o mesmo desconto
 * três vezes.</p>
 *
 * <p>O {@link ../versao-obsoleta} conserta o estrago depois de ele acontecer. Este serviço
 * existe para que ele não aconteça: avisa <i>antes</i> de o usuário esbarrar no 404, em um
 * momento em que ele ainda pode escolher a hora de atualizar.</p>
 *
 * <p><b>Como a versão é lida.</b> Não há arquivo de versão a manter: a identidade do build
 * é o hash que o próprio Angular põe no nome do bundle de entrada
 * (<code>main-YBHCXDWH.js</code>). Comparar o que o servidor referencia agora com o que
 * esta aba carregou responde exatamente a pergunta que importa — "o meu app ainda é o app
 * que está no ar?" — sem nenhum passo extra de build para alguém esquecer de rodar.</p>
 *
 * @author <b>Thalisson</b>
 */

/** De quanto em quanto tempo perguntar ao servidor. */
const INTERVALO_DE_CHECAGEM_MS = 5 * 60_000;

/** Quanto tempo o aviso some quando o usuário responde "agora não". */
const ADIAMENTO_MS = 30 * 60_000;

/**
 * Nome do bundle de entrada, com o hash do build.
 *
 * O grupo do hash é opcional porque o `ng serve` serve `main.js` sem hash: em
 * desenvolvimento os dois lados leem o mesmo texto e a comparação nunca acusa diferença,
 * que é o comportamento desejado — não existe deploy para detectar ali.
 */
const PADRAO_DO_BUNDLE = /\bmain(?:-[A-Za-z0-9_]+)?\.js\b/;

@Injectable({ providedIn: 'root' })
export class VersaoAppService {
  private readonly connectivity = inject(ConnectivityService);
  private readonly destroyRef = inject(DestroyRef);

  /** Identidade do build que ESTA aba carregou. `null` desliga o serviço. */
  private readonly versaoEmExecucao = identidadeDoBundleEmExecucao();

  /** Verdadeiro quando há build novo no ar e o usuário ainda não pediu para adiar. */
  readonly atualizacaoDisponivel = signal(false);

  /** Trava o aviso depois que ele aparece uma vez: não há por que continuar perguntando. */
  private jaDetectada = false;

  private adiamento?: ReturnType<typeof setTimeout>;

  constructor() {
    if (!this.versaoEmExecucao) return;

    const timer = setInterval(() => void this.verificar(), INTERVALO_DE_CHECAGEM_MS);

    // Voltar para a aba é o instante de maior valor para checar: é quando o usuário esteve
    // fora por tempo indeterminado, e é logo antes de ele voltar a clicar em coisas.
    const aoVoltar = () => {
      if (document.visibilityState === 'visible') void this.verificar();
    };
    document.addEventListener('visibilitychange', aoVoltar);

    this.destroyRef.onDestroy(() => {
      clearInterval(timer);
      clearTimeout(this.adiamento);
      document.removeEventListener('visibilitychange', aoVoltar);
    });
  }

  /** Recarrega a aba para adotar o build novo. */
  atualizar(): void {
    location.reload();
  }

  /**
   * Esconde o aviso por um tempo.
   *
   * <p>Some, mas volta: quem está no meio de um lançamento tem o direito de terminar sem
   * interrupção, e não o de ficar para sempre em uma versão que já quebrou as rotas lazy.</p>
   */
  adiar(): void {
    this.atualizacaoDisponivel.set(false);
    clearTimeout(this.adiamento);
    this.adiamento = setTimeout(() => this.atualizacaoDisponivel.set(true), ADIAMENTO_MS);
  }

  private async verificar(): Promise<void> {
    if (this.jaDetectada || !this.connectivity.isOnline()) return;

    try {
      // `no-store` porque a pergunta é sobre o servidor, não sobre o cache — e é o cache
      // do próprio index.html que mascararia justamente a mudança que se quer detectar.
      const resposta = await fetch('index.html', { cache: 'no-store' });
      if (!resposta.ok) return;

      const publicada = PADRAO_DO_BUNDLE.exec(await resposta.text())?.[0];
      if (!publicada || publicada === this.versaoEmExecucao) return;

      console.info(`Nova versão publicada: ${publicada} (esta aba roda ${this.versaoEmExecucao}).`);
      this.jaDetectada = true;
      this.atualizacaoDisponivel.set(true);
    } catch {
      // Rede oscilando, servidor reiniciando no meio do deploy: nada a fazer e nada a
      // dizer ao usuário. A próxima checagem cobre.
    }
  }
}

/**
 * Lê a identidade do build a partir dos `<script>` que esta página carregou.
 *
 * @returns `null` quando nenhum bundle reconhecível está presente — caso em que não há
 *          base para comparar e o serviço se desliga em vez de arriscar um aviso falso.
 */
function identidadeDoBundleEmExecucao(): string | null {
  for (const script of Array.from(document.scripts)) {
    const encontrado = PADRAO_DO_BUNDLE.exec(script.getAttribute('src') ?? '');
    if (encontrado) return encontrado[0];
  }
  return null;
}
