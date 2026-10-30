describe('Visibility based on Roles', () => {
  const loginWithRole = (roles: string[]) => {
    // 1. Intercept the login request
    cy.intercept('POST', '/api/v1/auth/login', {
      statusCode: 200,
      body: {
        token: 'fake-jwt-token'
      }
    }).as('loginRequest');

    // 2. We mock localStorage to set our token directly
    // This assumes the app stores the token in localStorage and reads it on startup.
    // However, jwtDecode will try to decode 'fake-jwt-token' and fail, returning null.
    // A better approach for E2E is to mock the AuthService or generate a valid signed JWT
    // For simplicity, we can intercept the token payload decoding if possible, 
    // or just pass a valid base64 encoded JWT payload.

    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({
      sub: 'user-123',
      nome: 'Test User',
      roles: roles,
      exp: Math.floor(Date.now() / 1000) + 3600
    }));
    const signature = 'fake-signature';
    const fakeToken = `${header}.${payload}.${signature}`;

    cy.window().then((win) => {
      win.localStorage.setItem('ces_auth_token', fakeToken);
    });

    // Visite o dashboard (a rota padrão pós-login)
    cy.visit('/dashboard');
  };

  it('should show Inspecoes and Descontos for Administrativo', () => {
    loginWithRole(['ROLE_ADMINISTRADOR', 'outra:permissao']);

    // Verifica se os links administrativos estão presentes na nav
    cy.get('app-bottom-nav').contains('Inspeções').should('be.visible');
    cy.get('app-bottom-nav').contains('Descontos').should('be.visible');

    // Verifica se os links de campo NÃO estão presentes
    cy.get('app-bottom-nav').contains('Pedidos').should('not.exist');
    cy.get('app-bottom-nav').contains('Campo').should('not.exist');
  });

  it('should show Campo routes for normal field user', () => {
    loginWithRole(['execucao:comercial:write']);

    // Verifica se os links de campo estão presentes
    cy.get('app-bottom-nav').contains('Pedidos').should('be.visible');
    cy.get('app-bottom-nav').contains('Campo').should('be.visible');
    
    // Verifica se os links administrativos NÃO estão presentes
    cy.get('app-bottom-nav').contains('Inspeções').should('not.exist');
    cy.get('app-bottom-nav').contains('Descontos').should('not.exist');
  });
});
