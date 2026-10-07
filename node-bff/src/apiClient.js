'use strict';

const { TokenSet } = require('openid-client');

// sessionID -> in-flight refresh Promise, so concurrent requests sharing a
// session coalesce onto one Keycloak refresh call instead of racing it -
// if refresh-token rotation is on, a losing concurrent racer would otherwise
// get back `invalid_grant` for a token that was, in fact, just refreshed a
// moment earlier by a sibling request.
const refreshInFlight = new Map();

async function ensureFreshToken(req, getClient) {
  if (!req.session.tokenSet) {
    return null;
  }
  // req.session.tokenSet round-trips through Redis as plain JSON between
  // requests, so it's a plain object here, not the TokenSet instance
  // auth.js's /callback originally stored - rewrap it before calling .expired().
  let tokenSet = new TokenSet(req.session.tokenSet);
  if (!tokenSet.expired() || !tokenSet.refresh_token) {
    return tokenSet.access_token;
  }

  const sessionId = req.sessionID;
  let refreshPromise = refreshInFlight.get(sessionId);
  if (!refreshPromise) {
    refreshPromise = getClient().refresh(tokenSet.refresh_token);
    refreshPromise.then(
      () => refreshInFlight.delete(sessionId),
      () => refreshInFlight.delete(sessionId)
    );
    refreshInFlight.set(sessionId, refreshPromise);
  }
  tokenSet = await refreshPromise;
  req.session.tokenSet = tokenSet;
  return tokenSet.access_token;
}

/**
 * Attaches `req.api`, a small server-side client for spring-boot-api calls
 * made while rendering a page (as opposed to routes/api.js's raw passthrough
 * for the few bits of client-side JS that need JSON directly). Every call
 * carries the current session's bearer token, refreshed first if expired.
 */
function attachApiClient(getClient) {
  return async (req, res, next) => {
    try {
      const accessToken = await ensureFreshToken(req, getClient);
      req.api = {
        get: (path) => call(req, 'GET', path, accessToken),
        post: (path, body) => call(req, 'POST', path, accessToken, body),
        put: (path, body) => call(req, 'PUT', path, accessToken, body),
      };
      next();
    } catch (err) {
      if (err.name === 'OPError' && err.error === 'invalid_grant') {
        return req.session.destroy(() => res.redirect('/auth/login'));
      }
      next(err);
    }
  };
}

async function call(req, method, path, accessToken, body) {
  const headers = { accept: 'application/json' };
  if (accessToken) {
    headers.authorization = `Bearer ${accessToken}`;
  }
  let requestBody;
  if (body !== undefined) {
    headers['content-type'] = 'application/json';
    requestBody = JSON.stringify(body);
  }
  const response = await fetch(`${process.env.API_BASE_URL}${path}`, { method, headers, body: requestBody });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(`API ${method} ${path} failed: ${response.status}`);
    error.status = response.status;
    error.data = data;
    throw error;
  }
  return data;
}

module.exports = { attachApiClient, ensureFreshToken };
