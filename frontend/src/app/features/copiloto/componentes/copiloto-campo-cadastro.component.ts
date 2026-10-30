import {
  ChangeDetectionStrategy,
  Component,
  computed,
  forwardRef,
  inject,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  CesAutocompleteComponent,
  CesBooleanControlComponent,
  CesButtonComponent,
  CesDatepickerComponent,
  CesIconButtonComponent,
  CesNumberInputComponent,
  CesSelectComponent,
  CesTextInputComponent,
  CesTextareaComponent,
  LookupOption,
  LookupStatus,
} from '@ces/ui';
import { CampoCadastro, OpcaoCadastro } from '../../../core/models/assistente.models';
import { AssistenteService } from '../../../core/services/assistente.service';

/** Ajuste pedido por um campo do cartão. */
export interface AjusteCampo {
  caminho: string;
  valor?: unknown;
  remover?: boolean;
}

const TEXTOS = new Set(['TEXTO', 'EMAIL', 'CPF', 'CNPJ', 'UUID', 'HORA']);
const NUMEROS = new Set(['INTEIRO', 'DECIMAL', 'MOEDA']);

/** Data do cartão (aaaa-mm-dd) para o calendário, sem fuso. */
export function paraData(valor: unknown): Date | null {
  if (typeof valor !== 'string' || !valor) return null;
  const [ano, mes, dia] = valor.substring(0, 10).split('-').map(Number);
  if (!ano || !mes || !dia) return null;
  const data = new Date(ano, mes - 1, dia);
  if (valor.length > 10) {
    const hora = new Date(valor);
    if (!Number.isNaN(hora.getTime())) data.setHours(hora.getHours(), hora.getMinutes());
  }
  return data;
}

function doisDigitos(n: number): string {
  return String(n).padStart(2, '0');
}

/** Calendário → texto que o Copiloto entende (dd/mm/aaaa [hh:mm]). */
export function deData(data: Date | null, comHora: boolean): string | null {
  if (!data) return null;
  const dia = `${doisDigitos(data.getDate())}/${doisDigitos(data.getMonth() + 1)}/${data.getFullYear()}`;
  return comHora ? `${dia} ${doisDigitos(data.getHours())}:${doisDigitos(data.getMinutes())}` : dia;
}

