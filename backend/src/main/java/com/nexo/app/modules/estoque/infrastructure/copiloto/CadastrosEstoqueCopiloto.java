package com.nexo.app.modules.estoque.infrastructure.copiloto;

import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ProvedorCadastros;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.estoque.application.dto.AvaliacaoMaterialRequest;
import com.nexo.app.modules.estoque.application.dto.CentroInvestimentoRequest;
import com.nexo.app.modules.estoque.application.dto.CriarSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.EstoqueRequest;
import com.nexo.app.modules.estoque.application.dto.MaterialRequest;
import com.nexo.app.modules.estoque.application.dto.MotivoPendenciaRequest;
import com.nexo.app.modules.estoque.application.dto.MotivoRejeicaoRequest;
import com.nexo.app.modules.estoque.application.dto.MovimentacaoBulkRequest;
import com.nexo.app.modules.estoque.application.dto.SolicitacaoRequest;
import com.nexo.app.modules.estoque.application.dto.TipoEstoqueRequest;
import com.nexo.app.modules.estoque.application.usecases.AvaliacaoMaterialUseCases;
import com.nexo.app.modules.estoque.application.usecases.CentroInvestimentoUseCases;
import com.nexo.app.modules.estoque.application.usecases.EstoqueUseCases;
import com.nexo.app.modules.estoque.application.usecases.MaterialUseCases;
import com.nexo.app.modules.estoque.application.usecases.MotivoPendenciaUseCases;
import com.nexo.app.modules.estoque.application.usecases.MotivoRejeicaoUseCases;
import com.nexo.app.modules.estoque.application.usecases.MovimentacaoEstoqueUseCases;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoMaterialUseCases;
import com.nexo.app.modules.estoque.application.usecases.SolicitacaoTecnicaUseCases;
import com.nexo.app.modules.estoque.application.usecases.TipoEstoqueUseCases;
import com.nexo.app.modules.estoque.domain.OrigemComandoTecnico;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** O que o Copiloto cadastra no Almoxarifado: solicitacoes de material, movimentacoes e cadastros de apoio. */
@Component
public class CadastrosEstoqueCopiloto implements ProvedorCadastros {

    private static final String MODULO = "Almoxarifado";

    /** Entrada avulsa: material que chega a um estoque sem vir de outro. */
    public record EntradaAvulsaCadastro(
            @NotNull @Schema(description = "Estoque que recebe o material") UUID idEstoqueDestino,
            @NotEmpty List<MovimentacaoBulkRequest.ItemMovimentacaoRequest> itens) {
    }

    /** Baixa avulsa: material que sai de um estoque sem ir para outro. */
    public record BaixaAvulsaCadastro(
            @NotNull @Schema(description = "Estoque de onde o material sai") UUID idEstoqueOrigem,
            @NotEmpty List<MovimentacaoBulkRequest.ItemMovimentacaoRequest> itens) {
    }

    /** Transferencia entre dois estoques. */
    public record TransferenciaCadastro(
            @NotNull @Schema(description = "Estoque que entrega o material") UUID idEstoqueOrigem,
            @NotNull @Schema(description = "Estoque que recebe o material") UUID idEstoqueDestino,
            @NotEmpty List<MovimentacaoBulkRequest.ItemMovimentacaoRequest> itens) {
    }

    private final SolicitacaoMaterialUseCases solicitacoes;
    private final SolicitacaoTecnicaUseCases solicitacoesTecnicas;
    private final MovimentacaoEstoqueUseCases movimentacoes;
    private final MaterialUseCases materiais;
    private final EstoqueUseCases estoques;
    private final TipoEstoqueUseCases tiposEstoque;
    private final CentroInvestimentoUseCases centrosInvestimento;
    private final AvaliacaoMaterialUseCases avaliacoes;
    private final MotivoRejeicaoUseCases motivosRejeicao;
    private final MotivoPendenciaUseCases motivosPendencia;

    public CadastrosEstoqueCopiloto(SolicitacaoMaterialUseCases solicitacoes, SolicitacaoTecnicaUseCases solicitacoesTecnicas,
            MovimentacaoEstoqueUseCases movimentacoes, MaterialUseCases materiais, EstoqueUseCases estoques,
            TipoEstoqueUseCases tiposEstoque, CentroInvestimentoUseCases centrosInvestimento,
            AvaliacaoMaterialUseCases avaliacoes, MotivoRejeicaoUseCases motivosRejeicao,
            MotivoPendenciaUseCases motivosPendencia) {
        this.solicitacoes = solicitacoes;
        this.solicitacoesTecnicas = solicitacoesTecnicas;
        this.movimentacoes = movimentacoes;
        this.materiais = materiais;
        this.estoques = estoques;
        this.tiposEstoque = tiposEstoque;
        this.centrosInvestimento = centrosInvestimento;
        this.avaliacoes = avaliacoes;
        this.motivosRejeicao = motivosRejeicao;
        this.motivosPendencia = motivosPendencia;
    }

