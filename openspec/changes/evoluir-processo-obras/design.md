## Context

O processo de Obras do Nexo (módulo `obras`) tem Obra com subprojetos, relacionamentos, anexos tipados, status e agente
responsável, além de importação de obras. O módulo `execucao` tem Turno (equipe, veículo, colaboradores, abertura/fechamento) e a
execução comercial por nota; o frontend tem listagem/detalhamento de turnos e o dashboard de turnos. `contrato` tem Ação com valor
versionado por vigência (`AcaoValor`), `estoque` tem Material, Estoque, Movimentação e Solicitação de Material (incluindo
solicitação técnica), `rh` tem Equipe, TipoEquipe, BaseOperacional (com coordenadas) e MetaFaturamentoEquipe, e `frota` integra
provedores de telemetria.

A operação usa hoje um sistema externo de referência para o ciclo técnico (planejamento sobre o PDF, programação, medição). O
levantamento desse sistema (somente leitura) serviu de insumo funcional; a interface segue o Design System Nexo e não copia a
referência. A execução de campo no app fica para a change `execucao-campo-obras`.

Restrições: white label, módulos isolados por eventos, BaseEntity com soft delete e versão, Problem Details, Design System
source-owned, offline-first no app, sem regressão em Obra/Turno comercial/Equipe/Solicitação de Material.

## Goals / Non-Goals

**Goals:**
- Modelo de dados único para o ciclo planejar → programar → executar → medir, com valores congelados e rastreáveis.
- Editor de planejamento sobre PDF fluido para obras grandes (centenas de pontos por obra, PDFs multipágina).
- Frentes de trabalho independentes após a fundação (kits + modelo de planejamento), permitindo desenvolvimento em paralelo.
- Reaproveitar Turno, dashboard de turnos, estoque/solicitação e telemetria existentes, estendendo-os.

**Non-Goals:**
- Execução e evidências no app de campo, avaliação de fotos, status em tempo real da equipe, georreferenciamento automático
  (etapa 2, change `execucao-campo-obras`).
- Valoração em US (unidade de serviço).
- OCR de PDFs escaneados (a extração de texto usa só a camada de texto do PDF; ver Open Questions).
- Módulo de homologação/tickets.

## Decisions

### D1. Kits como subdomínio de `obras`, referências por UUID
Kit, ItemKit ficam em `com.nexo.app.modules.obras` (pacote de domínio próprio), referenciando Ação (`contrato`) e Material
(`estoque`) por UUID, com leitura via portas/adapters já usados pelo módulo (`*ApiAdapter`).
- Alternativa: módulo `kits` separado. Rejeitada: o kit só existe para planejar obras; um módulo a mais aumenta eventos sem ganho.
- Alternativa: kit dentro de `contrato`. Rejeitada: mistura catálogo comercial com engenharia de obra.

### D2. Agregado de planejamento por obra
`PlanejamentoObra` (1:1 com Obra) é a raiz de: Ponto, Vao, KitAplicado, ItemPlanejado, Anotacao e preferências do projeto mestre
(rotação por página). Invariantes no domínio: número de ponto único, vão sem auto-ligação/duplicata, remoção bloqueada com item
programado/executado, quantidade prevista = qtd do kit × multiplicador.
- Gravação por comandos pequenos (adicionar ponto, mover, aplicar kit, ...) com `versao` por entidade (optimistic locking), não
  "salvar o planejamento inteiro": evita sobrescrever edição concorrente e mantém o log de auditoria granular.
- Leitura: um endpoint devolve o planejamento completo da obra em DTO compacto (pontos, vãos, kits, itens, programações
  resumidas), para o editor montar a árvore sem N chamadas.

### D3. Coordenadas normalizadas no espaço da página não rotacionada
Ponto/vão/anotação guardam `pagina`, `x`, `y` em [0,1] relativos à página do PDF sem rotação. O frontend converte para a tela
aplicando rotação do PDF + rotação salva. Assim rotação e zoom nunca alteram dados, e a rotação vira preferência do anexo
(persistida no servidor, não no navegador, diferente da referência).
- Alternativa: coordenada relativa ao canvas exibido (como a referência). Rejeitada: acopla dado à rotação de visualização.

