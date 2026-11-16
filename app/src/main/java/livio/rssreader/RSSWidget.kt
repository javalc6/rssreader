package livio.rssreader

import livio.rssreader.backend.RSSWidgetBase

class RSSWidget : RSSWidgetBase() {
    //widget-theme, light version
    override fun getLayout(): Int { //widget-theme
        return R.layout.widget_message
    }
}

