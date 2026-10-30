package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.RegistroEvidencia;
import com.nexo.app.modules.sst.domain.StatusEventoSst;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.shared.application.SelecaoMultipla;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Recortes da listagem de eventos de SST.
 *
 * <p>Todos os filtros de lista aceitam múltipla seleção: repita o parâmetro
 * ({@code ?tipo=ACIDENTE_TIPICO&tipo=DANO_MATERIAL}) ou separe por vírgula. Omitido,
 * um filtro não restringe nada.</p>
 *
 * <p><b>Busca por nome de pessoa é assimétrica</b>, e isso é consequência de como cada
 * nome está protegido. {@code nomeColaborador} casa <b>parcialmente</b>: o trecho é
 * resolvido em ids dentro do RH, que é o único módulo com uma coluna de busca
 * normalizada. {@code nomeTerceiro} casa de forma <b>exata</b>: o nome do terceiro só
 * existe aqui, cifrado, e é comparado pelo blind index — um hash não tem "começa com".</p>
 */
public record EventoSstFilter(

        List<TipoEventoSst> tipo,

        List<StatusEventoSst> status,

        @Parameter(description = "Entradas do catálogo CLASSIFICACAO_EVENTO")
        List<UUID> idClassificacao,

        List<UUID> idAtividade,

        @Parameter(description = "Gestor responsável registrado no evento (titular ou substituto)")
        List<UUID> idGestor,

        List<UUID> idProcesso,

        List<UUID> idBaseOperacional,

        @Parameter(description = "Centro de custo (centro de faturamento) das bases operacionais. "
                + "Equivale a selecionar todas as bases vinculadas a ele.")
        List<UUID> idCentroCusto,

        List<UUID> idEquipe,

        List<UUID> idVeiculo,

        @Parameter(description = "Técnicos de segurança responsáveis pelo evento")
        List<UUID> idTecnico,

        @Parameter(description = "Colaboradores envolvidos, em qualquer papel")
        List<UUID> idColaboradorEnvolvido,

        @Parameter(description = "Entradas do catálogo LOCAL_LESAO")
        List<UUID> idLocalLesao,

        List<RegistroEvidencia> telemetria,

        List<RegistroEvidencia> filmagem,

        @Parameter(description = "Somente eventos com (ou sem) CAT declarada")
        Boolean possuiCat,

        @Parameter(description = "Somente eventos de tipos que implicam lesão (ou somente os demais)")
        Boolean comLesao,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataOcorrenciaDe,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataOcorrenciaAte,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataComunicacaoDe,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        OffsetDateTime dataComunicacaoAte,

        @Parameter(description = "Trecho do nome de um colaborador envolvido — casa PARCIALMENTE, "
                + "resolvido em ids pelo módulo de RH")
        String nomeColaborador,

        @Parameter(description = "Nome completo de um terceiro envolvido — casa de forma EXATA, "
                + "por blind index sobre o nome cifrado")
        String nomeTerceiro,

        @Parameter(description = "Busca rápida (LIKE, case/accent-insensitive) por número de registro, "
                + "descrição, local da ocorrência, atividade e base")
        String buscaGlobal
) {

    public EventoSstFilter {
        tipo = SelecaoMultipla.normalizar(tipo);
        status = SelecaoMultipla.normalizar(status);
        idClassificacao = SelecaoMultipla.normalizar(idClassificacao);
        idAtividade = SelecaoMultipla.normalizar(idAtividade);
        idGestor = SelecaoMultipla.normalizar(idGestor);
        idProcesso = SelecaoMultipla.normalizar(idProcesso);
        idBaseOperacional = SelecaoMultipla.normalizar(idBaseOperacional);
        idCentroCusto = SelecaoMultipla.normalizar(idCentroCusto);
        idEquipe = SelecaoMultipla.normalizar(idEquipe);
        idVeiculo = SelecaoMultipla.normalizar(idVeiculo);
        idTecnico = SelecaoMultipla.normalizar(idTecnico);
        idColaboradorEnvolvido = SelecaoMultipla.normalizar(idColaboradorEnvolvido);
        idLocalLesao = SelecaoMultipla.normalizar(idLocalLesao);
        telemetria = SelecaoMultipla.normalizar(telemetria);
        filmagem = SelecaoMultipla.normalizar(filmagem);
    }
}
