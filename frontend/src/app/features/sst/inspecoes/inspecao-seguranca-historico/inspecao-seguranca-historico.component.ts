import { CommonModule, DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  Input,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { CesAsyncStateComponent, CesButtonComponent, CesTone, NotificationService } from '@ces/ui';
import { getErrorMessage } from '../../../../core/models/shared.models';
import {
  HistoricoInspecaoAlteracaoItem,
  HistoricoInspecaoEvento,
  HistoricoInspecaoResponse,
} from '../../../../core/models/sst.models';
import { SstService } from '../../../../core/services/sst.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';

export interface SnapshotDisplayItem {
  rotulo: string;
  valor: string;
}

@Component({
  selector: 'app-inspecao-seguranca-historico',
  standalone: true,
  imports: [CommonModule, DatePipe, CesAsyncStateComponent, CesButtonComponent],
  templateUrl: './inspecao-seguranca-historico.component.html',
  styleUrl: './inspecao-seguranca-historico.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InspecaoSegurancaHistoricoComponent implements OnInit {
  private readonly sstService = inject(SstService);
  private readonly notifications = inject(NotificationService);
  private readonly userLookup = inject(UsuarioLookupService);
  private readonly cdr = inject(ChangeDetectorRef);

  @Input()
  set idInspecao(val: string) {
    this._idInspecao.set(val || '');
  }
  get idInspecao(): string {
    return this._idInspecao();
  }
  private readonly _idInspecao = signal<string>('');

  @Input() ocultarCabecalho = false;

  protected readonly page = signal(0);
  protected readonly response = signal<HistoricoInspecaoResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly totalPages = computed(() => this.response()?.page.totalPages ?? 1);

  /** A sequência do evento é a fonte de ordenação da linha do tempo */
  protected readonly sortedEvents = computed(() =>
    [...(this.response()?.eventos ?? [])].sort((a, b) => {
      const sequenceDifference = this.sequenceOf(a) - this.sequenceOf(b);
      return sequenceDifference || this.dateOf(a) - this.dateOf(b);
    }),
  );

  /** A integridade informa a cadeia de integridade SHA-256 */
  protected readonly integrityInfo = computed(() => {
    const chains = this.response()?.integridade ?? [];
    if (!chains.length) return null;

    const status = chains.some((chain) => chain.status === 'VIOLADO')
      ? 'VIOLADO'
      : chains.every((chain) => chain.status === 'INTEGRO')
        ? 'INTEGRO'
        : 'NAO_VERIFICADO';

    return { status, chains };
  });

  ngOnInit(): void {
    this.load();
  }

  public recarregar(): void {
    this.page.set(0);
    this.load();
  }

  protected previous(): void {
    if (this.page() > 0) {
      this.page.update((value) => value - 1);
      this.load();
    }
  }

  protected next(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update((value) => value + 1);
      this.load();
    }
  }

  protected load(): void {
    const id = this.idInspecao;
    if (!id) return;
    this.loading.set(true);
    this.error.set('');
    this.sstService.obterHistoricoInspecao(id, this.page()).subscribe({
      next: (response) => {
        this.response.set(response);
        this.loading.set(false);
        this.cdr.markForCheck();
      },
      error: (error) => {
        const message = getErrorMessage(error);
        this.error.set(message);
        this.loading.set(false);
        this.cdr.markForCheck();
        this.notifications.error(message, { title: 'Histórico indisponível' });
      },
    });
  }

  protected integrityTone(status: string): CesTone {
    return status === 'INTEGRO' ? 'success' : status === 'VIOLADO' ? 'danger' : 'warning';
  }

  protected integrityLabel(status: string): string {
    return status === 'INTEGRO'
      ? 'Cadeia íntegra'
      : status === 'VIOLADO'
        ? 'Integridade comprometida'
        : 'Não verificada';
  }

  protected integrityDescription(status: string): string {
    if (status === 'INTEGRO') return 'Todos os eventos foram verificados criptograficamente e a sequência está íntegra.';
    if (status === 'VIOLADO') return 'A sequência apresenta inconsistências criptográficas e exige análise.';
    return 'Baseline legado: a cadeia não possui verificação disponível. Isso não indica corrupção.';
  }

  protected eventLabel(tipo: string): string {
    switch (tipo) {
      case 'REGISTRO_INICIAL':
        return 'Registro inicial da inspeção';
      case 'EDICAO_CABECALHO':
        return 'Edição de cabeçalho';
      case 'EDICAO_TOTAL':
        return 'Edição completa da inspeção';
      case 'TRATAMENTO_DESVIO':
        return 'Tratamento de desvio';
      default:
        return tipo.replace(/_/g, ' ');
    }
  }

  protected eventIcon(tipo: string): string {
    switch (tipo) {
      case 'REGISTRO_INICIAL':
        return 'pi pi-file-plus';
      case 'EDICAO_CABECALHO':
        return 'pi pi-pencil';
      case 'EDICAO_TOTAL':
        return 'pi pi-file-edit';
      case 'TRATAMENTO_DESVIO':
        return 'pi pi-check-circle';
      default:
        return 'pi pi-info-circle';
    }
  }

  protected eventTone(tipo: string): 'info' | 'success' | 'warning' | 'danger' | 'neutral' {
    switch (tipo) {
      case 'REGISTRO_INICIAL':
        return 'info';
      case 'EDICAO_CABECALHO':
      case 'EDICAO_TOTAL':
        return 'warning';
      case 'TRATAMENTO_DESVIO':
        return 'success';
      default:
        return 'neutral';
    }
  }

  protected fieldLabel(caminho: string): string {
    const clean = caminho.split('.').at(-1) ?? caminho;
    const mapa: Record<string, string> = {
      tipoInspecao: 'Tipo de inspeção',
      idTipoInspecao: 'Tipo de inspeção',
      processo: 'Processo',
      idProcesso: 'Processo',
      baseOperacional: 'Base operacional',
      idBaseOperacional: 'Base operacional',
      veiculo: 'Veículo',
      idVeiculo: 'Veículo',
      equipe: 'Equipe',
      idEquipe: 'Equipe',
      observacoes: 'Observações',
      dataRealizacao: 'Data/Hora de realização',
      latitude: 'Latitude',
      longitude: 'Longitude',
      geolocalizacao: 'Geolocalização',
      colaboradoresInspecionados: 'Colaboradores inspecionados',
      colaboradores: 'Colaboradores inspecionados',
      totalInspecionados: 'Total de inspecionados',
      'total inspecionados': 'Total de inspecionados',
      status: 'Status',
      formulario: 'Formulário',
      acaoCorretiva: 'Ação corretiva',
      statusDesvio: 'Status do desvio',
      prazo: 'Prazo',
      idDesvio: 'Desvio'
    };

    if (mapa[clean]) return mapa[clean];
    const separada = clean.replace(/([A-Z])/g, ' $1').trim().toLowerCase();
    return separada.charAt(0).toUpperCase() + separada.slice(1);
  }

  protected formatAuthor(autor?: { id?: string; nome?: string } | string | null): string {
    return this.userLookup.resolverNome(autor, 'Sistema');
  }

  protected getOldValue(diff: HistoricoInspecaoAlteracaoItem): unknown {
    return diff.valorAnterior ?? null;
  }

  protected getNewValue(diff: HistoricoInspecaoAlteracaoItem): unknown {
    return diff.valorNovo ?? null;
  }

  protected changeKind(diff: HistoricoInspecaoAlteracaoItem): 'alteracao' | 'inclusao' | 'remocao' {
    const oldMissing = this.isMissing(this.getOldValue(diff));
    const newMissing = this.isMissing(this.getNewValue(diff));

    if (oldMissing && !newMissing) return 'inclusao';
    if (!oldMissing && newMissing) return 'remocao';
    return 'alteracao';
  }

  protected oldDisplayValue(diff: HistoricoInspecaoAlteracaoItem): string {
    const oldValue = this.getOldValue(diff);
    return this.isMissing(oldValue) ? 'não informado' : this.formatValue(oldValue, diff.caminho);
  }

  protected newDisplayValue(diff: HistoricoInspecaoAlteracaoItem): string {
    const kind = this.changeKind(diff);
    if (kind === 'inclusao') {
      return `incluído — ${this.formatValue(this.getNewValue(diff), diff.caminho)}`;
    }
    if (kind === 'remocao') return 'removido';

    const newValue = this.getNewValue(diff);
    return this.isMissing(newValue) ? 'não informado' : this.formatValue(newValue, diff.caminho);
  }

  protected formatValue(value: unknown, caminho = ''): string {
    if (this.isMissing(value)) return 'não informado';

    if (typeof value === 'object' && value !== null) {
      const rec = value as Record<string, unknown>;
      if (typeof rec['nome'] === 'string') return rec['nome'];
      if (typeof rec['placa'] === 'string') return rec['placa'];
      if (typeof rec['codigo'] === 'string') return rec['codigo'];
      if (typeof rec['titulo'] === 'string') return rec['titulo'];
      return JSON.stringify(value);
    }

    const stringValue = String(value).trim();

    if (/^\d{4}-\d{2}-\d{2}/.test(stringValue) || caminho.toLowerCase().includes('data')) {
      if (/^\d{4}-\d{2}-\d{2}$/.test(stringValue)) {
        const [year, month, day] = stringValue.split('-');
        return `${day}/${month}/${year}`;
      }

      const parsedDate = new Date(stringValue);
      if (!Number.isNaN(parsedDate.getTime())) {
        const day = String(parsedDate.getDate()).padStart(2, '0');
        const month = String(parsedDate.getMonth() + 1).padStart(2, '0');
        const year = parsedDate.getFullYear();
        const hours = String(parsedDate.getHours()).padStart(2, '0');
        const minutes = String(parsedDate.getMinutes()).padStart(2, '0');
        return `${day}/${month}/${year} ${hours}:${minutes}`;
      }
    }

    return stringValue;
  }

  protected isInitialEvent(event: HistoricoInspecaoEvento): boolean {
    return event.tipo === 'REGISTRO_INICIAL';
  }

  protected alterations(event: HistoricoInspecaoEvento): HistoricoInspecaoAlteracaoItem[] {
    const diffs = Array.isArray(event.payload?.alteracoes) ? event.payload.alteracoes : [];
    return diffs.filter((d) => !this.isIgnoredField(d.caminho));
  }

  private isIgnoredField(caminho: string): boolean {
    if (!caminho) return true;
    const clean = (caminho.split('.').at(-1) ?? caminho).trim().toLowerCase();
    const idKeys = [
      'id',
      'idinspecao',
      'idtipoinspecao',
      'idprocesso',
      'idbaseoperacional',
      'idveiculo',
      'idequipe',
      'idinspetor',
      'idformulario',
    ];
    if (idKeys.includes(clean)) return true;

    const auditKeys = [
      'hash',
      'hashanterior',
      'sequencia',
      'criadoem',
      'atualizadoem',
      'versao',
    ];
    return auditKeys.includes(clean);
  }

  protected extractSnapshots(event: HistoricoInspecaoEvento): SnapshotDisplayItem[] {
    const snapshots = event.payload?.snapshots;
    if (!snapshots || Array.isArray(snapshots) || typeof snapshots !== 'object') return [];

    const displayItems: SnapshotDisplayItem[] = [];
    Object.entries(snapshots).forEach(([key, value]) => {
      if (this.isIgnoredField(key)) return;
      if (this.isMissing(value)) return;
      displayItems.push({ rotulo: this.fieldLabel(key), valor: this.formatValue(value, key) });
    });

    return displayItems;
  }

  protected hasSnapshots(event: HistoricoInspecaoEvento): boolean {
    return this.isInitialEvent(event) && this.extractSnapshots(event).length > 0;
  }

  private sequenceOf(event: HistoricoInspecaoEvento): number {
    const sequence = Number(event.sequencia);
    return Number.isFinite(sequence) ? sequence : Number.MAX_SAFE_INTEGER;
  }

  private dateOf(event: HistoricoInspecaoEvento): number {
    const date = new Date(event.ocorridoEm).getTime();
    return Number.isFinite(date) ? date : Number.MAX_SAFE_INTEGER;
  }

  private isMissing(value: unknown): boolean {
    return value === null || value === undefined || value === '';
  }
}
