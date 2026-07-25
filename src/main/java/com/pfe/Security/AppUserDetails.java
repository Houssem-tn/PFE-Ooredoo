package com.pfe.Security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;
import java.util.Objects;

/**
 * {@link org.springframework.security.core.userdetails.UserDetails} enriched
 * with the account's current {@code tokenVersion}. Loaded once per request by
 * {@link UserDetailsServiceImpl} so both the REST filter and the WebSocket
 * interceptor can reject access/STOMP tokens whose embedded version no longer
 * matches the live account (the session-invalidation mechanism used after a
 * password reset) without a second database lookup.
 */
public class AppUserDetails extends User {

    private static final long serialVersionUID = 1L;

    // A plain serializable primitive (Spring's User is Serializable); kept
    // non-transient so it survives any UserDetails serialization intact.
    private final long tokenVersion;

    public AppUserDetails(String username,
                          String password,
                          boolean enabled,
                          Collection<? extends GrantedAuthority> authorities,
                          long tokenVersion) {
        super(username, password, enabled, true, true, true, authorities);
        this.tokenVersion = tokenVersion;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    // Overridden because this subclass adds identity-relevant state
    // (tokenVersion) on top of User's username-only equality.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AppUserDetails other)) {
            return false;
        }
        return super.equals(obj) && tokenVersion == other.tokenVersion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tokenVersion);
    }
}
