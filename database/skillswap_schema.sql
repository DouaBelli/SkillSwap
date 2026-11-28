-- =====================================================================
-- SkillSwap database schema (Milestone 1: Database Design)
-- Target: PostgreSQL (written for 19; tested on 16, uses no version-specific features)
-- Contents: 12 tables, 19 foreign keys, 1 helper function, 11 triggers,
--           4 views, indexes, and starting data.
-- Run on an EMPTY database named skillswap (see skillswap_explanation.md).
-- =====================================================================

-- ---------------------------------------------------------------------
-- OPTIONAL RESET (only if you must run the script again on the same DB).
-- Uncomment, run once, then comment it again. This DELETES ALL DATA.
-- ---------------------------------------------------------------------
-- DROP VIEW IF EXISTS v_needs_attention, v_user_profile, v_user_rating, v_user_credit_balance;
-- DROP TABLE IF EXISTS user_block, report, certificate, certificate_milestone,
--     credit_transaction, service_request, service_offer, category,
--     email_verification, users, department, role CASCADE;
-- DROP FUNCTION IF EXISTS fn_is_admin(INT), fn_ev_resend_guard(), fn_request_insert_guard(),
--     fn_request_transition(), fn_offer_hidden(), fn_credit_on_confirm(), fn_credit_guard(),
--     fn_issue_certificates(), fn_report_guard(), fn_block_guard(), fn_block_apply(),
--     fn_unblock_apply();

-- ---------------------------------------------------------------------
-- 1. Identity and access
-- ---------------------------------------------------------------------
CREATE TABLE role (
    role_id     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_name   VARCHAR(30) NOT NULL UNIQUE,
    CONSTRAINT ck_role_name CHECK (role_name IN ('STUDENT', 'ADMIN'))
);

CREATE TABLE department (
    department_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE users (
    user_id           INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    role_id           INT NOT NULL REFERENCES role(role_id),
    department_id     INT NOT NULL REFERENCES department(department_id),
    full_name         VARCHAR(100) NOT NULL,
    university_email  VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255) NOT NULL,
    year_of_study     SMALLINT NOT NULL,
    is_verified       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_name CHECK (length(btrim(full_name)) > 0),
    CONSTRAINT ck_users_year CHECK (year_of_study BETWEEN 1 AND 7)
);
-- E-mail is also unique ignoring upper/lower case (the backend searches with lower()).
CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(university_email));

-- One row per emailed code (registration or password reset).
-- Rules: 6-digit code (stored hashed), valid 10 minutes, 3 wrong tries,
-- resend every 60 seconds (trigger). The newest unused row is the valid one.
CREATE TABLE email_verification (
    verification_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    purpose          VARCHAR(30) NOT NULL,
    otp_hash         VARCHAR(255) NOT NULL,
    expires_at       TIMESTAMPTZ NOT NULL DEFAULT now() + interval '10 minutes',
    attempts         SMALLINT NOT NULL DEFAULT 0,
    used_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_ev_purpose  CHECK (purpose IN ('REGISTRATION', 'PASSWORD_RESET')),
    CONSTRAINT ck_ev_attempts CHECK (attempts BETWEEN 0 AND 3),
    CONSTRAINT ck_ev_expiry   CHECK (expires_at = created_at + interval '10 minutes')
);

-- ---------------------------------------------------------------------
-- 2. Services
-- ---------------------------------------------------------------------
CREATE TABLE category (
    category_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(80) NOT NULL UNIQUE
);

CREATE TABLE service_offer (
    offer_id      INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    volunteer_id  INT NOT NULL REFERENCES users(user_id),
    category_id   INT NOT NULL REFERENCES category(category_id),
    title         VARCHAR(150) NOT NULL,
    description   TEXT NOT NULL,
    availability  VARCHAR(255) NOT NULL,
    help_method   VARCHAR(255) NOT NULL,          -- free text: how help is given
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_offer_status CHECK (status IN ('ACTIVE', 'HIDDEN', 'HIDDEN_BY_BLOCK')),
    CONSTRAINT ck_offer_required CHECK (
        length(btrim(title)) > 0 AND length(btrim(description)) > 0 AND
        length(btrim(availability)) > 0 AND length(btrim(help_method)) > 0)
);

