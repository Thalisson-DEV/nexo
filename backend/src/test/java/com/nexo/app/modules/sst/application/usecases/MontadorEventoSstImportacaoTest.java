package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.BaseOperacionalResponse;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.sst.application.dto.EventoSstImportacaoDTO;
import com.nexo.app.modules.sst.domain.AtividadeSst;
import com.nexo.app.modules.sst.domain.CatalogoSst;
import com.nexo.app.modules.sst.domain.PapelEnvolvido;
import com.nexo.app.modules.sst.domain.RegistroEvidencia;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import com.nexo.app.modules.sst.domain.TipoEnvolvido;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.AtividadeSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MontadorEventoSstImportacaoTest {

    @Mock private AtividadeSstRepository atividadeRepository;
    @Mock private CatalogoSstRepository catalogoRepository;
    @Mock private ColaboradorApi colaboradorApi;
    @Mock private BaseOperacionalApi baseApi;
    @Mock private EquipeApi equipeApi;
    @Mock private VeiculoApi veiculoApi;
    @Mock private ProcessoNegocioApi processoApi;
    @Mock private EventoSstUseCases eventoUseCases;

    private MontadorEventoSstImportacao montador;

    @BeforeEach
    void setUp() {
        montador = new MontadorEventoSstImportacao(
                atividadeRepository, catalogoRepository, colaboradorApi, baseApi,
                equipeApi, veiculoApi, processoApi, eventoUseCases,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    void deveResolverRotulosNomesEEnvolvidosParaORequestDoCadastroManual() {
        UUID idAtividade = UUID.randomUUID();
        UUID idBase = UUID.randomUUID();
        UUID idClassificacao = UUID.randomUUID();
        UUID idLocal = UUID.randomUUID();
        UUID idNatureza = UUID.randomUUID();
        UUID idAgente = UUID.randomUUID();
        UUID idTecnico = UUID.randomUUID();
        UUID idAcidentado = UUID.randomUUID();

        AtividadeSst atividade = new AtividadeSst("Abertura de Cava", null, UUID.randomUUID(), null);
        atribuirId(atividade, idAtividade);
        when(atividadeRepository.findByNomeNormalizadoAndAtivoTrue("abertura de cava"))
                .thenReturn(Optional.of(atividade));
        catalogo("fac", TipoCatalogoSst.CLASSIFICACAO_EVENTO, "FAC", idClassificacao);
        catalogo("mao", TipoCatalogoSst.LOCAL_LESAO, "Mão", idLocal);
        catalogo("fratura", TipoCatalogoSst.NATUREZA_LESAO, "Fratura", idNatureza);
        catalogo("chao", TipoCatalogoSst.AGENTE_CAUSADOR, "Chão", idAgente);

        when(baseApi.buscarIdPorNome("Base Sul")).thenReturn(Optional.of(idBase));
        when(baseApi.buscarPorIdResponse(idBase))
                .thenReturn(Optional.of(new BaseOperacionalResponse(
                        idBase, "Base Sul", null, null, null, null, null, null, true)));
        when(colaboradorApi.buscarPorNomeCompletoResponse("Técnico Um"))
                .thenReturn(List.of(colaborador(idTecnico, "Técnico Um")));
        when(colaboradorApi.buscarPorNomeCompletoResponse("Colaborador Um"))
                .thenReturn(List.of(colaborador(idAcidentado, "Colaborador Um")));

        EventoSstImportacaoDTO dto = new EventoSstImportacaoDTO(
                1, "Acidente típico", "01/08/2026 08:00", "01/08/2026 09:00",
                "Abertura de Cava", "", "Base Sul", "", "", "FAC", "",
                "Não aplicável", "Sim", "Rua", "-23,5", "-46,6", "Queda durante a atividade",
                "Mão", "Fratura", "Chão", "2", "Não", "", "", "",
                "Técnico Um", "COLABORADOR~ACIDENTADO~Colaborador Um",
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);

        var request = montador.montarEValidar(dto);

        assertThat(request.tipo()).isEqualTo(TipoEventoSst.ACIDENTE_TIPICO);
        assertThat(request.idAtividade()).isEqualTo(idAtividade);
        assertThat(request.idBaseOperacional()).isEqualTo(idBase);
        assertThat(request.idClassificacao()).isEqualTo(idClassificacao);
        assertThat(request.idLocalLesao()).isEqualTo(idLocal);
        assertThat(request.idNaturezaLesao()).isEqualTo(idNatureza);
        assertThat(request.idAgenteCausador()).isEqualTo(idAgente);
        assertThat(request.telemetria()).isEqualTo(RegistroEvidencia.NAO_APLICAVEL);
        assertThat(request.filmagem()).isEqualTo(RegistroEvidencia.SIM);
        assertThat(request.idsTecnicos()).containsExactly(idTecnico);
        assertThat(request.envolvidos()).singleElement().satisfies(envolvido -> {
            assertThat(envolvido.tipo()).isEqualTo(TipoEnvolvido.COLABORADOR);
            assertThat(envolvido.papel()).isEqualTo(PapelEnvolvido.ACIDENTADO);
            assertThat(envolvido.idColaborador()).isEqualTo(idAcidentado);
        });
        verify(eventoUseCases).validarParaRegistro(request);
    }

    @Test
    void deveResolverColaboradorPeloNomeCompletoNormalizadoQuandoOHashExatoNaoCasar() {
        UUID idAtividade = UUID.randomUUID();
        UUID idBase = UUID.randomUUID();
        UUID idClassificacao = UUID.randomUUID();
        UUID idColaborador = UUID.randomUUID();

        AtividadeSst atividade = new AtividadeSst("Trajeto", null, UUID.randomUUID(), null);
        atribuirId(atividade, idAtividade);
        when(atividadeRepository.findByNomeNormalizadoAndAtivoTrue("trajeto"))
                .thenReturn(Optional.of(atividade));
        catalogo("va1", TipoCatalogoSst.CLASSIFICACAO_EVENTO, "VA1", idClassificacao);

        when(baseApi.buscarIdPorNome("Juazeiro")).thenReturn(Optional.of(idBase));
        when(baseApi.buscarPorIdResponse(idBase))
                .thenReturn(Optional.of(new BaseOperacionalResponse(
                        idBase, "Juazeiro", "BA", null, null, null, null, null, true)));
        when(colaboradorApi.buscarPorNomeCompletoResponse("jose ilton gomes da silva"))
                .thenReturn(List.of());
        when(colaboradorApi.buscarIdsPorTrechoDoNome("jose ilton gomes da silva"))
                .thenReturn(List.of(idColaborador));
        when(colaboradorApi.buscarPorIdResponse(idColaborador))
                .thenReturn(Optional.of(colaborador(idColaborador, "JOSÉ ILTON GOMES DA SILVA")));

        EventoSstImportacaoDTO dto = new EventoSstImportacaoDTO(
                1, "Dano material", "01/08/2026 08:00", "01/08/2026 09:00",
                "Trajeto", "", "Juazeiro", "", "", "VA1", "",
                "Não aplicável", "Não aplicável", "", "", "", "Colisão sem lesão",
                "", "", "", "", "Não", "", "", "",
                "jose ilton gomes da silva", "COLABORADOR~ENVOLVIDO~jose ilton gomes da silva",
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);

        var request = montador.montarEValidar(dto);

        assertThat(request.envolvidos()).singleElement()
                .extracting(envolvido -> envolvido.idColaborador())
                .isEqualTo(idColaborador);
        verify(eventoUseCases).validarParaRegistro(request);
    }

    private void catalogo(String normalizado, TipoCatalogoSst tipo, String nome, UUID id) {
        CatalogoSst item = new CatalogoSst(tipo, nome, null, null, 0);
        atribuirId(item, id);
        when(catalogoRepository.findByTipoAndNomeNormalizadoAndAtivoTrue(tipo, normalizado))
                .thenReturn(Optional.of(item));
    }

    private ColaboradorResponse colaborador(UUID id, String nome) {
        return new ColaboradorResponse(id, null, nome, "", nome, "", null, null, null, null, true);
    }

    private void atribuirId(Object entidade, UUID id) {
        try {
            var campo = entidade.getClass().getSuperclass().getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entidade, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
