# Nexo - Gestão Operacional 🛡️

> **Enterprise Resource Planning (ERP) de Alta Performance para Operações de Campo.**

O **Nexo** é um sistema de missão crítica projetado para orquestrar operações logísticas, comerciais e de recursos humanos com rigor matemático e segurança de dados de nível bancário. Utiliza uma arquitetura de **Monólito Modular (Modulith)**, projetada para alta performance, escalabilidade, segurança e extrema facilidade de manutenção.

---

## 🛠️ 1. Stack Tecnológica & Arquitetura

O sistema emprega tecnologias modernas e padrões robustos para garantir integridade e performance em larga escala:

- **Linguagem & Framework:** Java 21+ (LTS) e Spring Boot 4.0.3+.
- **Escalabilidade:** Uso intensivo de **Virtual Threads (Project Loom)** para suportar milhares de requisições de I/O simultâneas sem o overhead imposto por threads nativas (SO).
- **Modularidade Estrutural:** O **Spring Modulith** assegura isolamento estrito entre domínios lógicos. A comunicação inter-módulos ocorre *apenas* de forma assíncrona via Domain Events (`event_publication`), garantindo consistência eventual e baixo acoplamento.
- **Persistência de Dados:** PostgreSQL 17.5 aproveitando a flexibilidade de colunas **JSONB** para auditoria detalhada. Versionamento de banco de dados e migrações são orquestrados via **Flyway**.
- **Resiliência e Cache:** Implementação de **Resilience4j** (Circuit Breakers) para proteger operações de I/O, integrações e uso do banco de dados, somado ao uso de **Redis** para caching de alta performance.

---

## 🏛️ 2. Padrões de Design e Implementação

A aplicação é arquitetada sob os princípios do **Domain-Driven Design (DDD)**, evitando completamente e categoricamente o uso de modelos anêmicos.

### 🧠 Rich Domain Model
- **Invariantes de Domínio:** Entidades garantem seu próprio estado. Por exemplo, operações de ativação, desativação e criação de vínculos são restritas a rigorosas regras de negócio internas.
- **BaseEntity:** Fornece uma fundação robusta a todas as entidades do sistema, com UUID (v4) como PK, versionamento para Optimistic Locking, auditoria automática (`CreatedBy`, `LastModifiedDate`, etc.), suporte a **Soft Delete** integrado e snapshots de estado.
- **Encapsulamento Estrito:** Proibição de métodos genéricos como *Setters*. Mudanças de estado devem ocorrer por métodos declarativos que representam a real intenção do negócio (ex: `vincularColaborador()`, `desativar()`).

### 🛡️ Segurança & Defesa em Profundidade
- **Criptografia Híbrida (AttributeConverter):** Dados sensíveis (como CPF, senhas, notas) são automaticamente cifrados no banco em **AES-256 GCM**. A conversão ocorre diretamente e de forma transparente na camada JPA.
- **Blind Indexing:** Para possibilitar buscas de alta performance em colunas criptografadas (que não suportam operadores `LIKE` ou `=`), é utilizado o conceito de *Blind Index*, criando um hash MD5 determinístico associado a uma *Pepper* segura.
- **Autenticação Stateless:** Utilização de **JWT** (JSON Web Tokens) assinados com chaves assimétricas robustas para identificação nas requisições.
- **Padronização de Erros:** Todas as respostas de exceção utilizam o padrão global **RFC 7807 (Problem Details)**, com captura centralizada via `GlobalExceptionHandler`.

### 📊 Auditoria Imutável (Time-Travel via JSONB)
O Nexo possui um motor de auditoria de dados reativo e profundo:
- O sistema intercepta automaticamente eventos do ciclo de vida da persistência (`@PostLoad` e `@PostUpdate`) através do `AuditListener`.
- A diferença (delta) das informações gera um *snapshot* na tabela de auditoria `logs_sistema`.
- Este histórico armazena tanto os dados "Anteriores" quanto os "Novos" estritamente no formato **JSONB**, garantindo a rastreabilidade completa de qualquer alteração na plataforma.

---

## 📡 3. Documentação da API

A API foi desenhada no padrão RESTful, consumindo e retornando `application/json`. Utiliza UUIDs na URL dos recursos, suporta paginação customizada e otimizada (`PageResponse<T>`) e permite filtros dinâmicos com *Spring Data JPA Specifications*. Todas as rotas de negócio são protegidas por autenticação `Bearer Token`.

