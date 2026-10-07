-- Records every reminder/digest actually sent, keyed so the daily scheduled
-- job (ReminderSchedulerService) can run more than once a day (restarts,
-- manual re-runs) without re-sending the same email. reminder_date is the
-- Africa/Addis_Ababa calendar date the reminder logically belongs to - not
-- derived from sent_at, so it stays correct even if sent_at drifts past
-- midnight mid-run.
CREATE TABLE notification_log (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reminder_type    VARCHAR(40) NOT NULL,
    entity_type      VARCHAR(20) NOT NULL,
    entity_id        UUID NOT NULL,
    recipient_email  VARCHAR(255) NOT NULL,
    reminder_date    DATE NOT NULL,
    sent_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reminder_type, entity_type, entity_id, recipient_email, reminder_date)
);
