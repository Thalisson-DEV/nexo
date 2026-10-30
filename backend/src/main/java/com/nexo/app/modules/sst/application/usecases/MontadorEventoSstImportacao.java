package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.ColaboradorApi;
import com.nexo.app.modules.rh.ColaboradorResponse;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.VeiculoApi;
import com.nexo.app.modules.sst.application.dto.EnvolvidoEventoRequest;
import com.nexo.app.modules.sst.application.dto.EventoSstImportacaoDTO;
import com.nexo.app.modules.sst.application.dto.EventoSstRequest;
import com.nexo.app.modules.sst.domain.AtividadeSst;
import com.nexo.app.modules.sst.domain.CatalogoSst;
import com.nexo.app.modules.sst.domain.PapelEnvolvido;
import com.nexo.app.modules.sst.domain.RegistroEvidencia;
import com.nexo.app.modules.sst.domain.TipoCat;
import com.nexo.app.modules.sst.domain.TipoCatalogoSst;
import com.nexo.app.modules.sst.domain.TipoEnvolvido;
import com.nexo.app.modules.sst.domain.TipoEventoSst;
import com.nexo.app.modules.sst.infrastructure.persistence.AtividadeSstRepository;
import com.nexo.app.modules.sst.infrastructure.persistence.CatalogoSstRepository;
import com.nexo.app.shared.infrastructure.persistence.SpecificationUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Traduz os valores legíveis do CSV para o mesmo request usado pelo cadastro manual. */
@Component
@RequiredArgsConstructor
public class MontadorEventoSstImportacao {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId FUSO_NEGOCIO = ZoneId.of("America/Sao_Paulo");

    private final AtividadeSstRepository atividadeRepository;
    private final CatalogoSstRepository catalogoRepository;
    private final ColaboradorApi colaboradorApi;
    private final BaseOperacionalApi baseApi;
    private final EquipeApi equipeApi;
    private final VeiculoApi veiculoApi;
    private final ProcessoNegocioApi processoApi;
    private final EventoSstUseCases eventoUseCases;
    private final Validator validator;

    public EventoSstRequest montarEValidar(EventoSstImportacaoDTO dto) {
        EventoSstRequest request = new EventoSstRequest(
                enumObrigatorio(dto.tipo(), TipoEventoSst.class, TipoEventoSst::getRotulo, "tipo do evento"),
                dataHoraObrigatoria(dto.dataOcorrencia(), "data da ocorrência"),
                dataHoraObrigatoria(dto.dataComunicacao(), "data da comunicação"),
                resolverAtividade(dto.atividade()),
                resolverColaboradorOpcional(dto.gestor(), "gestor"),
                resolverBase(dto.baseOperacional()),
                resolverEquipeOpcional(dto.equipe()),
                resolverVeiculoOpcional(dto.veiculo()),
                resolverCatalogo(dto.classificacao(), TipoCatalogoSst.CLASSIFICACAO_EVENTO, true),
                resolverProcessoOpcional(dto.processo()),
                enumOpcional(dto.telemetria(), RegistroEvidencia.class, RegistroEvidencia::getRotulo, "telemetria"),
                enumOpcional(dto.filmagem(), RegistroEvidencia.class, RegistroEvidencia::getRotulo, "filmagem"),
                textoOuNulo(dto.localOcorrencia()),
                decimalOpcional(dto.latitude(), "latitude"),
                decimalOpcional(dto.longitude(), "longitude"),
                dto.descricao(),
                resolverCatalogo(dto.localLesao(), TipoCatalogoSst.LOCAL_LESAO, false),
                resolverCatalogo(dto.naturezaLesao(), TipoCatalogoSst.NATUREZA_LESAO, false),
                resolverCatalogo(dto.agenteCausador(), TipoCatalogoSst.AGENTE_CAUSADOR, false),
                inteiroOpcional(dto.diasPerdidos(), "dias perdidos"),
                booleano(dto.possuiCat(), "possui CAT"),
                enumOpcional(dto.tipoCat(), TipoCat.class, TipoCat::getRotulo, "tipo da CAT"),
                textoOuNulo(dto.numeroCat()),
                dataOpcional(dto.dataEmissaoCat(), "data de emissão da CAT"),
                resolverTecnicos(dto.tecnicos()),
                resolverEnvolvidos(dto.envolvidos()));

        validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .findFirst()
                .ifPresent(mensagem -> {
                    throw new IllegalArgumentException(mensagem);
                });

        // Reutiliza referências, snapshots e invariantes do cadastro manual sem gravar.
        eventoUseCases.validarParaRegistro(request);
        return request;
    }

    private UUID resolverAtividade(String valor) {
        exigirTexto(valor, "A atividade é obrigatória");
        UUID id = uuidOuNulo(valor);
        if (id != null) {
            return id;
        }
        return atividadeRepository
                .findByNomeNormalizadoAndAtivoTrue(SpecificationUtils.normalizarTermo(valor))
                .map(AtividadeSst::getId)
                .orElseThrow(() -> new IllegalArgumentException("Atividade ativa não encontrada: " + valor));
    }

