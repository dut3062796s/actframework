package testapp.endpoint.ghissues;

import act.controller.annotation.UrlContext;
import org.osgl.mvc.annotation.GetAction;

/**
 * Test Github #289 implementation
 */
@UrlContext("289")
public class GH289 extends GithubIssueBase {

    @GetAction
    public void test() {
        String who = "World";
        render("Hello @who!", who);
    }

}
