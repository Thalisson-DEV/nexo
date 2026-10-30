package com.nexo.app.modules.iam.infrastructure.copiloto;

import com.nexo.app.modules.assistente.application.cadastro.MotorCadastro;
import com.nexo.app.modules.assistente.application.cadastro.OperacaoCadastro;
import com.nexo.app.modules.assistente.application.cadastro.ProvedorCadastros;
import com.nexo.app.modules.assistente.application.cadastro.ReferenciaCadastro;
import com.nexo.app.modules.iam.application.dto.PerfilAcessoRequest;
import com.nexo.app.modules.iam.application.dto.UsuarioRequest;
import com.nexo.app.modules.iam.application.dto.UsuarioUpdateRequest;
import com.nexo.app.modules.iam.application.usecases.PerfilAcessoUseCases;
import com.nexo.app.modules.iam.application.usecases.UsuarioUseCases;
import org.springframework.stereotype.Component;

import java.util.List;

/** Usuários e perfis de acesso pelo Copiloto. */
@Component
public class CadastrosIamCopiloto implements ProvedorCadastros {

    private static final String MODULO = "Acessos";

    private final UsuarioUseCases usuarios;
    private final PerfilAcessoUseCases perfis;

    public CadastrosIamCopiloto(UsuarioUseCases usuarios, PerfilAcessoUseCases perfis) {
        this.usuarios = usuarios;
        this.perfis = perfis;
    }

    @Override
    public List<ReferenciaCadastro> referencias() {
        return List.of(
                new ReferenciaCadastro("perfil", "Perfil de acesso", "select id, nome as rotulo, null as detalhe from perfis_acesso where ativo"),
                new ReferenciaCadastro("usuario", "Usuário",
                        "select id, login as rotulo, email as detalhe from usuarios where ativo"));
    }

    @Override
    public List<OperacaoCadastro<?>> operacoes() {
        return List.of(
                OperacaoCadastro.criar("usuario", UsuarioRequest.class)
                        .modulo(MODULO).titulo("Novo usuário").registro("Usuário")
                        .descricao("Cria o acesso de uma pessoa ao Nexo (login, perfil, tipo ADMINISTRATIVO ou CAMPO), "
                                + "opcionalmente ligado a um colaborador. Equipes de campo costumam usar o login com prefixo + os 6 "
                                + "ultimos digitos do CPF. Sem senha informada, o sistema gera uma provisoria e mostra so ao concluir.")
                        .autoridade("iam:usuario:write")
                        .campo("idPerfil", c -> c.rotulo("Perfil de acesso").referencia("perfil"))
                        .campo("idColaborador", c -> c.rotulo("Colaborador").referencia("colaborador"))
                        .campo("tipo", c -> c.rotulo("Tipo de usuário"))
                        .campo("senha", c -> c.rotulo("Senha").obrigatorio(false).padrao(MotorCadastro.senhaProvisoria()))
                        .identificadoPor("login")
                        .executor((r, id, usuario) -> usuarios.criar(r))
                        .destino("IAM_USUARIOS")
                        .exemplo("Cria o usuario USR123456 para o colaborador Joao Silva, perfil Campo, tipo campo")
                        .build(),
                OperacaoCadastro.editar("usuario", UsuarioUpdateRequest.class)
                        .modulo(MODULO).titulo("Editar usuário").registro("Usuário")
                        .descricao("Muda perfil, tipo, e-mail, telefone ou login de um usuario. Para trocar a senha, informe a nova.")
                        .autoridade("iam:usuario:write")
                        .carregador("usuario", usuarios::buscarPorId)
                        .campo("idPerfil", c -> c.rotulo("Perfil de acesso").referencia("perfil"))
                        .campo("tipo", c -> c.rotulo("Tipo de usuário"))
                        .campo("senha", c -> c.rotulo("Nova senha"))
                        .identificadoPor("login")
                        .executor((r, id, usuario) -> usuarios.atualizar(id, r))
                        .destino("IAM_USUARIOS")
                        .semLote()
                        .build(),
                OperacaoCadastro.criar("perfil_acesso", PerfilAcessoRequest.class)
                        .modulo(MODULO).titulo("Novo perfil de acesso").registro("Perfil de acesso")
                        .descricao("Cria um perfil de acesso vazio; as permissoes sao marcadas depois na tela de perfis.")
                        .autoridade("iam:perfil:write")
                        .identificadoPor("nome")
                        .executor((r, id, usuario) -> perfis.criar(r))
                        .destino("IAM_PERFIS_ACESSO")
                        .build());
    }
}
