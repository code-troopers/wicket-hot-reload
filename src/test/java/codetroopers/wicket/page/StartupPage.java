package codetroopers.wicket.page;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.util.time.Duration;

/**
 * @author cgatay
 */
public class StartupPage extends WebPage {
    int i;
    public StartupPage() {
        i = 0;
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        add(new Label("hello", new AbstractReadOnlyModel<String>() {
            @Override
            public String getObject() {
                return "Hello Reload " + i;
            }
        }));
        add(new AjaxSelfUpdatingTimerBehavior(Duration.seconds(15)){
            @Override
            protected void onPostProcessTarget(final AjaxRequestTarget target) {
                i++;
                super.onPostProcessTarget(target);
            }
        });
    }
}
