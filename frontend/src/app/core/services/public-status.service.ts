import { HttpBackend, HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PublicStatusResponse } from '../models/status.models';

/** Cliente anônimo do contrato público do monitor Gatus. */
@Injectable({ providedIn: 'root' })
export class PublicStatusService {
  private readonly http = new HttpClient(inject(HttpBackend));
  private readonly endpoint = environment.statusPublicApiUrl
    ? `${environment.statusPublicApiUrl.replace(/\/$/, '')}/api/v1/public/status`
    : null;

  get(): Observable<PublicStatusResponse> {
    if (!this.endpoint) {
      return throwError(() => new Error('STATUS_PUBLIC_API_URL não configurada.'));
    }
    return this.http.get<PublicStatusResponse>(this.endpoint, {
      headers: { Accept: 'application/json' },
      transferCache: false,
    });
  }
}
