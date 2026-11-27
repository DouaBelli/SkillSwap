package com.skillswap.backend.offer;

import com.skillswap.backend.offer.dto.OfferDtos.OfferBody;
import com.skillswap.backend.offer.dto.OfferDtos.OfferResult;
import com.skillswap.backend.offer.dto.OfferDtos.OfferView;
import com.skillswap.backend.offer.dto.OfferDtos.Option;
import com.skillswap.backend.request.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Offers API. All endpoints need a logged-in user.
 *   GET  /api/offers?categoryId=&departmentId=&keyword=   Browse offers (newest first)
 *   GET  /api/offers/{id}                                  Offer details
 *   POST /api/offers                                       Post offer
 *   PUT  /api/offers/{id}                                  Edit own offer
 *   POST /api/offers/{id}/hide                             Hide own offer
 *   GET  /api/categories                                   the category buttons and dropdown
 * (GET /api/departments already exists, it is used by the department filter.)
 */
@RestController
@RequestMapping("/api")
public class OfferController {

    private final OfferService service;
    private final CurrentUserService currentUser;

    public OfferController(OfferService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/offers")
    public List<OfferView> search(@RequestParam(required = false) Integer categoryId,
                                  @RequestParam(required = false) Integer departmentId,
                                  @RequestParam(required = false) String keyword) {
        return service.search(currentUser.currentUserId(), categoryId, departmentId, keyword);
    }

    @GetMapping("/offers/{id}")
    public OfferView get(@PathVariable Integer id) {
        return service.get(currentUser.currentUserId(), id);
    }

    @PostMapping("/offers")
    @ResponseStatus(HttpStatus.CREATED)
    public OfferResult create(@Valid @RequestBody OfferBody body) {
        return service.create(currentUser.currentUserId(), body);
    }

    @PutMapping("/offers/{id}")
    public OfferResult update(@PathVariable Integer id, @Valid @RequestBody OfferBody body) {
        return service.update(currentUser.currentUserId(), id, body);
    }

    @PostMapping("/offers/{id}/hide")
    public OfferResult hide(@PathVariable Integer id) {
        return service.hide(currentUser.currentUserId(), id);
    }

    @GetMapping("/categories")
    public List<Option> categories() {
        return service.categories();
    }
}
