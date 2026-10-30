import { TipoCatalogoSst } from '../../../core/models/sst-evento.models';

export const CATALOGO_SST_PAGE_SIZE_OPTIONS: number[] = [10, 25, 50, 100];

export interface CatalogoSstPageConfig {
  titulo: string;
  entityLabel: string;
  colunaNome: string;
  nomeLabel: string;
  nomePlaceholder: string;
  nomeAjuda: string;
  dialogNovo: string;
  dialogEditar: string;
  botaoCadastrar: string;
  vazio: string;
  icone: string;
}

/**
 * A tabela é genérica apenas no backend. A rota fixa o tipo e apresenta cada
 * vocabulário como um cadastro independente, sem expor essa decisão técnica.
 */
export const CATALOGO_SST_PAGE_CONFIG: Record<TipoCatalogoSst, CatalogoSstPageConfig> = {
  CLASSIFICACAO_EVENTO: {
    titulo: 'Classificações de Eventos',
    entityLabel: 'Nova classificação',
    colunaNome: 'Classificação',
    nomeLabel: 'Classificação do evento',
    nomePlaceholder: 'Ex.: Acidente com afastamento',
    nomeAjuda: 'Informe como o evento deve ser classificado nos registros e relatórios.',
    dialogNovo: 'Nova classificação de evento',
    dialogEditar: 'Editar classificação de evento',
    botaoCadastrar: 'Cadastrar classificação',
    vazio: 'Nenhuma classificação encontrada',
    icone: 'pi pi-tags'
  },
  LOCAL_LESAO: {
    titulo: 'Locais de Lesão',
    entityLabel: 'Novo local de lesão',
    colunaNome: 'Local da lesão',
    nomeLabel: 'Local da lesão',
    nomePlaceholder: 'Ex.: Mão',
    nomeAjuda: 'Informe a parte do corpo atingida de forma objetiva.',
    dialogNovo: 'Novo local de lesão',
    dialogEditar: 'Editar local de lesão',
    botaoCadastrar: 'Cadastrar local',
    vazio: 'Nenhum local de lesão encontrado',
    icone: 'pi pi-map-marker'
  },
  NATUREZA_LESAO: {
    titulo: 'Naturezas de Lesão',
    entityLabel: 'Nova natureza de lesão',
    colunaNome: 'Natureza da lesão',
    nomeLabel: 'Natureza da lesão',
    nomePlaceholder: 'Ex.: Corte ou laceração',
    nomeAjuda: 'Informe o tipo de lesão sofrida pelo acidentado.',
    dialogNovo: 'Nova natureza de lesão',
    dialogEditar: 'Editar natureza de lesão',
    botaoCadastrar: 'Cadastrar natureza',
    vazio: 'Nenhuma natureza de lesão encontrada',
    icone: 'pi pi-heart'
  },
  AGENTE_CAUSADOR: {
    titulo: 'Agentes Causadores',
    entityLabel: 'Novo agente causador',
    colunaNome: 'Agente causador',
    nomeLabel: 'Agente causador',
    nomePlaceholder: 'Ex.: Ferramenta manual',
    nomeAjuda: 'Informe o objeto, equipamento, material ou energia que causou o evento.',
    dialogNovo: 'Novo agente causador',
    dialogEditar: 'Editar agente causador',
    botaoCadastrar: 'Cadastrar agente',
    vazio: 'Nenhum agente causador encontrado',
    icone: 'pi pi-bolt'
  }
};
