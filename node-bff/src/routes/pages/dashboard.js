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

function hoursUntil(iso) {
  return (new Date(iso).getTime() - Date.now()) / (60 * 60 * 1000);
}

/** Mirrors DashboardService's lifecycleBucket() exactly, so the Kanban columns agree with the KPI strip's own stage counts. */
const STAGE_BUCKET = {
  IDENTIFIED: 'Identified', UNDER_REVIEW: 'Identified',
  PREPARING: 'Preparing',
  SUBMITTED: 'Submitted', OPENED: 'Submitted',
  UNDER_EVALUATION: 'Evaluation',
  WON: 'Won',
};
const STAGES = ['Identified', 'Preparing', 'Submitted', 'Evaluation', 'Won'];
// The real BidStatus value a stage's "View all" link filters /bids by - an approximation for
// buckets that merge two statuses (Identified = IDENTIFIED or UNDER_REVIEW, Submitted =
// SUBMITTED or OPENED): picks the more common/primary one rather than a lossless OR, which
// the /bids list's single-value status filter doesn't support.
const STAGE_PRIMARY_STATUS = { Identified: 'IDENTIFIED', Preparing: 'PREPARING', Submitted: 'SUBMITTED', Evaluation: 'UNDER_EVALUATION', Won: 'WON' };
const KANBAN_CARD_LIMIT = 3;

/** Mirrors DashboardService's applyDivisionFilter() exactly, so the Kanban board stays in sync with the rest of the page when a division pill is active. */
function filterBidsByDivision(allBids, divisionParam) {
  if (!divisionParam) return allBids;
  if (divisionParam === 'none') {
    return allBids.filter((b) => b.lots.every((l) => !l.division));
  }
  return allBids.filter((b) => b.lots.some((l) => l.division && l.division.id === divisionParam));
}

/**
 * Builds the Overview tab's pipeline Kanban from the real bid list - one additional call to
 * the existing GET /api/bids (already used by the /bids list page), no new backend endpoint.
 * All/Open/Won/Lost counts and each stage's bid cards all come from this one real list (after
 * the same division-pill scoping the rest of the page already applies), so they can never
 * disagree with each other or with the KPI strip above.
 */
function buildPipelineKanban(allBids, divisionParam) {
  const scoped = filterBidsByDivision(allBids, divisionParam);
  const columns = STAGES.map((stage) => ({ stage, bids: [], count: 0, primaryStatus: STAGE_PRIMARY_STATUS[stage] }));
  const byStage = new Map(columns.map((c) => [c.stage, c]));
  let wonCount = 0;
  let lostCount = 0;

  for (const bid of scoped) {
    const stage = STAGE_BUCKET[bid.status];
    if (stage) {
      const column = byStage.get(stage);
      column.count++;
      if (column.bids.length < KANBAN_CARD_LIMIT) {
        column.bids.push({
          id: bid.id,
          title: bid.title,
          organizationName: bid.organization ? bid.organization.name : null,
          closingAt: bid.closingAt,
        });
      }
      if (stage === 'Won') wonCount++;
    } else {
      lostCount++; // LOST/DROPPED/CANCELLED
    }
  }

  return {
    columns,
    allCount: scoped.length,
    openCount: scoped.length - wonCount - lostCount,
    wonCount,
    lostCount,
  };
}

/**
 * Real, already-computed follow-ups surfaced as a short urgency-ranked list - no new data
 * fetched, no invented urgency score. Urgency reuses the windows the rest of the dashboard
 * already treats as meaningful: within 48h (the KPI cards' own "close within 48h" cutoff), the
 * 7-day closing-soon window, and DashboardService's existing 14-day bond "expiring soon"
 * threshold - never a number made up for this list alone.
 */
