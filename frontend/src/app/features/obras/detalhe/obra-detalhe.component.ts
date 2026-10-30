import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ConfirmationService, MenuItem, MessageService } from 'primeng/api';
import { AutoCompleteModule } from 'primeng/autocomplete';
import { BreadcrumbModule } from 'primeng/breadcrumb';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { TextareaModule } from 'primeng/textarea';
import { TooltipModule } from 'primeng/tooltip';
import {
  CesAsyncStateComponent,
  CesButtonComponent,
  CesDialogShellComponent,
  CesStatusComponent
} from '@ces/ui';
import {
  AnexoObraResponse,
  ObraResponse,
  ObraResumoResponse,
  StatusObraResponse,
  SubprojetoResponse,
  SubprojetoVinculoRequest,
  TipoAnexoObraResponse,
  TipoVinculoSubprojeto
} from '../../../core/models/obras.models';
import { ProjecaoObraSolicitacoesResponse } from '../../../core/models/solicitacao-material-tecnica.models';
import { EquipeResponse } from '../../../core/models/rh.models';
import { getErrorMessage } from '../../../core/models/shared.models';
import { AuthService } from '../../../core/services/auth.service';
import { AnalisarComCopilotoComponent } from '../../../shared/components/analisar-com-copiloto/analisar-com-copiloto.component';
import { FavoritosService } from '../../../core/services/favoritos.service';
import { ObrasService } from '../../../core/services/obras.service';
import { RhService } from '../../../core/services/rh.service';
import { SolicitacaoMaterialTecnicaService } from '../../../core/services/solicitacao-material-tecnica.service';
import {
  statusLabel as statusSolicitacaoLabel,
  statusTone as statusSolicitacaoTone,
  tipoTecnicoEhAdicao,
  tipoTecnicoIcon,
  tipoTecnicoLabel
} from '../../almoxarifado/solicitacoes-material/solicitacao-tecnica-presentation';

import {
  TIPO_VINCULO_OPTIONS,
  formatarBytes,
  formatarDataHora,
  formatarDataParaApi,
  formatarMoeda,
  parseDataBrParaDate,
  statusClass,
  statusIcon,
  statusTone
} from '../obras-ui';

declare var L: any;

