package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.DocumentoFinanceiroResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.AcoesDocumentoResponse;
import com.nexo.app.modules.financeiro.domain.DocumentoFinanceiro;
import com.nexo.app.modules.financeiro.domain.FonteReferencia;
import com.nexo.app.modules.financeiro.domain.SituacaoDocumento;
import com.nexo.app.modules.financeiro.domain.TipoSolicitacao;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.shared.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converte documentos nas respostas que a tela consome.
 *
 * <p>Faz duas coisas que o frontend, de outro modo, teria de fazer sozinho:
 * resolve o texto legível de cada referência (fornecedor, base, centro de custo,
 * obra, usuários) e responde as perguntas derivadas — vencido, duplicado,
 * identificação pendente, dias na fila. Todas são regra de leitura, e mantê-las
 * aqui é o que impede a tela e o dashboard de calcularem "vencido" de dois jeitos
 * diferentes.</p>
 *
 * <p>A resolução acontece sob um {@link Contexto} de uma requisição só: uma
 * listagem de cinquenta documentos da mesma obra consulta o contrato uma vez, não
 * cinquenta.</p>
 */
@Component
@RequiredArgsConstructor
public class MontadorDocumento {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");

    private final ResolvedorReferencias resolvedor;
    private final UsuarioApi usuarioApi;
    private final DocumentoFinanceiroRepository repository;

    /**
     * Memória de uma montagem. Guarda o que já foi resolvido e o conjunto de
     * conteúdos repetidos, que é uma consulta agregada só para a página inteira.
     */
    public final class Contexto {
        private final Set<String> hashesDuplicados;
        private final Map<String, String> descricoes = new HashMap<>();
        private final Map<UUID, String> usuarios = new HashMap<>();
        private final LocalDate hoje = LocalDate.now(ZONA);

        private Contexto(Set<String> hashesDuplicados) {
            this.hashesDuplicados = hashesDuplicados;
        }

        private String descrever(FonteReferencia fonte, UUID id) {
            if (id == null) {
                return null;
            }
            return descricoes.computeIfAbsent(fonte.name() + ":" + id, chave -> resolvedor.descrever(fonte, id));
        }

        private String usuario(UUID id) {
            if (id == null) {
                return null;
            }
            return usuarios.computeIfAbsent(id, chave -> usuarioApi.buscarNomePorId(chave).orElse(null));
        }
    }

    public Contexto novoContexto() {
        return new Contexto(Set.copyOf(repository.buscarHashesDuplicados()));
    }

    public DocumentoFinanceiroResponse montar(DocumentoFinanceiro documento) {
        return montar(documento, novoContexto());
    }

    public List<DocumentoFinanceiroResponse> montar(List<DocumentoFinanceiro> documentos) {
        Contexto contexto = novoContexto();
        return documentos.stream().map(documento -> montar(documento, contexto)).toList();
    }

    public DocumentoFinanceiroResponse montar(DocumentoFinanceiro documento, Contexto contexto) {
        var origem = documento.getOrigem() != null ? documento.getOrigem() : com.nexo.app.modules.financeiro.domain.OrigemDocumento.semVinculo();
        var tipo = documento.getTipo();
        var situacao = documento.getSituacao();

        return new DocumentoFinanceiroResponse(
                documento.getId(),
                documento.getNomeArquivo(),
                documento.getContentType(),
                documento.getTamanhoBytes(),
                documento.getHashConteudo(),
                documento.getHashConteudo() != null && contexto.hashesDuplicados.contains(documento.getHashConteudo()),

                tipo,
                tipo != null ? tipo.getRotulo() : null,
                situacao,
                situacao != null ? situacao.getRotulo() : null,
                documento.getNumeroDocumento(),
                documento.getChaveAcesso(),
                documento.getCodigoPagamento(),
                documento.possuiCodigoPagamento(),
                documento.identificacaoPendente(),

                documento.getFornecedorId(),
                nomeDoEmissor(documento, contexto),

                documento.getDataEmissao(),
                documento.getDataVencimento(),
                documento.getCompetencia(),
                documento.vencidoEm(contexto.hoje),
                documento.diasNaSituacao(contexto.hoje),
                documento.getValor(),

                documento.getBaseId(),
                contexto.descrever(FonteReferencia.BASE_OPERACIONAL, documento.getBaseId()),
                documento.getProcessoId(),
                contexto.descrever(FonteReferencia.PROCESSO, documento.getProcessoId()),
                documento.getObraId(),
                contexto.descrever(FonteReferencia.OBRA, documento.getObraId()),

                documento.getDescricao(),
                documento.getObservacoes(),

                origem.getTipo(),
                origem.getSolicitacaoId(),
                origem.getNumero(),

                documento.getUsuarioEnvioId(),
                contexto.usuario(documento.getUsuarioEnvioId()),
                documento.getUsuarioConferenciaId(),
                contexto.usuario(documento.getUsuarioConferenciaId()),
                documento.getDataConferencia(),
                documento.getObservacaoConferencia(),
                documento.getMotivoPendencia(),

                montarAcoes(documento),

                documento.isAtivo(),
                documento.getDataCriacao(),
                documento.getDataUltimaAlteracao(),
                documento.getUsuarioUltimaAlteracao());
    }

