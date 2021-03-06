package mangoo.io.authentication;

import java.time.LocalDateTime;

import org.apache.commons.lang3.StringUtils;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import mangoo.io.configuration.Config;
import mangoo.io.enums.Default;

/**
 *
 * @author svenkubiak
 *
 */
public class Authentication {
    private static final Logger LOG = LoggerFactory.getLogger(Authentication.class);
    private Config config;
    private LocalDateTime expires;
    private String authenticatedUser;
    private boolean remember;
    private boolean loggedOut;

    @Inject
    public Authentication(Config config) {
        this.config = config;
        this.expires = LocalDateTime.now().plusSeconds(this.config.getAuthenticationExpires());
    }

    public Authentication(Config config, String authenticatedUser, LocalDateTime expires) {
        this.config = config;
        this.expires = expires;
        this.authenticatedUser = authenticatedUser;
    }

    public String getAuthenticatedUser() {
        return this.authenticatedUser;
    }

    public LocalDateTime getExpires() {
        return expires;
    }

    public void setExpires(LocalDateTime localDateTime) {
        this.expires = localDateTime;
    }

    public boolean isLogout() {
        return loggedOut;
    }

    public boolean isRemember() {
        return remember;
    }

    /**
     * Hashes a given clear text password using JBCrypt
     *
     * @param password The clear text password
     * @return The hashed password
     */
    public String getHashedPassword(String password) {
        Preconditions.checkNotNull(password, "Password is required for getHashedPassword");

        return BCrypt.hashpw(password, BCrypt.gensalt(Default.JBCRYPT_ROUNDS.toInt()));
    }

    /**
     * Creates a hashed value of a given clear text password and checks if the
     * value matches a given, already hashed password
     *
     * @param password The clear text password
     * @param hash The previously hashed password to check
     * @return True if the new hashed password matches the hash, false otherwise
     */
    public boolean authenticate(String password, String hash) {
        Preconditions.checkNotNull(password, "Password is required for authenticate");
        Preconditions.checkNotNull(password, "Hashed password is required for authenticate");

        boolean authenticated = false;
        try {
            authenticated = BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            LOG.error("Failed to check password against hash", e);
        }

        return authenticated;
    }

    /**
     * Performs a logout of the currently authenticated user
     */
    public void logout() {
        this.loggedOut = true;
    }

    /**
     * Performs a login for a given user name
     *
     * @param username The user name to login
     * @param remember If true, the user will stay logged in for (default) 2 weeks
     */
    public void login(String username, boolean remember) {
        Preconditions.checkNotNull(username, "Username is required for login");

        if (StringUtils.isNotBlank(StringUtils.trimToNull(username))) {
            this.authenticatedUser = username;
            this.remember = remember;
        }
    }

    /**
     * Checks if the authentication contains an authenticated user
     *
     * @return True if authentication contains an authenticated user, false otherwise
     */
    public boolean hasAuthenticatedUser() {
        return StringUtils.isNotBlank(this.authenticatedUser);
    }

    /**
     * Checks if the given user name is authenticated
     *
     * @param username The user name to check
     * @return True if the given user name is authenticates
     */
    public boolean isAuthenticated(String username) {
        Preconditions.checkNotNull(username, "Username is required for isAuthenticated");

        return username.equals(this.authenticatedUser);
    }
}