package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.domain.SolicitacaoMaterial;
import com.nexo.app.modules.estoque.application.services.GravadorEventoSolicitacaoService;
import com.nexo.app.modules.estoque.infrastructure.persistence.SolicitacaoMaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolicitacaoAgendamentoServiceCharacterizationTest {

    @Mock
    private SolicitacaoMaterialRepository repository;

    @Mock
    private GravadorEventoSolicitacaoService gravadorEvento;

    @Mock
    private SolicitacaoMaterial solicitacaoExpirada;

    @InjectMocks
    private SolicitacaoAgendamentoService service;

    @Test
    void encerraSomenteEntregasParciaisAnterioresAoLimiteDeSetentaEDuasHoras() {
        when(repository.findAllByStatusAndEncerradaEmIsNullAndDataEntregaBefore(
                org.mockito.ArgumentMatchers.eq(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)
        )).thenReturn(List.of(solicitacaoExpirada));
        OffsetDateTime limiteInferior = OffsetDateTime.now().minusHours(72).minusSeconds(1);

        service.processarSolicitacoesParciaisExpiradas();

        OffsetDateTime limiteSuperior = OffsetDateTime.now().minusHours(72).plusSeconds(1);
        ArgumentCaptor<OffsetDateTime> limiteCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).findAllByStatusAndEncerradaEmIsNullAndDataEntregaBefore(
                org.mockito.ArgumentMatchers.eq(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL),
                limiteCaptor.capture()
        );
        assertThat(limiteCaptor.getValue()).isBetween(limiteInferior, limiteSuperior);
        verify(solicitacaoExpirada).encerrarEntregaParcialExpirada();
        verify(repository).save(solicitacaoExpirada);
    }

    @Test
    void expiraFilhoTecnicoParcialAutomaticamenteAposSetentaEDuasHoras() {
        SolicitacaoMaterial tecnico = org.mockito.Mockito.mock(SolicitacaoMaterial.class);
        when(tecnico.getContexto()).thenReturn(com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.TECNICO);
        when(repository.findAllByStatusAndEncerradaEmIsNullAndDataEntregaBefore(
                org.mockito.ArgumentMatchers.eq(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)
        )).thenReturn(List.of(tecnico));

        service.processarSolicitacoesParciaisExpiradas();

        verify(tecnico).encerrarEntregaParcialExpirada();
        verify(repository).save(tecnico);
    }

    @Test
    void encerrarEntregaParcialExpiradaEmSolicitacaoTecnicaMarcaEncerradaComMotivo() throws Exception {
        java.lang.reflect.Constructor<SolicitacaoMaterial> constructor =
                SolicitacaoMaterial.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        SolicitacaoMaterial solicitacao = constructor.newInstance();
        org.springframework.test.util.ReflectionTestUtils.setField(
                solicitacao, "contexto", com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.TECNICO);
        org.springframework.test.util.ReflectionTestUtils.setField(
                solicitacao, "status", SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);

        solicitacao.encerrarEntregaParcialExpirada();

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);
        assertThat(solicitacao.estaEncerrada()).isTrue();
        assertThat(solicitacao.isEncerrada()).isTrue();
        assertThat(solicitacao.getEncerradaEm()).isNotNull();
        assertThat(solicitacao.getMotivoEncerramento()).contains("72 horas");
    }

    @Test
    void encerrarEntregaParcialExpiradaEmSolicitacaoComercialMarcaEncerradaComMotivo() throws Exception {
        java.lang.reflect.Constructor<SolicitacaoMaterial> constructor =
                SolicitacaoMaterial.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        SolicitacaoMaterial solicitacao = constructor.newInstance();
        org.springframework.test.util.ReflectionTestUtils.setField(
                solicitacao, "contexto", com.nexo.app.modules.estoque.domain.ContextoSolicitacaoMaterial.COMERCIAL);
        org.springframework.test.util.ReflectionTestUtils.setField(
                solicitacao, "status", SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);

        solicitacao.encerrarEntregaParcialExpirada();

        assertThat(solicitacao.getStatus()).isEqualTo(SolicitacaoMaterial.StatusSolicitacao.ENTREGUE_PARCIAL);
        assertThat(solicitacao.estaEncerrada()).isTrue();
        assertThat(solicitacao.isEncerrada()).isTrue();
        assertThat(solicitacao.getEncerradaEm()).isNotNull();
        assertThat(solicitacao.getMotivoEncerramento()).contains("72 horas");
    }
}
