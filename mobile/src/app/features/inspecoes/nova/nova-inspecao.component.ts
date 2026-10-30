import { ChangeDetectionStrategy, Component, Injector, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { DbService } from '../../../core/db/db.service';
import { OutboxService } from '../../../core/sync/outbox.service';
import { TokenService } from '../../../core/auth/token.service';
import { AuthService } from '../../../core/auth/auth.service';
import { SyncService } from '../../../core/sync/sync.service';
import { ToastService } from '../../../core/ui/toast.service';
import { RascunhoService, VinculoRascunho } from '../../../core/sessao/rascunho.service';
import { RascunhoStore } from '../../../core/sessao/rascunho.store';
import { SessaoService } from '../../../core/sessao/sessao.service';
import {
  BaseOperacionalLocal, ColaboradorLocal, EquipeLocal, FormularioLocal, InspecaoLocal, ProcessoLocal, TipoInspecaoLocal, VeiculoLocal,
} from '../../../core/db/models';
import { BaseOperacionalService } from '../../../core/sst/base-operacional.service';
import {
  AnexoPendenteLocal, EvidenciaLocal, FormularioInspecao, RegistrarInspecaoRequest, sanitizarRegistrarInspecao,
  validarArquivoEvidencia, validarRegistrarInspecao,
} from '../../../core/sst/inspecao.models';
import { formatarTamanho, prepararEvidencia } from '../../../core/sst/evidencia-foto.util';
import { InspecaoService } from '../../../core/sst/inspecao.service';
import {
  BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, OpcaoSeletor, SeletorComponent, VazioComponent,
} from '../../../ui';

interface RespostaDraft {
  idPergunta: string;
  valorTexto?: string;
  valorNumero?: number;
  valorData?: string;
  opcoesSelecionadas?: string[];
}

interface DesvioDraft {
  idPergunta: string;
  idOpcaoResposta: string;
  descricao: string;
  acaoCorretiva: string;
  prazo?: string;
  colaboradores: string[];
  tratadoInLoco?: boolean;
}

/** Foto guardada no aparelho para o rascunho: o arquivo fica no banco de sessão; aqui só a referência. */
interface FotoRascunho {
  idLocal: string;
  idArquivo: string;
  nomeArquivo: string;
  contentType: string;
  tamanhoBytes?: number;
  idRemoto?: string | null;
  /** Só para fotos de desvio: `<idPergunta>:<idOpcaoResposta>`. */
  chaveDesvio?: string;
}

/** Tudo o que o técnico já fez na inspeção — gravado a cada toque, fotos no instante da captura. */
interface RascunhoInspecao {
  passo: number;
  idFormulario: string | null;
  idTipoInspecao: string | null;
  idProcesso: string | null;
  idEquipe: string | null;
  nomeEquipe: string;
  idBaseOperacional: string | null;
  idVeiculo: string | null;
  idColaboradores: string[];
  observacoes: string;
  respostas: RespostaDraft[];
  desvios: DesvioDraft[];
  evidencias: FotoRascunho[];
  fotosDesvio: FotoRascunho[];
  seletorAberto: string | null;
}

const PREFIXO_EVIDENCIA = 'evidencia:';
const PREFIXO_DESVIO = 'desvio:';
/** Arquivo bruto gravado antes de comprimir: se o app morrer comprimindo, a foto não se perde. */
const PREFIXO_BRUTO = 'bruto:';

/**
 * Inspeção de segurança: identificação → questionário → [desvios] → fotos → resumo.
 *
 * Fotos vão para o armazenamento do aparelho no instante da captura — abrir a câmera
 * pode fazer o Android matar o app, e a foto que volta não pode se perder. O
 * formulário inteiro é rascunho contínuo (salva sozinho a cada mudança) e aparece em
 * Inspeções como "Não terminada" — não há botão de salvar que tire a pessoa da tela.
 */
@Component({
  selector: 'app-nova-inspecao',
  standalone: true,
  imports: [BarraTopoComponent, FolhaComponent, IconeComponent, IndicadorRedeComponent, SeletorComponent, VazioComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="tela">
      <ces-barra-topo [titulo]="draftId() ? 'Inspeção em rascunho' : 'Nova inspeção'" [subtitulo]="stepTitle() + ' · ' + currentStep() + ' de ' + totalSteps()"
        navegacao="fechar" (voltar)="fechar()">
        <span class="auto-salvo" role="status" aria-label="Salvo automaticamente" title="Salvo automaticamente"><ces-icone nome="check" [tamanho]="14" />Salvo</span>
      </ces-barra-topo>
      <div class="passos" aria-hidden="true">
        @for (s of stepsArray(); track s) { <i [class.feito]="s <= currentStep()"></i> }
      </div>

      @if (isLoading()) {
        <div class="esqueletos" aria-busy="true" aria-label="Carregando">
          @for (i of [1, 2, 3]; track i) { <div class="ces-esqueleto" style="height: 64px"></div> }
        </div>
      } @else {
        <section class="conteudo" [attr.aria-label]="stepTitle()">
          @if (currentStep() === 1) {
            <div class="campos">
              <ces-seletor rotulo="Formulário" [obrigatorio]="true" [opcoes]="opcoesFormulario()" [valor]="idFormulario()"
                (valorChange)="onFormularioChange($event!)" [invalido]="tentou() && !idFormulario()"
                [aberto]="seletorAberto() === 'formulario'" (abertoChange)="abrirSeletor('formulario', $event)" />
              <ces-seletor rotulo="Tipo de inspeção" [obrigatorio]="true" [opcoes]="opcoesTipo()" [valor]="idTipoInspecao()"
                (valorChange)="idTipoInspecao.set($event)" [invalido]="tentou() && !idTipoInspecao()"
                [aberto]="seletorAberto() === 'tipo'" (abertoChange)="abrirSeletor('tipo', $event)" />
              <ces-seletor rotulo="Processo" [obrigatorio]="true" [opcoes]="opcoesProcesso()" [valor]="idProcesso()"
                (valorChange)="idProcesso.set($event)" [invalido]="tentou() && !idProcesso()"
                [aberto]="seletorAberto() === 'processo'" (abertoChange)="abrirSeletor('processo', $event)" />
              @if (isAdministrativo()) {
                <ces-seletor rotulo="Equipe" [opcoes]="opcoesEquipe()" [valor]="idEquipe()" (escolhido)="selecionarEquipe($event.valor)"
                  [aberto]="seletorAberto() === 'equipe'" (abertoChange)="abrirSeletor('equipe', $event)" />
              } @else {
                <div class="fixo"><span>Equipe</span><strong>{{ nomeEquipe() }}</strong></div>
              }
              @if (basesAccessDenied()) {
                <p class="aviso"><ces-icone nome="info" [tamanho]="18" />Seu perfil não consulta bases operacionais. Peça a permissão ao administrador.</p>
              } @else {
                <ces-seletor rotulo="Base operacional" [obrigatorio]="true" [opcoes]="opcoesBase()" [valor]="idBaseOperacional()"
                  (valorChange)="idBaseOperacional.set($event)" [invalido]="tentou() && !idBaseOperacional()"
                  [aberto]="seletorAberto() === 'base'" (abertoChange)="abrirSeletor('base', $event)" />
              }
              <ces-seletor rotulo="Veículo" placeholder="Opcional" [opcoes]="opcoesVeiculo()" [valor]="idVeiculo()" (valorChange)="idVeiculo.set($event)"
                [aberto]="seletorAberto() === 'veiculo'" (abertoChange)="abrirSeletor('veiculo', $event)" />
              <ces-seletor rotulo="Inspecionados" [obrigatorio]="true" [multiplo]="true" [opcoes]="opcoesColaborador()"
                [valores]="idColaboradoresInspecionados()" (valoresChange)="idColaboradoresInspecionados.set($event)"
                [invalido]="tentou() && !idColaboradoresInspecionados().length"
                [aberto]="seletorAberto() === 'inspecionados'" (abertoChange)="abrirSeletor('inspecionados', $event)" />
            </div>
          }

          @if (currentStep() === 2) {
            @if (formularioAtivo(); as form) {
              @for (grupo of form.grupos; track grupo.id) {
                <h2 class="ces-secao">{{ grupo.titulo }}</h2>
                <div class="perguntas">
                  @for (p of grupo.perguntas; track p.id) {
                    <fieldset class="pergunta" [class.pendente]="tentou() && p.obrigatoria && !respondida(p)">
                      <legend>{{ p.enunciado }}@if (p.obrigatoria) {<span aria-hidden="true"> *</span>}</legend>
                      @switch (p.tipoResposta) {
                        @case ('MULTIPLA_ESCOLHA') {
                          <div class="opcoes" role="radiogroup" [attr.aria-label]="p.enunciado">
                            @for (o of p.opcoes; track o.id) {
                              <button type="button" role="radio" class="opcao" [attr.aria-checked]="isOpcaoSelecionada(p.id, o.id)"
                                [class.gera-desvio]="o.geraDesvio && isOpcaoSelecionada(p.id, o.id)" (click)="setOpcaoRespostaUnica(p.id, o.id)">
                                <span class="radio" aria-hidden="true"></span>{{ o.texto }}
                              </button>
                            }
                          </div>
                        }
                        @case ('MULTIPLA_SELECAO') {
                          <div class="opcoes" role="group" [attr.aria-label]="p.enunciado">
                            @for (o of p.opcoes; track o.id) {
                              <button type="button" role="checkbox" class="opcao" [attr.aria-checked]="isOpcaoSelecionada(p.id, o.id)"
                                [class.gera-desvio]="o.geraDesvio && isOpcaoSelecionada(p.id, o.id)" (click)="toggleOpcaoRespostaMultipla(p.id, o.id)">
                                <span class="caixa" aria-hidden="true"><ces-icone nome="check" [tamanho]="14" /></span>{{ o.texto }}
                              </button>
                            }
                          </div>
                        }
                        @case ('TEXTO') {
                          <textarea class="ces-input texto" rows="3" [value]="getTextoResposta(p.id)" (input)="setTextoResposta(p.id, $event)"
                            [attr.aria-label]="p.enunciado" placeholder="Escreva a resposta"></textarea>
                        }
                        @case ('NUMERO') {
                          <input class="ces-input" type="text" inputmode="decimal" [value]="getNumeroResposta(p.id) ?? ''" (input)="setNumeroResposta(p.id, $event)"
                            [attr.aria-label]="p.enunciado" placeholder="0">
                        }
                        @case ('DATA') {
                          <input class="ces-input" type="date" [value]="getDataResposta(p.id)" (input)="setDataResposta(p.id, $event)" [attr.aria-label]="p.enunciado">
                        }
                      }
                    </fieldset>
                  }
                </div>
              }
            }
          }

          @if (hasDesvios() && currentStep() === 3) {
            <p class="intro"><ces-icone nome="desvio" [tamanho]="18" />Algumas respostas geraram desvio. Diga o que foi feito em cada um.</p>
            @for (d of desviosGerados(); track d.idPergunta + d.idOpcaoResposta) {
              <article class="desvio" [class.tratado]="d.tratadoInLoco">
                <header>
                  <span class="ref">{{ getPerguntaEnunciado(d.idPergunta) }}</span>
                  <strong>{{ getOpcaoTexto(d.idPergunta, d.idOpcaoResposta) }}</strong>
                </header>
                <button type="button" role="switch" class="interruptor" [attr.aria-checked]="!!d.tratadoInLoco" (click)="toggleTratadoInLoco(d.idPergunta)">
                  <span class="txt"><b>Corrigido na hora</b><span>{{ d.tratadoInLoco ? 'Vai como resolvido.' : 'Marque se já foi sanado no local.' }}</span></span>
                  <span class="trilho" aria-hidden="true"><span></span></span>
                </button>
                <div class="ces-campo">
                  <label [for]="'acao-' + d.idPergunta">{{ d.tratadoInLoco ? 'O que foi feito *' : 'Ação corretiva *' }}</label>
                  <textarea class="ces-input texto" rows="3" [id]="'acao-' + d.idPergunta" [value]="d.acaoCorretiva"
                    (input)="updateDesvioAcao(d.idPergunta, $any($event.target).value)"
                    [attr.aria-invalid]="tentou() && !d.acaoCorretiva.trim() ? 'true' : null"></textarea>
                </div>
                <div class="ces-campo">
                  <span class="ces-campo__rotulo">Pessoas envolvidas *</span>
                  <div class="opcoes">
                    @for (c of getColaboradoresInspecionadosList(); track c.id) {
                      <button type="button" role="checkbox" class="opcao" [attr.aria-checked]="d.colaboradores.includes(c.id)"
                        (click)="toggleColaboradorDesvio(d.idPergunta, c.id)">
                        <span class="caixa" aria-hidden="true"><ces-icone nome="check" [tamanho]="14" /></span>{{ c.nomeCompleto }}
                      </button>
                    }
                  </div>
                </div>
                @if (!d.tratadoInLoco) {
                  <div class="ces-campo">
                    <label [for]="'prazo-' + d.idPergunta">Prazo</label>
                    <input class="ces-input" type="date" [id]="'prazo-' + d.idPergunta" [value]="d.prazo ?? ''" (input)="updateDesvioPrazo(d.idPergunta, $any($event.target).value)">
                  </div>
                }
                <div class="ces-campo">
                  <span class="ces-campo__rotulo">Foto do desvio</span>
                  @if (previewDesvio(d); as url) {
                    <div class="foto-desvio">
                      <img [src]="url" alt="Foto do desvio">
                      <button type="button" class="ces-btn ces-btn--perigo ces-btn--compacto" (click)="removerFoto(d.idPergunta)">Tirar foto</button>
                    </div>
                  } @else if (temFotoDesvio(d)) {
                    <div class="foto-desvio"><span class="ces-selo ces-selo--ok"><ces-icone nome="check" /></span>Foto anexada
                      <button type="button" class="ces-btn ces-btn--perigo ces-btn--compacto" (click)="removerFoto(d.idPergunta)">Tirar foto</button></div>
                  } @else {
                    <div class="botoes-foto">
                      <label class="ces-btn ces-btn--tonal"><ces-icone nome="camera" [tamanho]="18" />Câmera
                        <input type="file" accept="image/*" capture="environment" class="ces-sr-only" (change)="onFotoSelected($event, d.idPergunta)"></label>
                      <label class="ces-btn ces-btn--neutro">Galeria
                        <input type="file" accept="image/*" class="ces-sr-only" (change)="onFotoSelected($event, d.idPergunta)"></label>
                    </div>
                  }
                </div>
              </article>
            }
          }

          @if (currentStep() === stepEvidencias()) {
            <p class="intro"><ces-icone nome="camera" [tamanho]="18" />Ao menos uma foto do local. Ela fica guardada no aparelho na hora em que é tirada.</p>
            <div class="botoes-foto evid">
              <label class="ces-btn ces-btn--principal"><ces-icone nome="camera" [tamanho]="18" />Tirar foto
                <input id="evidencia-camera" type="file" accept="image/*" capture="environment" multiple class="ces-sr-only" (change)="onEvidenciasSelected($event)"></label>
              <label class="ces-btn ces-btn--neutro">Da galeria
                <input id="evidencia-gallery" type="file" accept="image/jpeg,image/png,image/webp,image/heic,image/heif" multiple class="ces-sr-only" (change)="onEvidenciasSelected($event)"></label>
            </div>
            @if (isPreparandoEvidencia()) {
              <p class="preparando" aria-live="polite"><span class="giro" aria-hidden="true"></span>Preparando a foto…</p>
            }
            @if (evidencias().length) {
              <ul class="fotos" aria-label="Fotos da inspeção">
                @for (e of evidencias(); track e.idLocal) {
                  <li>
                    @if (previewEvidencia(e.idLocal); as url) { <img [src]="url" [alt]="e.nomeArquivo"> }
                    @else { <span class="sem-previa"><ces-icone nome="camera" /></span> }
                    <span class="tam ces-num">{{ tamanhoEvidencia(e) }}</span>
                    <button type="button" class="tirar" (click)="removerEvidencia(e.idLocal)" [attr.aria-label]="'Tirar foto ' + e.nomeArquivo">
                      <ces-icone nome="fechar" [tamanho]="16" />
                    </button>
                  </li>
                }
              </ul>
            } @else if (!isPreparandoEvidencia()) {
              <ces-vazio icone="camera" titulo="Nenhuma foto ainda" texto="Sem foto a inspeção não pode ser enviada." />
            }
          }

          @if (currentStep() === totalSteps()) {
            <dl class="resumo">
              <div><dt>Formulário</dt><dd>{{ nomeFormulario() }}</dd></div>
              <div><dt>Tipo</dt><dd>{{ nomeTipoInspecao() }}</dd></div>
              <div><dt>Processo</dt><dd>{{ getProcessoNome() }}</dd></div>
              <div><dt>Equipe</dt><dd>{{ nomeEquipe() || '—' }}</dd></div>
              <div><dt>Base</dt><dd>{{ nomeBaseOperacional() || '—' }}</dd></div>
              <div><dt>Inspecionados</dt><dd>{{ getInspecionadosNomes().join(', ') }}</dd></div>
              <div><dt>Respostas</dt><dd class="ces-num">{{ countRespostas() }}</dd></div>
              <div><dt>Desvios</dt><dd class="ces-num">{{ desviosGerados().length }}</dd></div>
              <div><dt>Fotos</dt><dd class="ces-num">{{ evidencias().length }}</dd></div>
            </dl>
            <p class="dica"><ces-icone nome="nuvem" [tamanho]="18" />Sem internet, fica salva no aparelho com as fotos e sai sozinha quando o sinal voltar.</p>
          }
        </section>
      }
    </div>

    <footer class="rodape">
      @if (currentStep() > 1) {
        <button type="button" class="ces-btn ces-btn--neutro" (click)="voltarPasso()">Voltar</button>
      }
      <button type="button" class="ces-btn ces-btn--principal principal" (click)="avancar()" [disabled]="isSaving() || isLoading() || isPreparandoEvidencia()">
        @if (currentStep() === totalSteps()) { {{ isSaving() ? 'Salvando…' : 'Finalizar inspeção' }} }
        @else { Continuar <ces-icone nome="seta" [tamanho]="18" /> }
      </button>
    </footer>
  `,
  styles: [`
    .auto-salvo { display: inline-flex; align-items: center; gap: 4px; font: 500 .75rem/1.2 var(--ces-fonte); color: var(--ces-ok); white-space: nowrap; }
    :host { display: flex; flex-direction: column; min-height: 100%; background: var(--ces-fundo); }
    /* Nada encolhe: com conteúdo maior que a tela, a página rola (grupos com overflow:hidden eram cortados). */
    :host > * { flex-shrink: 0; }
    .tela { flex: 1; padding-bottom: var(--ces-e5); }
    .passos { display: grid; grid-auto-flow: column; grid-auto-columns: 1fr; gap: 4px; padding: 0 var(--ces-e5) var(--ces-e3); }
    .passos i { height: 4px; border-radius: var(--ces-r-pilula); background: var(--ces-linha); }
    .passos i.feito { background: var(--ces-destaque); }
    .esqueletos { display: grid; gap: var(--ces-e2); padding: var(--ces-e4); }
    .conteudo { animation: entrar var(--ces-m-entrar) var(--ces-curva); }
    @keyframes entrar { from { opacity: 0; transform: translateY(6px); } }
    .campos { display: grid; gap: var(--ces-e2); padding: var(--ces-e2) var(--ces-e3); }
    .fixo { display: grid; gap: 2px; padding: var(--ces-e3) var(--ces-e4); background: var(--ces-superficie); border-radius: var(--ces-r-grupo); }
    .fixo span { font: 500 .8125rem/1.3 var(--ces-fonte); color: var(--ces-texto-2); }
    .aviso, .intro, .dica, .preparando { display: flex; gap: var(--ces-e2); align-items: flex-start; font: var(--ces-t-apoio); font-size: .875rem; }
    .aviso { padding: var(--ces-e3) var(--ces-e4); border-radius: var(--ces-r-grupo); background: var(--ces-atencao-suave); color: var(--ces-texto); margin: 0; }
    .intro { margin: var(--ces-e2) var(--ces-e5) var(--ces-e3); color: var(--ces-texto-2); }
    .dica { margin: var(--ces-e4) var(--ces-e5) 0; color: var(--ces-texto-2); }
    .perguntas { display: grid; gap: var(--ces-e2); padding: 0 var(--ces-e3); }
    .pergunta { margin: 0; border: 0; padding: var(--ces-e4); background: var(--ces-superficie); border-radius: var(--ces-r-grupo); display: grid; gap: var(--ces-e3); min-width: 0; }
    .pergunta.pendente { box-shadow: inset 0 0 0 1.5px var(--ces-erro); }
    legend { float: left; width: 100%; padding: 0; font: var(--ces-t-corpo-forte); }
    legend + * { clear: both; }
    .opcoes { display: grid; gap: var(--ces-e2); }
    .opcao { display: flex; align-items: center; gap: var(--ces-e3); min-height: 48px; padding: var(--ces-e2) var(--ces-e3); border: 1.5px solid var(--ces-linha);
      border-radius: var(--ces-r-campo); background: var(--ces-superficie); color: var(--ces-texto); text-align: left; font: var(--ces-t-corpo); cursor: pointer; }
    .opcao[aria-checked='true'] { border-color: var(--ces-destaque); background: var(--ces-destaque-suave); }
    .opcao.gera-desvio[aria-checked='true'] { border-color: var(--ces-atencao); background: var(--ces-atencao-suave); }
    .radio { width: 20px; height: 20px; border-radius: 50%; border: 2px solid var(--ces-linha-forte); flex: none; }
    [aria-checked='true'] > .radio { border: 6px solid var(--ces-destaque); }
    .caixa { width: 22px; height: 22px; border-radius: 6px; border: 2px solid var(--ces-linha-forte); display: grid; place-items: center; color: transparent; flex: none; }
    [aria-checked='true'] > .caixa { background: var(--ces-destaque); border-color: var(--ces-destaque); color: var(--ces-destaque-texto); }
    .texto { min-height: 96px; padding-top: var(--ces-e3); resize: vertical; }
    .desvio { margin: 0 var(--ces-e3) var(--ces-e3); padding: var(--ces-e4); background: var(--ces-superficie); border-radius: var(--ces-r-grupo);
      display: grid; gap: var(--ces-e4); border-left: 4px solid var(--ces-atencao); }
    .desvio.tratado { border-left-color: var(--ces-ok); }
    .desvio header { display: grid; gap: 2px; }
    .ref { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .interruptor { display: flex; align-items: center; gap: var(--ces-e3); min-height: 56px; padding: 0; border: 0; background: transparent; color: var(--ces-texto); text-align: left; cursor: pointer; }
    .interruptor .txt { flex: 1; display: grid; gap: 2px; }
    .interruptor .txt span { font: var(--ces-t-apoio); color: var(--ces-texto-2); }
    .trilho { width: 48px; height: 28px; border-radius: 14px; background: var(--ces-linha-forte); position: relative; flex: none; transition: background-color var(--ces-m-entrar); }
    .trilho span { position: absolute; top: 3px; left: 3px; width: 22px; height: 22px; border-radius: 50%; background: #fff; transition: transform var(--ces-m-entrar) var(--ces-curva); }
    [aria-checked='true'] .trilho { background: var(--ces-ok); }
    [aria-checked='true'] .trilho span { transform: translateX(20px); }
    .botoes-foto { display: flex; gap: var(--ces-e2); }
    .botoes-foto .ces-btn { flex: 1; position: relative; }
    .botoes-foto.evid { padding: 0 var(--ces-e3) var(--ces-e3); }
    .foto-desvio { display: flex; align-items: center; gap: var(--ces-e3); }
    .foto-desvio img { width: 72px; height: 72px; object-fit: cover; border-radius: var(--ces-r-campo); }
    .preparando { margin: 0 var(--ces-e5) var(--ces-e3); color: var(--ces-texto-2); align-items: center; }
    .giro { width: 16px; height: 16px; border-radius: 50%; border: 2px solid currentColor; border-right-color: transparent; animation: g .8s linear infinite; }
    @keyframes g { to { transform: rotate(360deg); } }
    .fotos { list-style: none; margin: 0; padding: 0 var(--ces-e3); display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: var(--ces-e2); }
    .fotos li { position: relative; aspect-ratio: 1; border-radius: var(--ces-r-campo); overflow: hidden; background: var(--ces-superficie-2); max-width: 100%; }
    .fotos img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .sem-previa { position: absolute; inset: 0; display: grid; place-items: center; color: var(--ces-texto-2); }
    .tam { position: absolute; left: 6px; bottom: 6px; padding: 2px 6px; border-radius: 6px; background: rgba(0, 0, 0, .55); color: #fff; font-size: 11px; }
    .tirar { position: absolute; top: 4px; right: 4px; width: 32px; height: 32px; border-radius: 50%; border: 0; display: grid; place-items: center;
      background: rgba(0, 0, 0, .55); color: #fff; cursor: pointer; }
    .tirar::after { content: ''; position: absolute; inset: -8px; }
    .resumo { margin: var(--ces-e2) var(--ces-e3) 0; padding: var(--ces-e2) var(--ces-e4); background: var(--ces-superficie); border-radius: var(--ces-r-grupo); }
    .resumo > div { display: flex; justify-content: space-between; gap: var(--ces-e3); padding: var(--ces-e3) 0; border-bottom: 1px solid var(--ces-linha); }
    .resumo > div:last-child { border-bottom: 0; }
    dt { color: var(--ces-texto-2); }
    dd { margin: 0; font-weight: 500; text-align: right; overflow-wrap: anywhere; }
    .rodape { position: sticky; bottom: 0; z-index: 5; display: flex; gap: var(--ces-e3); padding: var(--ces-e3) var(--ces-e4) calc(var(--ces-e3) + var(--ces-safe-base));
      background: var(--ces-fundo); border-top: 1px solid var(--ces-linha); }
    .rodape .principal { flex: 1; min-height: var(--ces-alvo-principal); font-size: 1rem; }
  `],
})
export class NovaInspecaoComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly dbService = inject(DbService);
  private readonly outboxService = inject(OutboxService);
  private readonly syncService = inject(SyncService);
  private readonly tokenService = inject(TokenService);
  private readonly toast = inject(ToastService);
  private readonly authService = inject(AuthService);
  private readonly baseOperacionalService = inject(BaseOperacionalService);
  private readonly inspecaoService = inject(InspecaoService);
  private readonly rascunhos = inject(RascunhoService);
  private readonly arquivos = inject(RascunhoStore);
  private readonly sessao = inject(SessaoService);
  private readonly injector = inject(Injector);
  readonly isAdministrativo = this.authService.isAdministrativo;

  readonly draftId = signal<string | null>(null);
  readonly isLoading = signal(true);
  readonly isSaving = signal(false);
  readonly currentStep = signal(1);
  readonly tentou = signal(false);
  readonly seletorAberto = signal<string | null>(null);

  // Catálogos
  readonly equipes = signal<EquipeLocal[]>([]);
  readonly basesOperacionais = signal<BaseOperacionalLocal[]>([]);
  readonly basesAccessDenied = signal(false);
  readonly formularios = signal<FormularioLocal[]>([]);
  readonly tiposInspecao = signal<TipoInspecaoLocal[]>([]);
  readonly processos = signal<ProcessoLocal[]>([]);
  readonly veiculos = signal<VeiculoLocal[]>([]);
  readonly colaboradores = signal<ColaboradorLocal[]>([]);
  readonly latitude = signal<number | undefined>(undefined);
  readonly longitude = signal<number | undefined>(undefined);

  // Contexto
  readonly idEquipe = signal<string | null>(null);
  readonly idBaseOperacional = signal<string | null>(null);
  readonly idVeiculo = signal<string | null>(null);
  readonly nomeEquipe = signal<string>('');
  readonly idFormulario = signal<string | null>(null);
  readonly idTipoInspecao = signal<string | null>(null);
  readonly idProcesso = signal<string | null>(null);
  readonly observacoes = signal<string>('');
  readonly idColaboradoresInspecionados = signal<string[]>([]);

  // Respostas, desvios e fotos
  readonly respostas = signal<RespostaDraft[]>([]);
  readonly desviosGerados = signal<DesvioDraft[]>([]);
  readonly anexosPendentes = signal<AnexoPendenteLocal[]>([]);
  readonly evidencias = signal<EvidenciaLocal[]>([]);
  readonly isPreparandoEvidencia = signal(false);
  private readonly previewsEvidencia = signal<Record<string, string>>({});
  private readonly previewsDesvio = signal<Record<string, string>>({});
  /** idLocal da foto → id do arquivo guardado no aparelho. */
  private readonly arquivoDaFoto = signal<Record<string, string>>({});
  private rascunho: VinculoRascunho | null = null;
  private chaveRascunho = 'inspecao:nova';

  // ---------- opções dos seletores ----------
  readonly opcoesFormulario = computed<OpcaoSeletor[]>(() => this.formularios().map(f => ({ valor: f.id, rotulo: f.titulo, apoio: f.descricao })));
  readonly opcoesTipo = computed<OpcaoSeletor[]>(() => this.tiposInspecao().map(t => ({ valor: t.id, rotulo: t.nome, apoio: t.descricao })));
  readonly opcoesProcesso = computed<OpcaoSeletor[]>(() => this.processos().map(p => ({ valor: p.id, rotulo: p.nome })));
  readonly opcoesEquipe = computed<OpcaoSeletor[]>(() => this.equipes().map(e => ({ valor: e.id, rotulo: e.codigo, apoio: e.nomeBaseOperacional })));
  readonly opcoesBase = computed<OpcaoSeletor[]>(() => this.basesOperacionais().map(b => ({ valor: b.id, rotulo: b.nome })));
  readonly opcoesVeiculo = computed<OpcaoSeletor[]>(() => this.veiculos().map(v => ({ valor: v.id, rotulo: v.placa, apoio: v.modelo })));
  readonly opcoesColaborador = computed<OpcaoSeletor[]>(() => this.colaboradores().map(c => ({ valor: c.id, rotulo: c.nomeCompleto, apoio: c.funcao })));

  readonly nomeFormulario = computed(() => this.formularioAtivo()?.titulo ?? '');
  readonly nomeTipoInspecao = computed(() => this.tiposInspecao().find(x => x.id === this.idTipoInspecao())?.nome ?? '');
  readonly nomeBaseOperacional = computed(() => this.basesOperacionais().find(b => b.id === this.idBaseOperacional())?.nome ?? '');

  readonly formularioAtivo = computed(() => {
    const id = this.idFormulario();
    return id ? this.formularios().find(f => f.id === id) || null : null;
  });

  readonly hasDesvios = computed(() => this.desviosGerados().length > 0);
  // 1 Identificação, 2 Questionário, [Desvios], Fotos, Resumo.
  readonly totalSteps = computed(() => (this.hasDesvios() ? 5 : 4));
  readonly stepEvidencias = computed(() => (this.hasDesvios() ? 4 : 3));
  readonly stepsArray = computed(() => Array.from({ length: this.totalSteps() }, (_, i) => i + 1));
  readonly stepTitle = computed(() => {
    const s = this.currentStep();
    if (s === 1) return 'Identificação';
    if (s === 2) return 'Questionário';
    if (this.hasDesvios() && s === 3) return 'Desvios';
    if (s === this.stepEvidencias()) return 'Fotos';
    if (s === this.totalSteps()) return 'Resumo';
    return '';
  });

  async ngOnInit(): Promise<void> {
    try {
      this.isLoading.set(true);
      await this.carregarCatalogos();
      const editId = this.route.snapshot.queryParamMap.get('editId');
      await this.carregarContexto(editId);
      if (editId) await this.carregarRascunhoSalvo(editId);
      if (!this.idTipoInspecao() && this.tiposInspecao().length > 0) {
        const padrao = this.tiposInspecao().find(t => t.nome.toLowerCase() === 'campo') || this.tiposInspecao()[0];
        this.idTipoInspecao.set(padrao.id);
      }
      await this.vincularRascunho(editId);
    } catch (e) {
      console.error(e);
      this.toast.error('Erro ao carregar dados locais.');
    } finally {
      this.isLoading.set(false);
    }
  }

  ngOnDestroy(): void {
    for (const url of [...Object.values(this.previewsEvidencia()), ...Object.values(this.previewsDesvio())]) URL.revokeObjectURL(url);
  }

  // ---------- carga (mesmas regras da versão anterior) ----------
  private async carregarCatalogos(): Promise<void> {
    let forms = await this.dbService.getFormularios();
    const procs = await this.dbService.getProcessos();
    const veics = await this.dbService.getVeiculos();
    let tipos = await this.dbService.getTiposInspecaoAtivos();
    let bases = await this.baseOperacionalService.obterAtivasDoCache();

    if (bases.length === 0 && navigator.onLine) {
      try { bases = await this.baseOperacionalService.sincronizarAtivas(); } catch { /* tratado abaixo */ }
    }
    if (tipos.length === 0 && navigator.onLine) {
      try {
        const remotos = await firstValueFrom(this.inspecaoService.listarTiposInspecao());
        if (Array.isArray(remotos) && remotos.length > 0) {
          await this.dbService.replaceTiposInspecao(remotos);
          tipos = remotos.filter(t => t.ativo).sort((a, b) => a.ordem - b.ordem);
        }
      } catch { /* offline ou erro na API */ }
    }
    if (forms.length === 0 && navigator.onLine) {
      try {
        const resumos = await firstValueFrom(this.inspecaoService.listarFormulariosAtivos(0, 100));
        const lista = resumos?.content || (Array.isArray(resumos) ? resumos : []);
        const completos: FormularioLocal[] = [];
        for (const resumo of lista) {
          try {
            const completo = await firstValueFrom(this.inspecaoService.obterFormulario(resumo.id));
            if (completo) completos.push(completo as FormularioLocal);
          } catch {
            completos.push(resumo as unknown as FormularioLocal);
          }
        }
        if (completos.length > 0) {
          await this.dbService.replaceFormularios(completos);
          forms = completos;
        }
      } catch { /* offline ou erro na API */ }
    }
    this.formularios.set(forms.filter(f => f.ativo !== false));
    this.tiposInspecao.set(tipos);
    this.processos.set(procs.filter(p => p.ativo));
    this.veiculos.set(veics);
    this.basesOperacionais.set(bases);
    this.basesAccessDenied.set(bases.length === 0 && await this.baseOperacionalService.acessoNegado());
  }

  private async carregarContexto(editId: string | null): Promise<void> {
    const colabs = await this.dbService.getMembrosEquipe();
    this.colaboradores.set(colabs.filter(c => c.ativo));
    if (this.isAdministrativo()) {
      this.equipes.set(await this.dbService.getEquipes());
      this.nomeEquipe.set('Nenhuma equipe vinculada');
      return;
    }
    const eId = this.tokenService.equipeId();
    if (!eId) {
      this.nomeEquipe.set('Nenhuma equipe vinculada');
      return;
    }
    this.idEquipe.set(eId);
    const equipe = await this.dbService.getEquipe();
    if (equipe) {
      this.nomeEquipe.set(equipe.codigo || 'Equipe atual');
      if (!editId) {
        this.idVeiculo.set(equipe.idVeiculo || null);
        this.idBaseOperacional.set(equipe.idBaseOperacional || null);
      }
    } else {
      this.nomeEquipe.set('Sua equipe');
    }
  }

  /** Rascunho salvo pelo botão "Salvar" (aparece na lista de inspeções). */
  private async carregarRascunhoSalvo(editId: string): Promise<void> {
    const draft = await this.dbService.getInspecao(editId);
    if (!draft || draft.status !== 'EM_ANDAMENTO') return;
    this.draftId.set(editId);
    if (draft.formularioSnapshot && !this.formularios().some(f => f.id === draft.idFormulario)) {
      this.formularios.update(items => [...items, draft.formularioSnapshot as FormularioLocal]);
    }
    this.anexosPendentes.set(draft.anexosPendentes ?? []);
    for (const a of draft.anexosPendentes ?? []) this.registrarPreviewDesvio(a.chaveDesvioLocal, a.arquivo);
    const evidencias = draft.evidencias ?? [];
    this.evidencias.set(evidencias);
    evidencias.forEach(e => this.registrarPreview(e));
    if (draft.idFormulario) this.idFormulario.set(draft.idFormulario);
    this.idTipoInspecao.set(draft.idTipoInspecao ?? draft.payload?.idTipoInspecao ?? this.idTipoInspecao());
    if (draft.idProcesso) this.idProcesso.set(draft.idProcesso);
    if (draft.idEquipe) this.idEquipe.set(draft.idEquipe);
    if (draft.idBaseOperacional) this.idBaseOperacional.set(draft.idBaseOperacional);
    if (draft.idVeiculo) this.idVeiculo.set(draft.idVeiculo);
    if (draft.idColaboradoresInspecionados) this.idColaboradoresInspecionados.set(draft.idColaboradoresInspecionados);
    if (draft.observacoes) this.observacoes.set(draft.observacoes);
    if (draft.draftData) {
      try {
        const parsed = JSON.parse(draft.draftData);
        if (parsed.respostas) this.respostas.set(parsed.respostas);
        if (parsed.desvios) this.desviosGerados.set(parsed.desvios.map((d: DesvioDraft & { fotoBase64?: string }) => {
          const { fotoBase64: _ignorado, ...resto } = d;
          return resto;
        }));
      } catch { /* rascunho antigo ilegível: segue com o que deu para ler */ }
    }
    if (draft.idFormulario) this.currentStep.set(2);
  }

  // ---------- rascunho contínuo ----------
  private async vincularRascunho(editId: string | null): Promise<void> {
    this.chaveRascunho = editId ? `inspecao:rascunho:${editId}` : 'inspecao:nova';
    this.rascunho = this.rascunhos.vincular<RascunhoInspecao>({
      chave: this.chaveRascunho,
      injector: this.injector,
      ler: () => this.estadoAtual(),
      aplicar: v => this.aplicarRascunho(v),
      titulo: () => `${this.nomeFormulario() || 'Inspeção'} · ${this.stepTitle().toLowerCase()}`,
      rota: () => (editId ? `/inspecoes/nova?editId=${encodeURIComponent(editId)}` : '/inspecoes/nova'),
      anexos: () => Object.values(this.arquivoDaFoto()),
      vazio: v => v.passo === 1 && !v.idFormulario && !v.idProcesso && !v.evidencias.length && !v.idColaboradores.length,
    });
    await this.rascunho.restaurado;
    await this.recuperarFotosInterrompidas();
  }

  private estadoAtual(): RascunhoInspecao {
    const arquivos = this.arquivoDaFoto();
    const comArquivo = (idLocal: string) => arquivos[idLocal];
    return {
      passo: this.currentStep(),
      idFormulario: this.idFormulario(),
      idTipoInspecao: this.idTipoInspecao(),
      idProcesso: this.idProcesso(),
      idEquipe: this.idEquipe(),
      nomeEquipe: this.nomeEquipe(),
      idBaseOperacional: this.idBaseOperacional(),
      idVeiculo: this.idVeiculo(),
      idColaboradores: this.idColaboradoresInspecionados(),
      observacoes: this.observacoes(),
      respostas: this.respostas(),
      desvios: this.desviosGerados(),
      evidencias: this.evidencias().filter(e => comArquivo(e.idLocal)).map(e => ({
        idLocal: e.idLocal, idArquivo: comArquivo(e.idLocal), nomeArquivo: e.nomeArquivo, contentType: e.contentType,
        tamanhoBytes: e.tamanhoBytes, idRemoto: e.idRemoto ?? null,
      })),
      fotosDesvio: this.anexosPendentes().filter(a => comArquivo(a.idLocal)).map(a => ({
        idLocal: a.idLocal, idArquivo: comArquivo(a.idLocal), nomeArquivo: a.nomeArquivo, contentType: a.contentType, chaveDesvio: a.chaveDesvioLocal,
      })),
      seletorAberto: this.seletorAberto(),
    };
  }

  private async aplicarRascunho(v: RascunhoInspecao): Promise<void> {
    this.idFormulario.set(v.idFormulario);
    this.idTipoInspecao.set(v.idTipoInspecao ?? this.idTipoInspecao());
    this.idProcesso.set(v.idProcesso);
    this.idEquipe.set(v.idEquipe);
    if (v.nomeEquipe) this.nomeEquipe.set(v.nomeEquipe);
    this.idBaseOperacional.set(v.idBaseOperacional);
    this.idVeiculo.set(v.idVeiculo);
    this.idColaboradoresInspecionados.set(v.idColaboradores ?? []);
    this.observacoes.set(v.observacoes ?? '');
    this.respostas.set(v.respostas ?? []);
    this.desviosGerados.set(v.desvios ?? []);
    this.seletorAberto.set(v.seletorAberto ?? null);

    const mapa: Record<string, string> = {};
    const evidencias: EvidenciaLocal[] = [];
    for (const f of v.evidencias ?? []) {
      const arquivo = await this.arquivos.lerAnexo(f.idArquivo);
      if (!arquivo) continue;
      mapa[f.idLocal] = f.idArquivo;
      const e: EvidenciaLocal = {
        idLocal: f.idLocal, arquivo: arquivo.blob, nomeArquivo: f.nomeArquivo, contentType: f.contentType, idRemoto: f.idRemoto ?? null,
        estado: 'LOCAL', tentativas: 0, tamanhoBytes: f.tamanhoBytes ?? arquivo.blob.size,
      };
      evidencias.push(e);
      this.registrarPreview(e);
    }
    const anexos: AnexoPendenteLocal[] = [];
    for (const f of v.fotosDesvio ?? []) {
      const arquivo = await this.arquivos.lerAnexo(f.idArquivo);
      if (!arquivo || !f.chaveDesvio) continue;
      mapa[f.idLocal] = f.idArquivo;
      anexos.push({ idLocal: f.idLocal, chaveDesvioLocal: f.chaveDesvio, contexto: 'DESVIO', arquivo: arquivo.blob, nomeArquivo: f.nomeArquivo, contentType: f.contentType });
      this.registrarPreviewDesvio(f.chaveDesvio, arquivo.blob);
    }
    this.evidencias.set(evidencias);
    this.anexosPendentes.set(anexos);
    this.arquivoDaFoto.set(mapa);
    this.currentStep.set(Math.min(Math.max(1, v.passo || 1), this.totalSteps()));
  }

  /**
   * Foto que chegou a ser gravada mas não entrou no formulário (o app morreu enquanto
   * ela era preparada). Entra de volta como foto da inspeção, com aviso.
   */
  private async recuperarFotosInterrompidas(): Promise<void> {
    const dono = this.tokenService.ownerId();
    if (!dono) return;
    const usados = new Set(Object.values(this.arquivoDaFoto()));
    const soltos = (await this.arquivos.anexosDoRascunho(dono, this.chaveRascunho)).filter(a => !usados.has(a.id));
    let recuperadas = 0;
    for (const a of soltos) {
      if (a.nome.startsWith(PREFIXO_BRUTO) || a.nome.startsWith(PREFIXO_EVIDENCIA)) {
        const nome = a.nome.replace(PREFIXO_BRUTO, '').replace(PREFIXO_EVIDENCIA, '');
        await this.incluirEvidencia(new File([a.blob], nome, { type: a.tipo }), a.id);
        recuperadas++;
      }
    }
    if (recuperadas) this.toast.info(recuperadas === 1 ? '1 foto recuperada.' : `${recuperadas} fotos recuperadas.`);
  }

  fechar(): void {
    void this.rascunho?.gravarEstadoAtual();
    if (this.idFormulario() || this.evidencias().length) this.toast.info('Guardada. Continue em Inspeções.');
    const anterior = this.sessao.rotaAnterior();
    void this.router.navigateByUrl(anterior && !anterior.startsWith('/inspecoes/nova') ? anterior : '/inspecoes');
  }

  abrirSeletor(qual: string, aberto: boolean): void {
    this.seletorAberto.set(aberto ? qual : null);
  }

  // ---------- navegação ----------
  canAdvance(): boolean {
    const step = this.currentStep();
    if (step === 1) {
      return !!this.idFormulario() && !!this.idTipoInspecao() && !!this.idProcesso() && !!this.idBaseOperacional()
        && this.idColaboradoresInspecionados().length > 0;
    }
    if (step === 2) {
      const form = this.formularioAtivo();
      if (!form) return false;
      return form.grupos.every(g => g.perguntas.every(p => !p.obrigatoria || this.respondida(p)));
    }
    if (this.hasDesvios() && step === 3) {
      return this.desviosGerados().every(d => d.acaoCorretiva?.trim().length > 0 && d.colaboradores?.length > 0);
    }
    // Nenhuma inspeção é aceita sem foto: o bloqueio acontece aqui, e não num 400 do backend.
    if (step === this.stepEvidencias()) return this.evidencias().length > 0 && !this.isPreparandoEvidencia();
    if (step === this.totalSteps()) return this.evidencias().length > 0;
    return true;
  }

  respondida(p: FormularioLocal['grupos'][number]['perguntas'][number]): boolean {
    const r = this.respostas().find(x => x.idPergunta === p.id);
    if (!r) return false;
    if (p.tipoResposta === 'MULTIPLA_ESCOLHA' || p.tipoResposta === 'MULTIPLA_SELECAO') return !!r.opcoesSelecionadas?.length;
    if (p.tipoResposta === 'TEXTO' || p.tipoResposta === 'DATA') return !!(r.valorTexto || r.valorData);
    if (p.tipoResposta === 'NUMERO') return r.valorNumero !== undefined && r.valorNumero !== null;
    return true;
  }

  avancar(): void {
    this.tentou.set(true);
    if (!this.canAdvance()) {
      this.toast.warning(this.currentStep() === this.stepEvidencias() ? 'Inclua ao menos uma foto.' : 'Falta preencher o que está marcado.');
      return;
    }
    if (this.currentStep() === this.totalSteps()) {
      void this.finalizar();
      return;
    }
    this.tentou.set(false);
    this.currentStep.update(s => s + 1);
  }

  voltarPasso(): void {
    if (this.currentStep() > 1) this.currentStep.update(s => s - 1);
  }

  // ---------- contexto ----------
  selecionarEquipe(id: string): void {
    const e = this.equipes().find(x => x.id === id);
    if (!e) return;
    this.idEquipe.set(e.id);
    this.nomeEquipe.set(e.codigo || 'Equipe');
    this.idVeiculo.set(e.idVeiculo || null);
    if (e.idBaseOperacional) this.idBaseOperacional.set(e.idBaseOperacional);
    if (e.membrosIds && e.membrosIds.length > 0) this.idColaboradoresInspecionados.set(e.membrosIds);
  }

  onFormularioChange(id: string): void {
    this.idFormulario.set(id);
    // Respostas novas com as opções padrão do formulário escolhido.
    const form = this.formularios().find(f => f.id === id);
    const iniciais: RespostaDraft[] = [];
    for (const grupo of form?.grupos ?? []) {
      for (const pergunta of grupo.perguntas) {
        const padrao = (pergunta.opcoes ?? []).filter(o => o.padrao).map(o => o.id);
        if (padrao.length > 0) iniciais.push({ idPergunta: pergunta.id, opcoesSelecionadas: padrao });
      }
    }
    this.respostas.set(iniciais);
    this.desviosGerados.set([]);
  }

  // ---------- respostas e desvios ----------
  isOpcaoSelecionada(idPergunta: string, idOpcao: string): boolean {
    return !!this.respostas().find(r => r.idPergunta === idPergunta)?.opcoesSelecionadas?.includes(idOpcao);
  }

  setOpcaoRespostaUnica(idPergunta: string, idOpcao: string): void {
    this.respostas.set([...this.respostas().filter(r => r.idPergunta !== idPergunta), { idPergunta, opcoesSelecionadas: [idOpcao] }]);
    this.avaliarDesvios();
  }

  toggleOpcaoRespostaMultipla(idPergunta: string, idOpcao: string): void {
    const atual = this.respostas().find(r => r.idPergunta === idPergunta)?.opcoesSelecionadas ?? [];
    const novas = atual.includes(idOpcao) ? atual.filter(x => x !== idOpcao) : [...atual, idOpcao];
    const outras = this.respostas().filter(r => r.idPergunta !== idPergunta);
    this.respostas.set(novas.length ? [...outras, { idPergunta, opcoesSelecionadas: novas }] : outras);
    this.avaliarDesvios();
  }

  getTextoResposta(idPergunta: string): string {
    return this.respostas().find(r => r.idPergunta === idPergunta)?.valorTexto || '';
  }

  setTextoResposta(idPergunta: string, event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    const r = this.respostas().filter(x => x.idPergunta !== idPergunta);
    this.respostas.set(val ? [...r, { idPergunta, valorTexto: val }] : r);
  }

  getNumeroResposta(idPergunta: string): number | null {
    return this.respostas().find(r => r.idPergunta === idPergunta)?.valorNumero ?? null;
  }

  setNumeroResposta(idPergunta: string, event: Event): void {
    const bruto = (event.target as HTMLInputElement).value.replace(',', '.');
    const r = this.respostas().filter(x => x.idPergunta !== idPergunta);
    const n = Number(bruto);
    this.respostas.set(bruto !== '' && Number.isFinite(n) ? [...r, { idPergunta, valorNumero: n }] : r);
  }

  getDataResposta(idPergunta: string): string {
    return this.respostas().find(r => r.idPergunta === idPergunta)?.valorData || '';
  }

  setDataResposta(idPergunta: string, event: Event): void {
    const val = (event.target as HTMLInputElement).value;
    const r = this.respostas().filter(x => x.idPergunta !== idPergunta);
    this.respostas.set(val ? [...r, { idPergunta, valorData: val }] : r);
  }

  private avaliarDesvios(): void {
    const form = this.formularioAtivo();
    if (!form) return;
    const desvios: DesvioDraft[] = [];
    for (const grupo of form.grupos) {
      for (const pergunta of grupo.perguntas) {
        const resp = this.respostas().find(r => r.idPergunta === pergunta.id);
        for (const idOpcao of resp?.opcoesSelecionadas ?? []) {
          const opcao = pergunta.opcoes.find(o => o.id === idOpcao);
          if (!opcao?.geraDesvio) continue;
          const existente = this.desviosGerados().find(d => d.idPergunta === pergunta.id && d.idOpcaoResposta === opcao.id);
          desvios.push(existente || {
            idPergunta: pergunta.id, idOpcaoResposta: opcao.id, descricao: opcao.texto, acaoCorretiva: '', prazo: '',
            colaboradores: [], tratadoInLoco: false,
          });
        }
      }
    }
    this.desviosGerados.set(desvios);
  }

  private atualizarDesvio(idPergunta: string, mudar: (d: DesvioDraft) => DesvioDraft): void {
    this.desviosGerados.set(this.desviosGerados().map(d => (d.idPergunta === idPergunta ? mudar({ ...d }) : d)));
  }

  toggleTratadoInLoco(idPergunta: string): void {
    this.atualizarDesvio(idPergunta, d => {
      d.tratadoInLoco = !d.tratadoInLoco;
      if (d.tratadoInLoco && !d.prazo) d.prazo = new Date().toISOString().substring(0, 10);
      return d;
    });
  }

  toggleColaboradorDesvio(idPergunta: string, idColaborador: string): void {
    this.atualizarDesvio(idPergunta, d => {
      d.colaboradores = d.colaboradores.includes(idColaborador) ? d.colaboradores.filter(id => id !== idColaborador) : [...d.colaboradores, idColaborador];
      return d;
    });
  }

  updateDesvioAcao(idPergunta: string, val: string): void {
    this.atualizarDesvio(idPergunta, d => ({ ...d, acaoCorretiva: val }));
  }

  updateDesvioPrazo(idPergunta: string, val: string): void {
    this.atualizarDesvio(idPergunta, d => ({ ...d, prazo: val }));
  }

  getColaboradoresInspecionadosList(): ColaboradorLocal[] {
    const ids = this.idColaboradoresInspecionados();
    return this.colaboradores().filter(c => ids.includes(c.id));
  }

  // ---------- foto do desvio ----------
  temFotoDesvio(d: DesvioDraft): boolean {
    return this.anexosPendentes().some(a => a.chaveDesvioLocal === `${d.idPergunta}:${d.idOpcaoResposta}`);
  }

  previewDesvio(d: DesvioDraft): string | undefined {
    return this.previewsDesvio()[`${d.idPergunta}:${d.idOpcaoResposta}`];
  }

  removerFoto(idPergunta: string): void {
    const d = this.desviosGerados().find(x => x.idPergunta === idPergunta);
    if (!d) return;
    const chave = `${d.idPergunta}:${d.idOpcaoResposta}`;
    const removidos = this.anexosPendentes().filter(a => a.chaveDesvioLocal === chave);
    this.anexosPendentes.update(items => items.filter(a => a.chaveDesvioLocal !== chave));
    for (const a of removidos) this.esquecerArquivo(a.idLocal);
    const url = this.previewsDesvio()[chave];
    if (url) URL.revokeObjectURL(url);
    this.previewsDesvio.update(({ [chave]: _r, ...resto }) => resto);
  }

  async onFotoSelected(event: Event, idPergunta: string): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 15 * 1024 * 1024) {
      this.toast.error('A foto passa de 15 MB.');
      return;
    }
    const d = this.desviosGerados().find(x => x.idPergunta === idPergunta);
    if (!d) return;
    const chave = `${d.idPergunta}:${d.idOpcaoResposta}`;
    // Primeiro o aparelho, depois a tela: se o app morrer agora, a foto já está guardada.
    const idArquivo = await this.rascunho?.anexar(file, `${PREFIXO_DESVIO}${chave}:${file.name}`);
    const anexo: AnexoPendenteLocal = {
      idLocal: crypto.randomUUID(), chaveDesvioLocal: chave, contexto: 'DESVIO', arquivo: file, nomeArquivo: file.name,
      contentType: file.type || 'application/octet-stream',
    };
    this.removerFoto(idPergunta);
    this.anexosPendentes.update(items => [...items, anexo]);
    if (idArquivo) this.arquivoDaFoto.update(m => ({ ...m, [anexo.idLocal]: idArquivo }));
    this.registrarPreviewDesvio(chave, file);
    await this.rascunho?.gravarEstadoAtual();
  }

  // ---------- fotos da inspeção (obrigatórias) ----------
  async onEvidenciasSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const arquivos = Array.from(input.files ?? []);
    input.value = '';
    if (!arquivos.length) return;
    this.isPreparandoEvidencia.set(true);
    try {
      for (const arquivo of arquivos) {
        const erro = validarArquivoEvidencia(arquivo);
        if (erro) {
          this.toast.error(erro);
          continue;
        }
        // Primeiro guarda o original no aparelho (o preparo pode demorar e o app pode morrer).
        const idBruto = await this.rascunho?.anexar(arquivo, `${PREFIXO_BRUTO}${arquivo.name}`);
        await this.incluirEvidencia(arquivo, idBruto ?? null);
      }
    } finally {
      this.isPreparandoEvidencia.set(false);
    }
  }

  /** Comprime, guarda a versão final no aparelho e põe na tela; o original bruto sai em seguida. */
  private async incluirEvidencia(arquivo: File, idBruto: string | null): Promise<void> {
    try {
      const preparada = await prepararEvidencia(arquivo);
      const idFinal = await this.rascunho?.anexar(preparada.arquivo, `${PREFIXO_EVIDENCIA}${preparada.nomeArquivo}`);
      const evidencia: EvidenciaLocal = {
        idLocal: crypto.randomUUID(), arquivo: preparada.arquivo, nomeArquivo: preparada.nomeArquivo, contentType: preparada.contentType,
        idRemoto: null, estado: 'LOCAL', tentativas: 0, tamanhoBytes: preparada.arquivo.size,
      };
      this.registrarPreview(evidencia);
      this.evidencias.update(items => [...items, evidencia]);
      if (idFinal) this.arquivoDaFoto.update(m => ({ ...m, [evidencia.idLocal]: idFinal }));
      await this.rascunho?.gravarEstadoAtual();
      if (idBruto && idFinal) await this.arquivos.removerAnexo(idBruto);
    } catch {
      this.toast.error(`Não foi possível preparar a foto "${arquivo.name}".`);
    }
  }

  removerEvidencia(idLocal: string): void {
    const evidencia = this.evidencias().find(e => e.idLocal === idLocal);
    if (!evidencia) return;
    this.evidencias.update(items => items.filter(e => e.idLocal !== idLocal));
    this.esquecerArquivo(idLocal);
    const url = this.previewsEvidencia()[idLocal];
    if (url) {
      URL.revokeObjectURL(url);
      this.previewsEvidencia.update(({ [idLocal]: _r, ...resto }) => resto);
    }
    // Foto já enviada é descartada no servidor por cortesia; se falhar, o expurgo de 72 h recolhe.
    if (evidencia.idRemoto) {
      firstValueFrom(this.inspecaoService.descartarEvidenciaPendente(evidencia.idRemoto)).catch(() => undefined);
    }
  }

  previewEvidencia(idLocal: string): string | undefined {
    return this.previewsEvidencia()[idLocal];
  }

  tamanhoEvidencia(e: EvidenciaLocal): string {
    return formatarTamanho(e.tamanhoBytes ?? e.arquivo?.size);
  }

  private esquecerArquivo(idLocal: string): void {
    const idArquivo = this.arquivoDaFoto()[idLocal];
    if (!idArquivo) return;
    this.arquivoDaFoto.update(({ [idLocal]: _r, ...resto }) => resto);
    void this.arquivos.removerAnexo(idArquivo);
  }

  private registrarPreview(e: EvidenciaLocal): void {
    try {
      const url = URL.createObjectURL(e.arquivo);
      this.previewsEvidencia.update(p => ({ ...p, [e.idLocal]: url }));
    } catch { /* HEIC não renderiza em <img> em parte dos navegadores */ }
  }

  private registrarPreviewDesvio(chave: string, arquivo: Blob): void {
    try {
      const url = URL.createObjectURL(arquivo);
      this.previewsDesvio.update(p => ({ ...p, [chave]: url }));
    } catch { /* sem prévia */ }
  }

  // ---------- resumo ----------
  getProcessoNome(): string {
    return this.processos().find(p => p.id === this.idProcesso())?.nome || '-';
  }

  getInspecionadosNomes(): string[] {
    const ids = this.idColaboradoresInspecionados();
    return this.colaboradores().filter(c => ids.includes(c.id)).map(c => c.nomeCompleto.split(' ')[0]);
  }

  countRespostas(): number {
    return this.respostas().length;
  }

  getPerguntaEnunciado(id: string): string {
    for (const g of this.formularioAtivo()?.grupos ?? []) {
      const p = g.perguntas.find(x => x.id === id);
      if (p) return p.enunciado;
    }
    return '';
  }

  getOpcaoTexto(idPergunta: string, idOpcao: string): string {
    for (const g of this.formularioAtivo()?.grupos ?? []) {
      const p = g.perguntas.find(x => x.id === idPergunta);
      if (p) return p.opcoes.find(x => x.id === idOpcao)?.texto || '';
    }
    return '';
  }

  // ---------- salvar e finalizar (mesmo contrato da versão anterior) ----------
  async finalizar(): Promise<void> {
    if (this.isSaving()) return;
    try {
      this.isSaving.set(true);
      const formulario = this.formularioAtivo();
      if (!formulario) throw new Error('Formulário não encontrado no cache local.');
      const payload = this.montarPayload();
      const evidencias = this.evidencias();
      const erros = validarRegistrarInspecao(formulario as FormularioInspecao, payload, { totalEvidencias: evidencias.length });
      if (erros.length) {
        this.toast.error(erros[0]);
        return;
      }
      const inspecaoLocal: Omit<InspecaoLocal, 'id'> = {
        idFormulario: payload.idFormulario,
        idTipoInspecao: payload.idTipoInspecao,
        nomeTipoInspecao: this.nomeTipoInspecao() || undefined,
        idProcesso: payload.idProcesso,
        idEquipe: payload.idEquipe ?? undefined,
        idBaseOperacional: payload.idBaseOperacional,
        nomeBaseOperacional: this.nomeBaseOperacional() || undefined,
        idVeiculo: payload.idVeiculo ?? undefined,
        observacoes: this.observacoes() || undefined,
        latitude: this.latitude() || undefined,
        longitude: this.longitude() || undefined,
        idColaboradoresInspecionados: payload.colaboradoresInspecionados,
        status: 'CONCLUIDA',
        dataRealizacao: payload.dataRealizacao,
        statusSync: 'PENDENTE',
        draftData: JSON.stringify({ respostas: payload.respostas, desvios: payload.desvios }),
        formularioSnapshot: structuredClone(formulario) as FormularioInspecao,
        payload,
        anexosPendentes: this.anexosPendentes(),
        evidencias,
      };
      await this.outboxService.enqueueInspecao({
        endpoint: '/_orchestrator/inspecao/lancar',
        method: 'POST',
        // Os Blobs viajam com a fila: o envio sobe as fotos antes do lançamento e só as libera depois do 201.
        payload: { request: payload, anexosPendentes: this.anexosPendentes(), evidencias },
      }, inspecaoLocal, this.draftId() || undefined);
      await this.rascunho?.concluir();
      this.toast.success('Inspeção registrada. Sai sozinha quando houver internet.');
      void this.router.navigate(['/inspecoes'], { replaceUrl: true });
      this.syncService.syncOutbox().catch(err => console.error('Erro no envio imediato:', err));
    } catch (e) {
      console.error(e);
      this.toast.error('Erro ao salvar inspeção no aparelho. Seus dados continuam aqui.');
    } finally {
      this.isSaving.set(false);
    }
  }

  private montarPayload(): RegistrarInspecaoRequest {
    return sanitizarRegistrarInspecao({
      idFormulario: this.idFormulario() || '',
      idTipoInspecao: this.idTipoInspecao() || '',
      idProcesso: this.idProcesso() || '',
      idVeiculo: this.idVeiculo(),
      idEquipe: this.idEquipe(),
      idBaseOperacional: this.idBaseOperacional() || '',
      dataRealizacao: agoraComOffset(),
      observacoes: this.observacoes() || null,
      latitude: this.latitude() ?? null,
      longitude: this.longitude() ?? null,
      colaboradoresInspecionados: this.idColaboradoresInspecionados(),
      respostas: this.respostas().map(r => ({
        idPergunta: r.idPergunta,
        opcoesSelecionadas: r.opcoesSelecionadas ?? [],
        valorTexto: r.valorTexto ?? null,
        valorNumero: r.valorNumero ?? null,
        valorData: r.valorData ?? null,
      })),
      desvios: this.desviosGerados().map(d => ({
        idPergunta: d.idPergunta,
        idOpcaoResposta: d.idOpcaoResposta,
        descricao: d.descricao || null,
        acaoCorretiva: d.acaoCorretiva || null,
        prazo: d.prazo || (d.tratadoInLoco ? new Date().toISOString().substring(0, 10) : null),
        colaboradores: d.colaboradores,
        status: d.tratadoInLoco ? 'RESOLVIDO' : 'ABERTO',
      })),
      // Preenchido pelo SyncService com os IDs devolvidos pelo upload das fotos.
      evidencias: [],
    });
  }
}

function agoraComOffset(): string {
  const date = new Date();
  const offset = -date.getTimezoneOffset();
  const sign = offset >= 0 ? '+' : '-';
  const pad = (value: number) => String(Math.abs(value)).padStart(2, '0');
  const local = `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
    + `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  return `${local}${sign}${pad(Math.trunc(offset / 60))}:${pad(offset % 60)}`;
}
