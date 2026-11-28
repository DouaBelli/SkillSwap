-- =====================================================================
-- SkillSwap schema corrections
-- Run AFTER skillswap_schema.sql on the skillswap database.
-- These are CREATE OR REPLACE function definitions plus two new triggers.
-- Existing tables, views, indexes, and original trigger names are preserved.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. Shared helper: is this user currently blocked?
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_is_user_blocked(p_user_id INT)
RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT EXISTS (
        SELECT 1
        FROM user_block
        WHERE user_id = p_user_id
          AND unblocked_at IS NULL
    );
$$;

-- ---------------------------------------------------------------------
-- 2. Request creation: reject blocked requesters as well as own/hidden offers.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_request_insert_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_volunteer INT;
    v_status    VARCHAR(20);
BEGIN
    SELECT volunteer_id, status
      INTO v_volunteer, v_status
      FROM service_offer
     WHERE offer_id = NEW.offer_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'This offer does not exist.';
    END IF;

    IF fn_is_user_blocked(NEW.requester_id) THEN
        RAISE EXCEPTION 'A blocked user cannot create a request.';
    END IF;

    IF fn_is_user_blocked(v_volunteer) THEN
        RAISE EXCEPTION 'A request cannot be made to a blocked volunteer.';
    END IF;

    IF v_volunteer = NEW.requester_id THEN
        RAISE EXCEPTION 'You cannot request help from your own offer.';
    END IF;

    IF v_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'This offer is not available any more.';
    END IF;

    IF NEW.status <> 'PENDING'
       OR NEW.rating IS NOT NULL
       OR NEW.accepted_at IS NOT NULL
       OR NEW.performed_at IS NOT NULL
       OR NEW.confirmed_at IS NOT NULL THEN
        RAISE EXCEPTION 'A new request must start as PENDING.';
    END IF;

    RETURN NEW;
END $$;

-- ---------------------------------------------------------------------
-- 3. Request transitions: client-supplied step times must remain unchanged.
--    The database itself writes exactly the timestamp for the new state.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_request_transition() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF OLD IS NOT DISTINCT FROM NEW THEN
        RETURN NEW;
    END IF;

    IF OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'A confirmed service cannot be changed or cancelled.';
    END IF;

    IF OLD.status IN ('REJECTED', 'CANCELLED') THEN
        RAISE EXCEPTION 'This request is already closed (%).', OLD.status;
    END IF;

    IF NEW.offer_id IS DISTINCT FROM OLD.offer_id
       OR NEW.requester_id IS DISTINCT FROM OLD.requester_id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'The offer, requester, and sending time cannot change.';
    END IF;

    IF NEW.status = OLD.status THEN
        IF NEW.accepted_at IS DISTINCT FROM OLD.accepted_at
           OR NEW.performed_at IS DISTINCT FROM OLD.performed_at
           OR NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at THEN
            RAISE EXCEPTION 'Step times change only together with the status.';
        END IF;
        RETURN NEW;
    END IF;

    IF NOT (
        (OLD.status = 'PENDING'   AND NEW.status IN ('ACCEPTED', 'REJECTED', 'CANCELLED'))
        OR
        (OLD.status = 'ACCEPTED'  AND NEW.status IN ('PERFORMED', 'CANCELLED'))
        OR
        (OLD.status = 'PERFORMED' AND NEW.status IN ('CONFIRMED', 'CANCELLED'))
    ) THEN
        RAISE EXCEPTION 'A % request cannot become %.', OLD.status, NEW.status;
    END IF;

    -- The client may change the status and business fields, but may not
    -- supply, rewrite, or clear any of the lifecycle timestamps.
    IF NEW.accepted_at IS DISTINCT FROM OLD.accepted_at
       OR NEW.performed_at IS DISTINCT FROM OLD.performed_at
       OR NEW.confirmed_at IS DISTINCT FROM OLD.confirmed_at THEN
        RAISE EXCEPTION 'Lifecycle timestamps are written by the database and cannot be supplied by the client.';
    END IF;

    IF NEW.status = 'ACCEPTED' THEN
        NEW.accepted_at := clock_timestamp();
    ELSIF NEW.status = 'PERFORMED' THEN
        NEW.performed_at := clock_timestamp();
    ELSIF NEW.status = 'CONFIRMED' THEN
        NEW.confirmed_at := clock_timestamp();
    END IF;

    RETURN NEW;
END $$;

-- ---------------------------------------------------------------------
-- 4. Offers: blocked users cannot insert ACTIVE offers or reactivate them.
--    This also prevents changing an offer's owner to a blocked user while active.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_offer_block_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'ACTIVE' AND fn_is_user_blocked(NEW.volunteer_id) THEN
        RAISE EXCEPTION 'A blocked user cannot publish or activate an offer.';
    END IF;

    RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_offer_block_guard ON service_offer;
