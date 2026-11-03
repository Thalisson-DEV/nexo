import { ChangeDetectionStrategy, Component, input, model } from '@angular/core';
import { CesBooleanControlComponent, CesDatepickerComponent, CesNumberInputComponent, CesTextareaComponent } from '@ces/ui';
import { EstruturaChecklist, PerguntaChecklist } from '../../../core/models/obras-documentacao.models';
import { RespostaEmEdicao, RespostasEmEdicao, alternarOpcao, respondida, respostaVazia, temOpcoes } from './checklist.logic';

/** Preenchimento de um modelo de checklist: um bloco por grupo, e as opções à vista, para responder com um toque. */
@Component({
  selector: 'app-checklist-respostas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CesBooleanControlComponent, CesDatepickerComponent, CesNumberInputComponent, CesTextareaComponent],
  template: `
    @for (grupo of estrutura().grupos; track $index) {
      <fieldset class="cr-grupo">
        <legend>{{ grupo.titulo }}</legend>
        @for (p of grupo.perguntas; track p.id) {
          <div class="cr-pergunta" [class.cr-pergunta--pendente]="pendente(p)">
            <span class="cr-enunciado" [id]="'cr-' + p.id">{{ p.enunciado }}@if (p.obrigatoria) {<span class="cr-obrigatoria" aria-hidden="true"> *</span>}</span>
            @if (temOpcoes(p.tipo)) {
              <div class="cr-opcoes" role="group" [attr.aria-labelledby]="'cr-' + p.id">
                @for (o of p.opcoes ?? []; track o) {
                  <button type="button" class="cr-opcao" [class.cr-opcao--marcada]="r(p).selecionadas.includes(o)" [attr.aria-pressed]="r(p).selecionadas.includes(o)"
                    [disabled]="r(p).naoSeAplica" (click)="alternar(p, o)">
                    @if (r(p).selecionadas.includes(o)) { <i class="pi pi-check" aria-hidden="true"></i> }
                    {{ o }}
                  </button>
                }
              </div>
            } @else if (p.tipo === 'NUMERO') {
              <!-- Sem [required]: obrigatório, o campo vazio viraria 0 e pareceria respondido. -->
              <ces-number-input [label]="p.enunciado" [labelVisible]="false" [value]="r(p).numero" [maxFractionDigits]="4" [useGrouping]="false"
                [disabled]="r(p).naoSeAplica" (valueChange)="alterar(p, { numero: $event })"></ces-number-input>
            } @else if (p.tipo === 'DATA') {
              <ces-datepicker [label]="p.enunciado" [labelVisible]="false" [value]="r(p).data" [disabled]="r(p).naoSeAplica"
                (valueChange)="alterar(p, { data: $event })"></ces-datepicker>
            } @else {
              <ces-textarea [label]="p.enunciado" [labelVisible]="false" [rows]="2" [maxlength]="2000" [value]="r(p).texto" [disabled]="r(p).naoSeAplica"
                (valueChange)="alterar(p, { texto: $event })"></ces-textarea>
            }
            @if (p.aceitaNaoSeAplica) {
              <ces-boolean-control label="Não se aplica" [inline]="true" [value]="r(p).naoSeAplica" (valueChange)="marcarNaoSeAplica(p, $event)"></ces-boolean-control>
            }
            @if (pendente(p)) {
              <p class="cr-erro" role="alert"><i class="pi pi-exclamation-circle" aria-hidden="true"></i> Responda esta pergunta{{ p.aceitaNaoSeAplica ? ' ou marque "Não se aplica"' : '' }}.</p>
            }
          </div>
        }
      </fieldset>
    }
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 1.25rem; }
    .cr-grupo { display: flex; flex-direction: column; gap: 1rem; min-width: 0; margin: 0; padding: 0; border: 0; }
    .cr-grupo legend { width: 100%; margin-bottom: 0.75rem; padding-bottom: 0.375rem; border-bottom: 1px solid var(--ces-border); color: var(--ces-foreground); font-size: 0.875rem; font-weight: 700; }
    .cr-pergunta { display: flex; flex-direction: column; gap: 0.5rem; }
    .cr-pergunta--pendente { padding-left: 0.625rem; border-left: 3px solid var(--ces-danger); }
    .cr-enunciado { color: var(--ces-foreground); font-size: 0.875rem; font-weight: 500; overflow-wrap: anywhere; }
    .cr-obrigatoria { color: var(--ces-danger); }
    .cr-opcoes { display: flex; flex-wrap: wrap; gap: 0.5rem; }
    .cr-opcao { display: inline-flex; align-items: center; gap: 0.375rem; min-height: 2.5rem; padding: 0.375rem 0.875rem; border: 1px solid var(--ces-border); border-radius: var(--ces-radius-md); background: var(--ces-surface); color: var(--ces-foreground); font: inherit; font-size: 0.875rem; cursor: pointer; }
    .cr-opcao:hover:not(:disabled) { background: var(--ces-surface-muted); }
    .cr-opcao:focus-visible { outline: 2px solid var(--ces-brand); outline-offset: 2px; }
    .cr-opcao:disabled { opacity: 0.55; cursor: not-allowed; }
    .cr-opcao--marcada, .cr-opcao--marcada:hover:not(:disabled) { border-color: var(--ces-brand); background: var(--ces-brand-soft); color: var(--ces-foreground); font-weight: 600; }
    .cr-erro { display: flex; gap: 0.375rem; align-items: center; margin: 0; color: var(--ces-danger); font-size: 0.8125rem; }
  `
})
export class ChecklistRespostasComponent {
  readonly estrutura = input.required<EstruturaChecklist>();
  readonly respostas = model.required<RespostasEmEdicao>();
  readonly mostrarPendencias = input(false);

  protected readonly temOpcoes = temOpcoes;

  protected r(p: PerguntaChecklist): RespostaEmEdicao {
    return this.respostas()[p.id] ?? respostaVazia();
  }

  protected pendente(p: PerguntaChecklist): boolean {
    return this.mostrarPendencias() && p.obrigatoria && !respondida(p, this.respostas()[p.id]);
  }

  protected alternar(p: PerguntaChecklist, opcao: string): void {
    this.alterar(p, { selecionadas: alternarOpcao(p, this.r(p).selecionadas, opcao) });
  }

  protected marcarNaoSeAplica(p: PerguntaChecklist, marcado: boolean): void {
    this.alterar(p, marcado ? { ...respostaVazia(), naoSeAplica: true } : { naoSeAplica: false });
  }

  protected alterar(p: PerguntaChecklist, mudanca: Partial<RespostaEmEdicao>): void {
    this.respostas.update((atuais) => ({ ...atuais, [p.id]: { ...this.r(p), ...mudanca } }));
  }
}
