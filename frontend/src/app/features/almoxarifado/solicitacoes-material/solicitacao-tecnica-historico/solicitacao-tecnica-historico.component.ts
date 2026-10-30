import { CommonModule } from '@angular/common';
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
  HistoricoAlteracaoItem,
  HistoricoSolicitacaoEvento,
  HistoricoSolicitacaoResponse,
} from '../../../../core/models/solicitacao-material-tecnica.models';
import { ObrasService } from '../../../../core/services/obras.service';
import { RhService } from '../../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../../core/services/solicitacao-material-tecnica.service';
import { UsuarioLookupService } from '../../../../core/services/usuario-lookup.service';
import {
  campoLabel,
  eventoIcon,
  eventoLabel,
  eventoTone,
  extrairIdItemDoCaminho,
  formatarAlmoxarifado,
  separacaoLabel,
  statusLabel,
  tipoObraLabel,
  tipoTecnicoDescricaoCompleta,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel,
} from '../solicitacao-tecnica-presentation';

export interface SnapshotDisplayItem {
  rotulo: string;
  valor: string;
}

export interface HistoricoMaterialDisplay {
  id?: string;
  codigo?: string;
  descricao: string;
  quantidade?: string;
  tipoTecnico?: string;
  numeroSerie?: string;
}

export interface HistoricoMaterialAlteracaoItem {
  campo: string;
  tipo: 'alteracao' | 'inclusao' | 'remocao';
  anterior?: string;
  novo?: string;
  detalhe?: string;
}

export interface HistoricoMaterialModificadoDisplay {
  itemId: string;
  codigo?: string;
  descricao: string;
  numeroSerie?: string;
  tipoTecnico?: string;
  acaoPrincipal: 'inclusao' | 'remocao' | 'alteracao';
  mudancas: HistoricoMaterialAlteracaoItem[];
}

