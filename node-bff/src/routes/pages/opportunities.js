'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const STAGE_OPTIONS = ['LEAD', 'QUALIFIED', 'RFI_PROPOSAL', 'EXPECTING_TENDER', 'CONVERTED_TO_BID', 'LOST_CLOSED'];

function buildOpportunitiesRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const opportunities = await req.api.get('/api/opportunities');
      res.render('opportunities/list', { title: 'Opportunities', user: req.session.user, opportunities });
    } catch (err) {
      next(err);
    }
  });

  router.get('/new', async (req, res, next) => {
    try {
      const [divisions, oems, users] = await Promise.all([
        req.api.get('/api/divisions'),
        req.api.get('/api/oems'),
        req.api.get('/api/users'),
      ]);
      res.render('opportunities/new', { title: 'New opportunity', user: req.session.user, divisions, oems, users, form: {} });
    } catch (err) {
      next(err);
    }
  });

  router.post('/', async (req, res, next) => {
    try {
      const body = req.body;
      const divisionCodes = Array.isArray(body.divisionCodes) ? body.divisionCodes : (body.divisionCodes ? [body.divisionCodes] : []);
      const oemNames = splitList(body.oemNames);
      const payload = {
        organizationId: body.organizationId || null,
        organizationName: body.organizationId ? null : body.organizationName,
        sector: body.sector || null,
        title: body.title,
        estimatedValue: body.estimatedValue ? Number(body.estimatedValue) : null,
        estimatedValueCurrency: body.estimatedValueCurrency || null,
        expectedTenderDate: body.expectedTenderDate || null,
        divisionCodes,
        oemNames,
        ownerId: body.ownerId || null,
        notes: body.notes || null,
      };
      const created = await req.api.post('/api/opportunities', payload);
      setFlash(req, 'success', 'Opportunity created.');
      res.redirect(`/opportunities/${created.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const [opportunity, activity] = await Promise.all([
        req.api.get(`/api/opportunities/${req.params.id}`),
        req.api.get(`/api/opportunities/${req.params.id}/activity`),
      ]);
      res.render('opportunities/detail', { title: opportunity.title, user: req.session.user, opportunity, activity, stageOptions: STAGE_OPTIONS });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/update', async (req, res, next) => {
    try {
      const body = req.body;
      await req.api.put(`/api/opportunities/${req.params.id}`, {
        stage: body.stage || null,
        lostReason: body.lostReason || null,
        notes: body.notes || null,
      });
      setFlash(req, 'success', 'Saved.');
      res.redirect(`/opportunities/${req.params.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/convert-to-bid', async (req, res, next) => {
    try {
      const bid = await req.api.post(`/api/opportunities/${req.params.id}/convert-to-bid`);
      setFlash(req, 'success', 'Converted to a bid.');
      res.redirect(`/bids/${bid.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/activity', async (req, res, next) => {
    try {
      await req.api.post(`/api/opportunities/${req.params.id}/activity`, { note: req.body.note });
      res.redirect(`/opportunities/${req.params.id}`);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

function splitList(raw) {
  if (!raw) return [];
  return raw.split(/[,/&]/).map((s) => s.trim()).filter(Boolean);
}

module.exports = buildOpportunitiesRouter;
