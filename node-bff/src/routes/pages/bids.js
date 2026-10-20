'use strict';

const express = require('express');
const { setFlash } = require('../../flash');
const { groupByDivision } = require('../../divisionGrouping');
const { buildSegments } = require('../../dealTimeline');

const STATUS_OPTIONS = ['IDENTIFIED', 'UNDER_REVIEW', 'PREPARING', 'SUBMITTED', 'OPENED', 'UNDER_EVALUATION', 'WON', 'LOST', 'DROPPED', 'CANCELLED'];
const WAITING_ON_OPTIONS = ['INTERNAL', 'CUSTOMER', 'OEM', 'DISTRIBUTOR', 'IMPLEMENTATION_PARTNER', 'BANK', 'FREIGHT_FORWARDER_CUSTOMS', 'OTHER'];
const TERMINAL_STATUSES = ['WON', 'LOST', 'DROPPED', 'CANCELLED'];
const SCOPE_TYPES = ['DELIVERY', 'IMPLEMENTATION', 'TRAINING', 'SUPPORT_RENEWAL'];
const PAGE_SIZE = 25;
const GROUP_LIMIT = 10;
const HISTORY_LIMIT = 8;
const DAY_MS = 24 * 60 * 60 * 1000;
const URGENT_CLOSING_DAYS = 5; // matches the dashboard's own red-flag window

/**
 * The checklist's 12 canonical item titles (exact strings, from V2__seed_reference_data.sql),
 * bucketed into the 4 visual groupings the bid-detail redesign spec suggests. Grouping is purely
 * a display concern - it doesn't touch stored data or add a "category" field - so any item whose
 * title isn't one of these 12 (i.e. a custom item someone added via "Add checklist item") falls
 * into its own "Additional items" bucket rather than being silently dropped.
 */
const CHECKLIST_GROUPS = [
  { label: 'Tender & Clarifications', titles: ['Tender document purchased', 'Clarification questions sent'] },
  { label: 'OEM & Commercial', titles: ['MAF from OEM', 'Vendor/distributor quotation', 'Price protection confirmed', 'Lead time confirmed'] },
  { label: 'Technical & Delivery', titles: ['Implementation partner quote', 'Training quote', 'Scope of work / HLD / LLD', 'Technical compliance sheet'] },
  { label: 'Financial & Submission', titles: ['Bid bond arranged', 'Financial offer approved', 'Final submission'] },
];

/** Splits one lot's checklist items into the 4 known groups (in their existing order) plus a trailing "Additional items" group for anything that doesn't match a canonical title. */
function groupChecklistItems(items) {
  const groups = CHECKLIST_GROUPS.map((g) => ({ label: g.label, items: items.filter((i) => g.titles.includes(i.title)) }));
  const grouped = new Set(CHECKLIST_GROUPS.flatMap((g) => g.titles));
  const extra = items.filter((i) => !grouped.has(i.title));
  if (extra.length > 0) groups.push({ label: 'Additional items', items: extra });
  return groups.filter((g) => g.items.length > 0);
}

/**
 * The bid-detail page's "Bid health summary" row - every figure here is derived from the same
 * `bid` object the page already rendered the long way, no new API calls. Value totals are kept
 * separate per currency (never summed), matching the rest of the app's money-display rule.
 */
function buildBidHealthSummary(bid) {
  const lots = bid.lots;
  const overallProgress = lots.length === 0 ? 0 : Math.round(lots.reduce((sum, l) => sum + l.checklistProgressPercent, 0) / lots.length);
  let totalItems = 0;
  let doneItems = 0;
  let valueEtb = 0;
  let valueUsd = 0;
  let anyPriced = false;
  for (const lot of lots) {
    totalItems += lot.checklistItems.length;
    doneItems += lot.checklistItems.filter((i) => i.done).length;
    if (lot.estimatedValue) {
      anyPriced = true;
      if (lot.estimatedValueCurrency === 'USD') valueUsd += lot.estimatedValue;
      else valueEtb += lot.estimatedValue;
    }
  }
  return {
    overallProgress, totalItems, doneItems, outstandingItems: totalItems - doneItems,
    lotCount: lots.length, valueEtb, valueUsd, anyPriced,
  };
}

