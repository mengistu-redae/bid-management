'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const STATUS_OPTIONS = ['AWARDED', 'SIGNED', 'IN_DELIVERY', 'COMPLETED', 'TERMINATED'];

function buildContractsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const contracts = await req.api.get('/api/contracts');
      res.render('contracts/list', { title: 'Contracts', user: req.session.user, contracts });
    } catch (err) {
      next(err);
    }
  });

  router.get('/new', async (req, res, next) => {
    try {
      const bidId = req.query.bidId || '';
      if (bidId) {
        const bid = await req.api.get(`/api/bids/${bidId}`);
        res.render('contracts/new', { title: 'New contract', user: req.session.user, bid, eligibleBids: null });
        return;
      }
      const [wonBids, contracts] = await Promise.all([
        req.api.get('/api/bids?status=WON'),
        req.api.get('/api/contracts'),
      ]);
      const eligibleBids = wonBids.filter((b) => !contracts.some((c) => c.bidId === b.id));
      res.render('contracts/new', { title: 'New contract', user: req.session.user, bid: null, eligibleBids });
    } catch (err) {
      next(err);
    }
  });

  router.post('/', async (req, res, next) => {
    try {
      const contract = await req.api.post('/api/contracts', {
        bidId: req.body.bidId,
        contractNumber: req.body.contractNumber || null,
        deliveryDeadline: req.body.deliveryDeadline || null,
        notes: req.body.notes || null,
      });
      setFlash(req, 'success', 'Contract created.');
      res.redirect(`/contracts/${contract.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/contracts/new${req.body.bidId ? `?bidId=${req.body.bidId}` : ''}`);
      }
      next(err);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const [contract, logistics, delayReasonCodes, attachments] = await Promise.all([
        req.api.get(`/api/contracts/${req.params.id}`),
        req.api.get(`/api/logistics?contractId=${req.params.id}`),
        req.api.get('/api/delay-reason-codes'),
        req.api.get(`/api/attachments?entityType=CONTRACT&entityId=${req.params.id}`),
      ]);
      res.render('contracts/detail', {
        title: contract.contractNumber || 'Contract', user: req.session.user, contract, logistics, statusOptions: STATUS_OPTIONS,
        delayReasonCodes: delayReasonCodes.filter((c) => c.active),
        attachments,
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/update', async (req, res, next) => {
    try {
      await req.api.put(`/api/contracts/${req.params.id}`, {
        contractNumber: req.body.contractNumber || null,
        deliveryDeadline: req.body.deliveryDeadline || null,
        notes: req.body.notes || null,
      });
      setFlash(req, 'success', 'Saved.');
      res.redirect(`/contracts/${req.params.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/status', async (req, res, next) => {
    try {
      await req.api.put(`/api/contracts/${req.params.id}/status`, {
        newStatus: req.body.newStatus, reason: req.body.reason || null, delayReasonCode: req.body.delayReasonCode || null,
      });
      setFlash(req, 'success', `Status changed to ${req.body.newStatus}.`);
      res.redirect(`/contracts/${req.params.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/contracts/${req.params.id}`);
      }
      next(err);
    }
  });

  return router;
}

module.exports = buildContractsRouter;
