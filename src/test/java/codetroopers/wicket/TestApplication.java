package codetroopers.wicket;

import codetroopers.wicket.web.HotReloadingUtils;
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
        //need to use the special method to allow reloading of the homePage class
        return HotReloadingUtils.reloadableHomePage(this, StartupPage.class);
    }
}
