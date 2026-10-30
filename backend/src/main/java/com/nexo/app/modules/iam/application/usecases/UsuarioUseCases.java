package com.nexo.app.modules.iam.application.usecases;

import com.nexo.app.modules.iam.ContextoAudienciaUsuario;
import com.nexo.app.modules.iam.ResumoUsuario;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.iam.application.dto.*;
import com.nexo.app.modules.iam.application.mappers.UsuarioMapper;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.domain.TipoUsuario;
import com.nexo.app.modules.iam.domain.SessaoAutenticacao;
import com.nexo.app.modules.iam.domain.Usuario;
import com.nexo.app.modules.iam.infrastructure.persistence.ColaboradoresResolvidos;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioSpecification;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ResumoColaborador;
import com.nexo.app.shared.application.dto.PageResponse;
import com.nexo.app.shared.domain.events.UsuarioCriadoParaColaboradorEvent;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@CacheConfig(cacheNames = "usuarios")
public class UsuarioUseCases implements UsuarioApi {

    private final UsuarioRepository repository;
    private final PerfilAcessoRepository perfilRepository;
    private final UsuarioMapper mapper;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final ColaboradorApi colaboradorApi;
    private final SessaoAutenticacaoUseCases sessoes;
    private final com.nexo.app.modules.iam.infrastructure.security.PerfilPermissaoCacheService permissoesPerfil;
    private final com.nexo.app.modules.iam.infrastructure.persistence.PermissaoRepository permissaoRepository;

