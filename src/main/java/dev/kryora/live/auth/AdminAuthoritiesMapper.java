package dev.kryora.live.auth;

import dev.kryora.live.config.KryoraProperties;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;

/**
 * At login, gives ROLE_ADMIN to accounts listed in ADMIN_EMAILS.
 * The security config then protects /api/admin/** with hasRole("ADMIN").
 */
@Component
public class AdminAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private final KryoraProperties properties;

    public AdminAuthoritiesMapper(KryoraProperties properties) {
        this.properties = properties;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        Set<GrantedAuthority> mapped = new HashSet<>(authorities);
        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidc
                    && Boolean.TRUE.equals(oidc.getIdToken().getEmailVerified())
                    && properties.isAdmin(oidc.getIdToken().getEmail())) {
                mapped.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            }
        }
        return mapped;
    }
}