function buildPriorityActions(data) {
  const actions = [];

  if (data.missingReadiness.length > 0) {
    const urgent = data.missingReadiness.some((r) => r.closingAt && hoursUntil(r.closingAt) <= 48);
    actions.push({
      icon: 'alert',
      urgency: urgent ? 'urgent' : 'high',
      title: 'Complete bid readiness documents',
      detail: `${data.missingReadiness.length} bid${data.missingReadiness.length === 1 ? '' : 's'} missing MAF or vendor quote`,
      href: '#readiness-gaps',
    });
  }

  if (data.closingThisWeek.length > 0) {
    const closingWithin48h = data.closingThisWeek.filter((r) => hoursUntil(r.closingAt) <= 48).length;
    actions.push({
      icon: 'calendar',
      urgency: closingWithin48h > 0 ? 'urgent' : 'high',
      title: 'Review upcoming submission deadlines',
      detail: `${data.closingThisWeek.length} bid${data.closingThisWeek.length === 1 ? '' : 's'} closing within 7 days${closingWithin48h > 0 ? ` (${closingWithin48h} within 48h)` : ''}`,
      href: '#closing-soon',
    });
  }

  if (data.bondsExpiringWithin90DaysCount > 0) {
    const expiringSoonCount = data.outstandingBonds.filter((b) => b.expiringSoon).length;
    actions.push({
      icon: 'shield',
      urgency: expiringSoonCount > 0 ? 'urgent' : 'medium',
      title: 'Review outstanding bid bonds',
      detail: `${data.bondsExpiringWithin90DaysCount} bond${data.bondsExpiringWithin90DaysCount === 1 ? '' : 's'} expiring within 90 days${expiringSoonCount > 0 ? ` (${expiringSoonCount} within 14 days)` : ''}`,
      href: '#bonds-outstanding',
    });
  }

  const regSummary = data.dealRegistrationSummary;
  if (regSummary.pendingOemCount > 0 || regSummary.expiringSoonCount > 0) {
    const parts = [];
    if (regSummary.pendingOemCount > 0) parts.push(`${regSummary.pendingOemCount} pending OEM approval`);
    if (regSummary.expiringSoonCount > 0) parts.push(`${regSummary.expiringSoonCount} expiring within 30 days`);
    actions.push({
      icon: 'repeat',
      urgency: regSummary.expiringSoonCount > 0 ? 'high' : 'medium',
      title: 'Review deal registrations',
      detail: parts.join(', '),
      href: '#deal-regs',
    });
  }

  const urgencyRank = { urgent: 0, high: 1, medium: 2 };
  actions.sort((a, b) => urgencyRank[a.urgency] - urgencyRank[b.urgency]);
  return actions;
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

function distinctSorted(values) {
  return [...new Set(values)].sort((a, b) => a.localeCompare(b));
}

function buildDashboardRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const divisionParam = req.query.division ? String(req.query.division) : undefined;
      const [data, divisions, allBids] = await Promise.all([
        req.api.get(`/api/dashboard${divisionParam ? `?division=${encodeURIComponent(divisionParam)}` : ''}`),
        req.api.get('/api/divisions'),
        req.api.get('/api/bids'),
      ]);
      const divisionNames = divisions.map((d) => d.name);
      const pipelineKanban = buildPipelineKanban(allBids, divisionParam);
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
      // The Closing & Readiness tab's "Closing Schedule" sub-tab shows one continuous
      // date-grouped list spanning both already-fetched windows (0-14 days) - both are
      // already sorted ascending by closingAt server-side and don't overlap, so
      // concatenating them keeps the combined list in order without re-sorting.
      const closingScheduleRows = [...data.closingThisWeek, ...data.closingNextWeek];
      const closingScheduleByDate = groupByClosingDate(closingScheduleRows);
      const closingScheduleDivisions = distinctSorted(closingScheduleRows.flatMap((r) => r.divisionNames));
      const closingScheduleOwners = distinctSorted(closingScheduleRows.filter((r) => r.ownerLabel).map((r) => r.ownerLabel));
      const clarificationGroups = groupByDivision(data.upcomingClarifications, divisionNames, byDivisionNames);
      const bondsDisplayItems = buildBondsDisplayItems(
        groupByDivision(data.outstandingBonds, divisionNames, byDivisionNames),
        BOND_ROW_LIMIT
      );

      const updatedAtLabel = `${new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: '2-digit', month: 'short', year: 'numeric', timeZone: TIMEZONE }).format(now)} · updated ${new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: TIMEZONE }).format(now)}`;
      const priorityActions = buildPriorityActions(data);

      res.render('dashboard', {
        title: 'Dashboard', user: req.session.user, data, dateRanges,
        closingThisWeekByDate, clarificationGroups,
        closingScheduleByDate, closingScheduleRows, closingScheduleDivisions, closingScheduleOwners,
        pipelineKanban,
        bondsDisplayItems, bondRowLimit: BOND_ROW_LIMIT,
        currentDivision: divisionParam || null,
        updatedAtLabel, priorityActions,
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildDashboardRouter;
