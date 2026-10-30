package com.nexo.app.modules.desconto.infrastructure.notificacao;

import com.nexo.app.modules.desconto.domain.Desconto;
import com.nexo.app.modules.desconto.domain.DescontoAnexo;
import com.nexo.app.modules.desconto.domain.DescontoItem;
import com.nexo.app.modules.desconto.domain.StatusDesconto;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ContextoOrganizacional;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao.Acao;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao.Campo;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao.Estilo;
import com.nexo.app.shared.application.notificacao.FornecedorDetalhesNotificacao;
import com.nexo.app.shared.domain.events.DescontoSolicitadoEvent;
import com.nexo.app.shared.domain.events.DescontoValidadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A ficha do termo de desconto nos e-mails, e os botões de validar, rejeitar e dar baixa.
 *
 * <p>O desconto é sobre uma pessoa: quem valida precisa saber de quem, de que base e
 * função, por quê, quanto e em quantas parcelas sem abrir o sistema. O colaborador
 * aparece em todo o fluxo — no aviso de registro, de validação, de baixa e de
 * rejeição —, porque é ele que o DP procura quando o e-mail chega.</p>
 *
 * <p>Os botões só saem no passo que ainda espera aquela ação: validar e rejeitar no
 * aviso de registro, enquanto o termo está solicitado; dar baixa no aviso de
 * validação, enquanto ele está validado.</p>
 */
@Component
@RequiredArgsConstructor
public class DescontoDetalhesNotificacao implements FornecedorDetalhesNotificacao {

    private static final Locale BRASIL = Locale.of("pt", "BR");
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final int LIMITE_TEXTO = 280;
    private static final int ITENS_LISTADOS = 5;

    private static final Map<StatusDesconto, String> SITUACAO = Map.of(
            StatusDesconto.SOLICITADO, "Aguardando validação",
            StatusDesconto.VALIDADO, "Validado, aguardando baixa",
            StatusDesconto.BAIXADO, "Baixado",
            StatusDesconto.REJEITADO, "Rejeitado");

    private final DescontoRepository repository;
    private final ColaboradorApi colaboradorApi;
    private final UsuarioApi usuarioApi;

