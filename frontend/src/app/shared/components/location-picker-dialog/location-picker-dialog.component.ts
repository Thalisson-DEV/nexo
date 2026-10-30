import { CommonModule } from '@angular/common';
import {
  Component,
  ElementRef,
  NgZone,
  inject,
  output,
  signal,
  viewChild
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AutoCompleteModule, AutoCompleteCompleteEvent } from 'primeng/autocomplete';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';

declare var L: any;

/** Sugestão de endereço retornada pela API pública do Nominatim (OpenStreetMap). */
interface EnderecoSugestao {
  display_name: string;
  lat: string;
  lon: string;
}

/** Resultado confirmado pelo usuário ao escolher um ponto no mapa. */
export interface LocalSelecionado {
  latitude: number;
  longitude: number;
  endereco: string | null;
}

/**
 * Diálogo reutilizável para o usuário **escolher** uma localização — clicando/arrastando
 * o marcador no mapa (Leaflet/OpenStreetMap) e/ou buscando um endereço (CEP, rua, cidade)
 * via API pública do Nominatim, que retorna até 10 sugestões.
 *
 * Regras de UX (importantes):
 * - Selecionar uma sugestão **não confirma**: apenas move o marcador para a coordenada.
 * - Clicar/arrastar no mapa também só reposiciona o marcador.
 * - A confirmação acontece **somente** no botão fixo no canto inferior direito.
 *
 * A busca usa `fetch` nativo de propósito: assim ela não passa pelo `authInterceptor`
 * e o JWT da sessão nunca é enviado a um serviço de terceiros (Nominatim).
 *
 * Uso: injete via template (#localPicker), chame `localPicker.open(lat?, lng?)` e
 * escute `(confirmar)="..."`.
 */
