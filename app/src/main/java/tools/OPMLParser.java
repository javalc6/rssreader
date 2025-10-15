package tools;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This class parses OPML input streams
 *
 */
public class OPMLParser {

//method to parse OPML content into an arraylist of OPMLOutline
    public ArrayList<OPMLOutline> parse(InputStream reader) throws IOException, SAXException, ParserConfigurationException {

		ArrayList<OPMLOutline> outlines = new ArrayList<>();

		SAXParserFactory.newInstance().newSAXParser().parse(reader, new DefaultHandler() {
			int status = 0;//0: init; 1: opml; 2: head; 3: body

			@Override
			public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
//				System.out.println("Start Element: " + qName);

                switch (qName) {
                    case "opml":
                        status = 1;
                        break;
                    case "head":
                        if (status == 1)//opml
                            status = 2;
                        break;
                    case "body":
                        if (status == 1 || status == 2)//opml or head
                            status = 3;
                        break;
                    case "outline":
                        if (status == 3) {//body
                            String type = attributes.getValue("type");
                            String text = attributes.getValue("text");
                            String xmlUrl = attributes.getValue("xmlUrl");
                            if (type != null && type.equals("rss") && text != null && xmlUrl != null)
                                outlines.add(new OPMLOutline(type, text, xmlUrl));
                        }
                        break;
                }
			}

/*			
			@Override
			public void endElement(String uri, String localName, String qName) throws SAXException {
				System.out.println("End Element: " + qName);
			}
*/
			
/*
			@Override
			public void characters(char[] ch, int start, int length) throws SAXException {
				String content = new String(ch, start, length).trim();
				if (!content.isEmpty()) {
					System.out.println("Characters: " + content);
				}
			}
*/
		});

		return outlines;
    }

	public static void main(String[] args) {//used only for testing purposes
		OPMLParser op = new OPMLParser();
        try (InputStream reader = new FileInputStream(args[0])) {
			ArrayList<OPMLOutline> outlines = op.parse(reader);
			System.out.println("Number of outlines: " + outlines.size());
			for (OPMLOutline feed: outlines) {
				System.out.println(feed.getType() + " - " + feed.getText() + " - " + feed.getUrl());
			}
		} catch (SAXException | ParserConfigurationException | IOException e) {
            e.printStackTrace();
        }
    }

}
