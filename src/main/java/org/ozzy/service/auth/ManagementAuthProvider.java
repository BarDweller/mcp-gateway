package org.ozzy.service.auth;

import org.ozzy.model.AppAuthConfig;

public interface ManagementAuthProvider {
    boolean supports(AppAuthConfig config);

    boolean authenticate(AppAuthConfig config, String authorizationHeader);
}
