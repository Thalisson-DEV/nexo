package com.nexo.app.modules.obras.domain;

import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Itens planejados de uma obra entregues a uma equipe num período, com as condições da intervenção e o valor
 * dos serviços congelado na confirmação.
 *
 * <p>Toda mudança depois de criada devolve o evento do histórico. Item com execução nunca volta a pendente:
 * cancelar mantém esse item na programação, e excluir ou retirar é recusado.</p>
 */
@Entity
@Table(name = "programacoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Programacao extends BaseEntity {

    /** Dados exigidos para confirmar a programação. */
    public record Condicoes(String descricao, Boolean linhaViva, Boolean desligamento, Boolean finalizaObra,
                            TurnoProgramacao turno, SituacaoIntervencao situacaoIntervencao, String observacao) {
    }

    @Column(name = "numero", nullable = false, unique = true, updatable = false)
    private Long numero;

    @Column(name = "id_obra", nullable = false, updatable = false)
    private UUID idObra;

    @Column(name = "id_equipe", nullable = false)
    private UUID idEquipe;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim", nullable = false)
    private LocalDate dataFim;

    @Column(name = "descricao", nullable = false, length = 500)
    private String descricao;

    @Column(name = "linha_viva", nullable = false)
    private boolean linhaViva;

    @Column(name = "desligamento", nullable = false)
    private boolean desligamento;

    @Column(name = "finaliza_obra", nullable = false)
    private boolean finalizaObra;

    @Enumerated(EnumType.STRING)
    @Column(name = "turno", nullable = false, length = 20)
    private TurnoProgramacao turno;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_intervencao", nullable = false, length = 20)
    private SituacaoIntervencao situacaoIntervencao;

    @Column(name = "observacao", length = 1000)
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusProgramacao status = StatusProgramacao.PROGRAMADA;

    @OneToMany(mappedBy = "programacao", cascade = CascadeType.ALL)
    private List<ProgramacaoItem> itens = new ArrayList<>();

    public Programacao(long numero, UUID idObra, UUID idEquipe, LocalDate inicio, LocalDate fim, Condicoes condicoes) {
        this.numero = numero;
        this.idObra = Objects.requireNonNull(idObra);
        definirEquipeEPeriodo(idEquipe, inicio, fim);
        aplicar(condicoes);
    }

    /** Coloca o item na programação com o valor unitário vigente no início (nulo para material ou sem valor). */
    public void incluirItem(ItemPlanejado item, BigDecimal valorUnitario) {
        item.programar();
        itens.add(new ProgramacaoItem(this, item, valorUnitario));
    }

    public List<ProgramacaoItem> getItensAtivos() {
        return itens.stream().filter(ProgramacaoItem::isAtivo).toList();
    }

    /** Soma congelada dos serviços. Na programação cancelada, conta o que ela tinha ao ser cancelada. */
    public BigDecimal valorTotal() {
        return (status == StatusProgramacao.CANCELADA ? itens : getItensAtivos()).stream()
                .map(ProgramacaoItem::getValorTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public SituacaoProgramacao situacaoEm(LocalDate hoje) {
        if (status == StatusProgramacao.CANCELADA) {
            return SituacaoProgramacao.CANCELADA;
        }
        List<ItemPlanejado> planejados = getItensAtivos().stream().map(ProgramacaoItem::getItemPlanejado).toList();
        if (!planejados.isEmpty() && planejados.stream().allMatch(ItemPlanejado::concluido)) {
            return SituacaoProgramacao.CONCLUIDA;
        }
        if (dataFim.isBefore(hoje)) {
            return SituacaoProgramacao.ATRASADA;
        }
        return planejados.stream().anyMatch(ItemPlanejado::temExecucao)
                ? SituacaoProgramacao.EM_ANDAMENTO
                : SituacaoProgramacao.PROGRAMADA;
    }

    /** @return o evento, ou vazio quando nada mudou */
    public Optional<EventoProgramacao> editar(Condicoes novas) {
        validarAberta("editar");
        List<String> mudancas = mudancas(novas);
        EventoProgramacao.Origem origem = origem();
        aplicar(novas);
        return mudancas.isEmpty()
                ? Optional.empty()
                : Optional.of(new EventoProgramacao(this, TipoEventoProgramacao.EDITADA, origem, String.join("; ", mudancas)));
    }

    /** Troca equipe ou período. O valor congelado na confirmação não muda. */
    public EventoProgramacao reprogramar(UUID novaEquipe, LocalDate inicio, LocalDate fim, String motivo) {
        validarAberta("reprogramar");
        String texto = motivoObrigatorio(motivo, "a reprogramação");
        if (novaEquipe != null && novaEquipe.equals(idEquipe) && Objects.equals(inicio, dataInicio) && Objects.equals(fim, dataFim)) {
            throw new InvarianteVioladaException("Informe outra equipe ou outro período para reprogramar.");
        }
        EventoProgramacao.Origem origem = origem();
        definirEquipeEPeriodo(novaEquipe, inicio, fim);
        return new EventoProgramacao(this, TipoEventoProgramacao.REPROGRAMADA, origem, texto);
    }

    public EventoProgramacao cancelar(String motivo) {
        validarAberta("cancelar");
        String texto = motivoObrigatorio(motivo, "o cancelamento");
        EventoProgramacao.Origem origem = origem();
        getItensAtivos().stream()
                .filter(vinculo -> !vinculo.getItemPlanejado().temExecucao())
                .forEach(this::devolver);
        this.status = StatusProgramacao.CANCELADA;
        return new EventoProgramacao(this, TipoEventoProgramacao.CANCELADA, origem, texto);
    }

    /** Excluir apaga o valor congelado, então programação com execução só pode ser cancelada. */
    public EventoProgramacao excluir(String observacao) {
        boolean comExecucao = getItensAtivos().stream().anyMatch(v -> v.getItemPlanejado().temExecucao());
        if (comExecucao) {
            throw new InvarianteVioladaException("A programação #" + numero + " já tem execução. Cancele em vez de excluir.");
        }
        EventoProgramacao.Origem origem = origem();
        getItensAtivos().forEach(this::devolver);
        desativar();
        return new EventoProgramacao(this, TipoEventoProgramacao.EXCLUIDA, origem, observacao);
    }

    /**
     * Retira um item, que volta a pendente. O material acompanha o serviço: sem serviço do kit na programação,
     * os materiais do kit saem junto. Sem nenhum item, a programação é excluída.
     */
    public EventoProgramacao retirarItem(UUID idItemPlanejado) {
        validarAberta("alterar");
        ProgramacaoItem vinculo = getItensAtivos().stream()
                .filter(v -> Objects.equals(v.getItemPlanejado().getId(), idItemPlanejado))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("O item não está na programação #" + numero + "."));
        ItemPlanejado item = vinculo.getItemPlanejado();
        if (item.temExecucao()) {
            throw new InvarianteVioladaException("O item " + item.getDescricao() + " já tem execução e não pode ser retirado.");
        }
        EventoProgramacao.Origem origem = origem();
        devolver(vinculo);
        if (item.getTipo() == TipoItemKit.SERVICO) {
            retirarMateriaisSemServico(item.getKitAplicado());
        }
        String texto = "Item retirado: " + item.getDescricao();
        if (getItensAtivos().isEmpty()) {
            desativar();
            return new EventoProgramacao(this, TipoEventoProgramacao.EXCLUIDA, origem, texto);
        }
        return new EventoProgramacao(this, TipoEventoProgramacao.EDITADA, origem, texto);
    }

    /** Registra o que a equipe executou de um item desta programação, com o valor congelado nela. */
    public ApontamentoExecucao apontar(UUID idItemPlanejado, BigDecimal quantidade, LocalDate data, UUID idTurno, String justificativa) {
        validarAberta("apontar");
        if (data == null) {
            throw new InvarianteVioladaException("Informe a data do apontamento.");
        }
        ProgramacaoItem vinculo = getItensAtivos().stream()
                .filter(v -> Objects.equals(v.getItemPlanejado().getId(), idItemPlanejado))
                .findFirst()
                .orElseThrow(() -> new InvarianteVioladaException("O item não está na programação #" + numero + "."));
        BigDecimal excedente = vinculo.getItemPlanejado().apontar(quantidade);
        return new ApontamentoExecucao(this, vinculo, idTurno, data, quantidade, excedente, justificativa);
    }

    /**
     * Desfaz o apontamento. Na programação cancelada, o item fica nela só por ter execução: sem nenhuma, ele volta
     * a pendente como os demais itens do cancelamento.
     */
    public void estornar(ApontamentoExecucao apontamento) {
        ItemPlanejado item = apontamento.getItemPlanejado();
        item.estornar(apontamento.getQuantidade());
        apontamento.desativar();
        if (status == StatusProgramacao.CANCELADA && !item.temExecucao()) {
            getItensAtivos().stream()
                    .filter(v -> v.getItemPlanejado().equals(item))
                    .findFirst()
                    .ifPresent(this::devolver);
        }
    }

    private void retirarMateriaisSemServico(KitAplicado kit) {
        List<ProgramacaoItem> doKit = getItensAtivos().stream()
                .filter(v -> v.getItemPlanejado().getKitAplicado().equals(kit))
                .toList();
        if (doKit.stream().anyMatch(v -> v.getItemPlanejado().getTipo() == TipoItemKit.SERVICO)) {
            return;
        }
        doKit.stream().filter(v -> !v.getItemPlanejado().temExecucao()).forEach(this::devolver);
    }

    private void devolver(ProgramacaoItem vinculo) {
        vinculo.getItemPlanejado().desprogramar();
        vinculo.desativar();
    }

    private EventoProgramacao.Origem origem() {
        return new EventoProgramacao.Origem(idEquipe, dataInicio, dataFim);
    }

    private void validarAberta(String acao) {
        if (status == StatusProgramacao.CANCELADA || !isAtivo()) {
            throw new InvarianteVioladaException("Não é possível " + acao + " a programação #" + numero + ": ela foi cancelada ou excluída.");
        }
    }

    private void definirEquipeEPeriodo(UUID equipe, LocalDate inicio, LocalDate fim) {
        if (equipe == null) {
            throw new InvarianteVioladaException("Informe a equipe da programação.");
        }
        if (inicio == null || fim == null) {
            throw new InvarianteVioladaException("Informe o início e o fim da programação.");
        }
        if (fim.isBefore(inicio)) {
            throw new InvarianteVioladaException("O fim da programação não pode ser antes do início.");
        }
        this.idEquipe = equipe;
        this.dataInicio = inicio;
        this.dataFim = fim;
    }

    private void aplicar(Condicoes c) {
        if (c == null || c.descricao() == null || c.descricao().isBlank()) {
            throw new InvarianteVioladaException("Informe a descrição do planejamento.");
        }
        if (c.linhaViva() == null) {
            throw new InvarianteVioladaException("Informe se a programação é em linha viva.");
        }
        if (c.desligamento() == null) {
            throw new InvarianteVioladaException("Informe se a programação tem desligamento.");
        }
        if (c.finalizaObra() == null) {
            throw new InvarianteVioladaException("Informe se a programação finaliza a obra.");
        }
        if (c.turno() == null) {
            throw new InvarianteVioladaException("Informe o turno da programação.");
        }
        if (c.situacaoIntervencao() == null) {
            throw new InvarianteVioladaException("Informe a situação da intervenção.");
        }
        this.descricao = c.descricao().trim();
        this.linhaViva = c.linhaViva();
        this.desligamento = c.desligamento();
        this.finalizaObra = c.finalizaObra();
        this.turno = c.turno();
        this.situacaoIntervencao = c.situacaoIntervencao();
        this.observacao = c.observacao() != null && !c.observacao().isBlank() ? c.observacao().trim() : null;
    }

    private List<String> mudancas(Condicoes n) {
        List<String> lista = new ArrayList<>();
        comparar(lista, "Descrição", descricao, n.descricao() != null ? n.descricao().trim() : null);
        comparar(lista, "Linha viva", simNao(linhaViva), simNao(n.linhaViva()));
        comparar(lista, "Desligamento", simNao(desligamento), simNao(n.desligamento()));
        comparar(lista, "Finaliza obra", simNao(finalizaObra), simNao(n.finalizaObra()));
        comparar(lista, "Turno", rotulo(turno), rotulo(n.turno()));
        comparar(lista, "Situação da intervenção", rotulo(situacaoIntervencao), rotulo(n.situacaoIntervencao()));
        String novaObservacao = n.observacao() != null && !n.observacao().isBlank() ? n.observacao().trim() : null;
        comparar(lista, "Observação", observacao, novaObservacao);
        return lista;
    }

    private static void comparar(List<String> lista, String campo, Object antes, Object depois) {
        if (!Objects.equals(antes, depois)) {
            lista.add(campo + ": " + (antes != null ? antes : "vazio") + " → " + (depois != null ? depois : "vazio"));
        }
    }

    private static String rotulo(TurnoProgramacao turno) {
        if (turno == null) {
            return null;
        }
        return switch (turno) {
            case DIA_TODO -> "Dia todo";
            case MANHA -> "Manhã";
            case TARDE -> "Tarde";
        };
    }

    private static String rotulo(SituacaoIntervencao situacao) {
        if (situacao == null) {
            return null;
        }
        return switch (situacao) {
            case CRIADO -> "Criado";
            case PENDENTE -> "Pendente";
            case NAO_SE_APLICA -> "Não se aplica";
        };
    }

    private static String simNao(Boolean valor) {
        return valor == null ? null : valor ? "Sim" : "Não";
    }

    private static String motivoObrigatorio(String motivo, String acao) {
        if (motivo == null || motivo.isBlank()) {
            throw new InvarianteVioladaException("Informe a observação para " + acao + ".");
        }
        return motivo.trim();
    }
}
