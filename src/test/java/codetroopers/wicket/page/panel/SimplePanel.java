package codetroopers.wicket.page.panel;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.markup.html.panel.Panel;

/**
 * @author cgatay
 */
public class SimplePanel extends Panel {
    public SimplePanel(final String id) {
        super(id);
        //you can play with this attributemodifier too
        add(AttributeModifier.append("style", "background-color:green;width:200px;height:30px;"));
    }
}
