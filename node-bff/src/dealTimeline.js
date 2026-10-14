'use strict';

/**
 * Turns one entity's stage_history rows (for a single stageField, e.g.
 * "status") into a sequence of segments for the Deal 360 horizontal
 * timeline: one per value the entity passed through, each sized
 * proportionally to its own duration (not a shared calendar axis across
 * entities - every row is its own 0-100% bar). Duration math here is
 * calendar days, not business days - a deliberate simplification for a
 * visual approximation; the precise business-day numbers already live in
 * the indicator badges shown elsewhere on the page.
 */
function buildSegments(stageHistory, targets, now) {
  const history = [...(stageHistory || [])].sort((a, b) => new Date(a.changedAt) - new Date(b.changedAt));
  if (history.length === 0) {
    return [];
  }
  const targetByStageValue = new Map();
  (targets || []).forEach((t) => {
    if (t.targetType === 'DURATION' && t.targetDays !== null && t.targetDays !== undefined && !targetByStageValue.has(t.stageValue)) {
      targetByStageValue.set(t.stageValue, Number(t.targetDays));
    }
  });

  const segments = [];
  for (let i = 0; i < history.length; i++) {
    const entry = history[i];
    const start = new Date(entry.changedAt);
    const end = i + 1 < history.length ? new Date(history[i + 1].changedAt) : now;
    const durationDays = Math.max(0, (end - start) / (1000 * 60 * 60 * 24));
    const value = entry.toValue;
    const targetDays = targetByStageValue.has(value) ? targetByStageValue.get(value) : null;
    const overTarget = targetDays !== null && durationDays > targetDays;
    segments.push({
      value, durationDays, targetDays, overTarget,
      ongoing: i + 1 === history.length,
      startedAt: entry.changedAt,
    });
  }

  const totalDays = segments.reduce((sum, s) => sum + s.durationDays, 0) || 1;
  segments.forEach((s) => { s.widthPercent = Math.max(2, Math.round((s.durationDays / totalDays) * 1000) / 10); });
  return segments;
}

module.exports = { buildSegments };
