'use strict';

const express = require('express');
const multer = require('multer');
const { ensureFreshToken } = require('../../apiClient');

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 25 * 1024 * 1024 } });

function buildImportRouter() {
  const router = express.Router();

  router.get('/', (req, res) => {
    res.render('import/index', { title: 'Import', user: req.session.user, preview: null, kind: null, divisions: [] });
  });

  router.post('/preview', upload.single('file'), async (req, res, next) => {
    try {
      const kind = req.body.kind; // 'upcoming' | 'tracker'
      const [preview, divisions] = await Promise.all([
        forwardMultipart(req, `/api/import/${kind}/preview`, req.file),
        req.api.get('/api/divisions'),
      ]);
      res.render('import/index', { title: 'Import', user: req.session.user, preview, kind, divisions });
    } catch (err) {
      next(err);
    }
  });

  router.post('/confirm', async (req, res, next) => {
    try {
      const kind = req.body.kind;
      const rowEdits = req.body.editsJson ? JSON.parse(req.body.editsJson) : [];
      const body = kind === 'upcoming'
        ? { token: req.body.token, edits: rowEdits.map(toUpcomingEdit) }
        : { token: req.body.token, editsBySheet: groupBySheet(rowEdits) };
      const summary = await req.api.post(`/api/import/${kind}/confirm`, body);
      res.render('import/confirmed', { title: 'Import complete', user: req.session.user, summary, kind });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

/** multer gives us the raw buffer; re-wrap it as a Blob so Node's global FormData/fetch can send a real multipart request. */
async function forwardMultipart(req, path, file) {
  // req.api was already built by attachApiClient's middleware with a plain
  // JSON helper - this call needs an actual multipart body instead, so it
  // re-derives the access token the same way rather than reusing req.api.
  const accessToken = await ensureFreshToken(req, req.app.get('oidcGetClient'));
  const form = new FormData();
  form.append('file', new Blob([file.buffer]), file.originalname);

  const headers = { accept: 'application/json' };
  if (accessToken) {
    headers.authorization = `Bearer ${accessToken}`;
  }
  const response = await fetch(`${process.env.API_BASE_URL}${path}`, { method: 'POST', headers, body: form });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(`Import preview failed: ${response.status}`);
    error.status = response.status;
    error.data = data;
    throw error;
  }
  return data;
}

/** The client sends {rowIndex, sheet, organizationRaw, cleanTitle, divisionCode, skip} per touched row - see import/index.ejs's inline script. */
function toUpcomingEdit(row) {
  return {
    rowIndex: row.rowIndex,
    organizationRaw: row.organizationRaw || null,
    cleanTitle: row.cleanTitle || null,
    divisionCode: row.divisionCode || null,
    skip: !!row.skip,
  };
}

function groupBySheet(rowEdits) {
  const bySheet = {};
  for (const row of rowEdits) {
    if (!bySheet[row.sheet]) {
      bySheet[row.sheet] = [];
    }
    bySheet[row.sheet].push(toUpcomingEdit(row));
  }
  return bySheet;
}

module.exports = buildImportRouter;
