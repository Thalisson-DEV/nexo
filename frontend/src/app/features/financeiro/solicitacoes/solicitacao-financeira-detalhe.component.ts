import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import {
  CesAsyncStateComponent,
  CesIconButtonComponent,
  CesStatusComponent,
  CesTone,
  NotificationService
} from '@ces/ui';
import { AccordionModule } from 'primeng/accordion';
import { ButtonModule } from 'primeng/button';
import {
  CampoFormulario,
  EventoSolicitacao,
  FormularioSchema,
  ICONE_GRUPO,
  InspecaoContexto,
  SolicitacaoFinanceira,
  StatusSolicitacao,
  TipoSolicitacao,
  ValorSolicitacao
} from '../../../core/models/financeiro.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { NavigationHistoryService } from '../../../core/navigation/navigation-history.service';
import { AuthService } from '../../../core/services/auth.service';
import { FinanceiroService } from '../../../core/services/financeiro.service';
import { AnalisarComCopilotoComponent } from '../../../shared/components/analisar-com-copiloto/analisar-com-copiloto.component';
import { imprimirSempreNoTemaClaro } from '../../execucao/relatorios/relatorio-impressao';
import { agruparCampos, GrupoDeCampos } from '../shared/campos-financeiros';
import { AcaoDecisao, DecisaoConfirmada, SolicitacaoDecisaoDrawerComponent } from './solicitacao-decisao-drawer.component';
import { SolicitacaoDocumentosComponent } from './solicitacao-documentos.component';
import { diasAte, pontosDeAtencao } from './solicitacao-financeira-atencao';
import { SolicitacaoFinanceiraHistoricoComponent } from './solicitacao-financeira-historico.component';
import { SolicitacaoFinanceiraInspecaoComponent } from './solicitacao-financeira-inspecao.component';
import { nomeSolicitante } from './solicitacao-financeira.utils';

const ROTULO_STATUS: Record<StatusSolicitacao, string> = {
  RASCUNHO: 'Rascunho',
  SOLICITADO: 'Aguardando aprovação',
  APROVADO: 'Aprovada',
  REJEITADO: 'Rejeitada',
  CANCELADO: 'Cancelada'
};

const TOM_STATUS: Record<StatusSolicitacao, CesTone> = {
  RASCUNHO: 'neutral',
  SOLICITADO: 'warning',
  APROVADO: 'success',
  REJEITADO: 'danger',
  CANCELADO: 'neutral'
};

/** Campos que abrem as outras solicitações pelo mesmo critério. */
const INSPECIONAVEIS = ['BASE', 'VEICULO', 'FORNECEDOR', 'PROCESSO', 'OBRA', 'SOLICITANTE_COLABORADOR'];

/** Campos que já aparecem nos dados gerais e nos cartões; não se repetem nos blocos. */
const EM_DESTAQUE = [
  'SOLICITANTE_COLABORADOR', 'BASE', 'PROCESSO', 'OBRA', 'VEICULO',
  'FORNECEDOR', 'FORNECEDOR_NOME', 'FORNECEDOR_RAZAO_SOCIAL', 'VALOR',
  'FORMA_PAGAMENTO', 'VENCIMENTO', 'QUANTIDADE_PARCELAS', 'TEM_NF', 'NUMERO_NF', 'PRIORIDADE', 'TIPO_DESPESA'
];

/** Campos da origem do pagamento que a seção Compra de Origem já mostra. */
const ORIGEM_EM_SECAO = ['POSSUI_COMPRA_PREVIA', 'SOLICITACAO_COMPRA'];

const UM_DIA = 24 * 60 * 60 * 1000;
const UMA_HORA = 60 * 60 * 1000;

interface LinhaKv {
  rotulo: string;
  valor: string;
  /** Presente quando o valor abre a inspeção. */
  campo?: CampoFormulario;
  apagado?: boolean;
}

interface Faixa {
  tom: CesTone;
  icone: string;
  titulo: string;
  texto: string;
  rotuloMotivo?: string;
  motivo?: string | null;
}

interface CartaoPrazo {
  rotulo: string;
  icone: string;
  valor: string;
  legenda: string;
}

type BlocoRelatorio = GrupoDeCampos & { largo: boolean };

