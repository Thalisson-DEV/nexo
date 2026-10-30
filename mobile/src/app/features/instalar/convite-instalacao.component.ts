import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { PlataformaService } from '../../core/plataforma/plataforma.service';
import { IconeComponent } from '../../ui';

/**
 * Convite em tela cheia para instalar o app (decisão R2: sugerir, sem bloquear).
 * Aparece ao abrir pelo navegador; "Agora não" adia por 24 h.
 */
@Component({
  selector: 'app-convite-instalacao',
  standalone: true,
  imports: [IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (plataforma.sugerirInstalacao()) {
      <div class="tela" role="dialog" aria-modal="true" aria-labelledby="convite-titulo">
        <div class="topo">
          <img class="icone-app" src="icons/icon-192x192.png" alt="" width="84" height="84">
          <h1 id="convite-titulo">Instale o Nexo Campo</h1>
          <p>Fica na tela inicial como os outros apps.</p>
        </div>

        <ul class="motivos">
          <li><span class="ces-selo ces-selo--destaque"><ces-icone nome="aparelho" /></span>Abre em tela cheia, sem a barra do navegador.</li>
          <li><span class="ces-selo ces-selo--destaque"><ces-icone nome="sem_rede" /></span>Funciona sem internet e envia quando o sinal voltar.</li>
          <li><span class="ces-selo ces-selo--destaque"><ces-icone nome="retomar" /></span>Volta exatamente de onde você parou.</li>
        </ul>

        <div class="acoes">
          @if (plataforma.podeInstalarDireto()) {
            <button type="button" class="ces-btn ces-btn--principal ces-btn--bloco" (click)="instalar()" [disabled]="instalando()">
              <ces-icone nome="baixar" />Instalar app
            </button>
          } @else {
            <div class="passos" aria-live="polite">
              @switch (plataforma.plataforma) {
                @case ('ios') {
                  <p><b>No iPhone:</b> toque em <span class="tecla"><ces-icone nome="compartilhar_ios" [tamanho]="18" /> Compartilhar</span>
                    e depois em <span class="tecla">Adicionar à Tela de Início</span>.</p>
                }
                @default {
                  <p><b>No Chrome:</b> toque em <span class="tecla"><ces-icone nome="mais_opcoes" [tamanho]="18" /> Menu</span>
                    e depois em <span class="tecla">Instalar app</span>.</p>
                }
              }
              <p class="apoio">Já instalou? Abra pelo ícone <b>Nexo Campo</b> na tela inicial.</p>
            </div>
          }
          <button type="button" class="ces-btn ces-btn--texto ces-btn--bloco" (click)="plataforma.adiar()">Agora não</button>
        </div>
      </div>
    }
  `,
  styles: [`
    .tela { position: fixed; inset: 0; z-index: 90; background: var(--ces-fundo); overflow-y: auto; display: flex; flex-direction: column;
      gap: var(--ces-e8); padding: calc(var(--ces-safe-topo) + var(--ces-e8)) var(--ces-e6) calc(var(--ces-safe-base) + var(--ces-e5)); }
    .topo { display: grid; gap: var(--ces-e2); }
    .icone-app { width: 84px; height: 84px; border-radius: 22px; box-shadow: var(--ces-sombra-flutuante); }
    h1 { margin: var(--ces-e4) 0 0; font: var(--ces-t-titulo); letter-spacing: -.02em; text-wrap: balance; }
    .topo p { margin: 0; color: var(--ces-texto-2); }
    .motivos { list-style: none; margin: 0; padding: 0; display: grid; gap: var(--ces-e4); }
    .motivos li { display: flex; align-items: center; gap: var(--ces-e3); font: var(--ces-t-corpo); }
    .acoes { margin-top: auto; display: grid; gap: var(--ces-e2); }
    .passos { display: grid; gap: var(--ces-e3); background: var(--ces-superficie); border-radius: var(--ces-r-grupo); padding: var(--ces-e4); }
    .passos p { margin: 0; line-height: 1.7; }
    .passos .apoio { color: var(--ces-texto-2); font-size: .875rem; }
    .tecla { display: inline-flex; align-items: center; gap: 4px; padding: 1px 8px; border-radius: 8px; background: var(--ces-superficie-2);
      font-weight: 600; white-space: nowrap; vertical-align: middle; }
  `],
})
export class ConviteInstalacaoComponent {
  readonly plataforma = inject(PlataformaService);
  readonly instalando = signal(false);

  async instalar(): Promise<void> {
    this.instalando.set(true);
    try {
      const aceito = await this.plataforma.instalar();
      if (!aceito) this.plataforma.adiar();
    } finally {
      this.instalando.set(false);
    }
  }
}
