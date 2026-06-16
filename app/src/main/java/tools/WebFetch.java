package tools;
/*
Version 1.0, 16-08-2021, First release by Livio (javalc6@gmail.com)
Version 2.0, 14-06-2026, OkHttpClient replaces HttpURLConnection.

IMPORTANT NOTICE, please read:

This software is licensed under the terms of the GNU GENERAL PUBLIC LICENSE,
please read the enclosed file license.txt or https://www.gnu.org/licenses/old-licenses/gpl-2.0-standalone.html

Note that this software is freeware and it is not designed, licensed or intended
for use in mission critical, life support and military purposes.

The use of this software is at the risk of the user.
*/
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import android.os.Build;
import android.util.Log;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class WebFetch {
    private String user_agent = null;
    private String ETag = null;
    private String LastMod = null;
    private URL server = null;
    private final static String tag = "WebFetch";
    private InputStream inStream;
    private String accept = null;

    // OkHttpClient is designed to be a singleton and shared across the application.
    // Creating a new instance for every request leads to resource exhaustion and potential thread leaks.
    private static final OkHttpClient client = getUnsafeBuilder()
            .connectTimeout(15000, TimeUnit.MILLISECONDS)
            .readTimeout(15000, TimeUnit.MILLISECONDS)
            // Disable OkHttp automatic redirects to handle them manually as in the original code
            .followRedirects(false)
            .followSslRedirects(false)
            .build();

    private Response currentResponse;

    public Request.Builder prepareRequestBuilder(String urlName) throws IOException {
        server = new URL(urlName);
        Request.Builder requestBuilder = new Request.Builder().url(urlName);

        requestBuilder.header("Host", server.getHost());
        if ((accept != null) && (!accept.isEmpty())) {
            requestBuilder.header("Accept", accept);
        }

        requestBuilder.header("Accept-Encoding", "gzip,deflate");

        if ((ETag != null) && (!ETag.isEmpty())) {
            requestBuilder.header("If-None-Match", ETag);
        }
        if ((LastMod != null) && (!LastMod.isEmpty())) {
            requestBuilder.header("If-Modified-Since", LastMod);
        }
        if (user_agent == null) {
            user_agent = "WebFetch/1 (Linux; U; Android " + Build.VERSION.RELEASE +
                    "; " + Build.MODEL + "/" + Build.MANUFACTURER + ")";
        }
        requestBuilder.header("User-Agent", user_agent);

        return requestBuilder;
    }

    public WebResponse fetchURL(String urlName, int requestId) throws IOException {
        return fetchURL(urlName, requestId, null);
    }

    // fetchURL(String urlName)
    public WebResponse fetchURL(String urlName, int requestId, String[] custom_header) throws IOException {
        Request.Builder reqBuilder = prepareRequestBuilder(urlName);
        if (custom_header != null) {
            reqBuilder.header(custom_header[0], custom_header[1]);
        }

        WebResponse resp = openStream(reqBuilder, requestId);
        if (inStream != null) {
            BufferedReader is;
            if (resp.coding != null && !resp.coding.isEmpty()) {
                is = new BufferedReader(new InputStreamReader(inStream, resp.coding));
            } else {
                is = new BufferedReader(new InputStreamReader(inStream));
            }
            StringBuilder result = new StringBuilder(8192);
            String line;
            while ((line = is.readLine()) != null) {
                result.append(line);
            }
            resp.result = result;
            is.close();
            closeStream();
        }

        if (currentResponse != null)
            currentResponse.close();

        return resp;
    }

    // openStream(Request.Builder reqBuilder, int requestId)
    public WebResponse openStream(Request.Builder reqBuilder, int requestId) throws IOException {
        int responseCode;
        int n = 0;

        do {
            Request request = reqBuilder.build();
            currentResponse = client.newCall(request).execute();
            responseCode = currentResponse.code();

            if ((responseCode == 301) || // HTTP_MOVED_PERM
                    (responseCode == 302) || // HTTP_MOVED_TEMP
                    (responseCode == 307) ||
                    (responseCode == 308)) {

                currentResponse.close(); // Close the previous connection before redirecting

                String urlName = currentResponse.header("Location");
                if (urlName != null) {
                    if (urlName.startsWith("/")) { // If path is relative, make it absolute
                        urlName = new URL(server, urlName).toExternalForm();
                    }
                }

                n++;
                if (n > 6) { // Maximum number of redirect attempts
                    return new WebResponse(null, null, null, responseCode, currentResponse.message(), requestId);
                }

                reqBuilder = prepareRequestBuilder(urlName);
            } else break;
        } while (true);

        String ctype = "";
        String cenc = "";
        String contype = "";

        ResponseBody body = currentResponse.body();
        okhttp3.MediaType mediaType = body.contentType();
        if (mediaType != null) {
            contype = mediaType.toString();
        }

        if (!contype.isEmpty()) {
            int hc = contype.indexOf(';');
            if (hc == -1) {
                ctype = contype.trim();
            } else {
                ctype = contype.substring(0, hc).trim();
                int charset = contype.indexOf("charset=");
                if (charset != -1) {
                    cenc = contype.substring(charset + 8).trim();
                }
            }
        }

        String diagnostic = currentResponse.message();
        if (responseCode == 303) { // HTTP_SEE_OTHER
            diagnostic += " " + currentResponse.header("Location");
        }

        inStream = body.byteStream();

        // If OkHttp hasn't automatically decompressed GZIP (e.g. if we forced the header)
        String contentEncoding = currentResponse.header("Content-Encoding");
        if ("gzip".equalsIgnoreCase(contentEncoding)) {
            try {
                inStream = new GZIPInputStream(inStream);
            } catch (java.io.EOFException ex) {//workaround for android bug: https://code.google.com/p/android/issues/detail?id=58637
            }
        }

        String etag = currentResponse.header("ETag");
        String lastmod = currentResponse.header("Last-Modified");

        if ((etag != null) || (lastmod != null)) {
            return new WebResponse(null, ctype, cenc, responseCode, diagnostic, requestId, etag, lastmod);
        } else {
            return new WebResponse(null, ctype, cenc, responseCode, diagnostic, requestId);
        }
    }

    // getStream()
    public InputStream getStream() {
        return inStream;
    }

    // closeStream()
    public void closeStream() throws IOException {
        if (inStream != null) {
            inStream.close();
        }
        if (currentResponse != null) {
            currentResponse.close();
        }
    }

    public static OkHttpClient.Builder getUnsafeBuilder() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N_MR1) {
//workaround to solve CertPathValidatorException: Trust anchor for certification path not found
            try {
                final X509TrustManager trustAllCerts = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                };

                final SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, new TrustManager[]{trustAllCerts}, new SecureRandom());

                final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                OkHttpClient.Builder builder = new OkHttpClient.Builder();
                builder.sslSocketFactory(sslSocketFactory, trustAllCerts);
                builder.hostnameVerifier((hostname, session) -> true);
                return builder;
            } catch (Exception e) {
                Log.d(tag, "getUnsafeBuilder: "+e.getMessage());
//fallback on safe builder
            }
        }
        return new OkHttpClient.Builder();
    }

    public void set_UA(String user_agent) {
        this.user_agent = user_agent;
    }
    public void set_ETag(String ETag) {
        this.ETag = ETag;
    }
    public void set_LastMod(String LastMod) {
        this.LastMod = LastMod;
    }
    public void set_Accept(String accept) {
        this.accept = accept;
    }
}