/**
 * Relatório de uma compra ou pagamento, no mesmo padrão dos relatórios de turno, de
 * nota e de solicitação de material: cabeçalho institucional, faixa da situação,
 * dados gerais, cartões, pontos de atenção, seções e a barra flutuante de ações.
 */
@Component({
  selector: 'app-solicitacao-financeira-detalhe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    AccordionModule,
    ButtonModule,
    CesAsyncStateComponent,
    CesIconButtonComponent,
    CesStatusComponent,
    AnalisarComCopilotoComponent,
    SolicitacaoDecisaoDrawerComponent,
    SolicitacaoDocumentosComponent,
    SolicitacaoFinanceiraHistoricoComponent,
    SolicitacaoFinanceiraInspecaoComponent
  ],
  templateUrl: './solicitacao-financeira-detalhe.component.html',
  styleUrls: ['../../execucao/relatorios/relatorio.css', './solicitacao-financeira-detalhe.component.css']
})
export class SolicitacaoFinanceiraDetalheComponent {
  private readonly service = inject(FinanceiroService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly navegacao = inject(NavigationHistoryService);
  private readonly notificacoes = inject(NotificationService);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private loadRequestId = 0;

  protected readonly tipo = this.route.snapshot.data['tipo'] as TipoSolicitacao;
  protected id = '';
  protected readonly carregando = signal(true);
  protected readonly erro = signal<string | null>(null);
  protected readonly processando = signal(false);
  protected readonly solicitacao = signal<SolicitacaoFinanceira | null>(null);
  protected readonly schema = signal<FormularioSchema | null>(null);
  protected readonly historico = signal<EventoSolicitacao[]>([]);
  protected readonly historicoCarregando = signal(false);
  protected readonly historicoErro = signal<string | null>(null);
  protected readonly historicoAberto = signal<string | number | string[] | number[] | null | undefined>(undefined);
  protected readonly inspecaoContexto = signal<InspecaoContexto | null>(null);
  protected readonly inspecaoAberta = signal(false);
  protected readonly acao = signal<AcaoDecisao | null>(null);
  protected readonly drawerAberto = signal(false);
  protected readonly emitidoEm = new Date();

  protected readonly rotuloStatus = ROTULO_STATUS;
  protected readonly tomStatus = TOM_STATUS;
  protected readonly nomeSolicitante = nomeSolicitante;
  protected readonly icone = (grupo: keyof typeof ICONE_GRUPO) => ICONE_GRUPO[grupo];

  /**
   * `acoes.podeDecidir` diz apenas que o estado admite decisão; quem pode decidir
   * é questão de permissão. As duas coisas precisam valer. A mesma alçada desfaz.
   */
  protected readonly podeDecidir =
    this.auth.currentUser()?.permissoes?.includes(
      this.tipo === 'COMPRA' ? 'financeiro:compra:aprovar' : 'financeiro:pagamento:aprovar'
    ) ?? false;

  /**
   * Os blocos do formulário, na ordem em que foram preenchidos, sem o que já está
   * nos dados gerais e sem campos vazios: numa ficha densa, traços escondem o que
   * de fato foi informado.
   */
  protected readonly blocos = computed<BlocoRelatorio[]>(() => {
    const item = this.solicitacao();
    const doc = this.schema();
    if (!item || !doc) return [];
    // Com a compra vinculada, a seção Compra de Origem já diz de onde o pagamento veio.
    const ocultos = item.compraVinculada ? [...EM_DESTAQUE, ...ORIGEM_EM_SECAO] : EM_DESTAQUE;
    const campos = doc.campos.filter(
      (campo) => item.valores[campo.chave]?.exibicao && !ocultos.includes(campo.campoCanonico?.toUpperCase() ?? '')
    );
    const grupos = agruparCampos(campos);
    const longo = (grupo?: GrupoDeCampos) =>
      !!grupo?.campos.some((campo) => campo.tipoCampo === 'TEXTO_LONGO');

    // Dois blocos por linha. Ocupa a linha inteira o bloco com texto longo e o que
    // ficaria sozinho nela — sem par ao lado, meia largura só deixa um buraco.
    const blocos: BlocoRelatorio[] = [];
    let coluna = 0;
    grupos.forEach((grupo, i) => {
      const largo = longo(grupo) || (coluna === 0 && (i === grupos.length - 1 || longo(grupos[i + 1])));
      blocos.push({ ...grupo, largo });
      coluna = largo ? 0 : (coluna + 1) % 2;
    });
    return blocos;
  });

  protected readonly identificacao = computed<LinhaKv[]>(() =>
    [
      this.linha('Base', 'BASE', true),
      this.linha('Setor / centro de custo', 'PROCESSO', true),
      this.linha('Obra / contrato', 'OBRA', true),
      this.linha('Veículo', 'VEICULO', true)
    ].filter((linha): linha is LinhaKv => !!linha)
  );

  protected readonly pagamento = computed<LinhaKv[]>(() => {
    const linhas = [
      this.linha('Forma de pagamento', 'FORMA_PAGAMENTO'),
      this.linha('Vencimento', 'VENCIMENTO'),
      this.linha('Parcelas', 'QUANTIDADE_PARCELAS'),
      this.linha('Tipo de despesa', 'TIPO_DESPESA'),
      this.linha('Prioridade', 'PRIORIDADE')
    ];
    const temNf = this.campo('TEM_NF');
    if (temNf) {
      const item = this.solicitacao()!;
      const valor = item.valores[temNf.chave]?.valor;
      const numero = this.exibicao('NUMERO_NF');
      linhas.push({
        rotulo: 'Nota fiscal',
        valor: valor === true ? (numero ? `Sim · nº ${numero}` : 'Sim') : valor === false ? 'Sem nota' : 'Não informado',
        apagado: valor == null
      });
    }
    return linhas.filter((linha): linha is LinhaKv => !!linha);
  });

  /**
   * Antes da decisão, tudo o que pesa nela. Depois, só o que ainda pede ação: a
   * nota e o boleto chegam depois da aprovação e podem voltar da conferência.
   */
  protected readonly atencao = computed(() => {
    const item = this.solicitacao();
    if (!item || item.status === 'CANCELADO') return [];
    const pontos = pontosDeAtencao(item, this.schema());
    const emAberto = item.status === 'RASCUNHO' || item.status === 'SOLICITADO';
    return emAberto ? pontos : pontos.filter((ponto) => ponto.aposDecisao);
  });

  /** O último desfazer, enquanto a solicitação ainda espera a nova decisão. */
  protected readonly decisaoDesfeita = computed<EventoSolicitacao | null>(() => {
    const item = this.solicitacao();
    const ultimo = this.historico().at(-1);
    return item?.status === 'SOLICITADO' && ultimo?.tipo === 'DECISAO_DESFEITA' ? ultimo : null;
  });

  protected readonly decisoesDesfeitas = computed(
    () => this.historico().filter((evento) => evento.tipo === 'DECISAO_DESFEITA').length
  );

  protected readonly resumoHistorico = computed(() => {
    const desfeitas = this.decisoesDesfeitas();
    if (desfeitas) return desfeitas === 1 ? '1 decisão desfeita' : `${desfeitas} decisões desfeitas`;
    const total = this.historico().length;
    return total === 1 ? '1 evento' : `${total} eventos`;
  });

  /** O que está acontecendo agora, e o que se espera de quem lê. */
  protected readonly faixaSituacao = computed<Faixa>(() => {
    const item = this.solicitacao()!;
    const desfeita = this.decisaoDesfeita();
    if (desfeita) {
      return {
        tom: 'warning',
        icone: 'pi pi-undo',
        titulo: 'Decisão desfeita · aguardando nova decisão',
        texto: `${desfeita.statusAnterior === 'APROVADO' ? 'A aprovação' : 'A rejeição'} foi desfeita por ${
          desfeita.usuario || 'usuário não identificado'
        } em ${this.dataHora(desfeita.ocorridoEm)}. Quem pediu e quem tinha decidido foram avisados.`,
        rotuloMotivo: 'Motivo',
        motivo: desfeita.texto
      };
    }
    switch (item.status) {
      case 'RASCUNHO':
        return {
          tom: 'neutral',
          icone: 'pi pi-file-edit',
          titulo: 'Rascunho',
          texto: 'Ainda não enviada. Os dados e anexos podem ser alterados até o envio para aprovação.'
        };
      case 'SOLICITADO':
        return {
          tom: 'info',
          icone: 'pi pi-hourglass',
          titulo: 'Aguardando decisão',
          texto: `Enviada em ${this.dataHora(item.dataSubmissao)}${this.textoFila(item)}. Quem tem alçada aprova, podendo autorizar valor menor, ou rejeita com justificativa.`
        };
      case 'APROVADO':
        return {
          tom: 'success',
          icone: 'pi pi-check-circle',
          titulo: 'Aprovada',
          texto: `Autorizada por ${this.decididaPor(item)} em ${this.dataHora(item.desfecho.dataDecisao)}: ${this.moeda(
            item.desfecho.valorAprovado
          )}${this.textoAbaixo(item)}.`,
          rotuloMotivo: 'Observação',
          motivo: item.desfecho.observacaoDecisao
        };
      case 'REJEITADO':
        return {
          tom: 'danger',
          icone: 'pi pi-times-circle',
          titulo: 'Rejeitada',
          texto: `Rejeitada por ${this.decididaPor(item)} em ${this.dataHora(item.desfecho.dataDecisao)}.`,
          rotuloMotivo: 'Motivo',
          motivo: item.desfecho.motivoRejeicao
        };
      default:
        return {
          tom: 'neutral',
          icone: 'pi pi-ban',
          titulo: 'Cancelada',
          texto: `Cancelada por ${this.canceladaPor(item)} em ${this.dataHora(item.desfecho.dataCancelamento)}.`,
          rotuloMotivo: 'Motivo',
          motivo: item.desfecho.motivoCancelamento
        };
    }
  });

  /** O quarto cartão mostra o prazo: o vencimento, a prioridade ou o tempo na fila. */
  protected readonly prazo = computed<CartaoPrazo>(() => {
    const item = this.solicitacao()!;
    const vencimento = this.campo('VENCIMENTO');
    const bruto = vencimento ? item.valores[vencimento.chave] : null;
    if (typeof bruto?.valor === 'string') {
      const dias = diasAte(bruto.valor);
      const legenda = dias < 0 ? `venceu há ${-dias} ${dias === -1 ? 'dia' : 'dias'}`
        : dias === 0 ? 'vence hoje' : `faltam ${dias} ${dias === 1 ? 'dia' : 'dias'}`;
      return { rotulo: 'Vencimento', icone: 'pi pi-calendar', valor: bruto.exibicao || bruto.valor, legenda };
    }
    const prioridade = this.exibicao('PRIORIDADE');
    if (prioridade) {
      return { rotulo: 'Prioridade', icone: 'pi pi-flag', valor: prioridade, legenda: 'informada por quem pediu' };
    }
    return {
      rotulo: item.status === 'SOLICITADO' ? 'Na fila' : 'Tempo até decidir',
      icone: 'pi pi-clock',
      valor: this.tempoDecisao(item) ?? '—',
      legenda: item.status === 'SOLICITADO' ? 'desde o envio' : 'do envio à decisão'
    };
  });

  constructor() {
    imprimirSempreNoTemaClaro();
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('id');
      if (!id || id === this.id) return;
      this.id = id;
      this.carregar();
    });
  }

  carregar(): void {
    const requisicao = ++this.loadRequestId;
    const id = this.id;
    this.carregando.set(true);
    this.erro.set(null);
    this.solicitacao.set(null);
    this.schema.set(null);
    this.historico.set([]);
    this.service.buscarSolicitacao(this.tipo, id).subscribe({
      next: (item) => {
        if (requisicao !== this.loadRequestId) return;
        this.carregarHistorico();
        this.service.buscarSchema(item.formulario.id).subscribe({
          next: (schema) => {
            if (requisicao !== this.loadRequestId) return;
            this.schema.set(schema);
            this.solicitacao.set(item);
            this.carregando.set(false);
            this.atenderAcaoDoEmail(item);
          },
          error: (error) => {
            if (requisicao === this.loadRequestId) this.erroCarregamento(error);
          }
        });
      },
      error: (error) => {
        if (requisicao === this.loadRequestId) this.erroCarregamento(error);
      }
    });
  }

  carregarHistorico(): void {
    const id = this.id;
    this.historicoCarregando.set(true);
    this.historicoErro.set(null);
    this.service.historico(this.tipo, id).subscribe({
      next: (eventos) => {
        if (id !== this.id) return;
        this.historico.set(eventos);
        this.historicoCarregando.set(false);
      },
      error: (error) => {
        if (id !== this.id) return;
        this.historicoErro.set(getErrorMessage(error));
        this.historicoCarregando.set(false);
      }
    });
  }

  /**
   * Os botões do e-mail abrem o relatório com `?acao=aprovar|rejeitar`: a decisão já
   * vem aberta para confirmar. Se ela não cabe mais (outro diretor decidiu, ou falta
   * alçada), a pessoa fica sabendo em vez de ver um botão sumir. O parâmetro sai da
   * URL em seguida, para que recarregar a página não reabra a decisão.
   */
  private atenderAcaoDoEmail(item: SolicitacaoFinanceira): void {
    const pedida = this.route.snapshot.queryParamMap?.get('acao');
    const acao = pedida === 'aprovar' ? 'APROVAR' : pedida === 'rejeitar' ? 'REJEITAR' : null;
    if (!acao) return;

    this.router.navigate([], { relativeTo: this.route, queryParams: { acao: null }, queryParamsHandling: 'merge', replaceUrl: true });
    if (!item.acoes.podeDecidir) {
      this.notificacoes.info(`Esta solicitação já está ${ROTULO_STATUS[item.status].toLowerCase()}: não há decisão pendente.`, {
        title: 'Nada a decidir'
      });
      return;
    }
    if (!this.podeDecidir) {
      this.notificacoes.warning('Seu perfil não tem alçada para decidir esta solicitação.', { title: 'Sem permissão' });
      return;
    }
    this.abrirAcao(acao);
  }

  // ── Textos do relatório ─────────────────────────────────────────────────────────

  /** Quem a solicitação atende: o colaborador informado no formulário, senão quem abriu. */
  protected solicitante(item: SolicitacaoFinanceira): string {
    return this.exibicao('SOLICITANTE_COLABORADOR') ?? nomeSolicitante(item);
  }

  /** Nome de quem decidiu: o do histórico (colaborador), senão o login. */
  protected decididaPor(item: SolicitacaoFinanceira): string {
    const evento = [...this.historico()].reverse().find((e) => e.tipo === 'APROVADA' || e.tipo === 'REJEITADA');
    return evento?.usuario ?? item.desfecho.usuarioDecisao ?? 'usuário não identificado';
  }

  protected canceladaPor(item: SolicitacaoFinanceira): string {
    const evento = this.historico().find((e) => e.tipo === 'CANCELADA');
    return evento?.usuario ?? nomeSolicitante(item);
  }

  protected dataDesfecho(item: SolicitacaoFinanceira): string | null {
    return item.desfecho.dataDecisao ?? item.desfecho.dataCancelamento ?? null;
  }

  /** Na fila: desde o envio. Decidida: do envio à decisão. */
  protected tempoDecisao(item: SolicitacaoFinanceira): string | null {
    if (!item.dataSubmissao) return null;
    const inicio = new Date(item.dataSubmissao).getTime();
    if (item.status === 'SOLICITADO') return this.duracao(Date.now() - inicio);
    if (!item.desfecho.dataDecisao) return null;
    return this.duracao(new Date(item.desfecho.dataDecisao).getTime() - inicio);
  }

  protected legendaAutorizado(item: SolicitacaoFinanceira): string {
    if (item.desfecho.valorAprovado != null) {
      const abaixo = this.abaixoDoSolicitado(item);
      return abaixo ? `${this.moeda(abaixo)} abaixo do solicitado` : 'igual ao solicitado';
    }
    switch (item.status) {
      case 'RASCUNHO':
        return 'ainda não enviada';
      case 'REJEITADO':
        return 'não autorizada (rejeitada)';
      case 'CANCELADO':
        return 'não autorizada (cancelada)';
      default:
        return 'aguardando decisão';
    }
  }

  protected legendaDocumentos(item: SolicitacaoFinanceira): string {
    const docs = item.documentos;
    if (!docs.length) return 'nenhum anexo ainda';
    const pendencia = docs.filter((d) => d.situacao === 'COM_PENDENCIA').length;
    if (pendencia) return `${pendencia} com pendência`;
    const aguardando = docs.filter((d) => d.situacao === 'PENDENTE').length;
    return aguardando ? `${aguardando} aguardando conferência` : 'todos conferidos';
  }

  protected tomDocumentos(item: SolicitacaoFinanceira): CesTone {
    if (item.documentos.some((d) => d.situacao === 'COM_PENDENCIA')) return 'danger';
    return item.documentos.length ? 'neutral' : 'warning';
  }

  // ── Navegação e ações ───────────────────────────────────────────────────────────

  protected irPara(secao: 'documentos'): void {
    document.getElementById(secao)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected editar(): void {
    this.router.navigate(['/financeiro', this.slug, this.id, 'editar']);
  }

  protected voltar(): void {
    this.navegacao.back(['/financeiro', this.slug]);
  }

  protected abrirCompra(idCompra: string): void {
    this.router.navigate(['/financeiro', 'compras', idCompra]);
  }

  protected abrirSolicitacao(item: SolicitacaoFinanceira): void {
    const slug = item.formulario.tipoSolicitacao === 'COMPRA' ? 'compras' : 'pagamentos';
    this.router.navigate(['/financeiro', slug, item.id]);
  }

  protected imprimir(): void {
    window.print();
  }

  /**
   * O componente de anexos sempre emite a resposta mais recente da solicitação.
   * Mantemos a atualização em um único ponto para evitar que o relatório continue
   * exibindo ações antigas depois de uma mutação documental concorrente.
   */
  protected documentosAtualizados(item: SolicitacaoFinanceira): void {
    this.solicitacao.set(item);
  }

  protected submeter(): void {
    this.processando.set(true);
    this.service.submeter(this.tipo, this.id).subscribe({
      next: (res) => {
        this.processando.set(false);
        this.solicitacao.set(res);
        this.carregarHistorico();
        this.notificacoes.success('Solicitação enviada para aprovação.');
      },
      error: (error) => this.erroAcao(error)
    });
  }

  protected abrirAcao(acao: AcaoDecisao): void {
    this.acao.set(acao);
    this.drawerAberto.set(true);
  }

  protected confirmar(decisao: DecisaoConfirmada): void {
    this.processando.set(true);
    const operacao = {
      APROVAR: () => this.service.aprovar(this.tipo, this.id, decisao.valorAprovado, decisao.texto || null),
      REJEITAR: () => this.service.rejeitar(this.tipo, this.id, decisao.texto),
      CANCELAR: () => this.service.cancelar(this.tipo, this.id, decisao.texto),
      DESFAZER: () => this.service.desfazerDecisao(this.tipo, this.id, decisao.texto)
    }[decisao.acao];
    const mensagem = {
      APROVAR: 'Solicitação aprovada.',
      REJEITAR: 'Solicitação rejeitada.',
      CANCELAR: 'Solicitação cancelada.',
      DESFAZER: 'Decisão desfeita. A solicitação voltou a aguardar aprovação.'
    }[decisao.acao];

    operacao().subscribe({
      next: (res) => {
        this.processando.set(false);
        this.drawerAberto.set(false);
        this.solicitacao.set(res);
        this.carregarHistorico();
        this.notificacoes.success(mensagem);
      },
      error: (error) => this.erroAcao(error)
    });
  }

  // ── Inspeção: abre outras solicitações pelo mesmo critério ──────────────────────

  protected podeInspecionarCampo(campo: CampoFormulario): boolean {
    return INSPECIONAVEIS.includes(campo.campoCanonico?.toUpperCase() ?? '') || campo.tipoCampo === 'REFERENCIA';
  }

  protected inspecionarCampo(campo: CampoFormulario, valor: ValorSolicitacao): void {
    const label = valor.exibicao || String(valor.valor);
    const id = valor.valor != null ? String(valor.valor) : null;
    const porCanonico: Record<string, InspecaoContexto> = {
      BASE: { tipo: 'BASE', id, label, subtitulo: 'Base operacional', icone: 'pi pi-map-marker', filtros: { baseId: id } },
      VEICULO: { tipo: 'VEICULO', id, label, subtitulo: 'Veículo da frota', icone: 'pi pi-car', filtros: { veiculoId: id } },
      FORNECEDOR: { tipo: 'FORNECEDOR', id, label, subtitulo: 'Fornecedor', icone: 'pi pi-building', filtros: { fornecedorId: id } },
      PROCESSO: { tipo: 'PROCESSO', id, label, subtitulo: 'Setor / centro de custo', icone: 'pi pi-cog', filtros: { processoId: id } },
      OBRA: { tipo: 'OBRA', id, label, subtitulo: 'Obra / contrato', icone: 'pi pi-briefcase', filtros: { obraId: id } }
    };
    this.abrirInspecao(
      porCanonico[campo.campoCanonico?.toUpperCase() ?? ''] ?? {
        tipo: 'SOLICITANTE',
        id,
        label,
        subtitulo: campo.rotulo,
        icone: 'pi pi-search',
        filtros: { buscaGlobal: label }
      }
    );
  }

  protected inspecionarFornecedor(item: SolicitacaoFinanceira): void {
    const fornecedor = item.fornecedor;
    if (!fornecedor) return;
    this.abrirInspecao({
      tipo: 'FORNECEDOR',
      id: fornecedor.id,
      label: fornecedor.nome,
      subtitulo: 'Fornecedor',
      icone: 'pi pi-building',
      filtros: fornecedor.id ? { fornecedorId: fornecedor.id } : { buscaGlobal: fornecedor.nome }
    });
  }

  protected inspecionarSolicitante(item: SolicitacaoFinanceira): void {
    const nome = nomeSolicitante(item);
    this.abrirInspecao({
      tipo: 'SOLICITANTE',
      id: item.usuarioSolicitanteId,
      label: nome,
      subtitulo: 'Quem abriu',
      icone: 'pi pi-user',
      filtros: {
        usuarioSolicitanteId: item.usuarioSolicitanteId || null,
        buscaGlobal: item.usuarioSolicitanteId ? null : nome
      }
    });
  }

  protected inspecionarDecisor(item: SolicitacaoFinanceira): void {
    const id = item.desfecho.usuarioDecisaoId ?? null;
    const nome = this.decididaPor(item);
    this.abrirInspecao({
      tipo: 'DECISOR',
      id,
      label: nome,
      subtitulo: 'Quem decidiu',
      icone: 'pi pi-check-circle',
      filtros: { usuarioDecisaoId: id, buscaGlobal: id ? null : nome }
    });
  }

  // ── Apoio ───────────────────────────────────────────────────────────────────────

  private abrirInspecao(contexto: InspecaoContexto): void {
    this.inspecaoContexto.set(contexto);
    this.inspecaoAberta.set(true);
  }

  private campo(canonico: string): CampoFormulario | undefined {
    return this.schema()?.campos.find((c) => c.campoCanonico?.toUpperCase() === canonico);
  }

  private exibicao(canonico: string): string | null {
    const campo = this.campo(canonico);
    return (campo && this.solicitacao()?.valores[campo.chave]?.exibicao) || null;
  }

  /** Linha dos dados gerais, só quando a variante tem o campo e ele foi preenchido. */
  private linha(rotulo: string, canonico: string, inspecionavel = false): LinhaKv | null {
    const campo = this.campo(canonico);
    const valor = this.exibicao(canonico);
    if (!campo || !valor) return null;
    return { rotulo, valor, campo: inspecionavel ? campo : undefined };
  }

  private abaixoDoSolicitado(item: SolicitacaoFinanceira): number | null {
    const aprovado = item.desfecho.valorAprovado;
    if (aprovado == null) return null;
    const diferenca = item.valor - aprovado;
    return diferenca > 0.005 ? diferenca : null;
  }

  private textoAbaixo(item: SolicitacaoFinanceira): string {
    const abaixo = this.abaixoDoSolicitado(item);
    return abaixo ? ` (${this.moeda(abaixo)} abaixo do solicitado)` : '';
  }

  private textoFila(item: SolicitacaoFinanceira): string {
    const tempo = this.tempoDecisao(item);
    return tempo ? `, na fila há ${tempo}` : '';
  }

  private duracao(ms: number): string {
    if (ms < UMA_HORA) return 'menos de 1 h';
    if (ms < UM_DIA) {
      const horas = Math.floor(ms / UMA_HORA);
      return `${horas} h`;
    }
    const dias = Math.floor(ms / UM_DIA);
    return dias === 1 ? '1 dia' : `${dias} dias`;
  }

  private dataHora(valor?: string | null): string {
    return valor
      ? new Date(valor).toLocaleString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
      : '—';
  }

  private moeda(valor?: number | null): string {
    return (valor ?? 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  }

  private erroAcao(error: unknown): void {
    this.processando.set(false);
    this.notificacoes.error(getErrorMessage(error), { title: 'Não foi possível concluir' });
  }

  private erroCarregamento(error: unknown): void {
    this.carregando.set(false);
    this.erro.set(getErrorMessage(error));
  }

  private get slug(): string {
    return this.tipo === 'COMPRA' ? 'compras' : 'pagamentos';
  }
}
