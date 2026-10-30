package com.nexo.app.modules.chamado.infrastructure.copiloto;

import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ProvedorCadastros;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.assistente.application.cadastro.TipoCampo;
import com.nexo.app.modules.chamado.application.dto.AbrirChamadoRequest;
import com.nexo.app.modules.chamado.application.dto.TopicoChamadoRequest;
import com.nexo.app.modules.chamado.application.usecases.ChamadoUseCases;
import com.nexo.app.modules.chamado.application.usecases.TopicoChamadoUseCases;
import org.springframework.stereotype.Component;

import java.util.List;

/** O que o Copiloto cadastra em Chamados: abertura de chamado e topicos de atendimento. */
@Component
public class CadastrosChamadoCopiloto implements ProvedorCadastros {

    private static final String MODULO = "Chamados";

    private final ChamadoUseCases chamados;
    private final TopicoChamadoUseCases topicos;

    public CadastrosChamadoCopiloto(ChamadoUseCases chamados, TopicoChamadoUseCases topicos) {
        this.chamados = chamados;
        this.topicos = topicos;
    }

    @Override
    public List<ReferenciaCadastro> referencias() {
        return List.of(
                new ReferenciaCadastro("topico_chamado", "Tópico",
                        "select id, nome as rotulo, descricao as detalhe from topicos_chamado where ativo"),
                new ReferenciaCadastro("equipe_atendimento", "Equipe de atendimento",
                        "select id, nome as rotulo, descricao as detalhe from equipes_atendimento_chamado where ativo"));
    }

    @Override
    public List<OperacaoCadastro<?>> operacoes() {
        return List.of(
                OperacaoCadastro.criar("chamado", AbrirChamadoRequest.class)
                        .modulo(MODULO).titulo("Novo chamado").registro("Chamado")
                        .descricao("Abre um chamado de suporte (topico, assunto curto, descricao do problema com o que "
                                + "aconteceu e o esperado, prioridade opcional).")
                        .autoridade("chamado:write")
                        .campo("topicoId", c -> c.rotulo("Tópico").referencia("topico_chamado"))
                        .campo("assunto", c -> c.rotulo("Assunto"))
                        .campo("descricao", c -> c.rotulo("Descrição").tipo(TipoCampo.TEXTO_LONGO))
                        .campo("prioridade", c -> c.rotulo("Prioridade"))
                        .identificadoPor("assunto")
                        .executor((r, id, usuario) -> chamados.abrir(r, usuario))
                        .destinoDetalhe("CHAMADO_MEU_DETALHE")
                        .exemplo("Abre um chamado: nao consigo acessar o sistema no celular desde ontem, aparece senha invalida")
                        .build(),
                OperacaoCadastro.criar("topico_chamado", TopicoChamadoRequest.class)
                        .modulo(MODULO).titulo("Novo tópico de chamado").registro("Tópico de chamado")
                        .descricao("Cadastra um topico de atendimento com equipe padrao e prazos (SLA em horas).")
                        .autoridade("chamado:manage")
                        .campo("equipePadraoId", c -> c.rotulo("Equipe de atendimento").referencia("equipe_atendimento"))
                        .campo("slaPrimeiraRespostaHoras", c -> c.rotulo("Primeira resposta em (horas)").padrao(() -> 4))
                        .campo("slaResolucaoHoras", c -> c.rotulo("Resolução em (horas)").padrao(() -> 24))
                        .campo("prazoReaberturaHoras", c -> c.rotulo("Reabertura em até (horas)").padrao(() -> 72))
                        .campo("pausarSlaAguardandoSolicitante", c -> c.rotulo("Pausar prazo aguardando o solicitante")
                                .padrao(() -> Boolean.TRUE))
                        .campo("ordemExibicao", c -> c.oculto())
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> topicos.criar(r))
                        .destino("CHAMADOS_TOPICOS")
                        .build());
    }
}
