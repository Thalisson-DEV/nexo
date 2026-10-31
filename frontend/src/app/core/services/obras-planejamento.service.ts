import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BaseService } from './base.service';
import { OperacaoItemKit } from '../models/obras-kit.models';
import {
  AceitarProposta,
  AnalisePlanejamento,
  DisponibilidadeAnalise,
  PlanejamentoObra,
  PontoPlanejamento,
  TipoExecucaoPonto,
  TipoPontoPlanejamento
} from '../models/obras-planejamento.models';

export interface EdicaoPonto {
  numero: string;
  barramento?: string | null;
  tipo: TipoPontoPlanejamento;
  tipoExecucao: TipoExecucaoPonto;
  temPoda: boolean;
  temAterramento: boolean;
  versao: number;
}

/** Comandos do planejamento: cada um grava uma alteração e devolve o planejamento atualizado. */
@Injectable({ providedIn: 'root' })
export class ObrasPlanejamentoService extends BaseService {
  private url(idObra: string, caminho = ''): string {
    return `${environment.apiUrl}/v1/obras/${idObra}/planejamento${caminho}`;
  }

  buscar(idObra: string): Observable<PlanejamentoObra> {
    return this.http.get<PlanejamentoObra>(this.url(idObra));
  }

  definirProjetoMestre(idObra: string, idAnexo: string): Observable<PlanejamentoObra> {
    return this.http.put<PlanejamentoObra>(this.url(idObra, '/projeto-mestre'), { idAnexo });
  }

  enviarProjetoMestre(idObra: string, idTipoAnexo: string, arquivo: File): Observable<PlanejamentoObra> {
    const dados = new FormData();
    dados.append('arquivo', arquivo);
    return this.http.post<PlanejamentoObra>(this.url(idObra, '/projeto-mestre'), dados, {
      params: new HttpParams().set('idTipoAnexo', idTipoAnexo)
    });
  }

  girarPagina(idObra: string, pagina: number, graus: number): Observable<PlanejamentoObra> {
    return this.http.put<PlanejamentoObra>(this.url(idObra, `/paginas/${pagina}/rotacao`), { graus });
  }

  adicionarPonto(idObra: string, ponto: { numero: string; pagina: number; x: number; y: number; tipo: TipoPontoPlanejamento }): Observable<PlanejamentoObra> {
    return this.http.post<PlanejamentoObra>(this.url(idObra, '/pontos'), ponto);
  }

  editarPonto(idObra: string, idPonto: string, edicao: EdicaoPonto): Observable<PlanejamentoObra> {
    return this.http.put<PlanejamentoObra>(this.url(idObra, `/pontos/${idPonto}`), edicao);
  }

  moverPonto(idObra: string, idPonto: string, posicao: { pagina: number; x: number; y: number; versao: number }): Observable<PontoPlanejamento> {
    return this.http.put<PontoPlanejamento>(this.url(idObra, `/pontos/${idPonto}/posicao`), posicao);
  }

  excluirPonto(idObra: string, idPonto: string): Observable<PlanejamentoObra> {
    return this.http.delete<PlanejamentoObra>(this.url(idObra, `/pontos/${idPonto}`));
  }

  adicionarVao(idObra: string, idPontoA: string, idPontoB: string): Observable<PlanejamentoObra> {
    return this.http.post<PlanejamentoObra>(this.url(idObra, '/vaos'), { idPontoA, idPontoB });
  }

  editarVao(idObra: string, idVao: string, metragem: number | null, versao: number): Observable<PlanejamentoObra> {
    return this.http.put<PlanejamentoObra>(this.url(idObra, `/vaos/${idVao}`), { metragem, versao });
  }

  excluirVao(idObra: string, idVao: string): Observable<PlanejamentoObra> {
    return this.http.delete<PlanejamentoObra>(this.url(idObra, `/vaos/${idVao}`));
  }

  aplicarKit(idObra: string, comando: {
    idKit: string;
    idsPontos: string[];
    idsVaos: string[];
    operacao: OperacaoItemKit;
    multiplicador?: number | null;
  }): Observable<{ planejamento: PlanejamentoObra; jaTinham: string[] }> {
    return this.http.post<{ planejamento: PlanejamentoObra; jaTinham: string[] }>(this.url(idObra, '/kits'), comando);
  }

  alterarMultiplicador(idObra: string, idKit: string, multiplicador: number, versao: number): Observable<PlanejamentoObra> {
    return this.http.put<PlanejamentoObra>(this.url(idObra, `/kits/${idKit}/quantidade`), { multiplicador, versao });
  }

  marcarExecutadoAntes(idObra: string, idKit: string, executado: boolean, versao: number): Observable<PlanejamentoObra> {
    return this.http.put<PlanejamentoObra>(this.url(idObra, `/kits/${idKit}/executado-antes`), { executado, versao });
  }

  removerKit(idObra: string, idKit: string): Observable<PlanejamentoObra> {
    return this.http.delete<PlanejamentoObra>(this.url(idObra, `/kits/${idKit}`));
  }

  alterarItem(idObra: string, idKit: string, idItem: string, quantidade: number, versao: number): Observable<PlanejamentoObra> {
    return this.http.put<PlanejamentoObra>(this.url(idObra, `/kits/${idKit}/itens/${idItem}`), { quantidade, versao });
  }

  removerItem(idObra: string, idKit: string, idItem: string): Observable<PlanejamentoObra> {
    return this.http.delete<PlanejamentoObra>(this.url(idObra, `/kits/${idKit}/itens/${idItem}`));
  }

  adicionarAnotacao(idObra: string, anotacao: { pagina: number; x: number; y: number; texto: string }): Observable<PlanejamentoObra> {
    return this.http.post<PlanejamentoObra>(this.url(idObra, '/anotacoes'), anotacao);
  }

  editarAnotacao(idObra: string, id: string, texto: string, versao: number): Observable<PlanejamentoObra> {
    return this.http.put<PlanejamentoObra>(this.url(idObra, `/anotacoes/${id}`), { texto, versao });
  }

  excluirAnotacao(idObra: string, id: string): Observable<PlanejamentoObra> {
    return this.http.delete<PlanejamentoObra>(this.url(idObra, `/anotacoes/${id}`));
  }

  disponibilidadeAnalise(idObra: string, pagina: number): Observable<DisponibilidadeAnalise> {
    return this.http.get<DisponibilidadeAnalise>(this.url(idObra, '/analises/disponibilidade'), {
      params: new HttpParams().set('pagina', pagina)
    });
  }

  iniciarAnalise(idObra: string, pagina: number): Observable<AnalisePlanejamento> {
    return this.http.post<AnalisePlanejamento>(this.url(idObra, '/analises'), { pagina });
  }

  buscarAnalise(idObra: string, id: string): Observable<AnalisePlanejamento> {
    return this.http.get<AnalisePlanejamento>(this.url(idObra, `/analises/${id}`));
  }

  aceitarAnalise(idObra: string, id: string, aceite: AceitarProposta): Observable<PlanejamentoObra> {
    return this.http.post<PlanejamentoObra>(this.url(idObra, `/analises/${id}/aceite`), aceite);
  }
}
