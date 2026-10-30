import { Injectable, computed, inject, signal } from '@angular/core';
import { TelemetriaService } from '../sessao/telemetria.service';

/** Evento do Chrome que permite abrir a instalação a partir de um botão nosso. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

export type PlataformaInstalacao = 'android' | 'ios' | 'outro';

const CHAVE_ADIADO = 'ces_instalar_adiado_em';
/** "Agora não" vale por um dia; depois o convite volta (decisão R2: só sugerir). */
const ADIAMENTO_MS = 24 * 60 * 60 * 1000;

/**
 * Tudo que depende do aparelho e não de regra de negócio: rodar instalado
 * (standalone) ou no navegador, convite de instalação e armazenamento persistente.
 */
@Injectable({ providedIn: 'root' })
export class PlataformaService {
  private readonly telemetria = inject(TelemetriaService);
  private evento: BeforeInstallPromptEvent | null = null;

  readonly instalado = signal(detectarStandalone());
  readonly podeInstalarDireto = signal(false);
  readonly plataforma: PlataformaInstalacao = detectarPlataforma();
  private readonly adiadoEm = signal<number>(lerNumero(CHAVE_ADIADO));

  /** Mostrar o convite: está no navegador e não adiou nas últimas 24 h. */
  readonly sugerirInstalacao = computed(() => !this.instalado() && Date.now() - this.adiadoEm() > ADIAMENTO_MS);

  iniciar(): void {
    if (typeof window === 'undefined') return;
    window.addEventListener('beforeinstallprompt', (e) => {
      e.preventDefault(); // o convite é nosso, no momento certo
      this.evento = e as BeforeInstallPromptEvent;
      this.podeInstalarDireto.set(true);
    });
    window.addEventListener('appinstalled', () => {
      this.instalado.set(true);
      this.podeInstalarDireto.set(false);
      void this.telemetria.registrar('instalacao', { resultado: 'instalado' });
    });
    window.matchMedia?.('(display-mode: standalone)').addEventListener?.('change', ev => this.instalado.set(ev.matches || detectarStandalone()));
    void this.pedirArmazenamentoPersistente();
  }

  /** Abre a instalação do Chrome. Devolve `false` quando não há como (iPhone, já instalado, navegador sem suporte). */
  async instalar(): Promise<boolean> {
    if (!this.evento) return false;
    const ev = this.evento;
    this.evento = null;
    this.podeInstalarDireto.set(false);
    await ev.prompt();
    const { outcome } = await ev.userChoice;
    void this.telemetria.registrar('instalacao', { resultado: outcome });
    return outcome === 'accepted';
  }

  adiar(): void {
    const agora = Date.now();
    this.adiadoEm.set(agora);
    try { localStorage.setItem(CHAVE_ADIADO, String(agora)); } catch { /* modo privado */ }
    void this.telemetria.registrar('instalacao', { resultado: 'adiado' });
  }

  /**
   * Pede ao navegador para não apagar os dados do app quando faltar espaço.
   * Chrome concede sozinho para app instalado/usado com frequência; a negativa
   * não muda nada no funcionamento, só fica registrada.
   */
  private async pedirArmazenamentoPersistente(): Promise<void> {
    try {
      const storage = navigator.storage;
      if (!storage?.persist) return;
      const ja = await storage.persisted();
      const concedido = ja || await storage.persist();
      void this.telemetria.registrar('armazenamento.persistencia', { concedido, instalado: this.instalado() });
    } catch {
      /* navegador sem suporte */
    }
  }
}

function detectarStandalone(): boolean {
  if (typeof window === 'undefined') return true;
  return window.matchMedia?.('(display-mode: standalone)').matches
    || window.matchMedia?.('(display-mode: fullscreen)').matches
    || (navigator as Navigator & { standalone?: boolean }).standalone === true;
}

function detectarPlataforma(): PlataformaInstalacao {
  if (typeof navigator === 'undefined') return 'outro';
  // Só para escolher QUAL instrução mostrar no convite — nenhum comportamento depende disto.
  const ua = navigator.userAgent;
  if (/iPhone|iPad|iPod/.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1)) return 'ios';
  if (/Android/.test(ua)) return 'android';
  return 'outro';
}

function lerNumero(chave: string): number {
  try { return Number(localStorage.getItem(chave)) || 0; } catch { return 0; }
}