@Component({
  selector: 'app-solicitacao-tecnica-historico',
  standalone: true,
  imports: [CommonModule, CesAsyncStateComponent, CesButtonComponent],
  templateUrl: './solicitacao-tecnica-historico.component.html',
  styleUrl: './solicitacao-tecnica-historico.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SolicitacaoTecnicaHistoricoComponent implements OnInit {
  private readonly service = inject(SolicitacaoMaterialTecnicaService);
  private readonly notifications = inject(NotificationService);
  private readonly userLookup = inject(UsuarioLookupService);
  private readonly rhService = inject(RhService, { optional: true });
  private readonly obrasService = inject(ObrasService, { optional: true });
  private readonly cdr = inject(ChangeDetectorRef);

  @Input()
  set idSolicitacao(val: string) {
    this._idSolicitacao.set(val || '');
  }
  get idSolicitacao(): string {
    return this._idSolicitacao();
  }
  private readonly _idSolicitacao = signal<string>('');

  @Input()
  set ocultarObra(val: boolean) {
    this._ocultarObra.set(Boolean(val));
  }
  get ocultarObra(): boolean {
    return this._ocultarObra();
  }
  protected readonly _ocultarObra = signal<boolean>(false);

  protected readonly page = signal(0);
  protected readonly response = signal<HistoricoSolicitacaoResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  protected readonly totalPages = computed(() => this.response()?.page.totalPages ?? 1);
  protected readonly equipesMap = signal<Map<string, string>>(new Map());
  protected readonly obrasMap = signal<Map<string, string>>(new Map());
  protected readonly expandedMaterials = signal<Set<string>>(new Set<string>());

  /** A sequência do evento é a fonte de ordenação da linha do tempo. */
  protected readonly sortedEvents = computed(() =>
    [...(this.response()?.eventos ?? [])].sort((a, b) => {
      const sequenceDifference = this.sequenceOf(a) - this.sequenceOf(b);
      return sequenceDifference || this.dateOf(a) - this.dateOf(b);
    }),
  );

  /** A integridade informa a cadeia, mas nunca bloqueia a leitura do payload. */
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
    this.loadLookups();
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

  protected toggleMaterials(eventId: string): void {
    this.expandedMaterials.update((current) => {
      const next = new Set(current);
      if (next.has(eventId)) {
        next.delete(eventId);
      } else {
        next.add(eventId);
      }
      return next;
    });
  }

  protected isMaterialsExpanded(eventId: string): boolean {
    return this.expandedMaterials().has(eventId);
  }

  protected hasTipoTecnicoInList(list: HistoricoMaterialDisplay[]): boolean {
    return list.some((item) => Boolean(item.tipoTecnico));
  }

  protected hasTipoTecnicoInModList(list: HistoricoMaterialModificadoDisplay[]): boolean {
    return list.some((item) => Boolean(item.tipoTecnico));
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
    if (status === 'INTEGRO') return 'Todos os eventos foram verificados e a sequência está íntegra.';
    if (status === 'VIOLADO') return 'A sequência apresenta inconsistências e exige análise.';
    return 'Baseline legado: a cadeia não possui verificação disponível. Isso não indica corrupção.';
  }

  protected eventLabel = eventoLabel;
  protected eventIcon = eventoIcon;
  protected eventTone = eventoTone;
  protected typeLabel = tipoTecnicoLabel;
  protected typeIcon = tipoTecnicoIcon;
  protected typeIsAdicao = tipoTecnicoEhAdicao;
  protected fieldLabel = campoLabel;

  protected formatAuthor(autor?: { id?: string; nome?: string } | string | null): string {
    return this.userLookup.resolverNome(autor, 'Sistema');
  }

  /** Lê o contrato atual e, se necessário, o nome legado do valor. */
  protected getOldValue(diff: HistoricoAlteracaoItem): unknown {
    return diff.valorAnterior ?? diff.anterior ?? null;
  }

  /** Lê o contrato atual e, se necessário, o nome legado do valor. */
  protected getNewValue(diff: HistoricoAlteracaoItem): unknown {
    return diff.valorNovo ?? diff.novo ?? null;
  }

  protected itemId(caminho: string): string | null {
    return extrairIdItemDoCaminho(caminho);
  }

  protected itemDescription(event: HistoricoSolicitacaoEvento, caminho: string): string | null {
    const id = this.itemId(caminho);
    if (!id) return null;
    const snapshots = event.payload?.snapshots;
    if (snapshots && !Array.isArray(snapshots) && this.isRecord(snapshots)) {
      const itens = snapshots['itens'];
      if (Array.isArray(itens)) {
        const item = itens.find((candidate) => this.isRecord(candidate) && candidate['id'] === id);
        if (this.isRecord(item)) {
          const material = item['material'] ?? item['snapshot'];
          if (!this.isMissing(material)) {
            return this.formatMaterialValue(material);
          }
        }
      }
    }
    return null;
  }

  /** Oculta IDs técnicos e campos imutáveis que não mudam pós-criação. */
  private isIgnoredField(caminho: string): boolean {
    if (!caminho) return true;
    const clean = (caminho.split('.').at(-1) ?? caminho).trim().toLowerCase();

    // Identificadores técnicos a omitir
    const idKeys = [
      'id',
      'idsolicitacao',
      'iditem',
      'idmaterial',
      'idobra',
      'idequipe',
      'idequipesaque',
      'idalmoxarifado',
      'idalmoxarifadoatendimento',
      'idusuario',
      'idrequisitante',
      'idaprovador',
      'identregadoralmox',
      'idagente',
      'idagenteresponsavel',
      'idagentereserva',
      'idoperacao',
      'idoperacaooriginal',
      'idusuarioencerramento',
      'idcolaboradorsaque',
      'idocorrencia',
    ];
    if (idKeys.includes(clean)) return true;

    // Campos imutáveis pós-criação ou metadados internos de auditoria
    const auditAndImmutableKeys = [
      'versao',
      'versaoesperada',
      'escopo',
      'hash',
      'hashanterior',
      'sequencia',
      'comando',
      'origem',
      'contexto',
      'contextosolicitacao',
      'datasolicitacao',
      'datacriacao',
      'registradaem',
      'atualizadaem',
      'requisitante',
      'tipotecnico',
      'tipoobra',
      'tipoobrasnapshot',
      'encerradaem',
    ];
    if (auditAndImmutableKeys.includes(clean)) return true;

    // Propriedade interna de id dentro de itens[...]
    if (/^itens\[[^\]]+\]\.id(?:[A-Z].*)?$/i.test(caminho)) {
      return true;
    }

    return false;
  }

  /** Identifica se o evento é o registro inicial (criação da solicitação). */
  protected isInitialEvent(event: HistoricoSolicitacaoEvento): boolean {
    if (
      Number(event.sequencia) === 1 ||
      event.tipo === 'SOLICITACAO_CRIADA' ||
      event.tipo === 'LEGACY_BASELINE' ||
      event.tipo === 'SUBMISSAO_CRIADA'
    ) {
      return true;
    }
    const events = this.sortedEvents();
    if (events.length > 0 && events[0].id === event.id) {
      const isFirst = Number(event.sequencia) <= 1 || !event.sequencia;
      if (isFirst) return true;
    }
    return false;
  }

  /** Nunca pressupõe que um evento tenha alterações ou que elas sejam únicas. */
  protected alterations(event: HistoricoSolicitacaoEvento): HistoricoAlteracaoItem[] {
    const diffs = Array.isArray(event.payload?.alteracoes) ? event.payload.alteracoes : [];
    return diffs.filter((d) => {
      if (this.isIgnoredField(d.caminho)) return false;
      if (this.ocultarObra && this.isObraField(d.caminho)) return false;
      return true;
    });
  }

  /** Verifica se uma alteração é estritamente relacionada a materiais/itens. */
  protected isMaterialDiff(diff: HistoricoAlteracaoItem): boolean {
    if (!diff?.caminho) return false;
    return (
      this.isItemPath(diff.caminho) ||
      diff.caminho.startsWith('itens') ||
      diff.caminho === 'material'
    );
  }

  /** Retorna apenas as alterações gerais (não relacionadas a materiais). */
  protected generalAlterations(event: HistoricoSolicitacaoEvento): HistoricoAlteracaoItem[] {
    return this.alterations(event).filter((diff) => !this.isMaterialDiff(diff));
  }

  /** Retorna apenas as alterações relacionadas a materiais. */
  protected materialAlterations(event: HistoricoSolicitacaoEvento): HistoricoAlteracaoItem[] {
    return this.alterations(event).filter((diff) => this.isMaterialDiff(diff));
  }

  private isObraField(caminho: string): boolean {
    const clean = caminho.split('.').at(-1) ?? caminho;
    return ['obra', 'idObra', 'tipoObra', 'tipoObraSnapshot'].includes(clean);
  }

  protected changeKind(diff: HistoricoAlteracaoItem): 'alteracao' | 'inclusao' | 'remocao' {
    const oldMissing = this.isMissing(this.getOldValue(diff));
    const newMissing = this.isMissing(this.getNewValue(diff));

    if (oldMissing && !newMissing) return 'inclusao';
    if (!oldMissing && newMissing) return 'remocao';
    return 'alteracao';
  }

  protected oldDisplayValue(diff: HistoricoAlteracaoItem): string {
    const oldValue = this.getOldValue(diff);
    return this.isMissing(oldValue) ? 'não informado' : this.formatValue(oldValue, diff.caminho);
  }

  protected newDisplayValue(diff: HistoricoAlteracaoItem): string {
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

    if (this.isItemPath(caminho) && !this.itemField(caminho)) {
      return this.formatItemValue(value);
    }

    if (this.itemField(caminho) === 'material' || caminho === 'material') {
      return this.formatMaterialValue(value);
    }

    const cleanField = caminho.split('.').at(-1) ?? caminho;

    if (
      cleanField === 'tipoTecnico' ||
      cleanField === 'tipoMovimentacao' ||
      caminho === 'tipo' ||
      caminho === 'tipoTecnico'
    ) {
      return tipoTecnicoDescricaoCompleta(value as any);
    }

    if (cleanField === 'tipoObra' || cleanField === 'tipoObraSnapshot' || caminho === 'tipoObra') {
      return tipoObraLabel(String(value));
    }

    if (cleanField === 'status') return statusLabel(value as any);
    if (cleanField === 'statusSeparacao') return separacaoLabel(value as any);

    if (cleanField === 'usuario' || cleanField === 'idUsuario') {
      return this.userLookup.resolverNome(value as any, 'Sistema');
    }

    if (
      cleanField === 'almoxarifado' ||
      cleanField === 'idAlmoxarifadoAtendimento' ||
      cleanField === 'idAlmoxarifado'
    ) {
      if (this.isRecord(value)) {
        const snap = value['snapshot'] ?? value['nomeIdentificador'] ?? value['nome'];
        if (snap) return formatarAlmoxarifado(String(snap));
      }
      return formatarAlmoxarifado(String(value));
    }

    if (cleanField === 'obra' || cleanField === 'idObra') {
      if (this.isRecord(value)) {
        const snap = value['snapshot'] ?? value['titulo'] ?? value['numero'];
        if (snap && typeof snap === 'string' && snap.trim()) return snap;
        if (typeof value['id'] === 'string') {
          const id = value['id'];
          const cached = this.obrasMap().get(id);
          if (cached) return cached;
          return `Obra (${id.slice(0, 8)})`;
        }
      }
      if (typeof value === 'string') {
        const cached = this.obrasMap().get(value);
        if (cached) return cached;
        if (/^[0-9a-f]{8}-[0-9a-f]{4}/i.test(value)) {
          return `Obra (${value.slice(0, 8)})`;
        }
      }
    }

    if (cleanField === 'equipe' || cleanField === 'idEquipe' || cleanField === 'idEquipeSaque') {
      if (this.isRecord(value)) {
        const snap = value['snapshot'] ?? value['codigo'] ?? value['nome'];
        if (snap && typeof snap === 'string' && snap.trim()) return snap;
        if (typeof value['id'] === 'string') {
          const id = value['id'];
          const cached = this.equipesMap().get(id);
          if (cached) return cached;
          return `Equipe (${id.slice(0, 8)})`;
        }
      }
      if (typeof value === 'string') {
        const cached = this.equipesMap().get(value);
        if (cached) return cached;
        if (/^[0-9a-f]{8}-[0-9a-f]{4}/i.test(value)) {
          return `Equipe (${value.slice(0, 8)})`;
        }
      }
    }

    if (this.isRecord(value)) {
      if (typeof value['snapshot'] === 'string' && value['snapshot'].trim()) return value['snapshot'];
      if (value['codigo'] && value['descricao']) {
        return `${value['codigo']} — ${value['descricao']}`;
      }
      if (typeof value['descricao'] === 'string') return value['descricao'];
      if (typeof value['nome'] === 'string') return value['nome'];
      if (typeof value['nomeIdentificador'] === 'string') return formatarAlmoxarifado(value['nomeIdentificador']);
      if (typeof value['id'] === 'string' && Object.keys(value).length === 1) {
        const id = value['id'];
        const team = this.equipesMap().get(id);
        if (team) return team;
        const obra = this.obrasMap().get(id);
        if (obra) return obra;
        return `Ref (${id.slice(0, 8)})`;
      }
      return this.stringifyValue(value);
    }

    const stringValue = String(value).trim();
    if (this.isQuantityPath(caminho)) return this.formatDecimalString(stringValue);

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

    if (stringValue.startsWith('ALMOXARIFADO_')) {
      return formatarAlmoxarifado(stringValue);
    }

    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(stringValue)) {
      return `Ref (${stringValue.slice(0, 8)})`;
    }

    return stringValue;
  }

  protected hasSnapshots(event: HistoricoSolicitacaoEvento): boolean {
    if (!this.isInitialEvent(event)) return false;
    return this.extractSnapshots(event).length > 0 || this.extractMaterialItems(event).length > 0;
  }

  /** Extrai campos gerais de snapshot omitindo itens, IDs e propriedades imutáveis. */
  protected extractSnapshots(event: HistoricoSolicitacaoEvento): SnapshotDisplayItem[] {
    const snapshots = event.payload?.snapshots;
    if (!snapshots || Array.isArray(snapshots) || !this.isRecord(snapshots)) return [];

    const displayItems: SnapshotDisplayItem[] = [];

    Object.entries(snapshots).forEach(([key, value]) => {
      if (key === 'itens') return;
      if (this.isIgnoredField(key)) return;
      if (this.ocultarObra && this.isObraField(key)) return;
      if (this.isMissing(value)) return;
      displayItems.push({ rotulo: campoLabel(key), valor: this.formatValue(value, key) });
    });

    return displayItems;
  }

  /** Extrai lista estruturada de materiais para exibição expansível. */
  protected extractMaterialItems(event: HistoricoSolicitacaoEvento): HistoricoMaterialDisplay[] {
    const snapshots = event.payload?.snapshots;
    if (!snapshots) return [];

    let rawItens: unknown[] = [];
    if (Array.isArray(snapshots)) {
      rawItens = snapshots;
    } else if (this.isRecord(snapshots) && Array.isArray(snapshots['itens'])) {
      rawItens = snapshots['itens'];
    }

    if (!rawItens.length) return [];

    return rawItens
      .filter((item) => this.isRecord(item))
      .map((item, index) => {
        const record = item as Record<string, unknown>;
        const mat = record['material'] ?? record['snapshot'];
        const { codigo, descricao } = this.parseMaterialInfo(mat, index);
        const qtdRaw = record['quantidade'] ?? record['qtd'];
        const quantidade = this.isMissing(qtdRaw)
          ? ''
          : this.formatDecimalString(String(qtdRaw).trim());
        const tipoTecnicoRaw = record['tipoTecnico'] ?? record['tipoMovimentacao'];
        const tipoTecnico =
          typeof tipoTecnicoRaw === 'string' && tipoTecnicoRaw.trim()
            ? tipoTecnicoDescricaoCompleta(tipoTecnicoRaw as any)
            : undefined;
        const numeroSerie =
          typeof record['numeroSerie'] === 'string' && record['numeroSerie'].trim()
            ? record['numeroSerie'].trim()
            : typeof record['numSerie'] === 'string' && record['numSerie'].trim()
              ? record['numSerie'].trim()
              : undefined;

        return {
          id: typeof record['id'] === 'string' ? record['id'] : undefined,
          codigo,
          descricao,
          quantidade,
          tipoTecnico,
          numeroSerie,
        };
      });
  }

  private parseMaterialInfo(
    mat: unknown,
    index: number,
  ): { codigo?: string; descricao: string } {
    if (this.isMissing(mat)) {
      return { descricao: `Item ${index + 1}` };
    }

    if (this.isRecord(mat)) {
      const cod = typeof mat['codigo'] === 'string' ? mat['codigo'].trim() : undefined;
      const desc = typeof mat['descricao'] === 'string' ? mat['descricao'].trim() : undefined;
      if (cod && desc) return { codigo: cod, descricao: desc };
      if (desc) return { descricao: desc };
      if (typeof mat['snapshot'] === 'string' && mat['snapshot'].trim()) {
        return this.splitCodeAndDescription(mat['snapshot'].trim(), index);
      }
      if (typeof mat['nome'] === 'string' && mat['nome'].trim()) {
        return { descricao: mat['nome'].trim() };
      }
      if (typeof mat['id'] === 'string') {
        return { descricao: `Material` };
      }
    }

    if (typeof mat === 'string') {
      return this.splitCodeAndDescription(mat.trim(), index);
    }

    return { descricao: `Item ${index + 1}` };
  }

  private splitCodeAndDescription(
    str: string,
    index: number,
  ): { codigo?: string; descricao: string } {
    if (!str) return { descricao: `Item ${index + 1}` };
    if (/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(str)) {
      return { descricao: `Material` };
    }
    const match = str.match(/^([A-Za-z0-9_-]+)\s*[-–—]\s*(.+)$/);
    if (match) {
      return { codigo: match[1].trim(), descricao: match[2].trim() };
    }
    return { descricao: str };
  }

  protected hasDetails(event: HistoricoSolicitacaoEvento): boolean {
    if (this.isInitialEvent(event)) {
      return Boolean(
        event.justificativa ||
          this.alterations(event).length ||
          this.extractSnapshots(event).length ||
          this.extractMaterialItems(event).length ||
          (event.payload?.correlacoes && Object.keys(event.payload.correlacoes).length),
      );
    }
    return Boolean(
      event.justificativa ||
        this.generalAlterations(event).length ||
        this.extractMaterialModifications(event).length ||
        (event.payload?.correlacoes && Object.keys(event.payload.correlacoes).length),
    );
  }

  protected load(): void {
    const id = this.idSolicitacao;
    if (!id) return;
    this.loading.set(true);
    this.error.set('');
    this.service.historico(id, this.page()).subscribe({
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

  private loadLookups(): void {
    if (this.rhService) {
      this.rhService.listarEquipes({ ativo: true, size: 1000 }).subscribe({
        next: (res) => {
          const map = new Map<string, string>();
          (res.content ?? []).forEach((eq) => {
            const desc = eq.tipoEquipe?.nome ? ` — ${eq.tipoEquipe.nome}` : '';
            const label = eq.codigo ? `${eq.codigo}${desc}` : eq.id;
            map.set(eq.id, label);
          });
          this.equipesMap.set(map);
          this.cdr.markForCheck();
        },
        error: () => {},
      });
    }
  }

  private sequenceOf(event: HistoricoSolicitacaoEvento): number {
    const sequence = Number(event.sequencia);
    return Number.isFinite(sequence) ? sequence : Number.MAX_SAFE_INTEGER;
  }

  private dateOf(event: HistoricoSolicitacaoEvento): number {
    const date = new Date(event.ocorridoEm).getTime();
    return Number.isFinite(date) ? date : Number.MAX_SAFE_INTEGER;
  }

  private isMissing(value: unknown): boolean {
    return value === null || value === undefined || value === '';
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  }

  protected isItemPath(caminho: string): boolean {
    return this.itemId(caminho) !== null;
  }

  protected itemField(caminho: string): string | null {
    const match = caminho.match(/^itens\[[^\]]+\]\.(.+)$/);
    return match?.[1]?.split('.').at(-1) ?? null;
  }

  protected findItemInSnapshots(
    itemId: string,
    event?: HistoricoSolicitacaoEvento,
  ): Record<string, unknown> | null {
    if (!itemId) return null;

    if (event?.payload?.snapshots && this.isRecord(event.payload.snapshots)) {
      const itens = event.payload.snapshots['itens'];
      if (Array.isArray(itens)) {
        const found = itens.find((c) => this.isRecord(c) && c['id'] === itemId);
        if (this.isRecord(found)) return found as Record<string, unknown>;
      }
    }

    const allEvents = this.response()?.eventos ?? [];
    for (const evt of allEvents) {
      if (evt.payload?.snapshots && this.isRecord(evt.payload.snapshots)) {
        const itens = evt.payload.snapshots['itens'];
        if (Array.isArray(itens)) {
          const found = itens.find((c) => this.isRecord(c) && c['id'] === itemId);
          if (this.isRecord(found)) return found as Record<string, unknown>;
        }
      }
    }

    return null;
  }

  private resolveMaterialFromDiffs(itemId: string): { codigo?: string; descricao: string } | null {
    const allEvents = this.response()?.eventos ?? [];
    for (const evt of allEvents) {
      const diffs = Array.isArray(evt.payload?.alteracoes) ? evt.payload.alteracoes : [];
      for (const d of diffs) {
        if (this.itemId(d.caminho) === itemId) {
          if (this.isRecord(d.valorNovo)) {
            const mat = d.valorNovo['material'] ?? d.valorNovo['snapshot'];
            if (mat) {
              const parsed = this.parseMaterialInfo(mat, 0);
              if (parsed.descricao && !parsed.descricao.startsWith('Item ')) return parsed;
            }
          }
          if (this.isRecord(d.valorAnterior)) {
            const mat = d.valorAnterior['material'] ?? d.valorAnterior['snapshot'];
            if (mat) {
              const parsed = this.parseMaterialInfo(mat, 0);
              if (parsed.descricao && !parsed.descricao.startsWith('Item ')) return parsed;
            }
          }
          if (d.caminho.endsWith('.material')) {
            const mat = d.valorNovo ?? d.valorAnterior;
            if (mat) {
              const parsed = this.parseMaterialInfo(mat, 0);
              if (parsed.descricao && !parsed.descricao.startsWith('Item ')) return parsed;
            }
          }
        }
      }
    }
    return null;
  }

  protected subFieldLabel(subField: string): string {
    const labels: Record<string, string> = {
      quantidade: 'Quantidade',
      material: 'Material',
      quantidadeEntregaLiquida: 'Quantidade entregue',
      quantidadeDevolucaoLiquida: 'Quantidade devolvida',
      quantidadePendente: 'Quantidade pendente',
      tipoTecnico: 'Tipo técnico',
      tipoMovimentacao: 'Tipo de movimento',
      numeroSerie: 'Número de série',
      numSerie: 'Número de série',
    };
    return labels[subField] ?? campoLabel(subField);
  }

  private extractFieldFromDiffs(itemDiffs: HistoricoAlteracaoItem[], keys: string[]): unknown {
    for (const d of itemDiffs) {
      const field = this.itemField(d.caminho);
      if (field && keys.includes(field)) {
        return this.getNewValue(d) ?? this.getOldValue(d);
      }
      if (this.isRecord(d.valorNovo)) {
        for (const k of keys) {
          if (d.valorNovo[k]) return d.valorNovo[k];
        }
      }
      if (this.isRecord(d.valorAnterior)) {
        for (const k of keys) {
          if (d.valorAnterior[k]) return d.valorAnterior[k];
        }
      }
    }
    return null;
  }

  private extractQuantityFromVal(val: unknown): string | null {
    if (!val) return null;
    if (this.isRecord(val)) {
      const q = val['quantidade'] ?? val['qtd'];
      if (!this.isMissing(q)) {
        return this.formatDecimalString(String(q).trim());
      }
    }
    return null;
  }

  /** Extrai alterações agrupadas por item para exibição no dropdown de materiais. */
  protected extractMaterialModifications(
    event: HistoricoSolicitacaoEvento,
  ): HistoricoMaterialModificadoDisplay[] {
    const diffs = this.materialAlterations(event);
    if (!diffs.length) return [];

    const grouped = new Map<string, HistoricoAlteracaoItem[]>();
    for (const diff of diffs) {
      const id = this.itemId(diff.caminho) ?? 'geral';
      const list = grouped.get(id) ?? [];
      list.push(diff);
      grouped.set(id, list);
    }

    const result: HistoricoMaterialModificadoDisplay[] = [];

    grouped.forEach((itemDiffs, itemId) => {
      const snapshotItem = this.findItemInSnapshots(itemId, event);
      const fromDiffs = this.resolveMaterialFromDiffs(itemId);

      let rawMat = snapshotItem ? (snapshotItem['material'] ?? snapshotItem['snapshot']) : null;
      if (!rawMat) {
        for (const d of itemDiffs) {
          if (d.caminho.endsWith('.material')) {
            rawMat = d.valorNovo ?? d.valorAnterior;
            break;
          }
          if (this.isRecord(d.valorNovo) && (d.valorNovo['material'] ?? d.valorNovo['snapshot'])) {
            rawMat = d.valorNovo['material'] ?? d.valorNovo['snapshot'];
            break;
          }
          if (this.isRecord(d.valorAnterior) && (d.valorAnterior['material'] ?? d.valorAnterior['snapshot'])) {
            rawMat = d.valorAnterior['material'] ?? d.valorAnterior['snapshot'];
            break;
          }
        }
      }

      const parsed = rawMat
        ? this.parseMaterialInfo(rawMat, result.length)
        : fromDiffs;
      const codigo = parsed?.codigo;
      const descricao =
        parsed?.descricao || (itemId !== 'geral' ? `Item (${itemId.slice(0, 8)})` : 'Item');

      const tipoTecnicoRaw =
        snapshotItem?.['tipoTecnico'] ??
        snapshotItem?.['tipoMovimentacao'] ??
        this.extractFieldFromDiffs(itemDiffs, ['tipoTecnico', 'tipoMovimentacao']);
      const tipoTecnico =
        typeof tipoTecnicoRaw === 'string' && tipoTecnicoRaw.trim()
          ? tipoTecnicoDescricaoCompleta(tipoTecnicoRaw as any)
          : undefined;

      const numeroSerieRaw =
        snapshotItem?.['numeroSerie'] ??
        snapshotItem?.['numSerie'] ??
        this.extractFieldFromDiffs(itemDiffs, ['numeroSerie', 'numSerie']);
      const numeroSerie =
        typeof numeroSerieRaw === 'string' && numeroSerieRaw.trim()
          ? numeroSerieRaw.trim()
          : undefined;

      let acaoPrincipal: 'inclusao' | 'remocao' | 'alteracao' = 'alteracao';
      const mudancas: HistoricoMaterialAlteracaoItem[] = [];

      for (const diff of itemDiffs) {
        const subField = this.itemField(diff.caminho);
        const isWholeItem = !subField || diff.caminho === 'itens' || diff.caminho === `itens[${itemId}]`;

        if (isWholeItem) {
          const kind = this.changeKind(diff);
          if (kind === 'inclusao') {
            acaoPrincipal = 'inclusao';
            const qtd = this.extractQuantityFromVal(this.getNewValue(diff));
            mudancas.push({
              campo: 'Item',
              tipo: 'inclusao',
              detalhe: qtd ? `Item incluído — quantidade: ${qtd}` : 'Item incluído',
            });
          } else if (kind === 'remocao') {
            acaoPrincipal = 'remocao';
            const qtd = this.extractQuantityFromVal(this.getOldValue(diff));
            mudancas.push({
              campo: 'Item',
              tipo: 'remocao',
              detalhe: qtd ? `Item removido (quantidade anterior: ${qtd})` : 'Item removido',
            });
          } else {
            mudancas.push({
              campo: 'Item',
              tipo: 'alteracao',
              anterior: this.oldDisplayValue(diff),
              novo: this.newDisplayValue(diff),
            });
          }
        } else {
          const label = this.subFieldLabel(subField);
          mudancas.push({
            campo: label,
            tipo: this.changeKind(diff),
            anterior: this.oldDisplayValue(diff),
            novo: this.newDisplayValue(diff),
          });
        }
      }

      result.push({
        itemId,
        codigo,
        descricao,
        numeroSerie,
        tipoTecnico,
        acaoPrincipal,
        mudancas,
      });
    });

    return result;
  }

  private isQuantityPath(caminho: string): boolean {
    return /(?:^|\.)quantidade(?:[A-Z][a-zA-Z]*)?$/.test(caminho) || /(?:^|\.)qtd$/.test(caminho);
  }

  /** Mantém a escala decimal recebida pela API e evita arredondamento binário. */
  private formatDecimalString(value: string): string {
    const match = value.match(/^(-?)(\d+)(?:\.(\d+))?$/);
    if (!match) return value;

    const [, sign, integerPart, fractionPart] = match;
    const groupedInteger = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
    return `${sign}${groupedInteger}${fractionPart ? `,${fractionPart}` : ''}`;
  }

  private formatMaterialValue(value: unknown): string {
    if (typeof value === 'string') return value;
    if (!this.isRecord(value)) return this.stringifyValue(value);
    if (typeof value['snapshot'] === 'string') return value['snapshot'];
    if (value['codigo'] && value['descricao']) {
      return `${value['codigo']} — ${value['descricao']}`;
    }
    if (typeof value['descricao'] === 'string') return value['descricao'];
    if (typeof value['nome'] === 'string') return value['nome'];
    if (typeof value['id'] === 'string') return value['id'];
    return this.stringifyValue(value);
  }

  private formatItemValue(value: unknown): string {
    if (!this.isRecord(value)) return this.formatValue(value);

    const details: string[] = [];
    if (value['material'] !== undefined && value['material'] !== null) {
      details.push(`material: ${this.formatMaterialValue(value['material'])}`);
    } else if (typeof value['snapshot'] === 'string') {
      details.push(value['snapshot']);
    }
    if (value['quantidade'] !== undefined && value['quantidade'] !== null) {
      details.push(`quantidade: ${this.formatValue(value['quantidade'], 'quantidade')}`);
    }
    if (typeof value['tipoTecnico'] === 'string') {
      details.push(`tipo técnico: ${tipoTecnicoDescricaoCompleta(value['tipoTecnico'])}`);
    }

    return details.length ? details.join(' · ') : this.stringifyValue(value);
  }

  private stringifyValue(value: unknown): string {
    const serialized = JSON.stringify(value);
    return serialized === undefined ? String(value) : serialized;
  }
}
