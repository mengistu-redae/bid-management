'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const { buildSegments } = require('../src/dealTimeline');

test('buildSegments returns one proportional segment per stage, flagging over-target durations', () => {
  const history = [
    { fromValue: null, toValue: 'IDENTIFIED', changedAt: '2026-01-01T00:00:00Z' },
    { fromValue: 'IDENTIFIED', toValue: 'UNDER_REVIEW', changedAt: '2026-01-02T00:00:00Z' }, // IDENTIFIED lasted 1 day
    { fromValue: 'UNDER_REVIEW', toValue: 'PREPARING', changedAt: '2026-01-07T00:00:00Z' }, // UNDER_REVIEW lasted 5 days
  ];
  const targets = [
    { entityType: 'LOT', stageValue: 'IDENTIFIED', targetType: 'DURATION', targetDays: 2 },
    { entityType: 'LOT', stageValue: 'UNDER_REVIEW', targetType: 'DURATION', targetDays: 2 },
  ];
  const now = new Date('2026-01-10T00:00:00Z');

  const segments = buildSegments(history, targets, now);

  assert.equal(segments.length, 3);
  assert.equal(segments[0].value, 'IDENTIFIED');
  assert.equal(segments[0].durationDays, 1);
  assert.equal(segments[0].overTarget, false);
  assert.equal(segments[1].value, 'UNDER_REVIEW');
  assert.equal(segments[1].durationDays, 5);
  assert.equal(segments[1].overTarget, true);
  assert.equal(segments[2].value, 'PREPARING');
  assert.equal(segments[2].ongoing, true);
  assert.equal(segments[2].targetDays, null); // no DURATION target seeded for PREPARING in this fixture

  const totalWidth = segments.reduce((sum, s) => sum + s.widthPercent, 0);
  assert.ok(Math.abs(totalWidth - 100) < 1, `widths should sum to ~100%, got ${totalWidth}`);
});

test('buildSegments returns an empty array when there is no history yet', () => {
  assert.deepEqual(buildSegments([], [], new Date()), []);
  assert.deepEqual(buildSegments(undefined, undefined, new Date()), []);
});

test('buildSegments sorts out-of-order history by changedAt before building segments', () => {
  const history = [
    { toValue: 'B', changedAt: '2026-01-05T00:00:00Z' },
    { toValue: 'A', changedAt: '2026-01-01T00:00:00Z' },
  ];
  const now = new Date('2026-01-06T00:00:00Z');

  const segments = buildSegments(history, [], now);

  assert.equal(segments[0].value, 'A');
  assert.equal(segments[1].value, 'B');
});
