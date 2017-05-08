package istat.android.network.http;

import istat.android.network.http.interfaces.DownloadHandler;
import istat.android.network.http.interfaces.ProgressionListener;
import istat.android.network.utils.StreamOperationTools;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

import istat.android.network.http.HttpAsyncQuery.HttpQueryResponse;
import istat.android.network.http.interfaces.UpLoadHandler;

import android.annotation.TargetApi;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;


public final class HttpAsyncQuery extends
        AsyncTask<String, HttpQueryResponse, HttpQueryResponse> {
    public final static int TYPE_GET = 1, TYPE_POST = 2, TYPE_PUT = 3,
            TYPE_HEAD = 4, TYPE_DELETE = 5, TYPE_COPY = 6, TYPE_PATCH = 7,
            TYPE_RENAME = 8, TYPE_MOVE = 9, DEFAULT_BUFFER_SIZE = 16384;
    public final static HashMap<Integer, String> METHOD_TYPE_NAME_MAP = new HashMap<Integer, String>() {
        {
            put(TYPE_COPY, "COPY");
            put(TYPE_MOVE, "MOVE");
            put(TYPE_GET, "GET");
            put(TYPE_POST, "POST");
            put(TYPE_PATCH, "PATCH");
            put(TYPE_HEAD, "HEAD");
            put(TYPE_PUT, "PUT");
            put(TYPE_DELETE, "DELETE");

        }
    };
    public final static String DEFAULT_ENCODING = "UTF-8";
    UpLoadHandler uploadHandler;
    HttpQueryCallback mHttpCallBack;
    CancelListener mCancelListener;
    final HttpQuery<?> mHttp;
    int type = TYPE_GET;
    int bufferSize = DEFAULT_BUFFER_SIZE;
    String encoding = DEFAULT_ENCODING;
    private long startTimeStamp = 0;
    private long endTimeStamp = 0;
    static final ConcurrentHashMap<Object, HttpAsyncQuery> taskQueue = new ConcurrentHashMap<Object, HttpAsyncQuery>();
    Executor mExecutor;

    HttpAsyncQuery(HttpQuery<?> http) {
        this.mHttp = http;
    }

    private HttpAsyncQuery(HttpQuery<?> http, HttpQueryCallback callBack) {
        this.mHttpCallBack = callBack;
        this.mHttp = http;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        notifyStarting();
        startTimeStamp = System.currentTimeMillis();
        this.id = createQueryId();
        taskQueue.put(this.id, this);
    }

    @Override
    protected HttpQueryResponse doInBackground(String... urls) {
        for (String url : urls) {
            if (isCancelled()) {
                break;
            }
            InputStream stream = null;
            Exception error = null;
            Log.d("HttpAsyncQuery", "doInBackground::type=" + type);
            try {
                switch (type) {
                    case TYPE_GET:
                        stream = mHttp.doGet(url);
                        break;
                    case TYPE_POST:
                        stream = mHttp.doPost(url);
                        break;
                    case TYPE_PUT:
                        stream = mHttp.doPut(url);
                        break;
                    case TYPE_HEAD:
                        stream = mHttp.doHead(url);
                        break;
                    case TYPE_COPY:
                        stream = mHttp.doCopy(url);
                        break;
                    case TYPE_RENAME:
                        stream = mHttp.doQuery(url, "RENAME");
                        break;
                    case TYPE_MOVE:
                        stream = mHttp.doQuery(url, "MOVE", true, true);
                        break;
                    case TYPE_DELETE:
                        stream = mHttp.doDelete(url);
                        break;
                    case TYPE_PATCH:
                        stream = mHttp.doPatch(url);
                        break;
                    default:
                        stream = mHttp.doGet(url);
                        break;
                }
                HttpQueryResponse response = new HttpQueryResponse(stream, error, this);
                return response;
            } catch (HttpQuery.AbortionException e) {
                e.printStackTrace();
                Log.i("HttpAsycQ", "doInBackground::was aborded");
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                HttpQueryResponse errorResponse = HttpQueryResponse.newErrorInstance(e, this);
                return errorResponse;
            }
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(HttpQueryResponse... values) {

    }

    public HttpQueryResponse getResult() throws IllegalAccessException {
        if (result != null) {
            return result;
        }
        if (!isPending()) {
            throw new IllegalAccessException("Current query can't has result for now. it is still pending.");
        }
        throw new IllegalAccessException("Current query has not response for now.");
    }

    HttpQueryResponse result;

    private void dispatchQueryResponse(HttpQueryResponse resp) {
        result = resp;
        executedRunnable.clear();
        try {
            boolean aborted = isAborted();
            if (resp.isAccepted() && !aborted) {
                if (resp.isSuccess()) {
                    notifySuccess(resp);
                } else {
                    HttpQueryError error;
                    if (resp.hasError() && resp.getError() instanceof HttpQueryError) {
                        error = (HttpQueryError) resp.getError();
                    } else {
                        error = new HttpQueryError(resp.getError());
                    }
                    resp.error = error;
                    notifyError(resp, error);
                }
            }
        } catch (Exception e) {
            notifyFail(e);
        }
    }

    private void notifyStarting() {
        int when = WHEN_BEGIN;
        ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(when);
        executeWhen(runnableList, when);
    }

    private void notifySuccess(HttpQueryResponse resp) {
        int when = WHEN_SUCCEED;
        if (mHttpCallBack != null) {
            mHttpCallBack.onHttpSuccess(resp);
        }
        notifyCompleted(resp);
        ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(when);
        executeWhen(runnableList, when);
    }

    private void notifyError(HttpQueryResponse resp, HttpQueryError error) {
        int when = WHEN_ERROR;
        if (mHttpCallBack != null) {
            mHttpCallBack.onHttpError(resp, error);
        }
        notifyCompleted(resp);
        ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(when);
        executeWhen(runnableList, when);
    }

    private void notifyCompleted(HttpQueryResponse resp) {
        int when = WHEN_ANYWAY;
        if (mHttpCallBack != null) {
            mHttpCallBack.onHttComplete(resp);
        }
        ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(when);
        executeWhen(runnableList, when);
    }

    private void notifyFail(Exception e) {
        int when = WHEN_FAILED;
        if (mHttpCallBack != null) {
            mHttpCallBack.onHttpFail(e);
        }
        notifyCompleted(HttpQueryResponse.newErrorInstance(e, this));
        ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(when);
        executeWhen(runnableList, when);
    }

    private void notifyAborted() {
        int when = WHEN_ABORTION;
        if (mHttpCallBack != null) {
            mHttpCallBack.onHttpAborted();
        }

        if (mCancelListener != null) {
            mCancelListener.onCanceling(this);
        }
        result = HttpQueryResponse.newErrorInstance(new HttpQuery.AbortionException(this.mHttp), this);
        ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(when);
        ConcurrentLinkedQueue<Runnable> runnableAnywayList = runnableTask.get(WHEN_ANYWAY);
        if (runnableList != null && runnableAnywayList != null) {
            runnableList.addAll(runnableAnywayList);
        } else {
            runnableList = runnableAnywayList;
        }
        executeWhen(runnableList, when);
    }

    public boolean isAborted() {
        return isCancelled() || mHttp.isAborted();
    }

    @Override
    protected void onPostExecute(HttpQueryResponse result) {
        super.onPostExecute(result);
        if (result == null) {
            return;
        }
        if (mHttpCallBack != null && !mHttp.isAborted() && !isCancelled()) {
            dispatchQueryResponse(result);
        }
        taskQueue.values().removeAll(Collections.singletonList(this));
    }

    @Override
    protected void onCancelled() {
        Log.i("HttpAsyncQuery", "onCancelled::canceListener::" + mCancelListener);
        Log.i("HttpAsyncQuery", "onCancelled::httpCallback::" + mHttpCallBack);
        endTimeStamp = System.currentTimeMillis();
        if (mCancelListener != null) {
            mCancelListener.onCancelled(this);
        }
        taskQueue.values().removeAll(Collections.singletonList(this));
        super.onCancelled();
    }

    private Runnable httpAbortRunnable = new Runnable() {
        public void run() {
            mHttp.abortRequest();
        }
    };

    /**
     * Deprecated, unse {@link AsyncHttp} as a Builder instead.
     *
     * @param http
     * @param url
     * @return
     */
    @Deprecated
    public static HttpAsyncQuery doAsyncGet(HttpQuery<?> http, String url) {
        return doAsyncQuery(http, TYPE_GET, DEFAULT_BUFFER_SIZE,
                http.mOptions.encoding, null, url);
    }

    /**
     * Deprecated, unse {@link AsyncHttp} as a Builder instead.
     *
     * @param http
     * @param callBack
     * @param url
     * @return
     */
    @Deprecated
    public static HttpAsyncQuery doAsyncGet(HttpQuery<?> http,
                                            HttpQueryCallback callBack, String url) {
        return doAsyncQuery(http, TYPE_GET, DEFAULT_BUFFER_SIZE,
                http.mOptions.encoding, callBack, url);
    }

    /**
     * Deprecated, unse {@link AsyncHttp} as a Builder instead.
     *
     * @param http
     * @param queryType
     * @param bufferSize
     * @param encoding
     * @param callBack
     * @param processCallBack
     * @param url
     * @return
     */
    @Deprecated
    public static HttpAsyncQuery doAsyncQuery(HttpQuery<?> http, int queryType,
                                              int bufferSize, String encoding, HttpQueryCallback callBack,
                                              HttpDownloadHandler<?> processCallBack, String url) {
        return doAsyncQuery(http, queryType, bufferSize, encoding, callBack,
                processCallBack, null, url);
    }

    /**
     * Deprecated, unse {@link AsyncHttp} as a Builder instead.
     *
     * @param http
     * @param queryType
     * @param bufferSize
     * @param encoding
     * @param callBack
     * @param url
     * @return
     */
    @Deprecated
    public static HttpAsyncQuery doAsyncQuery(HttpQuery<?> http, int queryType,
                                              int bufferSize, String encoding, HttpQueryCallback callBack,
                                              String url) {
        return doAsyncQuery(http, queryType, bufferSize, encoding, callBack,
                null, null, url);
    }

    /**
     * Deprecated, unse {@link AsyncHttp} as a Builder instead.
     *
     * @param http
     * @param url
     * @return
     */
    @Deprecated
    public static HttpAsyncQuery doAsyncPost(HttpQuery<?> http, String url) {
        return doAsyncQuery(http, TYPE_POST, DEFAULT_BUFFER_SIZE,
                http.mOptions.encoding, null, url);
    }

    /**
     * Deprecated, unse {@link AsyncHttp} as a Builder instead.
     *
     * @param http
     * @param callBack
     * @param url
     * @return
     */
    @Deprecated
    public static HttpAsyncQuery doAsyncPost(HttpQuery<?> http,
                                             HttpQueryCallback callBack, String url) {
        return doAsyncQuery(http, TYPE_POST, DEFAULT_BUFFER_SIZE,
                http.mOptions.encoding, callBack, url);
    }

    /**
     * Deprecated, unse {@link AsyncHttp} as a Builder instead.
     *
     * @param http
     * @param callBack
     * @param uploadCallback
     * @param url
     * @return
     */
    @Deprecated
    public static HttpAsyncQuery doAsyncPost(HttpQuery<?> http,
                                             HttpQueryCallback callBack,
                                             HttpUploadHandler<?> uploadCallback, String url) {
        HttpAsyncQuery query = doAsyncQuery(http, TYPE_POST,
                DEFAULT_BUFFER_SIZE, http.mOptions.encoding, callBack, url);
        query.setUploadHandler(uploadCallback);
        return query;
    }

    /**
     * Deprecated, unse {@link AsyncHttp} as a Builder instead.
     *
     * @param http
     * @param bufferSize
     * @param encoding
     * @param callBack
     * @param processCallBack
     * @param cancelCallback
     * @param uploadCallBack
     * @param urls
     * @return
     */
    @Deprecated
    public static HttpAsyncQuery doAsyncPost(MultipartHttpQuery http,
                                             int bufferSize, String encoding, HttpQueryCallback callBack,
                                             HttpDownloadHandler<?> processCallBack,
                                             CancelListener cancelCallback,
                                             HttpUploadHandler<?> uploadCallBack, String... urls) {
        HttpAsyncQuery query = new HttpAsyncQuery(http, callBack);
        query.setDownloadHandler(processCallBack);
        query.setCancelListener(cancelCallback);
        query.setUploadHandler(uploadCallBack);
        query.type = TYPE_POST;
        query.encoding = encoding;
        query.bufferSize = bufferSize;
        query.executeURLs(urls);
        return query;
    }

    boolean prepareQuery() {
        if (uploadHandler != null) {
            mHttp.setUploadHandler(uploadHandler);
            if (uploadHandler instanceof HttpUploadHandler) {
                ((HttpUploadHandler) uploadHandler).query = this;
            }
            return true;
        }
        return false;
    }

    void setUploadHandler(UpLoadHandler uploader) {
        this.uploadHandler = uploader;
    }

    public static HttpAsyncQuery doAsyncQuery(HttpQuery<?> http, int queryType,
                                              int bufferSize, String encoding, HttpQueryCallback callBack,
                                              HttpDownloadHandler<?> processCallBack,
                                              CancelListener cancelListener, String... urls) {
        HttpAsyncQuery query = new HttpAsyncQuery(http, callBack);
        query.setDownloadHandler(processCallBack);
        query.setCancelListener(cancelListener);
        query.type = queryType;
        query.encoding = encoding;
        query.bufferSize = bufferSize;
        query.executeURLs(urls);
        return query;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    void executeURLs(String... urls) {
        prepareQuery();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            if (mExecutor == null) {
                mExecutor = AsyncTask.THREAD_POOL_EXECUTOR;
            }
            executeOnExecutor(mExecutor, urls);
        } else {
            execute(urls);
        }
    }

    public boolean isCompleted() {
        if (isCancelled())
            return false;
        return this.getStatus().equals(AsyncTask.Status.FINISHED);
    }

    public boolean isRunning() {
        return this.getStatus().equals(Status.RUNNING) || (mHttp.hasRunningRequest() && !this.getStatus().equals(Status.FINISHED));
    }

    public boolean isPending() {
        return this.getStatus().equals(Status.PENDING) || mHttp.hasPendingRequest();
    }

    public long getExecutionTime() {
        if (endTimeStamp <= startTimeStamp)
            return getDuration();
        return endTimeStamp - startTimeStamp;
    }

    public final boolean cancel() {
        Log.i("HttAsyncQuery", "cancel_start, running=" + mHttp.hasRunningRequest() + ", pending=\" + mHttp.hasPendingRequest() +, aborted=" + mHttp.isAborted());
        if (mHttp.hasPendingRequest()) {
            Log.i("HttQuery", "cancel_has_running");
            new Thread(httpAbortRunnable).start();
        }
        notifyAborted();
        return cancel(true);
    }

    private long getDuration() {
        return System.currentTimeMillis() - startTimeStamp;
    }

    // DEFAULT PROCESS CALLBACK IF USER DON'T HAS DEFINE it Own
    HttpDownloadHandler<?> downloadHandler = getDefaultDownloader();

    public HttpAsyncQuery setDownloadHandler(final DownloadHandler downloader) {
        HttpAsyncQuery.HttpDownloadHandler<Integer> downloadHandler = new HttpAsyncQuery.HttpDownloadHandler<Integer>() {
            @Override
            public void onProgress(HttpAsyncQuery query, Integer... integers) {

            }

            @Override
            public Object onBuildResponseBody(HttpURLConnection connexion, InputStream stream) throws Exception {
                DownloadHandler handler = downloader != null ? downloader : getDefaultDownloader();
                return handler.onBuildResponseBody(connexion, stream);
            }

        };
        return setDownloadHandler(downloadHandler);
    }

    public HttpAsyncQuery setDownloadHandler(HttpDownloadHandler<?> downloader) {
        if (downloader == null) {
            downloader = getDefaultDownloader();
        }
        downloader.query = this;
        this.downloadHandler = downloader;
        return this;
    }

    public boolean isPaused() {
        return executionController.isPaused();
    }

    public void resume() {
        executionController.resume();
    }

    public void pause() {
        executionController.pause();
    }

    public boolean isSuccess() {
        try {
            HttpQueryResponse result = getResult();
            return result != null && result.isSuccess();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return false;
        }
    }

    HttpDownloadHandler<?> getDefaultDownloader() {
        return new HttpDownloadHandler<Integer>() {
            {
                this.query = HttpAsyncQuery.this;
            }

            @Override
            public String onBuildResponseBody(HttpURLConnection currentConnexion,
                                              InputStream stream) {
                try {
                    return StreamOperationTools.streamToString(executionController,
                            stream, bufferSize, encoding);
                } catch (Exception e) {
                    e.printStackTrace();
                    return "";
                }
            }

            @Override
            public void onProgress(HttpAsyncQuery query, Integer... vars) {
                // NOTHING TO DO
            }

        };
    }

    public static class HttpQueryResponse {
        Object body;
        Exception error;
        HttpAsyncQuery mAsyncQ;
        int code = -1;
        String message;
        HttpURLConnection connexion;
        Map<String, List<String>> headers = new HashMap<String, List<String>>();

        static HttpQueryResponse newErrorInstance(Exception e, HttpAsyncQuery asycQ) {
            try {
                return new HttpQueryResponse(null, e, asycQ);
            } catch (Exception ex) {
                ex.printStackTrace();
                return null;
            }
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        HttpQueryResponse(InputStream stream, Exception e, HttpAsyncQuery asyncQ) throws HttpQuery.AbortionException {
            mAsyncQ = asyncQ;
            init(stream, e);
        }

        private void init(InputStream stream, Exception e) throws HttpQuery.AbortionException {
            HttpQuery<?> http = mAsyncQ.mHttp;
            connexion = http.getCurrentConnection();
            this.error = e;
            this.code = http.getCurrentResponseCode();
            this.message = http.getCurrentResponseMessage();
            if (connexion != null) {
                try {
                    this.body = null;
                    if (stream != null) {
                        this.body = mAsyncQ.downloadHandler.buildResponseBody(
                                connexion, stream);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    if (ex instanceof IOException && this.mAsyncQ.isAborted()) {
                        throw new HttpQuery.AbortionException(this.mAsyncQ.mHttp, e);
                    }
                    code = 0;
                    this.error = ex;
                }
                this.headers = connexion.getHeaderFields();
            }
            if (e == null && !isSuccess() && !TextUtils.isEmpty(message)
                    && code > 0) {
                this.error = new istat.android.network.http.HttpQueryError(code, message, body);
            }
        }

        public boolean containHeader(String name) {
            return getHeaders() != null && getHeaders().containsKey(name);
        }

        public Map<String, List<String>> getHeaders() {
            return headers;
        }

        public String getHeader(String name) {
            if (connexion != null) {
                return connexion.getHeaderField(name);
            }
            return "";
        }

        public long getHeaderAsLong(String name) {
            return getHeaderAsLong(name, 0);
        }

        public long getHeaderAsLong(String name, long deflt) {
            if (connexion != null) {
                return connexion.getHeaderFieldDate(name, deflt);
            }
            return deflt;
        }

        public int getHeaderAsInt(String name) {
            return getHeaderAsInt(name, 0);
        }

        public int getHeaderAsInt(String name, int deflt) {
            if (connexion != null) {
                return connexion.getHeaderFieldInt(name, deflt);
            }
            return deflt;
        }

        public boolean hasError() {
            // Log.d("HttpAsyc", "haserror:" + error + ", code=" + code);
            // if (error != null) {
            // error.printStackTrace();
            // }
            return error != null || !isSuccessCode(code);
        }

        public boolean isSuccess() {
            return !hasError();
        }

        public boolean isAccepted() {
            return code > 0;
        }

        @SuppressWarnings("unchecked")
        public <T> T getBody() {
            if (body == null) {
                return null;
            }
            try {
                return (T) body;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public <T> T getBodyAs(Class<T> cLass) {
            if (body == null) {
                return null;
            }
            if (cLass.isAssignableFrom(body.getClass())) {
                return (T) body;
            }
            return null;
        }

        public <T> T optBody() {
            if (body == null) {
                return null;
            }
            try {
                return (T) body;
            } catch (Exception e) {
                return null;
            }
        }

        public String getBodyAsString() {
            if (body == null)
                return null;
            return body.toString();
        }

        public JSONObject getBodyAsJson() throws JSONException {
            if (body == null)
                return null;
            return new JSONObject(body.toString());
        }

        public Exception getError() {
            return error;
        }

        public static boolean isSuccessCode(int code) {
            return code > 0 && code >= 200 && code <= 299;
        }

        public static boolean isErrorCode(int code) {
            return !isSuccessCode(code);
        }

        public static boolean isClientErrorCode(int code) {
            return code > 0 && code >= 400 && code <= 499;
        }

        public static boolean isServerErrorCode(int code) {
            return code > 0 && code >= 500 && code <= 599;
        }
    }


    public static interface CancelListener {
        public abstract void onCanceling(HttpAsyncQuery asyncQ);

        public abstract void onCancelled(HttpAsyncQuery asyncQ);
    }

    public static interface HttpQueryCallback {
        abstract void onHttpSuccess(HttpQueryResponse resp);

        abstract void onHttpError(HttpQueryResponse resp,
                                  istat.android.network.http.HttpQueryError e);

        abstract void onHttpFail(Exception e);

        abstract void onHttComplete(HttpQueryResponse resp);

        abstract void onHttpAborted();
    }

    public boolean setCancelListener(CancelListener listener) {
        if (listener != null) {
            this.mCancelListener = listener;
            return true;
        }
        return false;
    }

    public void addTocken(String uniqueToken) {
        taskQueue.put(uniqueToken, this);
    }

    public static HttpAsyncQuery getTask(HttpQueryCallback callback) {
        return taskQueue.get(callback);
    }

    public static HttpAsyncQuery getTask(Object token) {
        return taskQueue.get(token);
    }

    public static List<HttpAsyncQuery> getTaskQueue() {
        return new ArrayList<HttpAsyncQuery>(taskQueue.values());
    }

    public static void cancelAll() {
        for (HttpAsyncQuery http : getTaskQueue()) {
            http.cancel(true);
        }
    }

    public static void cancel(Object token) {
        HttpAsyncQuery http = getTask(token);
        if (http != null) {
            http.cancel(true);
        }
    }

    public static abstract class HttpUploadHandler<ProgressVar> implements
            UpLoadHandler, ProgressionListener<ProgressVar> {
        Handler handler;
        HttpAsyncQuery query;

        public HttpUploadHandler(Handler handler) {
            if (handler == null) {
                try {
                    this.handler = getHandler();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                this.handler = handler;
            }
        }

        public HttpUploadHandler() {
            this(null);
        }

        private Handler getHandler() {
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }
            return handler;
        }

        void notifyProcessFail(Exception e) {
            if (onFail(e)) {
                throw new RuntimeException(e);
            }
        }

        protected boolean onFail(Exception e) {
            return !(this.query.isCancelled() || this.query.mHttp.isAborted());
        }

        Runnable publishRunner = new Runnable() {
            @Override
            public void run() {
                onProgress(query, processVars);
            }
        };
        ProgressVar[] processVars;

        public void publishProgression(final ProgressVar... vars) {
            Handler tmpHandler = getHandler();
            if (tmpHandler != null) {
                processVars = vars;
                tmpHandler.post(publishRunner);
            }
        }

        @Override
        public final void onUploadStream(long uploadSize, InputStream stream, OutputStream request)
                throws IOException {
            try {
                onProceedStreamUpload(uploadSize, request, stream, query);
            } catch (final Exception e) {
                e.printStackTrace();
                Handler tmpHandler = getHandler();
                if (tmpHandler != null) {
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            notifyProcessFail(e);
                        }
                    });
                }
            }
        }

        public abstract void onProceedStreamUpload(long uploadSize, OutputStream request,
                                                   InputStream stream, HttpAsyncQuery asyc) throws IOException;

        public abstract void onProgress(HttpAsyncQuery query,
                                        ProgressVar... vars);
    }

    public static abstract class HttpDownloadHandler<ProgressVar> implements DownloadHandler<Object>, ProgressionListener<ProgressVar> {
        Handler handler;
        HttpAsyncQuery query;

        public HttpDownloadHandler(Handler handler) {
            this.handler = handler;
        }

        public HttpDownloadHandler() {
            try {
                handler = getHandler();
            } catch (Exception e) {

            }
        }

        public int getConnetionContentLenght() {
            return query != null && query.mHttp != null
                    && query.mHttp.currentConnection != null ? query.mHttp.currentConnection
                    .getContentLength() : 0;
        }

        public String getConnetionContentType() {
            return query != null && query.mHttp != null
                    && query.mHttp.currentConnection != null ? query.mHttp.currentConnection
                    .getContentType() : null;
        }

        public HttpAsyncQuery getAsyncQuery() {
            return query;
        }

        public HttpQuery<?> getQuery() {
            return getAsyncQuery().mHttp;
        }

        private Handler getHandler() {
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }
            return handler;
        }

        void notifyProcessFail(final Exception e) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    onFail(e);
                }
            });
            throw new RuntimeException(e);
        }

        /**
         * @param e
         * @return whether or not this method should rethrow the Exception.
         */
        protected boolean onFail(Exception e) {
            return !(this.query.isCancelled() || this.query.mHttp.isAborted());
        }

        Object buildResponseBody(HttpURLConnection connexion, InputStream stream) throws Exception {
            try {
                return onBuildResponseBody(connexion, stream);
            } catch (final Exception e) {
                e.printStackTrace();
                Handler tmpHandler = getHandler();
                if (tmpHandler != null) {
                    notifyProcessFail(e);
                }
                throw new RuntimeException(e);
            }
        }

        public void publishProgression(final ProgressVar... vars) {
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    onProgress(query, vars);
                }
            });
        }


        public abstract void onProgress(HttpAsyncQuery query,
                                        ProgressVar... vars);

    }

    public final StreamOperationTools.OperationController executionController = new StreamOperationTools.OperationController() {
        @Override
        public boolean isStopped() {
            return !isRunning();
        }
    };

    /**
     * use {@link AsyncHttp#from(HttpQuery)} instead.
     *
     * @param http
     * @return
     */
    @Deprecated
    public final static AsyncHttp from(HttpQuery<?> http) {
        return new AsyncHttp(new HttpAsyncQuery(http));
    }

    /**
     * * use {@link AsyncHttp#fromDefaultHttp()} instead.
     *
     * @return
     */
    @Deprecated
    public final static AsyncHttp fromDefaultHttp() {
        SimpleHttpQuery http = new SimpleHttpQuery();
        return new AsyncHttp(new HttpAsyncQuery(http));
    }

    /**
     * * use {@link AsyncHttp#fromMultipartHttp()} instead.
     *
     * @return
     */
    @Deprecated
    public final static AsyncHttp fromMultipartHttp() {
        MultipartHttpQuery http = new MultipartHttpQuery();
        return new AsyncHttp(new HttpAsyncQuery(http));
    }

    public HttpPromise then(Runnable runnable) {
        HttpPromise promise = new HttpPromise(this);
        promise.then(runnable);
        return promise;
    }

    public HttpPromise then(PromiseCallback callback) {
        HttpPromise promise = new HttpPromise(this);
        promise.then(callback);
        return promise;
    }

    public HttpPromise error(Runnable runnable) {
        HttpPromise promise = new HttpPromise(this);
        promise.error(runnable);
        return promise;
    }

    public HttpPromise error(WhenCallback callback) {
        HttpPromise promise = new HttpPromise(this);
        promise.error(callback);
        return promise;
    }

    public HttpPromise error(PromiseCallback callback, int when) {
        HttpPromise promise = new HttpPromise(this);
        promise.error(callback, when);
        return promise;
    }

    public final static int WHEN_BEGIN = -1;
    public final static int WHEN_ANYWAY = 0;
    public final static int WHEN_SUCCEED = 1;
    public final static int WHEN_ERROR = 2;
    public final static int WHEN_ABORTION = 3;
    public final static int WHEN_FAILED = 4;

    public static interface WhenCallback {
        public void onWhen(HttpQueryResponse resp, HttpAsyncQuery query, int when);
    }

    public static interface PromiseCallback {
        public void onPromise(HttpQueryResponse resp, HttpAsyncQuery query);
    }

    final ConcurrentHashMap<Runnable, Integer> executedRunnable = new ConcurrentHashMap<Runnable, Integer>();
    final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Runnable>> runnableTask = new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<Runnable>>();

    public HttpAsyncQuery runWhen(final WhenCallback callback, final int... when) {
        if (callback == null)
            return this;
        return runWhen(new Runnable() {
            @Override
            public void run() {
                HttpQueryResponse resp = null;
                int when = WHEN_ANYWAY;
                try {
                    resp = getResult();
                    when = executedRunnable.get(this);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
                callback.onWhen(resp, HttpAsyncQuery.this, when);
            }
        }, when);
    }

    public HttpAsyncQuery runWhen(Runnable runnable, int... when) {
        if (runnable == null) {
            return this;
        }
        for (int value : when) {
            addWhen(runnable, value);
        }
        return this;
    }

    private void addWhen(Runnable runnable, int conditionTime) {
        if (!isWhenContain(runnable, conditionTime)) {
            ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(conditionTime);
            if (runnableList == null) {
                runnableList = new ConcurrentLinkedQueue<Runnable>();
            }
            runnableList.add(runnable);
            runnableTask.put(conditionTime, runnableList);
        }
    }

    private boolean isWhenContain(Runnable run, int conditionTime) {
        ConcurrentLinkedQueue<Runnable> runnableList = runnableTask.get(conditionTime);
        if (runnableList == null || runnableList.isEmpty()) {
            return false;
        }
        return runnableList.contains(run);
    }

    private void executeWhen(ConcurrentLinkedQueue<Runnable> runnableList, int when) {
        if (runnableList != null && runnableList.size() > 0) {
            for (Runnable runnable : runnableList) {
                if (!executedRunnable.contains(runnable)) {
                    runnable.run();
                    executedRunnable.put(runnable, when);
                }
            }
        }
    }

    public final static class HttpPromise {

        HttpAsyncQuery query;

        public HttpAsyncQuery getQuery() {
            return query;
        }

        HttpPromise(HttpAsyncQuery query) {
            this.query = query;
        }

        public HttpPromise then(final PromiseCallback callback) {
            if (callback == null) {
                return this;
            }
            query.runWhen(new WhenCallback() {
                @Override
                public void onWhen(HttpQueryResponse resp, HttpAsyncQuery query, int when) {
                    callback.onPromise(resp, query);
                }
            }, WHEN_SUCCEED);
            return this;
        }

        public HttpPromise then(Runnable runnable) {
            if (runnable == null) {
                return this;
            }
            query.runWhen(runnable, WHEN_SUCCEED);
            return this;
        }

        public HttpPromise error(final PromiseCallback pCallback, int when) {
            if (pCallback == null) {
                return this;
            }
            WhenCallback callback = new WhenCallback() {
                @Override
                public void onWhen(HttpQueryResponse resp, HttpAsyncQuery query, int when) {
                    pCallback.onPromise(resp, query);
                }
            };
            if (when != WHEN_FAILED && when != WHEN_ERROR && when != WHEN_ABORTION) {
                query.runWhen(callback, WHEN_FAILED, WHEN_ERROR, WHEN_ABORTION);
            } else {
                query.runWhen(callback, when);
            }
            return this;
        }

        public void error(WhenCallback callback) {
            if (callback == null) {
                return;
            }
            query.runWhen(callback, WHEN_FAILED, WHEN_ERROR, WHEN_ABORTION);
        }

        public HttpPromise error(Runnable runnable) {
            if (runnable == null) {
                return this;
            }
            query.runWhen(runnable, WHEN_FAILED, WHEN_ERROR, WHEN_ABORTION);
            return this;
        }
    }

    public boolean dismissAllRunWhen() {
        boolean isEmpty = runnableTask.isEmpty();
        runnableTask.clear();
        return !isEmpty;
    }

    public boolean dismissRunWhen(int... when) {
        boolean isEmpty = false;
        for (int i : when) {
            ConcurrentLinkedQueue<Runnable> runnables = runnableTask.get(i);
            if (runnables != null) {
                isEmpty &= runnables.isEmpty();
                runnables.clear();
            }
        }
        return !isEmpty;
    }

    public boolean dismissCallback() {
        boolean dismiss = mHttpCallBack != null;
        mHttpCallBack = null;
        return dismiss;
    }

    public long getStartTimeStamp() {
        return startTimeStamp;
    }

    public long getEndTimeStamp() {
        return endTimeStamp;
    }

    String id;

    static String createQueryId() {
        long time = System.currentTimeMillis();
        while (taskQueue.contains(time + "")) {
            if (taskQueue.contains(time)) {
                time++;
            }
        }
        return time + "";
    }

    public String getID() {
        return this.id;
    }

    public HttpQuery<?> getHttpQuery() {
        return mHttp;
    }
}