    @Override
    public boolean atende(String chaveGatilho) {
        return chaveGatilho != null && chaveGatilho.startsWith("desconto.")
                && !chaveGatilho.equals("desconto.pendente_aprovacao");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DetalhesNotificacao> detalhesDe(String chaveGatilho, UUID idDesconto) {
        return repository.findById(idDesconto)
                .map(desconto -> new DetalhesNotificacao(ficha(desconto), acoes(chaveGatilho, desconto)));
    }

    private List<Acao> acoes(String chaveGatilho, Desconto desconto) {
        String caminho = "/dp/descontos/" + desconto.getId();
        if (DescontoSolicitadoEvent.CHAVE_GATILHO.equals(chaveGatilho)
                && desconto.getStatus() == StatusDesconto.SOLICITADO) {
            return List.of(
                    new Acao("Validar", caminho + "?acao=validar", Estilo.POSITIVA),
                    new Acao("Rejeitar", caminho + "?acao=rejeitar", Estilo.NEGATIVA));
        }
        if (DescontoValidadoEvent.CHAVE_GATILHO.equals(chaveGatilho)
                && desconto.getStatus() == StatusDesconto.VALIDADO) {
            return List.of(new Acao("Dar baixa", caminho + "?acao=baixar", Estilo.POSITIVA));
        }
        return List.of();
    }

    private List<Campo> ficha(Desconto desconto) {
        List<Campo> ficha = new ArrayList<>();
        ficha.add(new Campo("Termo", "#" + desconto.getId().toString().substring(0, 8)));
        ficha.add(new Campo("Situação", SITUACAO.get(desconto.getStatus())));

        ContextoOrganizacional colaborador = colaboradorApi
                .buscarContextoOrganizacional(Set.of(desconto.getColaboradorId()))
                .get(desconto.getColaboradorId());
        if (colaborador != null) {
            ficha.add(new Campo("Colaborador", colaborador.nomeCompleto()
                    + (colaborador.ativo() ? "" : " (desligado)")));
            adicionar(ficha, "Função", colaborador.funcao());
            adicionar(ficha, "Base operacional", colaborador.baseOperacional());
            adicionar(ficha, "Regional", colaborador.centroCusto());
            adicionar(ficha, "Processo", colaborador.processo());
        }

        if (desconto.getMotivo() != null) {
            adicionar(ficha, "Motivo", desconto.getMotivo().getDescricao());
        }
        if (desconto.getDataOcorrencia() != null) {
            ficha.add(new Campo("Ocorrência", desconto.getDataOcorrencia().atZoneSameInstant(FUSO).format(DATA)));
        }
        adicionar(ficha, "Itens", itens(desconto.getItens()));

        ficha.add(new Campo("Valor sugerido", moeda(desconto.getValorSugerido())));
        if (desconto.getValorValidado() != null) {
            ficha.add(new Campo("Valor validado", moeda(desconto.getValorValidado())));
        }
        if (desconto.getValorBaixado() != null) {
            ficha.add(new Campo("Valor baixado", moeda(desconto.getValorBaixado())));
        }
        adicionar(ficha, "Parcelas", parcelas(desconto));

        adicionar(ficha, "Registrado por", nome(desconto.getUsuarioSolicitanteId()));
        if (desconto.getDataValidacao() != null) {
            ficha.add(new Campo("Validado por", juntar(nome(desconto.getUsuarioValidadorId()),
                    dataHora(desconto.getDataValidacao()))));
        }
        if (desconto.getDataBaixa() != null) {
            ficha.add(new Campo("Baixado por", juntar(nome(desconto.getUsuarioBaixaId()),
                    dataHora(desconto.getDataBaixa()))));
        }
        if (desconto.getStatus() == StatusDesconto.REJEITADO) {
            adicionar(ficha, "Rejeitado por", juntar(nome(desconto.getUsuarioRejeicaoId()),
                    desconto.getDataRejeicao() == null ? null : dataHora(desconto.getDataRejeicao())));
            adicionar(ficha, "Motivo da rejeição", resumir(desconto.getMotivoRejeicao()));
        }
        adicionar(ficha, "Observações", resumir(desconto.getObservacoes()));
        ficha.add(new Campo("Anexos", anexos(desconto.getAnexos())));
        return ficha;
    }

    /** O valor que vale agora, dividido pelas parcelas: é o que vai sair no contracheque. */
    private static String parcelas(Desconto desconto) {
        Integer quantidade = desconto.getQuantidadeParcelas();
        if (quantidade == null || quantidade <= 0) {
            return null;
        }
        BigDecimal vigente = desconto.getValorBaixado() != null ? desconto.getValorBaixado()
                : desconto.getValorValidado() != null ? desconto.getValorValidado()
                : desconto.getValorSugerido();
        if (vigente == null || quantidade == 1) {
            return quantidade == 1 ? "Parcela única" : quantidade + "x";
        }
        return quantidade + "x de " + moeda(vigente.divide(BigDecimal.valueOf(quantidade), 2, RoundingMode.HALF_UP));
    }

    private static String itens(List<DescontoItem> itens) {
        if (itens == null || itens.isEmpty()) {
            return null;
        }
        String listados = itens.stream()
                .limit(ITENS_LISTADOS)
                .map(item -> descricao(item) + (item.getValor() == null ? "" : " (" + moeda(item.getValor()) + ")"))
                .collect(Collectors.joining("; "));
        int restantes = itens.size() - ITENS_LISTADOS;
        return restantes > 0 ? listados + "; e mais " + restantes : listados;
    }

    private static String descricao(DescontoItem item) {
        if (StringUtils.hasText(item.getDescricaoMaterial())) {
            return item.getDescricaoMaterial().trim();
        }
        return item.getTipoEquipamento() != null ? item.getTipoEquipamento().getDescricao() : "Item";
    }

    private static String anexos(List<DescontoAnexo> anexos) {
        long ativos = anexos == null ? 0 : anexos.stream().filter(DescontoAnexo::isAtivo).count();
        if (ativos == 0) {
            return "Nenhum anexo";
        }
        return ativos == 1 ? "1 anexo (segue junto, se couber)" : ativos + " anexos (seguem junto, se couberem)";
    }

    /** Nome do colaborador do usuário, senão o login. */
    private String nome(UUID idUsuario) {
        if (idUsuario == null) {
            return null;
        }
        return colaboradorApi.buscarNomeCompletoPorUsuario(idUsuario)
                .or(() -> usuarioApi.buscarNomePorId(idUsuario))
                .orElse(null);
    }

    private static void adicionar(List<Campo> ficha, String rotulo, String valor) {
        if (StringUtils.hasText(valor)) {
            ficha.add(new Campo(rotulo, valor));
        }
    }

    private static String juntar(String quem, String quando) {
        if (!StringUtils.hasText(quem)) {
            return quando;
        }
        return StringUtils.hasText(quando) ? quem + " · " + quando : quem;
    }

    private static String resumir(String texto) {
        if (!StringUtils.hasText(texto)) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.length() <= LIMITE_TEXTO ? limpo : limpo.substring(0, LIMITE_TEXTO).stripTrailing() + "…";
    }

    private static String moeda(BigDecimal valor) {
        return valor == null ? "—" : NumberFormat.getCurrencyInstance(BRASIL).format(valor);
    }

    private static String dataHora(OffsetDateTime data) {
        return data.atZoneSameInstant(FUSO).format(DATA_HORA);
    }
}
