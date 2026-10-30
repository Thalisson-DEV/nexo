import { Injectable, inject, signal } from '@angular/core';
import { DbService } from '../db/db.service';
import {
  ProgressoCiclo, ProgressoMeta, calcularMetaDiariaPadrao, calcularProgressoCiclo, calcularProgressoMeta
} from './campo-regras';

/**
 * Resumo do trabalho da equipe calculado com os dados do aparelho:
 * progresso da meta diária, da meta do ciclo (26 a 25) ou do mês e faturamento de
 * execuções (inclusive offline).
 */
@Injectable({ providedIn: 'root' })
export class CampoResumoService {
  private readonly dbService = inject(DbService);

  readonly progresso = signal<ProgressoMeta | null>(null);
  readonly ciclo = signal<ProgressoCiclo | null>(null);

  async carregar(): Promise<void> {
    const [execucoes, composicao, equipe, turnoAtivo, todasAcoes] = await Promise.all([
      this.dbService.getExecucoes(),
      this.dbService.getComposicaoEquipe(),
      this.dbService.getEquipe(),
      this.dbService.getTurnoAtivo(),
      this.dbService.getAcoes()
    ]);

    const acoesMap = new Map((todasAcoes || []).map(a => [a.id, a]));
    for (const e of execucoes) {
      if ((e.valorTotalFaturado === undefined || e.valorTotalFaturado === null) && e.acoes?.length > 0) {
        let soma = 0;
        let temValor = false;
        for (const idAcao of e.acoes) {
          const acao = acoesMap.get(idAcao);
          if (acao?.valor !== undefined && acao?.valor !== null) {
            soma += Number(acao.valor);
            temValor = true;
          }
        }
        if (temValor) {
          e.valorTotalFaturado = Math.round(soma * 100) / 100;
        }
      }
    }

    const metaDiaria = composicao?.metaDoCiclo?.metaDiaria
      ?? composicao?.metaFaturamentoDiaria
      ?? equipe?.metaFaturamentoDiaria
      ?? calcularMetaDiariaPadrao(composicao?.metaFaturamentoMensal ?? equipe?.metaFaturamentoMensal);

    const agora = new Date();
    this.progresso.set(calcularProgressoMeta(execucoes, metaDiaria, agora, turnoAtivo?.id));
    this.ciclo.set(calcularProgressoCiclo(composicao?.metaDoCiclo, execucoes, agora));
  }
}
