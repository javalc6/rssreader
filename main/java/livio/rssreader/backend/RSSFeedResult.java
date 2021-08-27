package livio.rssreader.backend;

public final class RSSFeedResult {
    public final int resultCode;
    public String newfeedurl;

    RSSFeedResult(int resultCode) {
        this.resultCode = resultCode;
    }

    public RSSFeedResult(int resultCode, String newfeedurl) {
        this.resultCode = resultCode;
        this.newfeedurl = newfeedurl;
    }
}
