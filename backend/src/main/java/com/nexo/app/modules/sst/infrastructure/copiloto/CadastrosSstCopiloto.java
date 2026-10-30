package com.nexo.app.modules.sst.infrastructure.copiloto;

import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ProvedorCadastros;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.assistente.application.cadastro.TipoCampo;
import com.nexo.app.modules.sst.application.dto.AcaoGovernancaSstRequest;
import com.nexo.app.modules.sst.application.dto.AtividadeSstRequest;
import com.nexo.app.modules.sst.application.dto.CatalogoSstRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstRequest;
import com.nexo.app.modules.sst.application.dto.MetaInspecaoRequest;
import com.nexo.app.modules.sst.application.dto.TipoInspecaoRequest;
import com.nexo.app.modules.sst.application.usecases.AcaoGovernancaSstUseCases;
import com.nexo.app.modules.sst.application.usecases.AtividadeSstUseCases;
import com.nexo.app.modules.sst.application.usecases.CatalogoSstUseCases;
import com.nexo.app.modules.sst.application.usecases.EventoSstUseCases;
import com.nexo.app.modules.sst.application.usecases.MetaInspecaoUseCases;
import com.nexo.app.modules.sst.application.usecases.TipoInspecaoUseCases;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/** O que o Copiloto cadastra em Segurança do trabalho: eventos, acoes, metas de inspecao e catalogos. */
@Component
public class CadastrosSstCopiloto implements ProvedorCadastros {

    private static final String MODULO = "Segurança do trabalho";

    private final EventoSstUseCases eventos;
    private final AcaoGovernancaSstUseCases acoes;
    private final MetaInspecaoUseCases metas;
    private final TipoInspecaoUseCases tiposInspecao;
    private final AtividadeSstUseCases atividades;
    private final CatalogoSstUseCases catalogos;

    public CadastrosSstCopiloto(EventoSstUseCases eventos, AcaoGovernancaSstUseCases acoes, MetaInspecaoUseCases metas,
            TipoInspecaoUseCases tiposInspecao, AtividadeSstUseCases atividades, CatalogoSstUseCases catalogos) {
        this.eventos = eventos;
        this.acoes = acoes;
        this.metas = metas;
        this.tiposInspecao = tiposInspecao;
        this.atividades = atividades;
        this.catalogos = catalogos;
    }

    @Override
    public List<ReferenciaCadastro> referencias() {
        return List.of(
                new ReferenciaCadastro("atividade_sst", "Atividade",
                        "select id, nome as rotulo, descricao as detalhe from atividades_sst where ativo"),
                catalogo("classificacao_evento_sst", "Classificação do evento", "CLASSIFICACAO_EVENTO"),
                catalogo("local_lesao_sst", "Local da lesão", "LOCAL_LESAO"),
                catalogo("natureza_lesao_sst", "Natureza da lesão", "NATUREZA_LESAO"),
                catalogo("agente_causador_sst", "Agente causador", "AGENTE_CAUSADOR"),
                new ReferenciaCadastro("tipo_inspecao", "Tipo de inspeção",
                        "select id, nome as rotulo, descricao as detalhe from tipos_inspecao where ativo"),
                new ReferenciaCadastro("corpo_governanca_sst", "CIPA ou brigada",
                        "select id, nome as rotulo, concat_ws(' · ', tipo, base_nome) as detalhe from corpos_governanca_sst "
                                + "where ativo and status = 'ATIVO'"));
    }

    private static ReferenciaCadastro catalogo(String tipo, String rotulo, String tipoCatalogo) {
        return new ReferenciaCadastro(tipo, rotulo,
                "select id, nome as rotulo, codigo as detalhe from catalogos_sst where ativo and tipo = '" + tipoCatalogo + "'");
    }

