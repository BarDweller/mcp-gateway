package org.ozzy.persistence;

import org.ozzy.model.AppAuthConfig;

public interface AppAuthConfigRepository {
    AppAuthConfig load();

    AppAuthConfig save(AppAuthConfig config);
}
