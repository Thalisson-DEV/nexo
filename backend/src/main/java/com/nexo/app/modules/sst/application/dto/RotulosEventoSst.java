package com.nexo.app.modules.sst.application.dto;

/**
 * Os nomes das entradas que o evento apenas <b>referencia</b> — classificação, local e
 * natureza da lesão, agente causador e processo de negócio.
 *
 * <p>Existe porque esses cinco campos são a exceção deliberada à regra de snapshot: o
 * evento guarda só o id, e o texto é resolvido na leitura para que corrigir um nome no
 * catálogo corrija em todo evento que aponta para ele. Passar os rótulos ao mapper como
 * uma segunda fonte evita reconstruir a resposta inteira campo a campo depois.</p>
 */
public record RotulosEventoSst(
        String classificacaoNome,
        String localLesaoNome,
        String naturezaLesaoNome,
        String agenteCausadorNome,
        String processoNome
) {

    public static final RotulosEventoSst VAZIO = new RotulosEventoSst(null, null, null, null, null);
}
