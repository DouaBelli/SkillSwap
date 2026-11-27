package com.skillswap.backend.offer;

import com.skillswap.backend.offer.dto.OfferDtos.OfferBody;
import com.skillswap.backend.offer.dto.OfferDtos.OfferView;
import com.skillswap.backend.offer.dto.OfferDtos.Option;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All SQL of the offers module, in one place.
 * Plain SQL (like RequestReadDao) so a small difference from the database schema
 * only breaks the call that uses it, and not the start of the whole application.
 *
 * Tables and columns used (check them against the schema):
 *   service_offer (offer_id, volunteer_id, category_id, title, description,
 *                  availability, help_method, status, created_at)
 *   category      (category_id, name)
 *   users, department, role, user_block (already used by RequestReadDao)
 */
@Repository
public class OfferDao {

    private static final String SELECT = """
            SELECT so.offer_id, so.title, so.description, so.availability, so.help_method,
                   so.status, so.created_at, so.category_id, c.name AS category_name,
                   so.volunteer_id, u.full_name AS volunteer_name, u.year_of_study,
                   d.name AS department_name
            FROM service_offer so
            JOIN category c ON c.category_id = so.category_id
            JOIN users u ON u.user_id = so.volunteer_id
            JOIN department d ON d.department_id = u.department_id
            """;

    private final JdbcTemplate jdbc;

    public OfferDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private RowMapper<OfferView> mapper(Integer me) {
        return (rs, i) -> new OfferView(
                rs.getInt("offer_id"), rs.getString("title"), rs.getString("description"),
                rs.getInt("category_id"), rs.getString("category_name"),
                rs.getInt("volunteer_id"), rs.getString("volunteer_name"), rs.getInt("year_of_study"),
                rs.getString("department_name"), rs.getString("availability"), rs.getString("help_method"),
                rs.getString("status"), rs.getObject("created_at", OffsetDateTime.class),
                me != null && me == rs.getInt("volunteer_id"));
    }

    /** Browse offers: active offers only, newest first, with the optional filters. */
    public List<OfferView> search(Integer me, Integer categoryId, Integer departmentId, String keyword) {
        StringBuilder sql = new StringBuilder(SELECT)
                .append(" WHERE so.status = 'ACTIVE'")
                .append(" AND NOT EXISTS (SELECT 1 FROM user_block b")
                .append("   WHERE b.user_id = so.volunteer_id AND b.unblocked_at IS NULL)");
        List<Object> args = new ArrayList<>();
        if (categoryId != null) {
            sql.append(" AND so.category_id = ?");
            args.add(categoryId);
        }
        if (departmentId != null) {
            sql.append(" AND u.department_id = ?");
            args.add(departmentId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + escapeLike(keyword.trim().toLowerCase()) + "%";
            sql.append(" AND (lower(so.title) LIKE ? OR lower(so.description) LIKE ?)");
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY so.created_at DESC, so.offer_id DESC LIMIT 100");
        return jdbc.query(sql.toString(), mapper(me), args.toArray());
    }

    public Optional<OfferView> findById(Integer me, Integer offerId) {
        return jdbc.query(SELECT + " WHERE so.offer_id = ?", mapper(me), offerId).stream().findFirst();
    }

    public Integer insert(Integer volunteerId, OfferBody b) {
        return jdbc.queryForObject("""
                INSERT INTO service_offer
                    (volunteer_id, category_id, title, description, availability, help_method, status)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                RETURNING offer_id
                """, Integer.class,
                volunteerId, b.categoryId(), b.title().trim(), b.description().trim(),
                b.availability().trim(), b.helpMethod().trim());
    }

    public void update(Integer offerId, OfferBody b) {
        jdbc.update("""
                UPDATE service_offer
                SET category_id = ?, title = ?, description = ?, availability = ?, help_method = ?
                WHERE offer_id = ?
                """, b.categoryId(), b.title().trim(), b.description().trim(),
                b.availability().trim(), b.helpMethod().trim(), offerId);
    }

    public void hide(Integer offerId) {
        jdbc.update("UPDATE service_offer SET status = 'HIDDEN' WHERE offer_id = ?", offerId);
    }

    /** When an offer is hidden, its PENDING requests are cancelled. Returns how many. */
    public int cancelPendingRequests(Integer offerId) {
        return jdbc.update("""
                UPDATE service_request
                SET status = 'CANCELLED', cancel_reason = 'Offer unavailable'
                WHERE offer_id = ? AND status = 'PENDING'
                """, offerId);
    }

    public List<Option> categories() {
        return jdbc.query("SELECT category_id, name FROM category ORDER BY category_id",
                (rs, i) -> new Option(rs.getInt("category_id"), rs.getString("name")));
    }

    public boolean categoryExists(Integer categoryId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM category WHERE category_id = ?)", Boolean.class, categoryId));
    }

    /** True if the user is a verified STUDENT with no active block (same rule as the volunteering flow). */
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

    /** % and _ typed by the user are searched as normal characters. */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
