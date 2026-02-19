package org.ozzy.service.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.ozzy.model.AppAuthConfig;
import org.ozzy.persistence.AppAuthConfigRepository;

@ApplicationScoped
public class AppAuthService {

    private static final Logger LOG = Logger.getLogger(AppAuthService.class);

    @Inject
    AppAuthConfigRepository repository;

    @Inject
    jakarta.enterprise.inject.Instance<ManagementAuthProvider> providers;

    public AppAuthConfig getConfig() {
        return repository.load();
    }

    public AppAuthConfig updateConfig(AppAuthConfig config) {
        return repository.save(config);
    }

    public boolean authenticate(String authorizationHeader) {
        AppAuthConfig config = repository.load();
        for (ManagementAuthProvider provider : providers) {
            if (provider.supports(config)) {
                return provider.authenticate(config, authorizationHeader);
            }
        }
        LOG.warnf("No management auth provider available for auth type %s", config.getAuthType());
        return false;
    }
}
