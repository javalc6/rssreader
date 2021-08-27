package tools;

import org.json.JSONException;

public interface FileHandler {
    String getMimeType();
    String encodeFile() throws JSONException;
    boolean decodeFile(String content) throws JSONException;
}
