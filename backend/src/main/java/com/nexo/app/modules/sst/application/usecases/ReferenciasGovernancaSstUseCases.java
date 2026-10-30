package com.nexo.app.modules.sst.application.usecases;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexo.app.modules.sst.application.dto.ReferenciaGovernancaSstDto;
import com.nexo.app.modules.sst.domain.*;
import com.nexo.app.modules.sst.infrastructure.persistence.*;
import com.nexo.app.shared.domain.exception.InvarianteVioladaException;
import com.nexo.app.shared.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReferenciasGovernancaSstUseCases {
    private static final String RAIZ = "sst/referencias-governanca/";
    private final ProcessoTreinamentoSstRepository processoRepository;
    private final TreinamentoGovernancaSstRepository treinamentoRepository;
    private final ReferenciaMatrizTreinamentoSstRepository matrizRepository;
    private final InstituicaoTreinamentoSstRepository instituicaoRepository;
    private final HomologacaoInstituicaoSstRepository homologacaoRepository;
    private final AtivoInspecionavelSstRepository ativoRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReferenciaGovernancaSstDto.ProcessoResponse criarProcesso(
            ReferenciaGovernancaSstDto.ProcessoRequest r) {
        ProcessoTreinamentoSst p = new ProcessoTreinamentoSst(r.codigo(), r.nome(), r.fonte());
        if (processoRepository.findFirstByCodigoAndAtivoTrueOrderByVersaoReferenciaDesc(p.getCodigo()).isPresent())
            throw new ConflitoGovernancaSstException("Já existe processo com este código.");
        return processo(processoRepository.save(p));
    }

    @Transactional
    public ReferenciaGovernancaSstDto.ProcessoResponse confirmarProcesso(UUID id, LocalDate inicio, UUID ator) {
        ProcessoTreinamentoSst p = processoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Processo não encontrado."));
        p.confirmar(inicio, ator); return processo(processoRepository.save(p));
    }
    public List<ReferenciaGovernancaSstDto.ProcessoResponse> listarProcessos(){
        return processoRepository.findAllByAtivoTrueOrderByNomeAsc().stream().map(this::processo).toList();
    }

    @Transactional
    public ReferenciaGovernancaSstDto.InstituicaoResponse criarInstituicao(
            ReferenciaGovernancaSstDto.InstituicaoRequest r) {
        InstituicaoTreinamentoSst i = new InstituicaoTreinamentoSst(r.cnpj(), r.nome(), r.razaoSocial(),
                r.parceriaInstituicao(), r.inscricaoMunicipalLiteral(), r.local(), r.telefones(), r.contatos(),
                "CADASTRO_MANUAL", LocalDate.now(), r.possuiDadoSuspeito());
        return instituicao(instituicaoRepository.save(i));
    }
    public List<ReferenciaGovernancaSstDto.InstituicaoResponse> listarInstituicoes(){
        return instituicaoRepository.findAllByAtivoTrueOrderByNomeAsc().stream().map(this::instituicao).toList();
    }
    @Transactional
    public ReferenciaGovernancaSstDto.HomologacaoResponse confirmarHomologacao(UUID idInstituicao, UUID id,
            ReferenciaGovernancaSstDto.ConfirmarHomologacaoRequest r, UUID ator){
        HomologacaoInstituicaoSst h=homologacaoRepository.findById(id)
                .filter(x->x.getIdInstituicao().equals(idInstituicao))
                .orElseThrow(()->new RecursoNaoEncontradoException("Homologação não encontrada."));
        h.confirmar(r.status(),r.vigenciaInicio(),r.vigenciaFim(),r.escopo(),r.eadLiberado(),
                r.competenciaLiberada(),r.idDocumentoEvidencia(),ator);
        return homologacao(homologacaoRepository.save(h));
    }

    @Transactional
    public ReferenciaGovernancaSstDto.AtivoResponse criarAtivo(ReferenciaGovernancaSstDto.AtivoRequest r){
        AtivoInspecionavelSst a=new AtivoInspecionavelSst(r.idBaseOperacional(),r.tipo(),r.identificadorLocal(),
                r.localizacao(),r.atributos(),"CADASTRO_MANUAL",r.possuiDadoSuspeito());
        return ativo(ativoRepository.save(a));
    }
    public List<ReferenciaGovernancaSstDto.AtivoResponse> listarAtivos(UUID base){
        return ativoRepository.findAllByIdBaseOperacionalAndAtivoTrueOrderByTipoAscIdentificadorLocalAsc(base)
                .stream().map(this::ativo).toList();
    }
    @Transactional public ReferenciaGovernancaSstDto.AtivoResponse confirmarAtivo(UUID id){
        AtivoInspecionavelSst a=ativoRepository.findByIdAndAtivoTrue(id)
                .orElseThrow(()->new RecursoNaoEncontradoException("Ativo não encontrado."));
        a.confirmar();return ativo(ativoRepository.save(a));
    }

    @Transactional
    public ReferenciaGovernancaSstDto.ImportacaoResponse importarMatriz() {
        String fonte = ler("matriz_treinamentos_normalizada.md"); String hash = sha256(fonte);
        if (matrizRepository.existsBySha256Fonte(hash))
            return new ReferenciaGovernancaSstDto.ImportacaoResponse("MATRIZ_TREINAMENTOS", 0, true, hash);
        int cursos=importarCursos(fonte); int linhas=importarLinhasMatriz(fonte,hash);
        return new ReferenciaGovernancaSstDto.ImportacaoResponse("MATRIZ_TREINAMENTOS",cursos+linhas,false,hash);
    }

    @Transactional
    public ReferenciaGovernancaSstDto.ImportacaoResponse importarEscolas() {
        String fonte=ler("escolas_homologadas_normalizado.md");String hash=sha256(fonte);
        if(instituicaoRepository.countByFonteReferencia(hash)>0)
            return new ReferenciaGovernancaSstDto.ImportacaoResponse("ESCOLAS",0,true,hash);
        Matcher m=Pattern.compile("(?ms)^  - empresa: (.*?)(?=^  - empresa:|\\z)").matcher(fonte);int n=0;
        while(m.find()){
            String b=m.group();String nome=valor(b,"empresa");String cnpj=nulo(valor(b,"cnpj"));
            InstituicaoTreinamentoSst i=new InstituicaoTreinamentoSst(cnpj,nome,nulo(valor(b,"razao_social")),
                    nulo(valor(b,"parceria_instituicao_ensino")),nulo(valor(b,"inscricao_municipal")),
                    nulo(valor(b,"local")),nulo(valor(b,"telefones")),nulo(valor(b,"contatos")),hash,
                    LocalDate.now(),b.contains("22020")||b.contains("E+"));
            i=instituicaoRepository.save(i);
            HomologacaoInstituicaoSst h=new HomologacaoInstituicaoSst(i.getId(),valor(b,"status"),
                    nulo(valor(b,"data_homologacao_inicio_parceria")),nulo(valor(b,"data_descredenciada")),
                    nested(b,"ead_teoria","liberacao"),nested(b,"certificacao_competencia","liberada"));
            homologacaoRepository.save(h);n++;
        }
        if(n!=24)throw new InvarianteVioladaException("A referência de escolas deveria conter 24 registros; encontrados: "+n);
        return new ReferenciaGovernancaSstDto.ImportacaoResponse("ESCOLAS",n,false,hash);
    }

    @Transactional
    public ReferenciaGovernancaSstDto.ImportacaoResponse importarAtivos(UUID base) {
        String fonte=ler("modelo_fiscalizacao_extintores_luzes_emergencia_normalizado.md");String hash=sha256(fonte);
        int n=0;Matcher luz=Pattern.compile("(?m)^  - sequencia: (\\d+)\\R    localizacao: \"([^\"]+)\"").matcher(fonte);
        while(luz.find()){String id="LUZ-"+luz.group(1);if(!ativoRepository.existsByIdBaseOperacionalAndTipoAndIdentificadorLocalAndAtivoTrue(base,AtivoInspecionavelSst.Tipo.LUZ_EMERGENCIA,id)){
            ativoRepository.save(new AtivoInspecionavelSst(base,AtivoInspecionavelSst.Tipo.LUZ_EMERGENCIA,id,luz.group(2),
                    "{\"sinalizacao\":null,\"limpezaEObstrucoes\":null,\"testeFuncionamento\":null,\"identificacaoTomadas\":null}",hash,false));n++;}}
        Matcher ext=Pattern.compile("(?m)^  - posto: (\\d+)\\R    agente: \"([^\"]*)\"\\R    capacidade: \"([^\"]*)\"\\R    tipo: \"([^\"]*)\"\\R    localizacao: \"([^\"]+)\"").matcher(fonte);
        while(ext.find()){String id="EXT-"+ext.group(1);if(!ativoRepository.existsByIdBaseOperacionalAndTipoAndIdentificadorLocalAndAtivoTrue(base,AtivoInspecionavelSst.Tipo.EXTINTOR,id)){
            String attrs=json(Map.of("agente",ext.group(2),"capacidade",ext.group(3),"tipo",ext.group(4)));
            ativoRepository.save(new AtivoInspecionavelSst(base,AtivoInspecionavelSst.Tipo.EXTINTOR,id,ext.group(5),attrs,hash,"10".equals(ext.group(1))&&"8".equals(ext.group(4))));n++;}}
        long total=ativoRepository.findAllByIdBaseOperacionalAndAtivoTrueOrderByTipoAscIdentificadorLocalAsc(base).stream()
                .filter(a->hash.equals(a.getOrigem())).count();
        if(total!=36)throw new InvarianteVioladaException("A referência deveria conter 22 luzes e 14 extintores; encontrados: "+total);
        return new ReferenciaGovernancaSstDto.ImportacaoResponse("ATIVOS_INSPECIONAVEIS",n,n==0,hash);
    }

    private int importarCursos(String fonte){int n=0;Matcher m=Pattern.compile("(?m)^\\| (TR-\\d{2}) \\| (.*?) \\| (.*?) \\| (.*?) \\|$").matcher(fonte);
        while(m.find()){if(!treinamentoRepository.existsByCodigoIgnoreCase(m.group(1))){treinamentoRepository.save(new TreinamentoGovernancaSst(m.group(1),m.group(2),"Carga de referência: "+m.group(3)+"; periodicidade: "+m.group(4),false,false));n++;}}
        Matcher pop=Pattern.compile("(?m)^\\| `(POP-[A-Z]+)` \\| (.*?) \\| (.*?) \\|$").matcher(fonte);
        while(pop.find()){if(!treinamentoRepository.existsByCodigoIgnoreCase(pop.group(1))){treinamentoRepository.save(new TreinamentoGovernancaSst(pop.group(1).replace('-','_'),pop.group(2),"Carga da fonte: "+pop.group(3),false,false));n++;}}
        if(treinamentoRepository.count()<40)throw new InvarianteVioladaException("Não foi possível preservar os 36 treinamentos e os 4 POPs.");return n;}

    private int importarLinhasMatriz(String fonte,String hash){int n=0;String processo=null;boolean principal=false,pop=false;
        for(String original:fonte.split("\\R")){String line=original.replace("\\|","§");
            if(original.equals("## 7. Matriz principal normalizada por processo e função")){principal=true;continue;}
            if(original.startsWith("## 8.")){principal=false;pop=original.startsWith("## 8. Matriz");}
            if(principal&&original.startsWith("### ")){processo=original.substring(4).trim();salvarProcesso(processo);continue;}
            if(principal&&line.startsWith("| ")&&line.contains("`TR-")){String[] c=line.split("\\|",-1);if(c.length>=5){matrizRepository.save(new ReferenciaMatrizTreinamentoSst(ReferenciaMatrizTreinamentoSst.Tipo.PRINCIPAL,processo,c[1].trim().replace('§','|'),c[2].trim().replace("`","").replace('§','|'),inteiro(c[3]),hash));n++;}}
            if(original.equals("### 8.3 Função → treinamento POP")){pop=true;continue;}
            if(pop&&line.startsWith("| ")&&line.contains("`POP-")){String[] c=line.split("\\|",-1);if(c.length>=6){matrizRepository.save(new ReferenciaMatrizTreinamentoSst(ReferenciaMatrizTreinamentoSst.Tipo.POP,c[1].trim().replace('§','|'),c[2].trim().replace('§','|'),c[3].trim().replace("`","")+"="+c[4].trim(),inteiro(c[4]),hash));n++;}}
        }
        long principais=matrizRepository.countBySha256FonteAndTipo(hash,ReferenciaMatrizTreinamentoSst.Tipo.PRINCIPAL), pops=matrizRepository.countBySha256FonteAndTipo(hash,ReferenciaMatrizTreinamentoSst.Tipo.POP);
        if(principais!=55||pops!=54)throw new InvarianteVioladaException("Importação incompleta: "+principais+" funções e "+pops+" relações POP.");return n;}
    private void salvarProcesso(String nome){String codigo=nome.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+","_").replaceAll("^_|_$","");if(processoRepository.findFirstByCodigoAndAtivoTrueOrderByVersaoReferenciaDesc(codigo).isEmpty())processoRepository.save(new ProcessoTreinamentoSst(codigo,nome,"MATRIZ_PENDENTE_CONFIRMACAO"));}

    private ReferenciaGovernancaSstDto.ProcessoResponse processo(ProcessoTreinamentoSst p){return new ReferenciaGovernancaSstDto.ProcessoResponse(p.getId(),p.getCodigo(),p.getNome(),p.getVersaoReferencia(),p.getFonte(),p.isConfirmado(),p.getVigenteDesde(),p.getVersao());}
    private ReferenciaGovernancaSstDto.InstituicaoResponse instituicao(InstituicaoTreinamentoSst i){return new ReferenciaGovernancaSstDto.InstituicaoResponse(i.getId(),i.getCnpj(),i.getNome(),i.getRazaoSocial(),i.getParceriaInstituicao(),i.getInscricaoMunicipalLiteral(),i.getLocal(),i.getTelefones(),i.getContatos(),i.getFonteReferencia(),i.getDataReferencia(),i.isPossuiDadoSuspeito(),homologacaoRepository.findAllByIdInstituicaoAndAtivoTrueOrderByDataCriacaoDesc(i.getId()).stream().map(this::homologacao).toList(),i.getVersao());}
    private ReferenciaGovernancaSstDto.HomologacaoResponse homologacao(HomologacaoInstituicaoSst h){return new ReferenciaGovernancaSstDto.HomologacaoResponse(h.getId(),h.getStatus(),h.getStatusFonte(),h.getInicioLiteral(),h.getFimLiteral(),h.getVigenciaInicio(),h.getVigenciaFim(),h.getEscopo(),h.getEadLiberado(),h.getEadLiteral(),h.getCompetenciaLiberada(),h.getCompetenciaLiteral(),h.getIdDocumentoEvidencia(),h.getConfirmadaEm(),h.getVersao());}
    private ReferenciaGovernancaSstDto.AtivoResponse ativo(AtivoInspecionavelSst a){return new ReferenciaGovernancaSstDto.AtivoResponse(a.getId(),a.getIdBaseOperacional(),a.getTipo(),a.getIdentificadorLocal(),a.getLocalizacao(),a.getAtributos(),a.getOrigem(),a.getStatusConferencia(),a.isPossuiDadoSuspeito(),a.getInicioVigencia(),a.getFimVigencia(),a.getVersao());}
    private String ler(String n){try{return new ClassPathResource(RAIZ+n).getContentAsString(StandardCharsets.UTF_8);}catch(IOException e){throw new IllegalStateException("Referência não encontrada: "+n,e);}}
    private static String sha256(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String valor(String b,String campo){Matcher m=Pattern.compile("(?m)^    "+Pattern.quote(campo)+": (.+)$").matcher(b);return m.find()?desquote(m.group(1).trim()):null;}
    private static String nested(String b,String secao,String campo){Matcher s=Pattern.compile("(?ms)^    "+secao+":\\R(.*?)(?=^    [a-z_]+:|\\z)").matcher(b);if(!s.find())return null;Matcher m=Pattern.compile("(?m)^      "+campo+": (.+)$").matcher(s.group());return m.find()?nulo(desquote(m.group(1).trim())):null;}
    private static String desquote(String v){return v!=null&&v.length()>=2&&v.startsWith("\"")&&v.endsWith("\"")?v.substring(1,v.length()-1):v;}
    private static String nulo(String v){return v==null||"null".equals(v)?null:v;}
    private static Integer inteiro(String v){try{return Integer.valueOf(v.replaceAll("[^0-9]",""));}catch(Exception e){return null;}}
    private String json(Object o){try{return objectMapper.writeValueAsString(o);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
}
