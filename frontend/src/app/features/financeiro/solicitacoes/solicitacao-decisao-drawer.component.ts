import { CurrencyPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  input,
  model,
  output,
  signal,
  untracked
} from '@angular/core';
import {
  CesButtonComponent,
  CesCalloutComponent,
  CesFormDrawerComponent,
  CesFormSectionComponent,
  CesNumberInputComponent,
  CesTextareaComponent
} from '@ces/ui';
import { SolicitacaoFinanceira } from '../../../core/models/financeiro.models';

export type AcaoDecisao = 'APROVAR' | 'REJEITAR' | 'CANCELAR' | 'DESFAZER';

/** O que o drawer devolve ao confirmar. O valor só vem na aprovação. */
export interface DecisaoConfirmada {
  acao: AcaoDecisao;
  texto: string;
  valorAprovado: number | null;
}

interface ConfiguracaoAcao {
  titulo: string;
  descricao: string;
  rotuloTexto: string;
  dicaTexto: string;
  placeholder: string;
  textoObrigatorio: boolean;
  confirmar: string;
  icone: string;
  variante: 'success' | 'destructive' | 'primary';
}

const MINIMO_MOTIVO = 5;

const CONFIGURACAO: Record<AcaoDecisao, ConfiguracaoAcao> = {
  APROVAR: {
    titulo: 'Aprovar solicitação',
    descricao: 'Confirme quanto fica autorizado. O valor pode ser menor que o solicitado, nunca maior.',
    rotuloTexto: 'Observação',
    dicaTexto: 'Opcional. Aparece para quem pediu e fica no histórico.',
    placeholder: 'Ex.: aprovado com o desconto negociado.',
    textoObrigatorio: false,
    confirmar: 'Aprovar',
    icone: 'pi pi-check',
    variante: 'success'
  },
  REJEITAR: {
    titulo: 'Rejeitar solicitação',
    descricao: 'Quem pediu recebe o motivo. Escreva o que precisa mudar para uma nova tentativa.',
    rotuloTexto: 'Motivo da rejeição',
    dicaTexto: 'Fica registrado com seu usuário e a data.',
    placeholder: 'Ex.: fora do orçamento do mês; falta orçamento de outro fornecedor.',
    textoObrigatorio: true,
    confirmar: 'Rejeitar',
    icone: 'pi pi-times',
    variante: 'destructive'
  },
  CANCELAR: {
    titulo: 'Cancelar solicitação',
    descricao: 'A solicitação sai da fila e não pode ser reaberta. Use quando o pedido não for mais necessário.',
    rotuloTexto: 'Motivo do cancelamento',
    dicaTexto: 'Fica registrado com seu usuário e a data.',
    placeholder: 'Ex.: compra feita por outro pedido.',
    textoObrigatorio: true,
    confirmar: 'Cancelar solicitação',
    icone: 'pi pi-ban',
    variante: 'destructive'
  },
  DESFAZER: {
    titulo: 'Desfazer decisão',
    descricao: 'A solicitação volta a aguardar aprovação e pode ser decidida de novo.',
    rotuloTexto: 'Motivo',
    dicaTexto: 'Quem pediu e quem tinha decidido recebem este motivo.',
    placeholder: 'Ex.: valor aprovado com erro de digitação; fornecedor trocou a proposta.',
    textoObrigatorio: true,
    confirmar: 'Desfazer decisão',
    icone: 'pi pi-undo',
    variante: 'primary'
  }
};

/**
 * Uma ação de decisão por vez, com o que ela pede: valor na aprovação, motivo nas
 * demais. Concentrar as quatro aqui mantém o mesmo texto de ajuda, a mesma
 * validação e o mesmo rótulo de confirmação em todas.
 */
