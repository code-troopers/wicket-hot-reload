package codetroopers.wicket;

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;

import codetroopers.wicket.page.StartupPage;

/**
 * @author cgatay
 */
public class TestApplication extends WebApplication{
    @Override
    protected void init() {
        super.init();
    }
    

    @Override
    public Class<? extends Page> getHomePage() {
        return StartupPage.class;
    }
}
