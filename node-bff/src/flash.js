'use strict';

/**
 * One-shot success/error banner across a redirect - set it right before
 * calling res.redirect(), and the next page render picks it up and clears
 * it (see index.js's flash middleware). Session-backed rather than a query
 * param so it never shows up in the URL or survives a refresh.
 */
function setFlash(req, type, message) {
  req.session.flash = { type, message };
}

module.exports = { setFlash };