    public DocumentoResumoResponse montarResumo(DocumentoFinanceiro documento) {
        return montarResumo(documento, novoContexto());
    }

    public DocumentoResumoResponse montarResumo(DocumentoFinanceiro documento, Contexto contexto) {
        var tipo = documento.getTipo();
        var situacao = documento.getSituacao();

        return new DocumentoResumoResponse(
                documento.getId(),
                documento.getNomeArquivo(),
                documento.getContentType(),
                documento.getTamanhoBytes(),
                tipo,
                tipo != null ? tipo.getRotulo() : null,
                situacao,
                situacao != null ? situacao.getRotulo() : null,
                documento.getNumeroDocumento(),
                documento.getChaveAcesso(),
                documento.getCodigoPagamento(),
                documento.possuiCodigoPagamento(),
                documento.getHashConteudo() != null
                        && contexto.hashesDuplicados.contains(documento.getHashConteudo()),
                documento.identificacaoPendente(),
                documento.getValor(),
                documento.getUsuarioConferenciaId(),
                documento.getDataConferencia(),
                documento.getObservacaoConferencia(),
                documento.getMotivoPendencia(),
                montarAcoesDaSolicitacao(documento),
                documento.getDataCriacao(),
                documento.getUsuarioCriacao());
    }

    private AcoesDocumentoResponse montarAcoesDaSolicitacao(DocumentoFinanceiro documento) {
        if (documento.getSituacao() == SituacaoDocumento.CONFERIDO) {
            boolean podeLer = podeLer(documento);
            return new AcoesDocumentoResponse(
                    podeLer,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false);
        }
        return montarAcoes(documento);
    }

    private boolean podeLer(DocumentoFinanceiro documento) {
        var origem = documento.getOrigem() != null ? documento.getOrigem() : com.nexo.app.modules.financeiro.domain.OrigemDocumento.semVinculo();
        return SecurityUtils.possuiAutoridade("financeiro:documento:read")
                || (origem.getTipo() == TipoSolicitacao.COMPRA
                    && SecurityUtils.possuiAutoridade("financeiro:compra:read"))
                || (origem.getTipo() == TipoSolicitacao.PAGAMENTO
                    && SecurityUtils.possuiAutoridade("financeiro:pagamento:read"));
    }

    private AcoesDocumentoResponse montarAcoes(DocumentoFinanceiro documento) {
        var origem = documento.getOrigem() != null ? documento.getOrigem() : com.nexo.app.modules.financeiro.domain.OrigemDocumento.semVinculo();
        var situacao = documento.getSituacao();

        boolean podeLer = podeLer(documento);
        boolean podeEscrever = SecurityUtils.possuiAutoridade("financeiro:documento:write");
        boolean podeConferir = SecurityUtils.possuiAutoridade("financeiro:documento:conferir");
        boolean podeGerir = SecurityUtils.possuiAutoridade("financeiro:documento:gerir");
        boolean arquivado = situacao == SituacaoDocumento.ARQUIVADO;
        boolean autor = documento.getUsuarioEnvioId() != null
                && documento.getUsuarioEnvioId().equals(SecurityUtils.getUsuarioLogadoId());

        return new AcoesDocumentoResponse(
                podeLer,
                podeEscrever && !arquivado,
                podeEscrever && !arquivado && (autor || podeGerir),
                false,
                podeConferir && !arquivado,
                podeConferir && situacao == SituacaoDocumento.CONFERIDO,
                podeConferir && (situacao == SituacaoDocumento.ARQUIVADO || situacao == SituacaoDocumento.COM_PENDENCIA),
                podeEscrever && origem.getSolicitacaoId() != null);
    }

    /**
     * O fornecedor cadastrado tem precedência sobre o nome digitado: quando os
     * dois existem, o cadastro é o que a contabilidade reconcilia. O nome livre
     * continua servindo ao emissor que nunca foi cadastrado.
     */
    private String nomeDoEmissor(DocumentoFinanceiro documento, Contexto contexto) {
        String doCadastro = contexto.descrever(FonteReferencia.FORNECEDOR, documento.getFornecedorId());
        return StringUtils.hasText(doCadastro) ? doCadastro : documento.getEmissorNome();
    }
}
