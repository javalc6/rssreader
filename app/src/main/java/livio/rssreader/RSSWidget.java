package livio.rssreader;

import livio.rssreader.backend.RSSWidgetBase;

public final class RSSWidget extends RSSWidgetBase { //widget-theme, light version
    @Override
    protected int getLayout() {//widget-theme
        return R.layout.widget_message;
    }
}

