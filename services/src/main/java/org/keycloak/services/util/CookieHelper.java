/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.services.util;

import org.jboss.logging.Logger;
import org.keycloak.http.HttpResponse;
import org.jboss.resteasy.util.CookieParser;
import org.keycloak.common.util.ServerCookie;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakTransaction;

import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.keycloak.common.util.ServerCookie.SameSiteAttributeValue;


/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class CookieHelper {

    public static final String LEGACY_COOKIE = "_LEGACY";

    private static final Logger logger = Logger.getLogger(CookieHelper.class);
    private static final String ADD_COOKIES_AT_END_OF_TRANSACTION = CookieHelper.class.getName() + "_ADD_COOKIES_AT_END_OF_TRANSACTION";

    /**
     * Set a response cookie.  This solely exists because JAX-RS 1.1 does not support setting HttpOnly cookies
     * @param name
     * @param value
     * @param path
     * @param domain
     * @param comment
     * @param maxAge
     * @param secure
     * @param httpOnly
     * @param sameSite
     */
    public static void addCookie(String name, String value, String path, String domain, String comment, int maxAge, boolean secure, boolean httpOnly, SameSiteAttributeValue sameSite, KeycloakSession session) {
        SameSiteAttributeValue sameSiteParam = sameSite;
        // when expiring a cookie we shouldn't set the sameSite attribute; if we set e.g. SameSite=None when expiring a cookie, the new cookie (with maxAge == 0)
        // might be rejected by the browser in some cases resulting in leaving the original cookie untouched; that can even prevent user from accessing their application
        if (maxAge == 0) {
            sameSite = null;
        }

        boolean secure_sameSite = sameSite == SameSiteAttributeValue.NONE || secure; // when SameSite=None, Secure attribute must be set

        HttpResponse response = session.getContext().getHttpResponse();
        StringBuffer cookieBuf = new StringBuffer();
        ServerCookie.appendCookieValue(cookieBuf, 1, name, value, path, domain, comment, maxAge, secure_sameSite, httpOnly, sameSite);
        String cookie = cookieBuf.toString();
        if (shouldAddCookiesAtEndOfTransaction(session)) {
            session.getTransactionManager().enlistAfterCompletion(new CookieTransaction(response, cookie));
        } else {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie);
        }

        // a workaround for browser in older Apple OSs – browsers ignore cookies with SameSite=None
        if (sameSiteParam == SameSiteAttributeValue.NONE) {
            addCookie(name + LEGACY_COOKIE, value, path, domain, comment, maxAge, secure, httpOnly, null, session);
        }
    }

    private static boolean shouldAddCookiesAtEndOfTransaction(KeycloakSession session) {
        return Objects.equals(session.getAttribute(ADD_COOKIES_AT_END_OF_TRANSACTION), Boolean.TRUE);
    }

    /**
     * Adding cookies at the end of the transaction helps when retrying a transaction might add the
     * cookie multiple times. In some scenarios it must not be added at the end of the transaction,
     * as at that time the response has already been sent to the caller ("committed"), so the code
     * needs to make a choice. As retrying transactions is the exception, adding cookies at the end
     * of the transaction is also the exception and needs to be switched on where necessary.
     */
    public static void addCookiesAtEndOfTransaction(KeycloakSession session) {
        session.setAttribute(ADD_COOKIES_AT_END_OF_TRANSACTION, Boolean.TRUE);
    }

    /**
     * Set a response cookie avoiding SameSite parameter
     * @param name
     * @param value
     * @param path
     * @param domain
     * @param comment
     * @param maxAge
     * @param secure
     * @param httpOnly
     */
    public static void addCookie(String name, String value, String path, String domain, String comment, int maxAge, boolean secure, boolean httpOnly, KeycloakSession session) {
        addCookie(name, value, path, domain, comment, maxAge, secure, httpOnly, null, session);
    }


    public static Set<String> getCookieValue(KeycloakSession session, String name) {
        Set<String> ret = getInternalCookieValue(session, name);
        if (ret.size() == 0) {
            String legacy = name + LEGACY_COOKIE;
            logger.debugv("Could not find any cookies with name '{0}', trying '{1}'", name, legacy);
            ret = getInternalCookieValue(session, legacy);
        }
        return ret;
    }

    private static Set<String> getInternalCookieValue(KeycloakSession session, String name) {
        HttpHeaders headers = session.getContext().getHttpRequest().getHttpHeaders();
        Set<String> cookiesVal = new HashSet<>();

        // check for cookies in the request headers
        cookiesVal.addAll(parseCookie(headers.getRequestHeaders().getFirst(HttpHeaders.COOKIE), name));

        // get cookies from the cookie field
        Cookie cookie = headers.getCookies().get(name);
        if (cookie != null) {
            logger.debugv("{0} cookie found in the cookie field", name);
            cookiesVal.add(cookie.getValue());
        }


        return cookiesVal;
    }


    public static Set<String> parseCookie(String header, String name) {
        if (header == null || name == null) {
            return Collections.emptySet();
        }

        Set<String> values = new HashSet<>();

        for (Cookie cookie : CookieParser.parseCookies(header)) {
            if (name.equals(cookie.getName())) {
                logger.debugv("{0} cookie found in the request header", name);
                values.add(cookie.getValue());
            }
        }

        return values;
    }

    public static Cookie getCookie(Map<String, Cookie> cookies, String name) {
        Cookie cookie = cookies.get(name);
        if (cookie != null) {
            return cookie;
        }
        else {
            String legacy = name + LEGACY_COOKIE;
            logger.debugv("Could not find cookie {0}, trying {1}", name, legacy);
            return cookies.get(legacy);
        }
    }

    /**
     * Ensure that cookies are only added when the transaction is complete, as otherwise cookies will be set for error pages,
     * or will be added twice when running retries.
     */
    private static class CookieTransaction implements KeycloakTransaction {
        private final HttpResponse response;
        private final String cookie;
        private boolean transactionActive;

        public CookieTransaction(HttpResponse response, String cookie) {
            this.response = response;
            this.cookie = cookie;
        }

        @Override
        public void begin() {
            transactionActive = true;
        }

        @Override
        public void commit() {
            response.addHeader(HttpHeaders.SET_COOKIE, cookie);
            transactionActive = false;
        }

        @Override
        public void rollback() {
            transactionActive = false;
        }

        @Override
        public void setRollbackOnly() {
        }

        @Override
        public boolean getRollbackOnly() {
            return false;
        }

        @Override
        public boolean isActive() {
            return transactionActive;
        }
    }
}
