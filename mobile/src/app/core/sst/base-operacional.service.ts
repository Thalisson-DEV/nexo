import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { DbService } from '../db/db.service';
import { BaseOperacionalLocal } from '../db/models';
import { ApiError } from '../http/error.interceptor';
import { silentContext } from '../http/http-context';

interface PageResponseDto<T> {
  content: T[];
}

interface BaseOperacionalResponseDto {
  id: string;
  nome: string;
  latitude: number | null;
  longitude: number | null;
  raio: number | null;
  ativo: boolean;
}

const ACCESS_CONFIG_KEY = 'basesOperacionaisAccessDenied';

@Injectable({ providedIn: 'root' })
export class BaseOperacionalService {
  private readonly http = inject(HttpClient);
  private readonly dbService = inject(DbService);

  async obterAtivasDoCache(): Promise<BaseOperacionalLocal[]> {
    return this.dbService.getBasesOperacionaisAtivas();
  }

  async sincronizarAtivas(): Promise<BaseOperacionalLocal[]> {
    const params = new HttpParams()
      .set('ativo', true)
      .set('page', 0)
      .set('size', 100)
      .set('sort', 'nome,asc');

    try {
      const response = await firstValueFrom(
        this.http.get<PageResponseDto<BaseOperacionalResponseDto>>(
          `${environment.apiBaseUrl}/v1/bases-operacionais`,
          { params, context: silentContext() }
        )
      );

      const bases = response.content
        .filter(base => base.ativo)
        .map<BaseOperacionalLocal>(base => ({
          id: base.id,
          nome: base.nome,
          latitude: base.latitude,
          longitude: base.longitude,
          raio: base.raio,
          ativo: base.ativo,
        }));

      await this.dbService.replaceBasesOperacionais(bases);
      await this.dbService.setConfig(ACCESS_CONFIG_KEY, false);
      return bases;
    } catch (error) {
      if ((error as ApiError)?.status === 403) {
        await this.dbService.setConfig(ACCESS_CONFIG_KEY, true);
      }
      throw error;
    }
  }

  async acessoNegado(): Promise<boolean> {
    return (await this.dbService.getConfig(ACCESS_CONFIG_KEY)) === true;
  }
}
