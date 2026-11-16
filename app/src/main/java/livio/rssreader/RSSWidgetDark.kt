package livio.rssreader

import livio.rssreader.backend.RSSWidgetBase

class RSSWidgetDark : RSSWidgetBase() {
    //widget-theme, dark version
    override fun getLayout(): Int { //widget-theme
        return R.layout.widget_message_dark
    }
}

