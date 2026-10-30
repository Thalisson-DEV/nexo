# Controle de Escolas Homologadas — versão normalizada para agentes de IA

## 1. Objetivo

Este documento normaliza a tabela **CONTROLE ESCOLAS HOMOLOGADAS** para uso por agentes de IA e pela aplicação.

A fonte original possui cabeçalhos agrupados e campos que visualmente dependem da posição da coluna. Aqui, cada escola é tratada como um registro independente, com campos fixos e sem células mescladas.

A normalização preserva o conteúdo informado na fonte. Ela **não valida nem atualiza externamente** a situação das escolas.

---

## 2. Regras de leitura

### 2.1 Chave recomendada

Use preferencialmente:

```text
cnpj
```

como identificador quando ele estiver disponível.

Como chave secundária de busca:

```text
empresa
razao_social
```

Não use `local` como identificador único.

### 2.2 Ausência de informação

Este documento diferencia:

```text
null
```

A célula estava vazia ou a informação não foi fornecida na fonte.

```text
"-"
```

A fonte continha explicitamente um hífen.

Esses dois valores **não são equivalentes**.

### 2.3 Datas e números anômalos

Valores aparentemente incorretos ou deformados pela planilha foram mantidos literalmente.

Exemplos preservados:

```text
IBEC.ead_teoria.data = "23/07/22020"
FB TREINAMENTO.inscricao_municipal = "1,03077E+12"
CETEM.inscricao_municipal = "2,02218E+14"
TOTAL SAFE.inscricao_municipal = "2,02218E+14"
SBL ENGENHARIA.inscricao_municipal = "2,02218E+14"
```

O agente **não deve corrigir esses valores por conta própria**.

### 2.4 Status de homologação

O campo:

```text
status
```

preserva o status explícito da fonte:

- `HOMOLOGADA`
- `DESCREDENCIADA`

Para decisões baseadas exclusivamente neste documento, o agente deve usar esse campo como fonte do estado cadastral.

Não deduzir o status apenas pela existência de uma data de homologação.

### 2.5 Liberação EAD e certificação por competência

São controles independentes:

```text
ead_teoria.data
ead_teoria.liberacao

certificacao_competencia.data
certificacao_competencia.liberada
```

Uma escola homologada **não deve ser considerada automaticamente autorizada para EAD** ou para **certificação por competência**.

---

## 3. Esquema normalizado

```yaml
empresa: string
razao_social: string
parceria_instituicao_ensino: string | null
cnpj: string | null
inscricao_municipal: string | null
data_homologacao_inicio_parceria: string | null
data_descredenciada: string | null
local: string | null

telefones:
  - string

contatos:
  - string

status: "HOMOLOGADA" | "DESCREDENCIADA"

ead_teoria:
  data: string | null
  liberacao: string | null

certificacao_competencia:
  data: string | null
  liberada: string | null
```

---

## 4. Registros normalizados

