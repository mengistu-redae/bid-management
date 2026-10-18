'use strict';

const TIMEZONE = 'Africa/Addis_Ababa';

/** DD-MMM-YYYY, per the brief's display convention. Accepts an ISO instant/date string or null. */
function formatDate(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  const day = new Intl.DateTimeFormat('en-GB', { day: '2-digit', timeZone: TIMEZONE }).format(date);
  const month = new Intl.DateTimeFormat('en-GB', { month: 'short', timeZone: TIMEZONE }).format(date);
  const year = new Intl.DateTimeFormat('en-GB', { year: 'numeric', timeZone: TIMEZONE }).format(date);
  return `${day}-${month}-${year}`;
}

/** YYYY-MM-DD in Africa/Addis_Ababa, for prefilling an &lt;input type="date"&gt; from a stored Instant - formatDate()'s DD-MMM-YYYY display format isn't what the date input control accepts as a value. */
function formatDateInput(value) {
  if (!value) return '';
  const date = new Date(value);
  return new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: TIMEZONE }).format(date);
}

/** HH:MM (24h) in Africa/Addis_Ababa, for prefilling an &lt;input type="time"&gt; from a stored Instant. */
function formatTimeInput(value) {
  if (!value) return '';
  const date = new Date(value);
  return new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: TIMEZONE, hour12: false }).format(date);
}

function formatDateTime(value) {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  const time = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: TIMEZONE, hour12: false }).format(date);
  return `${formatDate(value)} ${time}`;
}

/** Thousand separators, never mixing currencies - the brief is explicit that ETB and USD must never be summed/blended. */
function formatMoney(amount, currency) {
  if (amount === null || amount === undefined) {
    return '-';
  }
  const formatted = new Intl.NumberFormat('en-US').format(amount);
  return currency ? `${currency} ${formatted}` : formatted;
}

function statusBadgeClass(status) {
  if (!status) return '';
  const s = status.toLowerCase();
  if (['won', 'completed', 'delivered', 'approved'].includes(s)) return 'won';
  if (['lost', 'dropped', 'cancelled', 'terminated', 'rejected'].includes(s)) return 'lost';
  if (s === 'under_review') return 'review';
  if (['preparing', 'submitted', 'opened', 'under_evaluation', 'signed', 'in_delivery', 'dispatched', 'in_transit', 'customs', 'cleared'].includes(s)) return 'active';
  if (['identified', 'awarded', 'planning', 'draft'].includes(s)) return 'new';
  return '';
}

/** Width (0-100) for a .bar-fill, scaled against the largest value in its own chart - never against a fixed/arbitrary ceiling. */
function barWidth(value, max) {
  if (!max || max <= 0 || !value || value <= 0) return 0;
  return Math.min(100, Math.round((value / max) * 100));
}

function indicatorBadgeClass(level) {
  if (!level) return '';
  const l = level.toLowerCase();
  if (['green', 'amber', 'red', 'critical'].includes(l)) return `indicator-${l}`;
  return '';
}

/** "2.3d / 2d target" style label for an IndicatorDto - null-safe, since NO_TARGET/UNKNOWN carry no numbers. */
function indicatorLabel(indicator) {
  if (!indicator) return null;
  if (indicator.level === 'NO_TARGET') return null;
  if (indicator.level === 'UNKNOWN') return 'unknown';
  const unit = indicator.usesBusinessDays ? 'bd' : 'd';
  const used = indicator.daysUsed === null || indicator.daysUsed === undefined ? '?' : indicator.daysUsed.toFixed(1);
  const target = indicator.targetDays === null || indicator.targetDays === undefined ? '?' : indicator.targetDays.toFixed(1);
  return `${used}${unit} / ${target}${unit} target`;
}

/** OPPORTUNITY tracks its "status"-equivalent indicator field as `stage`, not `status` like everything else - see IndicatorService.compute's call sites. */
const STAGE_FIELD_BY_ENTITY_TYPE = { OPPORTUNITY: 'stage' };

/**
 * URL for the generic /stage-history page behind every indicator badge -
 * centralizes the entityType -> stageField mapping (so each view doesn't
 * have to know it) and the query-string encoding (so nothing double-
 * encodes or forgets to encode a title/subtitle that happens to contain
 * special characters). Returns null when there's no entity to link to
 * (e.g. a Bid-level rollup indicator that isn't really one entity's own
 * history - see bidRow.ejs/kanban.ejs, which link to the bid page instead).
 */
function stageHistoryHref(entityType, entityId, options) {
  if (!entityType || !entityId) return null;
  const opts = options || {};
  const params = new URLSearchParams({
    entityType,
    entityId,
    stageField: opts.stageField || STAGE_FIELD_BY_ENTITY_TYPE[entityType] || 'status',
  });
  if (opts.title) params.set('title', opts.title);
  if (opts.subtitle) params.set('subtitle', opts.subtitle);
  if (opts.backHref) params.set('backHref', opts.backHref);
  if (opts.backLabel) params.set('backLabel', opts.backLabel);
  return `/stage-history?${params.toString()}`;
}

module.exports = {
  formatDate, formatDateTime, formatDateInput, formatTimeInput, formatMoney, statusBadgeClass, barWidth, indicatorBadgeClass, indicatorLabel,
  stageHistoryHref,
};
