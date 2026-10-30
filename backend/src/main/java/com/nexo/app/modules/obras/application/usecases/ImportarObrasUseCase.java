package com.nexo.app.modules.obras.application.usecases;

import com.nexo.app.modules.obras.application.dto.ConfirmarImportacaoObraRequest;
import com.nexo.app.modules.obras.application.dto.ImportacaoObraResponse;
import com.nexo.app.modules.obras.application.dto.ObraImportacaoDTO;
import com.nexo.app.modules.obras.infrastructure.persistence.AgenteResponsavelRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.ObraRepository;
import com.nexo.app.modules.obras.infrastructure.persistence.StatusObraRepository;
import com.nexo.app.modules.rh.BaseOperacionalApi;
import com.nexo.app.modules.rh.CentroFaturamentoApi;
import com.nexo.app.modules.rh.CoordenadorApi;
import com.nexo.app.modules.rh.EquipeApi;
import com.nexo.app.modules.rh.MunicipioApi;
import com.nexo.app.modules.rh.ProcessoNegocioApi;
import com.nexo.app.modules.rh.SupervisorApi;
import com.nexo.app.shared.application.dto.ImportacaoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportarObrasUseCase {

    private static final int LIMITE_LINHAS = 2000;

    private final ObraRepository obraRepository;
    private final StatusObraRepository statusObraRepository;
    private final AgenteResponsavelRepository agenteRepository;
    private final ProcessoNegocioApi processoNegocioApi;
    private final BaseOperacionalApi baseOperacionalApi;
    private final MunicipioApi municipioApi;
    private final CentroFaturamentoApi centroFaturamentoApi;
    private final SupervisorApi supervisorApi;
    private final CoordenadorApi coordenadorApi;
    private final EquipeApi equipeApi;
    private final NormalizadorObraService normalizador;
    private final ImportarObrasAsyncService asyncService;

    public ImportacaoObraResponse executarValidacao(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new IllegalArgumentException("Selecione um arquivo CSV preenchido.");
        }

        List<ObraImportacaoDTO> registros = new ArrayList<>();
        Set<String> numerosNoLote = new HashSet<>();
        int sucessos = 0;
        int falhas = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(arquivo.getInputStream(), StandardCharsets.UTF_8))) {

            String primeiraLinha = reader.readLine();
            if (primeiraLinha == null || primeiraLinha.isBlank()) {
                throw new IllegalArgumentException("O arquivo CSV está vazio.");
            }
            if (primeiraLinha.startsWith("\uFEFF")) {
                primeiraLinha = primeiraLinha.substring(1);
            }

            char separador = detectarSeparador(primeiraLinha);
            Map<String, Integer> headerMap = mapearCabecalhos(primeiraLinha, separador);

            String linha;
            int numeroLinha = 1;

            while ((linha = reader.readLine()) != null) {
                numeroLinha++;
                if (linha.isBlank() || linha.trim().startsWith("#")) {
                    continue;
                }
                if (registros.size() >= LIMITE_LINHAS) {
                    log.warn("Arquivo de obras excede o limite de {} registros", LIMITE_LINHAS);
                    break;
                }

                ObraImportacaoDTO dto = parsearLinha(linha, separador, headerMap, numeroLinha);
                dto = validarRegistro(dto, numerosNoLote);

                if (dto.status() == ImportacaoStatus.COM_ERRO) {
                    falhas++;
                } else {
                    sucessos++;
                }
                registros.add(dto);
            }
        } catch (Exception e) {
            log.error("Falha crítica ao ler arquivo de importação de obras", e);
            throw new IllegalArgumentException("Falha ao ler o arquivo CSV: " + e.getMessage(), e);
        }

        return new ImportacaoObraResponse(registros, registros.size(), sucessos, falhas);
    }

    public ImportacaoObraResponse confirmarImportacao(ConfirmarImportacaoObraRequest request) {
        Set<String> numerosNoLote = new HashSet<>();
        List<ObraImportacaoDTO> revalidados = request.registros().stream()
                .map(r -> validarRegistro(r, numerosNoLote))
                .toList();

        long falhas = revalidados.stream().filter(r -> r.status() == ImportacaoStatus.COM_ERRO).count();

        List<ObraImportacaoDTO> emProcessamento = revalidados.stream()
                .filter(r -> r.status() != ImportacaoStatus.COM_ERRO)
                .map(r -> r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        if (!emProcessamento.isEmpty()) {
            asyncService.processarPersistencia(emProcessamento);
        }

        List<ObraImportacaoDTO> resposta = revalidados.stream()
                .map(r -> r.status() == ImportacaoStatus.COM_ERRO
                        ? r
                        : r.withStatus(ImportacaoStatus.EM_PROCESSAMENTO, null))
                .toList();

        return new ImportacaoObraResponse(
                resposta, resposta.size(), emProcessamento.size(), Math.toIntExact(falhas)
        );
    }

    public ObraImportacaoDTO validarRegistro(ObraImportacaoDTO dto, Set<String> numerosNoLote) {
        List<String> erros = new ArrayList<>();
        List<String> avisos = new ArrayList<>();

        String numeroNorm = normalizador.normalizarNumero(dto.numero());
        if (numeroNorm == null || numeroNorm.isBlank()) {
            erros.add("Número da obra é obrigatório.");
        } else {
            if (!numerosNoLote.add(numeroNorm.toUpperCase(Locale.ROOT))) {
                erros.add("Número '" + numeroNorm + "' duplicado no arquivo de importação.");
            } else if (obraRepository.existsByNumeroIgnoreCaseAndAtivoTrue(numeroNorm)) {
                erros.add("Já existe uma obra ativa no sistema com o número '" + numeroNorm + "'.");
            }
        }

        String tituloNorm = normalizador.normalizarTitulo(dto.titulo());
        if (tituloNorm == null || tituloNorm.isBlank()) {
            erros.add("Título da obra é obrigatório.");
        }

        LocalDate dataInicio = null;
        if (dto.dataInicio() == null || dto.dataInicio().isBlank()) {
            erros.add("Data de início é obrigatória.");
        } else {
            try {
                dataInicio = normalizador.parseData(dto.dataInicio());
            } catch (Exception e) {
                erros.add("Data de início inválida: " + dto.dataInicio() + " (esperado dd/MM/yyyy).");
            }
        }

        LocalDate dataFim = null;
        if (dto.dataPrevisaoFim() == null || dto.dataPrevisaoFim().isBlank()) {
            erros.add("Data prevista de término é obrigatória.");
        } else {
            try {
                dataFim = normalizador.parseData(dto.dataPrevisaoFim());
            } catch (Exception e) {
                erros.add("Data prevista de término inválida: " + dto.dataPrevisaoFim() + " (esperado dd/MM/yyyy).");
            }
        }

        if (dataInicio != null && dataFim != null && dataFim.isBefore(dataInicio)) {
            avisos.add("Data de previsão de término (" + dto.dataPrevisaoFim() + ") é anterior à data de início (" + dto.dataInicio() + ").");
        }

        if (dto.nomeMunicipio() == null || dto.nomeMunicipio().isBlank()) {
            erros.add("Município da obra é obrigatório.");
        } else if (municipioApi.buscarIdPorNome(dto.nomeMunicipio().trim()).isEmpty()) {
            erros.add("Município '" + dto.nomeMunicipio() + "' não encontrado no cadastro do RH.");
        }

        if (dto.nomeCentroCusto() == null || dto.nomeCentroCusto().isBlank()) {
            erros.add("Centro de custo da obra é obrigatório.");
        } else if (centroFaturamentoApi.buscarIdPorNome(dto.nomeCentroCusto().trim()).isEmpty()) {
            erros.add("Centro de custo '" + dto.nomeCentroCusto() + "' não encontrado no cadastro do RH.");
        }

        if (dto.nomeStatus() != null && !dto.nomeStatus().isBlank()) {
            boolean encontrado = statusObraRepository.findByNomeIgnoreCase(dto.nomeStatus().trim()).isPresent();
            if (!encontrado) {
                String norm = java.text.Normalizer.normalize(dto.nomeStatus().trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                encontrado = statusObraRepository.findAll().stream().anyMatch(s ->
                        java.text.Normalizer.normalize(s.getNome(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").equalsIgnoreCase(norm)
                );
            }
            if (!encontrado) {
                avisos.add("Status '" + dto.nomeStatus() + "' não encontrado; será associado o status padrão 'PLANEJADA'.");
            }
        }

        if (dto.nomeSupervisor() == null || dto.nomeSupervisor().isBlank()) {
            erros.add("Supervisor da obra é obrigatório.");
        } else if (supervisorApi.buscarIdPorNomeColaborador(dto.nomeSupervisor().trim()).isEmpty()) {
            erros.add("Supervisor '" + dto.nomeSupervisor() + "' não encontrado no cadastro do RH.");
        }

        if (dto.nomeCoordenador() == null || dto.nomeCoordenador().isBlank()) {
            erros.add("Coordenador da obra é obrigatório.");
        } else if (coordenadorApi.buscarIdPorNomeColaborador(dto.nomeCoordenador().trim()).isEmpty()) {
            erros.add("Coordenador '" + dto.nomeCoordenador() + "' não encontrado no cadastro do RH.");
        }

        if (dto.nomeProcesso() == null || dto.nomeProcesso().isBlank()) {
            erros.add("Processo de negócio da obra é obrigatório.");
        } else if (processoNegocioApi.buscarIdPorNome(dto.nomeProcesso().trim()).isEmpty()) {
            erros.add("Processo de negócio '" + dto.nomeProcesso() + "' não encontrado no cadastro do RH.");
        }

        if (dto.nomeBaseOperacional() == null || dto.nomeBaseOperacional().isBlank()) {
            erros.add("Base operacional da obra é obrigatória.");
        } else if (baseOperacionalApi.buscarIdPorNome(dto.nomeBaseOperacional().trim()).isEmpty()) {
            erros.add("Base operacional '" + dto.nomeBaseOperacional() + "' não encontrada no cadastro do RH.");
        }

        if (dto.codigosEquipes() != null && !dto.codigosEquipes().isBlank()) {
            String[] partes = dto.codigosEquipes().split("[,;/]");
            for (String cod : partes) {
                String c = cod.trim();
                if (!c.isBlank() && equipeApi.buscarIdPorCodigo(c).isEmpty()) {
                    avisos.add("Equipe com código '" + c + "' não encontrada no cadastro do RH.");
                }
            }
        }

        if (dto.nomeAgenteResponsavel() != null && !dto.nomeAgenteResponsavel().isBlank()) {
            String nomeAgenteNorm = dto.nomeAgenteResponsavel().trim().toUpperCase(Locale.ROOT);
            if (agenteRepository.findByNomeIgnoreCase(nomeAgenteNorm).isEmpty()) {
                avisos.add("Agente responsável '" + dto.nomeAgenteResponsavel() + "' não encontrado no cadastro; a obra será importada sem agente vinculado.");
            }
        }

        try {
            normalizador.parseTipoObra(dto.tipoObra());
        } catch (IllegalArgumentException e) {
            erros.add(e.getMessage());
        }

        if (!erros.isEmpty()) {
            return dto.withStatus(ImportacaoStatus.COM_ERRO, String.join("; ", erros));
        }
        if (!avisos.isEmpty()) {
            return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, String.join("; ", avisos));
        }

        return dto.withStatus(ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null);
    }

    public byte[] obterModeloCsv() {
        String cabecalho = "Numero Obra;Titulo;Tipo Obra;Nota Obra;Descricao;Data Inicio;Data Previsao Fim;Valor Projeto;Processo;Centro Custo;Base Operacional;Municipio;Supervisor;Coordenador;Latitude;Longitude;Status Obra;Agente Responsavel;Codigos Equipes\n";
        String exemplo = "# Exemplo: 1228694;REDE PEDAGOGICA REMANSO (CONCESSIONARIA);OBRA;12345;Obra de ampliacao de rede;03/11/2025;30/11/2025;150000.00;Obras;CC-01;Remanso;Remanso;JOAO SILVA;CARLOS OLIVEIRA;-9.439007;-41.968228;PLANEJADA;MARLOS PEREIRA;EQ-01,EQ-02\n";
        return ("\uFEFF" + cabecalho + exemplo).getBytes(StandardCharsets.UTF_8);
    }

    private char detectarSeparador(String primeiraLinha) {
        long pontoVirgula = primeiraLinha.chars().filter(c -> c == ';').count();
        long virgula = primeiraLinha.chars().filter(c -> c == ',').count();
        long tab = primeiraLinha.chars().filter(c -> c == '\t').count();
        if (tab > pontoVirgula && tab > virgula) return '\t';
        return pontoVirgula >= virgula ? ';' : ',';
    }

    private Map<String, Integer> mapearCabecalhos(String headerLine, char separador) {
        String[] cols = splitLinha(headerLine, separador);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            String raw = cols[i].replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT);
            String semAcento = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");
            String colNorm = semAcento
                    .replace("_", "")
                    .replace(" ", "")
                    .replace("-", "")
                    .replace("(", "")
                    .replace(")", "");
            map.put(colNorm, i);
            map.put(raw.replace("_", "").replace(" ", "").replace("-", ""), i);
        }
        return map;
    }

    private ObraImportacaoDTO parsearLinha(String linha, char separador, Map<String, Integer> headerMap, int numLinha) {
        String[] cols = splitLinha(linha, separador);

        String numero = obterColuna(cols, headerMap, "numeroobra", "numerodaobra", "numero", "codobra", "codigo");
        String titulo = obterColuna(cols, headerMap, "titulodaobra", "titulo", "descricaoobra", "descricaodaobra", "nomeobra", "nome");
        String tipoObra = obterColuna(cols, headerMap, "tipoobra", "tipodaobra", "tipooperacional");
        if (tipoObra == null) {
            tipoObra = com.nexo.app.modules.obras.domain.TipoObra.OBRA.name();
        }
        String descricao = obterColuna(cols, headerMap, "descricao", "detalhes", "observacoes", "descricaocompleta");
        String notaObra = obterColuna(cols, headerMap, "notaobra", "notadaobra", "nota");
        String valorProjeto = obterColuna(cols, headerMap, "valorprojeto", "valordoprojeto", "valor");
        String dataInicio = obterColuna(cols, headerMap, "datainicio", "datadeinicio", "datadeinicioddmmyyyy", "inicio");
        String dataPrevisaoFim = obterColuna(cols, headerMap, "dataprevisaofim", "dataprevisaofimddmmyyyy", "datafinal", "datafim", "fim");
        String latitude = obterColuna(cols, headerMap, "latitudeobra", "latitude", "lat");
        String longitude = obterColuna(cols, headerMap, "longetudeobra", "longitudeobra", "longitude", "long");
        String processo = obterColuna(cols, headerMap, "processo", "processodenegocio");
        String base = obterColuna(cols, headerMap, "baseop", "baseoperacional", "base");
        String municipio = obterColuna(cols, headerMap, "municipio", "cidade");
        String centroCusto = obterColuna(cols, headerMap, "centrocusto", "centrodefaturamento", "centrofaturamento");
        String supervisor = obterColuna(cols, headerMap, "supervisor", "nomesupervisor", "supervisordaobra");
        String coordenador = obterColuna(cols, headerMap, "coordenador", "nomecoordenador", "coordenadordaobra");
        String status = obterColuna(cols, headerMap, "statusobra", "status", "situacao");
        String agenteNome = obterColuna(cols, headerMap, "agentesresponsaveisnomes", "agenteresponsavel", "nomeagenteresponsavel", "agente", "fiscal", "agentesresponsaveis");
        String equipes = obterColuna(cols, headerMap, "equipe", "equipes", "codigosequipes", "codigosdasequipes");

        return new ObraImportacaoDTO(
                numLinha, numero, titulo, descricao, notaObra, valorProjeto,
                dataInicio, dataPrevisaoFim, latitude, longitude,
                processo, base, municipio, centroCusto,
                supervisor, coordenador, status,
                agenteNome, equipes, tipoObra, ImportacaoStatus.AGUARDANDO_PROCESSAMENTO, null
        );
    }

    private String obterColuna(String[] cols, Map<String, Integer> headerMap, String... chaves) {
        for (String chave : chaves) {
            Integer idx = headerMap.get(chave);
            if (idx != null && idx < cols.length) {
                String val = cols[idx].trim();
                if (!val.isBlank()) {
                    return val;
                }
            }
        }
        return null;
    }

    private String[] splitLinha(String linha, char separador) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == separador && !inQuotes) {
                tokens.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString());
        return tokens.toArray(new String[0]);
    }
}
