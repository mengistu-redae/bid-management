'use strict';

const express = require('express');
const { generators } = require('openid-client');

/**
 * Decodes a JWT's payload without verifying its signature - safe here only
 * because the token's authenticity was already established by the OAuth
 * token exchange with Keycloak (client.callback() above). Needed because
 * tokenSet.claims() only exposes the ID token's claims, and Keycloak's
 * realm_access.roles claim lives on the access token instead.
 */
function decodeJwtPayload(token) {
  try {
    const payload = token.split('.')[1];
    return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
  } catch {
    return null;
  }
}

/**
 * Authorization Code + PKCE against Keycloak. The BFF is the only thing that
 * ever sees tokens - the browser only ever gets a session cookie.
 */
function buildAuthRouter(getClient) {
  const router = express.Router();

  router.get('/login', (req, res, next) => {
    try {
      const client = getClient();
      const state = generators.state();
      const nonce = generators.nonce();
      const codeVerifier = generators.codeVerifier();
      const codeChallenge = generators.codeChallenge(codeVerifier);

      req.session.oidc = { state, nonce, codeVerifier };

      const authorizationUrl = client.authorizationUrl({
        scope: 'openid profile email',
        state,
        nonce,
        code_challenge: codeChallenge,
        code_challenge_method: 'S256',
      });
      res.redirect(authorizationUrl);
    } catch (err) {
      next(err);
    }
  });

  router.get('/callback', async (req, res, next) => {
    try {
      const client = getClient();
      const params = client.callbackParams(req);
      const stashed = req.session.oidc;

      if (!stashed || params.state !== stashed.state) {
        return res.status(400).send('Invalid or expired login attempt - please try logging in again.');
      }

      const tokenSet = await client.callback(
        `${process.env.BFF_BASE_URL}/auth/callback`,
        params,
        { state: stashed.state, nonce: stashed.nonce, code_verifier: stashed.codeVerifier }
      );

      delete req.session.oidc;
      req.session.tokenSet = tokenSet;

      const userClaims = tokenSet.claims();
      const accessTokenClaims = decodeJwtPayload(tokenSet.access_token);
      if (accessTokenClaims?.realm_access) {
        userClaims.realm_access = accessTokenClaims.realm_access;
      }
      req.session.user = userClaims;

      res.redirect('/');
    } catch (err) {
      next(err);
    }
  });

  router.post('/logout', (req, res, next) => {
    try {
      const client = getClient();
      const idToken = req.session.tokenSet && req.session.tokenSet.id_token;
      req.session.destroy((err) => {
        if (err) {
          return next(err);
        }
        res.redirect(client.endSessionUrl({
          id_token_hint: idToken,
          post_logout_redirect_uri: process.env.BFF_BASE_URL,
        }));
      });
    } catch (err) {
      next(err);
    }
  });

  return router;
}

module.exports = { buildAuthRouter, decodeJwtPayload };
