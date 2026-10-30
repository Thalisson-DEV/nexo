import { describe, expect, it } from 'vitest';
import { contagemSlaClass, contagemSlaLabel, prazoClass, prazoIcon } from './chamados-ui';

describe('chamados-ui SLA helpers', () => {
  describe('contagemSlaLabel', () => {
    it('deve retornar Concluída no prazo para chamados resolvidos com SLA em dia', () => {
      expect(
        contagemSlaLabel({
          status: 'RESOLVIDO',
          slaResolucaoVencido: false,
          slaPausado: false
        })
      ).toBe('Concluída no prazo');
    });

    it('deve retornar Concluída no prazo para chamados fechados com SLA em dia', () => {
      expect(
        contagemSlaLabel({
          status: 'FECHADO',
          slaResolucaoVencido: false,
          slaPausado: false
        })
      ).toBe('Concluída no prazo');
    });

    it('deve retornar Concluída fora do prazo para chamados resolvidos/fechados com SLA vencido', () => {
      expect(
        contagemSlaLabel({
          status: 'RESOLVIDO',
          slaResolucaoVencido: true,
          slaPausado: false
        })
      ).toBe('Concluída fora do prazo');

      expect(
        contagemSlaLabel({
          status: 'FECHADO',
          slaResolucaoVencido: true,
          slaPausado: false
        })
      ).toBe('Concluída fora do prazo');
    });

    it('deve retornar Cancelada para chamados cancelados', () => {
      expect(
        contagemSlaLabel({
          status: 'CANCELADO',
          slaResolucaoVencido: false,
          slaPausado: false
        })
      ).toBe('Cancelada');
    });

    it('deve retornar Pausada para chamados com SLA pausado', () => {
      expect(
        contagemSlaLabel({
          status: 'AGUARDANDO_SOLICITANTE',
          slaResolucaoVencido: false,
          slaPausado: true
        })
      ).toBe('Pausada');
    });

    it('deve retornar Vencida para chamados em andamento com SLA vencido', () => {
      expect(
        contagemSlaLabel({
          status: 'EM_ATENDIMENTO',
          slaResolucaoVencido: true,
          slaPausado: false
        })
      ).toBe('Vencida');
    });

    it('deve retornar Em andamento para chamados em curso com SLA no prazo', () => {
      expect(
        contagemSlaLabel({
          status: 'EM_ATENDIMENTO',
          slaResolucaoVencido: false,
          slaPausado: false
        })
      ).toBe('Em andamento');
    });
  });

  describe('contagemSlaClass', () => {
    it('deve retornar classe de sucesso para resolvido/fechado no prazo', () => {
      expect(
        contagemSlaClass({
          status: 'RESOLVIDO',
          slaResolucaoVencido: false,
          slaPausado: false
        })
      ).toContain('!text-emerald-600');
    });

    it('deve retornar classe de erro para resolvido/fechado fora do prazo', () => {
      expect(
        contagemSlaClass({
          status: 'FECHADO',
          slaResolucaoVencido: true,
          slaPausado: false
        })
      ).toContain('!text-red-600');
    });

    it('deve retornar classe neutra para cancelado', () => {
      expect(
        contagemSlaClass({
          status: 'CANCELADO',
          slaResolucaoVencido: false,
          slaPausado: false
        })
      ).toContain('!text-slate-500');
    });
  });

  describe('prazoClass e prazoIcon', () => {
    it('deve indicar cancelado com ícone de cancelamento e cor neutra', () => {
      const chamado = {
        status: 'CANCELADO' as const,
        slaResolucaoVencido: false,
        slaPausado: false,
        dataLimiteResolucao: new Date(Date.now() - 3600000).toISOString()
      };
      expect(prazoClass(chamado)).toContain('text-slate-500');
      expect(prazoIcon(chamado)).toBe('pi pi-times-circle');
    });

    it('deve indicar resolvido no prazo como sucesso e não como alerta de prazo próximo', () => {
      const chamado = {
        status: 'RESOLVIDO' as const,
        slaResolucaoVencido: false,
        slaPausado: false,
        dataLimiteResolucao: new Date(Date.now() - 3600000).toISOString()
      };
      expect(prazoClass(chamado)).toContain('text-emerald-700');
      expect(prazoIcon(chamado)).toBe('pi pi-check-circle');
    });

    it('deve indicar resolvido fora do prazo como perigo', () => {
      const chamado = {
        status: 'RESOLVIDO' as const,
        slaResolucaoVencido: true,
        slaPausado: false,
        dataLimiteResolucao: new Date(Date.now() - 3600000).toISOString()
      };
      expect(prazoClass(chamado)).toContain('text-red-700');
      expect(prazoIcon(chamado)).toBe('pi pi-exclamation-circle');
    });
  });
});
