import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, effect, input, output, signal, untracked, viewChild } from '@angular/core';
import { IconeComponent } from './icone.component';
import { Estabilizador, janelaNoQuadro, sobreALinha } from './leitor/bip';
import { Motor, cameraDisponivel, criarMotores } from './leitor/motores';

export type TomRetorno = 'ok' | 'atencao' | 'erro' | 'neutro';
export interface RetornoLeitura { texto: string; tom: TomRetorno }
export type ModoLeitor = 'auto' | 'gatilho';

const INTERVALO_LEITURA_MS = 90;
/** O recorte da mira vai ao decodificador com no máximo esta largura (nitidez sem pesar). */
const LARGURA_MAXIMA_RECORTE = 1280;
const ZOOMS = [1, 2, 3];
const CHAVE_MODO = 'ces.leitor.modo';

/** Câmera disponível: com o ZXing em WebAssembly, qualquer navegador com câmera lê códigos. */
export function leitorPorCameraDisponivel(): boolean {
  return cameraDisponivel();
}

function lerModo(): ModoLeitor {
  try { return localStorage.getItem(CHAVE_MODO) === 'gatilho' ? 'gatilho' : 'auto'; } catch { return 'auto'; }
}

/**
 * A câmera como um leitor de bip: só decodifica a faixa da mira, aceita um código por vez (o que
 * está sobre a linha vermelha) e bipa uma vez por código — o mesmo código só conta de novo depois
 * de sair da mira. Usa o leitor do sistema (ML Kit no Android) e o ZXing-C++ em WebAssembly,
 * alternando entre eles; funciona em qualquer navegador com câmera.
 *
 * - **Automático**: bipa sozinho ao centralizar o código na linha;
 * - **Gatilho**: só lê enquanto o botão está pressionado — um aperto, um bip.
 *
 * Cada código aceito sai em `lido`; quem usa devolve o resultado em `retorno` (cor da mira,
 * texto grande, vibração e som).
 */
