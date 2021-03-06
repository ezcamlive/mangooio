package mangoo.io.routing.handlers;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.boon.json.JsonFactory;
import org.boon.json.ObjectMapper;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.inject.Injector;

import freemarker.template.TemplateException;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;
import io.undertow.server.handlers.form.FormData;
import io.undertow.server.handlers.form.FormDataParser;
import io.undertow.server.handlers.form.FormParserFactory;
import io.undertow.util.HeaderValues;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import io.undertow.util.StatusCodes;
import mangoo.io.annotations.FilterWith;
import mangoo.io.authentication.Authentication;
import mangoo.io.configuration.Config;
import mangoo.io.core.Application;
import mangoo.io.crypto.Crypto;
import mangoo.io.enums.ContentType;
import mangoo.io.enums.Default;
import mangoo.io.enums.Header;
import mangoo.io.enums.Key;
import mangoo.io.i18n.Messages;
import mangoo.io.interfaces.MangooGlobalFilter;
import mangoo.io.routing.Response;
import mangoo.io.routing.bindings.Body;
import mangoo.io.routing.bindings.Exchange;
import mangoo.io.routing.bindings.Flash;
import mangoo.io.routing.bindings.Form;
import mangoo.io.routing.bindings.Session;
import mangoo.io.templating.TemplateEngine;

public class RequestHandler implements HttpHandler {
    private static final int AUTH_PREFIX_LENGTH = 2;
    private static final int TOKEN_LENGTH = 16;
    private static final int INDEX_2 = 2;
    private static final int INDEX_1 = 1;
    private static final int INDEX_0 = 0;
    private static final int SESSION_PREFIX_LENGTH = 3;
    private int parameterCount;
    private Class<?> controllerClass;
    private String controllerMethod;
    private Object controller;
    private Map<String, Class<?>> parameters;
    private Method method;
    private ObjectMapper mapper;
    private Authentication authentication;
    private Session session;
    private Flash flash;
    private Form form;
    private Config config;
    private Injector injector;
    private Exchange exchange;
    private boolean globalFilter;

    public RequestHandler(Class<?> controllerClass, String controllerMethod) {
        this.injector = Application.getInjector();
        this.controllerClass = controllerClass;
        this.controllerMethod = controllerMethod;
        this.controller = this.injector.getInstance(this.controllerClass);
        this.parameters = getMethodParameters();
        this.parameterCount = this.parameters.size();
        this.config = this.injector.getInstance(Config.class);
        this.globalFilter = this.injector.getAllBindings().containsKey(com.google.inject.Key.get(MangooGlobalFilter.class));
        this.mapper = JsonFactory.create();
    }

    @Override
    @SuppressWarnings("all")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (this.method == null) {
            this.method = this.controller.getClass().getMethod(this.controllerMethod, parameters.values().toArray(new Class[0]));
        }
        this.exchange = null;
        this.session = null;
        this.form = null;
        this.authentication = null;

        setLocale(exchange);
        getSession(exchange);
        getAuthentication(exchange);
        getFlash(exchange);
        getForm(exchange);

