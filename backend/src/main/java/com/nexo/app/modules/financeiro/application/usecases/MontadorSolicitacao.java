package com.nexo.app.modules.financeiro.application.usecases;

import com.nexo.app.modules.financeiro.application.dto.AcoesDisponiveisResponse;
import com.nexo.app.modules.financeiro.application.dto.CampoValorResponse;
import com.nexo.app.modules.financeiro.application.dto.DocumentoResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.DesfechoResponse;
import com.nexo.app.modules.financeiro.application.dto.FornecedorSolicitacaoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoCompraResumoResponse;
import com.nexo.app.modules.financeiro.application.dto.SolicitacaoPagamentoResponse;
import com.nexo.app.modules.financeiro.application.mappers.FormularioMapper;
import com.nexo.app.modules.financeiro.domain.CampoFormulario;
import com.nexo.app.modules.financeiro.domain.FonteReferencia;
import com.nexo.app.modules.financeiro.domain.Fornecedor;
import com.nexo.app.modules.financeiro.domain.Rotulavel;
import com.nexo.app.modules.financeiro.domain.SolicitacaoBase;
import com.nexo.app.modules.financeiro.domain.SolicitacaoCompra;
import com.nexo.app.modules.financeiro.domain.SolicitacaoPagamento;
import com.nexo.app.modules.financeiro.domain.StatusSolicitacao;
import com.nexo.app.modules.financeiro.domain.TipoCampo;
import com.nexo.app.modules.financeiro.infrastructure.persistence.DocumentoFinanceiroRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.shared.application.dto.PageResponse;
import org.springframework.data.domain.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Converte as entidades de solicitação nas respostas que a tela consome.
 *
 * <p>Faz três coisas que o frontend, de outro modo, teria de fazer sozinho: indexa
 * os valores pela mesma chave publicada no schema (para uma tela genérica saber
 * onde cada dado vai), resolve o texto legível de cada referência e enumeração
 * (para listagem e detalhe não dispararem uma consulta por linha) e informa quais
 * ações o estado atual permite.</p>
 *
 * <p>Os documentos vêm do repositório financeiro, e não do agregado: eles deixaram
 * de pertencer à solicitação para poderem chegar depois da aprovação.</p>
 */
@Component
@RequiredArgsConstructor
public class MontadorSolicitacao {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final Locale BRASIL = Locale.of("pt", "BR");

    private final FormularioMapper formularioMapper;
    private final ResolvedorReferencias resolvedor;
    private final UsuarioApi usuarioApi;
    private final ColaboradorApi colaboradorApi;
    private final DocumentoFinanceiroRepository documentoRepository;
    private final MontadorDocumento montadorDocumento;

    // ── Compra ──────────────────────────────────────────────────────────────────

    public SolicitacaoCompraResponse montar(SolicitacaoCompra compra) {
        return montar(compra, documentosDe(compra.getId()));
    }

    /**
     * Sobrecarga usada pela listagem, que carrega os documentos de todas as linhas
     * numa consulta só — sem ela, montar uma página de vinte solicitações
     * dispararia vinte consultas ao repositório documental.
     */
    public SolicitacaoCompraResponse montar(SolicitacaoCompra compra, List<DocumentoResumoResponse> anexados) {
        return new SolicitacaoCompraResponse(
                compra.getId(),
                compra.getNumero(),
                compra.getStatus(),
                formularioMapper.toResumo(compra.getFormulario()),
                montarValores(compra),
                compra.getNucleo().getValor(),
                montarFornecedor(compra),
                compra.getUsuarioSolicitanteId(),
                nomeDoUsuario(compra.getUsuarioSolicitanteId()),
                nomeDoColaborador(compra.getUsuarioSolicitanteId()),
                compra.getDataSubmissao(),
                montarDesfecho(compra),
                anexados,
                montarAcoes(compra),
                compra.isAtivo(),
                compra.getDataCriacao(),
                compra.getDataUltimaAlteracao(),
                compra.getUsuarioUltimaAlteracao());
    }

    /** Resumo usado do lado do pagamento, para identificar a compra de origem. */
    public SolicitacaoCompraResumoResponse montarResumo(SolicitacaoCompra compra) {
        return new SolicitacaoCompraResumoResponse(
                compra.getId(),
                compra.getNumero(),
                compra.getStatus(),
                compra.getValorAprovado(),
                descreverFornecedorDe(compra),
                compra.getNucleo().getDescricao());
    }

    // ── Pagamento ───────────────────────────────────────────────────────────────

