package com.nexo.app.modules.estoque.application.usecases;

import com.nexo.app.modules.estoque.application.dto.ConfirmacaoImportacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.ConfirmarImportacaoSolicitacaoTecnicaRequest;
import com.nexo.app.modules.estoque.application.dto.ImportacaoSolicitacaoTecnicaResponse;
import com.nexo.app.modules.estoque.application.dto.PreviaImportacaoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.dto.ProcessamentoImportacao;
import com.nexo.app.modules.estoque.application.services.LeitorArquivoImportacaoSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.ModeloImportacaoSolicitacaoTecnicaService;
import com.nexo.app.modules.estoque.application.services.ObservabilidadeSolicitacaoTecnica;
import com.nexo.app.modules.estoque.application.services.ValidadorImportacaoSolicitacaoTecnicaService;
import com.nexo.app.modules.estoque.domain.LoteImportacaoSolicitacao;
import com.nexo.app.modules.estoque.infrastructure.persistence.LoteImportacaoSolicitacaoRepository;
import com.nexo.app.shared.domain.exception.PreenchimentoInvalidoException;
import com.nexo.app.shared.infrastructure.logging.LogFingerprint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacaoSolicitacaoTecnicaUseCases {

    private static final Duration VALIDADE_PREVIA = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final LeitorArquivoImportacaoSolicitacaoTecnica leitor;
    private final ValidadorImportacaoSolicitacaoTecnicaService validador;
    private final ModeloImportacaoSolicitacaoTecnicaService modelos;
    private final LoteImportacaoSolicitacaoRepository loteRepository;
    private final ConfirmadorGrupoImportacaoSolicitacaoTecnica confirmador;
    private final ObservabilidadeSolicitacaoTecnica observabilidade;

    @Transactional
    public ImportacaoSolicitacaoTecnicaResponse validar(MultipartFile arquivo, UUID idUsuario) {
        try {
            return validarInterno(arquivo, idUsuario);
        } catch (RuntimeException ex) {
            observabilidade.registrarImportacao(
                    ObservabilidadeSolicitacaoTecnica.EtapaImportacao.VALIDACAO,
                    ObservabilidadeSolicitacaoTecnica.ResultadoImportacao.FALHA, 1);
            throw ex;
        }
    }

    private ImportacaoSolicitacaoTecnicaResponse validarInterno(MultipartFile arquivo, UUID idUsuario) {
        if (idUsuario == null) {
            throw new PreenchimentoInvalidoException(Map.of("usuario", "O usuario autenticado e obrigatorio."));
        }
        var arquivoLido = leitor.ler(arquivo);
        PreviaImportacaoSolicitacaoTecnica previa = validador.validar(arquivoLido.linhas());
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (java.io.IOException ex) {
            throw new PreenchimentoInvalidoException(Map.of("arquivo", "Nao foi possivel ler o arquivo enviado."));
        }
        String token = gerarToken();
        String hashArquivo = sha256(bytes);
        OffsetDateTime criadoEm = OffsetDateTime.now();
        OffsetDateTime expiraEm = criadoEm.plus(VALIDADE_PREVIA);
        String nomeArquivo = arquivo.getOriginalFilename() == null ? "importacao" : arquivo.getOriginalFilename();
        LoteImportacaoSolicitacao lote = loteRepository.save(new LoteImportacaoSolicitacao(
                sha256(token), hashArquivo, nomeArquivo, arquivoLido.formato(), previa,
                arquivoLido.linhas().size(), idUsuario, criadoEm, expiraEm));
        log.info("Previa de importacao tecnica validada: lote={}, formato={}, linhas={}, grupos={}",
                lote.getId(), lote.getFormato(), lote.getTotalLinhas(), lote.getTotalSolicitacoes());
        boolean possuiErros = previa.solicitacoesPrevistas().stream()
                .flatMap(grupo -> grupo.mensagens().stream())
                .anyMatch(mensagem -> mensagem.severidade()
                        == PreviaImportacaoSolicitacaoTecnica.Severidade.ERRO);
        observabilidade.registrarImportacao(
                ObservabilidadeSolicitacaoTecnica.EtapaImportacao.VALIDACAO,
                possuiErros
                        ? ObservabilidadeSolicitacaoTecnica.ResultadoImportacao.COM_ERROS
                        : ObservabilidadeSolicitacaoTecnica.ResultadoImportacao.SUCESSO,
                1);
        return respostaValidacao(token, lote);
    }

    public ConfirmacaoImportacaoSolicitacaoTecnicaResponse confirmar(
            ConfirmarImportacaoSolicitacaoTecnicaRequest request,
            UUID idUsuario,
            String chaveIdempotencia
    ) {
        if (request == null) {
            observabilidade.registrarImportacao(
                    ObservabilidadeSolicitacaoTecnica.EtapaImportacao.CONFIRMACAO,
                    ObservabilidadeSolicitacaoTecnica.ResultadoImportacao.FALHA, 1);
            throw new PreenchimentoInvalidoException(Map.of("confirmacao", "A confirmacao e obrigatoria."));
        }
        if (chaveIdempotencia == null || chaveIdempotencia.isBlank() || chaveIdempotencia.length() > 200) {
            observabilidade.registrarImportacao(
                    ObservabilidadeSolicitacaoTecnica.EtapaImportacao.CONFIRMACAO,
                    ObservabilidadeSolicitacaoTecnica.ResultadoImportacao.FALHA, 1);
            throw new PreenchimentoInvalidoException(Map.of(
                    "Idempotency-Key", "Informe uma chave de idempotencia com ate 200 caracteres."));
        }
        String tokenHash = sha256(request.token());
        UUID idLote = loteRepository.findByTokenHashAndCriadoPor(tokenHash, idUsuario)
                .map(LoteImportacaoSolicitacao::getId).orElse(null);
        String chaveHash = sha256(chaveIdempotencia);
        OffsetDateTime agora = OffsetDateTime.now();
        List<ConfirmarImportacaoSolicitacaoTecnicaRequest.ChaveTipo> combinacoes = request.combinacoes();
        List<ConfirmacaoImportacaoSolicitacaoTecnicaResponse.Resultado> resultados = new ArrayList<>();
        for (ConfirmarImportacaoSolicitacaoTecnicaRequest.ChaveTipo combinacao : combinacoes) {
            String chave = combinacao.chave();
            String tipo = combinacao.tipo();
            try {
                var resultado = confirmador.confirmar(
                        tokenHash, chave, tipo, request.reconhecerAvisos(), idUsuario, chaveHash, agora);
                resultados.add(resultado);
                observabilidade.registrarImportacao(
                        ObservabilidadeSolicitacaoTecnica.EtapaImportacao.CONFIRMACAO,
                        ObservabilidadeSolicitacaoTecnica.ResultadoImportacao.SUCESSO, 1);
            } catch (RuntimeException ex) {
                String detalheErro = mensagemSegura(ex);
                log.warn("Falha ao confirmar solicitacao tecnica importada: lote={}, chave={}, tipo={}, erro={}, detalhe={}",
                        idLote, LogFingerprint.de(chave), tipo, ex.getClass().getSimpleName(), detalheErro);
                observabilidade.registrarImportacao(
                        ObservabilidadeSolicitacaoTecnica.EtapaImportacao.CONFIRMACAO,
                        ObservabilidadeSolicitacaoTecnica.ResultadoImportacao.FALHA, 1);
                resultados.add(new ConfirmacaoImportacaoSolicitacaoTecnicaResponse.Resultado(
                        chave, tipo, List.of(), detalheErro));
            }
        }
        int sucessos = (int) resultados.stream().filter(resultado -> resultado.erro() == null).count();
        return new ConfirmacaoImportacaoSolicitacaoTecnicaResponse(idLote, resultados,
                ProcessamentoImportacao.confirmacao(sucessos, resultados.size() - sucessos));
    }

    public byte[] modeloCsv() {
        return modelos.gerarCsv();
    }

    private ImportacaoSolicitacaoTecnicaResponse respostaValidacao(
            String token,
            LoteImportacaoSolicitacao lote
    ) {
        List<PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista> grupos = lote.getPrevia().solicitacoesPrevistas();
        int confirmaveis = (int) grupos.stream()
                .filter(PreviaImportacaoSolicitacaoTecnica.SolicitacaoPrevista::confirmavel).count();
        int erros = (int) grupos.stream().flatMap(grupo -> grupo.mensagens().stream())
                .filter(mensagem -> mensagem.severidade() == PreviaImportacaoSolicitacaoTecnica.Severidade.ERRO)
                .count();
        int avisos = (int) grupos.stream().flatMap(grupo -> grupo.mensagens().stream())
                .filter(mensagem -> mensagem.severidade() == PreviaImportacaoSolicitacaoTecnica.Severidade.AVISO)
                .count();
        return new ImportacaoSolicitacaoTecnicaResponse(
                token, lote.getHashArquivo(), lote.getExpiraEm(), lote.getTotalLinhas(),
                lote.getTotalSolicitacoes(), confirmaveis, erros, avisos, grupos,
                ProcessamentoImportacao.validacao(confirmaveis, erros));
    }

    private String gerarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String valor) {
        return sha256(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String sha256(byte[] valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponivel", ex);
        }
    }

    private String mensagemSegura(RuntimeException ex) {
        if (ex instanceof PreenchimentoInvalidoException preenchimento) {
            return preenchimento.getErros().entrySet().stream()
                    .map(erro -> rotuloCampo(erro.getKey()) + ": " + erro.getValue())
                    .collect(Collectors.joining("; "));
        }
        if (ex instanceof com.nexo.app.shared.domain.exception.BaseBusinessException) {
            return ex.getMessage();
        }
        return "Não foi possível criar esta solicitação. Tente novamente. Se o problema continuar, fale com o suporte.";
    }

    private String rotuloCampo(String campo) {
        if (campo == null) return "Campo";
        return switch (campo) {
            case "idObra", "numeroObra", "Obra" -> "Obra";
            case "ocorrencia", "Ocorrência" -> "Ocorrência";
            case "idAlmoxarifadoAtendimento", "idAlmoxarifado", "almoxarifado", "Almoxarifado" -> "Almoxarifado de atendimento";
            case "idEquipe", "codigoEquipe", "Equipe" -> "Equipe";
            case "idAgenteResponsavel", "agenteResponsavel", "Agente Responsável" -> "Agente responsável";
            case "numero", "numeroReserva", "Número da reserva" -> "Número da reserva";
            case "dataCriacao", "dataCriacaoReserva", "Data de criação da reserva" -> "Data de criação da reserva";
            case "dataPrevista", "Data prevista" -> "Data prevista";
            case "tipoMovimentacao", "tipo", "Tipo de movimentação" -> "Tipo de movimentação";
            case "idMaterial", "codigoMaterial", "Material" -> "Material";
            case "quantidade", "Quantidade" -> "Quantidade";
            case "observacao", "Observação" -> "Observação";
            case "idColaboradorSaque" -> "Colaborador do saque";
            case "saqueForaEquipe" -> "Saque fora da equipe";
            case "itens" -> "Itens";
            default -> {
                if (campo.startsWith("itens[") && campo.endsWith(".idMaterial")) {
                    yield "Material do item";
                }
                if (campo.startsWith("itens[") && campo.endsWith(".tipoMovimentacao")) {
                    yield "Tipo de movimentação do item";
                }
                if (campo.startsWith("itens[") && campo.endsWith(".quantidade")) {
                    yield "Quantidade do item";
                }
                if (campo.startsWith("itens[")) {
                    yield "Item da solicitação";
                }
                yield campo;
            }
        };
    }
}
