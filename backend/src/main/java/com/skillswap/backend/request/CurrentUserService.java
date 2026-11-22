package com.skillswap.backend.request;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
  Finds the id of the logged-in user from Spring Security.
  principal's name is either the user id  or the university email.
 * If the login is set up differently, this is the only class to change.
 */
@Service
public class CurrentUserService {

    private final RequestReadDao dao;

    public CurrentUserService(RequestReadDao dao) {
        this.dao = dao;
    }

    public Integer currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw ApiException.unauthorized("Please log in first.");
        }
        String name = auth.getName();
        if (name != null && name.matches("\\d+")) {
            return Integer.valueOf(name);
        }
        return dao.findUserIdByEmail(name)
                .orElseThrow(() -> ApiException.unauthorized("Please log in first."));
    }
}
