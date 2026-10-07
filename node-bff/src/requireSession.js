'use strict';

/** Mounted after /auth and /health - everything else needs a logged-in session. */
function requireSession(req, res, next) {
  if (!req.session.tokenSet) {
    return res.redirect('/auth/login');
  }
  next();
}

module.exports = { requireSession };
