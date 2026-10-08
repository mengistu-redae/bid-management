'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const STATUS_OPTIONS = ['IDENTIFIED', 'UNDER_REVIEW', 'PREPARING', 'SUBMITTED', 'OPENED', 'UNDER_EVALUATION', 'WON', 'LOST', 'DROPPED', 'CANCELLED'];
const SCOPE_TYPES = ['DELIVERY', 'IMPLEMENTATION', 'TRAINING', 'SUPPORT_RENEWAL'];
const PAGE_SIZE = 25;

function buildBidsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const [allBids, divisions, users, organizations] = await Promise.all([
        req.api.get(`/api/bids${buildFilterQuery(req.query)}`),
        req.api.get('/api/divisions'),
        req.api.get('/api/users'),
        req.api.get('/api/organizations'),
      ]);
      const totalCount = allBids.length;
      const totalPages = Math.max(1, Math.ceil(totalCount / PAGE_SIZE));
      const page = Math.min(Math.max(1, Number(req.query.page) || 1), totalPages);
      const bids = allBids.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);
      res.render('bids/list', {
        title: 'Bids', user: req.session.user, bids, divisions, users, organizations,
        statusOptions: STATUS_OPTIONS, query: req.query,
        page, totalPages, totalCount, pageSize: PAGE_SIZE,
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
      res.render('bids/calendar', { title: 'Calendar', user: req.session.user, year, month, days });
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
      const [bid, history, activity, allDealRegistrations] = await Promise.all([
        req.api.get(`/api/bids/${req.params.id}`),
        req.api.get(`/api/bids/${req.params.id}/status-history`),
        req.api.get(`/api/bids/${req.params.id}/activity`),
        req.api.get('/api/deal-registrations'),
      ]);
      const dealRegistrations = allDealRegistrations.filter((r) => r.bidId === bid.id);
      res.render('bids/detail', {
        title: bid.title, user: req.session.user, bid, history, activity, dealRegistrations, statusOptions: STATUS_OPTIONS,
      });
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
      await req.api.post(`/api/lots/${req.params.lotId}/status`, { newStatus: req.body.newStatus, reason: req.body.reason || null });
      setFlash(req, 'success', `Lot status changed to ${req.body.newStatus}.`);
      res.redirect(`/bids/${req.body.bidId}`);
    } catch (err) {
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
    try {
      await req.api.put(`/api/lots/${req.body.lotId}/checklist-items/${req.params.itemId}`, { done: req.body.done === 'true' });
      res.redirect(`/bids/${req.body.bidId}`);
    } catch (err) {
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
  const addEvent = (iso, label, bidId, kind) => {
    if (!iso) return;
    const key = iso.slice(0, 10);
    if (!eventsByDate[key]) eventsByDate[key] = [];
    eventsByDate[key].push({ label, bidId, kind });
  };
  for (const bid of bids) {
    addEvent(bid.closingAt, bid.title, bid.id, 'closing');
    addEvent(bid.openingAt, bid.title, bid.id, 'opening');
    addEvent(bid.clarificationDeadline, bid.title, bid.id, 'clarification');
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