@Component({
  selector: 'ces-leitor-codigo',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="leitor" [class.cheio]="telaCheia()" [class.pausado]="pausado()" [attr.data-tom]="retornoVisivel()?.tom ?? null"
      [attr.data-modo]="modo()" [class.segurando]="segurando()"
      [attr.role]="telaCheia() ? 'dialog' : null" [attr.aria-modal]="telaCheia() ? 'true' : null" [attr.aria-label]="telaCheia() ? 'Leitor de códigos' : null">
      <video #video playsinline autoplay [muted]="true" aria-hidden="true"></video>
      <div #janela class="janela" aria-hidden="true"><i class="laser"></i></div>

      <div class="topo">
        @if (retornoVisivel(); as r) {
          <span class="retorno" [attr.data-tom]="r.tom">{{ r.texto }}</span>
        } @else if (estado() === 'lendo' && !pausado()) {
          <span class="instrucao">{{ modo() === 'gatilho' ? 'Mire na linha e aperte o botão' : 'Centralize o código na linha vermelha' }}</span>
        }
      </div>

      @if (estado() === 'iniciando') {
        <span class="aviso"><span class="giro" aria-hidden="true"></span>Abrindo a câmera…</span>
      } @else if (estado() === 'erro') {
        <span class="aviso erro">
          <ces-icone nome="alerta" [tamanho]="20" /><span class="aviso__texto">{{ erro() }}</span>
          @if (podeTentarDeNovo()) { <button type="button" class="tentar" (click)="tentarDeNovo()">Tentar de novo</button> }
        </span>
      } @else if (pausado()) {
        <span class="aviso"><ces-icone nome="check" [tamanho]="20" />{{ motivoPausa() }}</span>
      }

      <div class="base">
        @if (contador(); as c) { <span class="contador ces-num" [attr.aria-label]="c + ' lidas'">{{ c }}</span> } @else { <span></span> }
        @if (modo() === 'gatilho' && estado() === 'lendo') {
          <button type="button" class="gatilho" [attr.aria-pressed]="segurando()" aria-label="Segure para ler o código na linha"
            (pointerdown)="apertar($event)" (pointerup)="soltar()" (pointercancel)="soltar()" (pointerleave)="soltar()"
            (keydown.space)="apertar($event)" (keyup.space)="soltar()" (contextmenu)="$event.preventDefault()">
            <ces-icone nome="codigo_barras" [tamanho]="30" />
          </button>
        } @else if (telaCheia()) {
          <button type="button" class="concluir" (click)="telaCheia.set(false)">Concluir</button>
        } @else { <span></span> }
        <div class="controles">
          <button type="button" class="controle modo" (click)="alternarModo()" [attr.aria-label]="modo() === 'gatilho' ? 'Modo gatilho. Trocar para automático' : 'Modo automático. Trocar para gatilho'">
            {{ modo() === 'gatilho' ? 'Gatilho' : 'Auto' }}</button>
          @if (temZoom()) {
            <button type="button" class="controle ces-num" (click)="alternarZoom()" [attr.aria-label]="'Zoom ' + zoom() + 'x. Toque para mudar'">{{ zoom() }}x</button>
          }
          @if (temLanterna()) {
            <button type="button" class="controle" [attr.aria-pressed]="lanterna()" (click)="alternarLanterna()"
              [attr.aria-label]="lanterna() ? 'Apagar a lanterna' : 'Acender a lanterna'"><ces-icone nome="lanterna" /></button>
          }
          <button type="button" class="controle" (click)="telaCheia.set(!telaCheia())"
            [attr.aria-label]="telaCheia() ? 'Sair da tela cheia' : 'Abrir a câmera em tela cheia'"><ces-icone [nome]="telaCheia() ? 'recolher' : 'expandir'" /></button>
        </div>
      </div>
    </div>
    <p class="ces-sr-only" aria-live="polite">{{ retorno()?.texto ?? '' }}</p>
  `,
  styles: [`
    :host { display: block; }
    .leitor { position: relative; width: 100%; aspect-ratio: 4 / 3; max-height: 56dvh; overflow: hidden; border-radius: var(--ces-r-grupo);
      background: #000; isolation: isolate; touch-action: manipulation; user-select: none; -webkit-user-select: none; }
    /* Celular em pé: câmera alta, ocupando a maior parte da tela. */
    @media (max-width: 600px) and (orientation: portrait) { .leitor { aspect-ratio: 3 / 4; max-height: 64dvh; } }
    .leitor.cheio { position: fixed; inset: 0; z-index: 1000; width: 100vw; height: 100dvh; max-height: none; aspect-ratio: auto; border-radius: 0; }
    video { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
    .pausado video { filter: grayscale(.6) brightness(.5); }

    /* Janela da mira: só esta faixa é lida. O resto da imagem fica escurecido. */
    .janela { position: absolute; left: 7%; right: 7%; top: 50%; height: clamp(96px, 26%, 220px); transform: translateY(-50%);
      border-radius: 14px; border: 3px solid rgba(255, 255, 255, .9); box-shadow: 0 0 0 100vmax rgba(0, 0, 0, .5); pointer-events: none;
      transition: border-color 120ms, background-color 120ms; }
    .laser { position: absolute; left: 4%; right: 4%; top: 50%; height: 3px; margin-top: -1.5px; border-radius: 2px;
      background: #ff3b30; box-shadow: 0 0 10px 2px rgba(255, 59, 48, .75); transition: background-color 120ms, box-shadow 120ms, opacity 120ms; }
    .leitor[data-modo='gatilho']:not(.segurando) .laser { opacity: .35; box-shadow: none; }
    .leitor.segurando .laser { height: 5px; margin-top: -2.5px; }
    .pausado .laser { display: none; }
    .leitor[data-tom='ok'] .janela { border-color: #34c759; background: rgba(52, 199, 89, .18); }
    .leitor[data-tom='ok'] .laser { background: #34c759; box-shadow: 0 0 12px 3px rgba(52, 199, 89, .8); }
    .leitor[data-tom='atencao'] .janela { border-color: #ffb020; background: rgba(255, 176, 32, .16); }
    .leitor[data-tom='erro'] .janela { border-color: #ff5a4f; background: rgba(255, 90, 79, .16); }

    .topo { position: absolute; left: 12px; right: 12px; top: calc(12px + env(safe-area-inset-top)); display: flex; justify-content: center; pointer-events: none; }
    .retorno { max-width: 100%; padding: 10px 18px; border-radius: 16px; background: #e7edf5; color: #06121f; text-align: center;
      font: 800 1.125rem/1.25 var(--ces-fonte); overflow-wrap: anywhere; animation: surgir 140ms var(--ces-curva); }
    .retorno[data-tom='ok'] { background: #34c759; }
    .retorno[data-tom='atencao'] { background: #ffb020; }
    .retorno[data-tom='erro'] { background: #ff7a6e; }
    .instrucao { padding: 8px 14px; border-radius: var(--ces-r-pilula); background: rgba(0, 0, 0, .55); color: #fff; font: 600 .9375rem/1.25 var(--ces-fonte); text-align: center; }
    @keyframes surgir { from { opacity: 0; transform: translateY(-6px); } }

    .aviso { position: absolute; left: 12px; right: 12px; top: 50%; transform: translateY(-50%); display: flex; flex-wrap: wrap; align-items: center;
      justify-content: center; gap: 8px; padding: 12px 14px; border-radius: 14px; background: rgba(11, 16, 23, .88); color: #fff;
      font: 600 .9375rem/1.3 var(--ces-fonte); text-align: center; }
    .aviso.erro { background: rgba(46, 20, 20, .94); }
    .aviso__texto { flex: 1 1 200px; }
    .tentar { min-height: 48px; padding: 0 18px; border: 0; border-radius: var(--ces-r-pilula); background: #fff; color: #06121f; font: 700 .9375rem/1 var(--ces-fonte); cursor: pointer; }

    .base { position: absolute; left: 10px; right: 10px; bottom: calc(10px + env(safe-area-inset-bottom)); display: grid; grid-template-columns: 1fr auto 1fr;
      align-items: end; gap: 8px; }
    .contador { justify-self: start; min-width: 52px; padding: 8px 14px; border-radius: var(--ces-r-pilula); background: rgba(0, 0, 0, .65); color: #fff;
      font: 800 1.375rem/1.1 var(--ces-fonte); text-align: center; }
    .controles { justify-self: end; display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
    .controle { min-width: 48px; height: 48px; padding: 0 10px; border: 0; border-radius: 24px; display: grid; place-items: center;
      background: rgba(0, 0, 0, .6); color: #fff; font: 700 .875rem/1 var(--ces-fonte); cursor: pointer; }
    .controle[aria-pressed='true'] { background: #ffb020; color: #06121f; }
    .gatilho { width: 84px; height: 84px; border-radius: 50%; border: 4px solid #fff; background: #ff3b30; color: #fff; display: grid; place-items: center;
      cursor: pointer; box-shadow: 0 4px 16px rgba(0, 0, 0, .45); transition: transform 80ms, background-color 80ms; -webkit-tap-highlight-color: transparent; }
    .gatilho[aria-pressed='true'] { transform: scale(.92); background: #c4271f; }
    .concluir { min-height: 52px; padding: 0 24px; border: 0; border-radius: var(--ces-r-pilula); background: #fff; color: #06121f; font: 800 1rem/1 var(--ces-fonte); cursor: pointer; }
    .giro { width: 16px; height: 16px; border-radius: 50%; border: 2px solid currentColor; border-right-color: transparent; animation: giro .8s linear infinite; }
    @keyframes giro { to { transform: rotate(360deg); } }
    @media (prefers-reduced-motion: reduce) { .retorno, .giro { animation: none; } .janela, .laser, .gatilho { transition: none; } }
  `],
})
export class LeitorCodigoComponent implements OnDestroy {
  /** Liga a câmera enquanto verdadeiro. */
  readonly ativo = input(true);
  /** Mantém a câmera aberta sem ler (ex.: quantidade completa). */
  readonly pausado = input(false);
  readonly motivoPausa = input('Leitura pausada');
  /** Resultado do último código, definido por quem usa. */
  readonly retorno = input<RetornoLeitura | null>(null);
  /** Contagem grande sobre o vídeo (ex.: "12 de 50"). */
  readonly contador = input<string | null>(null);
  readonly lido = output<string>();

  readonly estado = signal<'parado' | 'iniciando' | 'lendo' | 'erro'>('parado');
  readonly erro = signal('');
  readonly podeTentarDeNovo = signal(true);
  readonly temLanterna = signal(false);
  readonly lanterna = signal(false);
  readonly temZoom = signal(false);
  readonly zoom = signal(1);
  readonly telaCheia = signal(false);
  readonly modo = signal<ModoLeitor>(lerModo());
  readonly segurando = signal(false);
  readonly retornoVisivel = signal<RetornoLeitura | null>(null);

  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private readonly janela = viewChild.required<ElementRef<HTMLElement>>('janela');
  private readonly estabilizador = new Estabilizador();
  private readonly recorte = typeof document !== 'undefined' ? document.createElement('canvas') : null;
  private contexto: CanvasRenderingContext2D | null = null;
  private stream: MediaStream | null = null;
  private motores: Motor[] = [];
  private vez = 0;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private timerRetorno: ReturnType<typeof setTimeout> | null = null;
  private zoomMaximo = 1;
  private audio: AudioContext | null = null;
  private vivo = true;

  /** O Android derruba a câmera com o app em segundo plano: reabre ao voltar. */
  private readonly aoVoltar = () => {
    if (document.visibilityState !== 'visible' || !this.ativo() || this.estado() === 'iniciando') return;
    const trilha = this.stream?.getVideoTracks()[0];
    if (!trilha || trilha.readyState === 'ended') this.reiniciar();
  };
  /** Esc sai da tela cheia antes de fechar a folha. */
  private readonly aoTeclar = (e: KeyboardEvent) => {
    if (e.key === 'Escape' && this.telaCheia()) { e.stopImmediatePropagation(); e.preventDefault(); this.telaCheia.set(false); }
  };

  constructor() {
    effect(() => {
      const ativo = this.ativo();
      untracked(() => {
        if (ativo) { void this.iniciar(); return; }
        this.parar();
        this.telaCheia.set(false);
      });
    });
    effect(() => {
      const r = this.retorno();
      untracked(() => this.mostrarRetorno(r));
    });
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', this.aoVoltar);
      document.addEventListener('keydown', this.aoTeclar, true);
    }
  }

  ngOnDestroy(): void {
    this.vivo = false;
    document.removeEventListener('visibilitychange', this.aoVoltar);
    document.removeEventListener('keydown', this.aoTeclar, true);
    this.parar();
    if (this.timerRetorno) clearTimeout(this.timerRetorno);
    void this.audio?.close().catch(() => undefined);
  }

  tentarDeNovo(): void {
    this.estado.set('parado');
    void this.iniciar();
  }

  alternarModo(): void {
    const novo: ModoLeitor = this.modo() === 'auto' ? 'gatilho' : 'auto';
    this.modo.set(novo);
    this.segurando.set(false);
    this.estabilizador.reiniciar();
    try { localStorage.setItem(CHAVE_MODO, novo); } catch { /* sem armazenamento: vale só nesta tela */ }
  }

  apertar(e: Event): void {
    e.preventDefault();
    this.desbloquearAudio();
    if (this.segurando()) return;
    this.estabilizador.reiniciar();
    this.segurando.set(true);
  }

  soltar(): void {
    this.segurando.set(false);
  }

  private reiniciar(): void {
    this.parar();
    void this.iniciar();
  }

  private async iniciar(): Promise<void> {
    if (this.stream || this.estado() === 'iniciando') return;
    if (!cameraDisponivel()) {
      this.falhar('Este navegador não dá acesso à câmera. Use "Digitar" ou um leitor conectado.', false);
      return;
    }
    this.estado.set('iniciando');
    try {
      const [motores, stream] = await Promise.all([
        this.motores.length ? Promise.resolve(this.motores) : criarMotores(),
        navigator.mediaDevices.getUserMedia({
          audio: false,
          video: { facingMode: { ideal: 'environment' }, width: { ideal: 1920 }, height: { ideal: 1080 } },
        }),
      ]);
      if (!this.vivo || !this.ativo()) { stream.getTracks().forEach(t => t.stop()); this.estado.set('parado'); return; }
      if (!motores.length) {
        stream.getTracks().forEach(t => t.stop());
        this.falhar('Não foi possível carregar o leitor de códigos. Confira a internet e toque em "Tentar de novo".');
        return;
      }
      this.motores = motores;
      this.stream = stream;
      const v = this.video().nativeElement;
      // Mudo e inline como propriedade: sem isso o navegador só toca o vídeo logo após um toque.
      v.muted = true;
      v.playsInline = true;
      v.srcObject = stream;
      await v.play().catch(() => undefined);
      const trilha = stream.getVideoTracks()[0];
      trilha?.addEventListener('ended', () => { if (this.vivo && this.ativo() && this.stream === stream) this.reiniciar(); });
      await this.ajustarCamera(trilha);
      this.estabilizador.reiniciar();
      this.estado.set('lendo');
      this.agendar();
    } catch (e) {
      const nome = (e as DOMException)?.name;
      this.falhar(nome === 'NotAllowedError' ? 'Permita o uso da câmera nas configurações do navegador para ler códigos.'
        : nome === 'NotFoundError' || nome === 'OverconstrainedError' ? 'Nenhuma câmera encontrada neste aparelho.'
        : nome === 'NotReadableError' ? 'A câmera está em uso por outro app. Feche-o e tente de novo.'
        : 'Não foi possível abrir a câmera.', nome !== 'NotFoundError' && nome !== 'OverconstrainedError');
    }
  }

  /** Foco contínuo (etiqueta perto da lente), lanterna e zoom quando o aparelho oferece. */
  private async ajustarCamera(trilha: MediaStreamTrack | undefined): Promise<void> {
    const caps = (trilha?.getCapabilities?.() ?? {}) as { torch?: boolean; zoom?: { max: number }; focusMode?: string[] };
    this.temLanterna.set(!!caps.torch);
    this.zoomMaximo = caps.zoom ? Math.min(caps.zoom.max, ZOOMS[ZOOMS.length - 1]) : 1;
    this.temZoom.set(this.zoomMaximo >= 2);
    this.zoom.set(1);
    if (trilha && caps.focusMode?.includes('continuous')) {
      await trilha.applyConstraints({ advanced: [{ focusMode: 'continuous' } as MediaTrackConstraintSet] }).catch(() => undefined);
    }
  }

  private falhar(msg: string, podeTentar = true): void {
    this.parar();
    this.erro.set(msg);
    this.podeTentarDeNovo.set(podeTentar);
    this.estado.set('erro');
  }

  private parar(): void {
    if (this.timer) clearTimeout(this.timer);
    this.timer = null;
    this.stream?.getTracks().forEach(t => t.stop());
    this.stream = null;
    this.lanterna.set(false);
    this.segurando.set(false);
    if (this.estado() !== 'erro') this.estado.set('parado');
  }

  private agendar(): void {
    this.timer = setTimeout(() => void this.ler(), INTERVALO_LEITURA_MS);
  }

  /** Um quadro: recorta a mira, passa por um dos motores e decide se é hora de bipar. */
  private async ler(): Promise<void> {
    if (!this.stream) return;
    const v = this.video().nativeElement;
    // O navegador pode recusar ou interromper o play (troca de aba, economia de energia): retoma.
    if (v.paused && !document.hidden) void v.play().catch(() => undefined);
    const esperando = this.modo() === 'gatilho' && !this.segurando();
    if (!this.pausado() && !esperando && v.readyState >= 2 && !document.hidden && this.recortar(v)) {
      const motor = this.motores[this.vez++ % this.motores.length];
      try {
        const codigos = await motor.detect(this.recorte!);
        const codigo = this.estabilizador.considerar(sobreALinha(codigos, this.recorte!.height), performance.now(), this.modo() === 'gatilho');
        if (codigo && this.stream) {
          if (this.modo() === 'gatilho') this.segurando.set(false); // um aperto, um bip
          this.lido.emit(codigo);
        }
      } catch {
        /* quadro inválido (troca de câmera, aba em segundo plano): tenta no próximo */
      }
    }
    if (this.stream) this.agendar();
  }

  /** Copia para o canvas só a faixa da mira, na resolução da câmera. */
  private recortar(v: HTMLVideoElement): boolean {
    if (!this.recorte || !v.videoWidth) return false;
    const rv = v.getBoundingClientRect();
    const rj = this.janela().nativeElement.getBoundingClientRect();
    if (!rv.width || !rj.width) return false;
    const area = janelaNoQuadro(
      { x: rj.left - rv.left, y: rj.top - rv.top, width: rj.width, height: rj.height },
      { largura: rv.width, altura: rv.height },
      { largura: v.videoWidth, altura: v.videoHeight },
    );
    if (area.width < 16 || area.height < 16) return false;
    const escala = Math.min(1, LARGURA_MAXIMA_RECORTE / area.width);
    const w = Math.round(area.width * escala);
    const h = Math.round(area.height * escala);
    if (this.recorte.width !== w || this.recorte.height !== h) { this.recorte.width = w; this.recorte.height = h; this.contexto = null; }
    this.contexto ??= this.recorte.getContext('2d', { willReadFrequently: true });
    if (!this.contexto) return false;
    this.contexto.drawImage(v, area.x, area.y, area.width, area.height, 0, 0, w, h);
    return true;
  }

  async alternarLanterna(): Promise<void> {
    const trilha = this.stream?.getVideoTracks()[0];
    if (!trilha) return;
    const ligar = !this.lanterna();
    try {
      await trilha.applyConstraints({ advanced: [{ torch: ligar } as MediaTrackConstraintSet] });
      this.lanterna.set(ligar);
    } catch {
      this.temLanterna.set(false);
    }
  }

  async alternarZoom(): Promise<void> {
    const trilha = this.stream?.getVideoTracks()[0];
    if (!trilha) return;
    const opcoes = ZOOMS.filter(z => z <= this.zoomMaximo);
    const proximo = opcoes[(opcoes.indexOf(this.zoom()) + 1) % opcoes.length] ?? 1;
    try {
      await trilha.applyConstraints({ advanced: [{ zoom: proximo } as MediaTrackConstraintSet] });
      this.zoom.set(proximo);
    } catch {
      this.temZoom.set(false);
    }
  }

  private mostrarRetorno(r: RetornoLeitura | null): void {
    if (this.timerRetorno) clearTimeout(this.timerRetorno);
    this.retornoVisivel.set(r);
    if (!r) return;
    this.timerRetorno = setTimeout(() => this.retornoVisivel.set(null), r.tom === 'ok' ? 1300 : 2400);
    if (r.tom === 'neutro') return;
    try { navigator.vibrate?.(r.tom === 'ok' ? 40 : [70, 50, 70]); } catch { /* sem vibração */ }
    this.bipe(r.tom === 'ok' ? 1480 : 380, r.tom === 'ok' ? 0.09 : 0.2);
  }

  /** O navegador só libera som depois de um toque: aproveita qualquer toque na tela. */
  private desbloquearAudio(): void {
    try {
      this.audio ??= new AudioContext();
      if (this.audio.state === 'suspended') void this.audio.resume();
    } catch { /* sem áudio */ }
  }

  private bipe(frequencia: number, duracao: number): void {
    try {
      this.desbloquearAudio();
      const audio = this.audio!;
      const osc = audio.createOscillator();
      const ganho = audio.createGain();
      osc.type = 'square';
      osc.frequency.value = frequencia;
      ganho.gain.setValueAtTime(0.12, audio.currentTime);
      ganho.gain.exponentialRampToValueAtTime(0.0001, audio.currentTime + duracao);
      osc.connect(ganho).connect(audio.destination);
      osc.start();
      osc.stop(audio.currentTime + duracao + 0.01);
    } catch {
      /* sem áudio: a vibração e o retorno visual bastam */
    }
  }
}
