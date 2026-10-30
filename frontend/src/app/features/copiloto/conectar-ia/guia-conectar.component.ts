import { ChangeDetectionStrategy, Component, DestroyRef, inject, input, model, output, signal } from '@angular/core';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesDrawerComponent,
  CesFormSectionComponent,
  CesIconButtonComponent,
  CesTabItem,
  CesTabPanelDirective,
  CesTabsComponent,
  CesTextInputComponent
} from '@ces/ui';
import { EXEMPLO_CADASTRO, EXEMPLOS_PERGUNTA, PROVEDORES, ProvedorIa, copiarTexto } from './conectar-ia.util';

/**
 * Passo a passo para conectar uma IA ao Nexo: copiar o endereço, seguir os cliques no
 * assistente escolhido e testar com uma pergunta.
 */
@Component({
  selector: 'app-guia-conectar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CesButtonComponent,
    CesCalloutComponent,
    CesDrawerComponent,
    CesFormSectionComponent,
    CesIconButtonComponent,
    CesTabPanelDirective,
    CesTabsComponent,
    CesTextInputComponent
  ],
  styleUrl: './guia-conectar.component.css',
  template: `
    <ces-drawer [(visible)]="visivel" title="Conectar uma IA" size="lg" eyebrow="Passo a passo"
      description="Leva uns dois minutos. Depois você pergunta sobre o Nexo direto no seu assistente.">
      <ces-form-section title="1. Copie o endereço do Nexo" icon="pi pi-link"
        description="É por ele que o seu assistente encontra o Nexo. Você vai colar no passo 2.">
        @if (erroEndereco()) {
          <ces-callout tone="danger" [dense]="true">Não foi possível carregar o endereço. Feche e abra este passo a passo de novo.</ces-callout>
        } @else {
          <div class="guia-copiar">
            <ces-text-input label="Endereço do Nexo" [labelVisible]="false" [readonly]="true"
              [value]="endereco() || 'Carregando...'"></ces-text-input>
            <ces-button [variant]="copiado() === 'endereco' ? 'success' : 'primary'"
              [icon]="copiado() === 'endereco' ? 'pi pi-check' : 'pi pi-copy'"
              [label]="copiado() === 'endereco' ? 'Copiado' : 'Copiar endereço'"
              [disabled]="!endereco()" (pressed)="copiar('endereco', endereco())"></ces-button>
          </div>
        }
      </ces-form-section>

      <ces-form-section title="2. Siga os passos no seu assistente" icon="pi pi-list-check"
        description="Escolha qual você usa. Os nomes em destaque são os botões que aparecem na tela dele.">
        <ces-tabs [tabs]="abas" [(value)]="provedor" ariaLabel="Qual assistente você usa" [lazy]="false">
          @for (p of provedores; track p.id) {
            <ng-template [cesTabPanel]="p.id">
              <p class="guia-onde">{{ p.onde }}</p>
              <ol class="guia-passos">
                @for (passo of p.passos; track $index) {
                  <li>
                    <span class="guia-passos__numero" aria-hidden="true">{{ $index + 1 }}</span>
                    <div class="guia-passos__texto">
                      <p>@for (t of passo.trechos; track $index) {@if (t.botao) {<strong class="guia-botao">{{ t.texto }}</strong>} @else {<span>{{ t.texto }}</span>}}</p>
                      @if (passo.dica) { <p class="guia-dica"><i class="pi pi-info-circle" aria-hidden="true"></i>{{ passo.dica }}</p> }
                    </div>
                  </li>
                }
              </ol>
              @if (p.id === 'outros') {
                <div class="guia-outros">
                  <ces-button variant="outline" icon="pi pi-key" label="Gerar chave de acesso" (pressed)="gerarChave.emit()"></ces-button>
                </div>
              }
            </ng-template>
          }
        </ces-tabs>
      </ces-form-section>

      <ces-form-section title="3. Teste com uma pergunta" icon="pi pi-comment"
        description="Copie uma destas e cole numa conversa nova do seu assistente.">
        <ul class="guia-exemplos">
          @for (e of exemplos(); track e) {
            <li>
              <span>{{ e }}</span>
              <ces-icon-button size="icon-sm" [icon]="copiado() === e ? 'pi pi-check' : 'pi pi-copy'"
                [label]="copiado() === e ? 'Pergunta copiada' : 'Copiar pergunta'" [tooltip]="copiado() === e ? 'Copiada' : 'Copiar'"
                (pressed)="copiar(e, e)"></ces-icon-button>
            </li>
          }
        </ul>
      </ces-form-section>

      <ces-form-section title="O que a sua IA pode fazer" icon="pi pi-shield">
        <div class="guia-copiloto">
          <img src="icons/copiloto-1.png" alt="" width="40" height="40" />
          <p>A sua IA usa as mesmas consultas do <strong>Copiloto</strong> e age sempre como você: enxerga só o que o seu perfil já enxerga no Nexo.</p>
        </div>
        <div class="guia-limites">
          <div>
            <h3><i class="pi pi-check-circle" aria-hidden="true"></i>Pode</h3>
            <ul>
              <li>Consultar estoque, solicitações, obras e o que mais o seu perfil acessa.</li>
              <li>Resumir e comparar os dados para você.</li>
              @if (podeEscrever()) { <li>Cadastrar e alterar registros, se você permitir ao conectar.</li> }
            </ul>
          </div>
          <div>
            <h3><i class="pi pi-ban" aria-hidden="true"></i>Não pode</h3>
            <ul>
              <li>Ver o que o seu perfil não vê.</li>
              <li>Cadastrar nada sem a sua permissão.</li>
              <li>Continuar usando o Nexo depois que você desconectar.</li>
            </ul>
          </div>
        </div>
      </ces-form-section>

      <p class="ces-sr-only" aria-live="polite">{{ copiado() ? 'Copiado para a área de transferência.' : '' }}</p>

      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Fechar" (pressed)="visivel.set(false)"></ces-button>
      </div>
    </ces-drawer>
  `
})
export class GuiaConectarComponent {
  readonly visivel = model(false);
  readonly endereco = input('');
  readonly erroEndereco = input(false);
  readonly podeEscrever = input(false);
  readonly gerarChave = output<void>();

  protected readonly provedores = PROVEDORES;
  protected readonly abas: CesTabItem[] = PROVEDORES.map((p) => ({ value: p.id, label: p.nome, icon: p.icone }));
  protected readonly provedor = signal<ProvedorIa>('claude');
  protected readonly copiado = signal<string | null>(null);
  protected exemplos(): string[] {
    return this.podeEscrever() ? [...EXEMPLOS_PERGUNTA, EXEMPLO_CADASTRO] : EXEMPLOS_PERGUNTA;
  }

  private temporizador: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.temporizador));
  }

  protected async copiar(chave: string, texto: string): Promise<void> {
    if (!texto || !(await copiarTexto(texto))) return;
    this.copiado.set(chave);
    clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => this.copiado.set(null), 2500);
  }
}