/** Average checklist completion across a bid's lots - same simple-average formula DashboardService uses server-side, just computed here from the lot data the list page already fetched. */
function averageProgress(bid) {
  if (bid.lots.length === 0) return 0;
  return Math.round(bid.lots.reduce((sum, l) => sum + l.checklistProgressPercent, 0) / bid.lots.length);
}

/**
 * Attaches the list/table-row view's derived fields once, up front - division/owner labels, bond
 * requirement, readiness - so the table partial, the KPI totals and the sort comparators below
 * all read the exact same values instead of three places recomputing them (and risking drift).
 */
function decorateBid(bid) {
  const divisionNames = [...new Set(bid.lots.map((l) => (l.division ? l.division.name : null)).filter(Boolean))];
  const ownerNames = [...new Set(bid.lots.flatMap((l) => l.accountOfficers.map((o) => o.fullName)))];
  return {
    ...bid,
    divisionNames,
    divisionLabel: divisionNames.length ? divisionNames.join(', ') : 'No division',
    ownerNames,
    ownerLabel: ownerNames.length ? ownerNames.join(', ') : 'Unassigned',
    bondRequired: bid.lots.some((l) => l.bidBondAmount),
    progress: averageProgress(bid),
  };
}

/** One comparator-key function per sortable table column - every bid passed in must already be decorateBid()'d. */
const SORT_KEY_FNS = {
  title: (b) => b.title.toLowerCase(),
  customer: (b) => (b.organization ? b.organization.name.toLowerCase() : ''),
  division: (b) => b.divisionLabel.toLowerCase(),
  deadline: (b) => (b.closingAt ? new Date(b.closingAt).getTime() : Number.POSITIVE_INFINITY),
  bond: (b) => (b.bondRequired ? 1 : 0),
  readiness: (b) => b.progress,
  status: (b) => b.status,
};

/**
 * Stable sort (Array.prototype.sort is stable since Node 12) so ties keep their prior relative
 * order rather than jumping around on every click - the comparator sign flips for "desc" instead
 * of sorting ascending then reversing, since reversing would also flip (and so un-stabilize) ties.
 */
function sortBids(bids, sortKey, dir) {
  const keyFn = SORT_KEY_FNS[sortKey];
  if (!keyFn) return bids;
  const sign = dir === 'desc' ? -1 : 1;
  return [...bids].sort((a, b) => {
    const av = keyFn(a);
    const bv = keyFn(b);
    if (av < bv) return -1 * sign;
    if (av > bv) return 1 * sign;
    return 0;
  });
}

/**
 * Derives the list page's KPI row entirely from the already-fetched, already-filtered bid list -
 * no extra API calls. Mirrors the dashboard's own "closing soon" (7 days) and "outstanding bond"
 * (not yet returned) definitions so the numbers mean the same thing in both places.
 */
function buildListKpis(allBids) {
  const now = Date.now();
  const sevenDaysMs = 7 * DAY_MS;
  let closingSoonCount = 0;
  let bondEtb = 0;
  let bondUsd = 0;
  let pipelineEtb = 0;
  let pipelineUsd = 0;

  for (const bid of allBids) {
    const isTerminal = TERMINAL_STATUSES.includes(bid.status);
    if (!isTerminal && bid.closingAt) {
      const msUntilClose = new Date(bid.closingAt).getTime() - now;
      if (msUntilClose >= 0 && msUntilClose <= sevenDaysMs) closingSoonCount++;
    }
    for (const lot of bid.lots) {
      if (lot.bidBondAmount && !lot.bidBondReturned) {
        if (lot.bidBondCurrency === 'USD') bondUsd += lot.bidBondAmount;
        else bondEtb += lot.bidBondAmount;
      }
      if (lot.estimatedValue) {
        if (lot.estimatedValueCurrency === 'USD') pipelineUsd += lot.estimatedValue;
        else pipelineEtb += lot.estimatedValue;
      }
    }
  }
  return { closingSoonCount, bondEtb, bondUsd, pipelineEtb, pipelineUsd };
}

function buildBidsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const [rawBids, divisions, users, organizations] = await Promise.all([
        req.api.get(`/api/bids${buildFilterQuery(req.query)}`),
        req.api.get('/api/divisions'),
        req.api.get('/api/users'),
        req.api.get('/api/organizations'),
      ]);
      const decorated = rawBids.map(decorateBid);
      const sortKey = SORT_KEY_FNS[req.query.sort] ? req.query.sort : null;
      const sortDir = req.query.dir === 'desc' ? 'desc' : 'asc';
      const allBids = sortKey ? sortBids(decorated, sortKey, sortDir) : decorated;
      const totalCount = allBids.length;
      const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
      const page = Math.min(Math.max(1, Number(req.query.page) || 1), totalPages);
      const bids = allBids.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
      const kpis = buildListKpis(allBids);
      const now = new Date();
      const closingSoonRange = {
        closingFrom: new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Addis_Ababa' }).format(now),
        closingTo: new Intl.DateTimeFormat('en-CA', { timeZone: 'Africa/Addis_Ababa' }).format(new Date(now.getTime() + 7 * DAY_MS)),
      };
      res.render('bids/list', {
        title: 'Bids', user: req.session.user, bids, divisions, users, organizations,
        statusOptions: STATUS_OPTIONS, query: req.query,
        page, totalPages, totalCount, pageSize: PAGE_SIZE, kpis, closingSoonRange,
        sortKey, sortDir,
      });
    } catch (err) {
      next(err);
    }
  });

  router.get('/by-division', async (req, res, next) => {
    try {
      const [bids, divisions, users, organizations] = await Promise.all([
        req.api.get(`/api/bids${buildFilterQuery(req.query)}`),
        req.api.get('/api/divisions'),
        req.api.get('/api/users'),
        req.api.get('/api/organizations'),
      ]);
      const groups = groupByDivision(
        bids,
        divisions.map((d) => d.name),
        (bid) => bid.lots.map((l) => l.division && l.division.name),
        { includeEmpty: true }
      );
      res.render('bids/by-division', {
        title: 'Bids by division', user: req.session.user, divisions, users, organizations,
        statusOptions: STATUS_OPTIONS, query: req.query,
        groups, groupLimit: GROUP_LIMIT,
      });
    } catch (err) {
      next(err);
    }
  });

  router.get('/kanban', async (req, res, next) => {
    try {
      const bids = await req.api.get(`/api/bids${buildFilterQuery(req.query)}`);
      const columns = STATUS_OPTIONS.map((status) => ({
        status,
        bids: bids.filter((b) => b.status === status),
      }));
      res.render('bids/kanban', { title: 'Kanban', user: req.session.user, columns, query: req.query });
    } catch (err) {
      next(err);
    }
  });

  router.get('/calendar', async (req, res, next) => {
    try {
      const today = new Date();
      const year = req.query.year ? Number(req.query.year) : today.getFullYear();
      const month = req.query.month ? Number(req.query.month) : today.getMonth() + 1; // 1-12
      const bids = await req.api.get('/api/bids');
      const days = buildCalendarGrid(year, month, bids);
      // Same UTC-sliced-ISO convention as each cell's own "date" key, so the
      // "today" comparison in the template is apples-to-apples (not a second,
      // Africa/Addis_Ababa-aware "today" that could disagree with the grid).
      const todayKey = new Date().toISOString().slice(0, 10);
      res.render('bids/calendar', { title: 'Calendar', user: req.session.user, year, month, days, todayKey });
    } catch (err) {
      next(err);
    }
  });

  router.get('/export', async (req, res, next) => {
    try {
      const { ensureFreshToken } = require('../../apiClient');
      const accessToken = await ensureFreshToken(req, req.app.get('oidcGetClient'));
      const headers = {};
      if (accessToken) headers.authorization = `Bearer ${accessToken}`;
      const upstream = await fetch(`${process.env.API_BASE_URL}/api/bids/export${buildFilterQuery(req.query)}`, { headers });
      res.status(upstream.status);
      res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/octet-stream');
      res.setHeader('Content-Disposition', upstream.headers.get('content-disposition') || 'attachment; filename="bids-export.xlsx"');
      res.send(Buffer.from(await upstream.arrayBuffer()));
    } catch (err) {
      next(err);
    }
  });

  router.get('/new', async (req, res, next) => {
    try {
      const [divisions, users] = await Promise.all([req.api.get('/api/divisions'), req.api.get('/api/users')]);
      res.render('bids/new', {
        title: 'New tender',
        user: req.session.user,
        divisions,
        users,
        scopeTypes: SCOPE_TYPES,
        duplicates: null,
        form: {},
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/', async (req, res, next) => {
    try {
      const body = req.body;
      const scoutNames = splitList(body.scoutNames);
      const accountOfficerNames = splitList(body.accountOfficerNames);
      const scopeTypes = Array.isArray(body.scopeTypes) ? body.scopeTypes : (body.scopeTypes ? [body.scopeTypes] : []);

      let organizationIdForDuplicateCheck = body.organizationId || null;
      if (!organizationIdForDuplicateCheck && body.organizationName) {
        // Best-effort: a substring match on the name the scout just typed,
        // good enough to catch "they typed the exact same org name again"
        // without a full organization picker on this fast-entry form.
        const candidates = await req.api.get(`/api/organizations?q=${encodeURIComponent(body.organizationName)}`);
        if (candidates.length === 1) {
          organizationIdForDuplicateCheck = candidates[0].id;
        }
      }

      if (body.confirmDuplicate !== 'true' && organizationIdForDuplicateCheck && body.referenceNumber) {
        const duplicates = await req.api.get(
          `/api/bids/duplicates?organizationId=${encodeURIComponent(organizationIdForDuplicateCheck)}&referenceNumber=${encodeURIComponent(body.referenceNumber)}`
        );
        if (duplicates.length > 0) {
          const [divisions, users] = await Promise.all([req.api.get('/api/divisions'), req.api.get('/api/users')]);
          return res.render('bids/new', {
            title: 'New tender', user: req.session.user, divisions, users, scopeTypes: SCOPE_TYPES, duplicates, form: body,
          });
        }
      }

      const payload = {
        organizationId: body.organizationId || null,
        organizationName: body.organizationId ? null : body.organizationName,
        sector: body.sector || null,
        title: body.title,
        referenceNumber: body.referenceNumber || null,
        source: body.source || null,
        scoutedDate: body.scoutedDate || null,
        scoutNames,
        closingAt: toInstant(body.closingDate, body.closingTime),
        openingAt: toInstant(body.openingDate, body.openingTime),
        clarificationDeadline: body.clarificationDate ? toInstant(body.clarificationDate, null) : null,
        bidValidityDays: body.bidValidityDays ? Number(body.bidValidityDays) : null,
        notes: body.notes || null,
        firstLot: {
          lotLabel: 'Lot 1',
          description: null,
          divisionCode: body.divisionCode || null,
          estimatedValue: body.estimatedValue ? Number(body.estimatedValue) : null,
          estimatedValueCurrency: body.estimatedValueCurrency || null,
          bidBondAmount: body.bidBondAmount ? Number(body.bidBondAmount) : null,
          bidBondCurrency: body.bidBondCurrency || null,
          bidBondValidityDays: body.bidBondValidityDays ? Number(body.bidBondValidityDays) : null,
          bidBondForm: body.bidBondForm || null,
          oemBrand: body.oemBrand || null,
          scopeTypes,
          accountOfficerNames,
        },
      };

      const created = await req.api.post('/api/bids', payload);
      setFlash(req, 'success', 'Bid created.');
      res.redirect(`/bids/${created.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const [bid, historyAsc, activity, allDealRegistrations, delayReasonCodes, divisions] = await Promise.all([
        req.api.get(`/api/bids/${req.params.id}`),
        req.api.get(`/api/bids/${req.params.id}/status-history`),
        req.api.get(`/api/bids/${req.params.id}/activity`),
        req.api.get('/api/deal-registrations'),
        req.api.get('/api/delay-reason-codes'),
        req.api.get('/api/divisions'),
      ]);
      const dealRegistrations = allDealRegistrations.filter((r) => r.bidId === bid.id);
      // Most-recent-first for display - the API returns it oldest-first (insertion order).
      const history = [...historyAsc].reverse();
      const [holdFlags, attachments, keyDates] = await Promise.all([
        Promise.all(bid.lots.map((lot) => req.api.get(`/api/holds/active?entityType=LOT&entityId=${lot.id}`))),
        req.api.get(`/api/attachments?entityType=BID&entityId=${bid.id}`),
        req.api.get(`/api/bids/${bid.id}/key-dates`),
      ]);
      const lotOnHold = {};
      bid.lots.forEach((lot, i) => { lotOnHold[lot.id] = holdFlags[i]; });
      const daysUntilClosing = bid.closingAt && !TERMINAL_STATUSES.includes(bid.status)
        ? Math.ceil((new Date(bid.closingAt).getTime() - Date.now()) / DAY_MS)
        : null;
      const closingUrgent = daysUntilClosing !== null && daysUntilClosing <= URGENT_CLOSING_DAYS;
      // Only WON bids can have a contract (ContractService enforces this) - skip the extra call otherwise.
      let contract = null;
      if (bid.status === 'WON') {
        const contracts = await req.api.get('/api/contracts');
        contract = contracts.find((c) => c.bidId === bid.id) || null;
      }
      const health = buildBidHealthSummary(bid);
      const checklistGroupsByLot = {};
      bid.lots.forEach((lot) => { checklistGroupsByLot[lot.id] = groupChecklistItems(lot.checklistItems); });
      res.render('bids/detail', {
        title: bid.title, user: req.session.user, bid, history, activity, dealRegistrations, statusOptions: STATUS_OPTIONS,
        daysUntilClosing, closingUrgent, historyLimit: HISTORY_LIMIT,
        delayReasonCodes: delayReasonCodes.filter((c) => c.active),
        waitingOnOptions: WAITING_ON_OPTIONS,
        contract, divisions, scopeTypes: SCOPE_TYPES, lotOnHold, attachments, keyDates,
        health, checklistGroupsByLot,
      });
    } catch (err) {
      next(err);
    }
  });

  router.get('/:id/deal360', async (req, res, next) => {
    try {
      const [bid, allOpportunities, allDealRegistrations, allContracts, allTargets] = await Promise.all([
        req.api.get(`/api/bids/${req.params.id}`),
        req.api.get('/api/opportunities'),
        req.api.get('/api/deal-registrations'),
        req.api.get('/api/contracts'),
        req.api.get('/api/stage-targets'),
      ]);
      const opportunity = allOpportunities.find((o) => o.convertedBidId === bid.id) || null;
      const dealRegistrations = allDealRegistrations.filter((r) => r.bidId === bid.id);
      const contract = allContracts.find((c) => c.bidId === bid.id) || null;
      const logistics = contract ? await req.api.get(`/api/logistics?contractId=${contract.id}`) : [];

      const now = new Date();
      const targetsFor = (entityType) => allTargets.filter((t) => t.entityType === entityType);

      const rows = [];
      if (opportunity) {
        const history = await req.api.get(`/api/stage-history?entityType=OPPORTUNITY&entityId=${opportunity.id}`);
        rows.push({ kind: 'Opportunity', label: opportunity.title, href: `/opportunities/${opportunity.id}`,
          segments: buildSegments(history.filter((h) => h.stageField === 'stage'), targetsFor('OPPORTUNITY'), now) });
      }
      for (const reg of dealRegistrations) {
        const history = await req.api.get(`/api/stage-history?entityType=DEAL_REGISTRATION&entityId=${reg.id}`);
        rows.push({ kind: 'Deal Registration', label: reg.oem.name, href: `/deal-registrations/${reg.id}`,
          segments: buildSegments(history.filter((h) => h.stageField === 'status'), targetsFor('DEAL_REGISTRATION'), now) });
      }
      for (const lot of bid.lots) {
        const history = await req.api.get(`/api/stage-history?entityType=LOT&entityId=${lot.id}`);
        rows.push({ kind: 'Bid Lot', label: lot.lotLabel, href: `/bids/${bid.id}`,
          segments: buildSegments(history.filter((h) => h.stageField === 'status'), targetsFor('LOT'), now) });
      }
      if (contract) {
        const history = await req.api.get(`/api/stage-history?entityType=CONTRACT&entityId=${contract.id}`);
        rows.push({ kind: 'Contract', label: contract.contractNumber || 'Contract', href: null,
          segments: buildSegments(history.filter((h) => h.stageField === 'status'), targetsFor('CONTRACT'), now) });
      }
      for (const shipment of logistics) {
        const history = await req.api.get(`/api/stage-history?entityType=LOGISTICS&entityId=${shipment.id}`);
        rows.push({ kind: 'Logistics', label: `${shipment.shipmentMode} shipment`, href: null,
          segments: buildSegments(history.filter((h) => h.stageField === 'status'), targetsFor('LOGISTICS'), now) });
      }

      res.render('bids/deal360', { title: `Deal 360 - ${bid.title}`, user: req.session.user, bid, opportunity, dealRegistrations, contract, logistics, rows });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/status', async (req, res, next) => {
    try {
      await req.api.post(`/api/bids/${req.params.id}/status`, { newStatus: req.body.newStatus, reason: req.body.reason || null });
      setFlash(req, 'success', `Status changed to ${req.body.newStatus}.`);
      res.redirect(`/bids/${req.params.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/bids/${req.params.id}`);
      }
      next(err);
    }
  });

  router.post('/:id/go-no-go', async (req, res, next) => {
    try {
      await req.api.put(`/api/bids/${req.params.id}`, {
        goNoGoDecision: req.body.goNoGoDecision || null,
        goNoGoReason: req.body.goNoGoReason || null,
      });
      setFlash(req, 'success', 'Go/no-go saved.');
      res.redirect(`/bids/${req.params.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/bids/${req.params.id}`);
      }
      next(err);
    }
  });

  router.post('/:id/dates', async (req, res, next) => {
    try {
      const body = req.body;
      await req.api.put(`/api/bids/${req.params.id}`, {
        closingAt: toInstant(body.closingDate, body.closingTime),
        openingAt: toInstant(body.openingDate, body.openingTime),
        clarificationDeadline: body.clarificationDate ? toInstant(body.clarificationDate, null) : null,
      });
      setFlash(req, 'success', 'Dates updated.');
      res.redirect(`/bids/${req.params.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/bids/${req.params.id}`);
      }
      next(err);
    }
  });

  router.post('/:id/lots', async (req, res, next) => {
    try {
      const body = req.body;
      const scopeTypes = Array.isArray(body.scopeTypes) ? body.scopeTypes : (body.scopeTypes ? [body.scopeTypes] : []);
      const payload = {
        lotLabel: body.lotLabel || null,
        description: body.description || null,
        divisionCode: body.divisionCode || null,
        estimatedValue: body.estimatedValue ? Number(body.estimatedValue) : null,
        estimatedValueCurrency: body.estimatedValueCurrency || null,
        bidBondAmount: body.bidBondAmount ? Number(body.bidBondAmount) : null,
        bidBondCurrency: body.bidBondCurrency || null,
        bidBondValidityDays: body.bidBondValidityDays ? Number(body.bidBondValidityDays) : null,
        bidBondForm: body.bidBondForm || null,
        oemBrand: body.oemBrand || null,
        scopeTypes,
        accountOfficerNames: splitList(body.accountOfficerNames),
      };
      await req.api.post(`/api/bids/${req.params.id}/lots`, payload);
      setFlash(req, 'success', 'Lot added.');
      res.redirect(`/bids/${req.params.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/bids/${req.params.id}`);
      }
      next(err);
    }
  });

  router.post('/lots/:lotId/waiting-on', async (req, res, next) => {
    try {
      await req.api.put(`/api/waiting-on?entityType=LOT&entityId=${req.params.lotId}`, {
        partyType: req.body.partyType, personId: req.body.personId || null, note: req.body.note || null,
      });
      setFlash(req, 'success', 'Waiting-on updated.');
      res.redirect(`/bids/${req.body.bidId}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/activity', async (req, res, next) => {
    try {
      await req.api.post(`/api/bids/${req.params.id}/activity`, { note: req.body.note });
      res.redirect(`/bids/${req.params.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/lots/:lotId/status', async (req, res, next) => {
    try {
      await req.api.post(`/api/lots/${req.params.lotId}/status`, {
        newStatus: req.body.newStatus, reason: req.body.reason || null, delayReasonCode: req.body.delayReasonCode || null,
      });
      setFlash(req, 'success', `Lot status changed to ${req.body.newStatus}.`);
      res.redirect(`/bids/${req.body.bidId}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/bids/${req.body.bidId}`);
      }
      next(err);
    }
  });

  router.post('/lots/:lotId/outcome', async (req, res, next) => {
    try {
      await req.api.post(`/api/lots/${req.params.lotId}/outcome`, {
        outcome: req.body.outcome || null,
        winnerName: req.body.winnerName || null,
        winningPrice: req.body.winningPrice ? Number(req.body.winningPrice) : null,
        winningPriceCurrency: req.body.winningPriceCurrency || null,
      });
      setFlash(req, 'success', 'Outcome recorded.');
      res.redirect(`/bids/${req.body.bidId}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/lots/:lotId/bond-returned', async (req, res, next) => {
    try {
      await req.api.post(`/api/lots/${req.params.lotId}/bond-returned`, { returned: true, returnedAt: null });
      setFlash(req, 'success', 'Bid bond marked as returned.');
      res.redirect(`/bids/${req.body.bidId}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/lots/:lotId/bond-details', async (req, res, next) => {
    try {
      await req.api.post(`/api/lots/${req.params.lotId}/bond-details`, {
        issuingBank: req.body.issuingBank || null,
        issueDate: req.body.issueDate || null,
      });
      setFlash(req, 'success', 'Bid bond details saved.');
      res.redirect(`/bids/${req.body.bidId}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/checklist/:itemId/toggle', async (req, res, next) => {
    // Progressive enhancement: the checklist's own JS fetches this with an
    // explicit Accept: application/json header and updates the item/lot
    // progress bar in place; a plain (no-JS) form submit falls back to the
    // old full-page redirect, landing back on this exact item via its #anchor.
    const wantsJson = (req.get('accept') || '').includes('application/json');
    try {
      const updatedItem = await req.api.put(`/api/lots/${req.body.lotId}/checklist-items/${req.params.itemId}`, { done: req.body.done === 'true' });
      if (!wantsJson) {
        return res.redirect(`/bids/${req.body.bidId}#item-${req.params.itemId}`);
      }
      // The toggle itself already succeeded above - re-fetching the bid just for
      // the lot's rolled-up percent is a "nice to have", not re-fetched via a
      // dedicated endpoint (none exists) so a failure here shouldn't report the
      // whole request as failed.
      let percent = null;
      try {
        const bid = await req.api.get(`/api/bids/${req.body.bidId}`);
        const lot = bid.lots.find((l) => l.id === req.body.lotId);
        percent = lot ? lot.checklistProgressPercent : null;
      } catch (percentErr) {
        // ignored - see comment above
      }
      res.json({ done: updatedItem.done, percent });
    } catch (err) {
      if (wantsJson) {
        const status = err.status && err.status < 600 ? err.status : 500;
        return res.status(status).json({ message: err.message || 'Failed to update checklist item' });
      }
      next(err);
    }
  });

  router.post('/lots/:lotId/checklist-items', async (req, res, next) => {
    try {
      await req.api.post(`/api/lots/${req.params.lotId}/checklist-items`, { title: req.body.title, ownerId: req.body.ownerId || null, dueDate: req.body.dueDate || null });
      res.redirect(`/bids/${req.body.bidId}`);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

function splitList(raw) {
  if (!raw) return [];
  return raw.split(/[,/&]/).map((s) => s.trim()).filter(Boolean);
}

function toInstant(dateStr, timeStr) {
  if (!dateStr) return null;
  const time = timeStr && timeStr.length > 0 ? timeStr : '00:00';
  // Interpreted as Africa/Addis_Ababa local time (UTC+3, no DST) - see the brief's timezone requirement.
  return `${dateStr}T${time}:00+03:00`;
}

function buildFilterQuery(query) {
  const params = new URLSearchParams();
  if (query.divisionId) params.set('divisionId', query.divisionId);
  if (query.status) params.set('status', query.status);
  if (query.officerId) params.set('officerId', query.officerId);
  if (query.organizationId) params.set('organizationId', query.organizationId);
  if (query.closingFrom) params.set('closingFrom', toInstant(query.closingFrom, null));
  if (query.closingTo) params.set('closingTo', toInstant(query.closingTo, '23:59'));
  if (query.search) params.set('search', query.search);
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

/** A 6-week month grid (always 42 cells, Sunday-first) with each bid's closing/opening/clarification events bucketed onto the right day. */
function buildCalendarGrid(year, month, bids) {
  const firstOfMonth = new Date(Date.UTC(year, month - 1, 1));
  const startWeekday = firstOfMonth.getUTCDay(); // 0 = Sunday
  const gridStart = new Date(firstOfMonth);
  gridStart.setUTCDate(gridStart.getUTCDate() - startWeekday);

  const eventsByDate = {};
  const addEvent = (iso, bid, kind) => {
    if (!iso) return;
    const key = iso.slice(0, 10);
    if (!eventsByDate[key]) eventsByDate[key] = [];
    eventsByDate[key].push({
      bidId: bid.id, title: bid.title, organizationName: bid.organization ? bid.organization.name : '-',
      status: bid.status, kind, time: iso, redFlag: bid.redFlag, redFlagReason: bid.redFlagReason,
    });
  };
  for (const bid of bids) {
    addEvent(bid.closingAt, bid, 'closing');
    addEvent(bid.openingAt, bid, 'opening');
    addEvent(bid.clarificationDeadline, bid, 'clarification');
  }
  for (const key of Object.keys(eventsByDate)) {
    eventsByDate[key].sort((a, b) => a.time.localeCompare(b.time));
  }

  const days = [];
  for (let i = 0; i < 42; i++) {
    const d = new Date(gridStart);
    d.setUTCDate(d.getUTCDate() + i);
    const key = d.toISOString().slice(0, 10);
    days.push({
      date: key,
      day: d.getUTCDate(),
      inMonth: d.getUTCMonth() === month - 1,
      events: eventsByDate[key] || [],
    });
  }
  return days;
}

module.exports = buildBidsRouter;