### D4. Visualizador com `pdfjs-dist` encapsulado no Design System
Novo componente source-owned no `@ces/ui` (ex.: `ces-pdf-canvas`) cuidando de render, zoom ancorado, pan (mão, botão do meio,
toque), rotação e extração de texto por região (`getTextContent` + `convertToViewportPoint`, ordenação linha/coluna). Overlays
(pontos, trechos SVG, anotações, seleção por área) ficam na feature `obras/planejamento`, posicionados em porcentagem sobre o
canvas. Render só da página atual; worker do PDF.js empacotado localmente (sem CDN).
- Alternativa: biblioteca de viewer pronta (ex.: ngx-extended-pdf-viewer). Rejeitada: pesada, difícil de sobrepor camadas e de
  aderir ao Design System.
- Registro no catálogo e em `prime-ng-coverage.md` junto com o componente.

### D5. Status de item e valores congelados
`ItemPlanejado.status`: PENDENTE → PROGRAMADO → EM_ANDAMENTO → CONCLUIDO → MEDIDO (e volta a PENDENTE ao cancelar/excluir
programação sem execução). O valor unitário é congelado em `ProgramacaoItem` pela vigência da Ação na data de início da
programação (`AcaoValor.dataVigencia`); a medição usa esse valor. Itens sem programação são valorizados pelo vigente hoje apenas
para exibição.

### D6. Programação como agregado próprio com histórico
`Programacao` (obra, equipe, período, condições operacionais, observação, status) com `ProgramacaoItem` e `EventoProgramacao`
(EDITADA, REPROGRAMADA, CANCELADA, EXCLUIDA com antes/depois). Capacidade e meta são calculadas em leitura (não persistidas):
jornada e plataforma vêm de Equipe/TipoEquipe (novos campos com padrão 8h/30min), deslocamento de Obra (D7), meta de
MetaFaturamentoEquipe vigente e dias úteis do período (calendário com feriados configuráveis).

### D7. Deslocamento base → obra via provedor de rotas com cache
Porta `RotaRodoviariaPort` com adaptador para um motor de rotas (OSRM self-hosted ou serviço configurado), envolvido por
Resilience4j e cache Redis por par (base, coordenada arredondada). Falha ou ausência de provedor → fallback haversine × fator de
sinuosidade configurável, marcado como "estimado". Recalculo por evento quando base ou coordenadas mudam.
- Alternativa: só haversine. Rejeitada: distorce capacidade em regiões rurais.

### D8. Turno de Obras como extensão do Turno existente
Turno ganha vínculo opcional com programações do dia, atividades (obra programada/não programada), líder de turma e papel do
componente (titular/substituto), APR, ocorrências e relatório diário, sem alterar o fluxo comercial. `ApontamentoExecucao`
(item, turno opcional, data, quantidade, usuário, justificativa) é a única forma de mudar `quantidadeExecutada`; na etapa 2 o app
grava o mesmo apontamento. O dashboard de turnos ganha a visão por equipe (cartões), reutilizando `dashboard-turnos`.

### D9. Comunicação entre módulos por eventos
`ItemExecutado`, `ProgramacaoConfirmada/Alterada`, `MedicaoGerada/Estornada`, `ObraLocalizacaoAlterada` publicados via Spring
Modulith (`event_publication`). Consumidores: indicadores da obra e da equipe, central de operações, solicitação técnica de
materiais (`estoque`), recálculo de deslocamento.

### D10. Medições
`Medicao` (obra, tipo PARCIAL ou CICLO, ciclo, status GERADA/ESTORNADA) com `MedicaoItem` referenciando `ProgramacaoItem` e
quantidade. Unicidade de item medido garantida por constraint parcial (item ativo em medição não estornada). Relatórios XLSX/PDF
gerados assíncronos com download quando prontos.

### D11. Canteiros sobre o estoque existente
Canteiro de postes = novo tipo de estoque com coordenadas e município; entradas, contagem e movimentos usam `MovimentacaoEstoque`.
Requisição a partir da programação cria solicitação técnica pelo fluxo existente (evento consumido por `estoque`).

### D12. Central de operações e telemetria
Endpoints de leitura agregados (um por bloco do painel, cacheados por 30–60 s). Mapa com Leaflet já presente; posições de
veículos via `frota`. Atualização periódica por polling (30 s) nesta etapa; STOMP fica como evolução.

### D13. Permissões
`obras:kit:{read,write,import}`, `obras:planejamento:{read,write}`, `obras:programacao:{read,write}`,
`obras:execucao:write`, `obras:medicao:{read,write}`, `obras:documentacao:{read,write}`, `obras:central:read`, inseridas por
migração e consumidas por `authGuard`.

