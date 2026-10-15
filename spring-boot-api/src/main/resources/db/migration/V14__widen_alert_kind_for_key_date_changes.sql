-- alert_state's alert_kind CHECK constraint only listed the 3 values that
-- existed when V12 created the table. KeyDateChangeNotificationService adds
-- a 4th AlertKind (KEY_DATE_CHANGED) to track which audit_log-recorded key
-- date changes have already been notified - widen the constraint before
-- that service ever tries to write one, learning from the identical
-- stale-CHECK-constraint bug fixed in V13 for activity_log/attachments.
ALTER TABLE alert_state DROP CONSTRAINT alert_state_alert_kind_check;
ALTER TABLE alert_state ADD CONSTRAINT alert_state_alert_kind_check
    CHECK (alert_kind IN ('INDICATOR', 'STALE', 'FOLLOWUP_DUE', 'KEY_DATE_CHANGED'));
