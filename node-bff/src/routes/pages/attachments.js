'use strict';

const express = require('express');
const multer = require('multer');
const { setFlash } = require('../../flash');
const { ensureFreshToken } = require('../../apiClient');

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 25 * 1024 * 1024 } });

/**
 * Generic file-upload/download control for any tracked entity type - same
 * entity-agnostic shape as stageHistory.js/holds.js. Uploads go through
 * AttachmentController's real multipart endpoint (req.api is JSON-only, so
 * this re-derives the token and builds the multipart request itself, the
 * same way importPage.js's forwardMultipart already does for file imports).
 * Downloads are proxied rather than linked directly at the API, since the
 * browser never holds the bearer token the API requires.
 */
function buildAttachmentsRouter() {
  const router = express.Router();

  router.post('/', upload.single('file'), async (req, res, next) => {
    const returnTo = req.body.returnTo || '/dashboard';
    try {
      if (!req.file) {
        setFlash(req, 'error', 'Choose a file to upload.');
        return res.redirect(returnTo);
      }
      await forwardUpload(req, req.body.entityType, req.body.entityId, req.file);
      setFlash(req, 'success', 'File uploaded.');
      res.redirect(returnTo);
    } catch (err) {
      if (err.status === 409 || err.status === 400) {
        setFlash(req, 'error', err.message);
        return res.redirect(returnTo);
      }
      next(err);
    }
  });

  router.get('/:id/download', async (req, res, next) => {
    try {
      const accessToken = await ensureFreshToken(req, req.app.get('oidcGetClient'));
      const headers = {};
      if (accessToken) headers.authorization = `Bearer ${accessToken}`;
      const upstream = await fetch(`${process.env.API_BASE_URL}/api/attachments/${req.params.id}/download`, { headers });
      if (!upstream.ok) {
        return res.status(upstream.status).render('error', { message: 'Could not download this file.', user: req.session.user });
      }
      res.setHeader('Content-Type', upstream.headers.get('content-type') || 'application/octet-stream');
      res.setHeader('Content-Disposition', upstream.headers.get('content-disposition') || 'attachment');
      res.send(Buffer.from(await upstream.arrayBuffer()));
    } catch (err) {
      next(err);
    }
  });

  return router;
}

async function forwardUpload(req, entityType, entityId, file) {
  const accessToken = await ensureFreshToken(req, req.app.get('oidcGetClient'));
  const form = new FormData();
  form.append('entityType', entityType);
  form.append('entityId', entityId);
  form.append('file', new Blob([file.buffer]), file.originalname);

  const headers = { accept: 'application/json' };
  if (accessToken) {
    headers.authorization = `Bearer ${accessToken}`;
  }
  const response = await fetch(`${process.env.API_BASE_URL}/api/attachments`, { method: 'POST', headers, body: form });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error((data && data.message) || `Upload failed: ${response.status}`);
    error.status = response.status;
    error.data = data;
    throw error;
  }
  return data;
}

module.exports = buildAttachmentsRouter;
