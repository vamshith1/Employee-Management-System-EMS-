package com.ems.security;

import com.ems.domain.Role;
import com.ems.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated user as seen by Spring Security. Carries ids so controllers avoid extra lookups. */
public class AppUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final Role role;
    private final Long employeeId;
    private final boolean enabled;

    public AppUserPrincipal(Long id, String username, String password, Role role, Long employeeId, boolean enabled) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.role = role;
        this.employeeId = employeeId;
        this.enabled = enabled;
    }

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(user.getId(), user.getUsername(), user.getPassword(), user.getRole(),
                user.getEmployee() == null ? null : user.getEmployee().getId(), user.isEnabled());
    }

    public Long getId() { return id; }
    public Role getRole() { return role; }
    public Long getEmployeeId() { return employeeId; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isEnabled() { return enabled; }
}
