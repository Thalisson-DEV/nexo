package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.ConfirmarImportacaoUsuarioRequest;
import com.nexo.app.modules.iam.application.dto.UsuarioImportacaoDTO;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportarUsuariosUseCaseTest {

    @Mock
    private UsuarioRepository repository;
    @Mock
    private PerfilAcessoRepository perfilRepository;
    @Mock
    private ColaboradorApi colaboradorApi;
    @Mock
    private ImportarUsuariosAsyncService asyncService;

    @InjectMocks
    private ImportarUsuariosUseCase useCase;

    @BeforeEach
    void configurarPerfil() {
        when(perfilRepository.findByNomeIgnoreCase(anyString()))
                .thenReturn(Optional.of(new PerfilAcesso("USUARIO")));
    }

    @Test
    void confirmarImportacaoAceitaUsuarioComESemColaborador() {
        when(colaboradorApi.buscarPorNomeCompletoResponse("MARIA DE SOUZA"))
                .thenReturn(List.of(colaborador("MARIA DE SOUZA")));

        var response = useCase.confirmarImportacao(new ConfirmarImportacaoUsuarioRequest(List.of(
                usuario(1, "JSILVA", "joao@empresa.com.br", ""),
                usuario(2, "MSOUZA", "maria@empresa.com.br", "MARIA DE SOUZA")
        )));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UsuarioImportacaoDTO>> captor = ArgumentCaptor.forClass(List.class);
        verify(asyncService).processarPersistencia(captor.capture());

        assertThat(captor.getValue()).hasSize(2);
        assertThat(response.totalSucesso()).isEqualTo(2);
        assertThat(response.totalFalha()).isZero();
        assertThat(response.registros())
                .allMatch(r -> r.status() == ImportacaoStatus.EM_PROCESSAMENTO);
    }

    @Test
    void confirmarImportacaoRejeitaNomeDeColaboradorAmbiguo() {
        when(colaboradorApi.buscarPorNomeCompletoResponse("JOAO DA SILVA"))
                .thenReturn(List.of(colaborador("JOAO DA SILVA"), colaborador("JOAO DA SILVA")));

        var response = useCase.confirmarImportacao(new ConfirmarImportacaoUsuarioRequest(List.of(
                usuario(1, "JSILVA", "joao@empresa.com.br", "JOAO DA SILVA")
        )));

        verify(asyncService, never()).processarPersistencia(org.mockito.ArgumentMatchers.anyList());
        assertThat(response.totalFalha()).isEqualTo(1);
        assertThat(response.registros().getFirst().mensagemErro()).contains("ambíguo");
    }

    private UsuarioImportacaoDTO usuario(int linha, String login, String email, String colaborador) {
        return new UsuarioImportacaoDTO(
                linha,
                login,
                email,
                "11999999999",
                "Senha@123",
                "USUARIO",
                "ADMINISTRATIVO",
                colaborador,
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO,
                null
        );
    }

    private ColaboradorResponse colaborador(String nome) {
        return new ColaboradorResponse(
                UUID.randomUUID(), null, "JOAO", "SILVA", nome, "52998224725",
                null, null, null, null, true
        );
    }
}
