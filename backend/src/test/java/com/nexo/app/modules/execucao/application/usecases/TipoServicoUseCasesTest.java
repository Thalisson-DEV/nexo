package com.nexo.app.modules.execucao.application.usecases;

import com.nexo.app.modules.execucao.application.dto.TipoServicoRequest;
import com.nexo.app.modules.execucao.application.dto.TipoServicoResponse;
import com.nexo.app.modules.execucao.application.mappers.TipoServicoMapper;
import com.nexo.app.modules.execucao.domain.TipoServico;
import com.nexo.app.modules.execucao.infrastructure.persistence.TipoServicoRepository;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TipoServicoUseCasesTest {

    @Mock
    private TipoServicoRepository repository;
    @Mock
    private TipoServicoMapper mapper;

    @InjectMocks
    private TipoServicoUseCases useCases;

    @BeforeEach
    void configurarPadroes() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.toResponse(any())).thenAnswer(inv -> {
            TipoServico tipo = inv.getArgument(0);
            return new TipoServicoResponse(tipo.getId(), tipo.getCodigo(), tipo.getDescricao(), tipo.isAtivo(), null, null);
        });
    }

    @Test
    @DisplayName("O código pode se repetir entre tipos: só a descrição é única")
    void codigoRepetidoEhPermitido() {
        when(repository.findByDescricaoNormalizadaAndAtivoTrue("alteracao")).thenReturn(Optional.empty());

        TipoServicoResponse criado = useCases.criar(new TipoServicoRequest("CM", "Alteração"));

        assertThat(criado.codigo()).isEqualTo("CM");
        assertThat(criado.descricao()).isEqualTo("Alteração");
    }

    @Test
    @DisplayName("Rejeita descrição já usada por outro tipo ativo, sem diferenciar acento e maiúsculas")
    void rejeitaDescricaoDuplicada() {
        when(repository.findByDescricaoNormalizadaAndAtivoTrue("ligacao nova"))
                .thenReturn(Optional.of(new TipoServico("CL", "LIGAÇÃO NOVA")));

        assertThatThrownBy(() -> useCases.criar(new TipoServicoRequest("CL", "ligacao  nova")))
                .isInstanceOf(InvarianteVioladaException.class)
                .hasMessageContaining("Já existe");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Editar mantendo a própria descrição não acusa duplicidade")
    void edicaoDoProprioTipo() {
        TipoServico existente = new TipoServico("CS", "Corte");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(existente, "id", id);
        when(repository.findById(id)).thenReturn(Optional.of(existente));
        when(repository.findByDescricaoNormalizadaAndAtivoTrue("corte")).thenReturn(Optional.of(existente));

        TipoServicoResponse atualizado = useCases.atualizar(id, new TipoServicoRequest("cs", "CORTE"));

        assertThat(atualizado.codigo()).isEqualTo("CS");
        assertThat(atualizado.descricao()).isEqualTo("CORTE");
    }

    @Test
    @DisplayName("Lançamento com tipo inexistente é recusado; tipo existente (mesmo inativo) é aceito")
    void validaTipoDoLancamento() {
        UUID existente = UUID.randomUUID();
        UUID inexistente = UUID.randomUUID();
        when(repository.existsById(existente)).thenReturn(true);
        when(repository.existsById(inexistente)).thenReturn(false);

        assertThatCode(() -> useCases.validarParaLancamento(existente)).doesNotThrowAnyException();
        assertThatThrownBy(() -> useCases.validarParaLancamento(inexistente))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }
}
