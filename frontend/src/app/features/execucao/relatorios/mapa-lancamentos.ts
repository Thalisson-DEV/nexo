// Leaflet é carregado globalmente (angular.json), como nos demais mapas do Nexo.
declare const L: any;

export interface PontoDoMapa {
  latitude: number;
  longitude: number;
  /** Texto curto dentro do marcador (ex.: ordem da nota no turno). */
  rotulo: string;
  /** Conteúdo do popup (texto simples). */
  descricao: string;
  /** Marcador em destaque (a nota do relatório). */
  destaque?: boolean;
}

/**
 * Desenha os lançamentos num mapa: marcadores numerados na ordem e o trajeto entre
 * eles. As cores vêm dos tokens do tema; o Leaflet precisa delas como valor literal
 * para o traçado, então são lidas do elemento no momento do desenho.
 *
 * Devolve o mapa (para `remove()` no destroy) ou `null` sem pontos.
 */
export function desenharMapaDeLancamentos(elemento: HTMLElement, pontos: readonly PontoDoMapa[]): any | null {
  if (!pontos.length || typeof L === 'undefined') return null;

  const estilo = getComputedStyle(elemento);
  const traco = estilo.getPropertyValue('--ces-brand').trim() || 'currentColor';

  const mapa = L.map(elemento, { zoomControl: true, attributionControl: true, scrollWheelZoom: false });
  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '© OpenStreetMap contributors'
  }).addTo(mapa);

  const coordenadas = pontos.map((p) => [p.latitude, p.longitude] as [number, number]);
  if (coordenadas.length > 1) {
    L.polyline(coordenadas, { color: traco, weight: 2, opacity: 0.6, dashArray: '6 6' }).addTo(mapa);
  }

  for (const ponto of pontos) {
    const tamanho = ponto.destaque ? 34 : 26;
    // Estilo inline com var(): o ícone é inserido fora da view do componente.
    const html =
      `<span style="display:grid;place-items:center;width:${tamanho}px;height:${tamanho}px;border-radius:999px;` +
      `border:2px solid var(--ces-surface);box-shadow:var(--ces-shadow-md);font:700 11px/1 system-ui,sans-serif;` +
      `background:${ponto.destaque ? 'var(--ces-foreground)' : 'var(--ces-brand)'};color:var(--ces-surface)">` +
      `${escapar(ponto.rotulo)}</span>`;
    L.marker([ponto.latitude, ponto.longitude], {
      icon: L.divIcon({ html, className: '', iconSize: [tamanho, tamanho], iconAnchor: [tamanho / 2, tamanho / 2] }),
      keyboard: true,
      title: ponto.descricao,
      zIndexOffset: ponto.destaque ? 1000 : 0
    })
      .addTo(mapa)
      .bindPopup(escapar(ponto.descricao));
  }

  if (coordenadas.length === 1) {
    mapa.setView(coordenadas[0], 16);
  } else {
    mapa.fitBounds(L.latLngBounds(coordenadas), { padding: [32, 32], maxZoom: 17 });
  }
  setTimeout(() => mapa.invalidateSize(), 300);
  return mapa;
}

function escapar(texto: string): string {
  return texto.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}
