package com.nexo.app.modules.rh.infrastructure.copiloto;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ProvedorCadastros;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.rh.application.dto.BaseOperacionalRequest;
import com.nexo.app.modules.rh.application.dto.CentroFaturamentoRequest;
import com.nexo.app.modules.rh.application.dto.ColaboradorRequest;
import com.nexo.app.modules.rh.application.dto.ColaboradorUpdateRequest;
import com.nexo.app.modules.rh.application.dto.CoordenadorRequest;
import com.nexo.app.modules.rh.application.dto.EquipeMembroRequest;
import com.nexo.app.modules.rh.application.dto.EquipeRequest;
import com.nexo.app.modules.rh.application.dto.FuncaoRequest;
import com.nexo.app.modules.rh.application.dto.MetaFaturamentoRequest;
import com.nexo.app.modules.rh.application.dto.MunicipioRequest;
import com.nexo.app.modules.rh.application.dto.ProcessoNegocioRequest;
import com.nexo.app.modules.rh.application.dto.SupervisorRequest;
import com.nexo.app.modules.rh.application.dto.TipoEquipeRequest;
import com.nexo.app.modules.rh.application.dto.VeiculoRequest;
import com.nexo.app.modules.rh.application.usecases.BaseOperacionalUseCases;
import com.nexo.app.modules.rh.application.usecases.CentroFaturamentoUseCases;
import com.nexo.app.modules.rh.application.usecases.ColaboradorUseCases;
import com.nexo.app.modules.rh.application.usecases.CoordenadorUseCases;
import com.nexo.app.modules.rh.application.usecases.EquipeUseCases;
import com.nexo.app.modules.rh.application.usecases.FuncaoUseCases;
import com.nexo.app.modules.rh.application.usecases.MembroEquipeUseCase;
import com.nexo.app.modules.rh.application.usecases.MetaFaturamentoUseCases;
import com.nexo.app.modules.rh.application.usecases.MunicipioUseCases;
import com.nexo.app.modules.rh.application.usecases.ProcessoNegocioUseCases;
import com.nexo.app.modules.rh.application.usecases.SupervisorUseCases;
import com.nexo.app.modules.rh.application.usecases.TipoEquipeUseCases;
import com.nexo.app.modules.rh.application.usecases.VeiculoUseCases;
import com.nexo.app.modules.rh.domain.Colaborador;
import com.nexo.app.modules.rh.domain.EquipeMembro;
import com.nexo.app.modules.rh.infrastructure.persistence.ColaboradorRepository;
import jakarta.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** O que o Copiloto cadastra em Pessoas e estrutura: colaboradores, equipes, funções, bases, veículos... */
@Component
public class CadastrosRhCopiloto implements ProvedorCadastros {

    private static final String MODULO = "Pessoas e estrutura";

    /** Inclusão de um colaborador numa equipe existente (o caso de uso recebe a equipe à parte). */
    public record MembroEquipeCadastro(
            @NotNull UUID idEquipe,
            @NotNull UUID idColaborador,
            @NotNull EquipeMembro.FuncaoMembro funcao) {
    }

    private final ColaboradorUseCases colaboradores;
    private final EquipeUseCases equipes;
    private final MembroEquipeUseCase membros;
    private final FuncaoUseCases funcoes;
    private final BaseOperacionalUseCases bases;
    private final ProcessoNegocioUseCases processos;
    private final TipoEquipeUseCases tiposEquipe;
    private final VeiculoUseCases veiculos;
    private final CoordenadorUseCases coordenadores;
    private final SupervisorUseCases supervisores;
    private final MunicipioUseCases municipios;
    private final CentroFaturamentoUseCases centros;
    private final MetaFaturamentoUseCases metas;
    private final ColaboradorRepository colaboradorRepository;
    private final JdbcTemplate jdbc;

