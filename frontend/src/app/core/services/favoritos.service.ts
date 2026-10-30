import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router, NavigationEnd } from '@angular/router';
import { filter, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { FavoritoRequest, FavoritoResponse } from '../models/iam.models';
import { toSignal } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';

@Injectable({
  providedIn: 'root'
})
export class FavoritosService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private messageService = inject(MessageService);
  private apiUrl = `${environment.apiUrl}/v1/iam/usuarios/me/favoritos`;

  private _favoritos = signal<FavoritoResponse[]>([]);
  favoritos = this._favoritos.asReadonly();

  currentRoute = toSignal(
    this.router.events.pipe(
      filter(event => event instanceof NavigationEnd),
      map(event => (event as NavigationEnd).urlAfterRedirects)
    ),
    { initialValue: this.router.url }
  );

  isCurrentPageFavorite = computed(() => {
    const currentUrl = this.currentRoute();
    return this._favoritos().some(f => f.rota === currentUrl);
  });

  constructor() {
    this.carregarFavoritos();
  }

  carregarFavoritos() {
    this.http.get<FavoritoResponse[]>(this.apiUrl).subscribe(favs => {
      this._favoritos.set(favs);
    });
  }

  toggleFavorite(titulo: string, icone?: string) {
    const currentUrl = this.router.url;
    const favorite = this._favoritos().find(f => f.rota === currentUrl);

    if (favorite) {
      this.removerFavorito(favorite.id);
    } else {
      this.adicionarFavorito({
        titulo,
        rota: currentUrl,
        icone: icone || 'pi pi-star'
      });
    }
  }

  adicionarFavorito(request: FavoritoRequest) {
    this.http.post<FavoritoResponse>(this.apiUrl, request).subscribe(newFav => {
      this._favoritos.update(favs => [...favs, newFav]);
      this.messageService.add({
        severity: 'success',
        summary: 'Favoritos',
        detail: `${request.titulo} adicionado aos favoritos`
      });
    });
  }

  removerFavorito(id: string) {
    const favorite = this._favoritos().find(f => f.id === id);
    this.http.delete(`${this.apiUrl}/${id}`).subscribe(() => {
      this._favoritos.update(favs => favs.filter(f => f.id !== id));
      if (favorite) {
        this.messageService.add({
          severity: 'warn',
          summary: 'Favoritos',
          detail: `${favorite.titulo} removido dos favoritos`
        });
      }
    });
  }
}
