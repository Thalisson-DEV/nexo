import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { MessageService } from 'primeng/api';

@Injectable({
  providedIn: 'root'
})
export class ImportacaoService {
  private http = inject(HttpClient);
  private messageService = inject(MessageService);
  private apiUrl = environment.apiUrl;

  private getFullUrl(endpointCaminho: string): string {
    if (endpointCaminho.startsWith('http://') || endpointCaminho.startsWith('https://')) {
      return endpointCaminho;
    }
    const cleanPath = endpointCaminho.startsWith('/') ? endpointCaminho : `/${endpointCaminho}`;
    if (cleanPath.startsWith('/v1/')) {
      return `${this.apiUrl}${cleanPath}`;
    }
    return `${this.apiUrl}/v1${cleanPath}`;
  }

  /**
   * Realiza o download do modelo CSV de importação a partir do endpoint informado.
   * @param endpointCaminho Ex: '/rh/colaboradores/importar/modelo' ou '/v1/rh/colaboradores/importar/modelo'
   * @param nomeArquivoSalvar Ex: 'modelo-colaboradores.csv'
   */
  baixarModeloCsv(endpointCaminho: string, nomeArquivoSalvar: string): void {
    const url = this.getFullUrl(endpointCaminho);

    this.http.get(url, {
      responseType: 'blob',
      observe: 'response'
    }).subscribe({
      next: (res: HttpResponse<Blob>) => {
        if (!res.body) return;

        // Tenta extrair o nome do header Content-Disposition se disponível
        const contentDisposition = res.headers.get('content-disposition');
        let filename = nomeArquivoSalvar;
        if (contentDisposition) {
          const match = contentDisposition.match(/filename="?([^"]+)"?/);
          if (match && match[1]) {
            filename = match[1];
          }
        }

        // Cria a URL temporária para o Blob (já contendo o BOM UTF-8 do backend)
        const blob = new Blob([res.body], { type: 'text/csv; charset=UTF-8' });
        const downloadUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = downloadUrl;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.URL.revokeObjectURL(downloadUrl);
      },
      error: (err) => {
        console.error('Erro ao baixar modelo CSV:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Erro de Download',
          detail: 'Não foi possível baixar o modelo CSV de importação.'
        });
      }
    });
  }

  /**
   * Submete o arquivo CSV preenchido para validação (Multipart)
   */
  validarArquivoCsv(endpointCaminho: string, arquivo: File): Observable<any> {
    const formData = new FormData();
    formData.append('arquivo', arquivo);

    return this.http.post(this.getFullUrl(endpointCaminho), formData);
  }

  /**
   * Submete a confirmação da importação para o backend
   */
  confirmarImportacao(endpointCaminho: string, payload: any): Observable<any> {
    return this.http.post(this.getFullUrl(endpointCaminho), payload);
  }
}
