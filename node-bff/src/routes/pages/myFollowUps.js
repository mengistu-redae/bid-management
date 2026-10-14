'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

function buildMyFollowUpsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const [data, users] = await Promise.all([
        req.api.get('/api/my-follow-ups'),
        req.api.get('/api/users'),
      ]);
      res.render('myFollowUps', { title: 'My Follow-ups', user: req.session.user, data, users });
    } catch (err) {
      next(err);
    }
  });

  router.post('/follow-up', async (req, res, next) => {
    try {
      await req.api.post(`/api/follow-up?entityType=LOT&entityId=${req.body.lotId}`, {
        note: req.body.note || null,
        nextFollowUpDate: req.body.nextFollowUpDate || null,
        ownerId: req.body.ownerId || null,
      });
      setFlash(req, 'success', 'Follow-up updated.');
      res.redirect('/my-follow-ups');
    } catch (err) {
      next(err);
    }
  });

  router.post('/snooze', async (req, res, next) => {
    try {
      await req.api.post(`/api/follow-up/snooze?entityType=LOT&entityId=${req.body.lotId}`, {
        reason: req.body.reason, snoozedUntil: req.body.snoozedUntil || null,
      });
      setFlash(req, 'success', 'Snoozed.');
      res.redirect('/my-follow-ups');
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildMyFollowUpsRouter;
