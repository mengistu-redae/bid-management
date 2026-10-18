'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const STATUS_OPTIONS = ['PLANNING', 'DISPATCHED', 'IN_TRANSIT', 'CUSTOMS', 'CLEARED', 'DELIVERED'];
const SHIPMENT_MODE_OPTIONS = ['AIR', 'SEA', 'LAND'];

function buildLogisticsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const contracts = await req.api.get('/api/contracts');
      const shipmentLists = await Promise.all(contracts.map((c) => req.api.get(`/api/logistics?contractId=${c.id}`)));
      const contractById = new Map(contracts.map((c) => [c.id, c]));
      const shipments = shipmentLists.flat().map((s) => ({ ...s, contract: contractById.get(s.contractId) }));
      res.render('logistics/list', { title: 'Logistics', user: req.session.user, shipments });
    } catch (err) {
      next(err);
    }
  });

  router.get('/new', async (req, res, next) => {
    try {
      const contractId = req.query.contractId || '';
      if (contractId) {
        const contract = await req.api.get(`/api/contracts/${contractId}`);
        res.render('logistics/new', { title: 'New shipment', user: req.session.user, contract, contracts: null, modeOptions: SHIPMENT_MODE_OPTIONS });
        return;
      }
      const contracts = await req.api.get('/api/contracts');
      res.render('logistics/new', { title: 'New shipment', user: req.session.user, contract: null, contracts, modeOptions: SHIPMENT_MODE_OPTIONS });
    } catch (err) {
      next(err);
    }
  });

  router.post('/', async (req, res, next) => {
    try {
      const shipment = await req.api.post('/api/logistics', {
        contractId: req.body.contractId,
        shipmentMode: req.body.shipmentMode || null,
        etaAt: req.body.etaAt || null,
        notes: req.body.notes || null,
      });
      setFlash(req, 'success', 'Shipment created.');
      res.redirect(`/logistics/${shipment.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/logistics/new${req.body.contractId ? `?contractId=${req.body.contractId}` : ''}`);
      }
      next(err);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const [shipment, delayReasonCodes, attachments] = await Promise.all([
        req.api.get(`/api/logistics/${req.params.id}`),
        req.api.get('/api/delay-reason-codes'),
        req.api.get(`/api/attachments?entityType=LOGISTICS&entityId=${req.params.id}`),
      ]);
      const contract = await req.api.get(`/api/contracts/${shipment.contractId}`);
      res.render('logistics/detail', {
        title: `${shipment.shipmentMode} shipment`, user: req.session.user, shipment, contract, statusOptions: STATUS_OPTIONS, modeOptions: SHIPMENT_MODE_OPTIONS,
        delayReasonCodes: delayReasonCodes.filter((c) => c.active),
        attachments,
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/update', async (req, res, next) => {
    try {
      await req.api.put(`/api/logistics/${req.params.id}`, {
        shipmentMode: req.body.shipmentMode || null,
        etaAt: req.body.etaAt || null,
        notes: req.body.notes || null,
      });
      setFlash(req, 'success', 'Saved.');
      res.redirect(`/logistics/${req.params.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/status', async (req, res, next) => {
    try {
      await req.api.put(`/api/logistics/${req.params.id}/status`, {
        newStatus: req.body.newStatus, reason: req.body.reason || null, delayReasonCode: req.body.delayReasonCode || null,
      });
      setFlash(req, 'success', `Status changed to ${req.body.newStatus}.`);
      res.redirect(`/logistics/${req.params.id}`);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/logistics/${req.params.id}`);
      }
      next(err);
    }
  });

  return router;
}

module.exports = buildLogisticsRouter;
