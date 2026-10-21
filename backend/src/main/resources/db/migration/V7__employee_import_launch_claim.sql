ALTER TABLE employee_import_uploads
    ADD COLUMN launch_claimed BOOLEAN NOT NULL DEFAULT FALSE;
