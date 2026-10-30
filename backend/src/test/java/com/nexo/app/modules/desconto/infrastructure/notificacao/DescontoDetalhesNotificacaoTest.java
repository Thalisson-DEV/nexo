package com.nexo.app.modules.desconto.infrastructure.notificacao;

import com.nexo.app.modules.desconto.domain.Desconto;
import com.nexo.app.modules.desconto.domain.MotivoDesconto;
import com.nexo.app.modules.desconto.domain.TipoEquipamento;
import com.nexo.app.modules.desconto.infrastructure.persistence.DescontoRepository;
import com.nexo.app.modules.iam.UsuarioApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ContextoOrganizacional;
import com.nexo.app.shared.application.notificacao.DetalhesNotificacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** A ficha do desconto no e-mail e os botões de cada etapa. */
@ExtendWith(MockitoExtension.class)
class DescontoDetalhesNotificacaoTest {

    private static final UUID COLABORADOR = UUID.randomUUID();

    @Mock private DescontoRepository repository;
    @Mock private ColaboradorApi colaboradorApi;
    @Mock private UsuarioApi usuarioApi;

    private DescontoDetalhesNotificacao detalhes;
    private Desconto desconto;

    @BeforeEach
    void configurar() {
        detalhes = new DescontoDetalhesNotificacao(repository, colaboradorApi, usuarioApi);
        desconto = new Desconto(COLABORADOR, new MotivoDesconto("Avaria de equipamento", 6), "Capacete quebrado",
                3, OffsetDateTime.parse("2026-10-20T10:00:00-03:00"), UUID.randomUUID());
        ReflectionTestUtils.setField(desconto, "id", UUID.randomUUID());
        desconto.adicionarItem("Capacete classe B", new TipoEquipamento("EPI"), new BigDecimal("90.00"));
        when(repository.findById(desconto.getId())).thenReturn(Optional.of(desconto));
        lenient().when(colaboradorApi.buscarContextoOrganizacional(any())).thenReturn(Map.of(COLABORADOR,
                new ContextoOrganizacional(COLABORADOR, "MARIA SOUZA", null, "ELETRICISTA",
                        null, "JUAZEIRO", null, "REGIONAL NORTE", null, "MANUTENÇÃO", true)));
        lenient().when(colaboradorApi.buscarNomeCompletoPorUsuario(any())).thenReturn(Optional.of("Ana do DP"));
    }

    @Test
    @DisplayName("A ficha traz o colaborador, a base e o desconto por parcela")
    void fichaComColaborador() {
        var ficha = detalhes.detalhesDe("desconto.solicitado", desconto.getId()).orElseThrow().ficha();

        assertThat(ficha).extracting(DetalhesNotificacao.Campo::rotulo, DetalhesNotificacao.Campo::valor)
                .contains(
                        org.assertj.core.groups.Tuple.tuple("Colaborador", "MARIA SOUZA"),
                        org.assertj.core.groups.Tuple.tuple("Função", "ELETRICISTA"),
                        org.assertj.core.groups.Tuple.tuple("Base operacional", "JUAZEIRO"),
                        org.assertj.core.groups.Tuple.tuple("Regional", "REGIONAL NORTE"),
                        org.assertj.core.groups.Tuple.tuple("Motivo", "Avaria de equipamento"),
                        org.assertj.core.groups.Tuple.tuple("Itens", "Capacete classe B (R$ 90,00)"),
                        org.assertj.core.groups.Tuple.tuple("Parcelas", "3x de R$ 30,00"),
                        org.assertj.core.groups.Tuple.tuple("Registrado por", "Ana do DP"));
    }

    @Test
    @DisplayName("Validar e rejeitar só no aviso de registro, enquanto o termo aguarda validação")
    void acoesDeValidacao() {
        var acoes = detalhes.detalhesDe("desconto.solicitado", desconto.getId()).orElseThrow().acoes();

        assertThat(acoes).extracting(DetalhesNotificacao.Acao::rotulo).containsExactly("Validar", "Rejeitar");
        assertThat(acoes.getFirst().caminho()).isEqualTo("/dp/descontos/" + desconto.getId() + "?acao=validar");

        desconto.validar(UUID.randomUUID(), new BigDecimal("90.00"));
        assertThat(detalhes.detalhesDe("desconto.solicitado", desconto.getId()).orElseThrow().acoes()).isEmpty();
    }

    @Test
    @DisplayName("Dar baixa só no aviso de validação, enquanto o termo está validado")
    void acaoDeBaixa() {
        desconto.validar(UUID.randomUUID(), new BigDecimal("90.00"));

        var acoes = detalhes.detalhesDe("desconto.validado", desconto.getId()).orElseThrow().acoes();

        assertThat(acoes).extracting(DetalhesNotificacao.Acao::rotulo).containsExactly("Dar baixa");
        assertThat(detalhes.detalhesDe("desconto.baixado", desconto.getId()).orElseThrow().acoes()).isEmpty();
    }
}
