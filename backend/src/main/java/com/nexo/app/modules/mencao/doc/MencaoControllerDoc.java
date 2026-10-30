package com.nexo.app.modules.mencao.doc;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.nexo.app.modules.mencao.UsuarioMencionavel;
import com.nexo.app.modules.mencao.domain.OrigemMencao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Marcações", description = """
        Apoio ao @ nos textos do sistema. As marcações em si são reconhecidas no
        servidor a partir do próprio texto enviado — não existe endpoint para
        "marcar alguém" avulso, porque uma marcação sem a frase que a motivou não
        diz nada a quem recebe.
        """)
@SecurityRequirement(name = "bearerAuth")
public interface MencaoControllerDoc {

    @Operation(summary = "Sugerir usuários para marcação",
            description = """
                    Alimenta o autocomplete disparado pelo @. Devolve no máximo 10 logins.
                    Quando o texto tem circulação restrita (comentário interno de chamado),
                    informe visibilidadeRestrita=true: a lista já vem sem quem não poderia
                    ler o texto, evitando que a marcação seja recusada só no envio.
                    """)
    ResponseEntity<List<UsuarioMencionavel>> sugerir(
            @Parameter(description = "Trecho digitado após o @; o próprio @ é tolerado")
            String termo,
            @Parameter(description = "Onde o texto está sendo escrito")
            OrigemMencao origem,
            @Parameter(description = "Marque como true para textos que não circulam para todos")
            boolean visibilidadeRestrita);
}
