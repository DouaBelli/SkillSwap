package com.skillswap.backend.offer;

import com.skillswap.backend.offer.dto.OfferDtos.OfferBody;
import com.skillswap.backend.offer.dto.OfferDtos.OfferView;
import com.skillswap.backend.request.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests of the offer rules. The DAO is a mock, so these tests run without a database. */
class OfferServiceTest {

    static final int VOLUNTEER = 1;   // owns offer 10 (ACTIVE) and offer 11 (HIDDEN)
    static final int OTHER = 2;       // another student
    static final int ADMIN = 99;      // not a STUDENT
    static final int OFFER = 10;
    static final int HIDDEN_OFFER = 11;

    private OfferDao dao;
    private OfferService service;

    private static OfferView offer(int id, String status, int ownerId, int me) {
        return new OfferView(id, "Excel basics", "Pivot tables", 1, "Tools", ownerId, "Vol", 3,
                "Computer Science", "Weekends", "Chat", status, null, ownerId == me);
    }

    private static OfferBody body(int categoryId) {
        return new OfferBody("  Excel basics  ", categoryId, "Pivot tables", "Weekends", "Chat");
    }

    @BeforeEach
    void setUp() {
        dao = mock(OfferDao.class);
        when(dao.isActiveStudent(anyInt())).thenAnswer(inv -> !Integer.valueOf(ADMIN).equals(inv.getArgument(0)));
        when(dao.categoryExists(anyInt())).thenAnswer(inv -> (Integer) inv.getArgument(0) <= 4);
        when(dao.findById(anyInt(), anyInt())).thenAnswer(inv -> {
            int me = inv.getArgument(0);
            int id = inv.getArgument(1);
            if (id == OFFER) return Optional.of(offer(OFFER, "ACTIVE", VOLUNTEER, me));
            if (id == HIDDEN_OFFER) return Optional.of(offer(HIDDEN_OFFER, "HIDDEN", VOLUNTEER, me));
            return Optional.empty();
        });
        when(dao.insert(anyInt(), any(OfferBody.class))).thenReturn(OFFER);
        service = new OfferService(dao);
    }

    private static HttpStatus statusOf(Runnable action) {
        return assertThrows(ApiException.class, action::run).getStatus();
    }

    @Test
    void student_canPostOffer() {
        var result = service.create(VOLUNTEER, body(1));
        assertEquals("Your offer was posted.", result.message());
        verify(dao).insert(anyInt(), any(OfferBody.class));
    }

    @Test
    void nonStudent_cannotPostOffer() {
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.create(ADMIN, body(1))));
        verify(dao, never()).insert(anyInt(), any(OfferBody.class));
    }

    @Test
    void unknownCategory_isRejected() {
        assertEquals(HttpStatus.BAD_REQUEST, statusOf(() -> service.create(VOLUNTEER, body(9))));
    }

    @Test
    void owner_canEditOwnActiveOffer() {
        service.update(VOLUNTEER, OFFER, body(2));
        verify(dao).update(OFFER, body(2));
    }

    @Test
    void otherStudent_cannotEditOrHide() {
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.update(OTHER, OFFER, body(1))));
        assertEquals(HttpStatus.FORBIDDEN, statusOf(() -> service.hide(OTHER, OFFER)));
        verify(dao, never()).hide(anyInt());
    }

    @Test
    void hiddenOffer_cannotBeEditedOrHiddenAgain() {
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.update(VOLUNTEER, HIDDEN_OFFER, body(1))));
        assertEquals(HttpStatus.CONFLICT, statusOf(() -> service.hide(VOLUNTEER, HIDDEN_OFFER)));
    }

    @Test
    void hiding_cancelsPendingRequests() {
        when(dao.cancelPendingRequests(OFFER)).thenReturn(2);
        var result = service.hide(VOLUNTEER, OFFER);
        verify(dao).hide(OFFER);
        verify(dao).cancelPendingRequests(OFFER);
        assertEquals("The offer is hidden. 2 pending request(s) were cancelled.", result.message());
    }

    @Test
    void hiddenOffer_isOnlyVisibleToItsOwner() {
        assertEquals(HIDDEN_OFFER, service.get(VOLUNTEER, HIDDEN_OFFER).offerId());
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.get(OTHER, HIDDEN_OFFER)));
    }

    @Test
    void unknownOffer_isNotFound() {
        assertEquals(HttpStatus.NOT_FOUND, statusOf(() -> service.get(OTHER, 404)));
    }
}
