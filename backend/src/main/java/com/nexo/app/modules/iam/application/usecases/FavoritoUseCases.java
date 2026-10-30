package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.application.dto.FavoritoRequest;
import com.nexo.app.modules.iam.application.dto.FavoritoResponse;
import com.nexo.app.modules.iam.application.mappers.FavoritoMapper;
import com.nexo.app.modules.iam.domain.Favorito;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.FavoritoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "favoritos")
public class FavoritoUseCases {

    private final FavoritoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final FavoritoMapper mapper;

    @Transactional(readOnly = true)
    @Cacheable(key = "#usuarioId")
    public List<FavoritoResponse> listarFavoritos(UUID usuarioId) {
        List<Favorito> favoritos = repository.findByUsuarioIdAndAtivoTrueOrderByTituloAsc(usuarioId);
        return mapper.toResponseList(favoritos);
    }

    @Transactional
    @CacheEvict(key = "#usuarioId")
    public FavoritoResponse adicionarFavorito(UUID usuarioId, FavoritoRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        Favorito favorito = new Favorito(
                request.titulo(),
                request.rota(),
                request.icone(),
                usuario
        );

        return mapper.toResponse(repository.save(favorito));
    }

    @Transactional
    @CacheEvict(key = "#usuarioId")
    public void removerFavorito(UUID usuarioId, UUID favoritoId) {
        Favorito favorito = repository.findByIdAndUsuarioIdAndAtivoTrue(favoritoId, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Favorito não encontrado ou não pertence ao usuário"));

        favorito.desativar(); // Soft delete exigido pelo Nexo
        repository.save(favorito);
    }
}