### Decisões tomadas na implementação (frente A)
- Classes de tensão: enum `ClasseTensao` (BT, MT_15, MT_25, MT_36, AT_72, AT_138). Kit sem classe vale para todas.
- Kit: contrato e código imutáveis após o cadastro; itens removidos são desativados (soft delete). Valor do kit usa
  `ContratoApi.listarAcoesComValor` (já com coeficiente do contrato); serviço sem valor vigente fica fora da soma e é contado
  em `servicosSemValor`.
- Importações (kits, carteira, orçamento) seguem o fluxo das demais importações do sistema, no `ces-import-drawer`:
  `validacao` devolve cada linha com situação e erro sem gravar, `revalidacao` confere de novo depois de cada correção
  na tela e `confirmacao` revalida e processa as válidas em `@Async`, com uma transação por kit/obra. A tabela
  `importacoes_obras` guarda o histórico e os erros por linha (JSONB). Leitura XLSX ou CSV com cabeçalho normalizado e aliases.
- `ces-import-drawer` e `ces-form-drawer` trazidos da `main` do `ces` (largura máxima redimensionável, arrastar e soltar,
  tabela sem colunas espremidas), mais o slot `[import-params]` para parâmetros que vêm antes do arquivo (contrato).
- A ficha da obra (`/obras/:id`) é uma página de aplicação, não A4: cabeçalho com prazo, indicadores, pontos de atenção
  (`obra-insights.ts`, função pura), dados, solicitações, orçamento, anexos, subprojetos, mapa e histórico. As ações ficam
  fixas no canto inferior direito; o que altera a obra fica em "Atualizar obra", e as seções mantêm seus atalhos.
- Carteira: o "prazo" é a `dataPrevisaoFim` existente; novo só `prazoRegulado`. A carteira não tem status próprio: a coluna de situação da planilha alimenta o `StatusObra`
  (a `situacao_carteira`, texto livre, saiu na V201). Status operacional reutiliza `StatusObra`
  com histórico em `historico_status_obras` (gravado pelo domínio em toda troca, inclusive no cadastro atual) + observação.
  Obra ganhou `idContrato` (opcional) para filtrar kits.
- Endpoints de carteira ficam em `CarteiraObraController`/`CarteiraObraUseCases`, sem alterar `ObraRequest`/`ObraUseCases`;
  `ObraResponse` e `ObraResumoResponse` ganharam o campo `carteira`.
- Deslocamento (D7): `RotaRodoviariaPort` + `OsrmRotaRodoviariaAdapter` (desligado sem `ces.obras.rotas.osrm-url`, circuit
  breaker `rotas-obras`, cache `obras-rotas`); fallback haversine × `fator-sinuosidade` (1,3) a `velocidade-media-kmh` (45),
  marcado como estimado. Recalculo por agendamento (`ces.obras.rotas.recalculo-intervalo`, 10 min) das obras cuja base ou
  coordenadas mudaram, em vez de evento, e sob demanda em `POST /v1/obras/{id}/deslocamento/recalcular`.
- Obras próximas: pré-filtro por retângulo no banco e distância em linha reta em memória (padrão 10 km, máximo 100 km).

### Decisões da frente B (estudo do sistema de referência, 30/10/2026)
Regras observadas e mantidas: ponto com execução (não definido, sem ou com desligamento) e barramento; kit com código, itens de
serviço e material, previsto × executado por item e bloqueio quando há programação; aplicação em lote; árvore página → ponto → kit →
itens com alternância só serviços ou serviço + material; "Avanço por kit"; seleção por clique, área e caixas; programar ignora os
itens já programados; materiais acompanham o serviço; grupos de kit (poste, estrutura BT/MT, aterramento, trafo, para-raios, ligação,
chave, vão BT/MT, poda, aprumo, calçada, religador); kit marcado como executado antes do sistema.

Melhorias decididas:
- Metragem do vão: aparece só no vão e escala todo item em metro, inclusive o serviço. Na referência, um vão de 35 m ficou com 36,4 m
  de cabo e 1,04 m de lançamento (R$ 7), e a regra depende do nome do material. Aqui depende da unidade do item.