    public SolicitacaoPagamentoResponse montar(SolicitacaoPagamento pagamento) {
        return montar(pagamento, documentosDe(pagamento.getId()));
    }

    /** Ver a nota na sobrecarga equivalente da compra. */
    public SolicitacaoPagamentoResponse montar(SolicitacaoPagamento pagamento,
                                               List<DocumentoResumoResponse> anexados) {
        return new SolicitacaoPagamentoResponse(
                pagamento.getId(),
                pagamento.getNumero(),
                pagamento.getStatus(),
                formularioMapper.toResumo(pagamento.getFormulario()),
                montarValores(pagamento),
                pagamento.getNucleo().getValor(),
                montarFornecedor(pagamento),
                resolvedor.buscarCompra(pagamento.getSolicitacaoCompraId())
                        .map(this::montarResumo)
                        .orElse(null),
                pagamento.getUsuarioSolicitanteId(),
                nomeDoUsuario(pagamento.getUsuarioSolicitanteId()),
                nomeDoColaborador(pagamento.getUsuarioSolicitanteId()),
                pagamento.getDataSubmissao(),
                montarDesfecho(pagamento),
                anexados,
                montarAcoes(pagamento),
                pagamento.isAtivo(),
                pagamento.getDataCriacao(),
                pagamento.getDataUltimaAlteracao(),
                pagamento.getUsuarioUltimaAlteracao());
    }

    // ── Valores ─────────────────────────────────────────────────────────────────

    /**
     * Percorre o formulário, e não as colunas, para que o resultado siga a ordem e
     * o conjunto de campos da variante — inclusive expondo como nulos os campos que
     * ainda não foram preenchidos.
     */
    private Map<String, CampoValorResponse> montarValores(SolicitacaoBase solicitacao) {
        Map<String, CampoValorResponse> resultado = new LinkedHashMap<>();
        for (CampoFormulario campo : solicitacao.getFormulario().camposOrdenados()) {
            Object bruto = solicitacao.getNucleo().valorDe(campo.getCampoCanonico());
            resultado.put(campo.getChave(), CampoValorResponse.de(bruto, descrever(campo, bruto)));
        }
        return resultado;
    }

    /**
     * Texto legível de um valor. Resolve referências no cadastro de origem e
     * enumerações no próprio catálogo, e formata datas e moeda no padrão brasileiro.
     */
    private String descrever(CampoFormulario campo, Object bruto) {
        if (bruto == null) {
            return null;
        }
        if (campo.getTipoCampo() == TipoCampo.REFERENCIA) {
            UUID id = bruto instanceof UUID uuid ? uuid : UUID.fromString(bruto.toString());
            String descricao = resolvedor.descrever(campo.getFonteReferencia(), id);
            return descricao != null ? descricao : id.toString();
        }
        if (bruto instanceof Rotulavel rotulavel) {
            return rotulavel.getRotulo();
        }
        if (bruto instanceof Boolean logico) {
            return Boolean.TRUE.equals(logico) ? "Sim" : "Não";
        }
        if (bruto instanceof LocalDate data) {
            return data.format(FORMATO_DATA);
        }
        if (campo.getTipoCampo() == TipoCampo.MOEDA && bruto instanceof BigDecimal valor) {
            return NumberFormat.getCurrencyInstance(BRASIL).format(valor);
        }
        // A coluna guarda quatro casas; "2,0000" esconde o número que importa.
        if (bruto instanceof BigDecimal decimal) {
            NumberFormat formato = NumberFormat.getNumberInstance(BRASIL);
            formato.setMaximumFractionDigits(4);
            return formato.format(decimal);
        }
        return bruto.toString();
    }

    // ── Blocos comuns ───────────────────────────────────────────────────────────

    private DesfechoResponse montarDesfecho(SolicitacaoBase solicitacao) {
        return new DesfechoResponse(
                solicitacao.getUsuarioDecisaoId(),
                nomeDoUsuario(solicitacao.getUsuarioDecisaoId()),
                solicitacao.getDataDecisao(),
                solicitacao.getValorAprovado(),
                solicitacao.getObservacaoDecisao(),
                solicitacao.getMotivoRejeicao(),
                solicitacao.getUsuarioCancelamentoId(),
                solicitacao.getDataCancelamento(),
                solicitacao.getMotivoCancelamento());
    }

