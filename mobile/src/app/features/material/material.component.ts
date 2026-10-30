import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { db } from '../../core/db/app-db';
import { TokenService } from '../../core/auth/token.service';
import { IconeComponent, IndicadorRedeComponent, TituloGrandeComponent } from '../../ui';
import { DbService } from '../../core/db/db.service';
import { DIAS_PARADO, diasDesde } from '../../core/saldo/tempo-saldo';

/**
 * Aba Material: pedidos, transferências e saldo do caminhão são o mesmo assunto
 * para o encarregado, então ficam num destino só.
 */
@Component({
  selector: 'app-material',
  standalone: true,
  imports: [RouterLink, TituloGrandeComponent, IconeComponent, IndicadorRedeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ces-titulo-grande titulo="Material" subtitulo="Pedidos, transferências e saldo">
      <ces-indicador-rede />
    </ces-titulo-grande>

    <h2 class="ces-secao">Novo</h2>
    <ul class="ces-grupo">
      <li><a class="ces-linha ces-linha--acao" routerLink="/solicitacoes/nova">
        <span class="ces-selo ces-selo--destaque"><ces-icone nome="documento" /></span>
        <span class="ces-linha__texto"><span class="ces-linha__titulo">Pedir material</span>
          <span class="ces-linha__apoio">Do almoxarifado para o caminhão</span></span>
        <ces-icone nome="avancar" [tamanho]="18" /></a></li>
      <li><a class="ces-linha ces-linha--acao" routerLink="/transferencias/nova">
        <span class="ces-selo ces-selo--destaque"><ces-icone nome="transferencia" /></span>
        <span class="ces-linha__texto"><span class="ces-linha__titulo">Transferir</span>
          <span class="ces-linha__apoio">Para outro estoque, com assinatura</span></span>
        <ces-icone nome="avancar" [tamanho]="18" /></a></li>
    </ul>

    <h2 class="ces-secao">Consultar</h2>
    <ul class="ces-grupo">
      <li><a class="ces-linha ces-linha--acao" routerLink="/solicitacoes">
        <span class="ces-selo"><ces-icone nome="documento" /></span>
        <span class="ces-linha__texto"><span class="ces-linha__titulo">Meus pedidos</span>
          <span class="ces-linha__apoio ces-num">{{ pendentes() ? pendentes() + ' aguardando aprovação' : 'Situação de cada pedido' }}</span></span>
        <ces-icone nome="avancar" [tamanho]="18" /></a></li>
      <li><a class="ces-linha ces-linha--acao" routerLink="/transferencias">
        <span class="ces-selo"><ces-icone nome="transferencia" /></span>
        <span class="ces-linha__texto"><span class="ces-linha__titulo">Transferências</span>
          <span class="ces-linha__apoio">Feitas por este aparelho</span></span>
        <ces-icone nome="avancar" [tamanho]="18" /></a></li>
      <li><a class="ces-linha ces-linha--acao" routerLink="/solicitacoes/estoque">
        <span class="ces-selo"><ces-icone nome="caminhao" /></span>
        <span class="ces-linha__texto"><span class="ces-linha__titulo">Saldo do caminhão</span>
          <span class="ces-linha__apoio ces-num">{{ itensSaldo() }} {{ itensSaldo() === 1 ? 'material' : 'materiais' }} com saldo
            @if (parados()) { · <span class="parados">{{ parados() }} {{ parados() === 1 ? 'parado' : 'parados' }} há {{ diasParado }}+ dias</span> }</span></span>
        <ces-icone nome="avancar" [tamanho]="18" /></a></li>
    </ul>
  `,
  styles: [`:host { display: block; padding-bottom: var(--ces-e6); } .parados { font-weight: 600; color: var(--ces-atencao); }`],
})
export class MaterialComponent implements OnInit {
  private readonly tokens = inject(TokenService);
  readonly pendentes = signal(0);
  readonly itensSaldo = signal(0);
  readonly parados = signal(0);
  readonly diasParado = DIAS_PARADO;
  private readonly dbService = inject(DbService);

  async ngOnInit(): Promise<void> {
    const idEstoque = this.tokens.estoqueId();
    const [pendentes, saldo] = await Promise.all([
      db.solicitacoes.where('status').equals('PENDENTE').count(),
      idEstoque ? db.saldos.where('idEstoque').equals(idEstoque).filter(s => s.quantidade > 0).count() : Promise.resolve(0),
    ]);
    this.pendentes.set(pendentes);
    this.itensSaldo.set(saldo);
    if (idEstoque) {
      const linhas = await this.dbService.getSaldosComMateriais(idEstoque);
      this.parados.set(linhas.filter(l => (diasDesde(l.detalhaSerie && l.serieMaisAntiga ? l.serieMaisAntiga : l.ultimaMovimentacao) ?? 0) >= DIAS_PARADO).length);
    }
  }
}