- Rotação salva no servidor, para todos os usuários (a referência salva no navegador).
- Resumo do ponto sempre dentro da área visível, e barra de ferramentas opaca, fora do desenho.
- Editar execução e barramento só grava ao confirmar ou sair do campo, com aviso de conflito por versão.
- Modelo: `planejamentos_obras` (obra, anexo mestre, rotações) e, por obra, `pontos_planejamento`, `vaos_planejamento`,
  `kits_aplicados`, `itens_planejados`, `anotacoes_planejamento`. Cada entidade tem versão própria e gravação por comando. O item guarda
  cópia de código, descrição, unidade e quantidade do kit, para o catálogo não alterar o que já foi planejado.
- Substituir o PDF mestre troca `id_anexo_mestre`. O anexo anterior segue ativo no histórico.

### Decisões da frente C (programação, 01/11/2026)
- A programação abre em drawer, não em diálogo, como os demais cadastros (D15). Criar nasce só da seleção no planejamento; o Gantt mostra e altera.
- Selecionar um serviço traz os materiais pendentes do mesmo kit. Retirar o último serviço do kit tira os materiais junto. Retirar o último item exclui a programação.
- Reprogramar mantém o valor congelado, mesmo que o início mude de vigência. Quem quiser o valor novo cancela e programa de novo.
- Excluir é recusado quando há execução: apagaria o valor congelado que a medição usa. Nesse caso só cabe cancelar, e o item executado continua na programação cancelada. Cancelar e excluir devolvem os demais itens.
- Status gravado: `PROGRAMADA` ou `CANCELADA`. Atrasada, em execução e concluída são calculadas pelos itens e pela data, sem sincronização.
- Jornada e plataforma ficam em `tipos_equipe` (480 e 30 por padrão no banco), com ajuste opcional em `equipes`. O diálogo lista equipes ativas do mesmo processo da obra.
- Deslocamento sai da base de cada equipe, não da base da obra, com o mesmo provedor e a mesma estimativa de D7. Não fica gravado.
- Dias úteis: segunda a sexta menos feriados nacionais (`JanelaMetaFaturamento`). Feriado municipal fica de fora até a operação pedir. Programação que passa do período conta pela proporção de dias úteis.
- Os eventos `ProgramacaoConfirmada/Alterada` de D9 entram quando houver consumidor (frentes G e H).
- A tela de programação tem três visões do mesmo recorte, como a de solicitações: tabela, semana e mês por equipe. Mostra as equipes livres dos processos que têm obras, indicadores e as obras a programar. Canceladas só aparecem se escolhidas no filtro. O Gantt do sistema de referência estava com erro em 01/11/2026, então a tela não foi comparada com ele.
- Arrastar grava ao soltar, com "Desfazer", e dispensa a observação obrigatória da reprogramação pelo detalhe: a observação automática e os dados de antes e depois ficam no histórico. No toque, arrastar é rolar, então a reprogramação fica no detalhe, que também é o caminho de teclado.
- Filtros da programação rodam na tela, sobre o período carregado; base e tipo de equipe vão ao servidor. Sem situação escolhida valem as "Ativas", sem canceladas. Filtrando a programação, equipe sem barra sai da linha do tempo, porque não está livre, está fora do recorte.
- O aviso de equipe ocupada considera o turno: manhã e tarde no mesmo dia não conflitam.

### Decisões da frente D

