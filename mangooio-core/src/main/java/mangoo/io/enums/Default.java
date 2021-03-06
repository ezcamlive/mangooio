package mangoo.io.enums;

/**
 * Default values
 *
 * @author svenkubiak
 *
 */
public enum Default {
    LANGUAGE("en"),
    DATA_DELIMITER("#"),
    DELIMITER("|"),
    FLASH_COOKIE_NAME("MANGOOIO-FLASH"),
    AUTH_COOKIE_NAME("MANGOOIO-AUTH"),
    COOKIE_EXPIRES("86400"),
    LOCALHOST("127.0.0.1"), //NOSONAR
    APPLICATION_HOST("127.0.0.1"), //NOSONAR
    JBCRYPT_ROUNDS("12"),
    SMTP_PORT("25"),
    SMTP_SSL("false"),
    APPLICATION_PORT("8080"),
    BUNDLE_NAME("translations/messages"),
    ASSETS_PATH("src/main/resources/files/assets/"),
    CONFIG_PATH("/src/main/resources/application.yaml"),
    FAKE_SMTP_PROTOCOL("smtp"),
    STYLESHEET_FOLDER("stylesheets"),
    JAVSCRIPT_FOLDER("javascripts"),
    CONFIGURATION_FILE("application.yaml"),
    DEFAULT_CONFIGURATION("default"),
    VERSION_PROPERTIES("version.properties"),
    CONTENT_TYPE("text/html; charset=UTF-8"),
    SCHEDULER_PREFIX("org.quartz."),
    APPLICATION_SECRET_MIN_LENGTH("16"),
    SERVER("Undertow"),
    CACHE_NAME("mangooio"),
    TEMPLATES_FOLDER("/templates/"),
    TEMPLATE_SUFFIX(".ftl"),
    AUTH_COOKIE_EXPIRES("3600"),
    SESSION_COOKIE_NAME("MANGOOIO-SESSION"),
    SPLITTER("&"),
    SEPERATOR(":"),
    NOSNIFF("nosniff"),
    SAMEORIGIN("SAMEORIGIN"),
    FILTER_METHOD_NAME("filter"),
    AUTHENTICITY_TOKEN("authenticityToken"),
    XSS_PROTECTION("1"),
    ROUTES_CLASS("conf.Routes"),
    FILES_FOLDER("files"),
    MODULE_CLASS("conf.Module"),
    VERSION("unknown"),
    LOGBACK_PROD_FILE("logback.prod.xml"),
    NUMBER_FORMAT("0.######"),
    EXCEPTION_TEMPLATE_NAME("exception.ftl"),
    DEFAULT_TEMPLATES_DIR("/defaults/");

    private final String value;

    Default (String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }

    public int toInt() {
        return Integer.valueOf(this.value);
    }

    public long toLong() {
        return Long.valueOf(this.value);
    }

    public boolean toBoolean() {
        return Boolean.valueOf(this.value);
    }
}