    private UUID resolverCatalogo(String valor, TipoCatalogoSst tipo, boolean obrigatorio) {
        if (valor == null || valor.isBlank()) {
            if (obrigatorio) {
                throw new IllegalArgumentException(tipo.getRotulo() + " é obrigatória");
            }
            return null;
        }
        UUID id = uuidOuNulo(valor);
        if (id != null) {
            return id;
        }
        return catalogoRepository
                .findByTipoAndNomeNormalizadoAndAtivoTrue(tipo, SpecificationUtils.normalizarTermo(valor))
                .map(CatalogoSst::getId)
                .orElseThrow(() -> new IllegalArgumentException(tipo.getRotulo() + " ativo não encontrado: " + valor));
    }

    private UUID resolverBase(String valor) {
        exigirTexto(valor, "A base operacional é obrigatória");
        UUID id = uuidOuNulo(valor);
        if (id == null) {
            id = baseApi.buscarIdPorNome(valor)
                    .orElseThrow(() -> new IllegalArgumentException("Base operacional não encontrada: " + valor));
        }
        var base = baseApi.buscarPorIdResponse(id)
                .orElseThrow(() -> new IllegalArgumentException("Base operacional não encontrada: " + valor));
        if (!base.ativo()) {
            throw new IllegalArgumentException("Base operacional inativa: " + base.nome());
        }
        return id;
    }

