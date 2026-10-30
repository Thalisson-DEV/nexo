package com.nexo.app.modules.iam.domain;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.infrastructure.persistence.CryptoConverterImpl;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Representa o acesso ao sistema.
 * Implementa UserDetails para integração nativa com Spring Security.
 */
@Entity
@Table(name = "usuarios")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends BaseEntity implements UserDetails {

    @NotBlank(message = "O login é obrigatório")
    @Size(min = 3, max = 9, message = "O Login deve conter entre 3 e 9 caracteres")
    @Column(name = "login", nullable = false, unique = true)
    private String login;

    @Email(message = "E-mail inválido")
    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "telefone")
    private String telefone;

    @NotBlank(message = "A senha é obrigatória")
    @Convert(converter = CryptoConverterImpl.class) // 🔒 Proteção extra em repouso
    @Column(name = "senha_hash", nullable = false, length = 500)
    private String senha;

    @NotNull(message = "O perfil de acesso é obrigatório")
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "id_perfil", nullable = false)
    private PerfilAcesso perfil;

    @Column(name = "id_colaborador")
    private UUID idColaborador;

    @NotNull(message = "O tipo de usuário é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_usuario", nullable = false)
    private TipoUsuario tipo = TipoUsuario.ADMINISTRATIVO;

    @Column(name = "tema", length = 20)
    private String tema = "light";

    @Column(name = "cor_primaria", length = 50)
    private String corPrimaria = "blue";

    @Column(name = "animacoes_ativadas")
    private boolean animacoesAtivadas = true;

    @Transient
    private Collection<? extends GrantedAuthority> authorities;

    public Usuario(String login, String email, String telefone, String senha, PerfilAcesso perfil, UUID idColaborador, TipoUsuario tipo) {
        validarCamposObrigatorios(login, senha, perfil);
        this.login = login.toUpperCase().trim();
        this.email = email != null ? email.toLowerCase().trim() : null;
        this.telefone = telefone != null ? telefone.replace(" ", "").replace("-", "") : null;
        this.senha = senha;
        this.perfil = perfil;
        this.idColaborador = idColaborador;
        this.tipo = tipo != null ? tipo : TipoUsuario.ADMINISTRATIVO;
    }

    /**
     * Atualiza as preferências de interface do usuário.
     * @param novoTema 'light' ou 'dark'
     * @param novaCor Nome da cor primária (ex: 'emerald', 'blue')
     * @param animacoesAtivadas Se as animações devem estar ativas
     */
    public void atualizarPreferencias(String novoTema, String novaCor, Boolean animacoesAtivadas) {
        if (novoTema != null && (novoTema.equalsIgnoreCase("light") || novoTema.equalsIgnoreCase("dark"))) {
            this.tema = novoTema.toLowerCase();
        }
        if (novaCor != null && !novaCor.isBlank()) {
            this.corPrimaria = novaCor.toLowerCase().trim();
        }
        if (animacoesAtivadas != null) {
            this.animacoesAtivadas = animacoesAtivadas;
        }
    }

    /**
     * Define as autoridades (perfil + permissões) do usuário de forma explícita.
     * Usado para evitar lazy loading em getAuthorities().
     */
    public void setAuthorities(Collection<? extends GrantedAuthority> authorities) {
        this.authorities = authorities;
    }

    /**
     * Atualiza os dados de contato do usuário.
     */
    public void atualizarContatos(String novoEmail, String novoTelefone) {
        if (novoEmail != null && !novoEmail.isBlank()) {
            this.email = novoEmail.toLowerCase();
        }
        this.telefone = novoTelefone;
    }

    /**
     * Altera o tipo de usuário.
     */
    public void alterarTipo(TipoUsuario novoTipo) {
        this.tipo = Objects.requireNonNull(novoTipo, "O tipo de usuário é obrigatório");
    }

    /**
     * Altera o perfil de acesso (Role) do usuário.
     */
    public void alterarPerfil(PerfilAcesso novoPerfil) {
        this.perfil = Objects.requireNonNull(novoPerfil, "O perfil de acesso é obrigatório");
    }

    /**
     * Altera o login de acesso do usuário.
     */
    public void trocarLogin(String novoLogin) {
        if (novoLogin == null || novoLogin.isBlank()) {
            throw new IllegalArgumentException("O novo login não pode ser nulo ou vazio");
        }
        this.login = novoLogin.toLowerCase();
    }

    /**
     * Atualiza a credencial de acesso. 
     * @param novaSenhaHash Deve ser o hash gerado pelo PasswordEncoder.
     */
    public void trocarSenha(String novaSenhaHash) {
        if (novaSenhaHash == null || novaSenhaHash.isBlank()) {
            throw new IllegalArgumentException("A nova senha não pode ser nula ou vazia");
        }
        this.senha = novaSenhaHash;
    }

    private void validarCamposObrigatorios(String login, String senha, PerfilAcesso perfil) {
        if (login == null || login.isBlank()) throw new IllegalArgumentException("Login inválido");
        if (senha == null || senha.isBlank()) throw new IllegalArgumentException("Senha inválida");
        if (perfil == null) throw new IllegalArgumentException("Perfil é obrigatório");
    }

    // UserDetails Implementation

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.authorities != null) {
            return this.authorities;
        }
        // Fallback básico para segurança caso não tenha sido injetado (não deve disparar SQL de permissões aqui)
        return List.of(new SimpleGrantedAuthority("ROLE_" + perfil.getNome()));
    }

    @Override
    public String getPassword() {
        return this.senha;
    }

    @Override
    public String getUsername() {
        return this.login;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isAtivo();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isAtivo();
    }
}