CREATE TRIGGER trg_offer_block_guard
    BEFORE INSERT OR UPDATE OF volunteer_id, status ON service_offer
    FOR EACH ROW
    EXECUTE FUNCTION fn_offer_block_guard();

-- ---------------------------------------------------------------------
-- 5. OTP creation: serialize resends per user, use the server clock,
--    and derive expiry from the server-created timestamp.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_ev_resend_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_last TIMESTAMPTZ;
    v_now  TIMESTAMPTZ;
BEGIN
    -- All transactions creating a code for this user take the same lock.
    PERFORM pg_advisory_xact_lock(7002, NEW.user_id);

    v_now := clock_timestamp();

    SELECT max(created_at)
      INTO v_last
      FROM email_verification
     WHERE user_id = NEW.user_id;

    IF v_last IS NOT NULL AND v_now < v_last + interval '60 seconds' THEN
        RAISE EXCEPTION 'A new code can be sent only 60 seconds after the last one.';
    END IF;

    -- Never trust timestamps supplied by the application.
    NEW.created_at := v_now;
    NEW.expires_at := v_now + interval '10 minutes';
    NEW.attempts := 0;
    NEW.used_at := NULL;

    RETURN NEW;
END $$;

-- ---------------------------------------------------------------------
-- 6. OTP lifecycle: immutable identity/hash/timestamps; attempts only rise
--    one at a time; a code can be marked used once, only if current, live,
--    and below the attempt limit. Hash comparison remains a backend task.
-- ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION fn_ev_update_guard() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
DECLARE
    v_latest_id INT;
BEGIN
    IF NEW.user_id IS DISTINCT FROM OLD.user_id
       OR NEW.purpose IS DISTINCT FROM OLD.purpose
       OR NEW.otp_hash IS DISTINCT FROM OLD.otp_hash
       OR NEW.created_at IS DISTINCT FROM OLD.created_at
       OR NEW.expires_at IS DISTINCT FROM OLD.expires_at THEN
        RAISE EXCEPTION 'The identity and contents of a verification code cannot change.';
    END IF;

    IF OLD.used_at IS NOT NULL THEN
        RAISE EXCEPTION 'This verification code has already been used.';
    END IF;

    SELECT verification_id
      INTO v_latest_id
      FROM email_verification
     WHERE user_id = OLD.user_id
     ORDER BY created_at DESC, verification_id DESC
     LIMIT 1;

    IF v_latest_id IS DISTINCT FROM OLD.verification_id THEN
        RAISE EXCEPTION 'A newer verification code exists.';
    END IF;

    IF OLD.expires_at <= clock_timestamp() THEN
        RAISE EXCEPTION 'This verification code has expired.';
    END IF;

    IF NEW.attempts IS DISTINCT FROM OLD.attempts THEN
        IF NEW.used_at IS DISTINCT FROM OLD.used_at THEN
            RAISE EXCEPTION 'An attempt update and code acceptance must be separate operations.';
        END IF;

        IF NEW.attempts <> OLD.attempts + 1 OR OLD.attempts >= 3 THEN
            RAISE EXCEPTION 'Attempts may only increase by one, up to the limit of three.';
        END IF;

        RETURN NEW;
    END IF;

    IF NEW.used_at IS DISTINCT FROM OLD.used_at THEN
        IF NEW.used_at IS NULL THEN
            RAISE EXCEPTION 'A used verification code cannot be reset.';
        END IF;

        IF OLD.attempts >= 3 THEN
            RAISE EXCEPTION 'This verification code has reached its attempt limit.';
        END IF;

        NEW.used_at := clock_timestamp();
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'No permitted verification-code change was made.';
END $$;

DROP TRIGGER IF EXISTS trg_ev_update_guard ON email_verification;
CREATE TRIGGER trg_ev_update_guard
    BEFORE UPDATE ON email_verification
    FOR EACH ROW
    EXECUTE FUNCTION fn_ev_update_guard();

-- Notes:
-- * The application must compare the submitted six-digit code with otp_hash
--   before setting used_at. A database trigger cannot verify plaintext that
--   is deliberately not stored.
-- * On a wrong code, the backend should execute:
--       UPDATE email_verification
--          SET attempts = attempts + 1
--        WHERE verification_id = :id;
-- * On a correct code, after hash comparison, it should set used_at = now().
-- * The resend trigger resets attempts only for a newly inserted code.
-- * The original seven-day NO_CONFIRMATION report rule is retained unchanged.
