import { CesSelectOption } from '@ces/ui';
import { Observable } from 'rxjs';

import {
  CLASSES_TENSAO,
  CarteiraImportacaoLinha,
  KitImportacaoLinha,
  OrcamentoImportacaoLinha,
  PreviaImportacaoObraResponse
} from '../../../core/models/obras-kit.models';
import { PRIORIDADES_OBRA } from '../../../core/models/obras.models';
import { ObrasKitService } from '../../../core/services/obras-kit.service';
import { ColunaPlanilha, FonteImportacao, LinhaPlanilha } from './importacao-planilha-obras.component';

const TIPOS_ITEM: CesSelectOption<string>[] = [
  { label: 'Serviço', value: 'SERVICO' },
  { label: 'Material', value: 'MATERIAL' }
];
const OPERACOES: CesSelectOption<string>[] = [
  { label: 'Instalar', value: 'INSTALAR' },
  { label: 'Retirar', value: 'RETIRAR' }
];
const TENSOES: CesSelectOption<string>[] = CLASSES_TENSAO.map((c) => ({ label: c.label, value: c.label }));
const PRIORIDADES: CesSelectOption<string>[] = PRIORIDADES_OBRA.map((p) => ({ label: p.label, value: p.label }));

/** As linhas tipadas do serviço circulam na pré-visualização genérica como registros de campos. */
const comoLinhas = <T>(resposta: Observable<PreviaImportacaoObraResponse<T>>) =>
  resposta as unknown as Observable<PreviaImportacaoObraResponse<LinhaPlanilha>>;
const deLinhas = <T>(registros: LinhaPlanilha[]) => registros as unknown as T[];

/** Rótulo de uma opção pelo valor da planilha (aceita o código ou o próprio rótulo). */
const rotulo = (opcoes: readonly CesSelectOption<string>[], chave: string) => (r: LinhaPlanilha) => {
  const valor = String(r[chave] ?? '').trim();
  if (!valor) return '—';
  return opcoes.find((o) => o.value.toUpperCase() === valor.toUpperCase() || o.label.toUpperCase() === valor.toUpperCase())?.label ?? valor;
};

// ═══════════════════════════════════════════════════════════════════════
// Kits
// ═══════════════════════════════════════════════════════════════════════

export const COLUNAS_KITS: ColunaPlanilha[] = [
  { key: 'codigoKit', header: 'Kit', priority: 1 },
  { key: 'descricaoKit', header: 'Descrição do kit' },
  { key: 'grupo', header: 'Grupo' },
  { key: 'tensoes', header: 'Tensões' },
  { key: 'tipoItem', header: 'Tipo', opcoes: TIPOS_ITEM, exibir: rotulo(TIPOS_ITEM, 'tipoItem') },
  { key: 'codigoItem', header: 'Código do item', priority: 1 },
  { key: 'operacao', header: 'Operação', opcoes: OPERACOES, exibir: rotulo(OPERACOES, 'operacao') },
  { key: 'quantidade', header: 'Qtd.' },
  { key: 'unidade', header: 'Un.' }
];

export function fonteKits(servico: ObrasKitService): FonteImportacao {
  return {
    validar: (arquivo, idContrato) => comoLinhas(servico.validarImportacaoKits(idContrato!, arquivo)),
    revalidar: (registros, idContrato) =>
      comoLinhas(servico.revalidarImportacaoKits({ idContrato, registros: deLinhas<KitImportacaoLinha>(registros) })),
    confirmar: (registros, idContrato, nomeArquivo) =>
      comoLinhas(servico.confirmarImportacaoKits({ idContrato, nomeArquivo, registros: deLinhas<KitImportacaoLinha>(registros) }))
  };
}

// ═══════════════════════════════════════════════════════════════════════
// Carteira
// ═══════════════════════════════════════════════════════════════════════

export const COLUNAS_CARTEIRA: ColunaPlanilha[] = [
  { key: 'numero', header: 'Número', priority: 1 },
  { key: 'obraNova', header: 'Obra', editavel: false, exibir: (r) => (r['obraNova'] == null ? '—' : r['obraNova'] ? 'Nova' : 'Existente') },
  { key: 'titulo', header: 'Título' },
  { key: 'tensao', header: 'Tensão', opcoes: TENSOES, exibir: rotulo(TENSOES, 'tensao') },
  { key: 'prioridade', header: 'Prioridade', opcoes: PRIORIDADES, exibir: rotulo(PRIORIDADES, 'prioridade') },
  { key: 'prazo', header: 'Prazo' },
  { key: 'prazoRegulado', header: 'Prazo regulado' },
  { key: 'lotePrevisto', header: 'Lote' },
  { key: 'valorProjeto', header: 'Valor de projeto' },
  { key: 'valorMaterialPrevisto', header: 'Material previsto' },
  { key: 'valorServicoPrevisto', header: 'Serviço previsto' },
  { key: 'latitude', header: 'Latitude' },
  { key: 'longitude', header: 'Longitude' },
  { key: 'dataInicio', header: 'Início' },
  { key: 'processoNegocio', header: 'Processo' },
  { key: 'baseOperacional', header: 'Base' },
  { key: 'municipio', header: 'Município' },
  { key: 'centroCusto', header: 'Centro de custo' },
  { key: 'supervisor', header: 'Supervisor' },
  { key: 'coordenador', header: 'Coordenador' },
  { key: 'statusObra', header: 'Status da obra' },
  { key: 'tipoObra', header: 'Tipo' }
];

export function fonteCarteira(servico: ObrasKitService): FonteImportacao {
  return {
    validar: (arquivo, idContrato) => comoLinhas(servico.validarImportacaoCarteira(idContrato!, arquivo)),
    revalidar: (registros, idContrato) =>
      comoLinhas(servico.revalidarImportacaoCarteira({ idContrato, registros: deLinhas<CarteiraImportacaoLinha>(registros) })),
    confirmar: (registros, idContrato, nomeArquivo) =>
      comoLinhas(servico.confirmarImportacaoCarteira({ idContrato, nomeArquivo, registros: deLinhas<CarteiraImportacaoLinha>(registros) }))
  };
}

// ═══════════════════════════════════════════════════════════════════════
// Serviços e orçamento previstos
// ═══════════════════════════════════════════════════════════════════════

export const COLUNAS_ORCAMENTO: ColunaPlanilha[] = [
  { key: 'numeroObra', header: 'Obra', priority: 1 },
  { key: 'codigoServico', header: 'Código do serviço', priority: 1 },
  { key: 'descricao', header: 'Descrição' },
  { key: 'unidade', header: 'Un.' },
  { key: 'quantidade', header: 'Qtd.' },
  { key: 'valorUnitario', header: 'Valor unitário' },
  { key: 'valorTotal', header: 'Valor total' },
  { key: 'valorMaterialPrevisto', header: 'Material previsto (obra)' },
  { key: 'valorServicoPrevisto', header: 'Serviço previsto (obra)' }
];

export function fonteOrcamento(servico: ObrasKitService): FonteImportacao {
  return {
    validar: (arquivo) => comoLinhas(servico.validarImportacaoOrcamento(arquivo)),
    revalidar: (registros) => comoLinhas(servico.revalidarImportacaoOrcamento({ registros: deLinhas<OrcamentoImportacaoLinha>(registros) })),
    confirmar: (registros, _idContrato, nomeArquivo) =>
      comoLinhas(servico.confirmarImportacaoOrcamento({ nomeArquivo, registros: deLinhas<OrcamentoImportacaoLinha>(registros) }))
  };
}