    @Override
    public List<ReferenciaCadastro> referencias() {
        return List.of(
                new ReferenciaCadastro("estoque", "Estoque",
                        "select e.id, e.nome_identificador as rotulo, concat_ws(' · ', t.nome, b.nome) as detalhe from estoques e "
                                + "join tipos_estoque t on t.id = e.id_tipo_estoque "
                                + "left join bases_operacionais b on b.id = e.id_base_operacional where e.ativo"),
                new ReferenciaCadastro("almoxarifado", "Almoxarifado",
                        "select e.id, e.nome_identificador as rotulo, b.nome as detalhe from estoques e "
                                + "join tipos_estoque t on t.id = e.id_tipo_estoque "
                                + "left join bases_operacionais b on b.id = e.id_base_operacional "
                                + "where e.ativo and e.id_equipe is null and e.id_obra is null"),
                new ReferenciaCadastro("material", "Material",
                        "select id, descricao as rotulo, concat_ws(' · ', cod_material, unidade_medida) as detalhe "
                                + "from materiais where ativo"),
                new ReferenciaCadastro("tipo_estoque", "Tipo de estoque",
                        "select id, nome as rotulo, null as detalhe from tipos_estoque where ativo"),
                new ReferenciaCadastro("centro_investimento", "Centro de investimento",
                        "select id, codigo as rotulo, null as detalhe from centros_investimento where ativo"),
                new ReferenciaCadastro("avaliacao_material", "Avaliação técnica",
                        "select id, nome as rotulo, null as detalhe from avaliacoes_material where ativo"));
    }

