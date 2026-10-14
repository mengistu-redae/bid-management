'use strict';

const express = require('express');
const { groupByDivision } = require('../../divisionGrouping');

const TIMEZONE = 'Africa/Addis_Ababa';
const DAY_MS = 24 * 60 * 60 * 1000;
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

/**
 * Groups closing-soon rows by calendar date (Addis timezone), each group
 * labeled with its weekday/date, a Today/Tomorrow/"in N days" relative tag,
 * and how many bids fall on it - the "Thu 15 Oct · Tomorrow · 3 bids" date
 * headers in the closing-in-7-days table. Rows are assumed already sorted
 * by closingAt (DashboardService sorts them server-side).
 */
function groupByClosingDate(rows) {
  const now = new Date();
  const todayIso = isoDate(now);
  const tomorrowIso = isoDate(new Date(now.getTime() + DAY_MS));
  const groups = [];
  let currentKey = null;
  let currentGroup = null;
  for (const row of rows) {
    const date = new Date(row.closingAt);
    const key = isoDate(date);
    if (key !== currentKey) {
      const weekday = new Intl.DateTimeFormat('en-GB', { weekday: 'short', timeZone: TIMEZONE }).format(date);
      const dayMonth = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', timeZone: TIMEZONE }).format(date);
      let relative;
      if (key === todayIso) {
        relative = 'Today';
      } else if (key === tomorrowIso) {
        relative = 'Tomorrow';
      } else {
        const days = Math.round((new Date(key + 'T00:00:00Z').getTime() - new Date(todayIso + 'T00:00:00Z').getTime()) / DAY_MS);
        relative = days >= 0 ? `in ${days} days` : null;
      }
      currentGroup = { key, label: `${weekday} ${dayMonth}`, relative, rows: [] };
      groups.push(currentGroup);
      currentKey = key;
    }
    currentGroup.rows.push(row);
  }
  return groups;
}

function buildDashboardRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const divisionParam = req.query.division ? String(req.query.division) : undefined;
      const [data, divisions] = await Promise.all([
        req.api.get(`/api/dashboard${divisionParam ? `?division=${encodeURIComponent(divisionParam)}` : ''}`),
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
      const closingThisWeekByDate = groupByClosingDate(data.closingThisWeek);
      const clarificationGroups = groupByDivision(data.upcomingClarifications, divisionNames, byDivisionNames);
      const bondsDisplayItems = buildBondsDisplayItems(
        groupByDivision(data.outstandingBonds, divisionNames, byDivisionNames),
        BOND_ROW_LIMIT
      );

      const updatedAtLabel = `${new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: '2-digit', month: 'short', year: 'numeric', timeZone: TIMEZONE }).format(now)} · updated ${new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: TIMEZONE }).format(now)}`;

      res.render('dashboard', {
        title: 'Dashboard', user: req.session.user, data, dateRanges,
        closingThisWeekByDate, clarificationGroups,
        bondsDisplayItems, bondRowLimit: BOND_ROW_LIMIT,
        currentDivision: divisionParam || null,
        updatedAtLabel,
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildDashboardRouter;
