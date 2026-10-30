import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { CesActionSize, CesActionVariant, CesButtonComponent } from '@ces/ui';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-analisar-com-copiloto',
  standalone: true,
  imports: [CesButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (permitido()) {
      <ces-button [label]="label()" icon="pi pi-sparkles" [variant]="variant()"
        [size]="size()" (pressed)="abrir()" />
    }
  `,
})
export class AnalisarComCopilotoComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly origem = input.required<string>();
  readonly tipo = input<string>();
  readonly entidade = input<string | number>();
  readonly rotulo = input<string>();
  readonly pergunta = input<string>();
  readonly filtros = input<Record<string, string | number | boolean | null | undefined>>({});
  readonly label = input('Analisar com Copiloto');
  readonly variant = input<CesActionVariant>('outline');
  readonly size = input<CesActionSize>('md');

  protected readonly permitido = computed(() =>
    this.auth.currentUser()?.permissoes?.includes('assistente:chat:use') ?? false,
  );

  protected abrir(): void {
    const queryParams: Record<string, string> = { origem: this.origem(), rota: this.router.url };
    const opcionais = {
      tipo: this.tipo(), entidade: this.entidade(), rotulo: this.rotulo(), pergunta: this.pergunta(),
    };
    for (const [chave, valor] of Object.entries(opcionais)) {
      if (valor !== undefined && valor !== null && valor !== '') queryParams[chave] = String(valor);
    }
    for (const [chave, valor] of Object.entries(this.filtros())) {
      if (valor !== undefined && valor !== null && valor !== '') queryParams[`f_${chave}`] = String(valor);
    }
    void this.router.navigate(['/copiloto'], { queryParams });
  }
}
