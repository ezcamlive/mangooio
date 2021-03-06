package mangoo.io.mail;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.MultiPartEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import mangoo.io.configuration.Config;
import mangoo.io.enums.Default;
import mangoo.io.enums.Key;

/**
 *
 * @author svenkubiak
 *
 */
@Singleton
public class Mailer {
    private static final Logger LOG = LoggerFactory.getLogger(Mailer.class);
    private DefaultAuthenticator defaultAuthenticator;
    private Config config;
    private String host;
    private int port;
    private boolean ssl;

    @Inject
    public Mailer(Config config) {
        this.config = config;

        this.host = this.config.getString(Key.SMTP_HOST, Default.LOCALHOST.toString());
        this.port = this.config.getInt(Key.SMTP_PORT, Default.SMTP_PORT.toInt());
        this.ssl = this.config.getBoolean(Key.SMTP_SSL, Default.SMTP_SSL.toBoolean());

        String username = this.config.getString(Key.SMTP_USERNAME, null);
        String password = this.config.getString(Key.SMTP_PASSWORD, null);
        if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            this.defaultAuthenticator = new DefaultAuthenticator(username, password);
        }
    }

    /**
     * Sends a plain text message using the SMTP configuration in application.conf
     *
     * For more information @see <a href="https://commons.apache.org/proper/commons-email/userguide.html">https://commons.apache.org/proper/commons-email/userguide.html</a>
     *
     * @param email The email to send
     */
    public void send(Email email) {
        email.setHostName(this.host);
        email.setSmtpPort(this.port);
        email.setAuthenticator(this.defaultAuthenticator);
        email.setSSLOnConnect(this.ssl);

        try {
            email.send();
        } catch (EmailException e) {
            LOG.error("Failed to send SimpleEmail", e);
        }
    }

    /**
     * Sends a multi part email using the SMTP configuration in application.conf
     *
     * For more information @see <a href="https://commons.apache.org/proper/commons-email/userguide.html">https://commons.apache.org/proper/commons-email/userguide.html</a>
     *
     * @param multiPartEmail The multi part email to send
     */
    public void send(MultiPartEmail multiPartEmail) {
        multiPartEmail.setHostName(this.host);
        multiPartEmail.setSmtpPort(this.port);
        multiPartEmail.setAuthenticator(this.defaultAuthenticator);
        multiPartEmail.setSSLOnConnect(this.ssl);

        try {
            multiPartEmail.send();
        } catch (EmailException e) {
            LOG.error("Failed to send MultiPartEmail", e);
        }
    }

    /**
     * Sends a HTML email using the SMTP configuration in application.conf
     *
     * For more information @see <a href="https://commons.apache.org/proper/commons-email/userguide.html">https://commons.apache.org/proper/commons-email/userguide.html</a>
     *
     * @param htmlEmail The HTML email to send
     */
    public void send(HtmlEmail htmlEmail) {
        htmlEmail.setHostName(this.host);
        htmlEmail.setSmtpPort(this.port);
        htmlEmail.setAuthenticator(this.defaultAuthenticator);
        htmlEmail.setSSLOnConnect(this.ssl);

        try {
            htmlEmail.send();
        } catch (EmailException e) {
            LOG.error("Failed to send HtmlEmail", e);
        }
    }
}