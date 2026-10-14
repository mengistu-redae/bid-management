-- Best-effort seed of 2026 Ethiopian public holidays (Gregorian dates), per
-- the brief's "seed it with the current year's holidays and let admins
-- edit it". Fixed-date holidays below are high-confidence; the ones
-- depending on the Ethiopian Orthodox Easter calculation or Islamic lunar
-- sighting (Good Friday, Fasika, Eid al-Fitr, Eid al-Adha, Mawlid) are
-- estimates and should be verified/corrected via the holiday_calendar admin
-- screen once it ships (Phase 2) - do not rely on them for real escalation
-- math until confirmed.
INSERT INTO holiday_calendar (holiday_date, name) VALUES
    ('2026-01-07', 'Genna (Ethiopian Christmas)'),
    ('2026-01-19', 'Timkat (Epiphany)'),
    ('2026-03-02', 'Victory of Adwa'),
    ('2026-04-10', 'Siklet (Good Friday) - estimated, verify'),
    ('2026-04-12', 'Fasika (Ethiopian Easter) - estimated, verify'),
    ('2026-05-01', 'International Labour Day'),
    ('2026-05-05', 'Patriots'' Victory Day'),
    ('2026-05-28', 'Downfall of the Derg'),
    ('2026-03-20', 'Eid al-Fitr - estimated, verify'),
    ('2026-05-27', 'Eid al-Adha (Arafat) - estimated, verify'),
    ('2026-08-25', 'Mawlid (Prophet''s Birthday) - estimated, verify'),
    ('2026-09-11', 'Enkutatash (Ethiopian New Year)'),
    ('2026-09-27', 'Meskel');
