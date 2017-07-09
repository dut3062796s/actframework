package act.handler.builtin.controller;

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

import act.app.ActionContext;
import act.handler.ExpressHandler;
import act.view.ActErrorResult;
import org.osgl.http.H;
import org.osgl.mvc.result.ErrorResult;
import org.osgl.util.E;

public class AlwaysErrorResponse extends FastRequestHandler implements ExpressHandler {

    private final ErrorResult errorResult;

    public AlwaysErrorResponse(H.Status status) {
        E.illegalArgumentIf(!status.isError(), "Error status required");
        this.errorResult = ActErrorResult.of(status);
    }

    @Override
    public void handle(ActionContext context) {
        errorResult.apply(context);
    }

    @Override
    public String toString() {
        return "error: " + errorResult;
    }
}
