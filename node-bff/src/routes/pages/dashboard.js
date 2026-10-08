'use strict';

const express = require('express');

const TIMEZONE = 'Africa/Addis_Ababa';
const DAY_MS = 24 * 60 * 60 * 1000;

/** YYYY-MM-DD in Africa/Addis_Ababa - the same bare-date format /bids's closingFrom/closingTo query params expect. */
function isoDate(date) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIMEZONE }).format(date);
}

function buildDashboardRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const data = await req.api.get('/api/dashboard');
      const now = new Date();
      // Exact date-range equivalents of the dashboard's own "this week"/"next
      // week" windows, so each section's "View all" link reopens the same
      // slice of bids on the full list page instead of an approximation.
      const dateRanges = {
        closingThisWeek: { closingFrom: isoDate(now), closingTo: isoDate(new Date(now.getTime() + 7 * DAY_MS)) },
        closingNextWeek: { closingFrom: isoDate(new Date(now.getTime() + 7 * DAY_MS)), closingTo: isoDate(new Date(now.getTime() + 14 * DAY_MS)) },
      };
      res.render('dashboard', { title: 'Dashboard', user: req.session.user, data, dateRanges });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildDashboardRouter;
