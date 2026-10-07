'use strict';

const express = require('express');
const { ensureFreshToken } = require('../../apiClient');

const TIMEZONE = 'Africa/Addis_Ababa';

function currentYearMonth() {
  const parts = new Intl.DateTimeFormat('en-US', { timeZone: TIMEZONE, year: 'numeric', month: 'numeric' }).formatToParts(new Date());
  const year = Number(parts.find((p) => p.type === 'year').value);
  const month = Number(parts.find((p) => p.type === 'month').value);
  return { year, month };
}

function buildReportsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const { year: defaultYear, month: defaultMonth } = currentYearMonth();
      const year = Number(req.query.year) || defaultYear;
      const month = Number(req.query.month) || defaultMonth;
      const summary = await req.api.get(`/api/reports/monthly-summary?year=${year}&month=${month}`);
      res.render('reports', { title: 'Reports', user: req.session.user, summary, year, month });
    } catch (err) {
      next(err);
    }
  });

  router.get('/export', async (req, res, next) => {
    try {
      const { year: defaultYear, month: defaultMonth } = currentYearMonth();
      const year = Number(req.query.year) || defaultYear;
      const month = Number(req.query.month) || defaultMonth;
      const accessToken = await ensureFreshToken(req, req.app.get('oidcGetClient'));
      const headers = {};
      if (accessToken) headers.authorization = `Bearer ${accessToken}`;
      const upstream = await fetch(`${process.env.API_BASE_URL}/api/reports/monthly-summary/export?year=${year}&month=${month}`, { headers });
      res.status(upstream.status);
      res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/octet-stream');
      res.setHeader('Content-Disposition', upstream.headers.get('content-disposition') || `attachment; filename="monthly-summary-${year}-${month}.xlsx"`);
      res.send(Buffer.from(await upstream.arrayBuffer()));
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildReportsRouter;
