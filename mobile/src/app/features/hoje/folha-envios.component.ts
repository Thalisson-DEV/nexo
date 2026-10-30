import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { FilaEnvioService } from '../../core/sync/fila-envio.service';
import { ConnectivityService } from '../../core/sync/connectivity.service';
import { ToastService } from '../../core/ui/toast.service';
import { FolhaComponent, StatusEnvioComponent, IconeComponent, VazioComponent } from '../../ui';

/** Folha "Envios": cada item da fila com seu estado e o que fazer. */
@Component({
  selector: 'app-folha-envios',
  standalone: true,
  imports: [FolhaComponent, StatusEnvioComponent, IconeComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-folha [aberta]="aberta()" (fechar)="fechar.emit()" titulo="Envios"
      [descricao]="rede.isOnline() ? 'Saem pela ordem em que foram lançados.' : 'Sem internet. Vão sozinhos quando o sinal voltar.'">
      @if (fila.itens().length) {
        <ul class="ces-grupo lista">
          @for (item of fila.itens(); track item.id) {
            <li>
              <div class="ces-linha">
                @if (item.estado === 'conflict' || item.estado === 'error') {
                  <span class="ces-selo" [class.ces-selo--erro]="item.estado === 'conflict'" [class.ces-selo--atencao]="item.estado === 'error'">
                    <ces-icone nome="alerta" />
                  </span>
                }
                <div class="ces-linha__texto">
                  <span class="ces-linha__titulo ces-num">{{ item.titulo }}</span>
                  <span class="ces-linha__apoio ces-num">{{ item.motivo ?? item.detalhe }}</span>
                </div>
                <ces-status-envio [estado]="item.estado" />
              </div>
            </li>
          }
        </ul>
      } @else {
        <ces-vazio icone="check" titulo="Tudo enviado" texto="Nada esperando para sair deste aparelho." />
      }
      <div rodape class="rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="fechar.emit()">Fechar</button>
        <button type="button" class="ces-btn ces-btn--principal" (click)="enviar()"
          [disabled]="!rede.isOnline() || fila.enviandoAgora() || !fila.itens().length">
          <ces-icone nome="atualizar" [tamanho]="18" />{{ fila.enviandoAgora() ? 'Enviando…' : 'Enviar agora' }}
        </button>
      </div>
    </ces-folha>
  `,
  styles: [`
    .lista { margin-bottom: var(--ces-e2); background: var(--ces-fundo); }
    .rodape { display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) 0; }
    .rodape .ces-btn { flex: 1; }
  `],
})
export class FolhaEnviosComponent {
  readonly fila = inject(FilaEnvioService);
  readonly rede = inject(ConnectivityService);
  private readonly toast = inject(ToastService);
  readonly aberta = input(false);
  readonly fechar = output<void>();

  async enviar(): Promise<void> {
    const r = await this.fila.enviarAgora();
    if (r === 'enviado' && this.fila.total() === 0) this.toast.success('Tudo enviado');
    if (r === 'falhou') this.toast.warning('Alguns itens não saíram. Tentamos de novo sozinhos.');
  }
}
