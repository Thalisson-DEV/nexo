package com.nexo.app.modules.automacao.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.nexo.app.modules.automacao.domain.definicao.DefinicaoAutomacao;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Uma automação: o que fazer quando um gatilho acontece, como uma árvore de passos.
 *
 * <p>A definição em si mora nas versões ({@link VersaoAutomacao}); aqui ficam a
 * identidade, o ciclo de vida e cópias do gatilho da versão atual, que o tick e o
 * ouvinte de eventos consultam sem abrir o JSON. Nada roda antes de uma pessoa ativar:
 * a ativação exige uma definição sem erros (o verificador decide isso no caso de uso).</p>
 */
@Entity
@Table(name = "automacoes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Automacao extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "descricao", length = 500)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoAutomacao tipo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusAutomacao status = StatusAutomacao.RASCUNHO;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 30, updatable = false)
    private OrigemAutomacao origem;

    /** Em nome de quem a automação lê dados e pede análises ao Copiloto. */
    @Column(name = "id_dono", nullable = false)
    private UUID idDono;

    @Column(name = "numero_versao", nullable = false)
    private int numeroVersao = 1;

    @Column(name = "tipo_gatilho", nullable = false, length = 64)
    private String tipoGatilho;

    @Column(name = "chave_evento", length = 80)
    private String chaveEvento;

    @Column(name = "ultimo_disparo")
    private OffsetDateTime ultimoDisparo;

    @Column(name = "proxima_verificacao")
    private OffsetDateTime proximaVerificacao;

    @Column(name = "ativada_em")
    private OffsetDateTime ativadaEm;

    @Column(name = "id_regra_origem", updatable = false)
    private UUID idRegraOrigem;

    public Automacao(String nome, String descricao, TipoAutomacao tipo, OrigemAutomacao origem, UUID idDono,
                     String tipoGatilho, String chaveEvento, UUID idRegraOrigem) {
        if (idDono == null) throw new InvarianteVioladaException("A automação precisa de um responsável.");
        this.origem = origem == null ? OrigemAutomacao.USUARIO : origem;
        this.idDono = idDono;
        this.idRegraOrigem = idRegraOrigem;
        renomear(nome, descricao, tipo);
        apontarGatilho(tipoGatilho, chaveEvento);
    }

    public void renomear(String nome, String descricao, TipoAutomacao tipo) {
        if (!StringUtils.hasText(nome)) throw new InvarianteVioladaException("O nome da automação é obrigatório.");
        String n = nome.strip();
        if (n.length() > 120) throw new InvarianteVioladaException("O nome da automação excede 120 caracteres.");
        String d = descricao == null ? null : descricao.strip();
        if (d != null && d.length() > 500) d = d.substring(0, 500);
        this.nome = n;
        this.descricao = StringUtils.hasText(d) ? d : null;
        this.tipo = tipo == null ? TipoAutomacao.GERAL : tipo;
    }

    /** Uma nova versão salva: o gatilho copiado acompanha. */
    public void registrarVersao(int numero, DefinicaoAutomacao definicao, String chaveEvento) {
        if (status == StatusAutomacao.ARQUIVADA) throw new InvarianteVioladaException("Automação arquivada não pode ser alterada.");
        this.numeroVersao = numero;
        boolean mudouGatilho = !definicao.gatilho().tipo().equals(this.tipoGatilho);
        apontarGatilho(definicao.gatilho().tipo(), chaveEvento);
        if (mudouGatilho) {
            this.ultimoDisparo = null;
            this.proximaVerificacao = null;
        }
    }

    private void apontarGatilho(String tipoGatilho, String chaveEvento) {
        if (!StringUtils.hasText(tipoGatilho)) throw new InvarianteVioladaException("Escolha o gatilho da automação.");
        this.tipoGatilho = tipoGatilho;
        this.chaveEvento = chaveEvento;
    }

    /**
     * Liga a automação. Os problemas vêm do verificador (definição) e do tipo; a lista
     * vazia é a condição para ativar.
     */
    public void ativar(List<String> problemas, OffsetDateTime agora) {
        if (status == StatusAutomacao.ARQUIVADA) throw new InvarianteVioladaException("Automação arquivada não pode ser ativada.");
        if (problemas != null && !problemas.isEmpty()) {
            throw new InvarianteVioladaException("Corrija antes de ativar: " + String.join("; ", problemas.subList(0, Math.min(5, problemas.size()))));
        }
        if (status != StatusAutomacao.ATIVA) {
            this.status = StatusAutomacao.ATIVA;
            this.ativadaEm = agora;
            this.ultimoDisparo = null;
            this.proximaVerificacao = null;
        }
    }

    public void pausar() {
        if (status == StatusAutomacao.ARQUIVADA) throw new InvarianteVioladaException("Automação arquivada não pode ser pausada.");
        this.status = StatusAutomacao.PAUSADA;
    }

    public void arquivar() {
        this.status = StatusAutomacao.ARQUIVADA;
        desativar();
    }

    public void transferirPara(UUID novoDono) {
        if (novoDono == null) throw new InvarianteVioladaException("Informe o novo responsável.");
        this.idDono = novoDono;
    }

    public boolean isAtiva() {
        return status == StatusAutomacao.ATIVA && isAtivo();
    }

    public void registrarDisparo(OffsetDateTime momento) {
        this.ultimoDisparo = momento;
    }

    public void agendarVerificacao(OffsetDateTime momento) {
        this.proximaVerificacao = momento;
    }
}