    public CadastrosRhCopiloto(ColaboradorUseCases colaboradores, EquipeUseCases equipes, MembroEquipeUseCase membros,
            FuncaoUseCases funcoes, BaseOperacionalUseCases bases, ProcessoNegocioUseCases processos,
            TipoEquipeUseCases tiposEquipe, VeiculoUseCases veiculos, CoordenadorUseCases coordenadores,
            SupervisorUseCases supervisores, MunicipioUseCases municipios, CentroFaturamentoUseCases centros,
            MetaFaturamentoUseCases metas, ColaboradorRepository colaboradorRepository, JdbcTemplate jdbc) {
        this.colaboradores = colaboradores;
        this.equipes = equipes;
        this.membros = membros;
        this.funcoes = funcoes;
        this.bases = bases;
        this.processos = processos;
        this.tiposEquipe = tiposEquipe;
        this.veiculos = veiculos;
        this.coordenadores = coordenadores;
        this.supervisores = supervisores;
        this.municipios = municipios;
        this.centros = centros;
        this.metas = metas;
        this.colaboradorRepository = colaboradorRepository;
        this.jdbc = jdbc;
    }

    @Override
    public List<ReferenciaCadastro> referencias() {
        return List.of(
                new ReferenciaCadastro("colaborador", "Colaborador",
                        "select c.id, c.nome_busca as rotulo, concat_ws(' · ', f.nome, b.nome) as detalhe from colaboradores c "
                                + "left join funcoes f on f.id = c.id_funcao left join bases_operacionais b on b.id = c.id_base_operacional "
                                + "where c.ativo",
                        this::nomesDeColaboradores),
                new ReferenciaCadastro("coordenador", "Coordenador",
                        "select co.id, c.nome_busca as rotulo, 'Coordenador' as detalhe from coordenadores co "
                                + "join colaboradores c on c.id = co.id_colaborador where co.ativo",
                        ids -> nomesPorVinculo(ids, "coordenadores")),
                new ReferenciaCadastro("supervisor", "Supervisor",
                        "select s.id, c.nome_busca as rotulo, 'Supervisor' as detalhe from supervisores s "
                                + "join colaboradores c on c.id = s.id_colaborador where s.ativo",
                        ids -> nomesPorVinculo(ids, "supervisores")),
                new ReferenciaCadastro("equipe", "Equipe",
                        "select e.id, e.cod_equipe as rotulo, concat_ws(' · ', t.nome, b.nome) as detalhe from equipes e "
                                + "left join tipos_equipe t on t.id = e.id_tipo_equipe left join bases_operacionais b on b.id = e.id_base_operacional "
                                + "where e.ativo"),
                new ReferenciaCadastro("funcao", "Função", "select id, nome as rotulo, descricao as detalhe from funcoes where ativo"),
                new ReferenciaCadastro("base_operacional", "Base operacional",
                        "select id, nome as rotulo, uf as detalhe from bases_operacionais where ativo"),
                new ReferenciaCadastro("processo", "Processo", "select id, nome as rotulo, null as detalhe from processos_negocio where ativo"),
                new ReferenciaCadastro("tipo_equipe", "Tipo de equipe", "select id, nome as rotulo, null as detalhe from tipos_equipe where ativo"),
                new ReferenciaCadastro("veiculo", "Veículo",
                        "select id, placa as rotulo, concat_ws(' ', fabricante, modelo) as detalhe from veiculos where ativo"),
                new ReferenciaCadastro("municipio", "Município",
                        "select m.id, m.nome as rotulo, b.nome as detalhe from municipios m left join bases_operacionais b "
                                + "on b.id = m.id_base_operacional where m.ativo"),
                new ReferenciaCadastro("centro_custo", "Centro de custo",
                        "select id, nome as rotulo, null as detalhe from centros_faturamento where ativo"));
    }

