import { inject, signal } from '@angular/core';
import { CesConfirmationService, CesImportRow, NotificationService } from '@ces/ui';
import { Observable } from 'rxjs';
import { getErrorMessage } from '../../core/models/shared.models';

export interface RespostaImportacao<T> {
  registros?: T[];
  totalFalha: number;
}

/**
 * Fluxo de importação de planilha das listagens, para usar com `ces-import-drawer`:
 * enviar arquivo → pré-visualização com correção por linha → processar (com confirmação).
 * Crie num inicializador de campo (usa `inject`).
 */
export function importacaoListagem<T extends CesImportRow>(opcoes: {
  /** Envia o arquivo e devolve a pré-visualização. */
  enviar: (arquivo: File) => Observable<RespostaImportacao<T>>;
  /** Confirma as linhas conferidas. */
  confirmar: (registros: T[]) => Observable<RespostaImportacao<T>>;
  /** Baixa o modelo da planilha. */
  modelo: () => void;
  /** Depois de processar (ex.: `lista.reload()`). */
  aoProcessar: () => void;
  /** Planilha com dado sensível (ex.: senhas): não mantém as linhas depois de processar sem erro. */
  sensivel?: boolean;
}) {
  const notificacoes = inject(NotificationService);
  const confirmacao = inject(CesConfirmationService);
  const aberta = signal(false);
  const linhas = signal<T[]>([]);
  const enviando = signal(false);
  const processando = signal(false);

  return {
    aberta,
    linhas,
    enviando,
    processando,
    baixarModelo: () => opcoes.modelo(),
    enviarArquivo(arquivo: File): void {
      enviando.set(true);
      opcoes.enviar(arquivo).subscribe({
        next: (r) => {
          enviando.set(false);
          linhas.set(r.registros ?? []);
          if (r.totalFalha > 0) notificacoes.warning(`${r.totalFalha} ${r.totalFalha === 1 ? 'linha tem' : 'linhas têm'} erro e precisa(m) de correção.`);
        },
        error: (e) => {
          enviando.set(false);
          notificacoes.error(getErrorMessage(e));
        }
      });
    },
    async processar(): Promise<void> {
      const registros = linhas().map(({ editing, ...resto }) => resto as T);
      if (!registros.length) return;
      const ok = await confirmacao.confirm({
        title: 'Processar importação',
        message: `Processar ${registros.length} ${registros.length === 1 ? 'linha conferida' : 'linhas conferidas'}?`,
        confirmLabel: 'Processar'
      });
      if (!ok) return;
      processando.set(true);
      opcoes.confirmar(registros).subscribe({
        next: (r) => {
          processando.set(false);
          linhas.set(opcoes.sensivel && r.totalFalha === 0 ? [] : (r.registros ?? []));
          if (r.totalFalha > 0) notificacoes.warning(`${r.totalFalha} ${r.totalFalha === 1 ? 'linha ainda tem' : 'linhas ainda têm'} erro.`);
          else notificacoes.success('Linhas enviadas para processamento.');
          opcoes.aoProcessar();
        },
        error: (e) => {
          processando.set(false);
          notificacoes.error(getErrorMessage(e));
        }
      });
    },
    concluirCorrecao(linha: T): void {
      linha.editing = false;
      linhas.set([...linhas()]);
    },
    remover(linha: T): void {
      linhas.set(linhas().filter((r) => r !== linha));
    },
    limpar(): void {
      linhas.set([]);
    }
  };
}

/** Colunas comuns ao fim da pré-visualização de importação. */
export const CHAVE_LINHA_IMPORTACAO = (r: CesImportRow, i: number) => String(r.linha ?? i);