- Revisão do usuário em 01/11/2026, que substitui o que vier abaixo em contrário: **o supervisor só programa e analisa**. Abrir e fechar turno, preencher APR, apontar, ocorrência e relatório diário são da equipe, pelo app (change `execucao-campo-obras`). No web, Boletim, Turnos e detalhe são só leitura; saíram as operações de abrir, fechar e corrigir composição em nome da equipe. Os demais comandos ficam na API para o app, com `obras:turno:write` e `obras:execucao:write`, que nenhum perfil recebe até o perfil da equipe existir (V205). Consequência aceita pelo usuário: nada de execução entra no sistema até o app chegar, e a frente F fica sem dados reais.
- O boletim segue a estrutura do dashboard de turnos, no contexto do técnico. O grupo Comercial sai do menu pelos módulos ocultos, sem bloquear as rotas que os dashboards usam; "Metas de faturamento" foi para Cadastros porque a meta do dia de Obras depende dela.
- APR é o formulário feito antes de executar, obrigatório só quando o serviço será realizado. A liberação vale por serviço do dia (`SituacaoLiberacao`; ver "Decisões da frente E"): APR concluída libera o apontamento; ocorrência com `impede_execucao` dispensa a APR e bloqueia o apontamento. A API recusa apontamento sem turno na data.
- As telas ficam no menu Obras (`/obras/turnos`, `/obras/turnos/:id`, `/obras/boletim`), com as peças do comercial, que não muda; o corte automático das 22:00 vale igual ao do comercial.
- Consequência aceita do corte: o fim do encerramento automático sai do último lançamento de nota. Equipe de Obras não lança nota, então turno esquecido fica com zero hora. Por isso, turno lançado para um dia que já passou é aberto e fechado no mesmo passo.
- Equipe de Obras é a de um processo com obras, como no Gantt. Turno de outro processo responde 404 no detalhe de Obras.
- Líder e substitutos ficam no Turno, sem mudar `TurnoResponse`. Substituto é quem está no turno e não é titular na hora de gravar; o cadastro da equipe não muda. Sem líder informado, vale o responsável da equipe.
- `ApontamentoExecucao` é a única origem de `quantidadeExecutada`, e o valor vem do congelado na programação. Passar do previsto exige justificativa por item; o excedente guarda só a parte que passou. O estorno desativa o apontamento e devolve a quantidade; item medido não estorna.
- O primeiro apontamento coloca a obra em EM EXECUÇÃO, com registro no histórico de status. Obra encerrada não reabre.
- Sem turno informado, o apontamento entra no turno da equipe na data. O apontamento também pode ser feito pela programação, sem passar pelo turno.
- Realizado do turno é a soma dos apontamentos dele; meta do dia vem do cadastro de metas, pelo dia da abertura. Turno sem meta (fim de semana, por exemplo) não entra em "bateu" nem em "não bateu".
- Os indicadores da listagem resumem equipe, base, obra, período e busca. Status, meta, relatório e ocorrência só filtram a tabela, então clicar num indicador não muda os números.
- Boletim: "até o dia" conta da abertura do ciclo da meta até o dia; "dia anterior" é o dia útil anterior, para a segunda-feira não mostrar domingo vazio. O programado reparte a programação pelos dias úteis, como no Gantt.
- Agendamento sem turno vale até hoje, para equipe sem nenhum turno no dia. Fim de semana no meio de uma programação longa não conta, salvo se ela começa nesse dia. Equipe que já fechou o turno do dia não entra.
- `obras:turno:read` e `obras:turno:write` são novas; a migração dá leitura a quem já programa ou aponta, e gravação a quem aponta. O apontamento segue em `obras:execucao:write`.

### Decisões da frente E (01/11/2026)

- O supervisor lança a documentação técnica (visita prévia, As Built e equipamentos) pelo web e pelo app, com a mesma API. Isso não muda o turno: abrir e fechar turno, APR e apontamento continuam da equipe. As telas do app entram com a etapa 2.
- `ModeloChecklistObra` guarda grupos e perguntas em JSONB, no desenho dos formulários do SST, com a finalidade VISITA_PREVIA ou AS_BUILT. A ficha As Built usa o mesmo modelo, porque os formulários da concessionária ainda não vieram: quando vierem, viram modelos sem mudar código. Nenhum modelo é semeado em migração. Quem configura tem `obras:documentacao:manage`, só administrador.
- As respostas guardam o texto das perguntas, para o registro antigo continuar legível. Depois do primeiro uso, a estrutura do modelo trava e a alteração é por duplicação.
- Ficha só existe para ponto com execução apontada ou kit marcado como executado antes. Ela pode ficar pela metade; incompleta, impede gerar o documento.
- O documento As Built leva só as folhas com execução, marcadas sobre o projeto mestre, mais as fichas, as fotos e os equipamentos. Entra nos anexos como tipo "AS BUILT" e como nova versão; tentativa que falha não consome número de versão.
- A liberação da APR passou a valer por serviço do dia (`id_programacao`), revisão do usuário em 01/11/2026: a equipe lança a qualquer hora, pode lançar mais de uma, e cada APR libera o seu serviço. Conta o dia da equipe, mesmo que em outro turno. APR concluída depois de um registro de "sem condição" libera o serviço.

### Book da obra (revisão do usuário em 01/11/2026)

