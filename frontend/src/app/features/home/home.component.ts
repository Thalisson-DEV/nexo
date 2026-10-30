import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

interface CesModule {
  code: string;
  name: string;
  description: string;
  highlight: string;
  icon: string;
  route: string;
}

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomeComponent {
  protected readonly modules: CesModule[] = [
    {
      code: '01',
      name: 'Almoxarifado',
      description: 'Materiais e estoques',
      highlight: 'Fluxos de materiais em movimento.',
      icon: 'pi pi-box',
      route: '/almoxarifado/solicitacoes',
    },
    {
      code: '02',
      name: 'Execução',
      description: 'Operação e produtividade',
      highlight: 'Acompanhe a operação onde ela acontece.',
      icon: 'pi pi-bolt',
      route: '/execucao/dashboard-operacional',
    },
    {
      code: '03',
      name: 'Segurança do trabalho',
      description: 'Inspeções e prevenção',
      highlight: 'Prevenção incorporada à rotina.',
      icon: 'pi pi-shield',
      route: '/sst/inspecoes',
    },
    {
      code: '04',
      name: 'Departamento pessoal',
      description: 'Pessoas e folha',
      highlight: 'Pessoas e processos organizados.',
      icon: 'pi pi-users',
      route: '/dp/descontos/lancamento',
    },
    {
      code: '05',
      name: 'Financeiro',
      description: 'Compras e pagamentos',
      highlight: 'Decisões financeiras com contexto.',
      icon: 'pi pi-wallet',
      route: '/financeiro/compras',
    },
    {
      code: '06',
      name: 'Administração',
      description: 'Cadastros e acessos',
      highlight: 'Estrutura e acessos sob controle.',
      icon: 'pi pi-cog',
      route: '/admin/cadastros/usuarios',
    },
    {
      code: '07',
      name: 'Chamados',
      description: 'Suporte e atendimento',
      highlight: 'Solicitações que encontram o caminho certo.',
      icon: 'pi pi-ticket',
      route: '/chamados/meus',
    },
  ];

  protected readonly featuredModuleIndex = signal(0);
  protected readonly featuredModule = computed(() => this.modules[this.featuredModuleIndex()]);

  protected featureModule(index: number): void {
    this.featuredModuleIndex.set(index);
  }
}
