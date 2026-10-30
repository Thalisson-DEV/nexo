package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.UsuarioImportacaoDTO;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import com.nexo.app.shared.domain.events.UsuarioCriadoParaColaboradorEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImportarUsuariosAsyncServiceTest {

    @Mock
    private UsuarioRepository repository;
    @Mock
    private PerfilAcessoRepository perfilRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private ColaboradorApi colaboradorApi;

    @InjectMocks
    private ImportarUsuariosAsyncService service;

    @Test
    void processarPersistenciaPublicaEventoParaVincularColaborador() {
        UUID colaboradorId = UUID.randomUUID();
        UUID usuarioId = UUID.randomUUID();
        PerfilAcesso perfil = new PerfilAcesso("USUARIO");
        Usuario salvo = mock(Usuario.class);

        when(perfilRepository.findByNomeIgnoreCase("USUARIO")).thenReturn(Optional.of(perfil));
        when(colaboradorApi.buscarPorNomeCompletoResponse("MARIA DE SOUZA"))
                .thenReturn(List.of(new ColaboradorResponse(
                        colaboradorId, null, "MARIA", "SOUZA", "MARIA DE SOUZA", "52998224725",
                        null, null, null, null, true)));
        when(passwordEncoder.encode("Senha@123")).thenReturn("senha-hash");
        when(repository.save(any(Usuario.class))).thenReturn(salvo);
        when(salvo.getId()).thenReturn(usuarioId);

        service.processarPersistencia(List.of(new UsuarioImportacaoDTO(
                1, "MSOUZA", "maria@empresa.com.br", "11999999999", "Senha@123",
                "USUARIO", "CAMPO", "MARIA DE SOUZA",
                ImportacaoStatus.EM_PROCESSAMENTO, null)));

        ArgumentCaptor<UsuarioCriadoParaColaboradorEvent> eventCaptor =
                ArgumentCaptor.forClass(UsuarioCriadoParaColaboradorEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().colaboradorId()).isEqualTo(colaboradorId);
        assertThat(eventCaptor.getValue().usuarioId()).isEqualTo(usuarioId);
    }
}
