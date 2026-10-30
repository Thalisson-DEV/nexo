import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  MencaoUsuariosFiltros,
  UsuarioMencionavel
} from '../models/mencao.models';

/**
 * Autocomplete de quem pode ser marcado com `@` (`/v1/mencoes/usuarios`).
 *
 * Exige apenas autenticação: marcar é parte de escrever. O servidor devolve no máximo 10 nomes,
 * ordenados por login, buscando por *contém*.
 *
 * Fora do padrão offline-first do `BaseService` de propósito: é uma sugestão de digitação, não um
 * dado de domínio. Sem rede não há o que sugerir, e cachear logins abriria uma lista de pessoas no
 * IndexedDB sem necessidade.
 */
@Injectable({ providedIn: 'root' })
export class MencoesService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = `${environment.apiUrl}/v1/mencoes`;

  buscarUsuarios(filtros: MencaoUsuariosFiltros): Observable<UsuarioMencionavel[]> {
    const termo = filtros.termo.trim();
    if (!termo) return of([]);

    let params = new HttpParams().set('termo', termo);
    if (filtros.origem) params = params.set('origem', filtros.origem);
    if (filtros.visibilidadeRestrita !== undefined) {
      params = params.set('visibilidadeRestrita', String(filtros.visibilidadeRestrita));
    }

    return this.http.get<UsuarioMencionavel[]>(`${this.apiUrl}/usuarios`, { params });
  }
}
