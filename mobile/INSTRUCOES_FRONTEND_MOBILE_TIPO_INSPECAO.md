# Instruções Técnicas para o Nexo Mobile — Tipos de Inspeção (SST)

Este documento orienta o time de engenharia e agentes de IA sobre as alterações necessárias no aplicativo móvel **Nexo Mobile** (Android/iOS/PWA) para suportar a funcionalidade de **Tipos de Inspeção** no módulo de Segurança e Saúde no Trabalho (SST).

---

## 1. Visão Geral e Contexto

No módulo de SST, as inspeções agora são classificadas de acordo com o **Tipo de Inspeção** (ex.: *Alojamento*, *Campo*, *Câmera*, *Veicular*).

### Principais mudanças:
1. O backend disponibiliza uma listagem dinâmica dos tipos de inspeção ativos.
2. O formulário de abertura e registro de inspeções deve exibir o campo de seleção de **Tipo de Inspeção** obrigatoriamente logo abaixo do seletor de **Modelo de Formulário**.
3. O payload de registro da inspeção (`POST /api/v1/sst/inspecoes`) passa a aceitar o identificador `idTipoInspecao`.
4. As inspeções retornadas pelo backend trazem o objeto `tipoInspecao` preenchido.

---

## 2. Endpoints e Contratos de Integração

### 2.1. Obtenção dos Tipos de Inspeção (Sincronização Offline)

Para popular o seletor na interface e sincronizar com o armazenamento local offline do dispositivo:

- **Método**: `GET`
- **URL**: `/api/v1/sst/tipos-inspecao/opcoes`
- **Autenticação**: Bearer Token (JWT)
- **Resposta Sucesso (`200 OK`)**:

```json
[
  {
    "id": "11111111-1111-1111-1111-111111111101",
    "nome": "Alojamento",
    "descricao": "Inspeções realizadas em alojamentos e instalações de suporte.",
    "ordem": 1,
    "ativo": true,
    "dataCriacao": "2026-09-11T12:00:00Z",
    "dataUltimaAlteracao": "2026-09-11T12:00:00Z"
  },
  {
    "id": "11111111-1111-1111-1111-111111111102",
    "nome": "Campo",
    "descricao": "Inspeções de segurança realizadas em campo e frentes de serviço.",
    "ordem": 2,
    "ativo": true,
    "dataCriacao": "2026-09-11T12:00:00Z",
    "dataUltimaAlteracao": "2026-09-11T12:00:00Z"
  },
  {
    "id": "11111111-1111-1111-1111-111111111103",
    "nome": "Câmera",
    "descricao": "Auditorias e inspeções remotas realizadas via câmeras de monitoramento.",
    "ordem": 3,
    "ativo": true,
    "dataCriacao": "2026-09-11T12:00:00Z",
    "dataUltimaAlteracao": "2026-09-11T12:00:00Z"
  },
  {
    "id": "11111111-1111-1111-1111-111111111104",
    "nome": "Veicular",
    "descricao": "Vistorias de conformidade em frotas e veículos operacionais.",
    "ordem": 4,
    "ativo": true,
    "dataCriacao": "2026-09-11T12:00:00Z",
    "dataUltimaAlteracao": "2026-09-11T12:00:00Z"
  }
]
```

> **Estratégia de Cache**: Armazenar os tipos em tabela local (ex: SQLite / Realm / WatermelonDB / AsyncStorage). Atualizar o cache durante o ciclo de sync ou abertura do módulo de SST.

---

### 2.2. Registro de Inspeção (Online / Enfileiramento Offline)

- **Método**: `POST`
- **URL**: `/api/v1/sst/inspecoes`
- **Corpo da Requisição (`application/json`)**:

