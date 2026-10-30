# Adaptador público de sincronização SST

`InspecaoSincronizacaoApi` expõe duas operações para o orquestrador de sincronização:

```java
InspecaoSegurancaResponse registrar(Map<String, Object> payload,
                                    List<AnexoSincronizacao> anexos,
                                    UUID idInspetor);
EvidenciaInspecaoResponse anexar(UUID idInspecao,
                                 AnexoSincronizacao anexo,
                                 UUID idUsuario);
```

`registrar` converte o snapshot para `RegistrarInspecaoRequest`, envia os bytes dos
anexos pelo caso de uso oficial de evidências e substitui os IDs locais pelo ID remoto
antes de chamar `InspecaoSegurancaUseCases.registrar`. `anexar` mantém o fluxo oficial de
upload e vínculo em uma inspeção existente.

O adaptador não faz merge, edição de inspeção já registrada ou replay de anexos que não
sejam fotos válidas para SST. Falhas de validação, referências inexistentes e violações
de invariantes continuam sendo devolvidas pelos casos de uso existentes.