    private UUID resolverEquipeOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        UUID id = uuidOuNulo(valor);
        if (id == null) {
            id = equipeApi.buscarIdPorCodigo(valor)
                    .orElseThrow(() -> new IllegalArgumentException("Equipe ativa não encontrada: " + valor));
        }
        var equipe = equipeApi.buscarPorIdResponse(id)
                .orElseThrow(() -> new IllegalArgumentException("Equipe não encontrada: " + valor));
        if (!equipe.ativo()) {
            throw new IllegalArgumentException("Equipe inativa: " + equipe.codigo());
        }
        return id;
    }

    private UUID resolverVeiculoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        UUID id = uuidOuNulo(valor);
        if (id == null) {
            String placa = normalizarPlaca(valor);
            id = veiculoApi.mapaPlacasIds().entrySet().stream()
                    .filter(entry -> normalizarPlaca(entry.getKey()).equals(placa))
                    .map(entry -> entry.getValue())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Veículo ativo não encontrado: " + valor));
        }
        var veiculo = veiculoApi.buscarPorIdResponse(id)
                .orElseThrow(() -> new IllegalArgumentException("Veículo não encontrado: " + valor));
        if (!veiculo.ativo()) {
            throw new IllegalArgumentException("Veículo inativo: " + veiculo.placa());
        }
        return id;
    }

    private UUID resolverProcessoOpcional(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        UUID id = uuidOuNulo(valor);
        return id != null ? id : processoApi.buscarIdPorNome(valor)
                .orElseThrow(() -> new IllegalArgumentException("Processo ativo não encontrado: " + valor));
    }

    private UUID resolverColaboradorOpcional(String valor, String papel) {
        return valor == null || valor.isBlank() ? null : resolverColaborador(valor, papel);
    }

    private UUID resolverColaborador(String valor, String papel) {
        UUID id = uuidOuNulo(valor);
        if (id != null) {
            ColaboradorResponse colaborador = colaboradorApi.buscarPorIdResponse(id)
                    .orElseThrow(() -> new IllegalArgumentException("Colaborador não encontrado para " + papel + ": " + valor));
            if (!colaborador.ativo()) {
                throw new IllegalArgumentException("Colaborador inativo para " + papel + ": " + colaborador.nomeCompleto());
            }
            return id;
        }

        List<ColaboradorResponse> encontrados = colaboradorApi.buscarPorNomeCompletoResponse(valor).stream()
                .filter(ColaboradorResponse::ativo)
                .toList();
        if (encontrados.isEmpty()) {
            String nomeNormalizado = SpecificationUtils.normalizarTermo(valor);
            encontrados = colaboradorApi.buscarIdsPorTrechoDoNome(valor).stream()
                    .map(colaboradorApi::buscarPorIdResponse)
                    .flatMap(java.util.Optional::stream)
                    .filter(ColaboradorResponse::ativo)
                    .filter(colaborador -> nomeNormalizado.equals(
                            SpecificationUtils.normalizarTermo(colaborador.nomeCompleto())))
                    .distinct()
                    .toList();
        }
        if (encontrados.isEmpty()) {
            throw new IllegalArgumentException("Colaborador ativo não encontrado para " + papel + ": " + valor);
        }
        if (encontrados.size() > 1) {
            throw new IllegalArgumentException("Há mais de um colaborador com o nome '" + valor
                    + "'. Informe o ID no campo de " + papel + ".");
        }
        return encontrados.getFirst().id();
    }

    private List<UUID> resolverTecnicos(String valor) {
        if (valor == null || valor.isBlank()) {
            return List.of();
        }
        Set<UUID> ids = new LinkedHashSet<>();
        for (String tecnico : valor.split("\\|")) {
            if (!tecnico.isBlank()) {
                ids.add(resolverColaborador(tecnico.trim(), "técnico de segurança"));
            }
        }
        return List.copyOf(ids);
    }

    /**
     * Sintaxe: TIPO~PAPEL~IDENTIFICADOR para colaborador e
     * TERCEIRO~PAPEL~NOME~FUNÇÃO~EMPRESA para terceiros. Entradas são separadas por |.
     */
    private List<EnvolvidoEventoRequest> resolverEnvolvidos(String valor) {
        if (valor == null || valor.isBlank()) {
            return List.of();
        }
        List<EnvolvidoEventoRequest> envolvidos = new ArrayList<>();
        for (String entrada : valor.split("\\|")) {
            if (entrada.isBlank()) {
                continue;
            }
            String[] partes = Arrays.stream(entrada.split("~", -1)).map(String::trim).toArray(String[]::new);
            if (partes.length < 3) {
                throw new IllegalArgumentException("Envolvido inválido: use TIPO~PAPEL~IDENTIFICAÇÃO");
            }
            TipoEnvolvido tipo = enumObrigatorio(partes[0], TipoEnvolvido.class,
                    TipoEnvolvido::getRotulo, "tipo do envolvido");
            PapelEnvolvido papel = enumObrigatorio(partes[1], PapelEnvolvido.class,
                    PapelEnvolvido::getRotulo, "papel do envolvido");
            if (tipo == TipoEnvolvido.COLABORADOR) {
                envolvidos.add(new EnvolvidoEventoRequest(tipo, papel,
                        resolverColaborador(partes[2], "envolvido"), null, null, null));
                continue;
            }
            if (partes.length < 5) {
                throw new IllegalArgumentException(
                        "Terceiro inválido: use TERCEIRO~PAPEL~NOME~FUNÇÃO~EMPRESA");
            }
            envolvidos.add(new EnvolvidoEventoRequest(tipo, papel, null,
                    partes[2], textoOuNulo(partes[3]), partes[4]));
        }
        return envolvidos;
    }

    private OffsetDateTime dataHoraObrigatoria(String valor, String campo) {
        exigirTexto(valor, "A " + campo + " é obrigatória");
        try {
            return LocalDateTime.parse(valor.trim(), DATA_HORA).atZone(FUSO_NEGOCIO).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Formato inválido para " + campo + " (esperado dd/MM/yyyy HH:mm)");
        }
    }

    private LocalDate dataOpcional(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim(), DATA);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Formato inválido para " + campo + " (esperado dd/MM/yyyy)");
        }
    }

    private BigDecimal decimalOpcional(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valor.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido para " + campo);
        }
    }

    private Integer inteiroOpcional(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor inválido para " + campo);
        }
    }

    private boolean booleano(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return false;
        }
        return switch (SpecificationUtils.normalizarTermo(valor)) {
            case "sim", "true", "1" -> true;
            case "nao", "false", "0" -> false;
            default -> throw new IllegalArgumentException("Valor inválido para " + campo + " (use Sim ou Não)");
        };
    }

    private <E extends Enum<E>> E enumObrigatorio(
            String valor, Class<E> classe, Function<E, String> rotulo, String campo) {
        exigirTexto(valor, "O " + campo + " é obrigatório");
        E resultado = localizarEnum(valor, classe, rotulo);
        if (resultado == null) {
            throw new IllegalArgumentException("Valor inválido para " + campo + ": " + valor);
        }
        return resultado;
    }

    private <E extends Enum<E>> E enumOpcional(
            String valor, Class<E> classe, Function<E, String> rotulo, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        E resultado = localizarEnum(valor, classe, rotulo);
        if (resultado == null) {
            throw new IllegalArgumentException("Valor inválido para " + campo + ": " + valor);
        }
        return resultado;
    }

    private <E extends Enum<E>> E localizarEnum(String valor, Class<E> classe, Function<E, String> rotulo) {
        String normalizado = SpecificationUtils.normalizarTermo(valor).replace(' ', '_');
        return Arrays.stream(classe.getEnumConstants())
                .filter(item -> item.name().toLowerCase(Locale.ROOT).equals(normalizado)
                        || SpecificationUtils.normalizarTermo(rotulo.apply(item)).replace(' ', '_').equals(normalizado))
                .findFirst()
                .orElse(null);
    }

    private UUID uuidOuNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(valor.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizarPlaca(String valor) {
        return valor == null ? "" : valor.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }

    private String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void exigirTexto(String valor, String mensagem) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
    }
}
