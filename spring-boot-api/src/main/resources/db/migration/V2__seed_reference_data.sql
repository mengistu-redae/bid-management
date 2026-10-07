INSERT INTO divisions (code, name) VALUES
    ('SERVER_STORAGE', 'Server & Storage'),
    ('NETWORK', 'Network'),
    ('SECURITY', 'Security'),
    ('DATACENTER_FACILITY', 'Datacenter Facility');

INSERT INTO checklist_item_templates (title, sort_order) VALUES
    ('Tender document purchased', 10),
    ('Clarification questions sent', 20),
    ('MAF from OEM', 30),
    ('Vendor/distributor quotation', 40),
    ('Price protection confirmed', 50),
    ('Lead time confirmed', 60),
    ('Implementation partner quote', 70),
    ('Training quote', 80),
    ('Scope of work / HLD / LLD', 90),
    ('Technical compliance sheet', 100),
    ('Bid bond arranged', 110),
    ('Financial offer approved', 120),
    ('Final submission', 130);

-- Director + division managers. keycloak_user_id is left NULL here - the
-- first login with a matching email backfills it (see CurrentUserService)
-- rather than creating a second row for the same person.
INSERT INTO app_users (full_name, email, role) VALUES
    ('Mengistu Redae', 'mengistu.redae@motiengineering.com', 'DIRECTOR'),
    ('Ermiyas Mesfin', 'ermiyas.mesfin@motiengineering.com', 'DIVISION_MANAGER'),
    ('Nardos Kibru', 'nardos.kibru@motiengineering.com', 'DIVISION_MANAGER'),
    ('Eyerusalem Tariku', 'eyerusalem.tariku@motiengineering.com', 'DIVISION_MANAGER');

INSERT INTO user_divisions (user_id, division_id, is_manager)
SELECT u.id, d.id, TRUE
FROM app_users u
JOIN divisions d ON (
    (u.email = 'ermiyas.mesfin@motiengineering.com' AND d.code = 'SERVER_STORAGE')
    OR (u.email = 'nardos.kibru@motiengineering.com' AND d.code IN ('NETWORK', 'SECURITY'))
    OR (u.email = 'eyerusalem.tariku@motiengineering.com' AND d.code = 'DATACENTER_FACILITY')
);

-- Account officers and scouts seen in the sample spreadsheets, with no
-- division assigned yet (the Director assigns these later in the admin
-- screens) and no login - a real login can be linked later by matching
-- email once one is created in Keycloak for them.
INSERT INTO app_users (full_name, email, role) VALUES
    ('Selam', NULL, 'ACCOUNT_OFFICER'),
    ('Tezana', NULL, 'ACCOUNT_OFFICER'),
    ('Abenezer', NULL, 'ACCOUNT_OFFICER'),
    ('Zufan', NULL, 'ACCOUNT_OFFICER'),
    ('Kaleab', NULL, 'ACCOUNT_OFFICER'),
    ('Endris', NULL, 'ACCOUNT_OFFICER'),
    ('Berhanu', NULL, 'SCOUT'),
    ('Betelhem', NULL, 'SCOUT'),
    ('Eyerus', NULL, 'SCOUT'),
    ('Betty', NULL, 'SCOUT');
