package com.nexo.app.modules.iam.application.usecases;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nexo.app.modules.iam.application.dto.ConfirmarImportacaoUsuarioRequest;
import com.nexo.app.modules.iam.application.dto.ImportacaoUsuarioResponse;
import com.nexo.app.modules.iam.application.dto.UsuarioImportacaoDTO;
import com.nexo.app.modules.iam.domain.PerfilAcesso;
import com.nexo.app.modules.iam.domain.TipoUsuario;
import com.nexo.app.modules.iam.infrastructure.persistence.PerfilAcessoRepository;
import com.nexo.app.modules.iam.infrastructure.persistence.UsuarioRepository;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.shared.application.dto.ImportacaoStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Valida e confirma a importação em lote de usuários via CSV.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarUsuariosUseCase {

    private static final String SEPARADOR = ";";
    private static final int LIMITE_LINHAS = 2000;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UsuarioRepository repository;
    private final PerfilAcessoRepository perfilRepository;
    private final ColaboradorApi colaboradorApi;
    private final ImportarUsuariosAsyncService asyncService;

    public ImportacaoUsuarioResponse executarValidacao(MultipartFile arquivo) {
        List<UsuarioImportacaoDTO> registrosLidos = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            int numeroLinha = 0;

            while ((linha = reader.readLine()) != null) {
                numeroLinha++;

                if (numeroLinha == 1) {
                    continue;
                }
                if (linha.isBlank() || linha.trim().startsWith("#")) {
                    continue;
                }
                if (registrosLidos.size() >= LIMITE_LINHAS) {
                    log.warn("Arquivo de usuários excede o limite de {} registros", LIMITE_LINHAS);
                    break;
                }

                registrosLidos.add(processarLinhaCsv(linha, numeroLinha - 1));
            }
        } catch (Exception e) {
            log.error("Falha crítica ao ler arquivo de importação de usuários", e);
            throw new IllegalArgumentException("Falha ao ler o arquivo CSV: " + e.getMessage(), e);
        }

        return criarResponse(validarLote(registrosLidos));
    }

    public ImportacaoUsuarioResponse confirmarImportacao(ConfirmarImportacaoUsuarioRequest request) {
        List<UsuarioImportacaoDTO> registrosValidados = validarLote(request.usuarios());
        long falhas = registrosValidados.stream()
                .filter(r -> r.status() == ImportacaoStatus.COM_ERRO)
                .count();

        // A persistência parcial foi habilitada somente para colaboradores.
        if (falhas > 0) {
            return criarResponse(registrosValidados);
        }

        List<UsuarioImportacaoDTO> registrosEmProcessamento = registrosValidados.stream()
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        if (!registrosEmProcessamento.isEmpty()) {
            asyncService.processarPersistencia(registrosEmProcessamento);
        }

        return new ImportacaoUsuarioResponse(
                registrosEmProcessamento,
                registrosEmProcessamento.size(),
                registrosEmProcessamento.size(),
                0
        );
    }

    private List<UsuarioImportacaoDTO> validarLote(List<UsuarioImportacaoDTO> registros) {
        Set<String> logins = new HashSet<>();
        Set<String> emails = new HashSet<>();
        Set<String> colaboradores = new HashSet<>();
        List<UsuarioImportacaoDTO> validados = new ArrayList<>();

        for (UsuarioImportacaoDTO registro : registros) {
            UsuarioImportacaoDTO validado = validarRegistro(registro);
            if (validado.status() != ImportacaoStatus.COM_ERRO) {
                validado = validarDuplicidadesDoLote(validado, logins, emails, colaboradores);
            }
            validados.add(validado);
        }
        return validados;
    }

    private UsuarioImportacaoDTO validarDuplicidadesDoLote(
            UsuarioImportacaoDTO dto,
            Set<String> logins,
            Set<String> emails,
            Set<String> colaboradores
    ) {
        if (!logins.add(dto.login().trim().toUpperCase(Locale.ROOT))) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, "Login duplicado no arquivo de importação");
        }

        if (!texto(dto.email()).isBlank()
                && !emails.add(dto.email().trim().toLowerCase(Locale.ROOT))) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, "E-mail duplicado no arquivo de importação");
        }

        if (!texto(dto.nomeColaborador()).isBlank()
                && !colaboradores.add(dto.nomeColaborador().trim().toUpperCase(Locale.ROOT))) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, "Colaborador duplicado no arquivo de importação");
        }

        return dto;
    }

    private UsuarioImportacaoDTO validarRegistro(UsuarioImportacaoDTO dto) {
        try {
            validarLogin(dto.login());
            validarEmail(dto.email());
            validarTelefone(dto.telefone());
            validarSenha(dto.senha());
            validarPerfil(dto.nomePerfil());
            validarTipo(dto.tipo());
            validarColaborador(dto.nomeColaborador());
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
        } catch (IllegalArgumentException e) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, e.getMessage());
        }
    }

    private void validarLogin(String login) {
        String valor = texto(login).trim();
        if (valor.length() < 3 || valor.length() > 9) {
            throw new IllegalArgumentException("Login deve ter entre 3 e 9 caracteres");
        }
        if (repository.existsByLoginIgnoreCase(valor)) {
            throw new IllegalArgumentException("Login já cadastrado no sistema");
        }
    }

    private void validarEmail(String email) {
        String valor = texto(email).trim();
        if (valor.isBlank()) {
            return;
        }
        if (!EMAIL_PATTERN.matcher(valor).matches()) {
            throw new IllegalArgumentException("E-mail inválido");
        }
        if (repository.existsByEmailIgnoreCase(valor)) {
            throw new IllegalArgumentException("E-mail já cadastrado no sistema");
        }
    }

    private void validarTelefone(String telefone) {
        String valor = texto(telefone).replace(" ", "").replace("-", "");
        if (valor.isBlank()) {
            return;
        }
        if (!valor.isBlank() && (valor.length() < 7 || valor.length() > 20)) {
            throw new IllegalArgumentException("Telefone deve ter entre 7 e 20 caracteres");
        }
    }

    private void validarSenha(String senha) {
        if (texto(senha).isBlank()) {
            throw new IllegalArgumentException("Senha é obrigatória");
        }
    }

    private void validarPerfil(String nomePerfil) {
        String valor = texto(nomePerfil).trim();
        if (valor.isBlank()) {
            throw new IllegalArgumentException("Perfil de acesso é obrigatório");
        }
        PerfilAcesso perfil = perfilRepository.findByNomeIgnoreCase(valor)
                .orElseThrow(() -> new IllegalArgumentException("Perfil de acesso não encontrado: " + valor));
        if (!perfil.isAtivo()) {
            throw new IllegalArgumentException("Perfil de acesso está inativo: " + valor);
        }
    }

    private void validarTipo(String tipo) {
        String valor = texto(tipo).trim();
        try {
            TipoUsuario.valueOf(valor.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de usuário inválido; use ADMINISTRATIVO ou CAMPO");
        }
    }

    private void validarColaborador(String nomeColaborador) {
        String valor = texto(nomeColaborador).trim();
        if (valor.isBlank()) {
            return;
        }

        List<ColaboradorResponse> encontrados = colaboradorApi.buscarPorNomeCompletoResponse(valor);
        if (encontrados.isEmpty()) {
            throw new IllegalArgumentException("Colaborador não encontrado: " + valor);
        }
        if (encontrados.size() > 1) {
            throw new IllegalArgumentException("Nome de colaborador ambíguo: " + valor);
        }

        ColaboradorResponse colaborador = encontrados.getFirst();
        if (!colaborador.ativo()) {
            throw new IllegalArgumentException("Colaborador está inativo: " + valor);
        }
        if (colaborador.idUsuario() != null) {
            throw new IllegalArgumentException("Colaborador já possui um usuário vinculado");
        }
    }

    private UsuarioImportacaoDTO processarLinhaCsv(String linha, int indice) {
        String[] colunas = linha.split(SEPARADOR, -1);
        return new UsuarioImportacaoDTO(
                indice,
                coluna(colunas, 0),
                coluna(colunas, 1),
                coluna(colunas, 2),
                coluna(colunas, 3),
                coluna(colunas, 4),
                coluna(colunas, 5),
                coluna(colunas, 6),
                ImportacaoStatus.AGUARDANDO_PROCESSAMENTO,
                null
        );
    }

    private String coluna(String[] colunas, int indice) {
        return colunas.length > indice ? colunas[indice].trim() : "";
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private ImportacaoUsuarioResponse criarResponse(List<UsuarioImportacaoDTO> registros) {
        int falhas = (int) registros.stream()
                .filter(r -> r.status() == ImportacaoStatus.COM_ERRO)
                .count();
        return new ImportacaoUsuarioResponse(
                registros,
                registros.size(),
                registros.size() - falhas,
                falhas
        );
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Login;E-mail;Telefone;Senha;Perfil de Acesso;Tipo de Usuário;Nome Completo do Colaborador (opcional)\n";
        String exemploSemVinculo = "# Exemplo sem colaborador: JSILVA;joao@empresa.com.br;11999999999;Senha@123;USUARIO;ADMINISTRATIVO;\n";
        String exemploComVinculo = "# Exemplo com colaborador: MSOUZA;maria@empresa.com.br;11988888888;Senha@123;CAMPO;CAMPO;MARIA DE SOUZA\n";
        return ("\uFEFF" + cabecalho + exemploSemVinculo + exemploComVinculo)
                .getBytes(StandardCharsets.UTF_8);
    }
}
