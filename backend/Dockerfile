# ---- Build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Resolve dependencias primeiro (camada de cache)
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Codigo-fonte
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ---- Runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario nao-root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080

# ---- Tuning de memoria da JVM ----
# O limite de cgroup do container no Railway e alto; com MaxRAMPercentage a JVM
# deixava o heap crescer ate ~1GB (e o Railway cobra por RSS usado). Aqui
# cravamos um teto de heap explicito e fazemos o G1 devolver memoria ociosa
# pro SO periodicamente. Calibravel sem rebuild:
#   - JVM_MAX_HEAP (default 384m): teto de heap. Suba p/ 512m se ver OOM em
#     importacoes CSV em lote pesadas; desca p/ 320m se sobrar folga.
#   - JVM_MAX_METASPACE (default 512m): teto das classes carregadas. A aplicacao
#     ja usa ~185MB so para subir; com 256m o processo caia por
#     "OutOfMemoryError: Metaspace" depois de algumas horas de uso. O teto so
#     reserva endereco: a memoria e consumida conforme as classes carregam.
#   - JVM_CPUS (default 4): o container enxerga 24 vCPUs, e a JVM dimensionava
#     por elas as threads do GC e do JIT, o pool das virtual threads e os event
#     loops do Redis, gastando memoria com threads ociosas.
#   - JAVA_OPTS: flags extras (vem por ultimo, sobrescrevem os defaults).
# stdout.encoding: sem locale no Alpine, acentos saiam corrompidos nos logs.
# exec -> java vira PID 1 e recebe SIGTERM do Railway (shutdown gracioso).
ENV JVM_MAX_HEAP=384m
ENV JVM_MAX_METASPACE=512m
ENV JVM_CPUS=4
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java -XX:+UseG1GC -Xms128m -Xmx${JVM_MAX_HEAP} -XX:MaxMetaspaceSize=${JVM_MAX_METASPACE} -XX:ActiveProcessorCount=${JVM_CPUS} -XX:+UseStringDeduplication -XX:G1PeriodicGCInterval=60000 -XX:+G1PeriodicGCInvokesConcurrent -XX:+ExitOnOutOfMemoryError -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 $JAVA_OPTS -jar app.jar"]