### Observabilidade e status público

O endpoint legado `/api/v1/status` e todos os seus subcaminhos foram removidos. Esta é uma alteração incompatível: clientes não devem fazer fallback para esse contrato nem esperar uma resposta `410`. O status público é fornecido pelo monitoramento Gatus independente, conforme a URL publicada pela implantação.

Abaixo a visão geral agrupada e detalhada de todos os endpoints suportados:

### 🔑 IAM & Autenticação (Gestão de Identidade e Acesso)
- **POST** `/v1/auth/login` - Autenticação no sistema e emissão do JWT.
- **GET / POST** `/v1/iam/usuarios` - Listagem com filtros dinâmicos / Registrar novo usuário.
- **GET / PUT / DELETE** `/v1/iam/usuarios/{id}` - Buscar, atualizar dados ou realizar Soft Delete de usuário.
- **PUT** `/v1/iam/usuarios/{id}/ativar` - Reativar um usuário inativo.
- **GET / POST** `/v1/iam/perfis` - Listar todos os perfis de acesso / Criar novo perfil.
- **GET / PUT / DELETE** `/v1/iam/perfis/{id}` - Buscar, atualizar ou realizar Soft Delete em perfis de acesso.
- **PUT** `/v1/iam/perfis/{id}/ativar` - Reativar um perfil.
- **POST** `/v1/iam/perfis/importar` - Validar arquivo CSV para importação em lote de perfis.
- **POST** `/v1/iam/perfis/importar/confirmar` - Confirmar importação em lote validada.

### 👥 Recursos Humanos, Processos e Frotas
Gestão completa e estruturada da força de trabalho operacional.

**Colaboradores**
- **GET / POST** `/v1/rh/colaboradores` - Listagem paginada e dinâmica / Cadastro individual de colaborador.
- **GET** `/v1/rh/colaboradores/coordenadores-supervisores` - Retorna listagem otimizada de supervisores.
- **GET / PUT / DELETE** `/v1/rh/colaboradores/{id}` - Consulta, Edição e Exclusão lógica.
- **PUT** `/v1/rh/colaboradores/{id}/ativar` - Reativação lógica do colaborador.
- **POST** `/v1/rh/colaboradores/gerar-acesso` - Gera acesso vinculando colaborador ao ecossistema IAM.
- **POST** `/v1/rh/colaboradores/importar` & `confirmar-importacao` - Fluxo de validação e importação (CSV) em lote.

**Equipes**
- **GET / POST** `/v1/equipes` - Listagem flexível e Criação de equipes (requer validação rigorosa de capacidade e recursos).
- **GET / PUT / DELETE** `/v1/equipes/{id}` - Buscas, Modificações ou Desativação de uma equipe.
- **PUT** `/v1/equipes/{id}/ativar` - Reativar equipe previamente desativada.
- **GET / POST** `/v1/equipes/{id}/membros` - Listagem e Alocação unitária de membros.
- **POST** `/v1/equipes/{id}/membros/batch` - Alocação estruturada de múltiplos membros de uma vez.
- **DELETE** `/v1/equipes/{id}/membros/{colaboradorId}` - Remoção/Desalocação do membro selecionado da equipe.
- **PATCH** `/v1/equipes/{id}/responsabilidade/{colaboradorId}` - Operação crítica de transferência de gestão (responsabilidade) da equipe.
- **POST** `/v1/equipes/importar` & `confirmar-importacao` - Processamento em lote de importação.

**Veículos**
- **GET / POST** `/v1/veiculos` - Consulta dinâmica / Cadastro de Veículos.
- **GET / PUT / DELETE / ATIVAR** `/v1/veiculos/{id}` - Ciclo de vida completo por identificador.
- **POST** `/v1/veiculos/importar` & `confirmar-importacao` - Rotinas de importação de arquivo CSV.

**Funções, Tipos de Equipe e Processos de Negócio**
Cada subdomínio acima possui seu ciclo completo na API, contendo:
- `GET / POST` baseados em `/v1/rh/funcoes`, `/v1/rh/tipos-equipe` e `/v1/processos-negocio`
- `GET / PUT / DELETE` por ID, incluindo fluxos de `ativar` para reverter soft deletes.
- Fluxo dividido de importação em duas etapas (Validação CSV `importar` -> Execução Real `confirmar-importacao`).