```json
{
  "idFormulario": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "idTipoInspecao": "11111111-1111-1111-1111-111111111102",
  "idProcesso": "c3e1e2d3-1a2b-4c5d-8e9f-0a1b2c3d4e5f",
  "idVeiculo": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
  "idEquipe": "d1e2f3a4-b5c6-7d8e-9f0a-1b2c3d4e5f6a",
  "idBaseOperacional": "b1c2d3e4-f5a6-7b8c-9d0e-1f2a3b4c5d6e",
  "dataRealizacao": "2026-09-11T14:30:00-03:00",
  "observacoes": "Inspeção realizada em conformidade com o procedimento.",
  "latitude": -23.550520,
  "longitude": -46.633308,
  "colaboradoresInspecionados": [
    "f1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c"
  ],
  "respostas": [
    {
      "idPergunta": "e1f2a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b",
      "opcoesSelecionadas": ["a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6e"],
      "valorTexto": null,
      "valorNumero": null,
      "valorData": null
    }
  ],
  "desvios": [],
  "evidencias": [
    "8e1f2a3b-4c5d-6e7f-8a9b-0c1d2e3f4a5b"
  ]
}
```

> **Compatibilidade**: Caso o payload seja enviado por uma versão anterior do app sem `idTipoInspecao`, o backend assumirá o tipo `"Campo"` como padrão. Contudo, a nova versão deve sempre validar e enviar o `idTipoInspecao` selecionado pelo inspetor.

---

### 2.3. Resposta de Inspeção

Ao consultar ou registrar uma inspeção, a resposta contém o detalhe do tipo:

```json
{
  "id": "7a8b9c0d-1e2f-3a4b-5c6d-7e8f9a0b1c2d",
  "formulario": {
    "id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
    "titulo": "Checklist de Segurança em Obras",
    "descricao": "Avaliação de segurança do canteiro",
    "ativo": true,
    "totalPerguntas": 15
  },
  "tipoInspecao": {
    "id": "11111111-1111-1111-1111-111111111102",
    "nome": "Campo",
    "descricao": "Inspeções de segurança realizadas em campo e frentes de serviço.",
    "ordem": 2,
    "ativo": true,
    "dataCriacao": "2026-09-11T12:00:00Z",
    "dataUltimaAlteracao": "2026-09-11T12:00:00Z"
  },
  "processo": { ... },
  "nomeInspetor": "João Silva",
  "status": "CONCLUIDA",
  ...
}
```

---

## 3. Diretrizes de Interface (UI/UX Mobile)

1. **Tela de Nova Inspeção (Header / Etapa 1 - Identificação)**:
   - Campo 1: **Modelo de Formulário** (Dropdown/Select).
   - Campo 2: **Tipo de Inspeção** (Dropdown/Select ou Chips de seleção única com rótulos amigáveis: *Alojamento*, *Campo*, *Câmera*, *Veicular*).
     - **Posicionamento**: Imediatamente abaixo do seletor de formulário.
     - **Validação**: Obrigatório. Não permitir avançar ou salvar o rascunho da inspeção sem selecionar um tipo válido.
     - **Valor padrão sugerido**: *Campo* (ou o primeiro tipo ativo ordenado por `ordem`).
2. **Histórico / Listagem de Inspeções**:
   - Exibir badge com o nome do tipo de inspeção no card de resumo de cada inspeção.
   - Adicionar filtro por tipo de inspeção na barra de filtros da listagem mobile.

---

## 4. Checklist de Implementação no Mobile

- [ ] Criar entidade/tabela local `TipoInspecao` no banco de dados offline.
- [ ] Incluir sincronização de `GET /api/v1/sst/tipos-inspecao/opcoes` no serviço de sync.
- [ ] Adicionar campo `idTipoInspecao` no estado/store de criação de nova inspeção.
- [ ] Inserir o componente visual de seleção de Tipo de Inspeção abaixo do modelo de formulário.
- [ ] Atualizar serializer/payload de envio `POST /api/v1/sst/inspecoes` com `idTipoInspecao`.
- [ ] Adicionar badge e filtro de Tipo de Inspeção na tela de histórico de inspeções realizadas.
