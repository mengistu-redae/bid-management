'use strict';

const MULTIPLE_DIVISIONS_LABEL = 'Multiple divisions';
const UNASSIGNED_LABEL = 'Unassigned';

/**
 * Buckets rows by division name: 0 distinct names -> Unassigned, 1 -> that
 * division, 2+ -> Multiple divisions. divisionOrder controls the order real
 * divisions appear in before the two catch-all buckets.
 *
 * getDivisionNames(row) must return an array of (possibly duplicate/falsy)
 * division name strings for that row.
 *
 * By default only non-empty buckets are returned (dashboard preview cards).
 * Pass { includeEmpty: true } to always return every division bucket even
 * when empty (the dedicated /bids/by-division page, matching the Kanban
 * view's "always show every column" behavior).
 */
function groupByDivision(rows, divisionOrder, getDivisionNames, { includeEmpty = false } = {}) {
  const groups = divisionOrder.map((name) => ({ key: name, label: name, rows: [] }));
  const multiGroup = { key: 'multiple', label: MULTIPLE_DIVISIONS_LABEL, rows: [] };
  const unassignedGroup = { key: 'unassigned', label: UNASSIGNED_LABEL, rows: [] };

  for (const row of rows) {
    const names = [...new Set((getDivisionNames(row) || []).filter(Boolean))];
    if (names.length === 0) {
      unassignedGroup.rows.push(row);
    } else if (names.length === 1) {
      const group = groups.find((g) => g.key === names[0]);
      (group || unassignedGroup).rows.push(row);
    } else {
      multiGroup.rows.push(row);
    }
  }

  const all = [...groups, multiGroup, unassignedGroup];
  return includeEmpty ? all : all.filter((g) => g.rows.length > 0);
}

module.exports = { groupByDivision };
