package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.iam.infrastructure.security.PerfilPermissaoCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AutenticacaoService implements UserDetailsService {

    private final UsuarioRepository repository;
    private final PerfilPermissaoCacheService perfilPermissaoCacheService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var usuario = repository.findByLoginIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado: " + username));
                
        var autoridadesStrings = perfilPermissaoCacheService.buscarAutoridadesPorPerfil(usuario.getPerfil().getId());
        var authorities = perfilPermissaoCacheService.converterParaAuthorities(autoridadesStrings);
        usuario.setAuthorities(authorities);
        
        return usuario;
    }
}