@Component({
  selector: 'app-solicitacao-decisao-drawer',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CurrencyPipe,
    CesButtonComponent,
    CesCalloutComponent,
    CesFormDrawerComponent,
    CesFormSectionComponent,
    CesNumberInputComponent,
    CesTextareaComponent
  ],
  template: `
    @if (config(); as c) {
      <ces-form-drawer [(visible)]="visivel" [title]="c.titulo" [description]="c.descricao"
        [dirty]="!!texto().trim()" [loading]="processando()" size="sm">
        @if (solicitacao(); as s) {
          <ces-form-section title="Solicitação" icon="pi pi-file">
            <dl class="ces-sheet">
              <div><dt>Número</dt><dd>nº {{ s.numero }}</dd></div>
              <div><dt>Valor solicitado</dt><dd>{{ s.valor | currency: 'BRL' }}</dd></div>
              @if (s.fornecedor) {
                <div><dt>Fornecedor</dt><dd>{{ s.fornecedor.nome }}</dd></div>
              }
              @if (acao() === 'DESFAZER') {
                <div>
                  <dt>Decisão atual</dt>
                  <dd>{{ s.status === 'APROVADO' ? 'Aprovada' : 'Rejeitada' }}@if (s.desfecho.valorAprovado != null) { · {{ s.desfecho.valorAprovado | currency: 'BRL' }} }</dd>
                </div>
              }
            </dl>
          </ces-form-section>
        }

        <ces-form-section [title]="acao() === 'APROVAR' ? 'Autorização' : 'Justificativa'"
          [icon]="acao() === 'APROVAR' ? 'pi pi-check-square' : 'pi pi-comment'">
          <div class="ces-form-stack">
            @if (acao() === 'APROVAR') {
              <ces-number-input label="Valor autorizado" mode="currency" [required]="true"
                [min]="0.01" [max]="solicitacao()?.valor" [minFractionDigits]="2" [maxFractionDigits]="2"
                [(value)]="valor" [error]="erroValor()"
                [description]="descricaoValor()"></ces-number-input>
              @if (solicitacao()?.documentos?.length) {
                <ces-callout tone="info" icon="pi pi-file-check" [dense]="true">
                  Os documentos anexados ainda em conferência passam a constar como conferidos.
                </ces-callout>
              }
            }

            @if (acao() === 'DESFAZER' && solicitacao()?.status === 'APROVADO') {
              <ces-callout tone="warning" icon="pi pi-exclamation-triangle" [dense]="true">
                Se a compra ou o pagamento já foi feito com base nesta aprovação, registre isso no motivo.
              </ces-callout>
            }

            <ces-textarea [label]="c.rotuloTexto" [required]="c.textoObrigatorio" [(value)]="texto" [rows]="4"
              [maxlength]="1000" [placeholder]="c.placeholder" [description]="c.dicaTexto"
              [error]="erroTexto()"></ces-textarea>
          </div>
        </ces-form-section>

        <div drawer-footer class="ces-actions-row">
          <ces-button variant="ghost" label="Voltar" (pressed)="visivel.set(false)" [disabled]="processando()"></ces-button>
          <ces-button [variant]="c.variante" [icon]="c.icone" [label]="c.confirmar" [loading]="processando()"
            (pressed)="confirmar()"></ces-button>
        </div>
      </ces-form-drawer>
    }
  `
})
export class SolicitacaoDecisaoDrawerComponent {
  readonly visivel = model(false);
  readonly acao = input<AcaoDecisao | null>(null);
  readonly solicitacao = input<SolicitacaoFinanceira | null>(null);
  readonly processando = input(false);
  readonly confirmado = output<DecisaoConfirmada>();

  protected readonly texto = signal('');
  protected readonly valor = signal<number | null>(null);
  protected readonly erroTexto = signal<string | undefined>(undefined);
  protected readonly erroValor = signal<string | undefined>(undefined);

  protected readonly config = computed(() => {
    const acao = this.acao();
    return acao ? CONFIGURACAO[acao] : null;
  });

  /** Mostra a diferença enquanto a pessoa digita: é o número que ela vai comunicar. */
  protected readonly descricaoValor = computed(() => {
    const solicitado = this.solicitacao()?.valor;
    const valor = this.valor();
    if (solicitado == null || valor == null) return undefined;
    const diferenca = solicitado - valor;
    if (diferenca > 0.005) {
      return `${diferenca.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })} abaixo do solicitado.`;
    }
    return 'Igual ao valor solicitado.';
  });

  constructor() {
    effect(() => {
      if (!this.visivel()) return;
      untracked(() => {
        this.texto.set('');
        this.valor.set(this.solicitacao()?.valor ?? null);
        this.erroTexto.set(undefined);
        this.erroValor.set(undefined);
      });
    });
  }

  protected confirmar(): void {
    const acao = this.acao();
    const config = this.config();
    if (!acao || !config) return;

    const texto = this.texto().trim();
    let valido = true;

    if (config.textoObrigatorio && texto.length < MINIMO_MOTIVO) {
      this.erroTexto.set(texto ? `Escreva ao menos ${MINIMO_MOTIVO} caracteres.` : 'Informe o motivo.');
      valido = false;
    } else {
      this.erroTexto.set(undefined);
    }

    if (acao === 'APROVAR') {
      const valor = this.valor();
      const solicitado = this.solicitacao()?.valor ?? 0;
      if (valor == null || valor <= 0) {
        this.erroValor.set('Informe o valor autorizado.');
        valido = false;
      } else if (valor - solicitado > 0.005) {
        this.erroValor.set('O valor autorizado não pode passar do solicitado.');
        valido = false;
      } else {
        this.erroValor.set(undefined);
      }
    }

    if (!valido) return;
    this.confirmado.emit({ acao, texto, valorAprovado: acao === 'APROVAR' ? this.valor() : null });
  }
}
