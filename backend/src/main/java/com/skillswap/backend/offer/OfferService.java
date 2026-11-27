package com.skillswap.backend.offer;

import com.skillswap.backend.offer.dto.OfferDtos.OfferBody;
import com.skillswap.backend.offer.dto.OfferDtos.OfferResult;
import com.skillswap.backend.offer.dto.OfferDtos.OfferView;
import com.skillswap.backend.offer.dto.OfferDtos.Option;
import com.skillswap.backend.request.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Rules of the offers module:
 *  - only a verified, unblocked STUDENT can post an offer
 *  - only the owner can edit or hide an offer, and only while it is ACTIVE
 *  - hiding an offer cancels its PENDING requests (ACCEPTED and PERFORMED ones can still be finished)
 *  - hidden offers are only visible to their owner
 */
@Service
public class OfferService {

    private final OfferDao dao;

    public OfferService(OfferDao dao) {
        this.dao = dao;
    }

    public List<OfferView> search(Integer me, Integer categoryId, Integer departmentId, String keyword) {
        return dao.search(me, categoryId, departmentId, keyword);
    }

    public OfferView get(Integer me, Integer offerId) {
        OfferView offer = dao.findById(me, offerId)
                .orElseThrow(() -> ApiException.notFound("This offer was not found."));
        if (!"ACTIVE".equals(offer.status()) && !offer.mine()) {
            throw ApiException.notFound("This offer was not found.");
        }
        return offer;
    }

    public List<Option> categories() {
        return dao.categories();
    }

    @Transactional
    public OfferResult create(Integer me, OfferBody body) {
        if (!dao.isActiveStudent(me)) {
            throw ApiException.forbidden("Only verified students can post offers.");
        }
        checkCategory(body);
        Integer id = dao.insert(me, body);
        return new OfferResult("Your offer was posted.", get(me, id));
    }

    @Transactional
    public OfferResult update(Integer me, Integer offerId, OfferBody body) {
        OfferView offer = ownActiveOffer(me, offerId, "change");
        checkCategory(body);
        dao.update(offer.offerId(), body);
        return new OfferResult("Your offer was saved.", get(me, offerId));
    }

    @Transactional
    public OfferResult hide(Integer me, Integer offerId) {
        OfferView offer = ownActiveOffer(me, offerId, "hide");
        dao.hide(offer.offerId());
        int cancelled = dao.cancelPendingRequests(offer.offerId());
        String message = cancelled == 0
                ? "The offer is hidden."
                : "The offer is hidden. " + cancelled + " pending request(s) were cancelled.";
        return new OfferResult(message, get(me, offerId));
    }

    private OfferView ownActiveOffer(Integer me, Integer offerId, String action) {
        OfferView offer = dao.findById(me, offerId)
                .orElseThrow(() -> ApiException.notFound("This offer was not found."));
        if (!offer.mine()) {
            throw ApiException.forbidden("You can only " + action + " your own offers.");
        }
        if (!"ACTIVE".equals(offer.status())) {
            throw ApiException.conflict("This offer is hidden, so it cannot be changed.");
        }
        return offer;
    }

    private void checkCategory(OfferBody body) {
        if (!dao.categoryExists(body.categoryId())) {
            throw ApiException.badRequest("Choose a category from the list.");
        }
    }
}
