import { Location } from '@angular/common';
import { idAtual } from '../../../core/sync/ids-trocados';
import { ChangeDetectionStrategy, Component, OnInit, signal, inject, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { DbService } from '../../../core/db/db.service';
import { OutboxService } from '../../../core/sync/outbox.service';
import { SyncService } from '../../../core/sync/sync.service';
import { ToastService } from '../../../core/ui/toast.service';
import { SessaoService } from '../../../core/sessao/sessao.service';
import { TransferenciaLocal, OutboxItem } from '../../../core/db/models';
import { environment } from '../../../../environments/environment';
import { BarraTopoComponent, EstadoEnvio, FolhaComponent, IconeComponent, StatusEnvioComponent, VazioComponent, dataCurta, hora, numeroBr } from '../../../ui';

interface ItemEnriquecido {
  idMaterial: string;
  quantidade: number;
  condicao: string;
  numSerie?: string | null;
  descricao: string;
  codigo: string;
  unidadeMedida: string;
}

/** Detalhe de uma transferência: estado de envio, caminho, materiais com quantidades e assinaturas. */
@Component({
  selector: 'app-transferencia-detalhe',
  standalone: true,
  imports: [BarraTopoComponent, FolhaComponent, IconeComponent, StatusEnvioComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-barra-topo titulo="Transferência" [subtitulo]="quando()" (voltar)="goBack()" />

    @if (isLoading()) {
      <div class="ces-esqueletos" aria-busy="true" aria-label="Carregando">
        <div class="ces-esqueleto" style="height: 64px"></div><div class="ces-esqueleto" style="height: 120px"></div><div class="ces-esqueleto" style="height: 160px"></div>
      </div>
    } @else if (transferencia(); as t) {
      <section class="ces-faixa-estado" [class.ces-faixa-estado--atencao]="estado() === 'conflict'" [class.ces-faixa-estado--erro]="estado() === 'error'" aria-live="polite">
        <ces-status-envio [estado]="estado()" [rotulo]="rotuloEstado()" />
        <p>{{ explicacao() }}</p>
      </section>
      @if (outboxItem()?.serverOperationId) {
        <p class="protocolo ces-num">Protocolo #{{ outboxItem()?.serverOperationId }}</p>
      }

      <h2 class="ces-secao">Caminho do material</h2>
      <div class="ces-grupo percurso">
        <div class="ponto"><span class="bolinha" aria-hidden="true"></span>
          <span class="txt"><span class="rot">Saiu de</span><strong>{{ estoqueOrigemNome() }}</strong></span></div>
        <div class="ponto"><span class="bolinha cheia" aria-hidden="true"></span>
          <span class="txt"><span class="rot">Foi para</span><strong>{{ estoqueDestinoNome() }}</strong></span></div>
      </div>

      <h2 class="ces-secao ces-secao--marcada"><span class="ces-marca ces-marca--aplicado" aria-hidden="true"><ces-icone nome="material" [tamanho]="14" /></span>
        <span>Materiais</span><span class="ces-contagem">{{ itens().length }}</span></h2>
      <ul class="ces-grupo" aria-label="Materiais transferidos">
        @for (item of itens(); track $index) {
          <li><div class="ces-linha">
            <span class="ces-linha__texto">
              <span class="ces-linha__titulo">{{ item.descricao }}</span>
              <span class="ces-linha__apoio ces-num"><span class="ces-cond" [class.ces-cond--sucata]="item.condicao !== 'NOVO'">{{ item.condicao === 'NOVO' ? 'Novo' : 'Sucata' }}</span>
                · {{ item.numSerie ? 'Série ' + item.numSerie : 'Cód. ' + item.codigo }}</span>
            </span>
            <span class="ces-qtd">{{ num(item.quantidade) }}<small>{{ item.unidadeMedida }}</small></span>
          </div></li>
        }
      </ul>

      <h2 class="ces-secao">Assinaturas</h2>
      <div class="assinaturas">
        @for (a of assinaturas(); track a.rotulo) {
          <figure class="assinatura">
            <figcaption>{{ a.rotulo }}</figcaption>
            @if (a.url) { <img [src]="a.url" [alt]="'Assinatura de ' + a.rotulo.toLowerCase()"> }
            @else if (a.fisica) { <span class="sem"><ces-icone nome="documento" [tamanho]="18" />Assinada no papel</span> }
            @else if (carregandoAssinaturas()) { <span class="ces-esqueleto carregando" aria-label="Carregando assinatura"></span> }
            @else { <span class="sem">Não colhida</span> }
          </figure>
        }
      </div>
      <div class="ces-respiro"></div>

      @if (showActionsFooter()) {
        <footer class="ces-rodape-acoes">
          @if (canDiscard()) { <button type="button" class="ces-btn ces-btn--perigo" (click)="abrirDescarte()">Descartar</button> }
          @if (isResultadoIncerto()) {
            <button type="button" class="ces-btn ces-btn--principal" (click)="reconciliar()" [disabled]="isReconciling()">
              <ces-icone nome="atualizar" [tamanho]="18" />{{ isReconciling() ? 'Conferindo…' : 'Conferir no servidor' }}
            </button>
          }
        </footer>
      }
    } @else {
      <ces-vazio icone="transferencia" titulo="Transferência não encontrada" texto="Ela não está mais neste aparelho.">
        <button type="button" class="ces-btn ces-btn--tonal" (click)="goBack()">Ver transferências</button>
      </ces-vazio>
    }

    <ces-folha [aberta]="showDiscardConfirmation()" (fechar)="fecharDescarte()" titulo="Descartar esta transferência?"
      descricao="Ela ainda não saiu do aparelho. Descartar apaga o registro e tira da fila de envio.">
      <div rodape class="ces-folha-rodape">
        <button type="button" class="ces-btn ces-btn--neutro" (click)="fecharDescarte()">Manter</button>
        <button type="button" class="ces-btn ces-btn--perigo" (click)="discardTransferencia()">Descartar</button>
      </div>
    </ces-folha>
  `,
  styles: [`
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    /* Nada encolhe: com conteúdo maior que a tela, a página rola (grupos com overflow:hidden eram cortados). */
    :host > * { flex-shrink: 0; }
    .protocolo { margin: var(--ces-e2) var(--ces-e5) 0; font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .percurso { padding: var(--ces-e3) var(--ces-e4); display: grid; gap: var(--ces-e4); position: relative; }
    .percurso::before { content: ''; position: absolute; left: calc(var(--ces-e4) + 6px); top: 28px; bottom: 28px; border-left: 2px dashed var(--ces-linha-forte); }
    .ponto { display: flex; gap: var(--ces-e3); align-items: center; }
    .bolinha { width: 14px; height: 14px; border-radius: 50%; border: 3px solid var(--ces-destaque); background: var(--ces-superficie); flex: none; z-index: 1; }
    .bolinha.cheia { background: var(--ces-destaque); }
    .txt { display: grid; gap: 2px; min-width: 0; }
    .rot { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .txt strong { font: var(--ces-t-corpo-forte); overflow-wrap: anywhere; }
    .assinaturas { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: var(--ces-e3); padding: 0 var(--ces-e3); }
    .assinatura { margin: 0; padding: var(--ces-e3); border-radius: var(--ces-r-grupo); background: var(--ces-superficie); display: grid; gap: var(--ces-e2); }
    figcaption { font: 500 .8125rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .assinatura img { width: 100%; height: 96px; object-fit: contain; background: #fff; border-radius: 8px; border: 1px solid var(--ces-linha); }
    .sem { min-height: 96px; display: flex; align-items: center; justify-content: center; gap: 6px; border-radius: 8px;
      background: var(--ces-superficie-2); font: var(--ces-t-apoio); color: var(--ces-texto-2); text-align: center; }
    .carregando { height: 96px; }
  `],
})
export class TransferenciaDetalheComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dbService = inject(DbService);
  private readonly outboxService = inject(OutboxService);
  private readonly syncService = inject(SyncService);
  private readonly toastService = inject(ToastService);
  private readonly sessao = inject(SessaoService);

  id = this.route.snapshot.paramMap.get('id')!;
  private readonly location = inject(Location);
  readonly isLoading = signal(true);
  readonly isReconciling = signal(false);
  readonly transferencia = signal<TransferenciaLocal | null>(null);
  readonly outboxItem = signal<OutboxItem | null>(null);
  readonly itens = signal<ItemEnriquecido[]>([]);

  readonly assinaturaEntregadorUrl = signal<string | null>(null);
  readonly assinaturaRecebedorUrl = signal<string | null>(null);
  readonly carregandoAssinaturas = signal(false);

  readonly estoqueOrigemNome = signal('');
  readonly estoqueDestinoNome = signal('');

  readonly showDiscardConfirmation = signal(!!this.sessao.estadoTela<{ folha?: string }>().folha);

  readonly canDiscard = computed(() => this.transferencia()?.statusSync !== 'SINCRONIZADO' && !!this.outboxItem() && this.outboxService.canDiscard(this.outboxItem()));
  readonly isEmTratamentoBackoffice = computed(() => this.outboxService.isEmTratamentoBackoffice(this.outboxItem()));
  readonly isResultadoIncerto = computed(() => this.outboxService.isResultadoIncerto(this.outboxItem()));
  readonly showActionsFooter = computed(() => this.canDiscard() || this.isResultadoIncerto());

  isAssinaturaFisica(valor?: string | null): boolean {
    return valor?.toUpperCase() === 'FISICA';
  }

  /** O registro foi aceito pelo servidor e ganhou id novo: segue com ele e corrige a URL. */
  private seguirIdNovo(): boolean {
    const novo = idAtual(this.id);
    if (!novo) return false;
    const antigo = this.id;
    this.id = novo;
    this.location.replaceState(this.location.path().replace(antigo, novo));
    return true;
  }

  async ngOnInit() {
    await this.loadData();
  }

  async loadData() {
    this.isLoading.set(true);
    try {
      let transf = await this.dbService.getTransferenciaById(this.id);
      if (!transf && this.seguirIdNovo()) transf = await this.dbService.getTransferenciaById(this.id);
      if (!transf) {
        this.isLoading.set(false);
        return;
      }
      this.transferencia.set(transf);

      // Resolver assinaturas locais imediatamente se existirem como imagem
      if (transf.assinaturaEntregador?.startsWith('data:image/')) {
        this.assinaturaEntregadorUrl.set(transf.assinaturaEntregador);
      } else {
        this.assinaturaEntregadorUrl.set(null);
      }

      if (transf.assinaturaRecebedor?.startsWith('data:image/')) {
        this.assinaturaRecebedorUrl.set(transf.assinaturaRecebedor);
      } else {
        this.assinaturaRecebedorUrl.set(null);
      }

      // Buscar item no outbox (buscamos pelo payload tempId se possível)
      const outbox = await this.dbService.getOutboxItemByTransferenciaId(this.id);
      this.outboxItem.set(outbox || null);

      // Buscar nomes dos estoques
      const [origem, destino] = await Promise.all([
        this.dbService.getEstoques().then(list => list.find(e => e.id === transf.idEstoqueOrigem)),
        this.dbService.getEstoques().then(list => list.find(e => e.id === transf.idEstoqueDestino))
      ]);

      this.estoqueOrigemNome.set(origem?.nomeIdentificador || 'Estoque da equipe');
      this.estoqueDestinoNome.set(destino?.nomeIdentificador || 'Estoque não encontrado');

      // Enriquecer itens com dados dos materiais
      const materiais = await this.dbService.getMateriais();
      const materiaisMap = new Map(materiais.map(m => [m.id, m]));

      const itensEnriquecidos = transf.itens.map(item => {
        const mat = materiaisMap.get(item.idMaterial);
        return {
          ...item,
          descricao: mat?.descricao || 'Material não encontrado',
          codigo: mat?.codigo || 'N/A',
          unidadeMedida: mat?.unidadeMedida || 'UN'
        };
      });
      this.itens.set(itensEnriquecidos);

      // Se sincronizado e alguma assinatura não for imagem local nem física, baixa do servidor
      const precisaBaixarEntregador = !this.assinaturaEntregadorUrl() && !this.isAssinaturaFisica(transf.assinaturaEntregador) && transf.statusSync === 'SINCRONIZADO';
      const precisaBaixarRecebedor = !this.assinaturaRecebedorUrl() && !this.isAssinaturaFisica(transf.assinaturaRecebedor) && transf.statusSync === 'SINCRONIZADO';

      if (precisaBaixarEntregador || precisaBaixarRecebedor) {
        this.baixarAssinaturasRemotas(transf, precisaBaixarEntregador, precisaBaixarRecebedor);
      }

    } catch (error) {
      console.error('Erro ao carregar detalhe da transferência:', error);
    } finally {
      this.isLoading.set(false);
    }
  }

  private async baixarAssinaturasRemotas(transf: TransferenciaLocal, buscarEntregador: boolean, buscarRecebedor: boolean) {
    this.carregandoAssinaturas.set(true);
    let alterou = false;

    if (buscarEntregador) {
      try {
        const blob = await firstValueFrom(
          this.http.get(`${environment.apiBaseUrl}/v1/movimentacoes-estoque/operacao/${this.id}/assinaturas/ENTREGADOR`, { responseType: 'blob' })
        );
        if (blob && blob.size > 0) {
          const dataUrl = await this.blobToDataUrl(blob);
          this.assinaturaEntregadorUrl.set(dataUrl);
          transf.assinaturaEntregador = dataUrl;
          alterou = true;
        }
      } catch {
        // Sem assinatura no servidor ou offline: mantém estado atual
      }
    }

    if (buscarRecebedor) {
      try {
        const blob = await firstValueFrom(
          this.http.get(`${environment.apiBaseUrl}/v1/movimentacoes-estoque/operacao/${this.id}/assinaturas/RECEBEDOR`, { responseType: 'blob' })
        );
        if (blob && blob.size > 0) {
          const dataUrl = await this.blobToDataUrl(blob);
          this.assinaturaRecebedorUrl.set(dataUrl);
          transf.assinaturaRecebedor = dataUrl;
          alterou = true;
        }
      } catch {
        // Sem assinatura no servidor ou offline: mantém estado atual
      }
    }

    if (alterou) {
      await this.dbService.saveTransferencia(transf);
    }
    this.carregandoAssinaturas.set(false);
  }

  private blobToDataUrl(blob: Blob): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onloadend = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(blob);
    });
  }

  readonly estado = computed<EstadoEnvio>(() => {
    if (this.isEmTratamentoBackoffice() || this.isResultadoIncerto()) return 'conflict';
    if (this.outboxItem()?.status === 'ERRO' || this.transferencia()?.statusSync === 'ERRO') return 'error';
    if (this.outboxItem()?.status === 'PENDENTE' || this.transferencia()?.statusSync === 'PENDENTE') return 'queued';
    return 'synced';
  });
  readonly rotuloEstado = computed(() => this.isEmTratamentoBackoffice() ? 'Com o escritório' : this.isResultadoIncerto() ? 'A confirmar' : undefined);
  readonly explicacao = computed(() => {
    if (this.isEmTratamentoBackoffice()) {
      const detalhe = this.outboxItem()?.erroDetalhes;
      return 'Recebida e guardada para o escritório analisar. Não dá para mudar pelo app.' + (detalhe ? ' ' + detalhe : '');
    }
    if (this.isResultadoIncerto()) return 'O sinal caiu durante o envio. Confira no servidor antes de qualquer mudança.';
    if (this.estado() === 'error') return this.outboxItem()?.erroDetalhes || this.transferencia()?.erroDetalhes || 'O servidor não aceitou esta transferência.';
    if (this.estado() === 'queued') return 'Guardada no aparelho. Sai sozinha quando houver internet.';
    return 'Registrada no sistema.';
  });
  readonly quando = computed(() => {
    const d = this.transferencia()?.dataTransferencia;
    return d ? dataCurta(d) + ' às ' + hora(d) : '';
  });
  readonly assinaturas = computed(() => {
    const t = this.transferencia();
    return [
      { rotulo: 'Quem entregou', url: this.assinaturaEntregadorUrl(), fisica: this.isAssinaturaFisica(t?.assinaturaEntregador) },
      { rotulo: 'Quem recebeu', url: this.assinaturaRecebedorUrl(), fisica: this.isAssinaturaFisica(t?.assinaturaRecebedor) },
    ];
  });

  num(n: number): string {
    return numeroBr(n);
  }

  abrirDescarte(): void {
    this.showDiscardConfirmation.set(true);
    this.sessao.definirEstadoTela({ folha: 'descartar' });
  }

  fecharDescarte(): void {
    this.showDiscardConfirmation.set(false);
    this.sessao.definirEstadoTela({ folha: null });
  }

  async reconciliar() {
    try {
      this.isReconciling.set(true);
      await this.syncService.syncOutbox({ reconciliar: true });
      await this.loadData();
      this.toastService.success('Conferido com o servidor.');
    } catch (err) {
      console.error('Erro na reconciliação:', err);
      this.toastService.error('Não foi possível conferir agora. Tente de novo com internet.');
    } finally {
      this.isReconciling.set(false);
    }
  }

  async discardTransferencia() {
    try {
      // 1. Remover do Outbox (se existir)
      const outbox = this.outboxItem();
      if (outbox) {
        const removed = await this.outboxService.discard(outbox.id);
        if (!removed) {
          this.toastService.error('Esta transferência foi preservada e aguarda tratamento administrativo.');
          return;
        }
      }

      // 2. Remover do IndexedDB (transferencias)
      await this.dbService.deleteTransferencia(this.id);

      this.toastService.success('Transferência descartada.');

      // 3. Voltar para a lista
      this.router.navigate(['/transferencias']);
    } catch (error: any) {
      console.error('Erro ao descartar transferência:', error);
      this.toastService.error(error?.message || 'Não foi possível descartar a transferência.');
    } finally {
      this.fecharDescarte();
    }
  }

  goBack() {
    this.router.navigate(['/transferencias']);
  }
}
