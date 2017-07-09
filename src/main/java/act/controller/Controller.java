package act.controller;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2017 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import act.Act;
import act.app.ActionContext;
import act.conf.AppConfigKey;
import act.controller.meta.HandlerMethodMetaInfo;
import act.data.Versioned;
import act.route.Router;
import act.util.DisableFastJsonCircularReferenceDetect;
import act.util.FastJsonIterable;
import act.util.PropertySpec;
import act.view.*;
import org.osgl.$;
import org.osgl.Osgl;
import org.osgl.http.H;
import org.osgl.mvc.result.*;
import org.osgl.storage.ISObject;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.IO;
import org.osgl.util.S;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Map;

import static com.alibaba.fastjson.JSON.toJSONString;
import static org.osgl.http.H.Format.*;
import static org.osgl.mvc.result.Redirect.F.*;

/**
 * Mark a class as Controller, which contains at least one of the following:
 * <ul>
 * <li>Action handler method</li>
 * <li>Any one of Before/After/Exception/Finally interceptor</li>
 * </ul>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Controller {


    /**
     * Singleton instance for an empty {@link H.Status#OK} result
     */
    Result OK = Result.OK;

    /**
     * Singleton instance for an empty {@link H.Status#CREATED} result
     */
    Result CREATED = Result.CREATED;

    /**
     * Singleton instance for an empty {@link H.Status#CREATED} result
     */
    Result ACCEPTED = Result.ACCEPTED;

    /**
     * Singleton instance for an empty {@link H.Status#NO_CONTENT} result
     */
    Result NO_CONTENT = Result.NO_CONTENT;

    /**
     * Singleton instance for an empty {@link H.Status#NOT_MODIFIED} result
     */
    Result NOT_MODIFIED = Result.NOT_MODIFIED;

    /**
     * Singleton instance for an empty {@link H.Status#BAD_REQUEST} result
     */
    Result BAD_REQUEST = Result.BAD_REQUEST;

    /**
     * Singleton instance for an empty {@link H.Status#UNAUTHORIZED} result
     */
    Result UNAUTHORIZED = Result.UNAUTHORIZED;

    /**
     * Singleton instance for an empty {@link H.Status#FORBIDDEN} result
     */
    Result FORBIDDEN = Result.FORBIDDEN;

    /**
     * Singleton instance for an empty {@link H.Status#NOT_FOUND} result
     */
    Result NOT_FOUND = Result.NOT_FOUND;

    /**
     * Singleton instance for an empty {@link H.Status#METHOD_NOT_ALLOWED} result
     */
    Result METHOD_NOT_ALLOWED = Result.METHOD_NOT_ALLOWED;

    /**
     * Singleton instance for an empty {@link H.Status#CONFLICT} result
     */
    Result CONFLICT = Result.CONFLICT;

    /**
     * Singleton instance for an empty {@link H.Status#NOT_IMPLEMENTED} result
     */
    Result NOT_IMPLEMENTED = Result.NOT_IMPLEMENTED;

    /**
     * Indicate the context path for all action methods declared
     * in this controller.
     * <p/>
     * <p>Default value: "{@code /}"</p>
     *
     * @return the controller context path
     */
    String value() default "/";

    /**
     * Specify the port(s) this controller's action method shall be
     * routed from.
     *
     * @return the port name
     * @see AppConfigKey#NAMED_PORTS
     */
    String[] port() default {};

    /**
     * Provides utilities for controller action methods to emit rendering results
     */
    class Util {

        /**
         * Returns an empty {@link H.Status#OK} result
         */
        public static Result ok() {
            return OK;
        }

        /**
         * Returns a {@link H.Status#CREATED} result
         *
         * @param resourceGetUrl the URL to access the new resource been created
         * @return the result as described
         */
        public static Result created(String resourceGetUrl) {
            return Result.created(resourceGetUrl);
        }

        /**
         * Return a {@link H.Status#CREATED} result
         * @return the result as described
         */
        public static Result created() {
            return CREATED;
        }

        /**
         * Return a {@link H.Status#NOT_MODIFIED} result
         * @return the result as described
         */
        public static Result notModified() {
            return NOT_MODIFIED;
        }

        /**
         * Create an new {@link H.Status#NOT_MODIFIED} result with etag specified
         * @param etag the etag string template
         * @param args the etag string arguments
         * @return the result as described
         */
        public static Result notModified(String etag, Object... args) {
            return Result.notModified().etag(S.fmt(etag, args));
        }

        /**
         * Returns an {@link H.Status#NOT_FOUND} result
         */
        public static Result notFound() {
            return Act.isDev() ? ActErrorResult.of(H.Status.NOT_FOUND) : NOT_FOUND;
        }

        /**
         * Returns an {@link H.Status#NOT_FOUND} result with custom message
         * template and arguments. The final message is rendered with
         * the template and arguments using {@link String#format(String, Object...)}
         *
         * @param msg  the message template
         * @param args the message argument
         */
        public static Result notFound(String msg, Object... args) {
            return ActErrorResult.of(H.Status.NOT_FOUND, msg, args);
        }

        /**
         * Throws out an {@link H.Status#NOT_FOUND} result if the object specified is
         * {@code null}
         *
         * @param o the object to be evaluated
         */
        public static <T> T notFoundIfNull(T o) {
            if (null == o) {
                throw notFound();
            }
            return o;
        }

        /**
         * Throws out an {@link H.Status#NOT_FOUND} result with custom message template and
         * arguments if the object specified is {@code null}. The final message is
         * rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param o    the object to be evaluated
         * @param msg  the message template
         * @param args the message argument
         */
        public static <T> T notFoundIfNull(T o, String msg, Object... args) {
            if (null == o) {
                throw notFound(msg, args);
            }
            return o;
        }

        /**
         * Throws out an {@link H.Status#NOT_FOUND} result if the boolean expression specified
         * is {@code true}
         * {@code null}
         *
         * @param test the boolean expression to be evaluated
         */
        public static void notFoundIf(boolean test) {
            if (test) {
                throw notFound();
            }
        }

        /**
         * Throws out an {@link H.Status#NOT_FOUND} result with custom message template and
         * arguments if the expression specified is {@code true}. The final message is
         * rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param test the boolean expression
         * @param msg  the message template
         * @param args the message argument
         */
        public static void notFoundIf(boolean test, String msg, Object... args) {
            if (test) {
                throw notFound(msg, args);
            }
        }

        /**
         * Throws out an {@link H.Status#NOT_FOUND} result if the boolean expression specified
         * is {@code false}
         * {@code null}
         *
         * @param test the boolean expression to be evaluated
         */
        public static void notFoundIfNot(boolean test) {
            notFoundIf(!test);
        }

        /**
         * Throws out an {@link H.Status#NOT_FOUND} result with custom message template and
         * arguments if the expression specified is {@code false}. The final message is
         * rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param test the boolean expression
         * @param msg  the message template
         * @param args the message argument
         */
        public static void notFoundIfNot(boolean test, String msg, Object... args) {
            notFoundIf(!test, msg, args);
        }

        /**
         * Returns an {@link H.Status#BAD_REQUEST} result
         */
        public static Result badRequest() {
            return Act.isDev() ? ActErrorResult.of(H.Status.BAD_REQUEST) : BAD_REQUEST;
        }

        /**
         * Returns an {@link H.Status#BAD_REQUEST} result with custom message
         * template and arguments. The final message is rendered with
         * the template and arguments using {@link String#format(String, Object...)}
         *
         * @param msg  the message template
         * @param args the message argument
         */
        public static Result badRequest(String msg, Object... args) {
            return ActErrorResult.of(H.Status.BAD_REQUEST, msg, args);
        }

        /**
         * Throws out an {@link H.Status#BAD_REQUEST} result if the boolean expression specified
         * is {@code true}
         *
         * @param test the boolean expression to be evaluated
         */
        public static void badRequestIf(boolean test) {
            if (test) {
                throw badRequest();
            }
        }

        /**
         * Throws out an {@link H.Status#BAD_REQUEST} result with custom message template and
         * arguments if the expression specified is {@code true}. The final message is
         * rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param test the boolean expression
         * @param msg  the message template
         * @param args the message argument
         */
        public static void badRequestIf(boolean test, String msg, Object... args) {
            if (test) {
                throw badRequest(msg, args);
            }
        }

        /**
         * Throws out an {@link H.Status#BAD_REQUEST} result if the specified string is blank
         *
         * @param test the string to be evaluated
         */
        public static void badRequestIfBlank(String test) {
            badRequestIf(S.blank(test));
        }

        /**
         * Throws out an {@link H.Status#BAD_REQUEST} result with custom message template and
         * arguments if the specified string is blank. The final message is
         * rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param test the string to be checked
         * @param msg  the message template
         * @param args the message argument
         */
        public static void badRequestIfBlank(String test, String msg, Object... args) {
            badRequestIf(S.blank(test), msg, args);
        }

        /**
         * Throws out an {@link H.Status#BAD_REQUEST} result if the specified object is `null`
         *
         * @param test the object to be evaluated
         */
        public static void badRequestIfNull(Object test) {
            badRequestIf(null == test);
        }

        /**
         * Throws out an {@link H.Status#BAD_REQUEST} result with custom message template and
         * arguments if the specified object is `null`. The final message is
         * rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param test the object to be checked
         * @param msg  the message template
         * @param args the message argument
         */
        public static void badRequestIfNull(Object test, String msg, Object... args) {
            badRequestIf(null == test, msg, args);
        }

        /**
         * Throws out an {@link H.Status#BAD_REQUEST} result if the boolean expression specified
         * is {@code false}
         *
         * @param test the boolean expression to be evaluated
         */
        public static void badRequestIfNot(boolean test) {
            badRequestIf(!test);
        }

        /**
         * Throws out an {@link H.Status#BAD_REQUEST} result with custom message template and
         * arguments if the expression specified is {@code false}. The final message is
         * rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param test the boolean expression
         * @param msg  the message template
         * @param args the message argument
         */
        public static void badRequestIfNot(boolean test, String msg, Object... args) {
            badRequestIf(!test, msg, args);
        }

        public static Result conflict() {
            return Act.isDev() ? CONFLICT : ActErrorResult.of(H.Status.CONFLICT);
        }

        public static Result conflict(String message, Object... args) {
            return ActErrorResult.of(H.Status.CONFLICT, message, args);
        }

        public static void conflictIf(boolean test) {
            if (test) {
                throw conflict();
            }
        }

        public static void conflictIf(boolean test, String message, Object... args) {
            if (test) {
                throw conflict(message, args);
            }
        }

        public static void conflictIfNot(boolean test) {
            conflictIf(!test);
        }

        public static void conflictIfNot(boolean test, String message, Object... args) {
            conflictIf(!test, message, args);
        }

        public static Result unauthorized() {
            return Act.isDev() ? ActErrorResult.of(H.Status.UNAUTHORIZED) : UNAUTHORIZED;
        }

        public static Unauthorized unauthorized(String realm) {
            return ActUnauthorized.create(realm);
        }

        public static void unauthorizedIf(boolean test) {
            if (test) {
                throw unauthorized();
            }
        }

        public static void unauthorizedIf(boolean test, String realm) {
            if (test) {
                throw unauthorized(realm);
            }
        }

        public static void unauthorizedIfNot(boolean test) {
            unauthorizedIf(!test);
        }

        public static void unauthorizedIfNot(boolean test, String realm) {
            unauthorizedIf(!test, realm);
        }

        /**
         * Returns a {@link H.Status#FORBIDDEN} result
         */
        public static Result forbidden() {
            return Act.isDev() ? ActErrorResult.of(H.Status.FORBIDDEN) : FORBIDDEN;
        }

        /**
         * Returns a {@link H.Status#FORBIDDEN} result with custom message
         * template and arguments. The final message is rendered with
         * the template and arguments using {@link String#format(String, Object...)}
         *
         * @param msg  the message template
         * @param args the message argument
         */
        public static Result forbidden(String msg, Object... args) {
            return ActErrorResult.of(H.Status.FORBIDDEN, msg, args);
        }

        /**
         * Throws a {@link H.Status#FORBIDDEN} result if the test condition is {@code true}
         *
         * @param test the test condition
         */
        public static void forbiddenIf(boolean test) {
            if (test) {
                throw forbidden();
            }
        }

        /**
         * Throws a {@link H.Status#FORBIDDEN} result if the test condition is {@code false}
         *
         * @param test the test condition
         */
        public static void forbiddenIfNot(boolean test) {
            forbiddenIf(!test);
        }

        /**
         * Throws a {@link H.Status#FORBIDDEN} result if test condition is {@code true}
         *
         * @param test the test condition
         * @param msg  the message format template
         * @param args the message format arguments
         */
        public static void forbiddenIf(boolean test, String msg, Object... args) {
            if (test) {
                throw forbidden(msg, args);
            }
        }

        /**
         * Throws a {@link H.Status#FORBIDDEN} result if the test condition is {@code false}
         *
         * @param test the test condition
         * @param msg  the message format template
         * @param args the message format arguments
         */
        public static void forbiddenIfNot(boolean test, String msg, Object... args) {
            forbiddenIf(!test, msg, args);
        }

        private static String processUrl(String url) {
            if (url.contains(".") || url.contains("(")) {
                String inferFullActionPath = Router.inferFullActionPath(url);
                if (inferFullActionPath != url) {
                    url = ActionContext.current().router().reverseRoute(url);
                }
            } else {
                if (!url.startsWith("/")) {
                    ActionContext context = ActionContext.current();
                    String urlContext = context.urlContext();
                    if (S.notBlank(urlContext)) {
                        url = S.pathConcat(urlContext, '/', url);
                    }
                }
            }
            return url;
        }

        private static <T extends Redirect> T _redirect($.Function<String, T> func, String url, Object... args) {
            url = processUrl(S.fmt(url, args));
            return func.apply(url);
        }

        private static <T extends Redirect> T _redirect($.Function<String, T> func, String url, Map reverseRoutingArguments) {
            url = Router.inferFullActionPath(url);
            url = ActionContext.current().router().reverseRoute(url, reverseRoutingArguments);
            return func.apply(url);
        }

        /**
         * This method is deprecated. Please use {@link #loginRedirect(String, Object...)} instead
         */
        @Deprecated
        public static Redirect redirect(String url, Object... args) {
            return _redirect(LOGIN_REDIRECT, url, args);
        }

        /**
         * This method is deprecated. Please use {@link #loginRedirect(String, Map)} instead
         */
        @Deprecated
        public static Redirect redirect(String url, Map reverseRoutingArguments) {
            return _redirect(LOGIN_REDIRECT, url, reverseRoutingArguments);
        }

        /**
         * This method is deprecated. Please use {@link #loginRedirectIf(boolean, String, Object...)} instead
         */
        @Deprecated
        public static void redirectIf(boolean test, String url, Object... args) {
            if (test) {
                throw redirect(url, args);
            }
        }

        /**
         * This method is deprecated. Please use {@link #loginRedirectIfNot(boolean, String, Object...)} instead
         */
        @Deprecated
        public static void redirectIfNot(boolean test, String url, Object... args) {
            redirectIf(!test, url, args);
        }

        /**
         * This method is deprecated. Please use {@link #loginRedirectIf(boolean, String, Map)} instead
         */
        @Deprecated
        public static void redirectIf(boolean test, String url, Map reverseRoutingArguments) {
            if (test) {
                throw redirect(url, reverseRoutingArguments);
            }
        }

        /**
         * This method is deprecated. Please use {@link #loginRedirectIfNot(boolean, String, Map)} instead
         */
        @Deprecated
        public static void redirectIfNot(boolean test, String url, Map reverseRoutingArguments) {
            redirectIf(!test, url, reverseRoutingArguments);
        }

        public static Redirect loginRedirect(String url, Object... args) {
            return _redirect(LOGIN_REDIRECT, url, args);
        }

        public static Redirect loginRedirect(String url, Map reverseRoutingArguments) {
            return _redirect(LOGIN_REDIRECT, url, reverseRoutingArguments);
        }

        public static void loginRedirectIf(boolean test, String url, Object... args) {
            if (test) {
                throw redirect(url, args);
            }
        }

        public static void loginRedirectIfNot(boolean test, String url, Object... args) {
            redirectIf(!test, url, args);
        }

        public static void loginRedirectIf(boolean test, String url, Map reverseRoutingArguments) {
            if (test) {
                throw redirect(url, reverseRoutingArguments);
            }
        }

        public static void loginRedirectIfNot(boolean test, String url, Map reverseRoutingArguments) {
            redirectIf(!test, url, reverseRoutingArguments);
        }

        public static Redirect movedPermanently(String url, Object... args) {
            return _redirect(MOVED_PERMANENTLY, url, args);
        }

        public static Redirect movedPermanently(String url, Map reverseRoutingArguments) {
            return _redirect(MOVED_PERMANENTLY, url, reverseRoutingArguments);
        }

        public static void movedPermanentlyIf(boolean test, String url, Object... args) {
            if (test) {
                throw movedPermanently(url, args);
            }
        }

        public static void movedPermanentlyIfNot(boolean test, String url, Object... args) {
            movedPermanentlyIf(!test, url, args);
        }

        public static void movedPermanentlyIf(boolean test, String url, Map reverseRoutingArguments) {
            if (test) {
                throw movedPermanently(url, reverseRoutingArguments);
            }
        }

        public static void movedPermanentlyIfNot(boolean test, String url, Map reverseRoutingArguments) {
            movedPermanentlyIf(!test, url, reverseRoutingArguments);
        }

        public static Redirect found(String url, Object... args) {
            return _redirect(FOUND, url, args);
        }

        public static Redirect found(String url, Map reverseRoutingArguments) {
            return _redirect(FOUND, url, reverseRoutingArguments);
        }

        public static void foundIf(boolean test, String url, Object... args) {
            if (test) {
                throw found(url, args);
            }
        }

        public static void foundIfNot(boolean test, String url, Object... args) {
            foundIf(!test, url, args);
        }

        public static void foundIf(boolean test, String url, Map reverseRoutingArguments) {
            if (test) {
                throw found(url, reverseRoutingArguments);
            }
        }

        public static void foundIfNot(boolean test, String url, Map reverseRoutingArguments) {
            foundIf(!test, url, reverseRoutingArguments);
        }

        public static Redirect seeOther(String url, Object... args) {
            return _redirect(SEE_OTHER, url, args);
        }

        public static Redirect seeOther(String url, Map reverseRoutingArguments) {
            return _redirect(SEE_OTHER, url, reverseRoutingArguments);
        }

        public static void seeOtherIf(boolean test, String url, Object... args) {
            if (test) {
                throw seeOther(url, args);
            }
        }

        public static void seeOtherIfNot(boolean test, String url, Object... args) {
            seeOtherIf(!test, url, args);
        }

        public static void seeOtherIf(boolean test, String url, Map reverseRoutingArguments) {
            if (test) {
                throw seeOther(url, reverseRoutingArguments);
            }
        }

        public static void seeOtherIfNot(boolean test, String url, Map reverseRoutingArguments) {
            seeOtherIf(!test, url, reverseRoutingArguments);
        }

        public static Redirect temporaryRedirect(String url, Object... args) {
            return _redirect(TEMPORARY_REDIRECT, url, args);
        }

        public static Redirect temporaryRedirect(String url, Map reverseRoutingArguments) {
            return _redirect(TEMPORARY_REDIRECT, url, reverseRoutingArguments);
        }

        public static void temporaryRedirectIf(boolean test, String url, Object... args) {
            if (test) {
                throw temporaryRedirect(url, args);
            }
        }

        public static void temporaryRedirectIfNot(boolean test, String url, Object... args) {
            temporaryRedirectIf(!test, url, args);
        }

        public static void temporaryRedirectIf(boolean test, String url, Map reverseRoutingArguments) {
            if (test) {
                throw temporaryRedirect(url, reverseRoutingArguments);
            }
        }

        public static void temporaryRedirectIfNot(boolean test, String url, Map reverseRoutingArguments) {
            temporaryRedirectIf(!test, url, reverseRoutingArguments);
        }

        public static Redirect permanentRedirect(String url, Object... args) {
            return _redirect(PERMANENT_REDIRECT, url, args);
        }

        public static Redirect permanentRedirect(String url, Map reverseRoutingArguments) {
            return _redirect(PERMANENT_REDIRECT, url, reverseRoutingArguments);
        }

        public static void permanentRedirectIf(boolean test, String url, Object... args) {
            if (test) {
                throw permanentRedirect(url, args);
            }
        }

        public static void permanentRedirectIfNot(boolean test, String url, Object... args) {
            permanentRedirectIf(!test, url, args);
        }

        public static void permanentRedirectIf(boolean test, String url, Map reverseRoutingArguments) {
            if (test) {
                throw permanentRedirect(url, reverseRoutingArguments);
            }
        }

        public static void permanentRedirectIfNot(boolean test, String url, Map reverseRoutingArguments) {
            permanentRedirectIf(!test, url, reverseRoutingArguments);
        }

        public static ErrorResult paymentRequired(String msg, Object... args) {
            return ActErrorResult.of(H.Status.PAYMENT_REQUIRED, msg, args);
        }

        public static void paymentRequiredIf(boolean test, String msg, Object... args) {
            if (test) {
                throw paymentRequired(msg, args);
            }
        }

        public static void paymentRequiredIfNot(boolean test, String msg, Object... args) {
            paymentRequiredIf(!test, msg, args);
        }

        public static void paymentRequiredIfNot(boolean test, String msg, Map reverseRoutingArguments) {
            paymentRequiredIf(!test, msg, reverseRoutingArguments);
        }

        /**
         * Returns a {@link RenderContent} result with specified message template
         * and args. The final message is rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param msg  the message format template
         * @param args the message format arguments
         */
        public static RenderContent text(String msg, Object... args) {
            return RenderContent.renderText(S.fmt(msg, args));
        }

        /**
         * Alias of {@link #text(String, Object...)}
         * @param msg  the message format template
         * @param args the message format arguments
         * @return the result
         */
        public static RenderContent renderText(String msg, Object... args) {
            return text(msg, args);
        }

        /**
         * Returns a {@link RenderContent} result with specified message template
         * and args. The final message is rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param msg  the message format template
         * @param args the message format arguments
         * @return the result
         */
        public static RenderContent html(String msg, Object... args) {
            return RenderContent.renderHtml(S.fmt(msg, args));
        }

        /**
         * Alias of {@link #html(String, Object...)}
         * @param msg  the message format template
         * @param args the message format arguments
         * @return the result
         */
        public static RenderContent renderHtml(String msg, Object args) {
            return html(msg, args);
        }

        /**
         * Returns a {@link RenderContent} result with specified message template
         * and args. The final message is rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param msg  the message format template
         * @param args the message format arguments
         * @return the result
         */
        public static RenderContent json(String msg, Object... args) {
            return RenderContent.renderJson(S.fmt(msg, args));
        }

        /**
         * Alias of {@link #json(String, Object...)}
         * @param msg the message format template
         * @param args the message format arguments
         * @return the result
         */
        public static RenderContent renderJson(String msg, Object... args) {
            return json(msg, args);
        }

        /**
         * Returns a {@link RenderContent} result with any object. This method will
         * call underline JSON serializer to transform the object into a JSON string
         *
         * @param data the data to be rendered as JSON string
         * @return the result
         */
        public static RenderContent json(Object data) {
            return RenderContent.renderJson(toJSONString(data));
        }

        /**
         * Alias of {@link #json(Object)}
         * @param data the data to be rendered as JSON string
         * @return the result
         */
        public static RenderContent renderJson(Object data) {
            return json(data);
        }

        /**
         * Returns a {@link RenderJsonMap} result with any object. This method will
         * generate a JSON object out from the {@link ActionContext#renderArgs}.
         * The response is always in JSON format and ignores the HTTP `Accept`
         * header setting
         * @param data the varargs of Object to be put into the JSON map
         * @return the result
         */
        public static RenderJsonMap jsonMap(Object... data) {
            return RenderJsonMap.get();
        }

        /**
         * Alias of {@link #jsonMap(Object...)}
         * @param data the data to be put into the JSON map
         * @return the result
         */
        public static RenderJsonMap renderJsonMap(Object ... data) {
            return jsonMap(data);
        }


        /**
         * Returns a {@link RenderContent} result with specified message template
         * and args. The final message is rendered with the template and arguments using
         * {@link String#format(String, Object...)}
         *
         * @param msg  the message format template
         * @param args the message format arguments
         * @return the result
         */
        public static RenderContent xml(String msg, Object... args) {
            return RenderContent.renderXml(S.fmt(msg, args));
        }

        /**
         * Alias of {@link #xml(String, Object...)}
         * @param msg the message format template
         * @param args the message format arguments
         * @return the result
         */
        public static RenderContent renderXml(String msg, Object... args) {
            return xml(msg, args);
        }

        /**
         * Returns a {@link RenderBinary} result with an {@link ISObject} instance. The result will render
         * the binary using "inline" content disposition
         *
         * @param sobj the {@link ISObject} instance
         * @return the result
         */
        public static RenderBinary binary(ISObject sobj) {
            return new RenderBinary().source(sobj);
        }

        /**
         * Alias of {@link #binary(ISObject)}
         * @param sobj the {@link ISObject} instance
         * @return the result
         */
        public static RenderBinary renderBinary(ISObject sobj) {
            return binary(sobj);
        }

        /**
         * Returns a {@link RenderBinary} result with an {@link ISObject} instance. The result will render
         * the binary using "attachment" content disposition
         *
         * @param sobj the {@link ISObject} instance
         */
        public static RenderBinary download(ISObject sobj) {
            return new RenderBinary().source(sobj).asAttachment();
        }

        /**
         * Returns a {@link RenderBinary} result with a file. The result will render
         * the binary using "inline" content disposition.
         *
         * @param file the file to be rendered
         * @return a result
         */
        public static RenderBinary binary(File file) {
            return new RenderBinary().source(file);
        }

        /**
         * Alias of {@link #binary(File)}
         * @param file the file to be rendered
         * @return a result
         */
        public static RenderBinary renderBinary(File file) {
            return binary(file);
        }

        /**
         * Returns a {@link RenderBinary} result with a delayed output stream writer.
         * The result will render the binary using "inline" content disposition.
         *
         * @param outputStreamWriter the delayed writer
         * @return the result
         */
        public static RenderBinary binary($.Function<OutputStream, ?> outputStreamWriter) {
            return new RenderBinary().source(outputStreamWriter);
        }

        /**
         * Alias of {@link #binary(Osgl.Function)}
         * @param outputStreamWriter the delayed writer
         * @return the result
         */
        public static RenderBinary renderBinary($.Function<OutputStream, ?> outputStreamWriter) {
            return binary(outputStreamWriter);
        }

        /**
         * Returns a {@link RenderBinary} result with a file. The result will render
         * the binary using "attachment" content disposition.
         *
         * @param file the file to be rendered
         */
        public static RenderBinary download(File file) {
            return new RenderBinary().source(file).asAttachment();
        }

        /**
         * Render barcode for given content
         * @param content the content to generate the barcode
         * @return the barcode as a binary result
         */
        public static ZXingResult barcode(String content) {
            return ZXingResult.barcode(content);
        }

        /**
         * Alias of {@link #barcode(String)}
         * @param content the content to generate the barcode
         * @return the barcode as a binary result
         */
        public static ZXingResult renderBarcode(String content) {
            return barcode(content);
        }

        /**
         * Render QRCode for given content
         * @param content the content to generate the qrcode
         * @return the qrcode as a binary result
         */
        public static ZXingResult qrcode(String content) {
            return ZXingResult.qrcode(content);
        }

        /**
         * Alias of {@link #qrcode(String)}
         * @param content the content to generate the barcode
         * @return the barcode as a binary result
         */
        public static ZXingResult renderQrcode(String content) {
            return qrcode(content);
        }

        /**
         * Returns a {@link RenderTemplate} result with a render arguments map.
         * Note the template path should be set via {@link ActionContext#templatePath(String)}
         * method
         *
         * @param args the template arguments
         * @return a result to render template
         */
        public static RenderTemplate template(Map<String, Object> args) {
            return RenderTemplate.of(args);
        }

        /**
         * Alias of {@link #template(Map)}
         *
         * @param args the template arguments
         * @return a result to render template
         */
        public static RenderTemplate renderTemplate(Map<String, Object> args) {
            return template(args);
        }

        /**
         * This method is deprecated, please use {@link #template(Object...)} instead
         *
         * @param args template argument list
         */
        public static RenderTemplate renderTemplate(Object... args) {
            return RenderTemplate.get();
        }

        /**
         * Kind of like {@link #render(Object...)}, the only differences is this method force to render a template
         * without regarding to the request format
         *
         * @param args template argument list
         */
        public static RenderTemplate template(Object... args) {
            return RenderTemplate.get(ActionContext.current().successStatus());
        }

        /**
         * The caller to this magic {@code render} method is subject to byte code enhancement. All
         * parameter passed into this method will be put into the application context via
         * {@link ActionContext#renderArg(String, Object)} using the variable name found in the
         * local variable table. If the first argument is of type String and there is no variable name
         * associated with that variable then it will be treated as template path and get set to the
         * context via {@link ActionContext#templatePath(String)} method.
         * <p>This method returns different render results depends on the request format</p>
         * <table>
         * <tr>
         * <th>Format</th>
         * <th>Result type</th>
         * </tr>
         * <tr>
         * <td>{@link org.osgl.http.H.Format#json}</td>
         * <td>A JSON string that map the arguments to their own local variable names</td>
         * </tr>
         * <tr>
         * <td>{@link org.osgl.http.H.Format#html} or any other text formats</td>
         * <td>{@link RenderTemplate}</td>
         * </tr>
         * <tr>
         * <td>{@link org.osgl.http.H.Format#pdf} or any other binary format</td>
         * <td>If first argument is of type File or InputStream, then outbound the
         * content as a binary stream, otherwise throw out {@link org.osgl.exception.UnsupportedException}</td>
         * </tr>
         * </table>
         *
         * @param args any argument that can be put into the returned JSON/XML data or as template arguments
         */
        public static RenderAny render(Object... args) {
            return RenderAny.get();
        }

        public static Result inferResult(Result r, ActionContext actionContext) {
            return r;
        }

        public static Result inferPrimitiveResult(Object v, ActionContext actionContext, boolean requireJSON, boolean requireXML, boolean isArray) {
            H.Status status = actionContext.successStatus();
            if (requireJSON) {
                return RenderContent.renderJson(C.map("result", v)).overwriteStatus(status);
            } else if (requireXML) {
                return RenderContent.renderXml(S.concat("<result>", S.string(v), "</result>")).overwriteStatus(status);
            } else {
                H.Format fmt = actionContext.accept();
                if (HTML == fmt || H.Format.UNKNOWN == fmt) {
                    String s = isArray ? $.toString2(v) : v.toString();
                    return RenderContent.renderHtml(s).overwriteStatus(status);
                }
                if (TXT == fmt || CSV == fmt) {
                    String s = isArray ? $.toString2(v) : v.toString();
                    return RenderContent.renderText(s).overwriteStatus(status).contentType(fmt);
                }
                throw E.unexpected("Cannot apply text result to format: %s", fmt);
            }
        }

        public static Result inferResult(Map<String, Object> map, ActionContext actionContext) {
            if (actionContext.acceptJson()) {
                return RenderContent.renderJson(map).overwriteStatus(actionContext.successStatus());
            }
            return RenderTemplate.of(actionContext.successStatus(), map);
        }

        /**
         * @param array
         * @param actionContext
         * @return
         */
        public static Result inferResult(Object[] array, ActionContext actionContext) {
            if (actionContext.acceptJson()) {
                return RenderContent.renderJson(array).overwriteStatus(actionContext.successStatus());
            }
            throw E.tbd("render template with render args in array");
        }

        /**
         * Infer {@link Result} from an {@link InputStream}. If the current context is in
         * {@code JSON} format then it will render a {@link RenderContent#renderJson(Object)} JSON} result from the content of the
         * input stream. Otherwise, it will render a {@link RenderBinary binary} result from the inputstream
         *
         * @param is            the inputstream
         * @param actionContext
         * @return a Result inferred from the inputstream specified
         */
        public static Result inferResult(final InputStream is, ActionContext actionContext) {
            if (actionContext.acceptJson()) {
                return RenderContent.renderJson(IO.readContentAsString(is)).overwriteStatus(actionContext.successStatus());
            } else {
                return new RenderBinary().source(new Osgl.Visitor<OutputStream>() {
                    @Override
                    public void visit(OutputStream outputStream) throws Osgl.Break {
                        IO.copy(is, outputStream, true);
                    }
                }).overwriteStatus(actionContext.successStatus());
            }
        }

        /**
         * Infer {@link Result} from an {@link File}. If the current context is in
         * {@code JSON} format then it will render a {@link RenderContent#renderJson(Object)}  JSON} result from the content of the
         * file. Otherwise, it will render a {@link RenderBinary binary} result from the file specified
         *
         * @param file          the file
         * @param actionContext
         * @return a Result inferred from the file specified
         */
        public static Result inferResult(File file, ActionContext actionContext) {
            if (actionContext.acceptJson()) {
                return RenderContent.renderJson(IO.readContentAsString(file)).overwriteStatus(actionContext.successStatus());
            } else {
                return new RenderBinary().source(file).overwriteStatus(actionContext.successStatus());
            }
        }

        public static Result inferResult(ISObject sobj, ActionContext context) {
            if (context.acceptJson()) {
                return RenderContent.renderJson(sobj.asString()).overwriteStatus(context.successStatus());
            } else {
                return binary(sobj).overwriteStatus(context.successStatus());
            }
        }

        /**
         * Infer a {@link Result} from a {@link Object object} value v:
         * <ul>
         * <li>If v is {@code null} then null returned</li>
         * <li>If v is instance of {@code Result} then it is returned directly</li>
         * to infer the {@code Result}</li>
         * <li>If v is instance of {@code InputStream} then {@link #inferResult(InputStream, ActionContext)} is used
         * to infer the {@code Result}</li>
         * <li>If v is instance of {@code File} then {@link #inferResult(File, ActionContext)} is used
         * to infer the {@code Result}</li>
         * <li>If v is instance of {@code Map} then {@link #inferResult(Map, ActionContext)} is used
         * to infer the {@code Result}</li>
         * <li>If v is an array of {@code Object} then {@link #inferResult(Object[], ActionContext)} is used
         * to infer the {@code Result}</li>
         * </ul>
         *
         * @param meta the HandlerMethodMetaInfo
         * @param v the value to be rendered
         * @param context the action context
         * @param hasTemplate a boolean flag indicate if the current handler method has corresponding template
         * @return the rendered result
         */
        public static Result inferResult(HandlerMethodMetaInfo meta, Object v, ActionContext context, boolean hasTemplate) {
            if (v instanceof Result) {
                return (Result)v;
            }
            final H.Request req = context.req();
            final H.Status status = context.successStatus();
            if (Act.isProd() && v instanceof Versioned && req.method().safe()) {
                processEtag(meta, v, context, req);
            }
            if (hasTemplate) {
                if (v instanceof Map) {
                    return inferToTemplate(((Map) v), context);
                }
                return inferToTemplate(v, context);
            }

            boolean requireJSON = context.acceptJson();
            boolean requireXML = !requireJSON && context.acceptXML();

            if (null == v) {
                // the following code breaks before handler without returning result
                //return requireJSON ? RenderJSON.of("{}") : requireXML ? RenderXML.of("<result></result>") : null;
                return null;
            } else if ($.isSimpleType(v.getClass())) {
                boolean isArray = meta.returnType().getDescriptor().startsWith("[");
                return inferPrimitiveResult(v, context, requireJSON, requireXML, isArray);
            } else if (v instanceof InputStream) {
                return inferResult((InputStream) v, context);
            } else if (v instanceof File) {
                return inferResult((File) v, context);
            } else if (v instanceof ISObject) {
                return inferResult((ISObject) v, context);
            } else if (v instanceof Map) {
                return RenderContent.renderJson(v).overwriteStatus(status);
            } else {
                if (requireJSON) {
                    // patch https://github.com/alibaba/fastjson/issues/478
                    if (meta.disableJsonCircularRefDetect()) {
                        DisableFastJsonCircularReferenceDetect.option.set(true);
                    }
                    if (v instanceof Iterable && !(v instanceof Collection)) {
                        v = new FastJsonIterable((Iterable) v);
                    }
                    PropertySpec.MetaInfo propertySpec = PropertySpec.MetaInfo.withCurrent(meta, context);
                    try {
                        if (null == propertySpec) {
                            return RenderContent.renderJson(v).overwriteStatus(status);
                        }
                        return FilteredRenderJSON.get(status, v, propertySpec, context);
                    } finally {
                        if (meta.disableJsonCircularRefDetect()) {
                            DisableFastJsonCircularReferenceDetect.option.set(false);
                        }
                    }
                } else if (context.acceptXML()) {
                    PropertySpec.MetaInfo propertySpec = PropertySpec.MetaInfo.withCurrent(meta, context);
                    return new FilteredRenderXML(status, v, propertySpec, context);
                } else if (context.accept() == H.Format.CSV) {
                    PropertySpec.MetaInfo propertySpec = PropertySpec.MetaInfo.withCurrent(meta, context);
                    return RenderCSV.get(status, v, propertySpec, context);
                } else {
                    boolean isArray = meta.returnType().getDescriptor().startsWith("[");
                    return inferPrimitiveResult(v, context, false, requireXML, isArray);
                }
            }
        }

        private static void processEtag(HandlerMethodMetaInfo meta, Object v, ActionContext context, H.Request req) {
            if (!(v instanceof Versioned)) {
                return;
            }
            String version = ((Versioned) v)._version();
            String etagVersion = etag(meta, version);
            if (req.etagMatches(etagVersion)) {
                throw NOT_MODIFIED;
            } else {
                context.resp().etag(etagVersion);
            }
        }

        private static String etag(HandlerMethodMetaInfo meta, String version) {
            return S.newBuffer(version).append(meta.hashCode()).toString();
        }

        private static Result inferToTemplate(Object v, ActionContext actionContext) {
            actionContext.renderArg("result", v);
            return RenderTemplate.get();
        }

        private static Result inferToTemplate(Map map, ActionContext actionContext) {
            return RenderTemplate.of(map);
        }

        private static H.Status successStatus() {
            return ActionContext.current().successStatus();
        }
    }

    /**
     * Controller class extends this class automatically get `ActionContext` injected
     * as a field
     */
    class Base extends Util {
        @Inject
        protected ActionContext context;
    }

}
