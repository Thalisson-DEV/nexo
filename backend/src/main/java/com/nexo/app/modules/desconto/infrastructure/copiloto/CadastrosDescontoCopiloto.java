package com.nexo.app.modules.desconto.infrastructure.copiloto;

import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ProvedorCadastros;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.assistente.application.cadastro.TipoCampo;
import com.nexo.app.modules.desconto.application.dto.DescontoItemRequest;
import com.nexo.app.modules.desconto.application.dto.DescontoRequest;
import com.nexo.app.modules.desconto.application.dto.MotivoDescontoRequest;
import com.nexo.app.modules.desconto.application.dto.TipoEquipamentoRequest;
import com.nexo.app.modules.desconto.application.usecases.DescontoUseCases;
import com.nexo.app.modules.desconto.application.usecases.MotivoDescontoUseCases;
import com.nexo.app.modules.desconto.application.usecases.TipoEquipamentoUseCases;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** O que o Copiloto cadastra em Descontos: a solicitacao de desconto (com os itens) e os cadastros de apoio. */
@Component
public class CadastrosDescontoCopiloto implements ProvedorCadastros {

    private static final String MODULO = "Descontos";
    private static final ZoneId FUSO = ZoneId.of("America/Bahia");

    /** Solicitacao de desconto ja com os itens cobrados (o caso de uso recebe os itens um a um). */
    public record DescontoCadastro(
            @NotNull UUID colaboradorId,
            @NotNull @Schema(description = "Motivo do desconto (perda, avaria, mau uso...)") UUID motivoId,
            @NotNull @Min(1) Integer quantidadeParcelas,
            @NotNull @Schema(description = "Dia em que a ocorrencia aconteceu") LocalDate dataOcorrencia,
            String observacoes,
            @Schema(description = "Materiais ou equipamentos cobrados") List<DescontoItemRequest> itens) {
    }

    private final DescontoUseCases descontos;
    private final MotivoDescontoUseCases motivos;
    private final TipoEquipamentoUseCases tiposEquipamento;

    public CadastrosDescontoCopiloto(DescontoUseCases descontos, MotivoDescontoUseCases motivos,
            TipoEquipamentoUseCases tiposEquipamento) {
        this.descontos = descontos;
        this.motivos = motivos;
        this.tiposEquipamento = tiposEquipamento;
    }

    @Override
    public List<ReferenciaCadastro> referencias() {
        return List.of(
                new ReferenciaCadastro("motivo_desconto", "Motivo do desconto",
                        "select id, descricao as rotulo, concat('até ', teto_parcelas, ' parcelas') as detalhe "
                                + "from motivo_desconto where ativo"),
                new ReferenciaCadastro("tipo_equipamento", "Tipo de equipamento",
                        "select id, descricao as rotulo, null as detalhe from tipo_equipamento where ativo"));
    }

    @Override
    public List<OperacaoCadastro<?>> operacoes() {
        return List.of(
                OperacaoCadastro.criar("desconto", DescontoCadastro.class)
                        .modulo(MODULO).titulo("Nova solicitação de desconto").registro("Desconto")
                        .descricao("Solicita desconto em folha de um colaborador (motivo, parcelas, data da ocorrencia) "
                                + "com os itens cobrados (descricao do material, tipo de equipamento e valor).")
                        .autoridade("desconto:solicitar")
                        .campo("colaboradorId", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("motivoId", c -> c.rotulo("Motivo").referencia("motivo_desconto"))
                        .campo("quantidadeParcelas", c -> c.rotulo("Parcelas").padrao(() -> 1))
                        .campo("dataOcorrencia", c -> c.rotulo("Data da ocorrência"))
                        .campo("observacoes", c -> c.rotulo("Observações").tipo(TipoCampo.TEXTO_LONGO))
                        .campo("itens", c -> c.rotulo("Itens cobrados"))
                        .campo("itens[].descricaoMaterial", c -> c.rotulo("Material"))
                        .campo("itens[].tipoEquipamentoId", c -> c.rotulo("Tipo de equipamento").referencia("tipo_equipamento"))
                        .campo("itens[].valor", c -> c.rotulo("Valor").tipo(TipoCampo.MOEDA))
                        .identificadoPor("colaboradorId", "motivoId")
                        .executor((r, id, usuario) -> {
                            UUID criado = descontos.solicitar(new DescontoRequest(r.colaboradorId(), r.motivoId(), r.observacoes(),
                                    r.quantidadeParcelas(), r.dataOcorrencia().atStartOfDay(FUSO).toOffsetDateTime()), usuario).id();
                            if (r.itens() != null) r.itens().forEach(item -> descontos.adicionarItem(criado, item));
                            return criado;
                        })
                        .destinoDetalhe("DESCONTO_DETALHE")
                        .exemplo("Desconto para o Joao da Silva por perda de um alicate, R$ 80, em 2 parcelas, ocorrido ontem")
                        .build(),
                OperacaoCadastro.criar("motivo_desconto", MotivoDescontoRequest.class)
                        .modulo(MODULO).titulo("Novo motivo de desconto").registro("Motivo de desconto")
                        .descricao("Cadastra um motivo de desconto e o maximo de parcelas permitido.")
                        .autoridade("desconto:motivo:write")
                        .campo("tetoParcelas", c -> c.rotulo("Máximo de parcelas"))
                        .identificadoPor("descricao")
                        .executor((r, id, usuario) -> motivos.criar(r))
                        .destino("DESCONTO_MOTIVOS")
                        .build(),
                OperacaoCadastro.criar("tipo_equipamento", TipoEquipamentoRequest.class)
                        .modulo(MODULO).titulo("Novo tipo de equipamento").registro("Tipo de equipamento")
                        .descricao("Cadastra um tipo de equipamento usado nos itens de desconto.")
                        .autoridade("desconto:tipo-equipamento:write")
                        .identificadoPor("descricao")
                        .executor((r, id, usuario) -> tiposEquipamento.criar(r))
                        .destino("DESCONTO_TIPOS_EQUIPAMENTO")
                        .build());
    }
}
