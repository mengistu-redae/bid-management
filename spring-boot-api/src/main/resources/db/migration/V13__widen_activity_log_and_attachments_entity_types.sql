-- activity_log and attachments were created in V1 before CHECKLIST_ITEM, CONTRACT
-- and LOGISTICS existed in the EntityType enum, and their CHECK constraints were
-- never widened when every other polymorphic table's was (V8/V10/V12). This went
-- unnoticed until FollowUpService.addNote (shared by the Delays board's follow-up/
-- snooze form, extended to Contract/Logistics) tried to INSERT an activity_log row
-- for a Contract and hit the stale constraint - caught via live browser verification.
ALTER TABLE activity_log DROP CONSTRAINT activity_log_entity_type_check;
ALTER TABLE activity_log ADD CONSTRAINT activity_log_entity_type_check
    CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS'));

ALTER TABLE attachments DROP CONSTRAINT attachments_entity_type_check;
ALTER TABLE attachments ADD CONSTRAINT attachments_entity_type_check
    CHECK (entity_type IN ('OPPORTUNITY', 'DEAL_REGISTRATION', 'BID', 'LOT', 'CHECKLIST_ITEM', 'CONTRACT', 'LOGISTICS'));