```yaml
escolas:
  - empresa: "APOIO"
    razao_social: "Escola Técnica Apoio"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "12.060.316/0001-58"
    inscricao_municipal: "656381"
    data_homologacao_inicio_parceria: "27/06/2017"
    data_descredenciada: null
    local: "TEIXEIRA DE FREITAS"
    telefones: ["(73) 99833-1120"]
    contatos: ["comercial@apoiocursos.com.br"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "13/04/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "SIM"
  - empresa: "CAPACITY"
    razao_social: "CAPACITY CONSULTORIA E TREINAMENTOS EM SEG DO TRABALHO"
    parceria_instituicao_ensino: "Complexo Educacional de Cursos Técnicos de Casa Nova"
    cnpj: "33.647.779/0001-90"
    inscricao_municipal: "9652536"
    data_homologacao_inicio_parceria: "09/10/2019"
    data_descredenciada: null
    local: "JUAZEIRO"
    telefones: ["Cel. (74) 9 8849-9592", "Cel. (74) 9 9967-2541"]
    contatos: ["gildenorjunior@capacitypetrolina.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "23/06/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "CETEC"
    razao_social: "Centro de Especialização Técnica"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "11.100.251/0001-64"
    inscricao_municipal: "000.000/015-78"
    data_homologacao_inicio_parceria: "28/08/2019"
    data_descredenciada: null
    local: "JACOBINA"
    telefones: ["(74) 3622-0188"]
    contatos: ["cetec@cetec-ba.com.br", "gustavoellias@gmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "03/08/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "CEZÁRIOS"
    razao_social: "CEZÁRIOS Consultoria e Treinamento"
    parceria_instituicao_ensino: "Escola de Formação Técnica de Saúde Dra Valquiria Saturnino"
    cnpj: "05.793.817/0001-88"
    inscricao_municipal: "144520014"
    data_homologacao_inicio_parceria: "22/09/2017"
    data_descredenciada: null
    local: "JUAZEIRO"
    telefones: ["(74) 98855-2010"]
    contatos: ["cezarios.consultoria@outlook.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "30/06/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "CIQPROL"
    razao_social: "Centro de Instrução Qualificação Profissional Ltda."
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "08.157.390/0001-10"
    inscricao_municipal: "80034661"
    data_homologacao_inicio_parceria: "28/12/2017"
    data_descredenciada: null
    local: "ITABUNA"
    telefones: ["(73) 3612-2197"]
    contatos: ["ciqprolcursosje@gmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "28/08/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "COLÉGIO BATISTA NOVO SIÃO"
    razao_social: "Colégio Batista Novo Sião"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "16.248.882/0001-49"
    inscricao_municipal: null
    data_homologacao_inicio_parceria: "08/05/2018"
    data_descredenciada: "07/03/2019"
    local: "JUAZEIRO"
    telefones: ["-"]
    contatos: ["colegio.batista.novasiao@hotmail.com"]
    status: "DESCREDENCIADA"
    ead_teoria:
      data: "-"
      liberacao: "-"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "CT PROFISSIONAL"
    razao_social: "Cabral Camara Serviços e Treinametos LTDA ME"
    parceria_instituicao_ensino: "Escola Técnica Complexo Educacional Ferreira e Silva Nobre - FESN"
    cnpj: "23.303.107/0001-04"
    inscricao_municipal: "12631300013"
    data_homologacao_inicio_parceria: "04/04/2019"
    data_descredenciada: null
    local: "JUAZEIRO"
    telefones: ["(74) 3612-6200", "(87) 99665-9849"]
    contatos: ["ctprofissional@hotmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "-"
      liberacao: "-"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "CWSE"
    razao_social: "CWSE Brasil Company LTDA"
    parceria_instituicao_ensino: "Escola Kinder Land"
    cnpj: "18.551.688/0001-63"
    inscricao_municipal: "214680"
    data_homologacao_inicio_parceria: "06/11/2019"
    data_descredenciada: null
    local: "Mossoró / RN"
    telefones: ["(84) 3061 - 0072", "0800 020 2869"]
    contatos: ["marcos@cwse.com.br"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "-"
      liberacao: "-"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "EEEMBA"
    razao_social: "SOC CIVIL EDUC E DE ENGENHARIA ELETRO MEC DA BAHIA"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "15.104.201/0001-06"
    inscricao_municipal: "068768/001-75"
    data_homologacao_inicio_parceria: "SEM PRAZO"
    data_descredenciada: null
    local: "SALVADOR"
    telefones: ["(71) 2103-5963", "5959"]
    contatos: ["elisangela@eeemba.br"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "24/03/2021"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "SIM"
  - empresa: "FB TREINAMENTO"
    razao_social: "FS ENGENHARIA EIRELI"
    parceria_instituicao_ensino: "ANA CARLA DOS SANTOS SEIXAS EDUCAÇÃO PROFISSIONAL - FESN EIRELI (A PARTIR DE 15/03/2021)"
    cnpj: "41.368.163/0001-09"
    inscricao_municipal: "1,03077E+12"
    data_homologacao_inicio_parceria: "18/12/2019"
    data_descredenciada: null
    local: "IRECÊ"
    telefones: ["(74) 99999-7970"]
    contatos: ["filipelsiqueira@gmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "06/07/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "IBEC"
    razao_social: "IBEC - Tur Instituto Brasileiro de Educação, Cultura e Turismo LTDA"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "06.168.418/0001-99"
    inscricao_municipal: "113520011"
    data_homologacao_inicio_parceria: "17/11/2017"
    data_descredenciada: null
    local: "ILHÉUS"
    telefones: ["(73) 3633-3205"]
    contatos: ["profreinaldosoares@hotmail.com", "ibecdigital@gmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "23/07/22020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: "19/08/2021"
      liberada: "SIM"
  - empresa: "IMG CURSOS"
    razao_social: "Instituto de Qualificação Profissional Grapiunense"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "05.507.255/0001-69"
    inscricao_municipal: "28464"
    data_homologacao_inicio_parceria: "05/10/2017"
    data_descredenciada: null
    local: "ITABUNA"
    telefones: ["(73) 3041-3170"]
    contatos: ["amaimil@hotmail.com", "atendimento@imgcursos.com.br"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "25/06/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: "31/08/2021"
      liberada: "SIM"
  - empresa: "I S"
    razao_social: "I S ITAMAR SANTANA CURSOS TECNICOS EIRELI"
    parceria_instituicao_ensino: "Cursos Livres"
    cnpj: "33.047.403/0001-45"
    inscricao_municipal: null
    data_homologacao_inicio_parceria: "06/11/2019"
    data_descredenciada: null
    local: "SALVADOR"
    telefones: ["(71) 3285-3008", "98823-0231"]
    contatos: ["escolatecnicaitamarsantana@gmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "11/12/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "ESCOLA TECMASTER"
    razao_social: "ESCOLA TECMASTER QUALIDADE EM ENSINO EIRELI"
    parceria_instituicao_ensino: "Cursos Livres"
    cnpj: "39.357.311/0001-48"
    inscricao_municipal: "76475200192"
    data_homologacao_inicio_parceria: "16/10/2020"
    data_descredenciada: null
    local: "SALVADOR"
    telefones: ["(71) 3286-5137"]
    contatos: ["escolatecmaster@gmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "16/10/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "PREMIUM"
    razao_social: "PREMIUM Desenvolvimento Profissional e Segurança do Trabalho"
    parceria_instituicao_ensino: "Escola de Formação Técnica de Saúde Dra Valquiria Saturnino"
    cnpj: "23.249.606/0001-60"
    inscricao_municipal: "1261720001"
    data_homologacao_inicio_parceria: "07/08/2018"
    data_descredenciada: null
    local: "SENHOR DO BONFIM"
    telefones: ["(74) 98805-1808"]
    contatos: ["premium.treinamentos@gmail.com", "claudiocezario@hotmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "29/06/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "PREV CONSULT"
    razao_social: "Prevenção Consultoria em Saude e Segurança do Trabalho LTDA - ME"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "16.660.757/0001-41"
    inscricao_municipal: "514144"
    data_homologacao_inicio_parceria: "22/02/2018"
    data_descredenciada: null
    local: "VITÓRIA DA CONQUISTA"
    telefones: ["(77) 98839-6602"]
    contatos: ["prevconsult.01@gmail.com"]
    status: "DESCREDENCIADA"
    ead_teoria:
      data: "-"
      liberacao: "-"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "RGS Cursos"
    razao_social: "RGS EMPREENDIMENTOS EDUCACIONAIS LTDA"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "10.144.546/0001-70"
    inscricao_municipal: "256900123"
    data_homologacao_inicio_parceria: "24/08/2020"
    data_descredenciada: null
    local: "SENHOR DO BONFIM"
    telefones: ["(74) 35415865"]
    contatos: ["Regivan@rgscursos.com.br"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "-"
      liberacao: "-"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "SENAI"
    razao_social: "Serviço Nacional de Aprendizagem Industrial"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: null
    inscricao_municipal: null
    data_homologacao_inicio_parceria: "SEM PRAZO"
    data_descredenciada: null
    local: "NACIONAL"
    telefones: ["(71) 3534-8090"]
    contatos: []
    status: "HOMOLOGADA"
    ead_teoria:
      data: "23/03/2020"
      liberacao: "SIM - VIDEOCONFERÊNCIA E PLATAFORMA"
    certificacao_competencia:
      data: null
      liberada: "SIM"
  - empresa: "TELEGEL SOLAR"
    razao_social: "TELEGEL ENERGIAS RENOVAVEIS, TELECOMUNICACAO E ELETRONICA"
    parceria_instituicao_ensino: "Cursos Livres"
    cnpj: "04.584.350/0001-01"
    inscricao_municipal: "9651390"
    data_homologacao_inicio_parceria: "22/06/2020"
    data_descredenciada: null
    local: "EUNÁPOLIS"
    telefones: ["(73) 988117777", "(73) 982096000", "(73) 3261 7636"]
    contatos: ["telegeltelecomunicacoes@yahoo.com.br"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "-"
      liberacao: "-"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "ÔMEGA CURSOS"
    razao_social: "Rocha Agrizzi Cursos de Informática LTDA"
    parceria_instituicao_ensino: "Cursos Livres"
    cnpj: "09.312.069/0001-25"
    inscricao_municipal: "654621"
    data_homologacao_inicio_parceria: "01/06/2021"
    data_descredenciada: null
    local: "EUNÁPOLIS"
    telefones: ["73 3291 4629", "(73) 99855-2502"]
    contatos: ["omegacursos10@hotmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "-"
      liberacao: "-"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "MSO"
    razao_social: "MATEUS SANTOS OLIVEIRA"
    parceria_instituicao_ensino: "Curso livre"
    cnpj: "15.863.997/0001-80"
    inscricao_municipal: "42653400103"
    data_homologacao_inicio_parceria: "15/03/2021"
    data_descredenciada: null
    local: "SALVADOR"
    telefones: ["(71)8760-4756(TIM)", "(71)9737-3946 (VIVO)", "(71)8371-1256 (CLARO)"]
    contatos: ["atendimento@msotreinamentos.com.br"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: "-"
      liberacao: "-"
    certificacao_competencia:
      data: null
      liberada: "-"
  - empresa: "CETEM"
    razao_social: "CENTRO DE TREINAMENTO ELETRICO MORRINHOS LTDA"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "40.171.288/0001-81"
    inscricao_municipal: "2,02218E+14"
    data_homologacao_inicio_parceria: "28/08/2024"
    data_descredenciada: null
    local: "ITABERABA"
    telefones: ["(64) 9330-0146"]
    contatos: ["felipejose150996@hotmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: null
      liberacao: null
    certificacao_competencia:
      data: null
      liberada: null
  - empresa: "TOTAL SAFE"
    razao_social: "TOTAL SAFE LTDA"
    parceria_instituicao_ensino: "Curso livre"
    cnpj: "57.081.487/0001-00"
    inscricao_municipal: "2,02218E+14"
    data_homologacao_inicio_parceria: "31/03/2025"
    data_descredenciada: null
    local: "SANTO ANTONIO DE JESUS"
    telefones: ["(75) 999115904"]
    contatos: ["totalsafecomercial@hotmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: null
      liberacao: null
    certificacao_competencia:
      data: null
      liberada: null
  - empresa: "SBL ENGENHARIA"
    razao_social: "SBL ENGENHARIA LTDA"
    parceria_instituicao_ensino: "Credenciada junto ao MEC"
    cnpj: "36.126.561/0001-89"
    inscricao_municipal: "2,02218E+14"
    data_homologacao_inicio_parceria: "31/03/2025"
    data_descredenciada: null
    local: "TEIXEIRA DE FREITAS"
    telefones: ["(73) 999459969"]
    contatos: ["luansbl1991@gmail.com"]
    status: "HOMOLOGADA"
    ead_teoria:
      data: null
      liberacao: null
    certificacao_competencia:
      data: null
      liberada: null
```

