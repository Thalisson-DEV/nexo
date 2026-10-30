import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

@Component({
  selector: 'app-manual-descontos',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    CardModule,
    TableModule,
    TagModule,
    TooltipModule
  ],
  templateUrl: './manual-descontos.component.html'
})
export class ManualDescontosComponent {
  
  protected readonly abs = Math.abs;

  protected mockDescontos = [
    { 
        colaborador: 'João Silva', 
        motivo: 'Perda de EPI', 
        valorSugerido: 150.00, 
        valorValidado: null,
        valorBaixado: null,
        divergenciaValidado: null,
        status: 'SOLICITADO', 
        statusClass: 'bg-orange-100 text-orange-700 border border-orange-200'
    },
    { 
        colaborador: 'Maria Oliveira', 
        motivo: 'Avaria de Frota', 
        valorSugerido: 800.00,
        valorValidado: 800.00,
        valorBaixado: null,
        divergenciaValidado: 0,
        status: 'VALIDADO', 
        statusClass: 'bg-blue-100 text-blue-700 border border-blue-200'
    },
    { 
        colaborador: 'Carlos Souza', 
        motivo: 'Mau uso Ferramenta', 
        valorSugerido: 45.00,
        valorValidado: 50.00,
        valorBaixado: 50.00,
        divergenciaValidado: 5.00,
        status: 'BAIXADO', 
        statusClass: 'bg-green-100 text-green-700 border border-green-200'
    },
  ];
}
