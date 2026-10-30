import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesCalloutComponent,
  CesNumberInputComponent,
  CesStatusComponent,
  CesTextareaComponent,
  CesTone
} from '@ces/ui';
import { AcaoDecisaoEmail, DecisaoEmail, SituacaoDecisaoEmail } from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { FinanceiroService } from '../../../core/services/financeiro.service';

const TOM: Record<SituacaoDecisaoEmail, CesTone> = {
  DISPONIVEL: 'info',
  DECIDIDA_POR_ESTE_LINK: 'success',
  JA_DECIDIDA: 'neutral',
  EXPIRADO: 'warning',
  SEM_PERMISSAO: 'danger'
};

const MINIMO_MOTIVO = 5;

/**
 * A página do link de decisão do e-mail: quem aprova confere a solicitação e decide
 * sem entrar no Nexo. O link é a credencial; esta página não dá acesso a mais nada.
 */
@Component({
  selector: 'app-decisao-email',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    DatePipe,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesCalloutComponent,
    CesNumberInputComponent,
    CesStatusComponent,
    CesTextareaComponent
  ],
  styles: `
    :host {
      display: block;
      min-height: 100vh;
      background: var(--ces-canvas);
      color: var(--ces-foreground);
      padding: 1.5rem 1rem 3rem;
    }

    .folha {
      max-width: 40rem;
      margin: 0 auto;
      display: grid;
      gap: 1rem;
    }

    .cabecalho {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding-bottom: 0.75rem;
      border-bottom: 2px solid var(--ces-brand);
    }

    .cabecalho img {
      height: 2.5rem;
    }

    .marca {
      display: grid;
      line-height: 1.2;
    }

    .marca strong {
      color: var(--ces-brand);
      letter-spacing: 0.04em;
    }

    .marca span,
    .apoio {
      font-size: var(--ces-font-caption);
      color: var(--ces-foreground-muted);
    }

    .cartao {
      display: grid;
      gap: 1rem;
      padding: 1.25rem;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-lg, 0.75rem);
      background: var(--ces-surface);
    }

    h1 {
      margin: 0;
      font-size: 1.125rem;
      font-weight: 700;
    }

    .linha-titulo {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }

    .valor {
      font-size: 1.5rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }

    dl {
      margin: 0;
      display: grid;
      border: 1px solid var(--ces-border);
      border-radius: var(--ces-radius-md, 0.5rem);
      overflow: hidden;
    }

    dl > div {
      display: grid;
      grid-template-columns: minmax(7rem, 38%) minmax(0, 1fr);
      gap: 0.75rem;
      padding: 0.55rem 0.85rem;
      background: var(--ces-surface-subtle);
    }

    dl > div + div {
      border-top: 1px solid var(--ces-border);
    }

    dt {
      font-size: var(--ces-font-caption);
      color: var(--ces-foreground-muted);
    }

    dd {
      margin: 0;
      font-weight: 600;
      overflow-wrap: anywhere;
      white-space: pre-line;
    }

    .escolha {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.5rem;
    }

    .opcao {
      padding: 0.75rem;
      border: 2px solid var(--ces-border);
      border-radius: var(--ces-radius-md, 0.5rem);
      background: var(--ces-surface);
      color: var(--ces-foreground);
      font: inherit;
      font-weight: 700;
      cursor: pointer;
    }

    .opcao[aria-pressed='true'][data-acao='APROVAR'] {
      border-color: var(--ces-success);
      background: var(--ces-success-soft);
      color: var(--ces-success-text);
    }

    .opcao[aria-pressed='true'][data-acao='REJEITAR'] {
      border-color: var(--ces-danger);
      background: var(--ces-danger-soft);
      color: var(--ces-danger-text);
    }

    .opcao:focus-visible {
      outline: none;
      box-shadow: var(--ces-focus-ring);
    }

    .rodape {
      text-align: center;
    }
  `,
  template: `
    <main class="folha">
      <header class="cabecalho">
        <img src="logo_vertical.svg" alt="" onerror="this.style.display='none'" />
        <div class="marca">
          <strong>Nexo</strong>
          <span>Decisão de compras e pagamentos</span>
        </div>
      </header>

      @if (carregando()) {
        <ces-async-state state="loading" title="Carregando a solicitação..."></ces-async-state>
      } @else if (erro()) {
        <ces-async-state state="error" title="Link indisponível" [description]="erro()!"></ces-async-state>
      } @else if (decisao(); as d) {
        <section class="cartao" aria-labelledby="titulo-solicitacao">
          <div class="linha-titulo">
            <h1 id="titulo-solicitacao">{{ d.titulo }}</h1>
            <ces-status [tone]="d.situacao === 'DISPONIVEL' ? 'warning' : 'neutral'">{{ d.situacaoSolicitacao }}</ces-status>
          </div>
          @if (d.valorSolicitado != null) {
            <div>
              <span class="apoio">Valor solicitado</span>
              <div class="valor">{{ d.valorSolicitado | currency: 'BRL' }}</div>
            </div>
          }
          <ces-callout [tone]="tom[d.situacao]" [dense]="true">{{ d.mensagem }}</ces-callout>
          <dl aria-label="Dados da solicitação">
            @for (campo of d.ficha; track campo.rotulo) {
              <div><dt>{{ campo.rotulo }}</dt><dd>{{ campo.valor }}</dd></div>
            }
          </dl>
        </section>

        @if (d.situacao === 'DISPONIVEL') {
          <section class="cartao" aria-labelledby="titulo-decisao">
            <h1 id="titulo-decisao">Sua decisão</h1>
            <div class="escolha" role="group" aria-label="Decisão">
              <button type="button" class="opcao" data-acao="APROVAR" [attr.aria-pressed]="acao() === 'APROVAR'"
                (click)="escolher('APROVAR')">Aprovar</button>
              <button type="button" class="opcao" data-acao="REJEITAR" [attr.aria-pressed]="acao() === 'REJEITAR'"
                (click)="escolher('REJEITAR')">Rejeitar</button>
            </div>

            @if (acao() === 'APROVAR') {
              <ces-number-input label="Valor autorizado" mode="currency" [required]="true"
                [min]="0.01" [max]="d.valorSolicitado ?? undefined" [minFractionDigits]="2" [maxFractionDigits]="2"
                [(value)]="valor" [error]="erroValor()"
                description="Pode ser menor que o solicitado, nunca maior."></ces-number-input>
            }
            @if (acao()) {
              <ces-textarea [label]="acao() === 'APROVAR' ? 'Observação' : 'Motivo da rejeição'"
                [required]="acao() === 'REJEITAR'" [(value)]="texto" [rows]="3" [maxlength]="1000"
                [description]="acao() === 'APROVAR' ? 'Opcional. Quem pediu recebe este texto.' : 'Quem pediu recebe este motivo.'"
                [error]="erroTexto()"></ces-textarea>
              <ces-button [variant]="acao() === 'APROVAR' ? 'success' : 'destructive'" [fluid]="true"
                [icon]="acao() === 'APROVAR' ? 'pi pi-check' : 'pi pi-times'"
                [label]="acao() === 'APROVAR' ? 'Confirmar aprovação' : 'Confirmar rejeição'"
                [loading]="enviando()" (pressed)="confirmar()"></ces-button>
              @if (d.decisor) {
                <p class="apoio rodape">A decisão será registrada em nome de {{ d.decisor }}.</p>
              }
            } @else {
              <p class="apoio">Escolha aprovar ou rejeitar para continuar.</p>
            }
          </section>
        }

        <p class="apoio rodape">
          Link pessoal, válido até {{ d.expiraEm | date: 'dd/MM/yyyy HH:mm' }} e para uma única decisão.
        </p>
      }
    </main>
  `
})
export class DecisaoEmailComponent {
  private readonly service = inject(FinanceiroService);
  private readonly route = inject(ActivatedRoute);
  private readonly token = this.route.snapshot.paramMap.get('token') ?? '';

