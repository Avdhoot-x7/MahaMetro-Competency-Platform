-- Step 23: Add age and sector to employee master
ALTER TABLE employee ADD COLUMN age INT NULL;
ALTER TABLE employee ADD COLUMN sector VARCHAR(100) NOT NULL DEFAULT '';