- "Book" é o registro fotográfico da obra por ponto, e o As Built sai dele no final. A lista de religadores e reguladores deixou de se chamar "book" e virou "Equipamentos".
- Quem fotografa é o pessoal de campo, pelo app. O supervisor e o administrativo também preenchem a ficha e anexam pelo web. Nesta revisão entraram a API e o web; a tela do app e o perfil da equipe ficam para a etapa 2.
- As fotos pedidas são itens do modelo da ficha As Built (`estrutura.evidencias`: título e obrigatoriedade), e a estrutura trava junto com o modelo. Modelo gravado antes não tem a lista e vale como vazia. Além dos itens, o ponto aceita fotos avulsas, que não dependem de modelo.
- A foto vale para qualquer ponto desde o início, porque a do "antes" vem antes da execução. A ficha continua só para ponto executado. O ponto segue um modelo só: o da ficha ou, antes dela, o das fotos já lançadas.
- Faltar foto só avisa: ao gerar, a tela pede confirmação e lista os pontos sem foto ou com item obrigatório sem foto. O bloqueio continua sendo só a ficha incompleta.
- O documento é um PDF só. Depois das folhas, cada ponto executado traz a ficha e as fotos, duas por linha, com item, legenda, data e autor.
- Só JPG e PNG, conferidos pelo conteúdo do arquivo. O servidor guarda o original e uma miniatura de 480 px; a orientação do EXIF é aplicada na miniatura e no documento. Remover a foto é exclusão lógica, e o arquivo fica no armazenamento.

### Decisões da frente F (medições, 02/11/2026)

- O sistema de referência foi usado só como base (orientação do usuário): telas no padrão Nexo e melhorias sobre o que se viu lá.
- Pré-fechamento em tela própria (`/obras/pre-fechamento`), não escondido no hub de relatórios. Situação calculada: pendente, pronto para concluir, sem levantamento e concluído. Concluir exige pontos e nenhum ponto ou vão sem kit, com a lista dos pontos sem kit na mensagem; reabrir é permitido. "Ponto sem vão" só avisa. Levantadores são os logins que criaram pontos, vãos ou kits. A conclusão fica em `planejamentos_obras` (quem e quando) e não trava o planejamento.
- Só serviço é medido; material não tem valor. Kit marcado como executado antes do sistema fica fora, porque foi executado (e cobrado) antes. Entra só serviço concluído; em andamento espera.
- Quantidade e valor medidos são a soma dos apontamentos ativos do item, com o valor congelado na programação. Serviço sem valor congelado entra com valor zero e a tela avisa antes de gerar.
- Medição tem número sequencial, tipo PARCIAL ou CICLO e competência (mês civil). A parcial usa o mês corrente; a por ciclo escolhe a competência e só aceita o concluído até o último dia dela. O número só é tirado depois de validar.
- A unicidade é o índice parcial `uq_medicao_itens_item_ativo`. Estorno exige justificativa, desativa os itens e devolve o serviço a concluído; a medição fica no histórico.
- O ciclo mostra valor de projeto da obra, medido nas competências anteriores, saldo anterior, medido no ciclo e saldo depois desta medição.
- Melhoria sobre a referência: em vez de digitar o código da obra, a tela abre na lista de obras com serviço a medir (valor e última execução), e a medição de cada obra abre num painel com os serviços marcados, saldo e confirmação do total.
- Fechamento aceita até 200 códigos colados (linha, vírgula, ponto e vírgula, tabulação), lista os não encontrados e exporta XLSX com abas de resumo, serviços, materiais, kits e medições. O planejado usa o valor vigente hoje, só para comparar.
- Filtros (pedido do usuário em 02/11/2026): pré-fechamento por situação, pendência, base, município, supervisor, status da obra, prioridade, levantador e tipo de execução. Os indicadores contam o recorte, e a situação só filtra a tabela. Medições: base, município e supervisor valem para as duas abas; "A medir" tem status e prioridade (na tela), "Geradas" tem tipo, situação, competência, quem gerou e período (no servidor).
- Os eventos `MedicaoGerada/Estornada` de D9 entram quando houver consumidor.
- Pendente: relatório detalhado orçado × faturado (6.4). As fotos "aprovadas" dependem da avaliação de fotos da etapa 2; até lá entram todas as fotos ativas do book.

### D14. Planejamento assistido pelo Copiloto
A leitura é híbrida, porque os projetos variam: em uns o desenho é vetorial com balões numerados, em outros é uma imagem embutida com
os códigos dos postes como texto. Primeiro `LeitorCamadaProjeto` lê do PDF, sem IA, os códigos (ex.: D381272), as caixas de estrutura
(ex.: 12/600; N1M; IM-R) e a escala, que são exatos. O modelo vê a folha inteira com essa lista e informa onde está cada símbolo, a qual
ponto cada caixa pertence e quais pontos se ligam. Sem camada de texto, a folha vai por blocos sobrepostos e tudo vem do modelo.

