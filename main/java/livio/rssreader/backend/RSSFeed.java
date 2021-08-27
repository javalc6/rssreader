package livio.rssreader.backend;
/*
Version 1.0, 16-08-2021, First release by Livio (javalc6@gmail.com)

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.UnsupportedCharsetException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.PriorityQueue;
import java.util.TimeZone;
import java.util.Vector;

import android.content.Context;
import android.util.Log;

import tools.DateParser;
import tools.Entities;


public final class RSSFeed implements Serializable {
	private static final long serialVersionUID = 2L;//publisher, changed value to 2

    private final static boolean debug = false;//BuildConfig.DEBUG;//should be false in prod environment

    private String _title = "<untitled>";
	private String _language = null;
	private Date _pubdate;
    private String _publisherurl = null;//publisher

    private String etag;
    private String lastmod;
    private final String feed_id;

	private long time; // new field
	private final List<RSSItem> _itemlist;
	private final static String tagz = "RSSFeed";
	
	private RSSFeed(String feed_id) {//do not use constructor from other classes, instead please use getInstance()
		_itemlist = new Vector<>(0);
		_pubdate = new Date(); // default date
        this.feed_id = feed_id;
	}

    public static RSSFeed getInstance(File cachedir, String feed_id) {
        File feedFile = new File(cachedir, feed_id.concat(".cache"));
        if (feedFile.exists()) {
            try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(feedFile))) {
                return (RSSFeed) is.readObject();
            } catch (ClassNotFoundException | IOException e) {
                e.printStackTrace(); // do nothing
            }
        }
        return new RSSFeed(feed_id);
    }

	public RSSItem getItem(int location) {
		return _itemlist.get(location);
	}
	
	public List<RSSItem> getAllItems() {
		return _itemlist;
	}
	
	public int size() {
		return _itemlist.size();
	}

	private void setTitle(String title) {
		_title = title.trim();
	}
	private void setLanguage(String lang) {
		_language = lang.trim();
	}
    void setPublisherLink(String link) {//publisher
        _publisherurl = link;
    }

	private void setPubDate(String pubdate)	{
        _pubdate = DateParser.parseDate(pubdate);
	}

	public String getTitle() {
		return _title;
	}
	public String getLanguage(String default_lang) {
	    if (_language == null)
	        return default_lang;
		return _language;
	}
    public String getPublisherLink() {//publisher, note that it defaults to feed_url
        return _publisherurl;
    }

    public String getPubDate()	{
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(TimeZone.getDefault());
        if (_pubdate == null)
            return sdf.format(new Date());
        else return sdf.format(_pubdate);
    }

    public String getETag() {
        return etag;
    }

    public String getLastMod() {
        return lastmod;
    }

    public void serialize(Context context, String etag, String lastmod) throws IOException {
        this.etag = etag;
        this.lastmod = lastmod;

		File feedFile = new File(context.getCacheDir(), feed_id.concat(".cache"));
//		feedFile.deleteOnExit(); // delete the file when exiting
		FileOutputStream fos = new FileOutputStream(feedFile);
		ObjectOutputStream os = new ObjectOutputStream(fos);
		os.writeObject(this);
		os.close();
    }
    
    public boolean isFileUpdated(long age) {
        return time != 0 && System.currentTimeMillis() - time <= age;
    }
     
// doProcessStream()
// in positive case it returns the number of collected items
// in negative case it returns an error code < 0
    private final static int zNullStream = -1;
//    final static int zInvalidResponseCode = -2; removed
    private final static int zMissingRSSTag = -3;
    public final static int zRedirectFeed = -4;
    private final static int zEmptyBody = -5;

    private final static String encoding_element = "encoding=\"";

//use doProcessStream() only for xml content (not suitable for html content!)
	public RSSFeedResult doProcessStream(InputStream is, String encoding, int max_titles, String feed_url, String title) throws IOException, UnsupportedCharsetException {
		if (is == null) return new RSSFeedResult(zNullStream);
        BufferedReader stream;//stream handling modified to support russian web sites with encoding windows-1251
        byte[] body = null;
        if (encoding.length() > 0)
            stream = new BufferedReader(new InputStreamReader(is, encoding));
        else {
//            stream = new BufferedReader(new InputStreamReader(is)); old implementation, not supporting change of encoding
//the following code enables change of encoding, as needed to support russian web sites with encoding windows-1251
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) >= 0)
                baos.write(buf, 0, n);
            body = baos.toByteArray();//body can be reused several time (i.e. with different encoding)
            if (body.length == 0)//ci sono alcuni feeds che ritornano il response code 200 con il body vuoto, inutile fare parsing
                return new RSSFeedResult(zEmptyBody);
            stream = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body)));
        }
        _title = title;//set title here and not during web data retrieval
        _publisherurl = feed_url;
        String newfeedurl = null;
		PriorityQueue<RSSItem> result = new PriorityQueue<>();
        time = System.currentTimeMillis();
		RSSItem item = null;
		int rssstate = 0; // 0: init, 1: rss found, 2: channel found
        if (debug) Log.d(tagz, "doProcessStream, encoding="+encoding);
		int state = 0; // idle
		int deepness = 0;
		String st;
		StringBuilder content = new StringBuilder(512), econtent = new StringBuilder(512);
		StringBuilder tag = new StringBuilder();
//		String stag;
		long timer = System.nanoTime();
        boolean rdf = false;
		try {
            main:
            while ((st = stream.readLine()) != null) {
//---begin---
                int xml = 0;
                if (rssstate == 0) { // no rss found
                    if ((body != null) && (encoding.length() == 0) && (xml = st.indexOf("<?xml")) != -1) {//if encoding is not defined, check encoding, to support russian web sites with encoding windows-1251
                        int pos = st.indexOf(encoding_element, xml);
                        if (pos != -1) {
                            int end = st.indexOf("\"", pos + encoding_element.length());
                            encoding = st.substring(pos + encoding_element.length(), end);//now encoding is defined
                            stream.close(); //close stream before re-opening it
                            stream = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), encoding));//re-open stream with proper encoding, beware of loops!!
                            if (debug)
                                Log.d(tagz, "encoding: " + encoding);
                        }
                    } else if ((xml = st.indexOf("<rss")) != -1)//check basic rss ?
                        rssstate = 1; // rss found, looking for channel
                    else if ((xml = st.indexOf("<feed")) != -1) { //check basic feed ?
                        if ((xml + 5 == st.length()) || st.charAt(xml + 5) == ' ')//nota: dopo <feed è importante che termini la linea o ci sia blank, non rimuovere!
                        rssstate = 2; // feed found
                    } else if ((xml = st.indexOf("<rdf:RDF")) != -1) {
                        if (st.indexOf("/rss/", xml) != -1)// check rdf based rss ?
                            rssstate = 1; // rss found, looking for channel
                        else rdf = true;
                    } else if (rdf && st.contains("/rss/"))// check rdf based rss ?
                        rssstate = 1; // rss found, looking for channel
                    else {
                        if (newfeedurl == null) {
                            int pos, href, start, stop;
                            if (((pos = st.indexOf("<link")) != -1) && ((pos = st.indexOf("alternate", pos)) != -1) &&
                                ((st.indexOf("type=\"application/rss+xml\"", pos) != -1) || (st.indexOf("type=\"atom/rss+xml\"", pos) != -1)) &&
                                    ((href = st.indexOf("href", pos)) != -1)) {
//autodiscovery: try rss feed auto-discovery via dirty parsing:
//e.g.  <link rel="alternate" type="application/rss+xml" title="blabla" href="http://www.repubblica.it/rss/homepage/rss2.0.xml" />
                                int end = st.indexOf(">", pos);
                                if (end == -1)
                                    end = st.length();
                                if ((href < end) && ((start = st.indexOf("\"", href)) != -1) && ((stop = st.indexOf("\"", start + 1)) != -1)) {
                                    String tempnewfeedurl = st.substring(start + 1, stop);
                                    if (!feed_url.equals(tempnewfeedurl)) {//check that it is not auto-referencing!!!
                                        newfeedurl = tempnewfeedurl;
                                        Log.i(tagz, "autodiscovery feed url: " + newfeedurl);
                                    }
                                }

                            }
                        }
                        continue;
                    }
                    if (debug) Log.d(tagz, "rssstate = " + rssstate);
                    if (st.indexOf(">", xml) == -1) continue;
                }

                boolean tagging = false;
                int st_length = st.length();
                for (int i = xml; i < st_length; i++) {
                    char ch = st.charAt(i);
                    switch (state) {
                        case 0: // idle
                            if (ch == '<') {
                                if (st.charAt(i + 1) == '/') { // warning: missing check on bound
                                    i++;
                                    state = 2; // end tag
                                    if (debug) Log.d(tagz, "state = " + state);
                                    deepness--;
                                } else {
                                    state = 1; // start tag (or standalone tag)
                                    if (debug) Log.d(tagz, "state = " + state);
                                    tagging = true;
                                    tag.setLength(0);
                                    deepness++;
                                }
                            }
                            break;

                        case 1: // start tag (or standalone tag)
                            switch (ch) {
                                case '>':
                                    if (st.charAt(i - 1) == '/') {
                                        deepness--; // standalone tag
                                        if (deepness == 0)
                                            state = 0; // idle
                                        else state = 3; // read content
                                        if (debug) Log.d(tagz, "state = " + state);
                                    } else {
                                        state = 3; // read content
                                        if (debug) Log.d(tagz, "state = " + state);
                                        content.setLength(0); // content is the final buffer final
                                        econtent.setLength(0); // econtent is temporary buffer to handle not CDATA content
                                        String stag = tag.toString().toLowerCase(); // note: equals() does not work on StringBuffer as you may expect!
                                        if (stag.equals("channel")) {
                                            rssstate = 2; // channel found
                                            if (debug) Log.d(tagz, "rssstate = " + rssstate);
                                        } else if ((rssstate > 1) && (stag.equals("item") || stag.equals("entry"))) {
                                            rssstate = 3; // item found
                                            if (debug) Log.d(tagz, "rssstate = " + rssstate);
                                            if (item != null) {
                                                result.add(item);
                                            }
                                            item = new RSSItem();
                                        }

                                    }
                                    break;
                                case ' ':
                                    tagging = false;
                                    break;
                                default:
                                    if (tagging)
                                        tag.append(ch);
                                    break;
                            }
                            break;

                        case 2: // end tag
                            int gt = st.indexOf(">", i); // uu
                            if (gt == -1) continue main; // uu
                            i = gt; // uu
//assert: now it is true that (st.charAt(i) == '>')							

                            if (deepness == 0)
                                state = 0; // idle
                            else state = 3; // read content
                            if (debug) Log.d(tagz, "state = " + state);
                            String stag = tag.toString().toLowerCase(); // note: equals() does not work on StringBuffer as you may expect!
                            if (debug) Log.d(tagz, "stag = " + stag);
                            if (rssstate == 2) {
                                if (debug) Log.d(tagz, "parsing " + stag);
                                switch (stag) {
                                    case "title":
//                                    setTitle(content.toString()); don't use title sent by server, as it is unreliable
                                        break;
                                    case "pubdate":
                                    case "published":
                                    case "updated":
                                    case "dc:date":
                                        setPubDate(content.toString());
                                        break;
                                    case "language":
                                        setLanguage(content.toString());
                                        break;
                                    case "link"://publisher
                                        setPublisherLink(content.toString());
                                        break;
                                }
                            } else if (item != null) { // i.e. rssstate == 3
                                switch (stag) {
                                    case "title":
                                        item.setTitle(content.toString());
                                        break;
                                    case "link":
                                        item.setLink(content.toString());
                                        break;
                                    case "description":
                                        item.setDescription(content.toString());
                                        break;
                                    case "content:encoded":
                                        item.setDescription(Entities.XML.unescape(content));
                                        break;
                                    case "category":
                                        item.setCategory(content.toString());
                                        break;
                                    case "pubdate":
                                    case "published":
                                    case "updated":
                                    case "dc:date":
                                        item.setPubDate(content.toString());
                                        break;
                                }
                            }
                            break;

                        case 3: // read content
                            int lt = st.indexOf("<", i); // uu
                            if (lt == -1) {// uu
                                econtent.append(st.substring(i));// uu
                                continue main;
                            }// uu
                            if (lt != i) { // uu (ch != '<')
                                econtent.append(st.substring(i, lt));// uu
                                i = lt; // uu
                            } // uu
//assert: now it is true that (st.charAt(i) == '<')
                            switch (st.charAt(i + 1)) {
                                case '/':  // warning: missing check on bound
                                    i++;
                                    state = 2; // end tag

                                    if (debug) Log.d(tagz, "state = " + state);
                                    content.append(Entities.XML.unescape(econtent)); // put temporary buffer in final one

                                    econtent.setLength(0); // clear temp buffer

                                    deepness--;
                                    break;
                                case '!':
//special (eg: <![CDATA[), not a tag!
                                    if (st.substring(i).startsWith("<![CDATA[")) {
                                        state = 4; // CDATA, look for end "]]>"
                                        if (debug) Log.d(tagz, "state = " + state);
                                        content.append(Entities.XML.unescape(econtent)); // put temporary buffer in final one
                                        econtent.setLength(0); // clear temp buffer
                                        i += 8; // skip CDATA
                                    } else econtent.append(ch);
                                    break;
                                default:
                                    state = 1; // start tag (or standalone tag)

                                    if (debug) Log.d(tagz, "state = " + state);
                                    tagging = true;
                                    tag.setLength(0);
                                    deepness++;
                                    break;
                            }
                            break;
                        case 4: // CDATA, look for end "]]>"
                            int eta = st.indexOf("]]>", i);
                            if (eta == -1) {
                                content.append(st.substring(i));
                                continue main;
                            } else {
                                content.append(st.substring(i, eta));
                                state = 3; // read content
                                if (debug) Log.d(tagz, "state = " + state);
                                i = eta + 2;
                            }
                            break;
                        default:
                            Log.w(tagz, "Invalid state");
                            break;
                    }
                }
                if (state == 2)
                    Log.w(tagz, "Error: parsing error, state: " + state);
//---end---
            }
            if (state == 1)
                Log.w(tagz, "Error: parsing error, state: " + state);
            double delta = (System.nanoTime() - timer) / 1000000.0;
            Log.i(tagz, "feed parser time: " + delta + " ms");
            if ((rssstate == 2) || (rssstate == 3)) {
                if (item != null)
                    result.add(item);
                _itemlist.clear();
                while ((result.size() > 0) && (_itemlist.size() < max_titles))
                    _itemlist.add(result.poll());
                if (debug) Log.d(tagz, "number of parsed items: " + _itemlist.size());
                return new RSSFeedResult(_itemlist.size());
            } else if (newfeedurl != null)
                return new RSSFeedResult(zRedirectFeed, newfeedurl);
            else return new RSSFeedResult(zMissingRSSTag);
        } finally {
		    stream.close();
        }
	} // end of doProcessStream()
	

}
