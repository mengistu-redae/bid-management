'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

/**
 * Generic "pause the SLA clock" control for any tracked entity type - mirrors
 * stageHistory.js's entity-agnostic shape, keyed purely by entityType/entityId
 * passed in from whichever detail page embeds partials/holdControl.ejs. Backed
 * by StageTrackingController's POST/DELETE /api/holds, which StageClockService
 * already subtracts from indicator/SLA day-counting - this router just gives
 * users a way to actually start/end one.
 */
function buildHoldsRouter() {
  const router = express.Router();

  router.post('/', async (req, res, next) => {
    try {
      const { entityType, entityId, reason, returnTo } = req.body;
      await req.api.post(`/api/holds?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`, { reason });
      setFlash(req, 'success', 'Hold started.');
      res.redirect(returnTo || '/dashboard');
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(req.body.returnTo || '/dashboard');
      }
      next(err);
    }
  });

  router.post('/end', async (req, res, next) => {
    try {
      const { entityType, entityId, returnTo } = req.body;
      await req.api.delete(`/api/holds?entityType=${encodeURIComponent(entityType)}&entityId=${encodeURIComponent(entityId)}`);
      setFlash(req, 'success', 'Hold ended.');
      res.redirect(returnTo || '/dashboard');
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(req.body.returnTo || '/dashboard');
      }
      next(err);
    }
  });

  return router;
}

module.exports = buildHoldsRouter;
