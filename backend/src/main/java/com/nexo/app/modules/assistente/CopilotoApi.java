package com.nexo.app.modules.assistente;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * API pública do Copiloto para outros módulos.
 *
 * <p>O motor de automações usa três coisas daqui: uma chamada simples ao modelo (os
 * passos de IA), uma análise completa feita pelo agente em nome de um usuário — com as
 * mesmas ferramentas, permissões e relatórios PDF do chat — e consultas estruturadas à
 * camada semântica. Tudo recebe a identidade explicitamente: não há requisição HTTP
 * nem usuário logado quando uma rotina roda às 7h de segunda-feira.</p>
 */
public interface CopilotoApi {

    /** O provedor de IA está configurado e o assistente ligado. */
    boolean disponivel();

    /** Uma resposta do modelo, sem ferramentas. */
    String completar(String sistema, String usuario, double temperatura, int limiteSegundos);

    /** Há modelo com visão configurado para ler imagens, além do assistente ligado. */
    boolean visaoDisponivel();

    /** Nome do modelo com visão, para registro. Nulo quando não há. */
    String modeloVisao();

    /**
     * Uma resposta do modelo com visão sobre as imagens, sem ferramentas.
     *
     * @param comRaciocinio com raciocínio a leitura é mais precisa, mas alguns modelos gastam nele todo o limite de saída
     *                      e a resposta volta vazia; sem ele é mais rápida e sempre responde
     */
    String completarComImagens(String sistema, String usuario, List<Imagem> imagens, double temperatura, int limiteTokens,
                               boolean comRaciocinio);

    /**
     * Pede uma análise ao agente em nome do usuário e espera a resposta. A conversa fica
     * salva no histórico do usuário (é possível continuar perguntando depois).
     */
    Analise analisar(PedidoAnalise pedido);

    /** Fontes de dados que estas permissões podem consultar. */
    List<FonteDados> fontes(Set<String> permissoes);

    /** Consulta estruturada (sem SQL livre) com as permissões informadas. */
    ResultadoConsulta consultar(UUID usuarioId, Set<String> permissoes, Consulta consulta);

    record Imagem(String tipoConteudo, String base64) {
    }

    record PedidoAnalise(UUID usuarioId, Set<String> permissoes, String nomeUsuario, String titulo, String pergunta,
                         int limiteSegundos) {
    }

    record Arquivo(String nome, String tipoConteudo, byte[] conteudo) {
    }

    /** {@code texto} em markdown. */
    record Analise(String texto, UUID conversaId, List<Arquivo> arquivos, boolean concluida, String erro, long duracaoMs) {
    }

    record FonteDados(String id, String nome, String descricao, String dominio, String colunaData,
                      Map<String, String> colunas) {
    }

    record Consulta(String fonte, List<String> colunas, List<String> agruparPor, List<Metrica> metricas,
                    List<Filtro> filtros, String colunaPeriodo, LocalDate inicio, LocalDate fim, String ordenarPor,
                    boolean decrescente, Integer limite) {
    }

    record Metrica(String funcao, String coluna, String alias) {
    }

    record Filtro(String coluna, String operador, List<Object> valores) {
    }

    record ResultadoConsulta(List<String> colunas, List<Map<String, Object>> linhas, int total, boolean truncado) {
    }
}
