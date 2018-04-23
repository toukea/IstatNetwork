package istat.android.network.http;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;

/**
 * Created by istat on 18/09/17.
 */
public class HttpQueryResult implements HttpQueryResponse {
    HttpQueryResponse response;

    HttpQueryResult(HttpAsyncQuery.HttpQueryResponse resp) {
        this.response = resp;

    }

    @Override
    public String getMessage() {
        return response.getMessage();
    }

    public int getCode() {
        return response.getCode();
    }

    @SuppressWarnings("unchecked")
    public <T> T getBody() {
        return response.getBody();
    }

    public JSONObject getBodyAsJson() throws JSONException {
        return response.getBodyAsJson();
    }

    public <T> T getBodyAs(Class<T> cLass) {
        return response.getBodyAs(cLass);
    }

    public String getBodyAsString() {
        return response.getBodyAsString();
    }

    public String getHeader(String name) {
        return response.getHeader(name);
    }

    public long getHeaderAsLong(String name) {
        return getHeaderAsLong(name, 0);
    }

    public long getHeaderAsLong(String name, long defaultValue) {
        return response.getHeaderAsLong(name);
    }

    public int getHeaderAsInt(String name) {
        return getHeaderAsInt(name, 0);
    }

    public int getHeaderAsInt(String name, int defaultValue) {
        return response.getHeaderAsInt(name, defaultValue);
    }

    public Map<String, List<String>> getHeaders() {
        return response.getHeaders();
    }

    @Override
    public boolean isSuccess() {
        return true;
    }

    @Override
    public boolean hasError() {
        return false;
    }

    public Throwable getError() {
        return null;
    }

    @Override
    public boolean isAccepted() {
        return true;
    }

    @Override
    public HttpURLConnection getConnection() {
        return response.getConnection();
    }

    public List<String> getHeaders(String name) {
        return response.getHeaders().get(name);
    }
}
