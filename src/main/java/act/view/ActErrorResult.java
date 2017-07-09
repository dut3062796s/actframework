package act.view;

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
import act.app.*;
import act.asm.AsmContext;
import act.asm.AsmException;
import act.controller.Controller;
import act.exception.BindException;
import act.util.ActError;
import org.osgl.$;
import org.osgl.exception.InvalidRangeException;
import org.osgl.exception.UnsupportedException;
import org.osgl.http.H;
import org.osgl.mvc.annotation.ResponseStatus;
import org.osgl.mvc.result.ErrorResult;
import org.osgl.mvc.result.Result;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;

import javax.validation.ValidationException;
import java.util.List;
import java.util.Map;

public class ActErrorResult extends ErrorResult implements ActError {

    protected SourceInfo sourceInfo;

    public ActErrorResult(Throwable cause) {
        super(H.Status.of(userDefinedStatusCode(cause.getClass())));
        initCause(cause);
        init();
        populateSourceInfo(cause);
    }

    public ActErrorResult(H.Status status) {
        super(status);
        init();
        populateSourceInfo();
    }

    public ActErrorResult(H.Status status, String message, Object ... args) {
        super(status, message, args);
        init();
        populateSourceInfo();
    }

    private ActErrorResult(AsmException exception, boolean scanning) {
        super(H.Status.of(userDefinedStatusCode(exception.getClass())), errorMsg(exception, scanning));
        initCause(exception.getCause());
        init();
        populateSourceInfo(exception.context());
    }

    public ActErrorResult(H.Status status, Throwable cause) {
        super(status);
        initCause(cause);
        init();
        populateSourceInfo(cause);
    }

    public ActErrorResult(H.Status status, Throwable cause, String message, Object... args) {
        super(status, message, args);
        initCause(cause);
        init();
        populateSourceInfo(cause);
    }

    @Override
    public Throwable getCauseOrThis() {
        Throwable cause = getCause();
        return null == cause ? this : cause;
    }

    public SourceInfo sourceInfo() {
        return sourceInfo;
    }

    public List<String> stackTrace() {
        return Util.stackTraceOf(this);
    }

    @Override
    public boolean isErrorSpot(String traceLine, String nextTraceLine) {
        return false;
    }

    protected void init() {}

    protected void populateSourceInfo(Throwable t) {
        if (!Act.isDev()) {
            return;
        }
        if (t instanceof SourceInfo) {
            this.sourceInfo = (SourceInfo)t;
        } else {
            doFillInStackTrace();
            Throwable cause = getCause();
            sourceInfo = Util.loadSourceInfo(null == cause ? getStackTrace() : cause.getStackTrace(), ActErrorResult.class);
        }
    }

    protected void populateSourceInfo(AsmContext context) {
        if (!Act.isDev()) {
            return;
        }
        this.sourceInfo = Util.loadSourceInfo(context);
    }

    private void populateSourceInfo() {
        populateSourceInfo(new RuntimeException());
    }

    private static Map<Class<? extends Throwable>, $.Function<Throwable, Result>> x = C.newMap();
    static {
        $.Function<Throwable, Result> unsupported = new $.Transformer<Throwable, Result>() {
            @Override
            public Result transform(Throwable throwable) {
                return new ActErrorResult(H.Status.NOT_IMPLEMENTED, throwable);
            }
        };
        x.put(UnsupportedException.class, unsupported);
        x.put(UnsupportedOperationException.class, unsupported);
        x.put(IllegalStateException.class, new $.Transformer<Throwable, Result>() {
            @Override
            public Result transform(Throwable throwable) {
                return new ActErrorResult(H.Status.CONFLICT, throwable);
            }
        });
        $.Transformer<Throwable, Result> badRequest = new $.Transformer<Throwable, Result>() {
            @Override
            public Result transform(Throwable throwable) {
                return new ActErrorResult(H.Status.BAD_REQUEST, throwable);
            }
        };
        x.put(IllegalArgumentException.class, badRequest);
        x.put(InvalidRangeException.class, badRequest);
        x.put(IndexOutOfBoundsException.class, badRequest);
        x.put(ValidationException.class, badRequest);
        x.put(BindException.class, badRequest);
    }

