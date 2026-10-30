package com.nexo.app.modules.obras.infrastructure.copiloto;

import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ProvedorCadastros;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.assistente.application.cadastro.TipoCampo;
import com.nexo.app.modules.obras.application.dto.AgenteResponsavelRequest;
import com.nexo.app.modules.obras.application.dto.ObraRequest;
import com.nexo.app.modules.obras.application.dto.StatusObraRequest;
import com.nexo.app.modules.obras.application.dto.TipoAnexoObraRequest;
import com.nexo.app.modules.obras.application.usecases.AgenteResponsavelUseCases;
import com.nexo.app.modules.obras.application.usecases.ObraUseCases;
import com.nexo.app.modules.obras.application.usecases.StatusObraUseCases;
import com.nexo.app.modules.obras.application.usecases.TipoAnexoObraUseCases;
import com.nexo.app.modules.obras.domain.TipoObra;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** O que o Copiloto cadastra em Obras: a obra e os cadastros de apoio (status, agentes, tipos de anexo). */
@Component
public class CadastrosObrasCopiloto implements ProvedorCadastros {

    private static final String MODULO = "Obras";

    /** Mesmos dados da tela de nova obra (o DTO da tela usa datas no formato do formulario). */
    public record ObraCadastro(
            @NotBlank @Size(max = 7) @Schema(description = "Numero da obra, ate 7 caracteres") String numero,
            @NotBlank @Size(min = 2, max = 300) String titulo,
            @Size(max = 1000) String descricao,
            TipoObra tipoObra,
            @Size(max = 50) String notaObra,
            BigDecimal valorProjeto,
            @NotNull LocalDate dataInicio,
            @NotNull LocalDate dataPrevisaoFim,
            @NotNull UUID idProcesso,
            @NotNull UUID idBaseOperacional,
            @NotNull UUID idMunicipio,
            @NotNull UUID idCentroCusto,
            @NotNull UUID idSupervisor,
            @NotNull UUID idCoordenador,
            @NotNull UUID idStatusObra,
            UUID idAgenteResponsavel,
            List<UUID> equipes) {
    }

    private final ObraUseCases obras;
    private final StatusObraUseCases status;
    private final AgenteResponsavelUseCases agentes;
    private final TipoAnexoObraUseCases tiposAnexo;

    public CadastrosObrasCopiloto(ObraUseCases obras, StatusObraUseCases status, AgenteResponsavelUseCases agentes,
            TipoAnexoObraUseCases tiposAnexo) {
        this.obras = obras;
        this.status = status;
        this.agentes = agentes;
        this.tiposAnexo = tiposAnexo;
    }

    @Override
    public List<ReferenciaCadastro> referencias() {
        return List.of(
                new ReferenciaCadastro("obra", "Obra",
                        "select id, numero as rotulo, titulo as detalhe from obras where ativo"),
                new ReferenciaCadastro("status_obra", "Status da obra",
                        "select id, nome as rotulo, null as detalhe from status_obras where ativo"),
                new ReferenciaCadastro("agente_responsavel", "Agente responsável",
                        "select id, nome as rotulo, empresa_orgao as detalhe from agentes_responsaveis_obras where ativo"));
    }

    @Override
    public List<OperacaoCadastro<?>> operacoes() {
        return List.of(
                OperacaoCadastro.criar("obra", ObraCadastro.class)
                        .modulo(MODULO).titulo("Nova obra").registro("Obra")
                        .descricao("Cadastra uma obra (numero, titulo, datas, processo, base, municipio, centro de custo, "
                                + "supervisor, coordenador, status e equipes).")
                        .autoridade("obras:obra:write")
                        .campo("numero", c -> c.rotulo("Número"))
                        .campo("titulo", c -> c.rotulo("Título"))
                        .campo("descricao", c -> c.rotulo("Descrição").tipo(TipoCampo.TEXTO_LONGO))
                        .campo("tipoObra", c -> c.rotulo("Tipo de obra"))
                        .campo("notaObra", c -> c.rotulo("Nota"))
                        .campo("valorProjeto", c -> c.rotulo("Valor do projeto"))
                        .campo("dataInicio", c -> c.rotulo("Início").padrao(LocalDate::now))
                        .campo("dataPrevisaoFim", c -> c.rotulo("Previsão de conclusão"))
                        .campo("idProcesso", c -> c.rotulo("Processo").referencia("processo"))
                        .campo("idBaseOperacional", c -> c.rotulo("Base operacional").referencia("base_operacional"))
                        .campo("idMunicipio", c -> c.rotulo("Município").referencia("municipio"))
                        .campo("idCentroCusto", c -> c.rotulo("Centro de custo").referencia("centro_custo"))
                        .campo("idSupervisor", c -> c.rotulo("Supervisor").referencia("supervisor"))
                        .campo("idCoordenador", c -> c.rotulo("Coordenador").referencia("coordenador"))
                        .campo("idStatusObra", c -> c.rotulo("Status").referencia("status_obra"))
                        .campo("idAgenteResponsavel", c -> c.rotulo("Agente responsável").referencia("agente_responsavel"))
                        .campo("equipes", c -> c.rotulo("Equipes").referencia("equipe"))
                        .identificadoPor("numero", "titulo")
                        .executor((r, id, usuario) -> obras.criar(new ObraRequest(r.numero(), r.titulo(), r.descricao(),
                                r.notaObra(), r.valorProjeto(), r.dataInicio(), r.dataPrevisaoFim(), null, null, null,
                                r.idProcesso(), r.idBaseOperacional(), r.idMunicipio(), r.idCentroCusto(), r.idSupervisor(),
                                r.idCoordenador(), r.idStatusObra(), r.idAgenteResponsavel(), null, null,
                                r.equipes() == null ? Set.of() : Set.copyOf(r.equipes()), r.tipoObra())))
                        .destinoDetalhe("OBRA_DETALHE")
                        .exemplo("Cadastra a obra 2123200, Ampliacao de rede em Juazeiro, inicio hoje, conclusao em 30 dias, "
                                + "base Juazeiro, processo Obras, supervisor Joao, coordenador Maria, status Planejada")
                        .build(),
                OperacaoCadastro.criar("status_obra", StatusObraRequest.class)
                        .modulo(MODULO).titulo("Novo status de obra").registro("Status de obra")
                        .descricao("Cadastra um status de obra (ordem no fluxo e se encerra a obra).")
                        .autoridade("obras:cadastros:write")
                        .campo("concluida", c -> c.rotulo("Encerra a obra").padrao(() -> Boolean.FALSE))
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> status.criar(r))
                        .destino("OBRAS_STATUS")
                        .build(),
                OperacaoCadastro.criar("agente_responsavel", AgenteResponsavelRequest.class)
                        .modulo(MODULO).titulo("Novo agente responsável").registro("Agente responsável")
                        .descricao("Cadastra um agente responsavel externo por obras (nome, e-mail, telefone, empresa ou orgao).")
                        .autoridade("obras:cadastros:write")
                        .campo("empresaOrgao", c -> c.rotulo("Empresa ou órgão"))
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> agentes.criar(r))
                        .destino("OBRAS_AGENTES")
                        .build(),
                OperacaoCadastro.criar("tipo_anexo_obra", TipoAnexoObraRequest.class)
                        .modulo(MODULO).titulo("Novo tipo de anexo de obra").registro("Tipo de anexo")
                        .descricao("Cadastra um tipo de anexo de obra (projeto, ART, foto...).")
                        .autoridade("obras:cadastros:write")
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> tiposAnexo.criar(r))
                        .destino("OBRAS_TIPOS_ANEXO")
                        .build());
    }
}
