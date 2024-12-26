package tools;


/**
 * This class is used to store/retrieve <outline> mandatory information
 *
 */
public class OPMLOutline {

	private String type;
	private String text;
	private String xmlUrl;

    public OPMLOutline(String type, String text, String xmlUrl) {
		this.type = type;
		this.text = text;
		this.xmlUrl = xmlUrl;
    }

	public void setType(String type) {
		this.type = type;
	}

	public void setText(String text) {
		this.text = text;
	}

	public void setUrl(String xmlUrl) {
		this.xmlUrl = xmlUrl;
	}

	public String getType() {
		return type;
	}

	public String getText() {
		return text;
	}

	public String getUrl() {
		return xmlUrl;
	}


}
