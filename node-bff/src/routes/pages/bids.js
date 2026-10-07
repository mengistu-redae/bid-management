'use strict';

const express = require('express');

const STATUS_OPTIONS = ['IDENTIFIED', 'UNDER_REVIEW', 'PREPARING', 'SUBMITTED', 'OPENED', 'UNDER_EVALUATION', 'WON', 'LOST', 'DROPPED', 'CANCELLED'];
const SCOPE_TYPES = ['DELIVERY', 'IMPLEMENTATION', 'TRAINING', 'SUPPORT_RENEWAL'];

function buildBidsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const bids = await req.api.get('/api/bids');
      res.render('bids/list', { title: 'Bids', user: req.session.user, bids });
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
      res.redirect(`/bids/${created.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const [bid, history, activity] = await Promise.all([
        req.api.get(`/api/bids/${req.params.id}`),
        req.api.get(`/api/bids/${req.params.id}/status-history`),
        req.api.get(`/api/bids/${req.params.id}/activity`),
      ]);
      res.render('bids/detail', {
        title: bid.title, user: req.session.user, bid, history, activity, statusOptions: STATUS_OPTIONS,
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/status', async (req, res, next) => {
    try {
      await req.api.post(`/api/bids/${req.params.id}/status`, { newStatus: req.body.newStatus, reason: req.body.reason || null });
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

module.exports = buildBidsRouter;