    /** Traduz o estado nas ações que a tela deve habilitar. */
    private AcoesDisponiveisResponse montarAcoes(SolicitacaoBase solicitacao) {
        StatusSolicitacao status = solicitacao.getStatus();
        return new AcoesDisponiveisResponse(
                status.isEditavel(),
                status == StatusSolicitacao.RASCUNHO,
                status == StatusSolicitacao.SOLICITADO,
                !status.isFinalizado(),
                status == StatusSolicitacao.APROVADO || status == StatusSolicitacao.REJEITADO);
    }

    private String descreverFornecedorDe(SolicitacaoCompra compra) {
        UUID fornecedorId = compra.getNucleo().getFornecedorId();
        if (fornecedorId != null) {
            String nome = resolvedor.descrever(FonteReferencia.FORNECEDOR, fornecedorId);
            if (nome != null) {
                return nome;
            }
        }
        return compra.getNucleo().getFornecedorNome();
    }

    private FornecedorSolicitacaoResponse montarFornecedor(SolicitacaoBase solicitacao) {
        var nucleo = solicitacao.getNucleo();
        UUID fornecedorId = nucleo.getFornecedorId();
        if (fornecedorId != null) {
            return resolvedor.buscarFornecedor(fornecedorId)
                    .map(this::montarFornecedorCadastrado)
                    .orElse(new FornecedorSolicitacaoResponse(
                            fornecedorId, fornecedorId.toString(), null, true));
        }
        String nome = StringUtils.hasText(nucleo.getFornecedorNome())
                ? nucleo.getFornecedorNome() : nucleo.getFornecedorRazaoSocial();
        if (!StringUtils.hasText(nome)) {
            return null;
        }
        return new FornecedorSolicitacaoResponse(
                null, nome, nucleo.getFornecedorRazaoSocial(), false);
    }

    private FornecedorSolicitacaoResponse montarFornecedorCadastrado(Fornecedor fornecedor) {
        return new FornecedorSolicitacaoResponse(
                fornecedor.getId(), fornecedor.getNomeFantasia(), fornecedor.getRazaoSocial(), true);
    }

    private String nomeDoUsuario(UUID idUsuario) {
        return idUsuario == null ? null : usuarioApi.buscarNomePorId(idUsuario).orElse(null);
    }

    private String nomeDoColaborador(UUID idUsuario) {
        return idUsuario == null ? null : colaboradorApi.buscarNomeCompletoPorUsuario(idUsuario).orElse(null);
    }

    // ── Documentos ──────────────────────────────────────────────────────────────

    /**
     * Monta uma página inteira carregando os documentos de todas as linhas numa
     * consulta só. Sem isto, listar vinte solicitações dispararia vinte consultas
     * ao repositório documental — e é a listagem, não o detalhe, que roda o tempo
     * todo.
     */
    public <T extends SolicitacaoBase, R> PageResponse<R> montarPagina(
            Page<T> pagina, BiFunction<T, List<DocumentoResumoResponse>, R> montagem) {
        Map<UUID, List<DocumentoResumoResponse>> porSolicitacao = mapearDocumentos(
                pagina.getContent().stream().map(SolicitacaoBase::getId).toList());
        return PageResponse.fromPage(pagina.map(
                solicitacao -> montagem.apply(solicitacao,
                        porSolicitacao.getOrDefault(solicitacao.getId(), List.of()))));
    }

    private List<DocumentoResumoResponse> documentosDe(UUID solicitacaoId) {
        var contexto = montadorDocumento.novoContexto();
        return documentoRepository.findByOrigemSolicitacaoIdAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoId)
                .stream()
                .map(documento -> montadorDocumento.montarResumo(documento, contexto))
                .toList();
    }

    private Map<UUID, List<DocumentoResumoResponse>> mapearDocumentos(List<UUID> solicitacaoIds) {
        if (solicitacaoIds.isEmpty()) {
            return Map.of();
        }
        var contexto = montadorDocumento.novoContexto();
        return documentoRepository
                .findByOrigemSolicitacaoIdInAndAtivoTrueOrderByDataCriacaoAsc(solicitacaoIds).stream()
                .collect(Collectors.groupingBy(
                        documento -> documento.getOrigem().getSolicitacaoId(),
                        Collectors.mapping(documento -> montadorDocumento.montarResumo(documento, contexto),
                                Collectors.toList())));
    }

    /** Reaproveitado pelo pré-preenchimento, que precisa das mesmas traduções. */
    CampoValorResponse valorLegivel(CampoFormulario campo, Object bruto) {
        return CampoValorResponse.de(bruto, descrever(campo, bruto));
    }
}
