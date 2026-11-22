package com.skillswap.backend.request;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;


@Repository
public class RequestReadDao {

    /** The offer fields the volunteering flow needs. */
    public record OfferInfo(Integer offerId, Integer volunteerId, String status, String title) {
        public boolean isActive() {
            return "ACTIVE".equals(status);
        }
    }

    /** A request joined with its offer and the two people involved. */
    public record RequestRow(
            Integer requestId, Integer offerId, String offerTitle,
            Integer volunteerId, String volunteerName,
            Integer requesterId, String requesterName,
            String message, RequestStatus status, Integer rating,
            String rejectReason, String cancelReason,
            OffsetDateTime createdAt, OffsetDateTime acceptedAt,
            OffsetDateTime performedAt, OffsetDateTime confirmedAt) {
    }

    private static final String ROW_SELECT = """
            SELECT sr.request_id, sr.offer_id, so.title AS offer_title,
                   so.volunteer_id, v.full_name AS volunteer_name,
                   sr.requester_id, u.full_name AS requester_name,
                   sr.message, sr.status, sr.rating, sr.reject_reason, sr.cancel_reason,
                   sr.created_at, sr.accepted_at, sr.performed_at, sr.confirmed_at
            FROM service_request sr
            JOIN service_offer so ON so.offer_id = sr.offer_id
            JOIN users v ON v.user_id = so.volunteer_id
            JOIN users u ON u.user_id = sr.requester_id
            """;

    private final JdbcTemplate jdbc;

    public RequestReadDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<OfferInfo> findOffer(Integer offerId) {
        List<OfferInfo> rows = jdbc.query(
                "SELECT offer_id, volunteer_id, status, title FROM service_offer WHERE offer_id = ?",
                (rs, i) -> new OfferInfo(rs.getInt("offer_id"), rs.getInt("volunteer_id"),
                        rs.getString("status"), rs.getString("title")),
                offerId);
        return rows.stream().findFirst();
    }

    public Optional<Integer> findUserIdByEmail(String email) {
        List<Integer> ids = jdbc.queryForList(
                "SELECT user_id FROM users WHERE lower(university_email) = lower(?)", Integer.class, email);
        return ids.stream().findFirst();
    }

    /** True if the user is a verified STUDENT with no active block. */
    public boolean isActiveStudent(Integer userId) {
        Boolean ok = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM users u
                    JOIN role r ON r.role_id = u.role_id
                    WHERE u.user_id = ?
                      AND r.role_name = 'STUDENT'
                      AND u.is_verified
                      AND NOT EXISTS (SELECT 1 FROM user_block b
                                      WHERE b.user_id = u.user_id AND b.unblocked_at IS NULL))
                """, Boolean.class, userId);
        return Boolean.TRUE.equals(ok);
    }

    public Optional<RequestRow> findRow(Integer requestId) {
        return jdbc.query(ROW_SELECT + " WHERE sr.request_id = ?", this::mapRow, requestId)
                .stream().findFirst();
    }

    /** Sent tab: requests this user made, newest first. */
    public List<RequestRow> findSent(Integer userId) {
        return jdbc.query(ROW_SELECT + " WHERE sr.requester_id = ? ORDER BY sr.created_at DESC",
                this::mapRow, userId);
    }

    /** Received tab: requests on this user's offers, newest first. */
    public List<RequestRow> findReceived(Integer userId) {
        return jdbc.query(ROW_SELECT + " WHERE so.volunteer_id = ? ORDER BY sr.created_at DESC",
                this::mapRow, userId);
    }

    /**
     * Requests waiting for this user: PENDING ones on their offers (to accept or reject)
     * plus PERFORMED ones they sent (to confirm).
     */
    public long countWaiting(Integer userId) {
        Long n = jdbc.queryForObject("""
                SELECT
                  (SELECT count(*) FROM service_request sr
                   JOIN service_offer so ON so.offer_id = sr.offer_id
                   WHERE so.volunteer_id = ? AND sr.status = 'PENDING')
                + (SELECT count(*) FROM service_request sr
                   WHERE sr.requester_id = ? AND sr.status = 'PERFORMED')
                """, Long.class, userId, userId);
        return n == null ? 0 : n;
    }

    private RequestRow mapRow(ResultSet rs, int i) throws SQLException {
        int rating = rs.getInt("rating");
        Integer ratingOrNull = rs.wasNull() ? null : rating;
        return new RequestRow(
                rs.getInt("request_id"), rs.getInt("offer_id"), rs.getString("offer_title"),
                rs.getInt("volunteer_id"), rs.getString("volunteer_name"),
                rs.getInt("requester_id"), rs.getString("requester_name"),
                rs.getString("message"), RequestStatus.valueOf(rs.getString("status")), ratingOrNull,
                rs.getString("reject_reason"), rs.getString("cancel_reason"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("accepted_at", OffsetDateTime.class),
                rs.getObject("performed_at", OffsetDateTime.class),
                rs.getObject("confirmed_at", OffsetDateTime.class));
    }
}
