import { FiltroCategoria, ManualModulo } from './central-treinamento.models';

export const FILTROS_CATEGORIA: FiltroCategoria[] = [
  { id: 'TODOS', label: 'Todos os Módulos', icone: 'pi pi-th-large' },
  { id: 'GERAL', label: 'Geral / Acesso', icone: 'pi pi-desktop' },
  { id: 'SST_WEB', label: 'Segurança - Web', icone: 'pi pi-shield' },
  { id: 'MOBILE', label: 'Mobile / Campo', icone: 'pi pi-mobile' }
];

export const MODULOS_TREINAMENTO: ManualModulo[] = [
  {
    id: '00',
    numeroOficial: '[00]',
    titulo: 'Introdução ao Sistema - Acesso, Personalização, Navegação e Copiloto',
    tituloCurto: 'Introdução ao sistema',
    subtitulo: 'Primeiro acesso, credenciais padrão, temas, atalhos globais e suporte inteligente',
    categoria: 'GERAL',
    categoriaRotulo: 'Geral / Login',
    contextoTag: 'Geral / Web',
    icone: 'pi pi-desktop',
    objetivo: 'Apresentar a interface inicial da aplicação, credenciais de primeiro acesso, recursos de acessibilidade e performance, central de busca e suporte inteligente.',
    videoId: '36bYYILpQH4',
    videoUrlOriginal: 'http://www.youtube.com/watch?v=36bYYILpQH4',
    palavrasChave: ['login', 'cpf', 'senha', 'copiloto', 'dark mode', 'ctrl+k', 'tema', 'favoritos', 'performance', 'notificacoes', 'menu', 'nexo2026'],
    alertasPrincipais: [
      {
        tipo: 'seguranca',
        titulo: 'Troca de Senha Obrigatória no Primeiro Acesso',
        mensagem: 'A senha provisória @Empresa2026 deve ser alterada logo no primeiro login para garantir a segurança da conta.',
        icone: 'pi pi-lock'
      },
      {
        tipo: 'dica',
        titulo: 'Busca Global do ERP via Teclado (Ctrl + K)',
        mensagem: 'Pressione Ctrl + K em qualquer tela para abrir a busca global do cabeçalho e saltar diretamente para qualquer formulário ou menu.',
        icone: 'pi pi-bolt'
      }
    ],
    secoes: [
      {
        numero: '1',
        titulo: 'Primeiro Acesso e Credenciais',
        icone: 'pi pi-id-card',
        subtitulo: 'Regras de usuário padrão, senha provisória e segurança',
        itens: [
          {
            rotulo: 'Usuário Padrão',
            descricao: 'Composto pela sigla USR seguida dos 6 últimos dígitos do CPF do colaborador.',
            icone: 'pi pi-user',
            destaque: 'USR + 6 últimos dígitos',
            comando: 'USR123456'
          },
          {
            rotulo: 'Senha Provisória',
            descricao: 'Senha padrão inicial com S maiúsculo (@Empresa2026).',
            icone: 'pi pi-key',
            destaque: 'Senha padrão inicial',
            comando: '@Empresa2026'
          },
          {
            rotulo: 'Troca Obrigatória',
            descricao: 'Acesse o perfil no canto superior direito e cadastre uma nova senha pessoal.',
            icone: 'pi pi-shield',
            badge: 'Obrigatório'
          }
        ]
      },
      {
        numero: '2',
        titulo: 'Barra Superior e Usabilidade (UI/UX)',
        icone: 'pi pi-sliders-h',
        subtitulo: 'Temas, acessibilidade, favoritos e atalhos rápidos',
        itens: [
          {
            rotulo: 'Modo Claro / Escuro',
            descricao: 'Alternância rápida entre tema Claro e Escuro (Beta) no cabeçalho superior.',
            icone: 'pi pi-sun'
          },
          {
            rotulo: 'Paleta de Cores',
            descricao: 'Personalize a cor primária de destaque (Azul, Esmeralda, Violeta, Âmbar, Rosa ou Grafite).',
            icone: 'pi pi-palette'
          },
          {
            rotulo: 'Modo Desempenho',
            descricao: 'Desative animações gráficas para melhorar a resposta em computadores modestos.',
            icone: 'pi pi-bolt',
            badge: 'Performance'
          },
          {
            rotulo: 'Favoritos Rápidos',
            descricao: 'Fixe telas frequentes com a estrela do cabeçalho para acesso em 1 clique.',
            icone: 'pi pi-star'
          },
          {
            rotulo: 'Notificações em Tempo Real',
            descricao: 'Alertas internos com link direto para aprovações de compras e SST.',
            icone: 'pi pi-bell'
          },
          {
            rotulo: 'Busca Global (Ctrl + K)',
            descricao: 'Atalho universal no teclado para pesquisar e navegar para qualquer tela.',
            icone: 'pi pi-search',
            atalho: 'Ctrl + K'
          }
        ]
      },
      {
        numero: '3',
        titulo: 'Menu Retrátil Lateral',
        icone: 'pi pi-bars',
        subtitulo: 'Navegação em árvore de módulos da empresa',
        itens: [
          {
            rotulo: 'Estrutura Modular',
            descricao: 'Expanda ou recolha o menu para navegar por Administração, Execução, Segurança, Financeiro, etc.',
            icone: 'pi pi-sitemap'
          }
        ]
      },
      {
        numero: '4',
        titulo: 'Assistente Virtual "Copiloto"',
        icone: 'pi pi-comments',
        subtitulo: 'Inteligência artificial para apoio operacional',
        itens: [
          {
            rotulo: 'Copiloto no menu lateral',
            descricao: 'Tela própria de IA, no item "Copiloto" do menu lateral: consulta dados reais do Nexo, compara períodos, gera dashboards, relatórios em PDF e planilhas, lê arquivos e mostra cada etapa do trabalho.',
            icone: 'pi pi-sparkles'
          }
        ]
      }
    ]
  },
  {
    id: '07-1',
    numeroOficial: '[07.1]',
    titulo: 'Inspeções de Segurança - Lançamento, Relatório e Tratamento de Desvios (Web)',
    tituloCurto: 'Lançar inspeções de segurança',
    subtitulo: 'Preenchimento de checklists, geolocalização no mapa, fotos obrigatórias e tratativas imediatas',
    categoria: 'SST_WEB',
    categoriaRotulo: 'Segurança - Web',
    contextoTag: 'Web',
    icone: 'pi pi-shield',
    objetivo: 'Guiar o inspetor e a equipe de campo no preenchimento correto de formulários de inspeção de segurança na versão desktop, registro de não conformidades e emissão do relatório gerencial.',
    videoId: 's9i1wGnd_98',
    videoUrlOriginal: 'http://www.youtube.com/watch?v=s9i1wGnd_98',
    palavrasChave: ['inspeção', 'checklist', 'sst', 'desvios', 'gps', 'geolocalização', 'foto', 'evidencia', 'relatorio', 'nao conformidade'],
    alertasPrincipais: [
      {
        tipo: 'atencao',
        titulo: 'Evidência Fotográfica Geral Obrigatória',
        mensagem: 'É obrigatório anexar ao menos uma foto panorâmica que comprove a inspeção para liberar o botão de envio.',
        icone: 'pi pi-camera'
      },
      {
        tipo: 'atencao',
        titulo: 'Alerta por E-mail em Desvios Abertos',
        mensagem: 'Desvios que não forem sanados de imediato geram notificações automáticas para a supervisão e gerência.',
        icone: 'pi pi-envelope'
      }
    ],
    secoes: [
      {
        numero: '1',
        titulo: 'Iniciando uma Nova Inspeção',
        icone: 'pi pi-plus-circle',
        subtitulo: 'Rotas de acesso e geolocalização precisa',
        itens: [
          {
            rotulo: 'Rotas de Acesso',
            descricao: 'Menu Segurança > Minhas Ações > Nova Inspeção; ou Menu Gestão > Inspeções > Botão "Nova Inspeção".',
            icone: 'pi pi-compass'
          },
          {
            rotulo: 'Geolocalização & Mapa',
            descricao: 'O GPS captura o ponto atual, permitindo pesquisar endereços ou arrastar o marcador para o local exato.',
            icone: 'pi pi-map-marker'
          }
        ]
      },
      {
        numero: '2',
        titulo: 'Cabeçalho e Dados Gerais',
        icone: 'pi pi-file-edit',
        subtitulo: 'Checklist, frente de serviço e comprovação',
        itens: [
          {
            rotulo: 'Modelo de Formulário',
            descricao: 'Selecione o formulário adequado (Inspeção de Campo, Câmaras, Alojamentos, Frota, etc.).',
            icone: 'pi pi-list'
          },
          {
            rotulo: 'Campos Obrigatórios (*)',
            descricao: 'Processo de Inspeção, Data/Hora da checagem e Base Operacional vinculada.',
            icone: 'pi pi-check'
          },
          {
            rotulo: 'Equipe Inspecionada',
            descricao: 'Carregue automaticamente pela Equipe cadastrada ou insira colaboradores manualmente.',
            icone: 'pi pi-users'
          },
          {
            rotulo: 'Foto Comprobatória Geral',
            descricao: 'O botão de envio permanece bloqueado até inclusão de pelo menos 1 foto panorâmica.',
            icone: 'pi pi-camera',
            badge: 'Bloqueante'
          }
        ]
      },
      {
        numero: '3',
        titulo: 'Checklist e Apontamento de Desvios',
        icone: 'pi pi-check-square',
        subtitulo: 'Geração automática de não conformidades',
        itens: [
          {
            rotulo: 'Respostas do Checklist',
            descricao: 'Responda cada pergunta do formulário com Sim, Não ou Não se Aplica.',
            icone: 'pi pi-list-check'
          },
          {
            rotulo: 'Desvio Automático ao Marcar "Não"',
            descricao: 'Abre campos de tratativa: Descrição da falha, Ação Corretiva (ex: reorientação), Prazo, Responsáveis e Foto do Desvio.',
            icone: 'pi pi-exclamation-triangle',
            badge: 'Automático'
          }
        ]
      },
      {
        numero: '4',
        titulo: 'Relatório Pós-Envio e Tratamento Imediato',
        icone: 'pi pi-file-check',
        subtitulo: 'Tratamento no ato e recursos de visualização',
        itens: [
          {
            rotulo: 'Tratamento no Ato',
            descricao: 'Se corrigido na hora, clique em "Tratar", mude status para Resolvido e salve para evitar notificações.',
            icone: 'pi pi-bolt'
          },
          {
            rotulo: 'Recursos do Relatório',
            descricao: 'Fotos separadas por abas (Inspeção vs. Desvios), mapa com localização, atalho lateral Ctrl + K e impressão limpa.',
            icone: 'pi pi-images'
          }
        ]
      }
    ]
  },
  {
    id: '07-2',
    numeroOficial: '[07.2]',
    titulo: 'Desvios e Gestão - Tratamento em Lote, Consulta e Exportação (Web)',
    tituloCurto: 'Gestão de desvios',
    subtitulo: 'Painel de acompanhamento, encerramento em massa de desvios e integração com Power BI',
    categoria: 'SST_WEB',
    categoriaRotulo: 'Segurança - Web',
    contextoTag: 'Web',
    icone: 'pi pi-sliders-h',
    objetivo: 'Instruir supervisores e líderes de segurança no acompanhamento de indicadores, encerramento de desvios em massa e exportação de dados para relatórios gerenciais e Power BI.',
    videoId: 'UQnaDblsmic',
    videoUrlOriginal: 'http://www.youtube.com/watch?v=UQnaDblsmic',
    palavrasChave: ['desvios', 'em lote', 'tratar selecao', 'exportar', 'power bi', 'csv', 'gestao', 'supervisao', 'indicadores', 'dds'],
    alertasPrincipais: [
      {
        tipo: 'dica',
        titulo: 'Encerramento em Lote para Ações Coletivas',
        mensagem: 'Use o botão "Tratar Seleção" para encerrar múltiplos desvios corrigidos em conjunto (ex: em DDS) com justificativa única.',
        icone: 'pi pi-check-circle'
      }
    ],
    secoes: [
      {
        numero: '1',
        titulo: 'Painel "Meus Desvios" e Tratativas',
        icone: 'pi pi-filter',
        subtitulo: 'Status dedicados e tratamento individual ou em massa',
        itens: [
          {
            rotulo: 'Abas por Status',
            descricao: 'Segmentação imediata entre Abertos, Em Tratamento, Resolvidos e Vencidos.',
            icone: 'pi pi-tags'
          },
          {
            rotulo: 'Tratamento Individual',
            descricao: 'Clique em "Tratar Desvio", relate a ação realizada e defina o status como Já Corrigido.',
            icone: 'pi pi-pencil'
          },
          {
            rotulo: 'Tratamento em Lote ("Tratar Seleção")',
            descricao: 'Marque múltiplos desvios (ou a página inteira), clique em "Tratar Seleção", escolha o status e informe a justificativa única.',
            icone: 'pi pi-clone',
            badge: 'Produtividade'
          }
        ]
      },
      {
        numero: '2',
        titulo: 'Gestão Global e Exportação',
        icone: 'pi pi-table',
        subtitulo: 'Consulta avançada e download em planilhas',
        itens: [
          {
            rotulo: 'Minhas vs. Todas as Inspeções',
            descricao: 'Alterne rapidamente entre suas próprias inspeções e a base corporativa inteira.',
            icone: 'pi pi-users'
          },
          {
            rotulo: 'Filtros Combinados',
            descricao: 'Busca por modelo de formulário, múltiplos processos simultâneos, centro de custo e base operacional.',
            icone: 'pi pi-filter'
          },
          {
            rotulo: 'Download em .CSV para Power BI',
            descricao: 'Clique em "Exportar", escolha Inspeções ou Desvios (Resumido ou Detalhado) e aplique o intervalo de datas.',
            icone: 'pi pi-download'
          }
        ]
      },
      {
        numero: '3',
        titulo: 'Auditoria Detalhada de Desvios',
        icone: 'pi pi-search',
        subtitulo: 'Rastreabilidade de checklists e fotos',
        itens: [
          {
            rotulo: 'Visualizar Detalhes',
            descricao: 'Consulte a pergunta exata que originou o desvio, colaboradores atribuídos, fotos de antes/depois e link para o relatório completo.',
            icone: 'pi pi-eye'
          }
        ]
      }
    ]
  },
  {
    id: '07-1-1',
    numeroOficial: '[07.1.1]',
    titulo: 'Inspeções de Segurança - Instalação do Aplicativo Mobile (PWA)',
    tituloCurto: 'Instalar o aplicativo mobile',
    subtitulo: 'Instalação direta no Android e iOS sem lojas de apps, funcionando em tela cheia idêntico a app nativo',
    categoria: 'MOBILE',
    categoriaRotulo: 'Mobile / Campo',
    contextoTag: 'Mobile',
    icone: 'pi pi-mobile',
    objetivo: 'Orientar o colaborador de campo na instalação do aplicativo progressivo (PWA) do Nexo em smartphones Android e iOS sem a necessidade de baixar arquivos externos ou lojas de aplicativos.',
    videoId: '8LbxF0v6p9k',
    videoUrlOriginal: 'http://www.youtube.com/watch?v=8LbxF0v6p9k',
    palavrasChave: ['pwa', 'mobile', 'celular', 'instalacao', 'atalho', 'android', 'ios', 'safari', 'chrome', 'app'],
    alertasPrincipais: [
      {
        tipo: 'dica',
        titulo: 'Vantagens do PWA da empresa',
        mensagem: 'Dispensa lojas de aplicativos (Google Play e App Store), ocupa pouquíssimo espaço na memória e atualiza instantaneamente.',
        icone: 'pi pi-sparkles'
      }
    ],
    secoes: [
      {
        numero: '1',
        titulo: 'Passo a Passo de Instalação no Celular',
        icone: 'pi pi-download',
        subtitulo: 'Instalação rápida direto pelo navegador do smartphone',
        itens: [
          {
            rotulo: 'Acesso Inicial pelo Navegador',
            descricao: 'Abra o Chrome no Android ou Safari no iPhone e acesse o endereço web direto da aplicação Nexo Mobile.',
            icone: 'pi pi-globe',
            destaque: 'Chrome ou Safari'
          },
          {
            rotulo: 'Criação do Atalho sem Login',
            descricao: 'A instalação do ícone na tela inicial pode ser realizada antes mesmo de informar seu usuário e senha.',
            icone: 'pi pi-user-plus',
            destaque: 'Sem necessidade de login prévio'
          },
          {
            rotulo: 'Adicionar à Tela de Início',
            descricao: 'No Android (Chrome): toque nos 3 pontos e selecione "Instalar aplicativo". No iOS (Safari): toque no botão Compartilhar e selecione "Adicionar à Tela de Início".',
            icone: 'pi pi-plus-circle',
            destaque: 'Android e iOS'
          },
          {
            rotulo: 'Pronto para Operar em Tela Cheia',
            descricao: 'O ícone oficial do Nexo estará disponível na grade de aplicativos do smartphone, abrindo em tela cheia idêntico a um aplicativo nativo.',
            icone: 'pi pi-check-circle',
            badge: 'PWA Nativo'
          }
        ]
      }
    ]
  },
  {
    id: '07-1-2',
    numeroOficial: '[07.1.2]',
    titulo: 'Inspeções de Segurança - Lançamento de Inspeções Offline (Mobile)',
    tituloCurto: 'Inspeções offline em campo',
    subtitulo: 'Operação sem sinal celular, cache local prévio, salvamento preventivo e sincronização automática',
    categoria: 'MOBILE',
    categoriaRotulo: 'Mobile / Campo',
    contextoTag: 'Mobile / Offline',
    icone: 'pi pi-wifi',
    objetivo: 'Capacitar as equipes de campo para o lançamento completo de inspeções em locais sem sinal de internet, gerenciamento de cache local e rotinas de sincronização.',
    videoId: '8t4Ox3voVzk',
    videoUrlOriginal: 'http://www.youtube.com/watch?v=8t4Ox3voVzk',
    palavrasChave: ['offline', 'sincronizacao', 'campo', 'cache', 'sem sinal', 'salvar', 'rascunho', 'committed', 'queued', 'synced', 'fotos'],
    alertasPrincipais: [
      {
        tipo: 'regra-ouro',
        titulo: 'Regra de Ouro: Sincronismo Prévio Obrigatório',
        mensagem: 'Antes de sair para o campo sem sinal, abra o app com Wi-Fi/4G e faça login para baixar formulários e cadastros.',
        icone: 'pi pi-sync'
      },
      {
        tipo: 'seguranca',
        titulo: 'Salvamento Preventivo de Rascunho',
        mensagem: 'Toque no botão "Salvar" no topo para manter rascunhos em andamento salvos no dispositivo, resguardando fotos se a bateria acabar.',
        icone: 'pi pi-save'
      }
    ],
    secoes: [
      {
        numero: '1',
        titulo: 'Regra Fundamental da Operação Offline',
        icone: 'pi pi-sync',
        subtitulo: 'Garantia de cache local antes de sair da base',
        itens: [
          {
            rotulo: 'Sincronismo Prévio no Wi-Fi',
            descricao: 'Conecte-se à internet, faça login e espere a sincronização inicial armazenar formulários e equipes na memória local.',
            icone: 'pi pi-wifi'
          },
          {
            rotulo: 'Painel de Conectividade',
            descricao: 'Identifica o status "Modo Offline", data/hora da última sincronização e quantidade de inspeções pendentes na fila.',
            icone: 'pi pi-info-circle'
          }
        ]
      },
      {
        numero: '2',
        titulo: 'Criação e Preenchimento em Campo',
        icone: 'pi pi-pencil',
        subtitulo: 'Preenchimento tátil e captura direta com câmera',
        itens: [
          {
            rotulo: 'Abertura & Gestos Táteis',
            descricao: 'Toque no botão flutuante "+" ou aba Inspeções. A tela suporta gestos de arrasto (swipe lateral) para alternar etapas.',
            icone: 'pi pi-hand'
          },
          {
            rotulo: 'Dados Gerais & Veículo',
            descricao: 'Escolha formulário e processo. Defina base operacional, placa do veículo e insira colaboradores inspecionados.',
            icone: 'pi pi-car'
          },
          {
            rotulo: 'Checklist Ágil',
            descricao: 'Responda as questões do formulário de forma ágil com botões tácteis de toque amplo.',
            icone: 'pi pi-check'
          },
          {
            rotulo: 'Desvios & Câmera do Celular',
            descricao: 'Aponte a tratativa recomendada, defina o prazo e capture a foto na hora com a câmera do smartphone.',
            icone: 'pi pi-camera'
          },
          {
            rotulo: 'Salvamento Preventivo ("Salvar")',
            descricao: 'Toque em "Salvar" no topo para resguardar fotos e respostas no armazenamento local (committed).',
            icone: 'pi pi-save',
            badge: 'Segurança'
          },
          {
            rotulo: 'Foto Comprobatória e Envio',
            descricao: 'Anexe a foto panorâmica obrigatória e toque em "Finalizar".',
            icone: 'pi pi-send'
          }
        ]
      },
      {
        numero: '3',
        titulo: 'Fila de Sincronização Automática',
        icone: 'pi pi-cloud-upload',
        subtitulo: 'Como os dados sobem com segurança para a nuvem',
        itens: [
          {
            rotulo: 'Aguardando Envio (queued)',
            descricao: 'Sem conexão móvel, o registro permanece seguro no banco de dados local do smartphone.',
            icone: 'pi pi-clock'
          },
          {
            rotulo: 'Envio Automático ao Reconectar (synced)',
            descricao: 'Ao retornar para área com sinal ou Wi-Fi, o aplicativo sincroniza tudo sozinho (ou force em "Sincronizar").',
            icone: 'pi pi-cloud-upload',
            badge: 'Automático'
          }
        ]
      }
    ]
  },
  {
    id: '07-1-3',
    numeroOficial: '[07.1.3]',
    titulo: 'Desvios e Gestão - Tratamento pelo Celular e Atualizações (Mobile)',
    tituloCurto: 'Desvios pelo celular',
    subtitulo: 'Tratativa ágil de desvios em campo, filtros por gravidade e atualização da versão PWA',
    categoria: 'MOBILE',
    categoriaRotulo: 'Mobile / Campo',
    contextoTag: 'Mobile',
    icone: 'pi pi-refresh',
    objetivo: 'Instruir sobre o tratamento ágil de desvios pelo celular, filtros dinâmicos de campo e procedimento de atualização da versão da aplicação móvel.',
    videoId: 'OKe2iioERjg',
    videoUrlOriginal: 'http://www.youtube.com/watch?v=OKe2iioERjg',
    palavrasChave: ['desvios mobile', 'atualizacao', 'versao', 'gravidade', 'tratar selecao', 'perfil', 'filtro', 'campo', 'supervisor'],
    alertasPrincipais: [
      {
        tipo: 'dica',
        titulo: 'Checagem Periódica de Atualização',
        mensagem: 'Na aba Perfil, toque em "Buscar Atualização" regularmente conectado ao Wi-Fi para receber os checklists mais recentes.',
        icone: 'pi pi-arrow-circle-down'
      }
    ],
    secoes: [
      {
        numero: '1',
        titulo: 'Tratamento de Desvios na Interface Mobile',
        icone: 'pi pi-sliders-v',
        subtitulo: 'Solução rápida de inconformidades direto no smartphone',
        itens: [
          {
            rotulo: 'Aba Desvios & Filtros Rápidos',
            descricao: 'Filtre por Gravidade (Leve, Média, Crítica), Vencidos, Processo ou Base Operacional.',
            icone: 'pi pi-filter'
          },
          {
            rotulo: 'Tratar Seleção em Massa',
            descricao: 'Marque os desvios que receberam a mesma ação, toque em "Tratar Seleção", justifique e salve.',
            icone: 'pi pi-check-circle',
            badge: 'Em Massa'
          }
        ]
      },
      {
        numero: '2',
        titulo: 'Perfil e Atualizações da Aplicação',
        icone: 'pi pi-user',
        subtitulo: 'Identificação do operador e download da versão mais recente',
        itens: [
          {
            rotulo: 'Dados do Usuário',
            descricao: 'Consulte seu ID de operador, credencial, polo de trabalho e data da última sincronização.',
            icone: 'pi pi-id-card'
          },
          {
            rotulo: 'Botão "Buscar Atualização"',
            descricao: 'Baixa a versão mais recente dos formulários e regras publicados pela TI, prevenindo inconsistências.',
            icone: 'pi pi-arrow-circle-down',
            badge: 'Importante'
          }
        ]
      }
    ]
  }
];