        boolean continueAfterFilter = executeFilter(exchange);
        if (continueAfterFilter) {
            Response response = getResponse(exchange);

            setSession(exchange);
            setFlash(exchange);
            setAuthentication(exchange);

            if (response.isRedirect()) {
                exchange.setResponseCode(StatusCodes.FOUND);
                exchange.getResponseHeaders().put(Headers.LOCATION, response.getRedirectTo());
                exchange.getResponseHeaders().put(Headers.SERVER, Default.SERVER.toString());
                exchange.endExchange();
            } else if (response.isBinary()) {
                exchange.dispatch(exchange.getDispatchExecutor(), new BinaryHandler(response));
            } else {
                exchange.setResponseCode(response.getStatusCode());
                exchange.getResponseHeaders().put(Header.X_XSS_PPROTECTION.toHttpString(), 1);
                exchange.getResponseHeaders().put(Header.X_CONTENT_TYPE_OPTIONS.toHttpString(), Default.NOSNIFF.toString());
                exchange.getResponseHeaders().put(Header.X_FRAME_OPTIONS.toHttpString(), Default.SAMEORIGIN.toString());
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, response.getContentType() + "; charset=" + response.getCharset());
                exchange.getResponseHeaders().put(Headers.SERVER, Default.SERVER.toString());
                response.getHeaders().forEach((key, value) -> exchange.getResponseHeaders().add(key, value));
                exchange.getResponseSender().send(response.getBody());
            }
        }
    }

    private void setLocale(HttpServerExchange exchange) {
        HeaderValues headerValues = exchange.getRequestHeaders().get(Headers.ACCEPT_LANGUAGE_STRING);
        if (headerValues != null && headerValues.getFirst() != null) {
            Iterable<String> split = Splitter.on(",").trimResults().split(headerValues.getFirst());
            if (split != null) {
                String language = split.iterator().next();
                if (StringUtils.isBlank(language)) {
                    language = this.config.getString(Key.APPLICATION_LANGUAGE, Default.LANGUAGE.toString());
                }

                Locale.setDefault(Locale.forLanguageTag(language.substring(0, 1)));
                Application.getInjector().getInstance(Messages.class).reload();
            }
        }
    }

    private boolean executeFilter(HttpServerExchange exchange) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        boolean continueAfterFilter = executeGlobalFilter(exchange);

        if (continueAfterFilter) {
            continueAfterFilter = executeFilter(this.controllerClass.getAnnotations(), exchange);
        }

        if (continueAfterFilter) {
            continueAfterFilter = executeFilter(this.method.getAnnotations(), exchange);
        }

        return continueAfterFilter;
    }

    private boolean executeGlobalFilter(HttpServerExchange exchange) {
        if (this.globalFilter) {
            MangooGlobalFilter mangooGlobalFilter = this.injector.getInstance(MangooGlobalFilter.class);
            return mangooGlobalFilter.filter(getExchange(exchange));
        }

        return true;
    }

    private Exchange getExchange(HttpServerExchange httpServerExchange) {
        if (this.exchange == null) {
            String authenticityToken = getRequestParameters(httpServerExchange).get(Default.AUTHENTICITY_TOKEN.toString());
            if (StringUtils.isBlank(authenticityToken)) {
                authenticityToken = this.form.get(Default.AUTHENTICITY_TOKEN.toString());
            }

            this.exchange = new Exchange(httpServerExchange, this.session, authenticityToken, this.authentication);
        }

        return this.exchange;
    }

    private boolean executeFilter(Annotation[] annotations, HttpServerExchange exchange) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        FilterWith filterWith = null;
        boolean continueAfterFilter = true;

        for (Annotation annotation : annotations) {
            if (annotation.annotationType().equals(FilterWith.class)) {
                filterWith = (FilterWith) annotation;
                for (Class<?> clazz : filterWith.value()) {
                    if (continueAfterFilter) {
                        Method classMethod = clazz.getMethod(Default.FILTER_METHOD_NAME.toString(), Exchange.class);
                        continueAfterFilter = (boolean) classMethod.invoke(this.injector.getInstance(clazz), getExchange(exchange));
                    } else {
                        return false;
                    }
                }
            }
        }

        return continueAfterFilter;
    }

    private Response getResponse(HttpServerExchange exchange) throws IllegalAccessException, InvocationTargetException, IOException, TemplateException {
        Response response;

        if (this.parameters.isEmpty()) {
            response = (Response) this.method.invoke(this.controller);
            response.andTemplate(this.method.getName());
        } else {
            Object [] convertedParameters = getConvertedParameters(exchange);

            response = (Response) this.method.invoke(this.controller, convertedParameters);
            response.andTemplate(this.method.getName());
        }

        if (!response.isRendered()) {
            if (response.getContent() != null && this.exchange != null && this.exchange.getContent() != null) {
                response.getContent().putAll(this.exchange.getContent());
            }

            TemplateEngine templateEngine = this.injector.getInstance(TemplateEngine.class);
            response.andBody(templateEngine.render(this.flash, this.session, this.form, this.injector.getInstance(Messages.class), this.controllerClass.getSimpleName(), response.getTemplate(), response.getContent()));
        }

        return response;
    }

    private Session getSession(HttpServerExchange exchange) {
        Session requestSession = null;
        Cookie cookie = exchange.getRequestCookies().get(this.config.getSessionCookieName());
        if (cookie != null) {
            String cookieValue = cookie.getValue();
            if (StringUtils.isNotBlank(cookieValue)) {
                if (this.config.getBoolean(Key.COOKIE_ENCRYPTION, false)) {
                    Crypto crypto = this.injector.getInstance(Crypto.class);
                    cookieValue = crypto.decrypt(cookieValue);
                }

                String sign = null;
                String expires = null;
                String authenticityToken = null;
                String prefix = StringUtils.substringBefore(cookieValue, Default.DATA_DELIMITER.toString());
                if (StringUtils.isNotBlank(prefix)) {
                    String [] prefixes = prefix.split("\\" + Default.DELIMITER.toString());
                    if (prefixes != null && prefixes.length == SESSION_PREFIX_LENGTH) {
                        sign = prefixes [INDEX_0];
                        authenticityToken = prefixes [INDEX_1];
                        expires = prefixes [INDEX_2];
                    }
                }

                if (StringUtils.isNotBlank(sign) && StringUtils.isNotBlank(expires) && StringUtils.isNotBlank(authenticityToken)) {
                    String data = cookieValue.substring(cookieValue.indexOf(Default.DATA_DELIMITER.toString()) + 1, cookieValue.length());

                    LocalDateTime expiresDate = LocalDateTime.parse(expires);
                    if (LocalDateTime.now().isBefore(expiresDate) && DigestUtils.sha512Hex(data + authenticityToken + expires + this.config.getApplicationSecret()).equals(sign)) {
                        Map<String, String> sessionValues = new HashMap<String, String>();
                        if (StringUtils.isNotEmpty(data)) {
                            for (Map.Entry<String, String> entry : Splitter.on(Default.SPLITTER.toString()).withKeyValueSeparator(Default.SEPERATOR.toString()).split(data).entrySet()) {
                                sessionValues.put(entry.getKey(), entry.getValue());
                            }
                        }
                        requestSession = new Session(sessionValues);
                        requestSession.setAuthenticityToken(authenticityToken);
                        requestSession.setExpires(expiresDate);
                    }
                }
            }
        }

        if (requestSession == null) {
            requestSession = new Session();
            requestSession.setAuthenticityToken(RandomStringUtils.randomAlphanumeric(TOKEN_LENGTH));
            requestSession.setExpires(LocalDateTime.now().plusSeconds(this.config.getSessionExpires()));
        }

        this.session = requestSession;

        return requestSession;
    }

    private void setSession(HttpServerExchange exchange) {
        if (this.session != null && this.session.hasChanges()) {
            String values = Joiner.on(Default.SPLITTER.toString()).withKeyValueSeparator(Default.SEPERATOR.toString()).join(this.session.getValues());

            String sign = DigestUtils.sha512Hex(values + this.session.getAuthenticityToken() + this.session.getExpires() + config.getApplicationSecret());
            String value = sign + Default.DELIMITER.toString() + this.session.getAuthenticityToken() + Default.DELIMITER.toString() + this.session.getExpires() + Default.DATA_DELIMITER.toString() + values;

            if (this.config.getBoolean(Key.COOKIE_ENCRYPTION, false)) {
                Crypto crypto = this.injector.getInstance(Crypto.class);
                value = crypto.encrypt(value);
            }

            Cookie cookie = new CookieImpl(config.getString(Key.COOKIE_NAME), value)
                    .setHttpOnly(true)
                    .setPath("/")
                    .setExpires(Date.from(this.session.getExpires().atZone(ZoneId.systemDefault()).toInstant()));

            exchange.setResponseCookie(cookie);
        }
    }

    private Authentication getAuthentication(HttpServerExchange exchange) {
        Authentication requestAuthentication = null;
        Cookie cookie = exchange.getRequestCookies().get(this.config.getAuthenticationCookieName());
        if (cookie != null) {
            String cookieValue = cookie.getValue();
            if (StringUtils.isNotBlank(cookieValue)) {
                if (this.config.getBoolean(Key.AUTH_COOKIE_ENCRYPT.toString(), false)) {
                    Crypto crypto = this.injector.getInstance(Crypto.class);
                    cookieValue = crypto.decrypt(cookieValue);
                }

                String sign = null;
                String expires = null;
                String prefix = StringUtils.substringBefore(cookieValue, Default.DATA_DELIMITER.toString());
                if (StringUtils.isNotBlank(prefix)) {
                    String [] prefixes = prefix.split("\\" + Default.DELIMITER.toString());
                    if (prefixes != null && prefixes.length == AUTH_PREFIX_LENGTH) {
                        sign = prefixes [INDEX_0];
                        expires = prefixes [INDEX_1];
                    }
                }

                if (StringUtils.isNotBlank(sign) && StringUtils.isNotBlank(expires)) {
                    String data = cookieValue.substring(cookieValue.indexOf(Default.DATA_DELIMITER.toString()) + 1, cookieValue.length());
                    LocalDateTime expiresDate = LocalDateTime.parse(expires);
                    if (LocalDateTime.now().isBefore(expiresDate) && DigestUtils.sha512Hex(data + expires + this.config.getApplicationSecret()).equals(sign)) {
                        requestAuthentication = new Authentication(this.config, data, expiresDate);
                    }
                }
            }
        }

        if (requestAuthentication == null) {
            requestAuthentication = new Authentication(this.config);
            requestAuthentication.setExpires(LocalDateTime.now().plusSeconds(this.config.getAuthenticationExpires()));
        }

        this.authentication = requestAuthentication;

        return requestAuthentication;
    }

    private void setAuthentication(HttpServerExchange exchange) {
        if (this.authentication != null && this.authentication.hasAuthenticatedUser()) {
            Cookie cookie;
            String cookieName = this.config.getAuthenticationCookieName();
            if (this.authentication.isLogout()) {
                cookie = exchange.getRequestCookies().get(cookieName);
                cookie.setMaxAge(0);
                cookie.setDiscard(true);
            } else {
                String sign = DigestUtils.sha512Hex(this.authentication.getAuthenticatedUser() + this.authentication.getExpires() + this.config.getString(Key.APPLICATION_SECRET));
                String value = sign + Default.DELIMITER.toString() + this.authentication.getExpires() + Default.DATA_DELIMITER.toString() + this.authentication.getAuthenticatedUser();

                if (this.config.getBoolean(Key.AUTH_COOKIE_ENCRYPT, false)) {
                    value = this.injector.getInstance(Crypto.class).encrypt(value);
                }

                cookie = new CookieImpl(cookieName, value)
                        .setHttpOnly(true)
                        .setPath("/")
                        .setExpires(Date.from(this.authentication.getExpires().atZone(ZoneId.systemDefault()).toInstant()));
            }

            exchange.setResponseCookie(cookie);
        }
    }

    private void getFlash(HttpServerExchange exchange) {
        Flash requestFlash = null;
        Cookie cookie = exchange.getRequestCookies().get(this.config.getFlashCookieName());
        if (cookie != null && StringUtils.isNotBlank(cookie.getValue())){
            Map<String, String> values = new HashMap<String, String>();
            for (Map.Entry<String, String> entry : Splitter.on("&").withKeyValueSeparator(":").split(cookie.getValue()).entrySet()) {
                values.put(entry.getKey(), entry.getValue());
            }

            requestFlash = new Flash(values);
            requestFlash.setDiscard(true);
        }

        if (requestFlash == null) {
            requestFlash = new Flash();
        }

        this.flash = requestFlash;
    }

    private void setFlash(HttpServerExchange exchange) {
        if (this.flash != null && !this.flash.isDiscard() && this.flash.hasContent()) {
            String values = Joiner.on("&").withKeyValueSeparator(":").join(this.flash.getValues());

            Cookie cookie = new CookieImpl(this.config.getFlashCookieName(), values)
                    .setHttpOnly(true)
                    .setPath("/");

            exchange.setResponseCookie(cookie);
        } else {
            Cookie cookie = exchange.getRequestCookies().get(this.config.getFlashCookieName());
            if (cookie != null) {
                cookie.setHttpOnly(true)
                .setPath("/")
                .setMaxAge(0);

                exchange.setResponseCookie(cookie);
            }
        }
    }

    private void getForm(HttpServerExchange exchange) throws IOException {
        this.form = this.injector.getInstance(Form.class);
        if (exchange.getRequestMethod().equals(Methods.POST) || exchange.getRequestMethod().equals(Methods.PUT)) {
            final FormDataParser formDataParser = FormParserFactory.builder().build().createParser(exchange);
            if (formDataParser != null) {
                exchange.startBlocking();
                FormData formData = formDataParser.parseBlocking();

                for (String data : formData) {
                    for (FormData.FormValue formValue : formData.get(data)) {
                        if (formValue.isFile()) {
                            form.addFile(formValue.getFile());
                        } else {
                            form.add(new HttpString(data).toString(), formValue.getValue());
                        }
                    }
                }

                this.form.setSubmitted(true);
            }
        }
    }

    private Body getBody(HttpServerExchange exchange) throws IOException {
        Body body = new Body();
        if (exchange.getRequestMethod().equals(Methods.POST) || exchange.getRequestMethod().equals(Methods.PUT)) {
            exchange.startBlocking();
            body.setContent(IOUtils.toString(exchange.getInputStream()));
        }

        return body;
    }

    private Object[] getConvertedParameters(HttpServerExchange exchange) throws IOException {
        Map<String, String> queryParameters = getRequestParameters(exchange);
        Object [] convertedParameters = new Object[this.parameterCount];

        int index = 0;
        for (Map.Entry<String, Class<?>> entry : this.parameters.entrySet()) {
            String key = entry.getKey();
            Class<?> clazz = entry.getValue();

            if ((Form.class).equals(clazz)) {
                convertedParameters[index] = this.form;
            } else if ((Body.class).equals(clazz)) {
                convertedParameters[index] = getBody(exchange);
            } else if ((Authentication.class).equals(clazz)) {
                convertedParameters[index] = this.authentication;
            } else if ((Session.class).equals(clazz)) {
                convertedParameters[index] = this.session;
            } else if ((Flash.class).equals(clazz)) {
                convertedParameters[index] = this.flash;
            } else if ((String.class).equals(clazz)) {
                convertedParameters[index] = StringUtils.isBlank(queryParameters.get(key)) ? "" : queryParameters.get(key);
            } else if ((Integer.class).equals(clazz) || (int.class).equals(clazz)) {
                convertedParameters[index] = StringUtils.isBlank(queryParameters.get(key)) ? Integer.valueOf(0) : Integer.valueOf(queryParameters.get(key));
            } else if ((Double.class).equals(clazz) || (double.class).equals(clazz)) {
                convertedParameters[index] = StringUtils.isBlank(queryParameters.get(key)) ? Double.valueOf(0) : Double.valueOf(queryParameters.get(key));
            } else if ((Float.class).equals(clazz) || (float.class).equals(clazz)) {
                convertedParameters[index] = StringUtils.isBlank(queryParameters.get(key)) ? Float.valueOf(0) : Float.valueOf(queryParameters.get(key));
            } else if ((Long.class).equals(clazz) || (long.class).equals(clazz)) {
                convertedParameters[index] = StringUtils.isBlank(queryParameters.get(key)) ? Long.valueOf(0) : Long.valueOf(queryParameters.get(key));
            } else if ((ContentType.APPLICATION_JSON.toString()).equals(exchange.getRequestHeaders().get(Headers.CONTENT_TYPE).element())) {
                convertedParameters[index] = this.mapper.readValue(getBody(exchange).asString(), clazz);
            }

            index++;
        }

        return convertedParameters;
    }

    private Map<String, String> getRequestParameters(HttpServerExchange exchange) {
        Map<String, String> requestParamater = new HashMap<String, String>();

        Map<String, Deque<String>> queryParameters = exchange.getQueryParameters();
        queryParameters.putAll(exchange.getPathParameters());

        for (Map.Entry<String, Deque<String>> entry : queryParameters.entrySet()) {
            requestParamater.put(entry.getKey(), entry.getValue().element());
        }

        return requestParamater;
    }

    private Map<String, Class<?>> getMethodParameters() {
        Map<String, Class<?>> methodParameters = new LinkedHashMap<String, Class<?>>();
        for (Method declaredMethod : this.controller.getClass().getDeclaredMethods()) {
            if (declaredMethod.getName().equals(this.controllerMethod) && declaredMethod.getParameterCount() > 0) {
                Parameter[] declaredParameters = declaredMethod.getParameters();
                for (Parameter parameter : declaredParameters) {
                    methodParameters.put(parameter.getName(), parameter.getType());
                }
                break;
            }
        }

        return methodParameters;
    }
}