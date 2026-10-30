package com.nexo.app.modules.iam.infrastructure.security;

import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.nexo.app.shared.infrastructure.config.CacheNames.PERMISSOES_PERFIL;

@Service
@RequiredArgsConstructor
public class PerfilPermissaoCacheService {

    private final PerfilAcessoRepository perfilAcessoRepository;

    /**
     * Busca as autoridades (ROLE + Permissões) de um perfil com cache.
     * Este método é transacional para permitir o carregamento das permissões (Lazy).
     */
    @Transactional(readOnly = true)
    @Cacheable(value = PERMISSOES_PERFIL, key = "#idPerfil")
    public List<String> buscarAutoridadesPorPerfil(UUID idPerfil) {
        PerfilAcesso perfil = perfilAcessoRepository.findById(idPerfil)
                .orElseThrow(() -> new IllegalArgumentException("Perfil não encontrado: " + idPerfil));

        List<String> authorities = new ArrayList<>();
        
        // Adiciona a ROLE do perfil
        authorities.add("ROLE_" + perfil.getNome());

        // Adiciona as permissões granulares
        perfil.getPermissoes().forEach(p -> authorities.add(p.getNome()));

        return authorities;
    }

    /**
     * Converte a lista de strings (do cache) em GrantedAuthority.
     */
    public Collection<? extends GrantedAuthority> converterParaAuthorities(List<String> authoritiesStrings) {
        return authoritiesStrings.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