---

## 5. Índice compacto para consulta humana

| Empresa | Local | Status | EAD teórico | Certificação por competência |
|---|---|---|---|---|
| APOIO | TEIXEIRA DE FREITAS | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | SIM |
| CAPACITY | JUAZEIRO | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | - |
| CETEC | JACOBINA | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | - |
| CEZÁRIOS | JUAZEIRO | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | - |
| CIQPROL | ITABUNA | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | - |
| COLÉGIO BATISTA NOVO SIÃO | JUAZEIRO | DESCREDENCIADA | - | - |
| CT PROFISSIONAL | JUAZEIRO | HOMOLOGADA | - | - |
| CWSE | Mossoró / RN | HOMOLOGADA | - | - |
| EEEMBA | SALVADOR | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | SIM |
| FB TREINAMENTO | IRECÊ | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | - |
| IBEC | ILHÉUS | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | SIM |
| IMG CURSOS | ITABUNA | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | SIM |
| I S | SALVADOR | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | - |
| ESCOLA TECMASTER | SALVADOR | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | - |
| PREMIUM | SENHOR DO BONFIM | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA | - |
| PREV CONSULT | VITÓRIA DA CONQUISTA | DESCREDENCIADA | - | - |
| RGS Cursos | SENHOR DO BONFIM | HOMOLOGADA | - | - |
| SENAI | NACIONAL | HOMOLOGADA | SIM - VIDEOCONFERÊNCIA E PLATAFORMA | SIM |
| TELEGEL SOLAR | EUNÁPOLIS | HOMOLOGADA | - | - |
| ÔMEGA CURSOS | EUNÁPOLIS | HOMOLOGADA | - | - |
| MSO | SALVADOR | HOMOLOGADA | - | - |
| CETEM | ITABERABA | HOMOLOGADA | `null` | `null` |
| TOTAL SAFE | SANTO ANTONIO DE JESUS | HOMOLOGADA | `null` | `null` |
| SBL ENGENHARIA | TEIXEIRA DE FREITAS | HOMOLOGADA | `null` | `null` |

