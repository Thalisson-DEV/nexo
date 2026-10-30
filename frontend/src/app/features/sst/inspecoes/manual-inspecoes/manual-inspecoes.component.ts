import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { TableModule } from 'primeng/table';

@Component({
  selector: 'app-manual-inspecoes',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    TableModule
  ],
  templateUrl: './manual-inspecoes.component.html'
})
export class ManualInspecoesComponent {
  
  // Mocks realistas para a tabela de listagem
  mockInspecoes = [
    {
      formulario: 'Inspeção Diária de Veículos (Checklist Frota)',
      processo: 'Manutenção de Redes (Linha Viva)',
      inspetor: 'Roberto Almeida (Téc. Seg. Trabalho)',
      data: new Date('2026-07-30T07:15:00'),
      desvios: 2,
      status: 'REALIZADA',
      statusClass: 'bg-emerald-50 text-emerald-700 border-emerald-200'
    },
    {
      formulario: 'Auditoria de EPI / EPC (Equipe Leve)',
      processo: 'Corte e Ligação',
      inspetor: 'Camila Santos (Téc. Seg. Trabalho)',
      data: new Date('2026-07-29T14:30:00'),
      desvios: 0,
      status: 'REALIZADA',
      statusClass: 'bg-emerald-50 text-emerald-700 border-emerald-200'
    },
    {
      formulario: 'Inspeção de Pátio e Almoxarifado',
      processo: 'Logística',
      inspetor: 'João Silva (Supervisor)',
      data: new Date('2026-07-29T08:00:00'),
      desvios: 5,
      status: 'PENDENTE_SINC',
      statusClass: 'bg-amber-50 text-amber-700 border-amber-200'
    }
  ];

  abs(value: number): number {
    return Math.abs(value);
  }
}
