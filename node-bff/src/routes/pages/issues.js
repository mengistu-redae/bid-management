'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const CATEGORY_OPTIONS = ['OPERATIONAL', 'CONTRACT_CLOSURE'];
const SEVERITY_OPTIONS = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const STATUS_OPTIONS = ['OPEN', 'IN_PROGRESS', 'WAITING_ON_CUSTOMER', 'WAITING_ON_VENDOR', 'BLOCKED', 'RESOLVED', 'CLOSED'];

function buildIssuesRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const issues = await req.api.get('/api/issues');
      res.render('issues/list', {
        title: 'Issues', user: req.session.user, issues,
        categoryOptions: CATEGORY_OPTIONS, statusOptions: STATUS_OPTIONS, severityOptions: SEVERITY_OPTIONS,
        lastUpdated: new Date(),
      });
    } catch (err) {
      next(err);
    }
  });

  router.get('/new', async (req, res, next) => {
    try {
      const [divisions, users] = await Promise.all([req.api.get('/api/divisions'), req.api.get('/api/users')]);
      res.render('issues/new', {
        title: 'New issue', user: req.session.user, divisions, users,
        categoryOptions: CATEGORY_OPTIONS, severityOptions: SEVERITY_OPTIONS, form: {}, duplicates: null,
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/', async (req, res, next) => {
    try {
      const body = req.body;

      let organizationIdForDuplicateCheck = body.organizationId || null;
      if (!organizationIdForDuplicateCheck && body.organizationName) {
        const candidates = await req.api.get(`/api/organizations?q=${encodeURIComponent(body.organizationName)}`);
        if (candidates.length === 1) {
          organizationIdForDuplicateCheck = candidates[0].id;
        }
      }

      if (body.confirmDuplicate !== 'true' && body.itemOrSystem && organizationIdForDuplicateCheck) {
        const duplicates = await req.api.get(`/api/issues/open-duplicates?organizationId=${encodeURIComponent(organizationIdForDuplicateCheck)}&itemOrSystem=${encodeURIComponent(body.itemOrSystem)}`);
        if (duplicates.length > 0) {
          const [divisions, users] = await Promise.all([req.api.get('/api/divisions'), req.api.get('/api/users')]);
          return res.render('issues/new', {
            title: 'New issue', user: req.session.user, divisions, users,
            categoryOptions: CATEGORY_OPTIONS, severityOptions: SEVERITY_OPTIONS, form: body, duplicates,
          });
        }
      }

      const payload = {
        category: body.category || 'OPERATIONAL',
        severity: body.severity || 'MEDIUM',
        organizationId: body.organizationId || null,
        organizationName: body.organizationId ? null : body.organizationName,
        sector: body.sector || null,
        divisionId: body.divisionId || null,
        itemOrSystem: body.itemOrSystem || null,
        description: body.description,
        currentStatusNote: body.currentStatusNote || null,
        nextAction: body.nextAction || null,
        challenge: body.challenge || null,
        bidId: body.bidId || null,
        contractId: body.contractId || null,
        opportunityId: body.opportunityId || null,
        financialImpact: body.financialImpact ? Number(body.financialImpact) : null,
        financialImpactCurrency: body.financialImpactCurrency || null,
        openedAt: body.openedAt ? new Date(body.openedAt).toISOString() : null,
        assignedToId: body.assignedToId || null,
      };
      const created = await req.api.post('/api/issues', payload);
      setFlash(req, 'success', 'Issue created.');
      res.redirect(`/issues/${created.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const [issue, activity, attachments, users, divisions] = await Promise.all([
        req.api.get(`/api/issues/${req.params.id}`),
        req.api.get(`/api/issues/${req.params.id}/activity`),
        req.api.get(`/api/attachments?entityType=ISSUE&entityId=${req.params.id}`),
        req.api.get('/api/users'),
        req.api.get('/api/divisions'),
      ]);
      res.render('issues/detail', {
        title: `${issue.organization.name} - ${issue.itemOrSystem || issue.category}`, user: req.session.user,
        issue, activity, attachments, users, divisions, statusOptions: STATUS_OPTIONS, severityOptions: SEVERITY_OPTIONS,
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/update', async (req, res, next) => {
    try {
      const body = req.body;
      await req.api.put(`/api/issues/${req.params.id}`, {
        status: body.status || null,
        severity: body.severity || null,
        divisionId: body.divisionId || null,
        itemOrSystem: body.itemOrSystem || null,
        description: body.description || null,
        currentStatusNote: body.currentStatusNote || null,
        nextAction: body.nextAction || null,
        challenge: body.challenge || null,
        financialImpact: body.financialImpact ? Number(body.financialImpact) : null,
        financialImpactCurrency: body.financialImpactCurrency || null,
        resolutionNotes: body.resolutionNotes || null,
        assignedToId: body.assignedToId || null,
      });
      setFlash(req, 'success', 'Saved.');
      res.redirect(`/issues/${req.params.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/issues/${req.params.id}`);
      }
      next(err);
    }
  });

  router.post('/:id/activity', async (req, res, next) => {
    try {
      await req.api.post(`/api/issues/${req.params.id}/activity`, { note: req.body.note });
      res.redirect(`/issues/${req.params.id}`);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildIssuesRouter;
