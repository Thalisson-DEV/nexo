package com.nexo.app.modules.automacao.application.motor;

import java.time.OffsetDateTime;
import java.util.Map;

import com.nexo.app.modules.automacao.domain.StatusEtapa;

/**
 * O estado de um passo numa execução (um por nó e iteração de "Para cada"). É o ponto
 * de retomada: cada mudança é gravada antes de seguir.
 */
public final class RegistroEtapa {

    public final String noId;
    public final String iteracao;
    public final String tipo;
    public StatusEtapa status;
    public int tentativa;
    public Map<String, Object> entradas;
    public Map<String, Object> saidas;
    public String erro;
    public OffsetDateTime inicio;
    public OffsetDateTime fim;
    /** Próxima tentativa, fim da espera ou prazo da aprovação. */
    public OffsetDateTime proximaEm;

    public RegistroEtapa(String noId, String iteracao, String tipo, StatusEtapa status, OffsetDateTime inicio) {
        this.noId = noId;
        this.iteracao = iteracao;
        this.tipo = tipo;
        this.status = status;
        this.inicio = inicio;
    }

    public RegistroEtapa copia() {
        RegistroEtapa c = new RegistroEtapa(noId, iteracao, tipo, status, inicio);
        c.tentativa = tentativa;
        c.entradas = entradas;
        c.saidas = saidas;
        c.erro = erro;
        c.fim = fim;
        c.proximaEm = proximaEm;
        return c;
    }

    public String chave() {
        return noId + "|" + iteracao;
    }
}