@Component({
  selector: 'app-location-picker-dialog',
  standalone: true,
  imports: [CommonModule, FormsModule, DialogModule, ButtonModule, AutoCompleteModule],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      header="Selecionar Localização"
      [style]="{ width: '95vw', maxWidth: '760px' }"
      [draggable]="false"
      [resizable]="false"
      appendTo="body"
      styleClass="custom-dialog"
      (onShow)="renderMap()"
      (onHide)="destroyMap()"
    >
      <div class="flex flex-col gap-3">
        <!-- Busca de endereço (CEP / rua / cidade) -->
        <div class="flex flex-col gap-1.5">
          <label class="filter-label !text-slate-600 !text-[11px] flex items-center gap-1.5">
            <i class="pi pi-search text-slate-400"></i> Buscar endereço
          </label>
          <p-autoComplete
            [(ngModel)]="termoBusca"
            [suggestions]="sugestoes()"
            (completeMethod)="buscarEndereco($event)"
            (onSelect)="onSelecionarSugestao($event)"
            optionLabel="display_name"
            [delay]="450"
            [minLength]="3"
            [forceSelection]="false"
            [showClear]="true"
            scrollHeight="280px"
            placeholder="Digite CEP, nome da rua, cidade..."
            styleClass="lp-autocomplete p-autocomplete-fluid"
            inputStyleClass="lp-autocomplete-input"
            appendTo="body"
            emptyMessage="Nenhum endereço encontrado"
          >
            <ng-template let-item pTemplate="item">
              <div class="flex items-start gap-2 py-0.5 max-w-[680px]">
                <i class="pi pi-map-marker text-primary-500 text-[12px] mt-0.5"></i>
                <span class="text-[11px] text-slate-600 leading-snug whitespace-normal">{{ item.display_name }}</span>
              </div>
            </ng-template>
          </p-autoComplete>
          <span class="text-[10px] text-slate-400 flex items-center gap-1">
            <i class="pi pi-info-circle"></i>
            Ao escolher uma opção o marcador se move; nada é salvo até você confirmar.
          </span>
        </div>

        <!-- Mapa + botão de confirmação -->
        <div class="relative w-full">
          <div
            #mapEl
            class="w-full h-[420px] rounded-xl overflow-hidden border border-slate-200 bg-slate-100"
          ></div>

          <!-- Coordenada atual -->
          <div
            class="absolute top-3 left-3 z-[1200] bg-white/95 backdrop-blur px-3 py-1.5 rounded-lg border border-slate-200 shadow-sm flex items-center gap-2 text-[11px] text-slate-600 font-medium"
          >
            <i class="pi pi-map-marker text-primary-500"></i>
            @if (temPonto()) {
              <span>{{ coordLabel() }}</span>
            } @else {
              <span class="text-slate-400">Clique no mapa para marcar</span>
            }
          </div>

          <!-- Botão de confirmar no canto inferior direito -->
          <div class="absolute bottom-4 right-4 z-[1200]">
            <p-button
              label="Confirmar Localização"
              icon="pi pi-check-circle"
              [disabled]="!temPonto()"
              (onClick)="confirmar()"
              styleClass="!rounded-xl !bg-primary-600 !border-primary-600 !text-white !font-medium !text-[11px] !tracking-widest !uppercase !px-6 !py-3 shadow-lg"
            ></p-button>
          </div>
        </div>
      </div>
    </p-dialog>
  `,
  styles: [
    `
      /*
       * Regras GLOBAIS (começam com ::ng-deep, sem :host): o p-dialog usa
       * appendTo="body", então o input é renderizado fora do host deste
       * componente — estilos escopados não o alcançariam. Escopo garantido
       * pela classe única .lp-autocomplete.
       */
      ::ng-deep .lp-autocomplete {
        display: flex !important;
        width: 100% !important;
      }
      ::ng-deep .lp-autocomplete .lp-autocomplete-input,
      ::ng-deep .lp-autocomplete .p-autocomplete-input {
        flex: 1 1 auto;
        width: 100% !important;
        font-size: 11px;
        font-weight: 500;
        border-radius: 6px;
      }
    `
  ]
})
export class LocationPickerDialogComponent {
  /** Emite a localização escolhida apenas quando o usuário confirma. */
  readonly localConfirmado = output<LocalSelecionado>();

  protected visible = signal(false);
  protected buscando = signal(false);
  protected sugestoes = signal<EnderecoSugestao[]>([]);
  protected temPonto = signal(false);
  protected coordLabel = signal('');
  protected termoBusca: any = '';

  private readonly zone = inject(NgZone);
  private mapEl = viewChild<ElementRef<HTMLDivElement>>('mapEl');
  private map: any;
  private marker: any;
  private lat: number | null = null;
  private lng: number | null = null;
  private enderecoSelecionado: string | null = null;

  /** Coordenadas padrão (Salvador/BA) quando não há ponto inicial. */
  private static readonly DEFAULT_CENTER: [number, number] = [-12.9714, -38.5014];

  /** Abre o diálogo, opcionalmente já posicionado em uma coordenada existente. */
  open(lat?: number | null, lng?: number | null) {
    this.sugestoes.set([]);
    this.termoBusca = '';
    this.enderecoSelecionado = null;
    if (lat != null && lng != null) {
      this.lat = lat;
      this.lng = lng;
      this.temPonto.set(true);
      this.atualizarLabel();
    } else {
      this.lat = null;
      this.lng = null;
      this.temPonto.set(false);
    }
    this.visible.set(true);
  }

  protected renderMap() {
    setTimeout(() => {
      const el = this.mapEl()?.nativeElement;
      if (!el || typeof L === 'undefined') return;
      this.destroyMap();

      const center = this.temPonto() ? [this.lat, this.lng] : LocationPickerDialogComponent.DEFAULT_CENTER;
      this.map = L.map(el, { zoomControl: true, attributionControl: true });
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap contributors'
      }).addTo(this.map);
      this.map.setView(center, this.temPonto() ? 16 : 12);

      // Clicar no mapa reposiciona o marcador (não confirma).
      this.map.on('click', (e: any) => {
        this.zone.run(() => this.posicionarMarcador(e.latlng.lat, e.latlng.lng));
      });

      if (this.temPonto()) {
        this.posicionarMarcador(this.lat!, this.lng!, false);
      }

      setTimeout(() => this.map?.invalidateSize(), 200);
    }, 150);
  }

  /** Cria ou move o marcador. `recenter` controla se a câmera acompanha. */
  private posicionarMarcador(lat: number, lng: number, recenter = true) {
    this.lat = lat;
    this.lng = lng;
    this.temPonto.set(true);
    this.atualizarLabel();

    const icon = L.divIcon({
      className: 'lp-marker',
      html: `
        <svg width="28" height="40" viewBox="0 0 20 32" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M10 0C4.47715 0 0 4.47715 0 10C0 17.5 10 32 10 32C10 32 20 17.5 20 10C20 4.47715 15.5228 0 10 0ZM10 13.5C8.067 13.5 6.5 11.933 6.5 10C6.5 8.067 8.067 6.5 10 6.5C11.933 6.5 13.5 8.067 13.5 10C13.5 11.933 11.933 13.5 10 13.5Z" fill="#2563eb"/>
        </svg>`,
      iconSize: [28, 40],
      iconAnchor: [14, 40]
    });

    if (this.marker) {
      this.marker.setLatLng([lat, lng]);
    } else {
      this.marker = L.marker([lat, lng], { icon, draggable: true }).addTo(this.map);
      // Arrastar o marcador também só reposiciona (não confirma).
      this.marker.on('dragend', () => {
        const pos = this.marker.getLatLng();
        this.zone.run(() => this.posicionarMarcador(pos.lat, pos.lng, false));
      });
    }

    if (recenter && this.map) {
      this.map.setView([lat, lng], Math.max(this.map.getZoom(), 16));
    }
  }

  private atualizarLabel() {
    if (this.lat == null || this.lng == null) return;
    this.coordLabel.set(`Lat: ${this.lat.toFixed(5)} · Lon: ${this.lng.toFixed(5)}`);
  }

  /** Consulta o Nominatim (até 10 ocorrências) com base no texto digitado. */
  protected buscarEndereco(event: AutoCompleteCompleteEvent) {
    const query = (event.query || '').trim();
    if (query.length < 3) {
      this.sugestoes.set([]);
      return;
    }

    this.buscando.set(true);
    const url =
      'https://nominatim.openstreetmap.org/search' +
      `?format=jsonv2&addressdetails=0&limit=10&countrycodes=br&accept-language=pt-BR&q=${encodeURIComponent(query)}`;

    // fetch nativo: evita o authInterceptor e não vaza o JWT para o serviço externo.
    fetch(url, { headers: { Accept: 'application/json' } })
      .then((res) => (res.ok ? res.json() : []))
      .then((data: EnderecoSugestao[]) => {
        this.zone.run(() => {
          this.sugestoes.set(Array.isArray(data) ? data.slice(0, 10) : []);
          this.buscando.set(false);
        });
      })
      .catch(() => {
        this.zone.run(() => {
          this.sugestoes.set([]);
          this.buscando.set(false);
        });
      });
  }

  /** Apenas move o marcador para o endereço escolhido — não confirma. */
  protected onSelecionarSugestao(event: { value: EnderecoSugestao }) {
    const sug = event.value;
    if (!sug) return;
    const lat = parseFloat(sug.lat);
    const lng = parseFloat(sug.lon);
    if (Number.isNaN(lat) || Number.isNaN(lng)) return;
    this.enderecoSelecionado = sug.display_name ?? null;
    this.posicionarMarcador(lat, lng, true);
  }

  /** Único ponto que efetiva a escolha da localização. */
  protected confirmar() {
    if (this.lat == null || this.lng == null) return;
    this.localConfirmado.emit({
      latitude: this.lat,
      longitude: this.lng,
      endereco: this.enderecoSelecionado
    });
    this.visible.set(false);
  }

  protected destroyMap() {
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
    this.marker = null;
  }
}
