package testapp.endpoint;

import org.junit.Test;

public class GHIssue289 extends EndpointTester {

    @Test
    public void testFoo() throws Exception {
        url("/gh/289").get();
        bodyEq("Hello World!");
    }

}
