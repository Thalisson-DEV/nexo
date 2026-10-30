package com.nexo.app.modules.sst.application.dto;

import com.nexo.app.modules.sst.domain.TipoCorpoGovernancaSst;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardGovernancaSstDto {
    private DashboardGovernancaSstDto() {}
    public record Filter(List<UUID> idsBases,List<TipoCorpoGovernancaSst> tiposCorpo,UUID idCorpo,
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate inicio,
            @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate fim,Integer limiteAtencao){}
    public record Periodo(LocalDate inicio,LocalDate fim){}
    public record Indicadores(long corposAtivos,long reunioesPendentesAta,long documentosVencidos,
            long documentosAVencer,long documentosPendentesConferencia,long treinamentosIrregulares,
            long inspecoesPendentes,long acoesAbertas,long acoesVencidas){}
    public record ItemAtencao(String tipo,String severidade,UUID idRegistro,String titulo,
            LocalDate prazo,String destino){}
    public record Response(OffsetDateTime calculadoEm,Periodo periodo,Map<String,Object> filtros,
            Indicadores indicadores,Map<String,String> definicoes,List<ItemAtencao> atencao){}
}
