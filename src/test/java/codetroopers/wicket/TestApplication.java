package codetroopers.wicket;

import codetroopers.wicket.page.StartupPage;
import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;

import codetroopers.wicket.restx.HotReloadingClassLoader;

/**
 * @author cgatay
 */
public class TestApplication extends WebApplication{
    @Override
    protected void init() {
        super.init();
        Thread.currentThread().setContextClassLoader(
                new HotReloadingClassLoader(Thread.currentThread().getContextClassLoader(), "codetroopers"));
    }

    @Override
    public Class<? extends Page> getHomePage() {
        return StartupPage.class;
    }
}
