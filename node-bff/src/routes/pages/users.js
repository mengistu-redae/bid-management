'use strict';

const express = require('express');
const { setFlash } = require('../../flash');

const ROLE_OPTIONS = ['DIRECTOR', 'DIVISION_MANAGER', 'ACCOUNT_OFFICER', 'SCOUT'];

function buildUsersRouter() {
  const router = express.Router();

  router.get('/', async (req, res, next) => {
    try {
      const users = await req.api.get('/api/users/admin');
      res.render('users/list', { title: 'Users', user: req.session.user, users, justGranted: null });
    } catch (err) {
      next(err);
    }
  });

  router.get('/new', async (req, res, next) => {
    try {
      const divisions = await req.api.get('/api/divisions');
      res.render('users/new', { title: 'Add person', user: req.session.user, divisions, roleOptions: ROLE_OPTIONS, form: {} });
    } catch (err) {
      next(err);
    }
  });

  router.post('/', async (req, res, next) => {
    try {
      const body = req.body;
      const created = await req.api.post('/api/users/admin', {
        fullName: body.fullName,
        email: body.email,
        role: body.role,
        divisionIds: toArray(body.divisionIds),
      });
      setFlash(req, 'success', `Added ${created.fullName}.`);
      res.redirect('/users');
    } catch (err) {
      next(err);
    }
  });

  router.get('/:id/edit', async (req, res, next) => {
    try {
      const [users, divisions] = await Promise.all([
        req.api.get('/api/users/admin'),
        req.api.get('/api/divisions'),
      ]);
      const target = users.find((u) => u.id === req.params.id);
      if (!target) {
        return res.status(404).render('error', { message: 'User not found', user: req.session.user });
      }
      res.render('users/edit', { title: 'Edit ' + target.fullName, user: req.session.user, target, divisions, roleOptions: ROLE_OPTIONS });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/update', async (req, res, next) => {
    try {
      const body = req.body;
      const updated = await req.api.put(`/api/users/admin/${req.params.id}`, {
        fullName: body.fullName,
        email: body.email || null,
        role: body.role,
        divisionIds: toArray(body.divisionIds),
        telegramChatId: body.telegramChatId || null,
      });
      setFlash(req, 'success', `Saved ${updated.fullName}.`);
      res.redirect('/users');
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/grant-login', async (req, res, next) => {
    try {
      const result = await req.api.post(`/api/users/admin/${req.params.id}/grant-login`);
      const users = await req.api.get('/api/users/admin');
      res.render('users/list', { title: 'Users', user: req.session.user, users, justGranted: result });
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/deactivate', async (req, res, next) => {
    try {
      await req.api.post(`/api/users/admin/${req.params.id}/deactivate`);
      setFlash(req, 'success', 'Deactivated.');
      res.redirect('/users');
    } catch (err) {
      next(err);
    }
  });

  router.post('/:id/reactivate', async (req, res, next) => {
    try {
      await req.api.post(`/api/users/admin/${req.params.id}/reactivate`);
      setFlash(req, 'success', 'Reactivated.');
      res.redirect('/users');
    } catch (err) {
      next(err);
    }
  });

  return router;
}

/** A single checked checkbox posts as a bare string, not an array - express.urlencoded only gives an array for 2+. */
function toArray(value) {
  if (value === undefined) return [];
  return Array.isArray(value) ? value : [value];
}

module.exports = buildUsersRouter;