---

## 6. Regras recomendadas para o agente

### Consultar se uma escola está homologada

```text
1. localizar por CNPJ;
2. se não houver CNPJ, localizar por empresa/razão social;
3. verificar status;
4. retornar homologada somente quando status == "HOMOLOGADA".
```

### Consultar se pode ministrar teoria por EAD

```text
1. confirmar que status == "HOMOLOGADA";
2. ler ead_teoria.liberacao;
3. somente afirmar liberação quando houver valor positivo explícito, como:
   "SIM - VIDEOCONFERÊNCIA"
   "SIM - VIDEOCONFERÊNCIA E PLATAFORMA"
4. null ou "-" não devem ser convertidos em "SIM".
```

### Consultar certificação por competência

```text
1. confirmar que status == "HOMOLOGADA";
2. verificar certificacao_competencia.liberada;
3. considerar autorizada somente quando o valor for explicitamente "SIM";
4. null ou "-" significam apenas que a fonte não apresenta liberação positiva.
```

### Escola descredenciada

Se:

```text
status == "DESCREDENCIADA"
```

a escola não deve ser retornada como opção homologada, mesmo que exista data de homologação histórica.

### Dados suspeitos

Se um campo possuir valor aparentemente inválido, por exemplo:

```text
"23/07/22020"
"1,03077E+12"
"2,02218E+14"
```