    @Override
    public List<OperacaoCadastro<?>> operacoes() {
        return List.of(
                OperacaoCadastro.criar("colaborador", ColaboradorRequest.class)
                        .modulo(MODULO).titulo("Novo colaborador").registro("Colaborador")
                        .descricao("Cadastra um colaborador (funcionario) com CPF, funcao, base, processo e data de contratacao. "
                                + "Para varios de uma vez (lista, planilha), use lote.")
                        .autoridade("rh:colaborador:write")
                        .campo("nomeCompleto", c -> c.rotulo("Nome completo"))
                        .campo("primeiroNome", c -> c.rotulo("Primeiro nome").derivado(dados -> parteDoNome(dados, true)))
                        .campo("ultimoNome", c -> c.rotulo("Último nome").derivado(dados -> parteDoNome(dados, false)))
                        .campo("cpf", c -> c.rotulo("CPF"))
                        .campo("idProcesso", c -> c.rotulo("Processo").referencia("processo"))
                        .campo("idBaseOperacional", c -> c.rotulo("Base operacional").referencia("base_operacional"))
                        .campo("idFuncao", c -> c.rotulo("Função").referencia("funcao"))
                        .identificadoPor("nomeCompleto")
                        .executor((r, id, usuario) -> colaboradores.criar(r))
                        .destino("RH_COLABORADORES")
                        .exemplo("Cadastra o colaborador Joao da Silva, CPF 123.456.789-09, eletricista na base Feira, processo Comercial, contratado hoje")
                        .build(),
                OperacaoCadastro.editar("colaborador", ColaboradorUpdateRequest.class)
                        .modulo(MODULO).titulo("Editar colaborador").registro("Colaborador")
                        .descricao("Corrige nome, funcao, base ou processo de um colaborador existente.")
                        .autoridade("rh:colaborador:write")
                        .carregador("colaborador", colaboradores::buscarPorId)
                        .campo("idProcesso", c -> c.rotulo("Processo").referencia("processo"))
                        .campo("idBaseOperacional", c -> c.rotulo("Base operacional").referencia("base_operacional"))
                        .campo("idFuncao", c -> c.rotulo("Função").referencia("funcao"))
                        .identificadoPor("nomeCompleto")
                        .executor((r, id, usuario) -> colaboradores.atualizar(id, r))
                        .destino("RH_COLABORADORES")
                        .build(),
                OperacaoCadastro.criar("equipe", EquipeRequest.class)
                        .modulo(MODULO).titulo("Nova equipe").registro("Equipe")
                        .descricao("Cria uma equipe de campo (codigo, tipo, base, veiculo, coordenador, supervisor, processo) "
                                + "e, se o usuario disser, ja com os integrantes (um RESPONSAVEL e os PARCEIROS).")
                        .autoridade("rh:equipe:write")
                        .campo("codigo", c -> c.rotulo("Código da equipe"))
                        .campo("idTipoEquipe", c -> c.rotulo("Tipo de equipe").referencia("tipo_equipe"))
                        .campo("idVeiculo", c -> c.rotulo("Veículo").referencia("veiculo"))
                        .campo("idCoordenador", c -> c.rotulo("Coordenador").referencia("coordenador"))
                        .campo("idSupervisor", c -> c.rotulo("Supervisor").referencia("supervisor"))
                        .campo("idProcesso", c -> c.rotulo("Processo").referencia("processo"))
                        .campo("membros", c -> c.rotulo("Integrantes"))
                        .campo("membros[].idColaborador", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("membros[].funcao", c -> c.rotulo("Papel na equipe"))
                        .identificadoPor("codigo")
                        .executor((r, id, usuario) -> equipes.criar(r))
                        .destino("RH_EQUIPES")
                        .exemplo("Cria a equipe FSA-01, tipo leve, base Feira, veiculo ABC1D23, supervisor Joao, coordenador Maria, processo Comercial")
                        .build(),
                OperacaoCadastro.editar("equipe", EquipeRequest.class)
                        .modulo(MODULO).titulo("Editar equipe").registro("Equipe")
                        .descricao("Troca veiculo, supervisor, coordenador, base, tipo ou integrantes de uma equipe existente.")
                        .autoridade("rh:equipe:write")
                        .carregador("equipe", equipes::buscarPorId)
                        .campo("idTipoEquipe", c -> c.rotulo("Tipo de equipe").referencia("tipo_equipe"))
                        .campo("idVeiculo", c -> c.rotulo("Veículo").referencia("veiculo"))
                        .campo("idCoordenador", c -> c.rotulo("Coordenador").referencia("coordenador"))
                        .campo("idSupervisor", c -> c.rotulo("Supervisor").referencia("supervisor"))
                        .campo("idProcesso", c -> c.rotulo("Processo").referencia("processo"))
                        .campo("membros", c -> c.rotulo("Integrantes"))
                        .campo("membros[].idColaborador", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("membros[].funcao", c -> c.rotulo("Papel na equipe"))
                        .identificadoPor("codigo")
                        .executor((r, id, usuario) -> equipes.atualizar(id, r))
                        .destino("RH_EQUIPES")
                        .semLote()
                        .build(),
                OperacaoCadastro.criar("membro_equipe", MembroEquipeCadastro.class)
                        .modulo(MODULO).titulo("Incluir integrante na equipe").registro("Integrante")
                        .descricao("Coloca um colaborador numa equipe existente como RESPONSAVEL ou PARCEIRO.")
                        .autoridade("rh:equipe:write")
                        .campo("idEquipe", c -> c.rotulo("Equipe").referencia("equipe"))
                        .campo("idColaborador", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("funcao", c -> c.rotulo("Papel na equipe"))
                        .identificadoPor("idColaborador", "idEquipe")
                        .executor((r, id, usuario) -> {
                            membros.alocarMembro(r.idEquipe(), new EquipeMembroRequest(r.idColaborador(), r.funcao()));
                            return r.idEquipe();
                        })
                        .destino("RH_EQUIPES")
                        .build(),
                simples("funcao", FuncaoRequest.class, "Nova função", "Função", "rh:funcao:write", "RH_FUNCOES",
                        "Cadastra uma funcao (cargo) de colaborador.", r -> funcoes.criar(r), "nome"),
                OperacaoCadastro.criar("base_operacional", BaseOperacionalRequest.class)
                        .modulo(MODULO).titulo("Nova base operacional").registro("Base operacional")
                        .descricao("Cadastra uma base operacional (unidade) com UF, endereco e centro de custo.")
                        .autoridade("rh:base-operacional:write")
                        .campo("uf", c -> c.rotulo("UF"))
                        .campo("centroCustoId", c -> c.rotulo("Centro de custo").referencia("centro_custo"))
                        .campo("raio", c -> c.rotulo("Raio da cerca (m)"))
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> bases.criar(r))
                        .destino("RH_BASES_OPERACIONAIS")
                        .build(),
                simples("processo", ProcessoNegocioRequest.class, "Novo processo", "Processo", "rh:processo-negocio:write",
                        "RH_PROCESSOS", "Cadastra um processo de negocio (ex.: Comercial, Emergencial).", r -> processos.criar(r), "nome"),
                simples("tipo_equipe", TipoEquipeRequest.class, "Novo tipo de equipe", "Tipo de equipe", "rh:tipo-equipe:write",
                        "RH_TIPOS_EQUIPE", "Cadastra um tipo de equipe.", r -> tiposEquipe.criar(r), "nome"),
                simples("centro_custo", CentroFaturamentoRequest.class, "Novo centro de custo", "Centro de custo",
                        "rh:centro-faturamento:write", "RH_CENTROS_CUSTO", "Cadastra um centro de custo (centro de faturamento).",
                        r -> centros.criar(r), "nome"),
                OperacaoCadastro.criar("veiculo", VeiculoRequest.class)
                        .modulo(MODULO).titulo("Novo veículo").registro("Veículo")
                        .descricao("Cadastra um veiculo (placa, modelo, fabricante, base) para vincular a equipes.")
                        .autoridade("rh:veiculo:write")
                        .campo("baseOperacionalId", c -> c.rotulo("Base operacional").referencia("base_operacional"))
                        .identificadoPor("placa")
                        .executor((r, id, usuario) -> veiculos.criar(r))
                        .destino("RH_VEICULOS")
                        .build(),
                OperacaoCadastro.criar("municipio", MunicipioRequest.class)
                        .modulo(MODULO).titulo("Novo município").registro("Município")
                        .descricao("Cadastra um municipio atendido por uma base.")
                        .autoridade("rh:municipio:write")
                        .campo("idBaseOperacional", c -> c.rotulo("Base operacional").referencia("base_operacional"))
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> municipios.criar(r))
                        .destino("RH_MUNICIPIOS")
                        .build(),
                OperacaoCadastro.criar("coordenador", CoordenadorRequest.class)
                        .modulo(MODULO).titulo("Novo coordenador").registro("Coordenador")
                        .descricao("Torna um colaborador coordenador a partir de uma data.")
                        .autoridade("rh:coordenador:write")
                        .campo("idColaborador", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("dataInicio", c -> c.rotulo("Início").padrao(() -> java.time.LocalDate.now()))
                        .identificadoPor("idColaborador")
                        .executor((r, id, usuario) -> coordenadores.criar(r))
                        .destino("RH_COLABORADORES")
                        .build(),
                OperacaoCadastro.criar("supervisor", SupervisorRequest.class)
                        .modulo(MODULO).titulo("Novo supervisor").registro("Supervisor")
                        .descricao("Torna um colaborador supervisor, ligado a um coordenador, a partir de uma data.")
                        .autoridade("rh:supervisor:write")
                        .campo("idColaborador", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("idCoordenador", c -> c.rotulo("Coordenador").referencia("coordenador"))
                        .campo("dataInicio", c -> c.rotulo("Início").padrao(() -> java.time.LocalDate.now()))
                        .identificadoPor("idColaborador")
                        .executor((r, id, usuario) -> supervisores.criar(r))
                        .destino("RH_COLABORADORES")
                        .build(),
                OperacaoCadastro.criar("meta_faturamento", MetaFaturamentoRequest.class)
                        .modulo(MODULO).titulo("Nova meta de faturamento").registro("Meta de faturamento")
                        .descricao("Define a meta de faturamento de uma ou mais equipes (valor por ciclo comercial ou mes).")
                        .autoridade("rh:meta-faturamento:write")
                        .campo("idsEquipes", c -> c.rotulo("Equipes").referencia("equipe"))
                        .campo("valor", c -> c.rotulo("Valor da meta"))
                        .campo("vigenciaInicio", c -> c.rotulo("Início da vigência"))
                        .campo("vigenciaFim", c -> c.rotulo("Fim da vigência"))
                        .campo("substituirVigentes", c -> c.rotulo("Substituir meta vigente").padrao(() -> Boolean.FALSE))
                        .identificadoPor("idsEquipes")
                        .executor((r, id, usuario) -> metas.criar(r))
                        .destino("RH_EQUIPES")
                        .semLote()
                        .build());
    }

    private static <R> OperacaoCadastro<R> simples(String id, Class<R> tipo, String titulo, String registro, String autoridade,
            String destino, String descricao, java.util.function.Function<R, Object> criar, String identificador) {
        return OperacaoCadastro.criar(id, tipo)
                .modulo(MODULO).titulo(titulo).registro(registro).descricao(descricao)
                .autoridade(autoridade)
                .identificadoPor(identificador)
                .executor((r, registroId, usuario) -> criar.apply(r))
                .destino(destino)
                .build();
    }

    private static Object parteDoNome(JsonNode dados, boolean primeiro) {
        JsonNode nome = dados == null ? null : dados.get("nomeCompleto");
        if (nome == null || nome.asText().isBlank()) return null;
        String[] partes = nome.asText().strip().split("\\s+");
        return primeiro ? partes[0] : partes[partes.length - 1];
    }

    private Map<UUID, String> nomesDeColaboradores(Collection<UUID> ids) {
        Map<UUID, String> nomes = new HashMap<>();
        for (Colaborador colaborador : colaboradorRepository.findAllById(ids)) nomes.put(colaborador.getId(), colaborador.getNomeCompleto());
        return nomes;
    }

    /** Coordenadores e supervisores sao vinculos de um colaborador: exibe o nome dele. */
    private Map<UUID, String> nomesPorVinculo(Collection<UUID> ids, String tabela) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, UUID> colaboradorPorVinculo = new HashMap<>();
        String marcadores = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        jdbc.query("select id, id_colaborador from " + tabela + " where id in (" + marcadores + ")",
                rs -> {
                    colaboradorPorVinculo.put(rs.getObject("id", UUID.class), rs.getObject("id_colaborador", UUID.class));
                },
                ids.toArray());
        Map<UUID, String> nomes = nomesDeColaboradores(colaboradorPorVinculo.values());
        Map<UUID, String> resultado = new HashMap<>();
        colaboradorPorVinculo.forEach((vinculo, colaborador) -> resultado.put(vinculo, nomes.get(colaborador)));
        return resultado;
    }
}
