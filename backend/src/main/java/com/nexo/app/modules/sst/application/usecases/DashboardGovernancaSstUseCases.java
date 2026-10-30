package com.nexo.app.modules.sst.application.usecases;

import com.nexo.app.modules.sst.application.dto.DashboardGovernancaSstDto;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardGovernancaSstUseCases {
    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly=true)
    public DashboardGovernancaSstDto.Response consultar(DashboardGovernancaSstDto.Filter f){
        LocalDate fim=f!=null&&f.fim()!=null?f.fim():LocalDate.now();
        LocalDate inicio=f!=null&&f.inicio()!=null?f.inicio():fim.minusMonths(12);
        if(fim.isBefore(inicio))throw new InvarianteVioladaException("A data final deve ser posterior à inicial.");
        int limite=f!=null&&f.limiteAtencao()!=null?Math.min(Math.max(f.limiteAtencao(),1),100):30;
        MapSqlParameterSource p=new MapSqlParameterSource().addValue("inicio",inicio).addValue("fim",fim)
                .addValue("hoje",LocalDate.now()).addValue("alerta",LocalDate.now().plusDays(30)).addValue("limite",limite);
        String corpo=escopo(f,p,"c.id_base_operacional","c.tipo","c.id");
        String reuniao=escopo(f,p,"r.id_base_operacional","r.tipo_corpo","r.id_corpo");
        String doc=escopo(f,p,"d.id_base","d.contexto","d.id_titular");
        String acao=escopo(f,p,"a.id_base_operacional","c.tipo","a.id_corpo");
        long corpos=q("select count(*) from corpos_governanca_sst c where c.ativo and c.status='ATIVO'"+corpo,p);
        long reunioes=q("select count(*) from reunioes_governanca_sst r where r.ativo and r.status in ('AGUARDANDO_ATA','AGUARDANDO_ASSINATURAS') and cast(r.data_hora as date) between :inicio and :fim"+reuniao,p);
        long vencidos=q("select count(*) from sst_documentos_governanca d where d.ativo and d.atual and d.data_arquivamento is null and d.situacao_conferencia='APPROVED' and d.data_validade<:hoje"+doc,p);
        long aVencer=q("select count(*) from sst_documentos_governanca d where d.ativo and d.atual and d.data_arquivamento is null and d.situacao_conferencia='APPROVED' and d.data_validade between :hoje and :alerta"+doc,p);
        long pendentes=q("select count(*) from sst_documentos_governanca d where d.ativo and d.data_arquivamento is null and d.situacao_conferencia='PENDING_REVIEW'"+doc,p);
        long treinamentos=q("select count(*) from sst_exigencias_treinamento e where e.ativo and e.atual and e.fonte_confirmada and not exists (select 1 from sst_documentos_governanca d where d.ativo and d.atual and d.data_arquivamento is null and d.situacao_conferencia='APPROVED' and d.id_treinamento=e.id_treinamento)"+(f!=null&&f.idCorpo()!=null?" and e.id_base=(select id_base_operacional from corpos_governanca_sst where id=:idCorpo)":""),p);
        long inspecoes=q("select count(*) from execucoes_checklist_governanca_sst x join corpos_governanca_sst c on c.id=x.id_corpo where x.ativo and x.status='RASCUNHO' and cast(x.data_execucao as date) between :inicio and :fim"+escopo(f,p,"x.id_base_operacional","c.tipo","x.id_corpo"),p);
        long abertas=q("select count(*) from acoes_governanca_sst a join corpos_governanca_sst c on c.id=a.id_corpo where a.ativo and a.status in ('ABERTA','EM_ANDAMENTO')"+acao,p);
        long atrasadas=q("select count(*) from acoes_governanca_sst a join corpos_governanca_sst c on c.id=a.id_corpo where a.ativo and a.status in ('ABERTA','EM_ANDAMENTO') and a.prazo<:hoje"+acao,p);
        var indicadores=new DashboardGovernancaSstDto.Indicadores(corpos,reunioes,vencidos,aVencer,pendentes,treinamentos,inspecoes,abertas,atrasadas);
        List<DashboardGovernancaSstDto.ItemAtencao> atencao=new ArrayList<>();
        atencao.addAll(documentosAtencao(doc,p,limite));atencao.addAll(acoesAtencao(acao,p,limite));atencao.addAll(reunioesAtencao(reuniao,p,limite));
        atencao=atencao.stream().sorted(Comparator
                .comparingInt((DashboardGovernancaSstDto.ItemAtencao x)->ordem(x.severidade()))
                .thenComparing(x->x.prazo()==null?LocalDate.MAX:x.prazo())).limit(limite).toList();
        Map<String,String> definicoes=Map.of("documentosVencidos","Versões atuais aprovadas cuja validade declarada já passou.","treinamentosIrregulares","Exigências confirmadas sem evidência documental aprovada.","acoesVencidas","Ações abertas ou em andamento com prazo anterior à data do cálculo.");
        Map<String,Object> filtros=new LinkedHashMap<>();filtros.put("idsBases",f==null?List.of():n(f.idsBases()));filtros.put("tiposCorpo",f==null?List.of():n(f.tiposCorpo()));filtros.put("idCorpo",f==null?null:f.idCorpo());
        return new DashboardGovernancaSstDto.Response(OffsetDateTime.now(),new DashboardGovernancaSstDto.Periodo(inicio,fim),filtros,indicadores,definicoes,atencao);
    }
    private List<DashboardGovernancaSstDto.ItemAtencao> documentosAtencao(String s,MapSqlParameterSource p,int l){return jdbc.query("select d.id,coalesce(d.numero_documento,'Documento') titulo,d.data_validade prazo from sst_documentos_governanca d where d.ativo and d.atual and d.data_arquivamento is null and d.situacao_conferencia='APPROVED' and d.data_validade<=:alerta"+s+" order by d.data_validade limit :limite",p,(rs,i)->new DashboardGovernancaSstDto.ItemAtencao("DOCUMENTO",rs.getObject("prazo",LocalDate.class).isBefore(LocalDate.now())?"VENCIDO":"URGENTE",rs.getObject("id",UUID.class),rs.getString("titulo"),rs.getObject("prazo",LocalDate.class),"/sst/governanca/documentos/"+rs.getObject("id")));}
    private List<DashboardGovernancaSstDto.ItemAtencao> acoesAtencao(String s,MapSqlParameterSource p,int l){return jdbc.query("select a.id,a.titulo,a.prazo from acoes_governanca_sst a join corpos_governanca_sst c on c.id=a.id_corpo where a.ativo and a.status in ('ABERTA','EM_ANDAMENTO') and a.prazo<=:alerta"+s+" order by a.prazo limit :limite",p,(rs,i)->new DashboardGovernancaSstDto.ItemAtencao("ACAO",rs.getObject("prazo",LocalDate.class).isBefore(LocalDate.now())?"VENCIDO":"URGENTE",rs.getObject("id",UUID.class),rs.getString("titulo"),rs.getObject("prazo",LocalDate.class),"/sst/governanca/acoes/"+rs.getObject("id")));}
    private List<DashboardGovernancaSstDto.ItemAtencao> reunioesAtencao(String s,MapSqlParameterSource p,int l){return jdbc.query("select r.id,r.tipo,cast(r.data_hora as date) prazo from reunioes_governanca_sst r where r.ativo and r.status in ('AGENDADA','AGUARDANDO_ATA','AGUARDANDO_ASSINATURAS') and cast(r.data_hora as date)<=:alerta"+s+" order by r.data_hora limit :limite",p,(rs,i)->new DashboardGovernancaSstDto.ItemAtencao("REUNIAO",rs.getObject("prazo",LocalDate.class).isBefore(LocalDate.now())?"PENDENTE":"INFORMATIVO",rs.getObject("id",UUID.class),"Reunião "+rs.getString("tipo"),rs.getObject("prazo",LocalDate.class),"/sst/governanca/reunioes/"+rs.getObject("id")));}
    private long q(String sql,MapSqlParameterSource p){Long v=jdbc.queryForObject(sql,p,Long.class);return v==null?0:v;}
    private String escopo(DashboardGovernancaSstDto.Filter f,MapSqlParameterSource p,String base,String tipo,String corpo){StringBuilder s=new StringBuilder();if(f!=null&&f.idsBases()!=null&&!f.idsBases().isEmpty()){p.addValue("idsBases",f.idsBases());s.append(" and ").append(base).append(" in (:idsBases)");}if(f!=null&&f.tiposCorpo()!=null&&!f.tiposCorpo().isEmpty()){p.addValue("tiposCorpo",f.tiposCorpo().stream().map(Enum::name).toList());s.append(" and ").append(tipo).append(" in (:tiposCorpo)");}if(f!=null&&f.idCorpo()!=null){p.addValue("idCorpo",f.idCorpo());s.append(" and ").append(corpo).append("=:idCorpo");}return s.toString();}
    private static int ordem(String s){return switch(s){case"VENCIDO"->0;case"URGENTE"->1;case"PENDENTE"->2;default->3;};}
    private static <T> List<T> n(List<T> v){return v==null?List.of():v;}
}