/** Um campo editável do cartão de cadastro; listas e blocos se desenham com o próprio componente. */
@Component({
  selector: 'app-copiloto-campo-cadastro',
  standalone: true,
  imports: [
    FormsModule,
    CesAutocompleteComponent,
    CesBooleanControlComponent,
    CesButtonComponent,
    CesDatepickerComponent,
    CesIconButtonComponent,
    CesNumberInputComponent,
    CesSelectComponent,
    CesTextInputComponent,
    CesTextareaComponent,
    forwardRef(() => CopilotoCampoCadastroComponent),
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './copiloto-campo-cadastro.component.html',
  styleUrl: './copiloto-campo-cadastro.component.css',
  host: {
    '[class.campo--largo]': 'largo()',
  },
})
export class CopilotoCampoCadastroComponent {
  private readonly api = inject(AssistenteService);

  readonly campo = input.required<CampoCadastro>();
  readonly desabilitado = input(false);
  readonly ajustar = output<AjusteCampo>();

  protected readonly tipo = computed(() => this.campo().tipo);
  protected readonly ehTexto = computed(() => TEXTOS.has(this.tipo()));
  protected readonly ehNumero = computed(() => NUMEROS.has(this.tipo()));
  protected readonly largo = computed(() => {
    const campo = this.campo();
    return campo.tipo === 'TEXTO_LONGO' || campo.tipo === 'LISTA' || campo.tipo === 'OBJETO' || !!campo.multiplo;
  });
  protected readonly id = computed(() => 'cad-' + this.campo().caminho.replace(/[^a-zA-Z0-9]/g, '-'));
  protected readonly erro = computed(() => this.campo().problema || undefined);
  protected readonly ajuda = computed(() => {
    const campo = this.campo();
    if (campo.tipo === 'REFERENCIA' && campo.detalhe && !campo.problema) return campo.detalhe;
    return undefined;
  });

  /** Valor em edição; volta ao valor do servidor quando a prévia muda. */
  protected readonly texto = linkedSignal(() => this.textoInicial());
  protected readonly numero = linkedSignal(() => {
    const valor = this.campo().valor;
    const n = typeof valor === 'number' ? valor : Number(valor);
    return valor === null || valor === undefined || valor === '' || Number.isNaN(n) ? null : n;
  });
  protected readonly data = linkedSignal(() => paraData(this.campo().valor));
  protected readonly booleano = linkedSignal(() => this.campo().valor === true);
  protected readonly opcao = linkedSignal(() => (this.campo().valor as string | null) ?? null);
  protected readonly referencia = linkedSignal(() => (this.campo().valor as string | null) ?? null);

  protected readonly opcoesSelect = computed(() =>
    (this.campo().opcoes ?? []).map((o) => ({ label: o.rotulo, value: o.valor })),
  );

  protected readonly buscaOpcoes = signal<LookupOption<string>[]>([]);
  protected readonly buscaStatus = signal<LookupStatus>('idle');
  protected readonly opcoesReferencia = computed<LookupOption<string>[]>(() => {
    const atual = this.campo();
    // O escolhido aparece com o nome do cadastro (o detalhe fica na ajuda abaixo do campo).
    const lista = this.buscaOpcoes().filter((o) => o.value !== atual.valor);
    if (!atual.multiplo && typeof atual.valor === 'string' && atual.valor && atual.exibicao) {
      lista.unshift({ value: atual.valor, label: atual.exibicao });
    }
    return lista;
  });
  private buscaAtual = 0;

  protected readonly selecionados = computed(() => {
    const campo = this.campo();
    if (!campo.multiplo) return [];
    return (campo.valores ?? []).map((item, indice) => ({
      indice,
      valor: item.valor,
      rotulo: item.exibicao || String(item.valor ?? ''),
      problema: item.problema,
      candidatos: item.candidatos ?? [],
    }));
  });

  private textoInicial(): string {
    const campo = this.campo();
    if (campo.valor === null || campo.valor === undefined) return campo.exibicao && campo.problema ? campo.exibicao : '';
    if (campo.tipo === 'CPF' || campo.tipo === 'CNPJ') return campo.exibicao ?? String(campo.valor);
    return String(campo.valor);
  }

  protected confirmarTexto(): void {
    const atual = this.textoInicial();
    const novo = this.texto().trim();
    if (novo === atual.trim()) return;
    this.ajustar.emit({ caminho: this.campo().caminho, valor: novo || null });
  }

  protected confirmarNumero(): void {
    const valor = this.campo().valor;
    const atual = valor === null || valor === undefined || valor === '' ? null : Number(valor);
    if (this.numero() === atual) return;
    this.ajustar.emit({ caminho: this.campo().caminho, valor: this.numero() });
  }

  protected escolherData(data: Date | null): void {
    this.data.set(data);
    this.ajustar.emit({ caminho: this.campo().caminho, valor: deData(data, this.tipo() === 'DATA_HORA') });
  }

  protected escolherBooleano(valor: boolean): void {
    this.booleano.set(valor);
    this.ajustar.emit({ caminho: this.campo().caminho, valor });
  }

  protected escolherOpcao(valor: string | null): void {
    this.opcao.set(valor);
    this.ajustar.emit({ caminho: this.campo().caminho, valor });
  }

  protected escolherReferencia(valor: string | null): void {
    this.referencia.set(valor);
    if (valor === (this.campo().valor ?? null)) return;
    this.ajustar.emit({ caminho: this.campo().caminho, valor });
  }

  protected escolherCandidato(candidato: OpcaoCadastro, indice?: number): void {
    const campo = this.campo();
    if (campo.multiplo && indice !== undefined) {
      const valores = (campo.valores ?? []).map((item, i) => (i === indice ? candidato.valor : item.valor ?? item.exibicao));
      this.ajustar.emit({ caminho: campo.caminho, valor: valores });
      return;
    }
    this.ajustar.emit({ caminho: campo.caminho, valor: candidato.valor });
  }

  protected incluirNaLista(valor: string | null): void {
    if (!valor) return;
    const campo = this.campo();
    const atuais = (campo.valores ?? []).map((item) => item.valor ?? item.exibicao).filter((v) => v !== undefined);
    if (atuais.includes(valor)) return;
    this.ajustar.emit({ caminho: campo.caminho, valor: [...atuais, valor] });
  }

  protected tirarDaLista(indice: number): void {
    const campo = this.campo();
    const restantes = (campo.valores ?? []).filter((_, i) => i !== indice).map((item) => item.valor ?? item.exibicao);
    this.ajustar.emit({ caminho: campo.caminho, valor: restantes });
  }

  protected buscar(termo: string): void {
    const tipo = this.campo().referencia;
    if (!tipo) return;
    const busca = ++this.buscaAtual;
    this.buscaStatus.set('loading');
    this.api.buscarReferencia(tipo, termo).subscribe({
      next: (opcoes) => {
        if (busca !== this.buscaAtual) return;
        this.buscaOpcoes.set(opcoes.map((o) => ({ value: o.valor, label: o.detalhe ? `${o.rotulo} · ${o.detalhe}` : o.rotulo })));
        this.buscaStatus.set(opcoes.length ? 'success' : 'empty');
      },
      error: () => {
        if (busca !== this.buscaAtual) return;
        this.buscaOpcoes.set([]);
        this.buscaStatus.set('error');
      },
    });
  }

  protected incluirItem(): void {
    this.ajustar.emit({ caminho: this.campo().caminho + '.#', valor: {} });
  }

  protected removerItem(indice: number): void {
    this.ajustar.emit({ caminho: `${this.campo().caminho}.${indice}`, remover: true });
  }

  protected repassar(ajuste: AjusteCampo): void {
    this.ajustar.emit(ajuste);
  }

  protected rotuloItem(indice: number): string {
    return `${this.campo().rotulo.replace(/s$/, '')} ${indice + 1}`;
  }
}
