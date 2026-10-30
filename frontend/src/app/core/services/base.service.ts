import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { Table } from 'dexie';
import { Observable, from, throwError } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { SyncService } from './sync.service';
import { ConnectivityService } from './connectivity.service';
import { PagedResponse } from '../models/shared.models';
import { toHttpParams } from '../utils/http-params.util';

export abstract class BaseService {
  protected http = inject(HttpClient);
  protected sync = inject(SyncService);
  protected connectivity = inject(ConnectivityService, { optional: true });

  /**
   * Recupera uma lista paginada com suporte offline.
   */
  protected getList<T extends { id?: string | number }>(
    url: string, 
    params: any = {}, 
    table?: Table<T, any>
  ): Observable<PagedResponse<T>> {
    // Arrays viram parâmetro repetido (`?id=a&id=b`) — filtros de múltipla seleção.
    const httpParams = toHttpParams(params);

    if (table && this.connectivity && !this.connectivity.isOnline()) {
      return from(table.toArray()).pipe(
        map(items => ({
          content: items,
          page: {
            totalElements: items.length,
            totalPages: 1,
            number: 0,
            size: items.length
          }
        } as PagedResponse<T>))
      );
    }

    return this.http.get<PagedResponse<T>>(url, { params: httpParams }).pipe(
      tap(res => {
        if (table && res.content) {
          // Atualiza cache local de forma otimista
          table.bulkPut(res.content);
        }
      }),
      catchError(err => {
        // Se falhar rede, tenta recuperar do IndexedDB
        if (table) {
          return from(table.toArray()).pipe(
            map(items => ({
              content: items,
              page: {
                totalElements: items.length,
                totalPages: 1,
                number: 0,
                size: items.length
              }
            } as PagedResponse<T>))
          );
        }
        return throwError(() => err);
      })
    );
  }

  /**
   * Recupera um único item com suporte offline.
   */
  protected getOne<T extends { id?: string | number }>(
    url: string, 
    id: string | number, 
    table?: Table<T, any>
  ): Observable<T> {
    if (table && this.connectivity && !this.connectivity.isOnline()) {
      return from(table.get(id as any)).pipe(
        map(item => {
          if (item) return item;
          throw new Error('Registro não encontrado no cache offline local');
        })
      );
    }

    return this.http.get<T>(`${url}/${id}`).pipe(
      tap(res => {
        if (table) table.put(res);
      }),
      catchError(err => {
        if (table) {
          return from(table.get(id as any)).pipe(
            map(item => {
              if (item) return item;
              throw err;
            })
          );
        }
        return throwError(() => err);
      })
    );
  }

  /**
   * Cria um recurso com sincronização offline.
   */
  protected createSync<T>(
    url: string,
    body: any,
    table?: Table<any, any>,
    chaveIdempotencia?: string
  ): Observable<T | null> {
    return from(this.sync.execute<T>('POST', url, body, chaveIdempotencia)).pipe(
      tap(res => {
        if (res && table) table.put(res);
      })
    );
  }

  /**
   * Atualiza um recurso com sincronização offline.
   */
  protected updateSync<T>(url: string, id: string | number, body: any, table?: Table<any, any>): Observable<T | null> {
    const fullUrl = `${url}/${id}`;
    return from(this.sync.execute<T>('PUT', fullUrl, body)).pipe(
      tap(res => {
        if (res && table) table.put(res);
      })
    );
  }

  /**
   * Atualiza parcialmente um recurso com sincronização offline.
   */
  protected patchSync<T>(url: string, id: string | number, body: any, table?: Table<any, any>): Observable<T | null> {
    const fullUrl = `${url}/${id}`;
    return from(this.sync.execute<T>('PATCH', fullUrl, body)).pipe(
      tap(res => {
        if (res && table) table.put(res);
      })
    );
  }

  /**
   * Remove um recurso com sincronização offline.
   */
  protected deleteSync(url: string, id: string | number, table?: Table<any, any>): Observable<void | null> {
    const fullUrl = `${url}/${id}`;
    return from(this.sync.execute<void>('DELETE', fullUrl, null)).pipe(
      tap(() => {
        if (table) table.delete(id as any);
      })
    );
  }
}
