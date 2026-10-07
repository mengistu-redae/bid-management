'use strict';

const path = require('path');
const express = require('express');

const { buildOidcClient } = require('./auth/oidc');
const { buildSessionMiddleware } = require('./auth/session');
const { buildAuthRouter } = require('./routes/auth');
const { attachApiClient } = require('./apiClient');
const { requireSession } = require('./requireSession');
const buildBidsRouter = require('./routes/pages/bids');
const buildImportRouter = require('./routes/pages/importPage');
const buildDashboardRouter = require('./routes/pages/dashboard');
const buildOpportunitiesRouter = require('./routes/pages/opportunities');
const buildDealRegistrationsRouter = require('./routes/pages/dealRegistrations');
const buildReportsRouter = require('./routes/pages/reports');

async function main() {
  const app = express();
  app.set('view engine', 'ejs');
  app.set('views', path.join(__dirname, 'views'));

  app.use(express.urlencoded({ extended: true }));
  app.use(express.json());
  // Available in every EJS view without needing to pass them per-render.
  Object.assign(app.locals, require('./viewHelpers'));
  app.use('/vendor/htmx', express.static(path.join(__dirname, '..', 'node_modules', 'htmx.org', 'dist')));
  app.use('/public', express.static(path.join(__dirname, '..', 'public')));

  app.get('/health', (req, res) => res.send('ok'));

  app.use(await buildSessionMiddleware());

  let oidcClient;
  const getClient = () => {
    if (!oidcClient) {
      throw new Error('OIDC client not ready yet');
    }
    return oidcClient;
  };

  app.set('oidcGetClient', getClient);
  app.use('/auth', buildAuthRouter(getClient));

  app.use(requireSession);
  app.use(attachApiClient(getClient));

  app.get('/', (req, res) => res.redirect('/dashboard'));
  app.use('/dashboard', buildDashboardRouter());
  app.use('/bids', buildBidsRouter());
  app.use('/opportunities', buildOpportunitiesRouter());
  app.use('/deal-registrations', buildDealRegistrationsRouter());
  app.use('/reports', buildReportsRouter());
  app.use('/import', buildImportRouter());

  app.use((req, res) => res.status(404).render('error', { message: 'Page not found', user: req.session.user }));
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    console.error(err);
    const status = err.status && err.status < 600 ? err.status : 500;
    res.status(status).render('error', { message: err.message || 'Something went wrong', user: req.session && req.session.user });
  });

  const port = process.env.PORT || 3000;
  app.listen(port, () => console.log(`[bidmgmt-bff] listening on :${port}`));

  // OIDC discovery happens after the server starts listening so the
  // /health check (used by docker-compose's healthcheck) succeeds even
  // while Keycloak is still mid-cold-start - see oidc.js's discoverWithRetry.
  oidcClient = await buildOidcClient();
  console.log('[bidmgmt-bff] OIDC client ready');
}

main().catch((err) => {
  console.error('[bidmgmt-bff] fatal startup error:', err);
  process.exit(1);
});
