package tools;

public final class WebResponse {
	public StringBuilder result;
	public final String type;
    public final String coding;
	public final int responseCode;
	private final String diagnostic;
	public final String ETag;
	public final String LastMod;
	public final int requestId;

	public WebResponse(StringBuilder result, String type, String coding, int responseCode, String diagnostic, int requestId, String ETag, String LastMod) {
		this.result = result;
		this.type = type;
		this.coding = coding;
		this.responseCode = responseCode;
		this.diagnostic = diagnostic;
		this.requestId = requestId;
		this.ETag = ETag;
		this.LastMod = LastMod;
	}	
// backward compatibility
	public WebResponse(StringBuilder result, String type, String coding, int responseCode, String diagnostic, int requestId) {//deprecated
		this.result = result;
		this.type = type;
		this.coding = coding;
		this.responseCode = responseCode;
		this.diagnostic = diagnostic;
		this.requestId = requestId;
		this.ETag = null;
		this.LastMod = null;
	}	
}
