import { TestBed } from '@angular/core/testing';

import { MapaCorpoLesoesComponent } from './mapa-corpo-lesoes.component';
import {
  LOCAIS_LESAO_MIGRATION,
  REGIOES_CORPO_POR_ID,
  regiaoDoLocal
} from './mapa-corpo-lesoes.config';

describe('MapaCorpoLesoesComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [MapaCorpoLesoesComponent] }).compileComponents();
  });

  it('reconhece todos os 45 locais cadastrados pela migration V93', () => {
    expect(LOCAIS_LESAO_MIGRATION).toHaveLength(45);

    for (const nome of LOCAIS_LESAO_MIGRATION) {
      const regiao = regiaoDoLocal(nome);
      expect(regiao, `Local sem mapeamento: ${nome}`).not.toBeNull();
      expect(REGIOES_CORPO_POR_ID.has(regiao!)).toBe(true);
    }
  });

  it('mantém regiões específicas sem inventar lateralidade', () => {
    expect(regiaoDoLocal('Crânio')).toBe('cranio');
    expect(regiaoDoLocal('Dorso e coluna')).toBe('dorsoColuna');
    expect(regiaoDoLocal('Braço - acima do cotovelo')).toBe('bracoSuperior');
    expect(regiaoDoLocal('Perna - abaixo do joelho')).toBe('pernaInferior');
    expect(regiaoDoLocal('Aparelho respiratório')).toBe('respiratorio');
  });

  it('renderiza a prancha realista e agrega toda a carga oficial sem pendências', async () => {
    const fixture = TestBed.createComponent(MapaCorpoLesoesComponent);
    fixture.componentRef.setInput(
      'itens',
      LOCAIS_LESAO_MIGRATION.map((nome, indice) => ({
        id: `local-${indice}`,
        nome,
        total: 1,
        percentual: 100 / LOCAIS_LESAO_MIGRATION.length
      }))
    );
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const component = fixture.componentInstance as any;
    const imagem = fixture.nativeElement.querySelector('.mapa__anatomia img') as HTMLImageElement;

    expect(component.totalLesoes()).toBe(45);
    expect(component.naoMapeados()).toHaveLength(0);
    expect(component.regioesCorporais().length).toBeGreaterThan(25);
    expect(component.regioesComplementares().length).toBeGreaterThan(5);
    expect(imagem.getAttribute('src')).toBe('/assets/sst/mapa-corporal-anatomico-v1.png');
    expect(imagem.getAttribute('alt')).toContain('frontal e posterior');
    expect(fixture.nativeElement.querySelectorAll('.mapa__ponto').length).toBe(
      component.regioesCorporais().length
    );
    expect(fixture.nativeElement.textContent).toContain('45 locais compatíveis');
    expect(fixture.nativeElement.textContent).toContain('Sistemas e abrangências');
    expect(fixture.nativeElement.textContent).not.toContain('Sem correspondência anatômica segura');
  });

  it('exibe o total uma única vez e usa referências sem número nas demais vistas', () => {
    const fixture = TestBed.createComponent(MapaCorpoLesoesComponent);
    fixture.componentRef.setInput('itens', [
      { id: 'mao', nome: 'Mão', total: 1, percentual: 100 }
    ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.mapa__ponto')).toHaveLength(1);
    expect(fixture.nativeElement.querySelectorAll('.mapa__referencia')).toHaveLength(3);
    expect(fixture.nativeElement.querySelector('.mapa__ponto')?.textContent.trim()).toBe('1');
    expect(fixture.nativeElement.textContent).toContain('uma única vez por região');
  });

  it('continua exibindo a anatomia quando o período não possui lesões', () => {
    const fixture = TestBed.createComponent(MapaCorpoLesoesComponent);
    fixture.componentRef.setInput('itens', []);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.mapa__anatomia img')).toBeTruthy();
    expect(fixture.nativeElement.textContent).toContain('Sem lesões no período');
    expect(fixture.nativeElement.textContent).toContain('Nenhuma lesão registrada');
  });
});
