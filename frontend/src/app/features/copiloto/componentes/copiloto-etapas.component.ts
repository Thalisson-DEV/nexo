import { ChangeDetectionStrategy, Component, computed, input, output, signal } from '@angular/core';
import { CesStatusComponent } from '@ces/ui';
import { ArquivoAssistente } from '../../../core/models/assistente.models';
import { EtapaView } from '../copiloto-pacotes';
import { TIPO_ETAPA, formatarCelula, formatarDuracao, rotuloColuna } from '../copiloto-formatos';

/**
 * Linha do tempo das ferramentas de uma resposta (como a timeline do Onyx): mostra o que o agente fez —
 * consultas, SQL, Python, pesquisas, artefatos — com status, resumo e detalhes expansíveis. Nunca exibe
 * raciocínio do modelo, apenas ações objetivas e seus resultados.
 */
let sequencia = 0;

@Component({
  selector: 'app-copiloto-etapas',
  standalone: true,
  imports: [CesStatusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './copiloto-etapas.component.html',
  styleUrl: './copiloto-etapas.component.css',
})
export class CopilotoEtapasComponent {
  protected readonly idLista = `copiloto-etapas-${++sequencia}`;
  readonly etapas = input.required<readonly EtapaView[]>();
  readonly observacoes = input<readonly string[]>([]);
  readonly emAndamento = input(false);
  readonly duracaoMs = input<number | undefined>();
  readonly baixarArquivo = output<ArquivoAssistente>();

  /** null = segue o padrão (aberta durante a execução, recolhida ao terminar). */
  private readonly abertaManual = signal<boolean | null>(null);
  protected readonly detalhesAbertos = signal<ReadonlySet<string>>(new Set());

  protected readonly aberta = computed(() => this.abertaManual() ?? this.emAndamento());
  protected readonly atual = computed(() => {
    const lista = this.etapas();
    return [...lista].reverse().find((etapa) => etapa.status === 'executando') ?? null;
  });
  protected readonly falhas = computed(() => this.etapas().filter((etapa) => etapa.status === 'erro').length);
  protected readonly resumo = computed(() => {
    const total = this.etapas().length;
    if (this.emAndamento()) {
      const atual = this.atual();
      return atual ? atual.rotulo : 'Analisando os resultados';
    }
    const partes = [`${total} ${total === 1 ? 'etapa' : 'etapas'}`];
    const duracao = formatarDuracao(this.duracaoMs());
    if (duracao) partes.push(duracao);
    return partes.join(' · ');
  });

  protected readonly formatarDuracao = formatarDuracao;
  protected readonly formatarCelula = formatarCelula;
  protected readonly rotuloColuna = rotuloColuna;

  protected icone(tipo: string): string {
    return TIPO_ETAPA[tipo]?.icone ?? 'pi pi-cog';
  }

  protected categoria(tipo: string): string {
    return TIPO_ETAPA[tipo]?.rotulo ?? 'Ferramenta';
  }

  protected alternar(): void {
    this.abertaManual.set(!this.aberta());
  }

  protected detalheAberto(id: string): boolean {
    return this.detalhesAbertos().has(id);
  }

  protected alternarDetalhe(id: string): void {
    this.detalhesAbertos.update((atual) => {
      const novo = new Set(atual);
      if (novo.has(id)) novo.delete(id);
      else novo.add(id);
      return novo;
    });
  }

  protected temDetalhes(etapa: EtapaView): boolean {
    return Boolean(this.codigo(etapa) || this.consultas(etapa).length || etapa.saida.length || etapa.previa
      || etapa.fontes.length || etapa.erro || this.argumentosSimples(etapa).length);
  }

  /** SQL ou código Python enviado pela ferramenta, exibido como bloco de código. */
  protected codigo(etapa: EtapaView): { linguagem: string; texto: string } | null {
    const args = etapa.argumentos ?? {};
    if (typeof args['sql'] === 'string') return { linguagem: 'SQL', texto: args['sql'] };
    if (typeof args['code'] === 'string') return { linguagem: 'Python', texto: args['code'] };
    return null;
  }

  protected consultas(etapa: EtapaView): string[] {
    const args = etapa.argumentos ?? {};
    const valores = args['consultas'] ?? args['urls'];
    return Array.isArray(valores) ? valores.map(String) : [];
  }

  /** Demais parâmetros (fonte, filtros, período...) em formato legível. */
  protected argumentosSimples(etapa: EtapaView): { chave: string; valor: string }[] {
    const args = etapa.argumentos ?? {};
    const ignorar = new Set(['sql', 'code', 'consultas', 'urls', 'objetivo', 'titulo', 'componentes', 'secoes']);
    return Object.entries(args)
      .filter(([chave, valor]) => !ignorar.has(chave) && valor !== null && valor !== undefined && valor !== '')
      .slice(0, 12)
      .map(([chave, valor]) => ({
        chave: rotuloColuna(chave),
        valor: typeof valor === 'string' ? valor : JSON.stringify(valor),
      }))
      .map((item) => ({ ...item, valor: item.valor.length > 400 ? `${item.valor.slice(0, 400)}…` : item.valor }));
  }
}
