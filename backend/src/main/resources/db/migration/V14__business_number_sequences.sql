-- Random six-digit business numbers can collide even with different phones.
-- Preserve historical numbers; append monotonically from the historical maximum.
CREATE SEQUENCE account_business_number_seq AS bigint;
SELECT setval('account_business_number_seq',
    COALESCE((SELECT max(substring(account_no from '[0-9]+$')::bigint)
              FROM accounts WHERE account_no ~ '^NUR-(STU|PAR|TEA|ADM|AGT)-[0-9]{1,12}$'),0)+1,false);
CREATE SEQUENCE student_business_number_seq AS bigint;
SELECT setval('student_business_number_seq',
    COALESCE((SELECT max(substring(student_no from '[0-9]+$')::bigint)
              FROM student_profiles WHERE student_no ~ '^NUR-STU-[0-9]{1,12}$'),0)+1,false);
