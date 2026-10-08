'use strict';

const express = require('express');
const { groupByDivision } = require('../../divisionGrouping');

const TIMEZONE = 'Africa/Addis_Ababa';
const DAY_MS = 24 * 60 * 60 * 1000;
const LIST_LIMIT = 6;
const BOND_ROW_LIMIT = 8;

/** YYYY-MM-DD in Africa/Addis_Ababa - the same bare-date format /bids's closingFrom/closingTo query params expect. */
function isoDate(date) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIMEZONE }).format(date);
}

/**
 * Flattens division groups of bond rows into one ordered list of divider and
 * row items, stamping "hidden" on everything from the limit-th data row
 * onward - a divider is hidden exactly when the first row under it already
 * is, so a group never shows an empty heading with nothing visible below it.
 */
function buildBondsDisplayItems(groups, limit) {
  const items = [];
  let dataCount = 0;
  for (const group of groups) {
    if (group.rows.length === 0) continue;
    items.push({ kind: 'divider', label: group.label, hidden: dataCount >= limit });
    for (const row of group.rows) {
      items.push({ kind: 'row', row, hidden: dataCount >= limit });
      dataCount++;
    }
  }
  return items;
}

function buildDashboardRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const [data, divisions] = await Promise.all([
        req.api.get('/api/dashboard'),
        req.api.get('/api/divisions'),
      ]);
      const divisionNames = divisions.map((d) => d.name);
      const now = new Date();
      // Exact date-range equivalents of the dashboard's own "this week"/"next
      // week" windows, so each section's "View all" link reopens the same
      // slice of bids on the full list page instead of an approximation.
      const dateRanges = {
        closingThisWeek: { closingFrom: isoDate(now), closingTo: isoDate(new Date(now.getTime() + 7 * DAY_MS)) },
        closingNextWeek: { closingFrom: isoDate(new Date(now.getTime() + 7 * DAY_MS)), closingTo: isoDate(new Date(now.getTime() + 14 * DAY_MS)) },
      };

      const byDivisionNames = (row) => row.divisionNames;
      const closingThisWeekGroups = groupByDivision(data.closingThisWeek.slice(0, LIST_LIMIT), divisionNames, byDivisionNames);
      const closingNextWeekGroups = groupByDivision(data.closingNextWeek.slice(0, LIST_LIMIT), divisionNames, byDivisionNames);
      const clarificationGroups = groupByDivision(data.upcomingClarifications, divisionNames, byDivisionNames);
      const bondsDisplayItems = buildBondsDisplayItems(
        groupByDivision(data.outstandingBonds, divisionNames, byDivisionNames),
        BOND_ROW_LIMIT
      );

      res.render('dashboard', {
        title: 'Dashboard', user: req.session.user, data, dateRanges,
        closingThisWeekGroups, closingNextWeekGroups, clarificationGroups,
        bondsDisplayItems, bondRowLimit: BOND_ROW_LIMIT,
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildDashboardRouter;