@Component({
  selector: 'app-obra-detalhe',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    AutoCompleteModule,
    BreadcrumbModule,
    ButtonModule,
    DatePickerModule,
    InputTextModule,
    SelectModule,
    TextareaModule,
    TooltipModule,
    CesAsyncStateComponent,
    CesButtonComponent,
    CesDialogShellComponent,
    CesStatusComponent,
    AnalisarComCopilotoComponent
  ],
  templateUrl: './obra-detalhe.component.html',
  styleUrl: './obra-detalhe.component.css'
})
export class ObraDetalheComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(ObrasService);
  private readonly rhService = inject(RhService);
  private readonly solicitacaoService = inject(SolicitacaoMaterialTecnicaService);
  private readonly auth = inject(AuthService);
  private readonly messages = inject(MessageService);
  private readonly confirmation = inject(ConfirmationService);
  private readonly sanitizer = inject(DomSanitizer);
  protected readonly favs = inject(FavoritosService);

  // Elemento do Mapa Leaflet
  private mapEl = viewChild<ElementRef<HTMLDivElement>>('mapEl');
  private leafletMap: any = null;

  // Permissões IAM
  protected readonly podeEscrever =
    this.auth.currentUser()?.permissoes?.includes('obras:obra:write') ?? false;
  protected readonly podeAnexar =
    this.auth.currentUser()?.permissoes?.includes('obras:anexos:write') ?? false;
  protected readonly podeLerAnexos =
    this.auth.currentUser()?.permissoes?.includes('obras:anexos:read') ?? false;

  // Sinais de Estado
  protected readonly obra = signal<ObraResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly dataEmissao = new Date();

  // Lookups e Dados Complementares
  protected readonly equipesDetalhadas = signal<EquipeResponse[]>([]);
  protected readonly statusList = signal<StatusObraResponse[]>([]);
  protected readonly tiposAnexosList = signal<TipoAnexoObraResponse[]>([]);
  protected readonly obrasParaVinculo = signal<ObraResumoResponse[]>([]);

  // Projeção de Solicitações Técnicas de Material
  protected readonly solicitacoesObra = signal<ProjecaoObraSolicitacoesResponse | null>(null);
  protected readonly solicitacoesLoading = signal(false);
  protected readonly totalSolicitacoesObra = computed(() => {
    const proj = this.solicitacoesObra();
    if (!proj) return 0;
    if (proj.tipoObra === 'CORRETIVA' || proj.tipoObra === 'PREVENTIVA') {
      return proj.ocorrencias.reduce((acc, oc) => acc + oc.solicitacoes.length, 0);
    }
    return proj.solicitacoes.length;
  });

  protected statusSolicitacaoLabel = statusSolicitacaoLabel;
  protected statusSolicitacaoTone = statusSolicitacaoTone;
  protected tipoTecnicoLabel = tipoTecnicoLabel;
  protected tipoTecnicoIcon = tipoTecnicoIcon;
  protected tipoTecnicoEhAdicao = tipoTecnicoEhAdicao;

  // Modal: Alteração Rápida de Status
  protected statusDialogVisible = signal(false);
  protected statusDialogLoading = signal(false);
  protected novoStatusId: string | null = null;
  protected novaDataFimEfetiva: Date | null = null;

  // Modal: Vincular Subprojeto
  protected vincularDialogVisible = signal(false);
  protected vincularLoading = signal(false);
  protected obraFilhaSelecionada = signal<ObraResumoResponse | null>(null);
  protected tipoVinculoSelecionado = TipoVinculoSubprojeto.KIT;
  protected readonly tipoVinculoOptions = TIPO_VINCULO_OPTIONS;

  // Modal: Upload de Anexo
  protected anexoDialogVisible = signal(false);
  protected anexoUploading = signal(false);
  protected anexoArquivoSelecionado: File | null = null;
  protected anexoTipoId: string | null = null;
  protected anexoObservacoes = '';

  // Modal: Pré-visualização de Anexo
  protected readonly showPreviewModal = signal(false);
  protected readonly previewNomeArquivo = signal('');
  protected readonly previewTipo = signal<'pdf' | 'image' | 'other'>('other');
  protected readonly loadingPreview = signal(false);
  protected readonly previewRawUrl = signal<string | null>(null);
  protected readonly previewUrl = signal<SafeResourceUrl | null>(null);

  // Breadcrumb
  protected breadcrumb: MenuItem[] = [
    { icon: 'pi pi-home', routerLink: '/home' },
    { label: 'Obras', routerLink: '/obras' },
    { label: 'Detalhes da Obra' }
  ];

  // Helpers de Template
  protected readonly formatarMoeda = formatarMoeda;
  protected readonly formatarBytes = formatarBytes;
  protected readonly formatarDataHora = formatarDataHora;
  protected readonly statusTone = statusTone;
  protected readonly statusIcon = statusIcon;
  protected readonly statusClass = statusClass;

  ngOnInit(): void {
    this.carregarLookups();
    const id = this.route.snapshot?.paramMap?.get('id');
    if (id === 'novo' || id === 'nova') {
      this.router.navigate(['/obras/nova']);
      return;
    }
    if (id) {
      this.carregarObra(id);
    } else {
      this.router.navigate(['/obras']);
    }
    this.route.paramMap?.subscribe(params => {
      const pId = params.get('id');
      if (pId === 'novo' || pId === 'nova') {
        this.router.navigate(['/obras/nova']);
        return;
      }
      if (pId && pId !== id) {
        this.carregarObra(pId);
      }
    });
  }

  ngOnDestroy(): void {
    this.fecharPreviewModal();
    this.destruirMapa();
  }

  protected carregarObra(id: string): void {
    this.loading.set(true);
    this.service.buscarObraPorId(id).subscribe({
      next: (dados) => {
        this.obra.set(dados);
        this.loading.set(false);
        this.breadcrumb = [
          { icon: 'pi pi-home', routerLink: '/home' },
          { label: 'Obras', routerLink: '/obras' },
          { label: `Obra ${dados.numero}` }
        ];

        // Carrega equipes vinculadas se houver
        if (dados.equipes && dados.equipes.length > 0) {
          this.carregarEquipes(dados.equipes);
        }

        // Carrega solicitações de material vinculadas à obra
        this.carregarSolicitacoesMaterial(dados.id);

        // Renderiza mapa se tiver coordenadas
        if (dados.latitude && dados.longitude) {
          setTimeout(() => this.renderizarMapa(dados.latitude!, dados.longitude!, dados.numero), 250);
        }
      },
      error: (error) => {
        this.loading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Erro ao carregar detalhes',
          detail: getErrorMessage(error)
        });
        this.router.navigate(['/obras']);
      }
    });
  }

  protected carregarSolicitacoesMaterial(idObra: string): void {
    this.solicitacoesLoading.set(true);
    this.solicitacaoService.obterProjecaoObra(idObra).subscribe({
      next: (projecao) => {
        this.solicitacoesObra.set(projecao);
        this.solicitacoesLoading.set(false);
      },
      error: () => {
        this.solicitacoesObra.set(null);
        this.solicitacoesLoading.set(false);
      }
    });
  }

  protected editar(): void {
    const id = this.obra()?.id;
    if (id) this.router.navigate(['/obras', id, 'editar']);
  }

  protected voltar(): void {
    this.router.navigate(['/obras']);
  }

  protected solicitarMateriais(): void {
    const o = this.obra();
    if (!o) return;
    void this.router.navigate(['/almoxarifado/solicitacoes-tecnicas/nova'], {
      queryParams: { idObra: o.id }
    });
  }

  protected imprimir(): void {
    window.print();
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Alteração Rápida de Status
  // ═══════════════════════════════════════════════════════════════════════

  protected abrirAlteracaoStatus(): void {
    const o = this.obra();
    if (!o) return;
    this.novoStatusId = o.statusId;
    this.novaDataFimEfetiva = o.dataFimEfetiva ? parseDataBrParaDate(o.dataFimEfetiva) : null;
    this.statusDialogVisible.set(true);
  }

  protected salvarAlteracaoStatus(): void {
    const o = this.obra();
    if (!o || !this.novoStatusId) return;

    this.statusDialogLoading.set(true);
    const dataFimStr = formatarDataParaApi(this.novaDataFimEfetiva);

    this.service.alterarStatus(o.id, this.novoStatusId, dataFimStr).subscribe({
      next: (atualizada) => {
        this.statusDialogLoading.set(false);
        this.statusDialogVisible.set(false);
        this.messages.add({
          severity: 'success',
          summary: 'Status atualizado',
          detail: `Status alterado para "${atualizada.statusNome}".`
        });
        this.carregarObra(o.id);
      },
      error: (error) => {
        this.statusDialogLoading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Erro ao alterar status',
          detail: getErrorMessage(error)
        });
      }
    });
  }

  protected statusSelecionadoEhConcluido(): boolean {
    if (!this.novoStatusId) return false;
    const st = this.statusList().find((s) => s.id === this.novoStatusId);
    return st?.concluida ?? false;
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Gestão de Subprojetos (Kits e Medidores)
  // ═══════════════════════════════════════════════════════════════════════

  protected abrirModalVincularSubprojeto(): void {
    this.obraFilhaSelecionada.set(null);
    this.tipoVinculoSelecionado = TipoVinculoSubprojeto.KIT;
    this.vincularDialogVisible.set(true);
  }

  protected buscarObrasParaFilha(event: { query: string }): void {
    const termo = event.query.trim();
    this.service.listarObras({ buscaGlobal: termo, apenasPrincipais: true, size: 15 }).subscribe({
      next: (res) => {
        const idAtual = this.obra()?.id;
        const jaVinculadas = new Set((this.obra()?.subprojetos ?? []).map((s) => s.id));
        const filtradas = (res.content ?? []).filter(
          (o) => o.id !== idAtual && !jaVinculadas.has(o.id)
        );
        this.obrasParaVinculo.set(filtradas);
      }
    });
  }

  protected vincularSubprojeto(): void {
    const paiId = this.obra()?.id;
    const filha = this.obraFilhaSelecionada();
    if (!paiId || !filha) return;

    this.vincularLoading.set(true);
    const req: SubprojetoVinculoRequest = {
      idObraFilha: filha.id,
      tipoVinculo: this.tipoVinculoSelecionado
    };

    this.service.vincularSubprojeto(paiId, req).subscribe({
      next: () => {
        this.vincularLoading.set(false);
        this.vincularDialogVisible.set(false);
        this.messages.add({
          severity: 'success',
          summary: 'Subprojeto vinculado',
          detail: `A obra ${filha.numero} foi vinculada como subprojeto ${req.tipoVinculo}.`
        });
        this.carregarObra(paiId);
      },
      error: (error) => {
        this.vincularLoading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Erro ao vincular subprojeto',
          detail: getErrorMessage(error)
        });
      }
    });
  }

  protected desvincularSubprojeto(sub: SubprojetoResponse): void {
    const paiId = this.obra()?.id;
    if (!paiId) return;

    this.confirmation.confirm({
      message: `Deseja desvincular o subprojeto "${sub.numero} - ${sub.titulo}" da obra principal?`,
      header: 'Desvincular Subprojeto',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Sim, Desvincular',
      rejectLabel: 'Cancelar',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.loading.set(true);
        this.service.desvincularSubprojeto(paiId, sub.id).subscribe({
          next: () => {
            this.loading.set(false);
            this.messages.add({
              severity: 'success',
              summary: 'Subprojeto desvinculado',
              detail: `A obra ${sub.numero} foi desvinculada.`
            });
            this.carregarObra(paiId);
          },
          error: (error) => {
            this.loading.set(false);
            this.messages.add({
              severity: 'error',
              summary: 'Erro ao desvincular',
              detail: getErrorMessage(error)
            });
          }
        });
      }
    });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Gestão de Anexos Técnicos
  // ═══════════════════════════════════════════════════════════════════════

  protected abrirModalAnexo(): void {
    this.anexoArquivoSelecionado = null;
    this.anexoTipoId = this.tiposAnexosList().length > 0 ? this.tiposAnexosList()[0].id : null;
    this.anexoObservacoes = '';
    this.anexoDialogVisible.set(true);
  }

  protected onArquivoSelecionado(event: any): void {
    const files = event.files || event.target?.files;
    if (files && files.length > 0) {
      this.anexoArquivoSelecionado = files[0];
    }
  }

  protected salvarAnexo(): void {
    const id = this.obra()?.id;
    if (!id || !this.anexoArquivoSelecionado || !this.anexoTipoId) {
      this.messages.add({
        severity: 'warn',
        summary: 'Arquivo ou Tipo ausente',
        detail: 'Selecione o arquivo e o tipo de anexo para continuar.'
      });
      return;
    }

    this.anexoUploading.set(true);
    this.service
      .anexarArquivo(id, this.anexoTipoId, this.anexoArquivoSelecionado, this.anexoObservacoes)
      .subscribe({
        next: () => {
          this.anexoUploading.set(false);
          this.anexoDialogVisible.set(false);
          this.messages.add({
            severity: 'success',
            summary: 'Anexo salvo',
            detail: `O arquivo ${this.anexoArquivoSelecionado?.name} foi enviado com sucesso.`
          });
          this.carregarObra(id);
        },
        error: (error) => {
          this.anexoUploading.set(false);
          this.messages.add({
            severity: 'error',
            summary: 'Erro ao enviar anexo',
            detail: getErrorMessage(error)
          });
        }
      });
  }

  protected visualizarAnexo(anexo: AnexoObraResponse): void {
    const id = this.obra()?.id;
    if (!id) return;

    const nome = anexo.nomeArquivo.toLowerCase();
    const isPdf = nome.endsWith('.pdf');
    const isImg =
      nome.endsWith('.png') ||
      nome.endsWith('.jpg') ||
      nome.endsWith('.jpeg') ||
      nome.endsWith('.webp') ||
      nome.endsWith('.svg') ||
      nome.endsWith('.gif');

    this.previewNomeArquivo.set(anexo.nomeArquivo);
    this.previewTipo.set(isPdf ? 'pdf' : (isImg ? 'image' : 'other'));
    this.loadingPreview.set(true);
    this.showPreviewModal.set(true);

    this.service.baixarAnexo(id, anexo.id, anexo.nomeArquivo).subscribe({
      next: (response) => {
        if (response.body) {
          let mimeType = response.body.type;
          if (isPdf && (!mimeType || mimeType === 'application/octet-stream')) {
            mimeType = 'application/pdf';
          } else if (isImg && (!mimeType || mimeType === 'application/octet-stream')) {
            mimeType = nome.endsWith('.png') ? 'image/png' : 'image/jpeg';
          }
          const typedBlob = new Blob([response.body], { type: mimeType });
          const objectUrl = window.URL.createObjectURL(typedBlob);
          this.previewRawUrl.set(objectUrl);
          this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(objectUrl));
        }
        this.loadingPreview.set(false);
      },
      error: (err) => {
        this.messages.add({
          severity: 'error',
          summary: 'Erro na visualização',
          detail: getErrorMessage(err)
        });
        this.loadingPreview.set(false);
        this.showPreviewModal.set(false);
      }
    });
  }

  protected fecharPreviewModal(): void {
    this.showPreviewModal.set(false);
    const raw = this.previewRawUrl();
    if (raw) {
      window.URL.revokeObjectURL(raw);
      this.previewRawUrl.set(null);
    }
    this.previewUrl.set(null);
  }

  protected abrirPreviewNovaAba(): void {
    const raw = this.previewRawUrl();
    if (raw) {
      window.open(raw, '_blank');
    }
  }

  protected baixarAnexo(anexo: AnexoObraResponse): void {
    const id = this.obra()?.id;
    if (!id) return;

    this.service.baixarAnexo(id, anexo.id, anexo.nomeArquivo).subscribe({
      next: (response) => {
        if (response.body) {
          const nome = this.service.extrairNomeArquivoDeResponse(response, anexo.nomeArquivo);
          this.service.baixarArquivoBlob(response.body, nome);
        }
      },
      error: (error) => {
        this.messages.add({
          severity: 'error',
          summary: 'Erro no download',
          detail: getErrorMessage(error)
        });
      }
    });
  }

  protected removerAnexo(anexo: AnexoObraResponse): void {
    const id = this.obra()?.id;
    if (!id) return;

    this.confirmation.confirm({
      message: `Deseja realmente remover o anexo "${anexo.nomeArquivo}"?`,
      header: 'Remover Anexo',
      icon: 'pi pi-trash',
      acceptLabel: 'Sim, Remover',
      rejectLabel: 'Cancelar',
      acceptButtonStyleClass: 'p-button-danger',
      accept: () => {
        this.loading.set(true);
        this.service.removerAnexo(id, anexo.id).subscribe({
          next: () => {
            this.loading.set(false);
            this.messages.add({
              severity: 'success',
              summary: 'Anexo removido',
              detail: `O arquivo foi excluído com sucesso.`
            });
            this.carregarObra(id);
          },
          error: (error) => {
            this.loading.set(false);
            this.messages.add({
              severity: 'error',
              summary: 'Erro ao remover anexo',
              detail: getErrorMessage(error)
            });
          }
        });
      }
    });
  }

  // ═══════════════════════════════════════════════════════════════════════
  // Mapa Leaflet
  // ═══════════════════════════════════════════════════════════════════════

  private renderizarMapa(lat: number, lng: number, titulo: string): void {
    const el = this.mapEl()?.nativeElement;
    if (!el || typeof L === 'undefined') return;

    this.destruirMapa();
    try {
      this.leafletMap = L.map(el, { zoomControl: true, attributionControl: false });
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19
      }).addTo(this.leafletMap);

      L.circleMarker([lat, lng], {
        radius: 9,
        fillColor: '#2563eb',
        color: '#ffffff',
        weight: 2,
        opacity: 1,
        fillOpacity: 0.9
      })
        .addTo(this.leafletMap)
        .bindPopup(`<strong>Obra ${titulo}</strong><br>Lat: ${lat.toFixed(5)}, Lon: ${lng.toFixed(5)}`);

      this.leafletMap.setView([lat, lng], 15);
      setTimeout(() => this.leafletMap?.invalidateSize(), 250);
    } catch {
      // Leaflet load fallback
    }
  }

  private destruirMapa(): void {
    if (this.leafletMap) {
      this.leafletMap.remove();
      this.leafletMap = null;
    }
  }

  private carregarEquipes(equipeIds: string[]): void {
    this.rhService.listarEquipes({ ativo: true, size: 200 }).subscribe({
      next: (res) => {
        const idSet = new Set(equipeIds);
        const filtradas = (res.content ?? []).filter((e) => idSet.has(e.id));
        this.equipesDetalhadas.set(filtradas);
      }
    });
  }

  private carregarLookups(): void {
    this.service.listarStatusAtivos().subscribe({
      next: (res) => this.statusList.set(res)
    });

    this.service.listarTiposAnexosAtivos().subscribe({
      next: (res) => this.tiposAnexosList.set(res)
    });
  }
}
