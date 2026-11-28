-- =====================================================================
-- SkillSwap demo data (permanent): admin, demo accounts, sample offers.
-- Run ONCE in pgAdmin Query Tool on the skillswap database, after the
-- schema, the fixes migration and the tests. Safe to re-run (no duplicates).
--
-- Demo passwords (BCrypt-hashed below, valid for Spring Security):
--   admin@skillswap.demo        Admin@12345
--   volunteer1/2@skillswap.demo Volunteer@12345
--   student1/2@skillswap.demo   Student@12345
-- =====================================================================
BEGIN;

-- 1. Accounts (verified, so they can log in without the e-mail code) ----
INSERT INTO users (role_id, department_id, full_name, university_email, password_hash, year_of_study, is_verified)
SELECT r.role_id,
       (SELECT department_id FROM department ORDER BY department_id OFFSET v.dept_pos LIMIT 1),
       v.full_name, v.email, v.pw, v.yr, TRUE
FROM (VALUES
  ('ADMIN',   0, 'Demo Admin',       'admin@skillswap.demo',      '$2a$10$Pv7qrmlexf3PJgIueS0YK.epw6eG0Py4JsqNMWNUQ6EQxznAl38Ai', 3),
  ('STUDENT', 1, 'Demo Volunteer 1', 'volunteer1@skillswap.demo', '$2a$10$U3RiWitii3ypzZB/PkdldO7AJPk.NkcQnwjyQZ9q6M0WtPxozDP9W', 4),
  ('STUDENT', 2, 'Demo Volunteer 2', 'volunteer2@skillswap.demo', '$2a$10$U3RiWitii3ypzZB/PkdldO7AJPk.NkcQnwjyQZ9q6M0WtPxozDP9W', 3),
  ('STUDENT', 3, 'Demo Student 1',   'student1@skillswap.demo',   '$2a$10$N5yhW2ot.j5O8LkU03eziu.u3QGRG2yagT2RcqG1btieu5z3/NPpa', 1),
  ('STUDENT', 4, 'Demo Student 2',   'student2@skillswap.demo',   '$2a$10$N5yhW2ot.j5O8LkU03eziu.u3QGRG2yagT2RcqG1btieu5z3/NPpa', 2)
) AS v(role_name, dept_pos, full_name, email, pw, yr)
JOIN role r ON r.role_name = v.role_name
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.university_email = v.email);

-- 2. Sample service offers (spread over the 4 categories) ---------------
INSERT INTO service_offer (volunteer_id, category_id, title, description, availability, help_method)
SELECT (SELECT user_id FROM users WHERE university_email = v.vol_email),
       (SELECT category_id FROM category ORDER BY category_id OFFSET v.cat_pos LIMIT 1),
       v.title, v.descr, v.avail, v.method
FROM (VALUES
  ('volunteer1@skillswap.demo', 0, 'Help with Data Structures problems',
     'I can explain linked lists, stacks, queues and trees with worked examples.', 'Weekdays after 4 PM', 'Online'),
  ('volunteer1@skillswap.demo', 1, 'Digital logic and Karnaugh maps',
     'Step-by-step help with K-maps, flip-flops and sequential circuit design.', 'Weekends', 'In person'),
  ('volunteer1@skillswap.demo', 2, 'Technical writing feedback',
     'I will review your report structure, headings and references.', 'Sunday to Tuesday evenings', 'Online'),
  ('volunteer2@skillswap.demo', 3, 'Study planning for exam week',
     'Help building a realistic revision timetable and staying on track.', 'Weekdays 6-8 PM', 'Online'),
  ('volunteer2@skillswap.demo', 0, 'Intro to Python for beginners',
     'Variables, loops, functions and debugging basics, with small exercises.', 'Weekends', 'In person'),
  ('volunteer2@skillswap.demo', 1, 'Electronics basics: diodes and transistors',
     'Explaining circuit analysis and how to read datasheets.', 'Thursday afternoons', 'Online')
) AS v(vol_email, cat_pos, title, descr, avail, method)
WHERE NOT EXISTS (
  SELECT 1 FROM service_offer so
  WHERE so.title = v.title
    AND so.volunteer_id = (SELECT user_id FROM users WHERE university_email = v.vol_email)
);

COMMIT;

-- 3. Verification: every row must say PASS ------------------------------
SELECT check_name, found, expected,
       CASE WHEN found = expected THEN 'PASS' ELSE 'FAIL' END AS result
FROM (
  SELECT 'departments'        AS check_name, (SELECT count(*) FROM department)::int AS found, 10 AS expected
  UNION ALL SELECT 'categories',             (SELECT count(*) FROM category)::int, 4
  UNION ALL SELECT 'roles (STUDENT, ADMIN)', (SELECT count(*) FROM role WHERE role_name IN ('STUDENT','ADMIN'))::int, 2
  UNION ALL SELECT 'admin accounts (verified)',
        (SELECT count(*) FROM users u JOIN role r USING (role_id)
          WHERE r.role_name='ADMIN' AND u.is_verified AND u.university_email='admin@skillswap.demo')::int, 1
  UNION ALL SELECT 'demo volunteers (verified)',
        (SELECT count(*) FROM users WHERE university_email LIKE 'volunteer_@skillswap.demo' AND is_verified)::int, 2
  UNION ALL SELECT 'demo students (verified)',
        (SELECT count(*) FROM users WHERE university_email LIKE 'student_@skillswap.demo' AND is_verified)::int, 2
  UNION ALL SELECT 'sample offers ACTIVE',
        (SELECT count(*) FROM service_offer so JOIN users u ON u.user_id = so.volunteer_id
          WHERE u.university_email LIKE 'volunteer_@skillswap.demo' AND so.status = 'ACTIVE')::int, 6
  UNION ALL SELECT 'categories used by offers',
        (SELECT count(DISTINCT so.category_id) FROM service_offer so JOIN users u ON u.user_id = so.volunteer_id
          WHERE u.university_email LIKE 'volunteer_@skillswap.demo')::int, 4
) c
ORDER BY check_name;
