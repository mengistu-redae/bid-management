'use strict';

const express = require('express');
const { groupByDivision } = require('../../divisionGrouping');

const TIMEZONE = 'Africa/Addis_Ababa';
const DAY_MS = 24 * 60 * 60 * 1000;
const RECENT_DEAL_REGISTRATIONS_LIMIT = 6;

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
      currentGroup = { key, label: `${weekday} ${dayMonth}`, weekday, dayMonth, relative, rows: [] };
      groups.push(currentGroup);
      currentKey = key;
    }
    currentGroup.rows.push(row);
  }
  // Per-date ready/gap/critical counts for the Closing Schedule's date-row summary (the
  // collapsed row shows these without needing to expand into the individual bid list below).
  // "critical" is a subset of "gap", not a third exclusive bucket - a redFlag bid (closing
  // soon AND still missing its MAF/vendor quote) always has at least one readiness gap by
  // definition, it's just a more urgent one, so the three counts intentionally don't sum to
  // the group's total bid count.
  for (const group of groups) {
    group.readyCount = group.rows.filter((r) => r.readinessGaps.length === 0).length;
    group.gapCount = group.rows.filter((r) => r.readinessGaps.length > 0).length;
    group.criticalCount = group.rows.filter((r) => r.redFlag).length;
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
      const [data, divisions, allBids, dealRegistrations] = await Promise.all([
        req.api.get(`/api/dashboard${divisionParam ? `?division=${encodeURIComponent(divisionParam)}` : ''}`),
        req.api.get('/api/divisions'),
        req.api.get('/api/bids'),
        req.api.get('/api/deal-registrations'),
      ]);
      const divisionNames = divisions.map((d) => d.name);
      const pipelineKanban = buildPipelineKanban(allBids, divisionParam);
      // The Bonds & Deal Regs tab's "Recent Deal Registrations" list needs a short, real
      // project label per row - the registration itself only carries a bidId/opportunityId
      // (see DealRegistrationDto), so resolve against the bid list already fetched above
      // for the Kanban board, rather than a second round-trip per registration.
      const bidTitleById = new Map(allBids.map((b) => [b.id, b.title]));
      const recentDealRegistrations = [...dealRegistrations]
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
        .slice(0, RECENT_DEAL_REGISTRATIONS_LIMIT)
        .map((reg) => ({
          ...reg,
          title: (reg.bidId && bidTitleById.get(reg.bidId)) || reg.registrationId || `${reg.oem ? reg.oem.name : 'OEM'} registration`,
        }));
      const now = new Date();
      // Exact date-range equivalents of the dashboard's own "this week"/"next
      // week" windows, so each section's "View all" link reopens the same
      // slice of bids on the full list page instead of an approximation.
      const dateRanges = {
        closingThisWeek: { closingFrom: isoDate(now), closingTo: isoDate(new Date(now.getTime() + 7 * DAY_MS)) },
        closingNextWeek: { closingFrom: isoDate(new Date(now.getTime() + 7 * DAY_MS)), closingTo: isoDate(new Date(now.getTime() + 14 * DAY_MS)) },
        // Today/Next-3-Days equivalents of the Closing Schedule's own quick-range pills, so
        // the "Open in bid list" link below it can follow whichever range is active instead
        // of always pointing at the full 14-day window regardless of the current filter.
        closingToday: { closingFrom: isoDate(now), closingTo: isoDate(new Date(now.getTime() + 1 * DAY_MS)) },
        closingNext3Days: { closingFrom: isoDate(now), closingTo: isoDate(new Date(now.getTime() + 3 * DAY_MS)) },
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

      const updatedAtLabel = `${new Intl.DateTimeFormat('en-GB', { weekday: 'long', day: '2-digit', month: 'short', year: 'numeric', timeZone: TIMEZONE }).format(now)} · updated ${new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: TIMEZONE }).format(now)}`;
      const priorityActions = buildPriorityActions(data);

      res.render('dashboard', {
        title: 'Dashboard', user: req.session.user, data, dateRanges,
        closingThisWeekByDate, clarificationGroups,
        closingScheduleByDate, closingScheduleRows, closingScheduleDivisions, closingScheduleOwners,
        pipelineKanban,
        dealRegistrationsTotal: dealRegistrations.length, recentDealRegistrations,
        currentDivision: divisionParam || null,
        updatedAtLabel, priorityActions,
      });
    } catch (err) {
      next(err);
    }
  });

  router.get('/activity', async (req, res, next) => {
    try {
      const divisionParam = req.query.division ? String(req.query.division) : undefined;
      const activity = await req.api.get(`/api/dashboard/activity${divisionParam ? `?division=${encodeURIComponent(divisionParam)}` : ''}`);
      res.render('dashboard-activity', {
        title: 'Recent activity', user: req.session.user, activity, currentDivision: divisionParam || null,
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildDashboardRouter;
