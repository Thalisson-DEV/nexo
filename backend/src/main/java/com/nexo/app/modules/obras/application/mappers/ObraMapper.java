package com.nexo.app.modules.obras.application.mappers;

import com.nexo.app.modules.obras.application.dto.CarteiraObraResponse;
import com.nexo.app.modules.obras.application.dto.AnexoObraResponse;
import com.nexo.app.modules.obras.application.dto.ObraResponse;
import com.nexo.app.modules.obras.application.dto.ObraResumoResponse;
import com.nexo.app.modules.obras.application.dto.ProjetoRelacionadoResponse;
import com.nexo.app.modules.obras.application.dto.SubprojetoResponse;
import com.nexo.app.modules.obras.domain.AnexoObra;
import com.nexo.app.modules.obras.domain.Obra;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ObraMapper {

    @Mapping(target = "statusId", source = "obra.statusObra.id")
    @Mapping(target = "statusNome", source = "obra.statusObra.nome")
    @Mapping(target = "statusConcluida", source = "obra.statusObra.concluida")
    @Mapping(target = "supervisorId", source = "obra.idSupervisor")
    @Mapping(target = "supervisorNome", source = "supervisorNome")
    @Mapping(target = "coordenadorId", source = "obra.idCoordenador")
    @Mapping(target = "coordenadorNome", source = "coordenadorNome")
    @Mapping(target = "agenteResponsavelId", source = "obra.agenteResponsavel.id")
    @Mapping(target = "agenteResponsavelNome", source = "obra.agenteResponsavel.nome")
    @Mapping(target = "agenteResponsavelEmail", source = "obra.agenteResponsavel.email")
    @Mapping(target = "obraPaiId", source = "obra.obraPai.id")
    @Mapping(target = "obraPaiNumero", source = "obra.obraPai.numero")
    @Mapping(target = "obraPaiTitulo", source = "obra.obraPai.titulo")
    @Mapping(target = "processoId", source = "obra.idProcesso")
    @Mapping(target = "processoNome", source = "processoNome")
    @Mapping(target = "baseOperacionalId", source = "obra.idBaseOperacional")
    @Mapping(target = "baseOperacionalNome", source = "baseOperacionalNome")
    @Mapping(target = "municipioId", source = "obra.idMunicipio")
    @Mapping(target = "municipioNome", source = "municipioNome")
    @Mapping(target = "centroCustoId", source = "obra.idCentroCusto")
    @Mapping(target = "centroCustoNome", source = "centroCustoNome")
    @Mapping(target = "subprojetos", expression = "java(toLegacySubprojetos(projetosRelacionados))")
    @Mapping(target = "anexos", source = "obra.anexos")
    @Mapping(target = "projetosRelacionados", source = "projetosRelacionados")
    @Mapping(target = "carteira", expression = "java(toCarteira(obra))")
    ObraResponse toResponse(Obra obra, String processoNome, String baseOperacionalNome,
                            String municipioNome, String centroCustoNome,
                            String supervisorNome, String coordenadorNome,
                            List<ProjetoRelacionadoResponse> projetosRelacionados);

    @Mapping(target = "statusId", source = "obra.statusObra.id")
    @Mapping(target = "statusNome", source = "obra.statusObra.nome")
    @Mapping(target = "statusConcluida", source = "obra.statusObra.concluida")
    @Mapping(target = "supervisorId", source = "obra.idSupervisor")
    @Mapping(target = "supervisorNome", source = "supervisorNome")
    @Mapping(target = "coordenadorId", source = "obra.idCoordenador")
    @Mapping(target = "coordenadorNome", source = "coordenadorNome")
    @Mapping(target = "agenteResponsavelId", source = "obra.agenteResponsavel.id")
    @Mapping(target = "agenteResponsavelNome", source = "obra.agenteResponsavel.nome")
    @Mapping(target = "obraPaiId", source = "obra.obraPai.id")
    @Mapping(target = "obraPaiNumero", source = "obra.obraPai.numero")
    @Mapping(target = "processoId", source = "obra.idProcesso")
    @Mapping(target = "processoNome", source = "processoNome")
    @Mapping(target = "baseOperacionalId", source = "obra.idBaseOperacional")
    @Mapping(target = "baseOperacionalNome", source = "baseOperacionalNome")
    @Mapping(target = "municipioId", source = "obra.idMunicipio")
    @Mapping(target = "municipioNome", source = "municipioNome")
    @Mapping(target = "centroCustoId", source = "obra.idCentroCusto")
    @Mapping(target = "centroCustoNome", source = "centroCustoNome")
    @Mapping(target = "totalEquipes", expression = "java(obra.getEquipes() != null ? obra.getEquipes().size() : 0)")
    @Mapping(target = "totalProjetosRelacionados", source = "totalProjetosRelacionados")
    @Mapping(target = "totalSubprojetos", source = "totalProjetosRelacionados")
    @Mapping(target = "totalAnexos", expression = "java(obra.getAnexos() != null ? obra.getAnexos().size() : 0)")
    @Mapping(target = "carteira", expression = "java(toCarteira(obra))")
    ObraResumoResponse toResumo(Obra obra, String processoNome, String baseOperacionalNome,
                                String municipioNome, String centroCustoNome,
                                String supervisorNome, String coordenadorNome,
                                int totalProjetosRelacionados);

    default CarteiraObraResponse toCarteira(Obra obra) {
        if (obra == null) {
            return null;
        }
        return new CarteiraObraResponse(
                obra.getIdContrato(),
                obra.getClasseTensao(),
                obra.getClasseTensao() != null ? obra.getClasseTensao().getRotulo() : null,
                obra.getClasseTensaoInformadaPor(),
                obra.getClasseTensaoInformadaEm(),
                obra.getPrazoRegulado(),
                obra.getPrioridade(),
                obra.getLotePrevisto(),
                obra.getSituacaoCarteira(),
                obra.getValorMaterialPrevisto(),
                obra.getValorServicoPrevisto(),
                obra.getObservacaoStatus(),
                obra.deslocamentoDesatualizado() ? null : obra.getDistanciaBaseKm(),
                obra.deslocamentoDesatualizado() ? null : obra.getTempoDeslocamentoMin(),
                obra.deslocamentoDesatualizado() ? null : obra.getDeslocamentoEstimado(),
                obra.deslocamentoDesatualizado() ? null : obra.getDeslocamentoCalculadoEm()
        );
    }

    @Mapping(target = "statusId", source = "statusObra.id")
    @Mapping(target = "statusNome", source = "statusObra.nome")
    @Mapping(target = "tipoVinculo", source = "tipoVinculoPai")
    SubprojetoResponse toSubprojetoResponse(Obra obra);

    List<SubprojetoResponse> toSubprojetoResponseList(List<Obra> obras);

    @Deprecated(forRemoval = true)
    default List<SubprojetoResponse> toLegacySubprojetos(List<ProjetoRelacionadoResponse> projetos) {
        if (projetos == null) {
            return List.of();
        }
        return projetos.stream()
                .map(projeto -> new SubprojetoResponse(
                        projeto.idProjeto(),
                        projeto.numero(),
                        projeto.titulo(),
                        projeto.notaObra(),
                        projeto.valorProjeto(),
                        projeto.dataInicio(),
                        projeto.dataPrevisaoFim(),
                        projeto.dataFimEfetiva(),
                        com.nexo.app.modules.obras.domain.TipoVinculoSubprojeto.valueOf(
                                projeto.tipoVinculo().name()),
                        projeto.statusId(),
                        projeto.statusNome()
                ))
                .toList();
    }

    @Mapping(target = "idObra", source = "anexo.obra.id")
    @Mapping(target = "idTipoAnexo", source = "anexo.tipoAnexo.id")
    @Mapping(target = "tipoAnexoNome", source = "anexo.tipoAnexo.nome")
    AnexoObraResponse toAnexoResponse(AnexoObra anexo);

    List<AnexoObraResponse> toAnexoResponseList(List<AnexoObra> anexos);
}
