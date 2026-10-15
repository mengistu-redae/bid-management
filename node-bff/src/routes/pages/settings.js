'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const ALL_DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];
const ENTITY_TYPE_OPTIONS = ['BID', 'LOT', 'OPPORTUNITY', 'DEAL_REGISTRATION', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS'];
const TARGET_TYPE_OPTIONS = ['DURATION', 'DEADLINE_RELATIVE'];
const SECTOR_OPTIONS = ['', 'BANK', 'GOVERNMENT', 'UTILITY', 'ENTERPRISE', 'OTHER'];
const SHIPMENT_MODE_OPTIONS = ['', 'AIR', 'SEA', 'LAND'];

function buildSettingsRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const [indicatorConfig, workingWeek, holidays, delayReasonCodes, divisions] = await Promise.all([
        req.api.get('/api/indicator-config'),
        req.api.get('/api/working-week-config'),
        req.api.get('/api/holidays'),
        req.api.get('/api/delay-reason-codes'),
        req.api.get('/api/divisions'),
      ]);
      res.render('settings/index', {
        title: 'Settings', user: req.session.user,
        indicatorConfig, workingWeek, holidays, delayReasonCodes, divisions, allDays: ALL_DAYS,
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/indicator-config', async (req, res, next) => {
    try {
      const body = req.body;
      await req.api.put('/api/indicator-config', {
        amberThresholdPercent: Number(body.amberThresholdPercent),
        redThresholdPercent: Number(body.redThresholdPercent),
        criticalThresholdPercent: Number(body.criticalThresholdPercent),
        defaultStaleAfterDays: Number(body.defaultStaleAfterDays),
      });
      setFlash(req, 'success', 'Indicator thresholds saved.');
      res.redirect('/settings');
    } catch (err) {
      if (err.status === 403 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect('/settings');
      }
      next(err);
    }
  });

  router.post('/working-week', async (req, res, next) => {
    try {
      await req.api.put('/api/working-week-config', { workingDays: toArray(req.body.workingDays) });
      setFlash(req, 'success', 'Working week saved.');
      res.redirect('/settings');
    } catch (err) {
      if (err.status === 403 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect('/settings');
      }
      next(err);
    }
  });

  router.post('/holidays', async (req, res, next) => {
    try {
      await req.api.post('/api/holidays', { holidayDate: req.body.holidayDate, name: req.body.name });
      setFlash(req, 'success', 'Holiday added.');
      res.redirect('/settings');
    } catch (err) {
      if (err.status === 403 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect('/settings');
      }
      next(err);
    }
  });

  router.post('/holidays/:id/update', async (req, res, next) => {
    try {
      await req.api.put(`/api/holidays/${req.params.id}`, { holidayDate: req.body.holidayDate, name: req.body.name });
      setFlash(req, 'success', 'Holiday updated.');
      res.redirect('/settings');
    } catch (err) {
      if (err.status === 403 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect('/settings');
      }
      next(err);
    }
  });

  router.post('/holidays/:id/delete', async (req, res, next) => {
    try {
      await req.api.delete(`/api/holidays/${req.params.id}`);
      setFlash(req, 'success', 'Holiday removed.');
      res.redirect('/settings');
    } catch (err) {
      if (err.status === 403) {
        setFlash(req, 'error', err.message);
        return res.redirect('/settings');
      }
      next(err);
    }
  });

  router.post('/delay-reason-codes', async (req, res, next) => {
    try {
      await req.api.post('/api/delay-reason-codes', {
        code: req.body.code, label: req.body.label, sortOrder: Number(req.body.sortOrder) || 0,
      });
      setFlash(req, 'success', 'Delay reason code added.');
      res.redirect('/settings');
    } catch (err) {
      if (err.status === 403 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect('/settings');
      }
      next(err);
    }
  });

  router.post('/delay-reason-codes/:id/update', async (req, res, next) => {
    try {
      await req.api.put(`/api/delay-reason-codes/${req.params.id}`, {
        label: req.body.label, active: req.body.active === 'on', sortOrder: Number(req.body.sortOrder) || 0,
      });
      setFlash(req, 'success', 'Delay reason code saved.');
      res.redirect('/settings');
    } catch (err) {
      if (err.status === 403 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect('/settings');
      }
      next(err);
    }
  });

  router.get('/stage-targets', async (req, res, next) => {
    try {
      const stageTargets = await req.api.get('/api/stage-targets');
      res.render('settings/stageTargets', { title: 'Stage targets', user: req.session.user, stageTargets });
    } catch (err) {
      next(err);
    }
  });

  router.get('/stage-targets/new', async (req, res, next) => {
    try {
      const divisions = await req.api.get('/api/divisions');
      res.render('settings/stageTargetForm', {
        title: 'New stage target', user: req.session.user, divisions, target: null,
        entityTypeOptions: ENTITY_TYPE_OPTIONS, targetTypeOptions: TARGET_TYPE_OPTIONS,
        sectorOptions: SECTOR_OPTIONS, shipmentModeOptions: SHIPMENT_MODE_OPTIONS,
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/stage-targets', async (req, res, next) => {
    try {
      await req.api.post('/api/stage-targets', stageTargetPayload(req.body));
      setFlash(req, 'success', 'Stage target added.');
      res.redirect('/settings/stage-targets');
    } catch (err) {
      if (err.status === 403 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect('/settings/stage-targets/new');
      }
      next(err);
    }
  });

  router.get('/stage-targets/:id/edit', async (req, res, next) => {
    try {
      const [stageTargets, divisions] = await Promise.all([
        req.api.get('/api/stage-targets'),
        req.api.get('/api/divisions'),
      ]);
      const target = stageTargets.find((t) => t.id === req.params.id);
      if (!target) {
        return res.status(404).render('error', { message: 'Stage target not found', user: req.session.user });
      }
      res.render('settings/stageTargetForm', {
        title: 'Edit stage target', user: req.session.user, divisions, target,
        entityTypeOptions: ENTITY_TYPE_OPTIONS, targetTypeOptions: TARGET_TYPE_OPTIONS,
        sectorOptions: SECTOR_OPTIONS, shipmentModeOptions: SHIPMENT_MODE_OPTIONS,
      });
    } catch (err) {
      next(err);
    }
  });

  router.post('/stage-targets/:id/update', async (req, res, next) => {
    try {
      await req.api.put(`/api/stage-targets/${req.params.id}`, stageTargetPayload(req.body));
      setFlash(req, 'success', 'Stage target saved.');
      res.redirect('/settings/stage-targets');
    } catch (err) {
      if (err.status === 403 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(`/settings/stage-targets/${req.params.id}/edit`);
      }
      next(err);
    }
  });

  return router;
}

function stageTargetPayload(body) {
  return {
    entityType: body.entityType,
    stageValue: body.stageValue,
    targetType: body.targetType,
    targetDays: body.targetDays ? Number(body.targetDays) : null,
    usesBusinessDays: body.usesBusinessDays === 'on',
    deadlineField: body.deadlineField || null,
    deadlineOffsetDays: body.deadlineOffsetDays ? Number(body.deadlineOffsetDays) : null,
    divisionId: body.divisionId || null,
    sector: body.sector || null,
    oemName: body.oemName || null,
    shipmentMode: body.shipmentMode || null,
    staleAfterDays: body.staleAfterDays ? Number(body.staleAfterDays) : null,
    active: body.active === 'on',
    notes: body.notes || null,
  };
}

/** A single checked checkbox posts as a bare string, not an array - express.urlencoded only gives an array for 2+. */
function toArray(value) {
  if (value === undefined) return [];
  return Array.isArray(value) ? value : [value];
}

module.exports = buildSettingsRouter;
