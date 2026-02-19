package org.ozzy.persistence.impl;

import java.util.Properties;

import jakarta.enterprise.context.ApplicationScoped;

import org.ozzy.model.AppAuthConfig;
import org.ozzy.persistence.AppAuthConfigRepository;

@ApplicationScoped
public class PropertiesAppAuthConfigRepository extends PropertiesRepositoryBase implements AppAuthConfigRepository {

    @Override
    public AppAuthConfig load() {
        Properties properties = loadProperties();
        AppAuthConfig config = new AppAuthConfig();
        String authType = properties.getProperty(APP_AUTH_TYPE_KEY, DEFAULT_APP_AUTH_TYPE);
        String username = properties.getProperty(APP_AUTH_USERNAME_KEY, DEFAULT_APP_AUTH_USERNAME);
        String encodedPassword = properties.getProperty(APP_AUTH_PASSWORD_KEY, encodeSecret(DEFAULT_APP_AUTH_PASSWORD));

        config.setAuthType(authType);
        config.setUsername(username);
        config.setPassword(decodeSecret(encodedPassword));

        if (properties.getProperty(APP_AUTH_TYPE_KEY) == null
                || properties.getProperty(APP_AUTH_USERNAME_KEY) == null
                || properties.getProperty(APP_AUTH_PASSWORD_KEY) == null) {
            save(config);
        }

        return config;
    }

    @Override
    public AppAuthConfig save(AppAuthConfig config) {
        Properties properties = loadProperties();
        String authType = config.getAuthType() == null ? DEFAULT_APP_AUTH_TYPE : config.getAuthType();
        String username = config.getUsername() == null ? DEFAULT_APP_AUTH_USERNAME : config.getUsername();
        String password = config.getPassword() == null ? DEFAULT_APP_AUTH_PASSWORD : config.getPassword();

        properties.setProperty(APP_AUTH_TYPE_KEY, authType);
        properties.setProperty(APP_AUTH_USERNAME_KEY, username);
        properties.setProperty(APP_AUTH_PASSWORD_KEY, encodeSecret(password));

        saveProperties(properties);

        AppAuthConfig saved = new AppAuthConfig();
        saved.setAuthType(authType);
        saved.setUsername(username);
        saved.setPassword(password);
        return saved;
    }
}
