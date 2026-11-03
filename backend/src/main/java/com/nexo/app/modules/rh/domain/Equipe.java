package com.nexo.app.modules.rh.domain;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.nexo.app.shared.domain.BaseEntity;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa uma unidade operacional (equipe) que executa serviços em campo.
 */
@Entity
@Table(name = "equipes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Equipe extends BaseEntity {

    @NotBlank(message = "O código da equipe é obrigatório")
    @Size(min = 3, max = 50, message = "O código da equipe deve ter entre 3 e 50 caracteres")
    @Column(name = "cod_equipe", nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(name = "id_usuario", unique = true)
    private UUID idUsuario;

    @Column(name = "id_contrato")
    private UUID idContrato;

    @NotNull(message = "O tipo de equipe é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tipo_equipe", nullable = false)
    private TipoEquipe tipoEquipe;

    @NotNull(message = "O veiculo da equipe é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_veiculo_vinculado")
    private Veiculo veiculo;

    @NotNull(message = "O coordenador da equipe é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_coordenador", nullable = false)
    private Coordenador coordenador;

    @NotNull(message = "O supervisor da equipe é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_supervisor", nullable = false)
    private Supervisor supervisor;

    @NotNull(message = "A base operacional é obrigatória")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_base_operacional", nullable = false)
    private BaseOperacional baseOperacional;

    @NotNull(message = "O processo é obrigatório")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_processo", nullable = false)
    private ProcessoNegocio processo;

    @JsonManagedReference
    @OneToMany(mappedBy = "equipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EquipeMembro> membros = new ArrayList<>();

    /** Ajuste da jornada do tipo de equipe; nulo usa a do tipo. */
    @Column(name = "jornada_minutos")
    private Integer jornadaMinutos;

    /** Ajuste do tempo de plataforma do tipo de equipe; nulo usa o do tipo. */
    @Column(name = "plataforma_minutos")
    private Integer plataformaMinutos;

    public Equipe(String codigo, TipoEquipe tipoEquipe, BaseOperacional baseOperacional, Veiculo veiculo, Coordenador coordenador, Supervisor supervisor, ProcessoNegocio processo) {
        validarCodigo(codigo);
        this.codigo = codigo.toUpperCase().trim();
        this.tipoEquipe = Objects.requireNonNull(tipoEquipe, "Tipo de equipe é obrigatório");
        this.baseOperacional = Objects.requireNonNull(baseOperacional, "Base operacional é obrigatória");
        this.veiculo = Objects.requireNonNull(veiculo, "Veículo é obrigatório");
        this.processo = Objects.requireNonNull(processo, "Processo é obrigatorio");

        vincularCoordenador(coordenador);
        vincularSupervisor(supervisor);
        
        validarEstadoAtivo(tipoEquipe, baseOperacional, veiculo, coordenador, supervisor, processo);
    }

    /**
     * Aloca um colaborador na equipe registrando a função.
     * Se o colaborador for o RESPONSAVEL, ele deve obrigatoriamente possuir um usuário vinculado.
     */
    public void alocarMembro(Colaborador colaborador, EquipeMembro.FuncaoMembro funcao) {
        if (!isAtivo()) {
            throw new IllegalArgumentException("Registro desativado.");
        }

        if (!colaborador.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível alocar um colaborador desativado.");
        }

        // Validação: Membro não pode ser o colaborador que atua como coordenador ou supervisor da própria equipe
        if (this.coordenador != null && this.coordenador.getColaborador() != null && colaborador.getId().equals(this.coordenador.getColaborador().getId())) {
            throw new InvarianteVioladaException("O coordenador da equipe não pode ser alocado como seu próprio membro.");
        }

        if (this.supervisor != null && this.supervisor.getColaborador() != null && colaborador.getId().equals(this.supervisor.getColaborador().getId())) {
            throw new InvarianteVioladaException("O supervisor da equipe não pode ser alocado como seu próprio membro.");
        }
        
        // Regra de Negócio: Responsável deve ter usuário para prover acesso à equipe
        if (funcao == EquipeMembro.FuncaoMembro.RESPONSAVEL) {
            if (colaborador.getIdUsuario() == null) {
                throw new InvarianteVioladaException("O colaborador deve possuir um usuário vinculado para ser definido como responsável da equipe.");
            }

            // Verifica se já existe um responsável ativo ao tentar alocar um novo responsável
            this.membros.stream()
                .filter(m -> m.isVinculoAtivo() && m.getFuncao() == EquipeMembro.FuncaoMembro.RESPONSAVEL)
                .findFirst()
                .ifPresent(m -> {
                    throw new InvarianteVioladaException("A equipe já possui um responsável ativo. Encerre o vínculo atual antes de alocar um novo.");
                });
            
            // Vincula o usuário do responsável à equipe
            this.idUsuario = colaborador.getIdUsuario();
        }

        // Verifica se o colaborador já está alocado nesta equipe de forma ativa
        this.membros.stream()
            .filter(m -> m.isVinculoAtivo() && m.getColaborador().getId().equals(colaborador.getId()))
            .findFirst()
            .ifPresent(m -> {
                throw new InvarianteVioladaException("O colaborador já é um membro ativo desta equipe.");
            });

        this.membros.add(new EquipeMembro(this, colaborador, funcao));
    }

    /**
     * Encerra o vínculo ativo de um colaborador específico com a equipe.
     * Se o colaborador for o responsável atual, o idUsuario da equipe é removido.
     */
    public void encerrarVinculoMembro(Colaborador colaborador) {
        if (!isAtivo()) {
            throw new IllegalArgumentException("Registro desativado.");
        }

        this.membros.stream()
            .filter(m -> m.isVinculoAtivo() && m.getColaborador().getId().equals(colaborador.getId()))
            .findFirst()
            .ifPresentOrElse(
                m -> {
                    if (m.getFuncao() == EquipeMembro.FuncaoMembro.RESPONSAVEL) {
                        this.idUsuario = null;
                    }
                    m.encerrarVinculo();
                },
                () -> { throw new InvarianteVioladaException("Colaborador não encontrado como membro ativo desta equipe."); }
            );
    }

    /**
     * Transfere a responsabilidade da equipe para outro colaborador que já é membro.
     * O novo responsável deve obrigatoriamente possuir um usuário vinculado.
     */
    public void transferirResponsabilidade(Colaborador novoResponsavel) {
        if (!isAtivo()) {
            throw new IllegalArgumentException("Registro desativado.");
        }

        if (!novoResponsavel.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível transferir a responsabilidade para um colaborador desativado.");
        }

        if (novoResponsavel.getIdUsuario() == null) {
            throw new InvarianteVioladaException("O novo responsável deve possuir um usuário vinculado para prover acesso à equipe.");
        }

        // Verifica se o novo responsável já é membro da equipe
        EquipeMembro vinculoNovoResp = this.membros.stream()
            .filter(m -> m.isVinculoAtivo() && m.getColaborador().getId().equals(novoResponsavel.getId()))
            .findFirst()
            .orElseThrow(() -> new InvarianteVioladaException("O novo responsável deve ser um membro ativo desta equipe."));

        // Se o colaborador já é o responsável atual, não faz nada
        if (vinculoNovoResp.getFuncao() == EquipeMembro.FuncaoMembro.RESPONSAVEL) {
            return;
        }

        // Busca o responsável atual para remover sua função de liderança
        this.membros.stream()
            .filter(m -> m.isVinculoAtivo() && m.getFuncao() == EquipeMembro.FuncaoMembro.RESPONSAVEL)
            .findFirst()
            .ifPresent(m -> {
                // Encerra vínculo como responsável e o torna parceiro
                m.encerrarVinculo();
                this.membros.add(new EquipeMembro(this, m.getColaborador(), EquipeMembro.FuncaoMembro.PARCEIRO));
            });

        // Atualiza a equipe para o usuário do novo responsável
        this.idUsuario = novoResponsavel.getIdUsuario();

        // Encerra o vínculo atual (PARCEIRO) do novo responsável e cria um novo como RESPONSAVEL
        vinculoNovoResp.encerrarVinculo();
        this.membros.add(new EquipeMembro(this, novoResponsavel, EquipeMembro.FuncaoMembro.RESPONSAVEL));
    }

    public void vincularContrato(UUID idContrato) {
        validarStatus();
        this.idContrato = Objects.requireNonNull(idContrato, "ID do contrato é obrigatório");
    }

    public void desvincularContrato() {
        validarStatus();
        this.idContrato = null;
    }

    /**
     * Indica se o usuário informado é o responsável (líder) desta equipe.
     */
    public boolean isResponsavel(UUID idUsuario) {
        return idUsuario != null && idUsuario.equals(this.idUsuario);
    }

    /** Ajusta jornada e plataforma só desta equipe. Nulo volta a usar o valor do tipo de equipe. */
    public void ajustarJornada(Integer jornada, Integer plataforma) {
        validarJornada(jornada != null ? jornada : tipoEquipe.getJornadaMinutos(),
                plataforma != null ? plataforma : tipoEquipe.getPlataformaMinutos());
        this.jornadaMinutos = jornada;
        this.plataformaMinutos = plataforma;
    }

    static void validarJornada(int jornada, int plataforma) {
        if (jornada <= 0 || jornada > 1440) {
            throw new InvarianteVioladaException("A jornada deve ficar entre 1 minuto e 24 horas.");
        }
        if (plataforma < 0 || plataforma >= jornada) {
            throw new InvarianteVioladaException("O tempo de plataforma deve ser menor que a jornada.");
        }
    }

    public void vincularUsuario(UUID idUsuario) {
        this.idUsuario = idUsuario;
    }

    public void atualizarTipoEquipe(TipoEquipe novoTipo) {
        this.tipoEquipe = Objects.requireNonNull(novoTipo, "Novo tipo de equipe não pode ser nulo");
        validarEstadoAtivo(novoTipo);
    }

    public void atualizarBaseOperacional(BaseOperacional novaBase) {
        this.baseOperacional = Objects.requireNonNull(novaBase, "Nova base operacional não pode ser nula");
        validarEstadoAtivo(novaBase);
    }

    public void vincularVeiculo(Veiculo veiculo) {
        if (veiculo != null && !veiculo.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um veículo desativado");
        }
        this.veiculo = veiculo;
    }

    public void vincularCoordenador(Coordenador coordenador) {
        Objects.requireNonNull(coordenador, "Coordenador é obrigatório");
        if (!coordenador.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um coordenador desativado");
        }
        // Verifica se o colaborador por trás do coordenador já é um membro ativo da equipe
        if (coordenador.getColaborador() != null) {
            this.membros.stream()
                .filter(m -> m.isVinculoAtivo() && m.getColaborador().getId().equals(coordenador.getColaborador().getId()))
                .findFirst()
                .ifPresent(m -> {
                    throw new InvarianteVioladaException("O colaborador associado ao coordenador já é um membro ativo desta equipe.");
                });
        }
        this.coordenador = coordenador;
    }

    public void vincularSupervisor(Supervisor supervisor) {
        Objects.requireNonNull(supervisor, "Supervisor é obrigatório");
        if (!supervisor.isAtivo()) {
            throw new InvarianteVioladaException("Não é possível vincular um supervisor desativado");
        }
        // Validação hierárquica: o supervisor deve responder ao coordenador da equipe
        if (this.coordenador != null && supervisor.getCoordenador() != null 
                && !supervisor.getCoordenador().getId().equals(this.coordenador.getId())) {
            throw new InvarianteVioladaException("O supervisor selecionado responde a um coordenador diferente do coordenador da equipe.");
        }
        // Verifica se o colaborador por trás do supervisor já é um membro ativo da equipe
        if (supervisor.getColaborador() != null) {
            this.membros.stream()
                .filter(m -> m.isVinculoAtivo() && m.getColaborador().getId().equals(supervisor.getColaborador().getId()))
                .findFirst()
                .ifPresent(m -> {
                    throw new InvarianteVioladaException("O colaborador associado ao supervisor já é um membro ativo desta equipe.");
                });
        }
        this.supervisor = supervisor;
    }

    public void vincularProcesso(ProcessoNegocio processo) {
        this.processo = Objects.requireNonNull(processo, "Novo processo não pode ser nulo");
        validarEstadoAtivo(processo);
    }

    public void atualizarCodigo(String novoCodigo) {
        validarCodigo(novoCodigo);
        this.codigo = novoCodigo.toUpperCase();
    }

    private void validarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new IllegalArgumentException("Código da equipe inválido");
        }
    }

    public void validarStatus() {
        if (!isAtivo()) {
            throw new IllegalArgumentException("Registro desativado.");
        }
    }

    private void validarEstadoAtivo(BaseEntity... entities) {
        for (BaseEntity entity : entities) {
            if (entity != null && !entity.isAtivo()) {
                throw new InvarianteVioladaException("Não é possível utilizar um recurso desativado: " + entity.getClass().getSimpleName());
            }
        }
    }
}