    private static Map<Class, Integer> userDefinedStatus = C.newMap();

    private static int userDefinedStatusCode(Class<? extends Throwable> exCls) {
        Integer I = userDefinedStatus.get(exCls);
        if (null == I) {
            ResponseStatus rs = exCls.getAnnotation(ResponseStatus.class);
            if (null == rs) {
                I = H.Status.Code.INTERNAL_SERVER_ERROR;
                userDefinedStatus.put(exCls, I);
            } else {
                I = rs.value();
            }
        }
        return I;
    }

    public static Result of(Throwable t) {
        if (t instanceof Result) {
            return (Result) t;
        } else if (t instanceof org.rythmengine.exception.RythmException) {
            return Act.isDev() ? new RythmTemplateException((org.rythmengine.exception.RythmException) t) : ErrorResult.of(H.Status.INTERNAL_SERVER_ERROR);
        } else if (t instanceof AsmException) {
            return new ActErrorResult((AsmException) t, true);
        } else {
            $.Function<Throwable, Result> transformer = transformerOf(t);
            return null == transformer ? new ActErrorResult(t) : transformer.apply(t);
        }
    }

    public static ActErrorResult scanningError(AsmException exception) {
        return new ActErrorResult(exception, true);
    }

    public static ActErrorResult enhancingError(AsmException exception) {
        return new ActErrorResult(exception, false);
    }

    private static $.Function<Throwable, Result> transformerOf(Throwable t) {
        Class tc = t.getClass();
        $.Function<Throwable, Result> transformer = x.get(tc);
        if (null != transformer) {
            return transformer;
        }
        for (Class c : x.keySet()) {
            if (c.isAssignableFrom(tc)) {
                return x.get(c);
            }
        }
        return null;
    }

    public static ErrorResult of(H.Status status) {
        E.illegalArgumentIf(!status.isError());
        return Act.isDev() ? new ActErrorResult(status) : new ErrorResult(status);
    }

    public static ErrorResult of(H.Status status, String message, Object... args) {
        E.illegalArgumentIf(!status.isClientError() && !status.isServerError());
        return Act.isDev() ? new ActErrorResult(status, message, args) : new ErrorResult(status, message, args);
    }

    public static ErrorResult of(H.Status status, int errorCode) {
        E.illegalArgumentIf(!status.isError());
        return (Act.isDev() ? new ActErrorResult(status) : new ErrorResult(status)).initErrorCode(errorCode);
    }

    public static ErrorResult of(H.Status status, int errorCode, String message, Object... args) {
        E.illegalArgumentIf(!status.isClientError() && !status.isServerError());
        return (Act.isDev() ? new ActErrorResult(status, message, args) : new ErrorResult(status, message, args)).initErrorCode(errorCode);
    }

    public static Throwable rootCauseOf(Throwable t) {
        if (null == t) {
            return null;
        }
        Throwable cause;
        for (;;) {
            cause = t.getCause();
            if (null != cause) {
                t = cause;
            } else {
                break;
            }
        }
        return t;
    }


    private static String errorMsg(AsmException exception, boolean scanning) {
        String userMsg = exception.getLocalizedMessage();
        return (S.blank(userMsg)) ? S.concat("Error ", scanning ? "scanning" : "enhancing", " bytecode at ", exception.context().toString()) : userMsg;
    }

    public static Result actNotFound() {
        return Act.isDev() ? of(H.Status.NOT_FOUND) : Controller.NOT_FOUND;
    }

    public static Result actForbidden() {
        return Act.isDev() ? of(H.Status.FORBIDDEN) : Controller.FORBIDDEN;
    }


    public static Result actMethodNotAllowed() {
        return Act.isDev() ? of(H.Status.METHOD_NOT_ALLOWED) : Controller.METHOD_NOT_ALLOWED;
    }
}
