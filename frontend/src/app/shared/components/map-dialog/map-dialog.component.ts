import { CommonModule } from '@angular/common';
import { Component, ElementRef, signal, viewChild } from '@angular/core';
import { DialogModule } from 'primeng/dialog';

declare var L: any;

/**
 * Diálogo reutilizável que exibe um ponto no mapa (Leaflet), seguindo o padrão
 * de geolocalização usado nos relatórios de turnos/execuções.
 *
 * Uso: injete via template (#mapDialog) e chame `mapDialog.open(lat, lng, 'Título')`.
 */
@Component({
  selector: 'app-map-dialog',
  standalone: true,
  imports: [CommonModule, DialogModule],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [header]="titulo()"
      [style]="{ width: '95vw', maxWidth: '720px' }"
      [draggable]="false"
      [resizable]="false"
      appendTo="body"
      styleClass="custom-dialog"
      (onShow)="renderMap()"
      (onHide)="destroyMap()"
    >
      <div class="flex flex-col gap-3">
        <div class="flex items-center gap-2 text-[11px] text-gray-500">
          <i class="pi pi-map-marker text-primary-500"></i>
          <span class="font-medium">{{ coordLabel() }}</span>
        </div>
        <div #mapEl class="w-full h-[440px] rounded-xl overflow-hidden border border-slate-200 bg-slate-100"></div>
      </div>
    </p-dialog>
  `
})
export class MapDialogComponent {
  protected visible = signal(false);
  protected titulo = signal('Localização');
  protected coordLabel = signal('');
  private mapEl = viewChild<ElementRef<HTMLDivElement>>('mapEl');
  private map: any;
  private lat = 0;
  private lng = 0;

  open(lat: number, lng: number, titulo = 'Localização') {
    this.lat = lat;
    this.lng = lng;
    this.titulo.set(titulo);
    this.coordLabel.set(`Lat: ${lat.toFixed(5)} · Lon: ${lng.toFixed(5)}`);
    this.visible.set(true);
  }

  protected renderMap() {
    setTimeout(() => {
      const el = this.mapEl()?.nativeElement;
      if (!el || typeof L === 'undefined') return;
      this.destroyMap();
      this.map = L.map(el, { zoomControl: true, attributionControl: true });
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '© OpenStreetMap contributors'
      }).addTo(this.map);
      L.circleMarker([this.lat, this.lng], {
        radius: 9,
        fillColor: '#2563eb',
        color: '#fff',
        weight: 2,
        opacity: 1,
        fillOpacity: 0.85
      })
        .addTo(this.map)
        .bindPopup(this.titulo());
      this.map.setView([this.lat, this.lng], 16);
      setTimeout(() => this.map.invalidateSize(), 200);
    }, 150);
  }

  protected destroyMap() {
    if (this.map) {
      this.map.remove();
      this.map = null;
    }
  }
}