    @Override
    public List<OperacaoCadastro<?>> operacoes() {
        return List.of(
                OperacaoCadastro.criar("evento_sst", EventoSstRequest.class)
                        .modulo(MODULO).titulo("Novo evento de segurança").registro("Evento de segurança")
                        .descricao("Registra um acidente, incidente ou quase acidente: tipo, data e hora, atividade, base, "
                                + "classificacao, o que aconteceu, tecnicos de seguranca responsaveis e envolvidos "
                                + "(acidentado/testemunha). Lesao e CAT so quando houver.")
                        .autoridade("sst:evento:write")
                        .campo("tipo", c -> c.rotulo("Tipo do evento"))
                        .campo("dataOcorrencia", c -> c.rotulo("Quando aconteceu"))
                        .campo("dataComunicacao", c -> c.rotulo("Quando foi comunicado")
                                .padrao(() -> OffsetDateTime.now(ZoneId.of("America/Bahia")).withSecond(0).withNano(0)))
                        .campo("idAtividade", c -> c.rotulo("Atividade em execução").referencia("atividade_sst"))
                        .campo("idGestor", c -> c.rotulo("Gestor responsável").referencia("colaborador"))
                        .campo("idBaseOperacional", c -> c.rotulo("Base operacional").referencia("base_operacional"))
                        .campo("idEquipe", c -> c.rotulo("Equipe").referencia("equipe"))
                        .campo("idVeiculo", c -> c.rotulo("Veículo").referencia("veiculo"))
                        .campo("idClassificacao", c -> c.rotulo("Classificação").referencia("classificacao_evento_sst"))
                        .campo("idProcesso", c -> c.rotulo("Processo").referencia("processo"))
                        .campo("telemetria", c -> c.oculto())
                        .campo("filmagem", c -> c.oculto())
                        .campo("latitude", c -> c.oculto())
                        .campo("longitude", c -> c.oculto())
                        .campo("localOcorrencia", c -> c.rotulo("Local"))
                        .campo("descricao", c -> c.rotulo("O que aconteceu").tipo(TipoCampo.TEXTO_LONGO))
                        .campo("idLocalLesao", c -> c.rotulo("Local da lesão").referencia("local_lesao_sst"))
                        .campo("idNaturezaLesao", c -> c.rotulo("Natureza da lesão").referencia("natureza_lesao_sst"))
                        .campo("idAgenteCausador", c -> c.rotulo("Agente causador").referencia("agente_causador_sst"))
                        .campo("diasPerdidos", c -> c.rotulo("Dias perdidos"))
                        .campo("possuiCat", c -> c.rotulo("Tem CAT").padrao(() -> Boolean.FALSE))
                        .campo("tipoCat", c -> c.rotulo("Tipo da CAT"))
                        .campo("numeroCat", c -> c.rotulo("Número da CAT"))
                        .campo("dataEmissaoCat", c -> c.rotulo("Emissão da CAT"))
                        .campo("idsTecnicos", c -> c.rotulo("Técnicos de segurança").referencia("colaborador"))
                        .campo("envolvidos", c -> c.rotulo("Envolvidos"))
                        .campo("envolvidos[].tipo", c -> c.rotulo("Colaborador ou terceiro"))
                        .campo("envolvidos[].papel", c -> c.rotulo("Papel"))
                        .campo("envolvidos[].idColaborador", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("envolvidos[].nome", c -> c.rotulo("Nome (terceiro)"))
                        .campo("envolvidos[].funcao", c -> c.rotulo("Função (terceiro)"))
                        .campo("envolvidos[].empresa", c -> c.rotulo("Empresa (terceiro)"))
                        .identificadoPor("tipo", "idBaseOperacional")
                        .executor((r, id, usuario) -> eventos.registrar(r))
                        .destinoDetalhe("SST_EVENTO_DETALHE")
                        .exemplo("Registra um quase acidente hoje as 9h na base Juazeiro, atividade Implantacao de Poste: "
                                + "poste escorregou do guindauto, ninguem ferido; tecnico Carlos")
                        .semLote()
                        .build(),
                OperacaoCadastro.criar("acao_sst", AcaoGovernancaSstRequest.class)
                        .modulo(MODULO).titulo("Nova ação de segurança").registro("Ação de segurança")
                        .descricao("Cria uma acao (corretiva, preventiva, inspecao...) da CIPA ou brigada com responsavel e prazo.")
                        .autoridade("sst:governanca:write")
                        .campo("idCorpo", c -> c.rotulo("CIPA ou brigada").referencia("corpo_governanca_sst"))
                        .campo("idBaseOperacional", c -> c.rotulo("Base operacional").referencia("base_operacional"))
                        .campo("idReuniao", c -> c.oculto())
                        .campo("idExecucaoChecklist", c -> c.oculto())
                        .campo("clientRequestId", c -> c.oculto())
                        .campo("versaoEsperada", c -> c.oculto())
                        .campo("titulo", c -> c.rotulo("Título"))
                        .campo("descricao", c -> c.rotulo("Descrição").tipo(TipoCampo.TEXTO_LONGO))
                        .campo("tipo", c -> c.rotulo("Tipo"))
                        .campo("prioridade", c -> c.rotulo("Prioridade"))
                        .campo("idResponsavel", c -> c.rotulo("Responsável").referencia("colaborador"))
                        .campo("prazo", c -> c.rotulo("Prazo"))
                        .identificadoPor("titulo")
                        .executor((r, id, usuario) -> acoes.criar(r))
                        .destino("SST_GOVERNANCA_ACOES")
                        .build(),
                OperacaoCadastro.criar("meta_inspecao", MetaInspecaoRequest.class)
                        .modulo(MODULO).titulo("Nova meta de inspeção").registro("Meta de inspeção")
                        .descricao("Define quantas inspecoes de um tipo uma funcao ou um colaborador deve fazer por periodo.")
                        .autoridade("sst:meta:write")
                        .campo("idTipoInspecao", c -> c.rotulo("Tipo de inspeção").referencia("tipo_inspecao"))
                        .campo("idFuncao", c -> c.rotulo("Função").referencia("funcao"))
                        .campo("idColaborador", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("quantidade", c -> c.rotulo("Inspeções por período"))
                        .campo("periodicidade", c -> c.rotulo("Período"))
                        .campo("vigenciaInicio", c -> c.rotulo("Início da vigência").padrao(LocalDate::now))
                        .campo("vigenciaFim", c -> c.rotulo("Fim da vigência"))
                        .identificadoPor("idTipoInspecao", "idFuncao", "idColaborador")
                        .executor((r, id, usuario) -> metas.criar(r))
                        .destino("SST_METAS_INSPECAO")
                        .build(),
                OperacaoCadastro.criar("tipo_inspecao", TipoInspecaoRequest.class)
                        .modulo(MODULO).titulo("Novo tipo de inspeção").registro("Tipo de inspeção")
                        .descricao("Cadastra um tipo de inspecao de seguranca.")
                        .autoridade("sst:inspecao:write", "sst:catalogo:write")
                        .campo("ordem", c -> c.oculto())
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> tiposInspecao.criar(r))
                        .destino("SST_TIPOS_INSPECAO")
                        .build(),
                OperacaoCadastro.criar("atividade_sst", AtividadeSstRequest.class)
                        .modulo(MODULO).titulo("Nova atividade de segurança").registro("Atividade")
                        .descricao("Cadastra uma atividade de campo (usada nos eventos de seguranca) com gestor e processo.")
                        .autoridade("sst:catalogo:write")
                        .campo("idGestor", c -> c.rotulo("Gestor").referencia("colaborador"))
                        .campo("idProcesso", c -> c.rotulo("Processo").referencia("processo"))
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> atividades.criar(r))
                        .destino("SST_ATIVIDADES")
                        .build(),
                OperacaoCadastro.criar("catalogo_sst", CatalogoSstRequest.class)
                        .modulo(MODULO).titulo("Novo item de catálogo de segurança").registro("Item de catálogo")
                        .descricao("Cadastra classificacao de evento, local da lesao, natureza da lesao ou agente causador.")
                        .autoridade("sst:catalogo:write")
                        .campo("tipo", c -> c.rotulo("Catálogo"))
                        .campo("ordem", c -> c.oculto())
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> catalogos.criar(r))
                        .destino("SST_EVENTOS")
                        .build());
    }
}
