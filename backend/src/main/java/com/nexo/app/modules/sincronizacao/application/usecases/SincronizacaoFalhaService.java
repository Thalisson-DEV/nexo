package com.nexo.app.modules.sincronizacao.application.usecases;
import com.nexo.app.modules.sincronizacao.infrastructure.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class SincronizacaoFalhaService {
 private final CasoSincronizacaoRepository casos;
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void registrar(UUID id,String detalhe){casos.findById(id).ifPresent(c->{c.getOperacao().tentativa();c.corrigir("Falha no reprocessamento: "+(detalhe==null?"erro não informado":detalhe).substring(0,Math.min(3800,(detalhe==null?"erro não informado":detalhe).length())));casos.save(c);});}
}