    /**
     * Cria o usuário e, quando ele nasce atrelado a um colaborador, avisa o RH para que
     * o vínculo inverso ({@code colaboradores.id_usuario}) também seja gravado. Sem esse
     * aviso o colaborador fica sem usuário do ponto de vista do RH, e todo indicador que
     * parte do colaborador para chegar ao que ele lançou (metas de inspeção, por exemplo)
     * passa a enxergá-lo como "sem acesso".
     */
    @Transactional
    @CacheEvict(allEntries = true)
    public UsuarioResponse criar(UsuarioRequest request) {
        validarUnicidade(request.login(), request.email());

        PerfilAcesso perfil = perfilRepository.findById(request.idPerfil())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));

        // O PasswordEncoder faz o hash BCrypt
        String senhaHash = passwordEncoder.encode(request.senha());

        Usuario usuario = new Usuario(
                request.login(),
                request.email(),
                request.telefone(),
                senhaHash,
                perfil,
                request.idColaborador(),
                request.tipo()
        );

        Usuario salvo = repository.save(usuario);

        if (salvo.getIdColaborador() != null) {
            eventPublisher.publishEvent(
                    new UsuarioCriadoParaColaboradorEvent(salvo.getIdColaborador(), salvo.getId()));
        }

        return comColaborador(mapper.toResponse(salvo));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public UsuarioResponse atualizar(UUID id, UsuarioUpdateRequest request) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        validarUnicidade(request.login(), request.email(), id);

        usuario.atualizarContatos(request.email(), request.telefone());

        if (request.login() != null && !request.login().isBlank()) {
            usuario.trocarLogin(request.login());
        }

        if (request.senha() != null && !request.senha().isBlank()) {
            usuario.trocarSenha(passwordEncoder.encode(request.senha()));
            // Senha redefinida pelo administrador: nenhum aparelho segue com a credencial antiga.
            sessoes.revogarTodas(id, null, SessaoAutenticacao.MOTIVO_SENHA_ALTERADA);
        }

        if (request.tipo() != null) {
            usuario.alterarTipo(request.tipo());
        }

        if (request.idPerfil() != null) {
            PerfilAcesso perfil = perfilRepository.findById(request.idPerfil())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Perfil de acesso não encontrado"));
            usuario.alterarPerfil(perfil);
        }

        return comColaborador(mapper.toResponse(repository.save(usuario)));
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void ativar(UUID id) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        usuario.ativar();
        repository.save(usuario);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void atualizarPreferencias(UUID id, PreferenciasRequest request) {
        Usuario usuario = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));

        usuario.atualizarPreferencias(request.tema(), request.corPrimaria(), request.animacoesAtivadas());
        repository.save(usuario);
    }

    @Transactional
    @CacheEvict(allEntries = true)
    public void desativar(UUID id) {
        repository.findById(id)
                .ifPresentOrElse(
                        usuario -> {
                            usuario.desativar();
                            repository.save(usuario);
                            sessoes.revogarTodas(id, null, SessaoAutenticacao.MOTIVO_USUARIO_INATIVO);
                        },
                        () -> { throw new RecursoNaoEncontradoException("Usuário não encontrado"); }
                );
    }

    /**
     * Lista os acessos já apresentando <b>quem</b> é o dono de cada um.
     *
     * <p>Login e perfil descrevem o acesso, não a pessoa: para quem administra o sistema
     * sem ter criado os logins, uma coluna de {@code MSOUZA1} é indecifrável. Por isso a
     * página resolve o vínculo com o RH — em uma consulta só, para a página inteira — e
     * devolve nome, função e base junto de cada registro.</p>
     */
    @Transactional(readOnly = true)
    @Cacheable(key = "{#filter.toString(), #pageable.pageNumber, #pageable.pageSize, #pageable.sort.toString()}")
    public PageResponse<UsuarioResponse> listar(UsuarioFilter filter, Pageable pageable) {
        Page<UsuarioResponse> pagina = repository
                .findAll(UsuarioSpecification.doFiltro(filter, resolverColaboradores(filter)), pageable)
                .map(mapper::toResponse);

        return PageResponse.fromPage(comColaboradores(pagina));
    }

    @Transactional(readOnly = true)
    @Cacheable(key = "#id")
    public UsuarioResponse buscarPorId(UUID id) {
        return repository.findById(id)
                .map(mapper::toResponse)
                .map(this::comColaborador)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado"));
    }

    /**
     * Traduz os filtros que falam de gente ({@code nomeColaborador} e a busca rápida) em
     * ids de colaborador, perguntando ao RH — o nome está cifrado lá e não há como
     * compará-lo daqui.
     */
    private ColaboradoresResolvidos resolverColaboradores(UsuarioFilter filter) {
        boolean filtraPorNome = filter.nomeColaborador() != null && !filter.nomeColaborador().isBlank();
        boolean temBuscaGlobal = filter.buscaGlobal() != null && !filter.buscaGlobal().isBlank();

        if (!filtraPorNome && !temBuscaGlobal) {
            return ColaboradoresResolvidos.nenhum();
        }

        return new ColaboradoresResolvidos(
                filtraPorNome ? colaboradorApi.buscarIdsPorTrechoDoNome(filter.nomeColaborador()) : null,
                temBuscaGlobal ? colaboradorApi.buscarIdsPorTrechoDoNome(filter.buscaGlobal()) : List.of());
    }

    private Page<UsuarioResponse> comColaboradores(Page<UsuarioResponse> pagina) {
        Map<UUID, ResumoColaborador> resumos = buscarResumos(pagina.getContent().stream()
                .map(UsuarioResponse::idColaborador)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        return resumos.isEmpty() ? pagina : pagina.map(usuario -> vincular(usuario, resumos));
    }

    private UsuarioResponse comColaborador(UsuarioResponse usuario) {
        if (usuario.idColaborador() == null) {
            return usuario;
        }
        return vincular(usuario, buscarResumos(Set.of(usuario.idColaborador())));
    }

    private Map<UUID, ResumoColaborador> buscarResumos(Set<UUID> ids) {
        return ids.isEmpty() ? Map.of() : colaboradorApi.buscarResumosPorIds(ids);
    }

    /**
     * Vínculo apontando para colaborador inexistente devolve o usuário como está: o
     * acesso continua administrável mesmo que o cadastro do RH tenha sumido.
     */
    private UsuarioResponse vincular(UsuarioResponse usuario, Map<UUID, ResumoColaborador> resumos) {
        ResumoColaborador resumo = usuario.idColaborador() == null ? null : resumos.get(usuario.idColaborador());
        if (resumo == null) {
            return usuario;
        }
        return usuario.comColaborador(new ColaboradorVinculadoResponse(
                resumo.id(),
                resumo.nomeCompleto(),
                resumo.funcao(),
                resumo.baseOperacional(),
                resumo.ativo()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> buscarNomePorId(UUID id) {
        return repository.findById(id).map(Usuario::getLogin);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsPorTrechoDoLogin(String trecho) {
        String termo = SpecificationUtils.normalizarTermo(trecho);
        return termo == null || termo.isBlank() ? List.of() : repository.buscarIdsPorTrechoDoLogin(termo);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUsuarioCampo(UUID id) {
        return repository.findById(id)
                .map(u -> u.getTipo() == TipoUsuario.CAMPO)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContextoAudienciaUsuario> buscarContextoAudiencia(UUID id) {
        return repository.findById(id)
                .filter(Usuario::isAtivo)
                .map(usuario -> new ContextoAudienciaUsuario(
                        usuario.getId(),
                        usuario.getPerfil().getId(),
                        usuario.getTipo().name()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsAtivosPorPerfil(UUID idPerfil) {
        return idPerfil == null ? List.of() : repository.buscarIdsAtivosPorPerfil(idPerfil);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsAtivosPorTipo(String tipoUsuario) {
        return converterTipo(tipoUsuario)
                .map(repository::buscarIdsAtivosPorTipo)
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsAtivos() {
        return repository.buscarIdsAtivos();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> buscarIdsAtivosComPermissao(String permissao) {
        if (permissao == null || permissao.isBlank()) {
            return List.of();
        }
        return repository.buscarIdsAtivosComPermissao(permissao.trim());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> buscarLoginsPorIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, Usuario::getLogin));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> buscarEmailsPorIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .filter(Usuario::isAtivo)
                .filter(usuario -> usuario.getEmail() != null && !usuario.getEmail().isBlank())
                .collect(Collectors.toMap(Usuario::getId, Usuario::getEmail));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, UUID> buscarIdsAtivosPorLogins(Collection<String> logins) {
        if (logins == null || logins.isEmpty()) {
            return Map.of();
        }
        // O cadastro guarda o login em maiúsculas; normalizar aqui é o que permite a
        // quem chama comparar contra o texto original sem se importar com a caixa.
        Set<String> normalizados = logins.stream()
                .filter(login -> login != null && !login.isBlank())
                .map(login -> login.trim().toUpperCase())
                .collect(Collectors.toSet());
        if (normalizados.isEmpty()) {
            return Map.of();
        }
        return repository.buscarAtivosPorLogins(normalizados).stream()
                .collect(Collectors.toMap(
                        resumo -> resumo.login().toUpperCase(), ResumoUsuario::id, (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResumoUsuario> buscarAtivosPorTrechoDoLogin(String trecho, int limite) {
        if (trecho == null || trecho.isBlank() || limite < 1) {
            return List.of();
        }
        return repository.buscarAtivosPorTrechoDoLogin(trecho.trim(), PageRequest.of(0, limite));
    }

    /**
     * Um tipo que não corresponde mais a nenhuma constante veio de configuração
     * antiga; expandi-lo para lista vazia é preferível a derrubar a publicação
     * inteira de um comunicado por causa de um segmento obsoleto.
     */
    private Optional<TipoUsuario> converterTipo(String tipoUsuario) {
        if (tipoUsuario == null || tipoUsuario.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(TipoUsuario.valueOf(tipoUsuario.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private void validarUnicidade(String login, String email, UUID id) {
        if (login != null && repository.existsByLoginIgnoreCaseAndIdNot(login, id)) {
            throw new InvarianteVioladaException("Já existe um usuário com este login");
        }
        if (email != null && repository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new InvarianteVioladaException("Já existe um usuário com este e-mail");
        }
    }

    private void validarUnicidade(String login, String email) {
        if (login != null && repository.existsByLoginIgnoreCase(login)) {
            throw new InvarianteVioladaException("Já existe um usuário com este login");
        }
        if (email != null && repository.existsByEmailIgnoreCase(email)) {
            throw new InvarianteVioladaException("Já existe um usuário com este e-mail");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, com.nexo.app.modules.iam.ContatoUsuario> buscarContatos(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Usuario::getId, usuario -> new com.nexo.app.modules.iam.ContatoUsuario(
                        usuario.getId(), usuario.getLogin(),
                        usuario.getEmail() == null || usuario.getEmail().isBlank() ? null : usuario.getEmail().trim(),
                        usuario.getIdColaborador(), usuario.isAtivo())));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> buscarIdAtivoPorEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return repository.findByEmailIgnoreCase(email.trim()).filter(Usuario::isAtivo).map(Usuario::getId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> buscarPermissoes(UUID id) {
        if (id == null) {
            return Set.of();
        }
        return repository.findById(id)
                .filter(Usuario::isAtivo)
                .map(usuario -> (Set<String>) new java.util.HashSet<>(
                        permissoesPerfil.buscarAutoridadesPorPerfil(usuario.getPerfil().getId())))
                .orElseGet(Set::of);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.nexo.app.shared.application.dto.LookupOptionDto> listarPerfisAtivos() {
        return perfilRepository.findAll().stream()
                .filter(PerfilAcesso::isAtivo)
                .sorted(java.util.Comparator.comparing(PerfilAcesso::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(perfil -> new com.nexo.app.shared.application.dto.LookupOptionDto(perfil.getId().toString(), perfil.getNome()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listarPermissoes() {
        return permissaoRepository.findAll().stream()
                .filter(com.nexo.app.modules.iam.domain.Permissao::isAtivo)
                .map(com.nexo.app.modules.iam.domain.Permissao::getNome)
                .sorted()
                .toList();
    }
}
