package act.view;

import act.Act;
import act.app.SourceInfo;
import act.util.ActError;
import org.osgl.mvc.result.Unauthorized;

import java.util.List;

/**
 * Decorate {@link org.osgl.mvc.result.Unauthorized} with Act Dev mode
 * error reporting support
 */
public class ActUnauthorized extends Unauthorized implements ActError {

    private SourceInfo sourceInfo;

    public ActUnauthorized() {
        super();
        if (Act.isDev()) {
            loadSourceInfo();
        }
    }

    public ActUnauthorized(String realm) {
        super();
        realm(realm);
        if (Act.isDev()) {
            loadSourceInfo();
        }
    }

    private void loadSourceInfo() {
        doFillInStackTrace();
        Throwable cause = getCause();
        sourceInfo = Util.loadSourceInfo(null == cause ? getStackTrace() : cause.getStackTrace(), ActUnauthorized.class);
    }

    @Override
    public Throwable getCauseOrThis() {
        return this;
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

    public static Unauthorized create(String realm) {
        return Act.isDev() ? new ActUnauthorized(realm) : new Unauthorized().realm(realm);
    }

}
