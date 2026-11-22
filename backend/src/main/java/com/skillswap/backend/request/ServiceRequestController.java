package com.skillswap.backend.request;

import com.skillswap.backend.request.dto.RequestDtos.ActionResult;
import com.skillswap.backend.request.dto.RequestDtos.CancelBody;
import com.skillswap.backend.request.dto.RequestDtos.ConfirmBody;
import com.skillswap.backend.request.dto.RequestDtos.CreateRequestBody;
import com.skillswap.backend.request.dto.RequestDtos.CreditTotal;
import com.skillswap.backend.request.dto.RequestDtos.RejectBody;
import com.skillswap.backend.request.dto.RequestDtos.RequestView;
import com.skillswap.backend.request.dto.RequestDtos.WaitingCount;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Volunteering flow API. All endpoints need a logged-in user
 *   POST /api/requests                  {offerId, message?}  request help
 *   POST /api/requests/{id}/accept                           volunteer
 *   POST /api/requests/{id}/reject      {reason?}            volunteer
 *   POST /api/requests/{id}/perform                          volunteer: Service performed
 *   POST /api/requests/{id}/confirm     {rating?}            student: confirm (+1 credit)
 *   POST /api/requests/{id}/cancel      {reason}             either side, before CONFIRMED
 *   GET  /api/requests/{id}                                  one request (only its two people)
 *   GET  /api/requests/sent                                  My activity, Sent tab
 *   GET  /api/requests/received                              My activity, Received tab
 *   GET  /api/requests/waiting-count                         My activity badge
 *   GET  /api/credits/me                                     total credits of the logged-in user
 */
@RestController
@RequestMapping("/api")
public class ServiceRequestController {

    private final ServiceRequestService service;
    private final CurrentUserService currentUser;

    public ServiceRequestController(ServiceRequestService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public ActionResult create(@Valid @RequestBody CreateRequestBody body) {
        Integer me = currentUser.currentUserId();
        ServiceRequest r = service.create(me, body.offerId(), body.message());
        return result("Your request was sent. The volunteer will answer soon.", me, r);
    }

    @PostMapping("/requests/{id}/accept")
    public ActionResult accept(@PathVariable Integer id) {
        Integer me = currentUser.currentUserId();
        return result("Request accepted.", me, service.accept(me, id));
    }

    @PostMapping("/requests/{id}/reject")
    public ActionResult reject(@PathVariable Integer id, @Valid @RequestBody(required = false) RejectBody body) {
        Integer me = currentUser.currentUserId();
        String reason = body == null ? null : body.reason();
        return result("Request rejected.", me, service.reject(me, id, reason));
    }

    @PostMapping("/requests/{id}/perform")
    public ActionResult perform(@PathVariable Integer id) {
        Integer me = currentUser.currentUserId();
        return result("Marked as performed. The student will now confirm it.", me, service.markPerformed(me, id));
    }

    @PostMapping("/requests/{id}/confirm")
    public ActionResult confirm(@PathVariable Integer id, @Valid @RequestBody(required = false) ConfirmBody body) {
        Integer me = currentUser.currentUserId();
        Integer rating = body == null ? null : body.rating();
        return result("Service confirmed. The volunteer earned 1 credit. Thank you!", me,
                service.confirm(me, id, rating));
    }

    @PostMapping("/requests/{id}/cancel")
    public ActionResult cancel(@PathVariable Integer id, @Valid @RequestBody CancelBody body) {
        Integer me = currentUser.currentUserId();
        return result("Request cancelled.", me, service.cancel(me, id, body.reason()));
    }

    @GetMapping("/requests/sent")
    public List<RequestView> sent() {
        return service.sent(currentUser.currentUserId());
    }

    @GetMapping("/requests/received")
    public List<RequestView> received() {
        return service.received(currentUser.currentUserId());
    }

    @GetMapping("/requests/waiting-count")
    public WaitingCount waitingCount() {
        return new WaitingCount(service.waitingCount(currentUser.currentUserId()));
    }

    @GetMapping("/requests/{id}")
    public RequestView get(@PathVariable Integer id) {
        return service.getView(currentUser.currentUserId(), id);
    }

    @GetMapping("/credits/me")
    public CreditTotal myCredits() {
        return new CreditTotal(service.totalCredits(currentUser.currentUserId()));
    }

    private ActionResult result(String message, Integer me, ServiceRequest r) {
        return new ActionResult(message, service.getView(me, r.getId()));
    }
}
