import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { CesAsyncStateComponent } from '../../feedback/async-state/ces-async-state.component';
import { CesDialogShellComponent } from './ces-dialog-shell.component';

/** Modal de espera para comandos cuja resposta final vem na mesma requisição. */
@Component({
  selector: 'ces-blocking-progress-dialog',
  standalone: true,
  imports: [CesAsyncStateComponent, CesDialogShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-dialog-shell
      [title]="title()"
      [description]="description()"
      size="sm"
      [visible]="renderedVisible()"
      [dismissable]="false"
    >
      <ces-async-state
        state="loading"
        [title]="currentTitle()"
        [description]="currentDescription()"
      ></ces-async-state>
      <span class="sr-only" aria-live="polite" aria-atomic="true">{{ currentDescription() }}</span>
    </ces-dialog-shell>
  `
})
export class CesBlockingProgressDialogComponent {
  readonly visible = input(false);
  readonly title = input('Processando');
  readonly description = input('');
  readonly statusTitle = input('Estamos cuidando disso');
  readonly statusDescription = input('Você verá o resultado assim que terminar.');
  readonly statusMessages = input<readonly string[]>([]);

  protected readonly renderedVisible = computed(() => this.visible());
  private readonly messageIndex = signal(0);
  protected readonly currentTitle = computed(() => this.statusTitle());
  protected readonly currentDescription = computed(() =>
    this.statusMessages()[this.messageIndex()] ?? this.statusDescription()
  );

  constructor() {
    effect((onCleanup) => {
      this.messageIndex.set(0);
      const messages = this.statusMessages();
      if (!this.visible() || messages.length < 2) return;

      let index = 0;
      const timer = setInterval(() => {
        index += 1;
        this.messageIndex.set(index);
        if (index >= messages.length - 1) clearInterval(timer);
      }, 10000);
      onCleanup(() => clearInterval(timer));
    });
  }
}
