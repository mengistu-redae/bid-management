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
const buildUsersRouter = require('./routes/pages/users');
const buildDelaysRouter = require('./routes/pages/delays');
const buildMyFollowUpsRouter = require('./routes/pages/myFollowUps');
const buildContractsRouter = require('./routes/pages/contracts');
const buildLogisticsRouter = require('./routes/pages/logistics');
const buildSettingsRouter = require('./routes/pages/settings');
const buildStageHistoryRouter = require('./routes/pages/stageHistory');
const buildHoldsRouter = require('./routes/pages/holds');
const buildAttachmentsRouter = require('./routes/pages/attachments');
const buildIssuesRouter = require('./routes/pages/issues');

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
  // One-shot flash banner, available in every view's scope without each
  // route needing to pass it explicitly - see flash.js's setFlash().
  app.use((req, res, next) => {
    res.locals.flash = (req.session && req.session.flash) || null;
    if (req.session) delete req.session.flash;
    next();
  });

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

  // Sidebar badge counts (My follow-ups / Delays), computed once per
  // request and read directly off res.locals by partials/header.ejs - see
  // its own comment for why this costs an extra two API calls per page.
  // Fails open (no badges) rather than breaking the page if either call errors.
  app.use(async (req, res, next) => {
    try {
      const [followUps, delaysBoard, issues] = await Promise.all([
        req.api.get('/api/my-follow-ups'),
        req.api.get('/api/delays-board'),
        req.api.get('/api/issues'),
      ]);
      res.locals.navCounts = {
        followUps: (followUps.overdue || []).length + (followUps.dueToday || []).length,
        delays: delaysBoard.length,
        issues: issues.filter((i) => i.status !== 'RESOLVED' && i.status !== 'CLOSED').length,
      };
    } catch (err) {
      res.locals.navCounts = null;
    }
    next();
  });

  app.get('/', (req, res) => res.redirect('/dashboard'));
  app.use('/dashboard', buildDashboardRouter());
  app.use('/bids', buildBidsRouter());
  app.use('/opportunities', buildOpportunitiesRouter());
  app.use('/deal-registrations', buildDealRegistrationsRouter());
  app.use('/reports', buildReportsRouter());
  app.use('/users', buildUsersRouter());
  app.use('/delays', buildDelaysRouter());
  app.use('/my-follow-ups', buildMyFollowUpsRouter());
  app.use('/contracts', buildContractsRouter());
  app.use('/logistics', buildLogisticsRouter());
  app.use('/settings', buildSettingsRouter());
  app.use('/stage-history', buildStageHistoryRouter());
  app.use('/holds', buildHoldsRouter());
  app.use('/attachments', buildAttachmentsRouter());
  app.use('/issues', buildIssuesRouter());
  app.use('/import', buildImportRouter());

  app.use((req, res) => res.status(404).render('error', { message: 'Page not found', user: req.session.user }));
  // eslint-disable-next-line no-unused-vars
  app.use((err, req, res, next) => {
    console.error(err);
    // A spring-boot-api call 401ing mid-request (the access token expired a
    // moment after ensureFreshToken's proactive check passed, or was
    // otherwise rejected) left the session's tokenSet looking locally valid,
    // so requireSession let the request through and this is the first place
    // that actually sees the rejection - same "log them out cleanly" outcome
    // attachApiClient's invalid_grant branch already gives the more common
    // "refresh itself failed" case, just reached from a different trigger.
    if (err.status === 401 && req.session) {
      return req.session.destroy(() => res.redirect('/auth/login'));
    }
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