  protected readonly tom = TOM;
  protected readonly carregando = signal(true);
  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);
  protected readonly decisao = signal<DecisaoEmail | null>(null);
  protected readonly acao = signal<AcaoDecisaoEmail | null>(this.acaoDaUrl());
  protected readonly valor = signal<number | null>(null);
  protected readonly texto = signal('');
  protected readonly erroValor = signal<string | undefined>(undefined);
  protected readonly erroTexto = signal<string | undefined>(undefined);

  protected readonly valorSolicitado = computed(() => this.decisao()?.valorSolicitado ?? null);

  constructor() {
    this.service.consultarDecisaoEmail(this.token).subscribe({
      next: (resposta) => {
        this.decisao.set(resposta);
        this.valor.set(resposta.valorSolicitado ?? null);
        this.carregando.set(false);
      },
      error: (error) => {
        this.erro.set(getErrorMessage(error));
        this.carregando.set(false);
      }
    });
  }

  protected escolher(acao: AcaoDecisaoEmail): void {
    this.acao.set(acao);
    this.erroTexto.set(undefined);
    this.erroValor.set(undefined);
  }

  protected confirmar(): void {
    const acao = this.acao();
    if (!acao || !this.valido(acao)) return;

    this.enviando.set(true);
    this.service
      .decidirPorEmail(this.token, {
        acao,
        valorAprovado: acao === 'APROVAR' ? this.valor() : null,
        texto: this.texto().trim() || null
      })
      .subscribe({
        next: (resposta) => {
          this.decisao.set(resposta);
          this.enviando.set(false);
        },
        error: (error) => {
          this.enviando.set(false);
          // A decisão pode ter deixado de caber (outro diretor decidiu): recarrega a situação.
          this.erroTexto.set(getErrorMessage(error));
          this.service.consultarDecisaoEmail(this.token).subscribe({
            next: (resposta) => this.decisao.set(resposta),
            error: () => {}
          });
        }
      });
  }

  private valido(acao: AcaoDecisaoEmail): boolean {
    const texto = this.texto().trim();
    if (acao === 'REJEITAR' && texto.length < MINIMO_MOTIVO) {
      this.erroTexto.set(texto ? `Escreva ao menos ${MINIMO_MOTIVO} caracteres.` : 'Informe o motivo da rejeição.');
      return false;
    }
    this.erroTexto.set(undefined);
    if (acao === 'APROVAR') {
      const valor = this.valor();
      const solicitado = this.valorSolicitado() ?? 0;
      if (valor == null || valor <= 0) {
        this.erroValor.set('Informe o valor autorizado.');
        return false;
      }
      if (valor - solicitado > 0.005) {
        this.erroValor.set('O valor autorizado não pode passar do solicitado.');
        return false;
      }
    }
    this.erroValor.set(undefined);
    return true;
  }

  private acaoDaUrl(): AcaoDecisaoEmail | null {
    const pedida = this.route.snapshot.queryParamMap.get('acao');
    return pedida === 'aprovar' ? 'APROVAR' : pedida === 'rejeitar' ? 'REJEITAR' : null;
  }
}
