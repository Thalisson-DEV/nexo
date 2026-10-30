package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.UsuarioImportacaoDTO;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.domain.TipoUsuario;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.shared.domain.events.UsuarioCriadoParaColaboradorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@CacheConfig(cacheNames = "usuarios")
public class ImportarUsuariosAsyncService {

    private final UsuarioRepository repository;
    private final PerfilAcessoRepository perfilRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final ColaboradorApi colaboradorApi;

    @Async
    @Transactional
    @CacheEvict(allEntries = true)
    public void processarPersistencia(List<UsuarioImportacaoDTO> usuarios) {
        log.info("Iniciando persistência assíncrona de {} usuários", usuarios.size());

        for (UsuarioImportacaoDTO dto : usuarios) {
            try {
                PerfilAcesso perfil = perfilRepository.findByNomeIgnoreCase(dto.nomePerfil())
                        .orElseThrow();
                UUID idColaborador = resolverIdColaborador(dto.nomeColaborador());

                Usuario usuario = new Usuario(
                        dto.login(),
                        dto.email(),
                        dto.telefone(),
                        passwordEncoder.encode(dto.senha()),
                        perfil,
                        idColaborador,
                        TipoUsuario.valueOf(dto.tipo().trim().toUpperCase(Locale.ROOT))
                );

                Usuario salvo = repository.save(usuario);
                if (idColaborador != null) {
                    eventPublisher.publishEvent(new UsuarioCriadoParaColaboradorEvent(
                            idColaborador,
                            salvo.getId()
                    ));
                }
            } catch (Exception e) {
                log.error("Erro ao persistir usuário da linha {}: {}", dto.linha(), e.getMessage());
            }
        }

        log.info("Finalizada persistência assíncrona de usuários");
    }

    private UUID resolverIdColaborador(String nomeColaborador) {
        if (nomeColaborador == null || nomeColaborador.isBlank()) {
            return null;
        }

        List<ColaboradorResponse> encontrados = colaboradorApi
                .buscarPorNomeCompletoResponse(nomeColaborador.trim());
        if (encontrados.size() != 1) {
            throw new IllegalArgumentException("Não foi possível resolver unicamente o colaborador");
        }
        return encontrados.getFirst().id();
    }
}