### 🏢 Estrutura Organizacional e Geográfica
- **Centros de Faturamento (`/v1/centros-faturamento`)**: Agrupamento contábil. Suporta CRUD, Filtros Dinâmicos, Ativação/Desativação.
- **Bases Operacionais (`/v1/bases-operacionais`)**: Unidades físicas locais. CRUD completo, ciclo de ativação e rotas de importação CSV.
- **Municípios (`/v1/municipios`)**: Cobertura geográfica de atendimento. CRUD com listagens filtradas e rotina de importação lote.

### 💰 Gestão Comercial e Faturamento (Core Engine)
O Motor de Faturamento funciona baseado na lógica: *Valor Total = AcaoValor * Coeficiente de Faturamento.*

- **Contratos (`/v1/comercial/contratos`)** - Agrupador máximo com controle de vigências e clientes. Permite Listagem, Criação, Atualização, Exclusão Lógica e Importação CSV.
- **Ações Comerciais (`/v1/comercial/acoes`)** - O catálogo padronizado de serviços base (ex: Manutenção Tipo A). Fluxo completo de CRUD, listagem dinâmica, ativação e importação.
- **Ações Valores (`/v1/acoes-valores`)** - Atribui métrica de preço a uma Ação conforme a data e o contrato vigente. Suporta CRUD isolado, Listagem customizada e Importação inteligente.
- **Contratos Coeficientes (`/v1/contratos-coeficientes`)** - Multiplicadores operacionais dinâmicos baseados no município ou base regional para ajuste de cobrança. Contempla CRUD, filtros, ativação e fluxo de importação lote.

### 🔍 Auditoria
- **GET** `/v1/auditoria/{registroId}` - Entrega detalhada de todo o log e ciclo de vida de alterações com snapshot `dados_anteriores` e `dados_novos` em JSON para o ID especificado.

---

## 🚀 4. Executando o Projeto

O projeto utiliza o Maven Wrapper, abstraindo a necessidade de instalar uma versão específica do Maven globalmente, e o Docker Compose para subir instantaneamente recursos periféricos.

### Pré-requisitos
- **Java 21+** instalado e configurado nas variáveis de ambiente.
- **Docker** e **Docker Compose** ativos.

### Passos de Inicialização
1. **Subir Banco de Dados e Cache Redis:**
   ```bash
   docker-compose up -d
   ```
2. **Construir e Executar a Aplicação Base:**
   ```bash
   ./mvnw spring-boot:run
   ```
3. A aplicação estará ativa em: `http://localhost:8080/api`.
4. Explore os contratos e endpoints usando o Swagger UI acessando:
   `http://localhost:8080/api/swagger-ui.html`

---

## 📝 5. Roadmap e Próximas Fases (To-Do)

Seguindo a ordem lógica de dependência de dados para expansão segura do Modulith:

- [ ] **[Follow-up] Importações CSV com persistência parcial**
  - Generalizar para os demais domínios o comportamento adotado em colaboradores: registros com erro de validação permanecem no retorno e somente os registros válidos seguem para persistência.
- [ ] **[Fase 1] Finalização Comercial (Core Logic)**
  - Implementação da lógica estrita de validador de vigência temporal, impossibilitando preços conflitantes no mesmo período, e rotinas do motor de cálculo da produção.
- [ ] **[Fase 2] Logística e Estoque Unificado**
  - Módulo de transferência sistêmica de custódia (Almoxarifado -> Campo) com validações severas de assinatura digital.
  - Gestão de saldo e distinção rigorosa de material classificado como SUCATA vs INVESTIMENTO.
- [ ] **[Fase 3] Execução e Campo (Motor de Regras)**
  - Registro avançado de notas de serviço e *Consumo Automático* de materiais baseados em domain events.
  - Lógica inviolável de estorno que gera movimentações ledger (inversas) para impedir furos financeiros e de inventário.
- [ ] **[Fase 4] Frontend, Angular & Dashboards**
  - Integração da SPA consumindo as APIs com forte uso de State Management.
  - Painéis de acompanhamento em tempo real (Observabilidade combinada ao Micrometer / Prometheus).

---

> **⚠️ Alerta para Desenvolvedores:** A integridade das fronteiras modulares é um requisito absoluto. Ao desenvolver novos fluxos, não injete repositórios ou chame *UseCases* de domínios externos diretamente. Use UUIDs e Eventos. Valide sempre as restrições executando de modo isolado: `./mvnw clean test`.
