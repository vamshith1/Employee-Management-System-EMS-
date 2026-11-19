package com.ems.security;

import com.ems.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public AppUserPrincipal get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return principal;
    }

    /** The employee record linked to the current login; fails if the account has none. */
    public Long requireEmployeeId() {
        Long employeeId = get().getEmployeeId();
        if (employeeId == null) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "Your account is not linked to an employee record");
        }
        return employeeId;
    }
}
