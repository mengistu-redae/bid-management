'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const STATUS_OPTIONS = ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'EXPIRED'];

function buildDealRegistrationsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const registrations = await req.api.get('/api/deal-registrations');
      res.render('dealRegistrations/list', { title: 'Deal Registrations', user: req.session.user, registrations });
    } catch (err) {
      next(err);
    }
  });

  router.get('/new', async (req, res, next) => {
    try {
      const oems = await req.api.get('/api/oems');
      res.render('dealRegistrations/new', { title: 'New deal registration', user: req.session.user, oems, statusOptions: STATUS_OPTIONS, conflicts: null, form: {} });
    } catch (err) {
      next(err);
    }
  });

  router.post('/', async (req, res, next) => {
    try {
      const body = req.body;

      let organizationIdForConflictCheck = body.organizationId || null;
      if (!organizationIdForConflictCheck && body.organizationName) {
        const candidates = await req.api.get(`/api/organizations?q=${encodeURIComponent(body.organizationName)}`);
        if (candidates.length === 1) {
          organizationIdForConflictCheck = candidates[0].id;
        }
      }

      if (body.confirmConflict !== 'true' && body.oemId && organizationIdForConflictCheck) {
        const conflicts = await req.api.get(`/api/deal-registrations/conflicts?organizationId=${encodeURIComponent(organizationIdForConflictCheck)}&oemId=${encodeURIComponent(body.oemId)}`);
        if (conflicts.length > 0) {
          const oems = await req.api.get('/api/oems');
          return res.render('dealRegistrations/new', { title: 'New deal registration', user: req.session.user, oems, statusOptions: STATUS_OPTIONS, conflicts, form: body });
        }
      }

      const payload = {
        oemId: body.oemId || null,
        oemName: body.oemId ? null : body.oemName,
        distributor: body.distributor || null,
        registrationId: body.registrationId || null,
        organizationId: body.organizationId || null,
        organizationName: body.organizationId ? null : body.organizationName,
        sector: body.sector || null,
        submittedDate: body.submittedDate || null,
        status: body.status || null,
        approvalDate: body.approvalDate || null,
        expiryDate: body.expiryDate || null,
        protectedDiscountPercent: body.protectedDiscountPercent ? Number(body.protectedDiscountPercent) : null,
        specialPriceReference: body.specialPriceReference || null,
        notes: body.notes || null,
      };
      const created = await req.api.post('/api/deal-registrations', payload);
      setFlash(req, 'success', 'Deal registration created.');
      res.redirect(`/deal-registrations/${created.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const [registration, activity] = await Promise.all([
        req.api.get(`/api/deal-registrations/${req.params.id}`),
        req.api.get(`/api/deal-registrations/${req.params.id}/activity`),
      ]);
      res.render('dealRegistrations/detail', { title: registration.oem.name + ' - ' + registration.organization.name, user: req.session.user, registration, activity, statusOptions: STATUS_OPTIONS });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/update', async (req, res, next) => {
    try {
      const body = req.body;
      await req.api.put(`/api/deal-registrations/${req.params.id}`, {
        distributor: body.distributor || null,
        registrationId: body.registrationId || null,
        submittedDate: body.submittedDate || null,
        status: body.status || null,
        approvalDate: body.approvalDate || null,
        expiryDate: body.expiryDate || null,
        protectedDiscountPercent: body.protectedDiscountPercent ? Number(body.protectedDiscountPercent) : null,
        specialPriceReference: body.specialPriceReference || null,
        notes: body.notes || null,
      });
      setFlash(req, 'success', 'Saved.');
      res.redirect(`/deal-registrations/${req.params.id}`);
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/activity', async (req, res, next) => {
    try {
      await req.api.post(`/api/deal-registrations/${req.params.id}/activity`, { note: req.body.note });
      res.redirect(`/deal-registrations/${req.params.id}`);
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = buildDealRegistrationsRouter;
