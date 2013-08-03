package codetroopers.wicket.page;

import codetroopers.wicket.page.panel.SimplePanel;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.AjaxSelfUpdatingTimerBehavior;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.model.PropertyModel;
import org.apache.wicket.util.time.Duration;

/**
 * @author cgatay
 */
public class AnotherPage extends WebPage {
    Long timeTick;

    public AnotherPage() {
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        final Label label = new Label("changingLabel", new PropertyModel<Long>(this, "timeTick"));
        add(label);
        //to test vary the duration for example (you need to refresh the page)
        label.add(new AjaxSelfUpdatingTimerBehavior(Duration.seconds(2)){
            @Override
            protected void onPostProcessTarget(final AjaxRequestTarget target) {
                super.onPostProcessTarget(target);
                timeTick = System.currentTimeMillis();
            }
        });
        add(new SimplePanel("simplePanel"));
    }
}
