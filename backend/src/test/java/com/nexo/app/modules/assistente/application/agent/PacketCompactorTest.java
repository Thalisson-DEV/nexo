package com.nexo.app.modules.assistente.application.agent;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PacketCompactorTest {

    @Test
    void juntaDeltasDaMesmaPosicaoEDescartaHeartbeats() {
        Packet.Placement texto = new Packet.Placement(2, 0);
        Packet.Placement ferramenta = new Packet.Placement(1, 1);
        List<Packet> pacotes = List.of(
                Packet.of(ferramenta, Packet.Types.TOOL_START, "toolCallId", "c1"),
                Packet.of(ferramenta, Packet.Types.TOOL_OUTPUT, "toolCallId", "c1", "stream", "stdout", "data", "linha 1\n"),
                Packet.of(ferramenta, Packet.Types.TOOL_OUTPUT, "toolCallId", "c1", "stream", "stdout", "data", "linha 2\n"),
                Packet.of(ferramenta, Packet.Types.TOOL_OUTPUT, "toolCallId", "c1", "stream", "stderr", "data", "aviso"),
                Packet.of(ferramenta, Packet.Types.TOOL_RESULT, "toolCallId", "c1", "status", "ok"),
                Packet.of(Packet.Placement.ROOT, Packet.Types.HEARTBEAT),
                Packet.of(texto, Packet.Types.MESSAGE_START),
                Packet.of(texto, Packet.Types.MESSAGE_DELTA, "content", "Ola, "),
                Packet.of(texto, Packet.Types.MESSAGE_DELTA, "content", "mundo"),
                Packet.of(texto, Packet.Types.SECTION_END));

        List<Packet> compactados = PacketCompactor.compactar(pacotes);

        assertThat(compactados).extracting(Packet::type).containsExactly(Packet.Types.TOOL_START, Packet.Types.TOOL_OUTPUT,
                Packet.Types.TOOL_OUTPUT, Packet.Types.TOOL_RESULT, Packet.Types.MESSAGE_START, Packet.Types.MESSAGE_DELTA,
                Packet.Types.SECTION_END);
        assertThat(compactados.get(1).obj().get("data")).isEqualTo("linha 1\nlinha 2\n");
        assertThat(compactados.get(5).obj().get("content")).isEqualTo("Ola, mundo");
    }
}