o agente deve:

```text
- preservar o valor da fonte;
- sinalizar que o dado aparenta inconsistência;
- não corrigir automaticamente;
- solicitar validação humana se o campo for necessário para uma decisão.
```

---

## 7. Relação com a matriz de treinamentos

Esta base responde **quem está homologado e quais liberações adicionais a escola possui**.

Ela não substitui a matriz de treinamento por função.

Fluxo recomendado na aplicação:

```text
1. identificar processo + função do colaborador;
2. consultar a matriz de treinamentos para determinar os cursos obrigatórios;
3. verificar as regras de escola homologada aplicáveis ao processo;
4. consultar esta base para selecionar uma escola com status HOMOLOGADA;
5. quando o treinamento for EAD, verificar também ead_teoria.liberacao;
6. quando houver certificação por competência, verificar também certificacao_competencia.liberada;
7. não presumir autorização adicional apenas porque a escola está homologada.
```

---

## 8. Resumo de integridade

```text
Total de escolas normalizadas: 24
HOMOLOGADA: 22
DESCREDENCIADA: 2
```

Foram preservados:

- CNPJ;
- inscrição municipal;
- data de homologação/início da parceria;
- data de descredenciamento;
- local;
- telefones;
- contatos;
- status;
- data e forma de liberação da teoria EAD;
- data e liberação de certificação por competência;
- texto da parceria ou condição de credenciamento junto à instituição de ensino.

Nenhum valor aparentemente incorreto foi corrigido automaticamente.