CREATE TABLE service_request (
    request_id    INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    offer_id      INT NOT NULL REFERENCES service_offer(offer_id),
    requester_id  INT NOT NULL REFERENCES users(user_id),
    message       TEXT,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rating        SMALLINT,                      -- optional 1-5, anonymous, set when confirming
    reject_reason TEXT,                          -- optional
    cancel_reason TEXT,                          -- required when cancelled
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at   TIMESTAMPTZ,
    performed_at  TIMESTAMPTZ,
    confirmed_at  TIMESTAMPTZ,
    CONSTRAINT ck_req_status CHECK (status IN
        ('PENDING', 'ACCEPTED', 'PERFORMED', 'CONFIRMED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_req_rating CHECK (rating IS NULL OR (status = 'CONFIRMED' AND rating BETWEEN 1 AND 5)),
    CONSTRAINT ck_req_cancel_reason CHECK (
        status <> 'CANCELLED' OR COALESCE(length(btrim(cancel_reason)), 0) > 0),
    -- Safety net: a confirmed request always has its three step times.
    CONSTRAINT ck_req_confirmed_times CHECK (
        status <> 'CONFIRMED' OR
        (accepted_at IS NOT NULL AND performed_at IS NOT NULL AND confirmed_at IS NOT NULL))
);
-- Duplicate request: not allowed while an earlier one is PENDING, ACCEPTED, or PERFORMED.
CREATE UNIQUE INDEX ux_req_one_open ON service_request (offer_id, requester_id)
    WHERE status IN ('PENDING', 'ACCEPTED', 'PERFORMED');

-- ---------------------------------------------------------------------
-- 3. Credits and certificates
-- ---------------------------------------------------------------------
CREATE TABLE credit_transaction (
    transaction_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    request_id      INT NOT NULL UNIQUE REFERENCES service_request(request_id),
    credits         INT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_credit_one CHECK (credits = 1)
);

CREATE TABLE certificate_milestone (
    milestone_id     INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credits_required INT NOT NULL UNIQUE,
    title            VARCHAR(100) NOT NULL,
    CONSTRAINT ck_milestone_positive CHECK (credits_required > 0)
);

CREATE TABLE certificate (
    certificate_id  INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id),
    milestone_id    INT NOT NULL REFERENCES certificate_milestone(milestone_id),
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cert_user_milestone UNIQUE (user_id, milestone_id)   -- 1 certificate per user per milestone
);

-- ---------------------------------------------------------------------
-- 4. Moderation
-- ---------------------------------------------------------------------
CREATE TABLE report (
    report_id         INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reporter_id       INT NOT NULL REFERENCES users(user_id),
    reported_user_id  INT NOT NULL REFERENCES users(user_id),
    offer_id          INT REFERENCES service_offer(offer_id),     -- set when an offer is reported
    request_id        INT REFERENCES service_request(request_id), -- set when a request is reported
    reason_code       VARCHAR(30) NOT NULL,                       -- the reason the user chose
    note              TEXT,                                       -- optional note by the reporter
    status            VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    resolution_action VARCHAR(20),                                -- the administrator's decision
    admin_note        TEXT,                                       -- the administrator's short reason
    reviewed_by       INT REFERENCES users(user_id),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at       TIMESTAMPTZ,
    CONSTRAINT ck_rep_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_rep_action CHECK (resolution_action IS NULL OR
        resolution_action IN ('DISMISSED', 'OFFER_HIDDEN', 'USER_BLOCKED')),
    CONSTRAINT ck_rep_not_self   CHECK (reporter_id <> reported_user_id),
    CONSTRAINT ck_rep_noconf_req CHECK (reason_code <> 'NO_CONFIRMATION' OR request_id IS NOT NULL),
    CONSTRAINT ck_rep_reason     CHECK (length(btrim(reason_code)) > 0),
    -- A report targets an offer OR a request, never both.
    CONSTRAINT ck_rep_one_target CHECK (offer_id IS NULL OR request_id IS NULL),
    CONSTRAINT ck_rep_resolved CHECK (
        (status = 'OPEN' AND resolution_action IS NULL AND admin_note IS NULL
                         AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR
        (status = 'RESOLVED' AND resolution_action IS NOT NULL AND reviewed_by IS NOT NULL
                         AND reviewed_at IS NOT NULL AND COALESCE(length(btrim(admin_note)), 0) > 0))
);
-- The same item cannot be reported twice by the same user.
CREATE UNIQUE INDEX ux_rep_user    ON report (reporter_id, reported_user_id)
    WHERE offer_id IS NULL AND request_id IS NULL;
CREATE UNIQUE INDEX ux_rep_offer   ON report (reporter_id, offer_id)   WHERE offer_id   IS NOT NULL;
CREATE UNIQUE INDEX ux_rep_request ON report (reporter_id, request_id) WHERE request_id IS NOT NULL;

CREATE TABLE user_block (
    block_id       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id        INT NOT NULL REFERENCES users(user_id),       -- the blocked user
    blocked_by     INT NOT NULL REFERENCES users(user_id),       -- administrator
    report_id      INT REFERENCES report(report_id),
    reason         TEXT NOT NULL,
    blocked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    unblocked_at   TIMESTAMPTZ,
    unblocked_by   INT REFERENCES users(user_id),
    unblock_reason TEXT,
    CONSTRAINT ck_block_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_block_unblock  CHECK (
        (unblocked_at IS NULL AND unblocked_by IS NULL AND unblock_reason IS NULL)
        OR (unblocked_at IS NOT NULL AND unblocked_by IS NOT NULL
            AND COALESCE(length(btrim(unblock_reason)), 0) > 0))
);
-- One active block per user.
CREATE UNIQUE INDEX ux_block_one_active ON user_block (user_id) WHERE unblocked_at IS NULL;

-- ---------------------------------------------------------------------
-- 5. Performance indexes (foreign keys and the main screen queries)
-- ---------------------------------------------------------------------
CREATE INDEX ix_users_role           ON users (role_id);
CREATE INDEX ix_users_department     ON users (department_id);
CREATE INDEX ix_ev_user_created      ON email_verification (user_id, created_at DESC);
CREATE INDEX ix_offer_volunteer      ON service_offer (volunteer_id);
CREATE INDEX ix_offer_category       ON service_offer (category_id);
CREATE INDEX ix_offer_active_newest  ON service_offer (created_at DESC) WHERE status = 'ACTIVE';
CREATE INDEX ix_req_offer            ON service_request (offer_id);
CREATE INDEX ix_req_requester        ON service_request (requester_id);
CREATE INDEX ix_req_performed_at     ON service_request (performed_at) WHERE status = 'PERFORMED';
CREATE INDEX ix_cert_milestone       ON certificate (milestone_id);
CREATE INDEX ix_rep_reported         ON report (reported_user_id);
CREATE INDEX ix_rep_offer_id         ON report (offer_id)   WHERE offer_id IS NOT NULL;
CREATE INDEX ix_rep_request_id       ON report (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX ix_rep_reviewed_by      ON report (reviewed_by) WHERE reviewed_by IS NOT NULL;
CREATE INDEX ix_rep_open_oldest      ON report (created_at) WHERE status = 'OPEN';
CREATE INDEX ix_block_blocked_by     ON user_block (blocked_by);
CREATE INDEX ix_block_report         ON user_block (report_id) WHERE report_id IS NOT NULL;
CREATE INDEX ix_block_unblocked_by   ON user_block (unblocked_by) WHERE unblocked_by IS NOT NULL;

-- =====================================================================
-- 6. Helper function
-- =====================================================================
CREATE FUNCTION fn_is_admin(p_user_id INT) RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT EXISTS (
        SELECT 1 FROM users u JOIN role r ON r.role_id = u.role_id
        WHERE u.user_id = p_user_id AND r.role_name = 'ADMIN');
$$;

-- =====================================================================
-- 7. Triggers (11) and their functions
-- =====================================================================

-- 7.1 trg_ev_resend_guard: a new code is refused within 60 seconds of the last one.
CREATE FUNCTION fn_ev_resend_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_last TIMESTAMPTZ;
BEGIN
    SELECT max(created_at) INTO v_last FROM email_verification WHERE user_id = NEW.user_id;
    IF v_last IS NOT NULL AND NEW.created_at < v_last + interval '60 seconds' THEN
        RAISE EXCEPTION 'A new code can be sent only 60 seconds after the last one.';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_ev_resend_guard
    BEFORE INSERT ON email_verification
    FOR EACH ROW EXECUTE FUNCTION fn_ev_resend_guard();

-- 7.2 trg_request_insert_guard: no request to your own offer or to a hidden offer;
--     a request always starts as PENDING.
CREATE FUNCTION fn_request_insert_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_volunteer INT;
    v_status    VARCHAR(20);
BEGIN
    SELECT volunteer_id, status INTO v_volunteer, v_status
    FROM service_offer WHERE offer_id = NEW.offer_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'This offer does not exist.';
    END IF;
    IF v_volunteer = NEW.requester_id THEN
        RAISE EXCEPTION 'You cannot request help from your own offer.';
    END IF;
    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'This offer is not available any more.';
    END IF;
    IF NEW.status <> 'PENDING' OR NEW.rating IS NOT NULL OR NEW.accepted_at IS NOT NULL
       OR NEW.performed_at IS NOT NULL OR NEW.confirmed_at IS NOT NULL THEN
        RAISE EXCEPTION 'A new request must start as PENDING.';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_request_insert_guard
    BEFORE INSERT ON service_request
    FOR EACH ROW EXECUTE FUNCTION fn_request_insert_guard();

-- 7.3 trg_request_transition: follows the status diagram, freezes closed requests,
--     and writes the step times with the database clock.
--       PENDING -> ACCEPTED | REJECTED | CANCELLED
--       ACCEPTED -> PERFORMED | CANCELLED
--       PERFORMED -> CONFIRMED | CANCELLED
CREATE FUNCTION fn_request_transition() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD IS NOT DISTINCT FROM NEW THEN
        RETURN NEW;                                   -- nothing changed
    END IF;
    IF OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'A confirmed service cannot be changed or cancelled.';
    END IF;
    IF OLD.status IN ('REJECTED', 'CANCELLED') THEN
        RAISE EXCEPTION 'This request is already closed (%).', OLD.status;
    END IF;
    IF NEW.offer_id <> OLD.offer_id OR NEW.requester_id <> OLD.requester_id
       OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'The offer, the requester and the sending time of a request cannot change.';
    END IF;

    IF NEW.status = OLD.status THEN
        IF NEW.accepted_at  IS DISTINCT FROM OLD.accepted_at
           OR NEW.performed_at IS DISTINCT FROM OLD.performed_at
           OR NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at THEN
            RAISE EXCEPTION 'Step times change only together with the status.';
        END IF;
        RETURN NEW;
    END IF;

    IF NOT ((OLD.status = 'PENDING'   AND NEW.status IN ('ACCEPTED', 'REJECTED', 'CANCELLED'))
         OR (OLD.status = 'ACCEPTED'  AND NEW.status IN ('PERFORMED', 'CANCELLED'))
         OR (OLD.status = 'PERFORMED' AND NEW.status IN ('CONFIRMED', 'CANCELLED'))) THEN
        RAISE EXCEPTION 'A % request cannot become %.', OLD.status, NEW.status;
    END IF;

    IF NEW.status = 'ACCEPTED'  THEN NEW.accepted_at  := now(); END IF;
    IF NEW.status = 'PERFORMED' THEN NEW.performed_at := now(); END IF;
    IF NEW.status = 'CONFIRMED' THEN NEW.confirmed_at := now(); END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_request_transition
    BEFORE UPDATE ON service_request
    FOR EACH ROW EXECUTE FUNCTION fn_request_transition();

-- 7.4 trg_offer_hidden: hiding an offer cancels its PENDING requests only.
CREATE FUNCTION fn_offer_hidden() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE service_request
       SET status = 'CANCELLED',
           cancel_reason = CASE NEW.status WHEN 'HIDDEN_BY_BLOCK' THEN 'User blocked'
                                           ELSE 'Offer unavailable' END
     WHERE offer_id = NEW.offer_id AND status = 'PENDING';
    RETURN NULL;
END $$;

CREATE TRIGGER trg_offer_hidden
    AFTER UPDATE OF status ON service_offer
    FOR EACH ROW
    WHEN (NEW.status IN ('HIDDEN', 'HIDDEN_BY_BLOCK') AND OLD.status IS DISTINCT FROM NEW.status)
    EXECUTE FUNCTION fn_offer_hidden();

-- 7.5 trg_credit_on_confirm: a confirmed request creates exactly one credit.
CREATE FUNCTION fn_credit_on_confirm() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO credit_transaction (request_id, credits)
    VALUES (NEW.request_id, 1)
    ON CONFLICT (request_id) DO NOTHING;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_credit_on_confirm
    AFTER UPDATE OF status ON service_request
    FOR EACH ROW
    WHEN (NEW.status = 'CONFIRMED' AND OLD.status IS DISTINCT FROM 'CONFIRMED')
    EXECUTE FUNCTION fn_credit_on_confirm();

-- 7.6 trg_credit_guard: credits only for confirmed requests, and are never edited or deleted.
CREATE FUNCTION fn_credit_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_status VARCHAR(20);
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        RAISE EXCEPTION 'Credits are never edited or deleted.';
    END IF;
    SELECT status INTO v_status FROM service_request WHERE request_id = NEW.request_id;
    IF v_status IS DISTINCT FROM 'CONFIRMED' THEN
        RAISE EXCEPTION 'A credit can be created only for a CONFIRMED request.';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_credit_guard
    BEFORE INSERT OR UPDATE OR DELETE ON credit_transaction
    FOR EACH ROW EXECUTE FUNCTION fn_credit_guard();

-- 7.7 trg_issue_certificates: issues the certificates (10, 25, 50) when the total is reached.
--     The advisory lock stops two confirmations of the same volunteer from missing a milestone.
CREATE FUNCTION fn_issue_certificates() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_volunteer INT;
    v_total     BIGINT;
BEGIN
    SELECT so.volunteer_id INTO v_volunteer
    FROM service_request sr JOIN service_offer so ON so.offer_id = sr.offer_id
    WHERE sr.request_id = NEW.request_id;

    PERFORM pg_advisory_xact_lock(7001, v_volunteer);

    SELECT COALESCE(SUM(ct.credits), 0) INTO v_total
    FROM credit_transaction ct
    JOIN service_request sr ON sr.request_id = ct.request_id
    JOIN service_offer so   ON so.offer_id   = sr.offer_id
    WHERE so.volunteer_id = v_volunteer;

    INSERT INTO certificate (user_id, milestone_id)
    SELECT v_volunteer, m.milestone_id
    FROM certificate_milestone m
    WHERE m.credits_required <= v_total
    ON CONFLICT (user_id, milestone_id) DO NOTHING;
    RETURN NULL;
END $$;

CREATE TRIGGER trg_issue_certificates
    AFTER INSERT ON credit_transaction
    FOR EACH ROW EXECUTE FUNCTION fn_issue_certificates();

-- 7.8 trg_report_guard: valid reports, the 7-day no-confirmation rule, administrator-only review.
CREATE FUNCTION fn_report_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_offer_owner INT;
    v_requester   INT;
    v_volunteer   INT;
    v_req_status  VARCHAR(20);
    v_performed   TIMESTAMPTZ;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'OPEN' THEN
            RAISE EXCEPTION 'A new report must start as OPEN.';
        END IF;

        IF NEW.offer_id IS NOT NULL THEN
            SELECT volunteer_id INTO v_offer_owner FROM service_offer WHERE offer_id = NEW.offer_id;
            IF v_offer_owner IS DISTINCT FROM NEW.reported_user_id THEN
                RAISE EXCEPTION 'The reported user must be the owner of the reported offer.';
            END IF;
        END IF;

        IF NEW.request_id IS NOT NULL THEN
            SELECT sr.requester_id, so.volunteer_id, sr.status, sr.performed_at
              INTO v_requester, v_volunteer, v_req_status, v_performed
            FROM service_request sr JOIN service_offer so ON so.offer_id = sr.offer_id
            WHERE sr.request_id = NEW.request_id;
            IF NOT ((NEW.reporter_id = v_requester AND NEW.reported_user_id = v_volunteer)
                 OR (NEW.reporter_id = v_volunteer AND NEW.reported_user_id = v_requester)) THEN
                RAISE EXCEPTION 'A request can be reported only by one of its two users, against the other.';
            END IF;

            IF NEW.reason_code = 'NO_CONFIRMATION' THEN
                IF NEW.reporter_id <> v_volunteer THEN
                    RAISE EXCEPTION 'Only the volunteer can send a no-confirmation report.';
                END IF;
                IF v_req_status <> 'PERFORMED' THEN
                    RAISE EXCEPTION 'A no-confirmation report needs a PERFORMED request (this one is %).', v_req_status;
                END IF;
                IF v_performed > now() - interval '7 days' THEN
                    RAISE EXCEPTION 'A no-confirmation report is possible only 7 days after the service was performed.';
                END IF;
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    -- UPDATE (the administrator's review)
    IF OLD IS NOT DISTINCT FROM NEW THEN
        RETURN NEW;
    END IF;
    IF OLD.status = 'RESOLVED' THEN
        RAISE EXCEPTION 'A resolved report cannot be changed.';
    END IF;
    IF NEW.reporter_id <> OLD.reporter_id OR NEW.reported_user_id <> OLD.reported_user_id
       OR NEW.offer_id   IS DISTINCT FROM OLD.offer_id
       OR NEW.request_id IS DISTINCT FROM OLD.request_id
       OR NEW.reason_code <> OLD.reason_code OR NEW.created_at <> OLD.created_at THEN
        RAISE EXCEPTION 'The reporter, the reported item, and the reason cannot change.';
    END IF;
    IF NEW.status = 'RESOLVED' THEN
        IF NOT fn_is_admin(NEW.reviewed_by) THEN
            RAISE EXCEPTION 'Only an administrator can review a report.';
        END IF;
        IF NEW.resolution_action = 'OFFER_HIDDEN' AND NEW.offer_id IS NULL THEN
            RAISE EXCEPTION 'OFFER_HIDDEN needs a report about an offer.';
        END IF;
        NEW.reviewed_at := now();
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER trg_report_guard
    BEFORE INSERT OR UPDATE ON report
    FOR EACH ROW EXECUTE FUNCTION fn_report_guard();

-- 7.9 trg_block_guard: only administrators block and unblock; block history is kept.
CREATE FUNCTION fn_block_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_reported INT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Block history is kept and cannot be deleted.';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NOT fn_is_admin(NEW.blocked_by) THEN
            RAISE EXCEPTION 'Only an administrator can block a user.';
        END IF;
        IF NEW.user_id = NEW.blocked_by THEN
            RAISE EXCEPTION 'An administrator cannot block himself or herself.';
        END IF;
        IF NEW.unblocked_at IS NOT NULL OR NEW.unblocked_by IS NOT NULL OR NEW.unblock_reason IS NOT NULL THEN
            RAISE EXCEPTION 'A new block must be active.';
        END IF;
        IF NEW.report_id IS NOT NULL THEN
            SELECT reported_user_id INTO v_reported FROM report WHERE report_id = NEW.report_id;
            IF v_reported IS DISTINCT FROM NEW.user_id THEN
                RAISE EXCEPTION 'The report of a block must be about the blocked user.';
            END IF;
        END IF;
        RETURN NEW;
    END IF;

    -- UPDATE: the only allowed change is the unblock.
    IF OLD.unblocked_at IS NOT NULL THEN
        RAISE EXCEPTION 'An ended block cannot be changed.';
    END IF;
    IF NEW.user_id <> OLD.user_id OR NEW.blocked_by <> OLD.blocked_by
       OR NEW.report_id IS DISTINCT FROM OLD.report_id
       OR NEW.reason <> OLD.reason OR NEW.blocked_at <> OLD.blocked_at THEN
        RAISE EXCEPTION 'Only the unblock fields of a block can change.';
    END IF;
    IF NEW.unblocked_at IS NULL THEN
        RAISE EXCEPTION 'The only change allowed on an active block is the unblock.';
    END IF;
    IF NOT fn_is_admin(NEW.unblocked_by) THEN
        RAISE EXCEPTION 'Only an administrator can unblock a user.';
    END IF;
    NEW.unblocked_at := now();
    RETURN NEW;
END $$;

CREATE TRIGGER trg_block_guard
    BEFORE INSERT OR UPDATE OR DELETE ON user_block
    FOR EACH ROW EXECUTE FUNCTION fn_block_guard();

-- 7.10 trg_block_apply: a block cancels the user's active requests (as student or volunteer)
--      and hides the user's active offers. Credits and certificates are not touched.
CREATE FUNCTION fn_block_apply() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE service_request sr
       SET status = 'CANCELLED', cancel_reason = 'User blocked'
     WHERE sr.status IN ('PENDING', 'ACCEPTED', 'PERFORMED')
       AND (sr.requester_id = NEW.user_id
            OR sr.offer_id IN (SELECT offer_id FROM service_offer WHERE volunteer_id = NEW.user_id));

    UPDATE service_offer
       SET status = 'HIDDEN_BY_BLOCK'
     WHERE volunteer_id = NEW.user_id AND status = 'ACTIVE';
    RETURN NULL;
END $$;

CREATE TRIGGER trg_block_apply
    AFTER INSERT ON user_block
    FOR EACH ROW EXECUTE FUNCTION fn_block_apply();

-- 7.11 trg_unblock_apply: unblocking shows again only the offers hidden by the block.
--      Cancelled requests stay cancelled.
CREATE FUNCTION fn_unblock_apply() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    UPDATE service_offer
       SET status = 'ACTIVE'
     WHERE volunteer_id = NEW.user_id AND status = 'HIDDEN_BY_BLOCK';
    RETURN NULL;
END $$;

CREATE TRIGGER trg_unblock_apply
    AFTER UPDATE OF unblocked_at ON user_block
    FOR EACH ROW
    WHEN (OLD.unblocked_at IS NULL AND NEW.unblocked_at IS NOT NULL)
    EXECUTE FUNCTION fn_unblock_apply();

-- =====================================================================
-- 8. Views (4). None returns the e-mail or an individual rating.
-- =====================================================================

-- Total credits of each user (0 when none). Credits of confirmed requests on the user's offers.
CREATE VIEW v_user_credit_balance AS
SELECT u.user_id,
       COALESCE(SUM(ct.credits), 0)::INT AS total_credits
FROM users u
LEFT JOIN service_offer so       ON so.volunteer_id = u.user_id
LEFT JOIN service_request sr     ON sr.offer_id     = so.offer_id
LEFT JOIN credit_transaction ct  ON ct.request_id   = sr.request_id
GROUP BY u.user_id;

-- Number of ratings and the average. The average stays empty (NULL) below 3 ratings.
CREATE VIEW v_user_rating AS
SELECT u.user_id,
       COUNT(sr.rating)::INT AS rating_count,
       CASE WHEN COUNT(sr.rating) >= 3 THEN ROUND(AVG(sr.rating), 2) END AS average_rating
FROM users u
LEFT JOIN service_offer so   ON so.volunteer_id = u.user_id
LEFT JOIN service_request sr ON sr.offer_id = so.offer_id
                            AND sr.status = 'CONFIRMED' AND sr.rating IS NOT NULL
GROUP BY u.user_id;

-- Public profile: name, department, year, credits, average rating, active offers. No e-mail.
CREATE VIEW v_user_profile AS
SELECT u.user_id,
       u.full_name,
       d.name AS department,
       u.year_of_study,
       b.total_credits,
       r.average_rating,
       (SELECT COUNT(*) FROM service_offer so
         WHERE so.volunteer_id = u.user_id AND so.status = 'ACTIVE')::INT AS active_offers
FROM users u
JOIN department d              ON d.department_id = u.department_id
JOIN v_user_credit_balance b   ON b.user_id = u.user_id
JOIN v_user_rating r           ON r.user_id = u.user_id;

-- Menu counter: requests the user must answer + services the user must confirm.
CREATE VIEW v_needs_attention AS
SELECT u.user_id,
       (SELECT COUNT(*) FROM service_request sr JOIN service_offer so ON so.offer_id = sr.offer_id
         WHERE so.volunteer_id = u.user_id AND sr.status = 'PENDING')::INT   AS requests_to_answer,
       (SELECT COUNT(*) FROM service_request sr
         WHERE sr.requester_id = u.user_id AND sr.status = 'PERFORMED')::INT AS services_to_confirm,
       ((SELECT COUNT(*) FROM service_request sr JOIN service_offer so ON so.offer_id = sr.offer_id
          WHERE so.volunteer_id = u.user_id AND sr.status = 'PENDING')
      + (SELECT COUNT(*) FROM service_request sr
          WHERE sr.requester_id = u.user_id AND sr.status = 'PERFORMED'))::INT AS total_waiting
FROM users u;

-- =====================================================================
-- 9. Starting data
-- =====================================================================
INSERT INTO role (role_name) VALUES ('STUDENT'), ('ADMIN');

INSERT INTO category (name) VALUES
    ('Academic subjects and sources'),
    ('Tools'),
    ('Languages'),
    ('CV and internship guidance');

INSERT INTO certificate_milestone (credits_required, title) VALUES
    (10, 'Volunteer Certificate - 10 Credits'),
    (25, 'Volunteer Certificate - 25 Credits'),
    (50, 'Volunteer Certificate - 50 Credits');

-- EXAMPLE departments: replace with the real list of the faculty.
INSERT INTO department (name) VALUES
    ('Architectural Engineering'),
    ('Chemical Engineering'),
    ('Civil Engineering'),
    ('Computer and Systems Engineering'),
    ('Electrical Power and Machines Engineering'),
    ('Electronics and Communications Engineering'),
    ('Marine Engineering and Naval Architecture'),
    ('Mechanical Engineering'),
    ('Petroleum Engineering'),
    ('Production Engineering');

-- End of skillswap_schema.sql
