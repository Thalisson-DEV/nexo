import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { GovernancaSstService } from './governanca-sst.service';

describe('GovernancaSstService', () => {
  let service: GovernancaSstService;
  let http: HttpTestingController;
  const url = `${environment.apiUrl}/v1/sst/governanca`;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(GovernancaSstService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('mantém CIPA e Brigada como filtro repetido na listagem', () => {
    service.listarCorpos({ tipo: ['CIPA', 'BRIGADA'], page: 0, size: 20 }).subscribe();
    const request = http.expectOne(req => req.url === `${url}/corpos`);
    expect(request.request.params.getAll('tipo')).toEqual(['CIPA', 'BRIGADA']);
    expect(request.request.params.get('size')).toBe('20');
    request.flush({ content: [], page: { totalElements: 0, totalPages: 0, number: 0, size: 20 } });
  });

  it('envia o contrato completo ao criar uma Brigada', () => {
    const body = {
      tipo: 'BRIGADA' as const,
      regimeComposicao: null,
      nome: 'Brigada 2026',
      idBaseOperacional: 'base-id',
      unidadeGovernanca: 'Edificação principal',
      uf: 'SP',
      inicioPeriodo: '2026-08-01',
      fimPeriodo: '2027-07-31',
      grauRisco: 3,
      idPerfilNormativo: null,
      hipoteseRepresentanteNomeadoConfirmada: false,
      idsBasesAbrangidas: ['base-apoio']
    };

    service.criarCorpo(body).subscribe();
    const request = http.expectOne(`${url}/corpos`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ corpo: {}, basesAbrangidas: [], membros: [] });
  });

  it('lista perfis normativos confirmados conforme o tipo do corpo', () => {
    service.listarPerfisNormativos({ tipoCorpo: 'CIPA', confirmado: true, size: 100 }).subscribe();
    const request = http.expectOne(req => req.url === `${url}/perfis-normativos`);
    expect(request.request.params.get('tipoCorpo')).toBe('CIPA');
    expect(request.request.params.get('confirmado')).toBe('true');
    request.flush({ content: [], page: { totalElements: 0, totalPages: 0, number: 0, size: 100 } });
  });

  it('inclui membro usando a versão atual da composição', () => {
    const body = {
      versao: 3,
      membro: {
        idColaborador: 'colaborador-id', papel: 'BRIGADISTA' as const,
        representacao: null, titularidade: null, origem: null,
        cicloEleitoral: null, classificacaoEleitoral: null, atoDesignacao: null,
        reeleito: false, inicioParticipacao: '2026-08-11', turnoDisponivel: null
      }
    };
    service.adicionarMembro('corpo-id', body).subscribe();
    const request = http.expectOne(`${url}/corpos/corpo-id/membros`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({ id: 'membro-id' });
  });

  it('registra saída e substituição nos endpoints históricos', () => {
    service.registrarSaidaMembro('corpo-id', 'membro-id', {
      versao: 4, dataEfeito: '2026-09-01', motivo: 'Mudança de função'
    }).subscribe();
    const saida = http.expectOne(`${url}/corpos/corpo-id/membros/membro-id/saida`);
    expect(saida.request.method).toBe('POST');
    saida.flush({ id: 'membro-id' });

    const sucessor = {
      idColaborador: 'sucessor-id', papel: 'BRIGADISTA' as const,
      representacao: null, titularidade: null, origem: null,
      cicloEleitoral: null, classificacaoEleitoral: null, atoDesignacao: null,
      reeleito: false, inicioParticipacao: '2026-09-01', turnoDisponivel: null
    };
    service.substituirMembro('corpo-id', 'membro-id', {
      versao: 5,
      dataEfeito: '2026-09-01',
      motivoSaida: 'Mudança de função',
      fundamentoProcedimental: 'Substituição aprovada',
      sucessor
    }).subscribe();
    const substituicao = http.expectOne(`${url}/corpos/corpo-id/membros/membro-id/substituicao`);
    expect(substituicao.request.method).toBe('POST');
    expect(substituicao.request.body.sucessor).toEqual(sucessor);
    substituicao.flush({ id: 'novo-membro-id' });
  });

  it('envia documento e metadados em multipart privado', () => {
    const arquivo = new File(['conteudo'], 'ata.pdf', { type: 'application/pdf' });
    service.enviarDocumento(arquivo, { contexto: 'CIPA', idCategoria: 'categoria-id' }).subscribe();
    const request = http.expectOne(`${url}/documentos`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeInstanceOf(FormData);
    const recebido = (request.request.body as FormData).get('arquivo') as File;
    expect(recebido.name).toBe('ata.pdf');
    expect(recebido.type).toBe('application/pdf');
    expect((request.request.body as FormData).get('dados')).toBeInstanceOf(Blob);
    request.flush({});
  });

  it('faz download documental pelo backend autenticado', () => {
    service.baixarDocumento('documento-id').subscribe(result => expect(result.type).toBe('application/pdf'));
    const request = http.expectOne(`${url}/documentos/documento-id/arquivo`);
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['pdf'], { type: 'application/pdf' }));
  });

  it('anexa foto opcional à reunião sem incluí-la no payload da IA', () => {
    const foto = new File(['foto'], 'evidencia.jpg', { type: 'image/jpeg' });
    service.anexarFotoReuniao('reuniao-id', foto, 'Vista da sala').subscribe();
    const request = http.expectOne(`${url}/reunioes/reuniao-id/evidencias`);
    const form = request.request.body as FormData;
    expect(request.request.method).toBe('POST');
    expect((form.get('arquivo') as File).name).toBe('evidencia.jpg');
    expect(form.get('legenda')).toBe('Vista da sala');
    expect(form.has('transcricao')).toBe(false);
    request.flush({});
  });

  it('cria vínculo explícito do checklist com CIPA ou Brigada', () => {
    const body = { idFormulario: 'form-id', tipoCorpo: 'BRIGADA', nome: 'Inspeção mensal' };
    service.salvarModeloChecklist(body).subscribe();
    const request = http.expectOne(`${url}/modelos-checklist`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(body);
    request.flush({});
  });

  it('gera e baixa o DOCX da versão aprovada em endpoints distintos', () => {
    service.gerarDocxAta('reuniao-id', 'versao-id').subscribe();
    const gerar = http.expectOne(`${url}/reunioes/reuniao-id/atas/versao-id/docx`);
    expect(gerar.request.method).toBe('POST');
    gerar.flush({ id: 'arquivo-id' });

    service.baixarDocxAta('reuniao-id', 'arquivo-id').subscribe();
    const baixar = http.expectOne(`${url}/reunioes/reuniao-id/atas/docx/arquivo-id/arquivo`);
    expect(baixar.request.method).toBe('GET');
    expect(baixar.request.responseType).toBe('blob');
    baixar.flush(new Blob(['docx']));
  });
});
