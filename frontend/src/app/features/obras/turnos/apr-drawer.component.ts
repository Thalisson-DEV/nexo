import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { CesButtonComponent, CesCalloutComponent, CesFormDrawerComponent, NotificationService } from '@ces/ui';
import { take } from 'rxjs';
import { AprDoTurno, FotoTurno, RespostaApr, TurnoObraDetalhe } from '../../../core/models/obras-turnos.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { ObrasTurnosService } from '../../../core/services/obras-turnos.service';

const RESPOSTAS: Record<RespostaApr, string> = { SIM: 'Sim', NAO: 'Não', NAO_SE_APLICA: 'Não se aplica' };

/** A análise preliminar de riscos como a equipe preencheu. Quem preenche é a equipe, em campo; aqui só se consulta. */
@Component({
  selector: 'app-apr-drawer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, CesButtonComponent, CesCalloutComponent, CesFormDrawerComponent],
  template: `
    <ces-form-drawer [(visible)]="aberto" [title]="'APR da obra ' + (apr()?.numeroObra ?? '')" size="md">
      @if (apr(); as a) {
        @if (a.status === 'CONCLUIDA') {
          <ces-callout tone="success" icon="pi pi-check-circle" title="APR concluída" [dense]="true">
            {{ a.responsavel ?? 'Sem responsável' }}{{ a.dataConclusao ? ' · ' + (a.dataConclusao | date: 'dd/MM/yyyy HH:mm') : '' }}
          </ces-callout>
        } @else {
          <ces-callout tone="warning" icon="pi pi-pencil" title="A equipe ainda está preenchendo" [dense]="true">O serviço só pode ser apontado quando ela concluir.</ces-callout>
        }
        <dl class="apr-leitura">
          <div><dt>Atividade</dt><dd>{{ a.atividade }}</dd></div>
          @if (a.formulario; as f) {
            @if (f.local) { <div><dt>Local</dt><dd>{{ f.local }}</dd></div> }
            <div><dt>Riscos</dt><dd>{{ juntar(f.riscos, f.outroRisco) }}</dd></div>
            <div><dt>Medidas de controle</dt><dd>{{ juntar(f.medidas, f.medidasAdicionais) }}</dd></div>
            <div><dt>Participantes</dt><dd>{{ nomesDe(f.idsParticipantes) }}</dd></div>
          }
        </dl>
        @if (a.formulario?.verificacoes?.length) {
          <h3 class="apr-subtitulo">Verificações antes de começar</h3>
          <ul class="apr-respostas">
            @for (v of a.formulario!.verificacoes; track v.pergunta) {
              <li><span>{{ v.pergunta }}</span><strong>{{ v.resposta ? respostas[v.resposta] : '—' }}</strong></li>
            }
          </ul>
        }
        @if (a.anexos.length) {
          <h3 class="apr-subtitulo">Documentos</h3>
          <ul class="apr-anexos">
            @for (d of a.anexos; track d.id) {
              <li><button type="button" class="apr-link" (click)="abrirAnexo(d)"><i class="pi pi-paperclip" aria-hidden="true"></i> {{ d.nomeArquivo }}</button></li>
            }
          </ul>
        }
      }
      <div drawer-footer class="ces-actions-row">
        <ces-button variant="ghost" label="Fechar" (pressed)="aberto.set(false)"></ces-button>
      </div>
    </ces-form-drawer>
  `,
  styles: `
    .apr-leitura { display: flex; flex-direction: column; gap: 0.625rem; margin: 1rem 0; }
    .apr-leitura dt { color: var(--ces-foreground-muted); font-size: 0.8125rem; font-weight: 600; }
    .apr-leitura dd { margin: 0; overflow-wrap: anywhere; }
    .apr-subtitulo { margin: 0 0 0.5rem; font-size: 0.875rem; font-weight: 700; }
    .apr-respostas { display: flex; flex-direction: column; gap: 0.375rem; margin: 0 0 1rem; padding: 0; font-size: 0.875rem; list-style: none; }
    .apr-respostas li { display: flex; gap: 1rem; justify-content: space-between; }
    .apr-respostas strong { flex: none; }
    .apr-anexos { margin: 0; padding: 0; list-style: none; }
    .apr-link { padding: 0; color: var(--ces-brand); font-weight: 600; text-align: left; background: none; border: 0; cursor: pointer; }
    .apr-link:hover { text-decoration: underline; }
    .apr-link:focus-visible { outline: 2px solid var(--ces-focus-ring); outline-offset: 2px; }
  `
})
export class AprDrawerComponent {
  private readonly api = inject(ObrasTurnosService);
  private readonly notificacoes = inject(NotificationService);

  readonly turno = input.required<TurnoObraDetalhe>();

  protected readonly respostas = RESPOSTAS;
  protected readonly aberto = signal(false);
  private readonly idApr = signal<string | null>(null);
  protected readonly apr = computed(() => this.turno().aprs.find((a) => a.id === this.idApr()) ?? null);

  abrir(apr: AprDoTurno): void {
    this.idApr.set(apr.id);
    this.aberto.set(true);
  }

  protected abrirAnexo(a: FotoTurno): void {
    this.api.baixarAnexo(this.turno().id, a.id).pipe(take(1)).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        window.open(url, '_blank', 'noopener');
        setTimeout(() => URL.revokeObjectURL(url), 60000);
      },
      error: (e) => this.notificacoes.error(getErrorMessage(e))
    });
  }

  protected juntar(lista: string[], outro: string | null): string {
    return [...lista, ...(outro?.trim() ? [outro.trim()] : [])].join(', ') || '—';
  }

  protected nomesDe(ids: string[]): string {
    return ids.map((id) => this.turno().composicao.find((m) => m.idColaborador === id)?.nome ?? 'Colaborador fora da composição').join(', ') || '—';
  }
}