    @Override
    public List<OperacaoCadastro<?>> operacoes() {
        return List.of(
                OperacaoCadastro.criar("solicitacao_material", SolicitacaoRequest.class)
                        .modulo(MODULO).titulo("Nova solicitação de material").registro("Solicitação de material")
                        .descricao("Pede material do almoxarifado para uma equipe (estoque de origem, equipe, data prevista do "
                                + "saque e itens com quantidade). Vai para aprovacao como na tela de solicitacoes.")
                        .autoridade("estoque:solicitacao:write")
                        .campo("idEstoqueOrigem", c -> c.rotulo("Almoxarifado").referencia("almoxarifado").obrigatorio(true))
                        .campo("idEstoqueDestino", c -> c.oculto())
                        .campo("idEquipe", c -> c.rotulo("Equipe").referencia("equipe"))
                        .campo("idColaboradorSaque", c -> c.rotulo("Quem vai sacar").referencia("colaborador"))
                        .campo("saqueForaEquipe", c -> c.rotulo("Quem vai sacar é de fora da equipe").padrao(() -> Boolean.FALSE))
                        .campo("dataPrevista", c -> c.rotulo("Data prevista do saque")
                                .padrao(() -> LocalDate.now().plusDays(2)))
                        .campo("itens", c -> c.rotulo("Materiais").obrigatorio(true))
                        .campo("itens[].idMaterial", c -> c.rotulo("Material").referencia("material").obrigatorio(true))
                        .campo("itens[].quantidade", c -> c.rotulo("Quantidade").obrigatorio(true))
                        .campo("observacao", c -> c.rotulo("Observação"))
                        .identificadoPor("idEquipe")
                        .executor((r, id, usuario) -> solicitacoes.criar(r, usuario))
                        .destinoDetalhe("ALMOX_SOLICITACAO_DETALHE")
                        .exemplo("Pede 20 metros de cabo de cobre e 1 transformador 32kVA do almoxarifado de Juazeiro para a equipe FSA-01 para amanha")
                        .build(),
                OperacaoCadastro.criar("solicitacao_tecnica", CriarSolicitacaoTecnicaRequest.class)
                        .modulo(MODULO).titulo("Nova solicitação técnica de obra").registro("Solicitação técnica")
                        .descricao("Solicitacao tecnica de material para uma obra: cada item informa o tipo "
                                + "(ADICAO_COMUM 221, ADICAO_CONCRETO 921, DEVOLUCAO_COMUM 222, DEVOLUCAO_CONCRETO 922).")
                        .autoridade("estoque:solicitacao:create")
                        .campo("idObra", c -> c.rotulo("Obra").referencia("obra"))
                        .campo("ocorrencia", c -> c.rotulo("Ocorrência"))
                        .campo("idAlmoxarifadoAtendimento", c -> c.rotulo("Almoxarifado").referencia("almoxarifado"))
                        .campo("idEquipe", c -> c.rotulo("Equipe").referencia("equipe"))
                        .campo("idColaboradorSaque", c -> c.rotulo("Quem vai sacar").referencia("colaborador"))
                        .campo("saqueForaEquipe", c -> c.rotulo("Quem vai sacar é de fora da equipe").padrao(() -> Boolean.FALSE))
                        .campo("dataPrevista", c -> c.rotulo("Data prevista do saque"))
                        .campo("observacao", c -> c.rotulo("Observação"))
                        .campo("itens", c -> c.rotulo("Materiais"))
                        .campo("itens[].idMaterial", c -> c.rotulo("Material").referencia("material"))
                        .campo("itens[].tipoMovimentacao", c -> c.rotulo("Tipo"))
                        .campo("itens[].quantidade", c -> c.rotulo("Quantidade"))
                        .identificadoPor("idObra")
                        .executor((r, id, usuario) -> {
                            var criadas = solicitacoesTecnicas.criar(r, usuario, OrigemComandoTecnico.UI).solicitacoesCriadas();
                            return criadas.isEmpty() ? null : criadas.getFirst().id();
                        })
                        .destinoDetalhe("ALMOX_SOLICITACAO_TECNICA_DETALHE")
                        .exemplo("Solicitacao tecnica da obra 2123123: adicao comum de 3 transformadores 32kVA pelo almoxarifado de Juazeiro")
                        .build(),
                OperacaoCadastro.criar("entrada_estoque", EntradaAvulsaCadastro.class)
                        .modulo(MODULO).titulo("Entrada avulsa de material").registro("Entrada avulsa")
                        .descricao("Lanca material que chegou a um estoque (compra, devolucao, ajuste para mais). "
                                + "Materiais com serie: um item por numero de serie, quantidade 1.")
                        .autoridade("estoque:movimentacao:write")
                        .campo("idEstoqueDestino", c -> c.rotulo("Estoque").referencia("estoque"))
                        .campo("itens", c -> c.rotulo("Materiais"))
                        .campo("itens[].idMaterial", c -> c.rotulo("Material").referencia("material").obrigatorio(true))
                        .campo("itens[].quantidade", c -> c.rotulo("Quantidade").obrigatorio(true))
                        .campo("itens[].numSerie", c -> c.rotulo("Número de série"))
                        .campo("itens[].condicao", c -> c.rotulo("Condição"))
                        .identificadoPor("idEstoqueDestino")
                        .executor((r, id, usuario) -> {
                            movimentacoes.entradaLote(new MovimentacaoBulkRequest(null, r.idEstoqueDestino(), r.itens()), usuario);
                            return null;
                        })
                        .destino("ALMOX_MOVIMENTACOES")
                        .semLote()
                        .exemplo("Da entrada de 100 metros de cabo de cobre no almoxarifado de Juazeiro")
                        .build(),
                OperacaoCadastro.criar("baixa_estoque", BaixaAvulsaCadastro.class)
                        .modulo(MODULO).titulo("Baixa avulsa de material").registro("Baixa avulsa")
                        .descricao("Retira material de um estoque sem destino (perda, consumo, ajuste para menos).")
                        .autoridade("estoque:movimentacao:write")
                        .campo("idEstoqueOrigem", c -> c.rotulo("Estoque").referencia("estoque"))
                        .campo("itens", c -> c.rotulo("Materiais"))
                        .campo("itens[].idMaterial", c -> c.rotulo("Material").referencia("material").obrigatorio(true))
                        .campo("itens[].quantidade", c -> c.rotulo("Quantidade").obrigatorio(true))
                        .campo("itens[].numSerie", c -> c.rotulo("Número de série"))
                        .campo("itens[].condicao", c -> c.rotulo("Condição"))
                        .identificadoPor("idEstoqueOrigem")
                        .executor((r, id, usuario) -> {
                            movimentacoes.baixaLote(new MovimentacaoBulkRequest(r.idEstoqueOrigem(), null, r.itens()), usuario);
                            return null;
                        })
                        .destino("ALMOX_MOVIMENTACOES")
                        .semLote()
                        .build(),
                OperacaoCadastro.criar("transferencia_estoque", TransferenciaCadastro.class)
                        .modulo(MODULO).titulo("Transferência entre estoques").registro("Transferência")
                        .descricao("Move material de um estoque para outro (ex.: do almoxarifado para o estoque de campo de uma equipe).")
                        .autoridade("estoque:movimentacao:write")
                        .campo("idEstoqueOrigem", c -> c.rotulo("De").referencia("estoque"))
                        .campo("idEstoqueDestino", c -> c.rotulo("Para").referencia("estoque"))
                        .campo("itens", c -> c.rotulo("Materiais"))
                        .campo("itens[].idMaterial", c -> c.rotulo("Material").referencia("material").obrigatorio(true))
                        .campo("itens[].quantidade", c -> c.rotulo("Quantidade").obrigatorio(true))
                        .campo("itens[].numSerie", c -> c.rotulo("Número de série"))
                        .campo("itens[].condicao", c -> c.rotulo("Condição"))
                        .identificadoPor("idEstoqueOrigem", "idEstoqueDestino")
                        .executor((r, id, usuario) -> movimentacoes.transferenciaLote(
                                new MovimentacaoBulkRequest(r.idEstoqueOrigem(), r.idEstoqueDestino(), r.itens()), usuario))
                        .destinoDetalhe("ALMOX_MOVIMENTACAO_DETALHE")
                        .semLote()
                        .build(),
                OperacaoCadastro.criar("material", MaterialRequest.class)
                        .modulo(MODULO).titulo("Novo material").registro("Material")
                        .descricao("Cadastra um material no catalogo (codigo, descricao, unidade de medida, se detalha numero de serie).")
                        .autoridade("estoque:material:write")
                        .campo("codigo", c -> c.rotulo("Código"))
                        .campo("unidadeMedida", c -> c.rotulo("Unidade de medida"))
                        .campo("suprMatrFlag", c -> c.oculto())
                        .campo("idProcesso", c -> c.rotulo("Processo").referencia("processo"))
                        .campo("idAvaliacaoTecnica", c -> c.rotulo("Avaliação técnica").referencia("avaliacao_material"))
                        .campo("idCentroInvestimento", c -> c.rotulo("Centro de investimento").referencia("centro_investimento"))
                        .campo("detalhaSerie", c -> c.rotulo("Controla número de série").padrao(() -> Boolean.FALSE))
                        .identificadoPor("codigo", "descricao")
                        .executor((r, id, usuario) -> materiais.criar(r))
                        .destino("ALMOX_MATERIAIS")
                        .build(),
                OperacaoCadastro.criar("estoque", EstoqueRequest.class)
                        .modulo(MODULO).titulo("Novo estoque").registro("Estoque")
                        .descricao("Cria um estoque (almoxarifado fisico, estoque de campo de equipe) numa base operacional.")
                        .autoridade("estoque:estoque:write")
                        .campo("nomeIdentificador", c -> c.rotulo("Nome"))
                        .campo("idTipoEstoque", c -> c.rotulo("Tipo de estoque").referencia("tipo_estoque"))
                        .campo("idBaseOperacional", c -> c.rotulo("Base operacional").referencia("base_operacional"))
                        .campo("idEquipe", c -> c.rotulo("Equipe").referencia("equipe"))
                        .identificadoPor("nomeIdentificador")
                        .executor((r, id, usuario) -> estoques.criar(r))
                        .destino("ALMOX_ESTOQUES")
                        .build(),
                simples("tipo_estoque", TipoEstoqueRequest.class, "Novo tipo de estoque", "Tipo de estoque",
                        "estoque:tipo-estoque:write", "ALMOX_TIPOS_ESTOQUE", "Cadastra um tipo de estoque.",
                        tiposEstoque::criar, "nome"),
                simples("centro_investimento", CentroInvestimentoRequest.class, "Novo centro de investimento",
                        "Centro de investimento", "estoque:centro-investimento:write", "ALMOX_CENTROS_INVESTIMENTO",
                        "Cadastra um centro de investimento (codigo de ate 10 caracteres).", centrosInvestimento::criar, "codigo"),
                simples("avaliacao_material", AvaliacaoMaterialRequest.class, "Nova avaliação de material",
                        "Avaliação de material", "estoque:avaliacao-material:write", "ALMOX_AVALIACOES",
                        "Cadastra uma avaliacao tecnica de material.", avaliacoes::criar, "nome"),
                simples("motivo_rejeicao", MotivoRejeicaoRequest.class, "Novo motivo de rejeição", "Motivo de rejeição",
                        "estoque:motivo-rejeicao:write", "ALMOX_SOLICITACOES",
                        "Cadastra um motivo usado para rejeitar solicitacoes de material.", motivosRejeicao::criar, "nome"),
                simples("motivo_pendencia", MotivoPendenciaRequest.class, "Novo motivo de pendência", "Motivo de pendência",
                        "estoque:motivo-rejeicao:write", "ALMOX_SOLICITACOES",
                        "Cadastra um motivo de pendencia de solicitacao de material.", motivosPendencia::criar, "nome"));
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
}