O processamento é assíncrono. A primeira tentativa usa raciocínio, mais preciso; se a resposta vier vazia ou fora do formato, repete sem
ele. Se o modelo falhar, o que veio do texto do PDF continua valendo. O backend converte as coordenadas para [0,1] da página, une
duplicados, numera na sequência da rede o que não tem número, estima a metragem pela escala quando ela não está escrita (e diz que é
estimada) e procura os kits candidatos no catálogo do contrato, sem deixar o modelo inventar kit. Poste cujo código já está planejado não
vira ponto novo: é assim que a rede continua de uma folha para a outra. O resultado fica em `analises_planejamento` como proposta.
Gravar é um comando separado, com as sugestões que o usuário aceitou, pelas mesmas invariantes do planejamento manual.
- Modelo com visão em `AI_LLM_VISION_MODEL`, no mesmo provedor e chave do chat; vazio deixa o recurso indisponível.
- Alternativa: gravar direto e deixar o usuário corrigir. Rejeitada: erro de leitura viraria valor em R$ e programação.
- O PDF do projeto é enviado ao provedor configurado; quem instala decide o provedor.

## Risks / Trade-offs

- [PDFs grandes e muitos pontos travam o editor] → render só da página atual, overlay em porcentagem, árvore virtualizada,
  DTO compacto e atualização incremental após cada comando.
- [Concorrência entre programadores no mesmo planejamento] → versão por entidade, resposta 409 com estado atual e recarga parcial.
- [Provedor de rotas indisponível] → fallback estimado sinalizado, cache, circuit breaker.
- [Frentes em paralelo divergindo do modelo] → fundação (Frente A) entregue primeiro com migrações e contratos de API revisados;
  demais frentes só criam tabelas próprias.
- [Regressão em turnos comerciais e solicitações] → testes de regressão dos fluxos atuais antes de estender; novos campos
  opcionais.
- [Importações com planilhas inconsistentes] → processamento assíncrono por linha com relatório de erros (padrão de importação
  já existente em obras).
- [Valor congelado diverge do vigente em reajustes] → é intencional; relatórios mostram data de referência do valor.

## Migration Plan

1. Migrações V195+ só aditivas (novas tabelas e colunas opcionais), seguidas de permissões.
2. Carga inicial de kits por importação no ambiente de homologação, validada com a operação.
3. Liberação por permissões: telas novas só aparecem para perfis com as permissões novas.
4. Rollback: desativar permissões; migrações aditivas não precisam ser revertidas.

## Open Questions

- Motor de rotas: OSRM self-hosted (custo de infraestrutura) ou serviço externo pago?
- Conteúdo dos modelos de visita prévia e de ficha As Built: cadastrar a partir dos formulários da concessionária (precisamos deles).
- Calendário de dias úteis: feriados por município/base ou só nacionais?
- Metragem no serviço: confirmar com a operação se o lançamento de cabo do vão é pago por metro (assumido sim, ver frente B).
- Formato exato das planilhas de carteira, kits e serviços/orçamento previstos (amostras para os importadores).

### D15. Cadastros em drawer e solicitação só técnica

O cadastro da obra e as solicitações de material saem das telas em etapas e viram drawer de painel único (revisão não é etapa, conforme o Design System). As URLs antigas redirecionam e abrem o drawer sobre a listagem; a permissão de escrita é conferida pela listagem, porque rota de redirecionamento não aceita guard. A obra nova fica na lista, e a notificação oferece "Abrir obra".

O processo comercial de solicitação de material fica oculto (menu e rotas), sem remoção de código ou dados. A solicitação técnica passa a ter tabela, quadro e calendário; o calendário usa a data prevista, e solicitação sem data não aparece nele.

### D16. Devolução técnica direta

A nova devolução técnica segue a devolução comercial: movimenta o saldo na hora, sem solicitação, com seletor 222/922 por item. A origem é o estoque da obra e o material pode não constar nele: a diferença entra antes na obra como `ENTRADA_AVULSA` e a `DEVOLUCAO` sai com a quantidade inteira, para o saldo não ficar negativo e o histórico fechar. As duas movimentações guardam a obra e o tipo (V200). Pedidos 222/922 já existentes continuam no fluxo antigo.
