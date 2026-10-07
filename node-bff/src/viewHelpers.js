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
  if (s === 'won') return 'won';
  if (['lost', 'dropped', 'cancelled'].includes(s)) return s;
  return '';
}

module.exports = { formatDate, formatDateTime, formatMoney, statusBadgeClass };
