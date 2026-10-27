'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const WAITING_ON_OPTIONS = ['INTERNAL', 'CUSTOMER', 'OEM', 'DISTRIBUTOR', 'IMPLEMENTATION_PARTNER', 'BANK', 'FREIGHT_FORWARDER_CUSTOMS', 'OTHER'];
const LEVEL_OPTIONS = ['AMBER', 'RED', 'CRITICAL', 'STALE'];

function buildQuery(query) {
  const params = new URLSearchParams();
  ['divisionId', 'officerId', 'organizationId', 'oemBrand', 'waitingOnParty', 'level'].forEach((key) => {
    if (query[key]) params.set(key, query[key]);
  });
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

function buildDelaysRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const [rows, divisions, users, organizations] = await Promise.all([
        req.api.get(`/api/delays-board${buildQuery(req.query)}`),
        req.api.get('/api/divisions'),
        req.api.get('/api/users'),
        req.api.get('/api/organizations'),
      ]);
      res.render('delays', {
        title: 'Delays', user: req.session.user, rows, divisions, users, organizations,
        waitingOnOptions: WAITING_ON_OPTIONS, levelOptions: LEVEL_OPTIONS, query: req.query,
        lastUpdated: new Date(),
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/follow-up', async (req, res, next) => {
    try {
      // entityType/entityId are generic as of the Contract/Logistics board extension - req.body.lotId
      // is kept as a fallback so nothing breaks if an older cached page form still submits it.
      const entityType = req.body.entityType || 'LOT';
      const entityId = req.body.entityId || req.body.lotId;
      await req.api.post(`/api/follow-up?entityType=${entityType}&entityId=${entityId}`, {
        note: req.body.note || null,
        nextFollowUpDate: req.body.nextFollowUpDate || null,
        ownerId: req.body.ownerId || null,
      });
      setFlash(req, 'success', 'Follow-up updated.');
      res.redirect(req.body.returnTo || '/delays');
    } catch (err) {
      next(err);
    }
  });

  router.post('/snooze', async (req, res, next) => {
    try {
      const entityType = req.body.entityType || 'LOT';
      const entityId = req.body.entityId || req.body.lotId;
      await req.api.post(`/api/follow-up/snooze?entityType=${entityType}&entityId=${entityId}`, {
        reason: req.body.reason, snoozedUntil: req.body.snoozedUntil || null,
      });
      setFlash(req, 'success', 'Snoozed.');
      res.redirect(req.body.returnTo || '/delays');
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildDelaysRouter;
