'use strict';

const { buildSegments } = require('../../dealTimeline');

/**
 * Generic "why is this indicator badge this color" page for any tracked
 * entity type - reuses the same /api/stage-history + /api/stage-targets
 * data and buildSegments() timeline math Deal 360 already built, just
 * scoped to one entity instead of a whole deal chain. The caller (any
 * indicator badge) passes entityType/entityId plus display-only context
 * (title/subtitle/stageField/backHref/backLabel) as query params, since
 * the calling page already has all of that in hand - this route stays
 * entity-agnostic and never needs to know how to label a Bid vs. a
 * Contract vs. an Opportunity itself.
 */
function buildStageHistoryRouter() {
  const router = require('express').Router();

  router.get('/', async (req, res, next) => {
    try {
      const { entityType, entityId } = req.query;
      if (!entityType || !entityId) {
        return res.status(400).render('error', { message: 'entityType and entityId are required', user: req.session.user });
      }
      const stageField = req.query.stageField || 'status';
      const [fullHistory, allTargets] = await Promise.all([
        req.api.get(`/api/stage-history?entityType=${entityType}&entityId=${entityId}`),
        req.api.get('/api/stage-targets'),
      ]);
      const history = fullHistory.filter((h) => h.stageField === stageField);
      const targets = allTargets.filter((t) => t.entityType === entityType);
      const segments = buildSegments(history, targets, new Date());
      // Oldest-first for the timeline bar's left-to-right reading order, newest-first for the list below it.
      const historyNewestFirst = [...history].reverse();

      res.render('stageHistory', {
        title: (req.query.title || 'Entity') + ' - Stage history',
        user: req.session.user,
        pageTitle: req.query.title || null,
        subtitle: req.query.subtitle || null,
        backHref: req.query.backHref || null,
        backLabel: req.query.backLabel || 'Back',
        entityType, stageField, segments,
        history: historyNewestFirst,
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildStageHistoryRouter;
