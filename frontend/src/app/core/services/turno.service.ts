import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { 
  TurnoResponse 
} from '../models/turno.models';

@Injectable({
  providedIn: 'root'
})
export class TurnoService extends BaseService {
  private readonly apiUrl = `${environment.apiUrl}/v1/turnos`;

  /**
   * Busca o turno ativo de uma equipe específica.
   * Retorna 404 se não houver turno ativo.
   */
  buscarAtivoPorEquipe(idEquipe: string): Observable<TurnoResponse> {
    return this.getOne<TurnoResponse>(this.apiUrl, `equipe/${idEquipe}/ativo`);
  }
}
