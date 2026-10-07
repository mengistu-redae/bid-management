'use strict';

const express = require('express');

function buildDashboardRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const data = await req.api.get('/api/dashboard');
      res.render('dashboard', { title: 'Dashboard', user: req.session.user, data });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildDashboardRouter;
