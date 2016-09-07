package istat.android.network.http;

import istat.android.network.util.ToolKits.Stream;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import istat.android.network.http.HttpAsyncQuery.HttpQueryResponse;
import istat.android.network.http.MultipartHttpQuery.UpLoadHandler;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

public final class HttpAsyncQuery extends
		AsyncTask<String, HttpQueryResponse, Void> {
	public final static int TYPE_GET = 1, TYPE_POST = 2, TYPE_PUT = 3,
			TYPE_HEAD = 4, TYPE_DELETE = 5, TYPE_COPY = 6, TYPE_PATCH = 7,
			TYPE_RENAME = 8, TYPE_MOVE = 9, DEFAULT_BUFFER_SIZE = 16384;
	public final static String DEFAULT_ENCODING = "UTF-8";
	HttpQueryCallBack mHttpCallBack;
	CancelListener mCancelListener;
	HttpQuery<?> mHttp;
	int type = TYPE_GET;
	String typeString;
	int buffersize = DEFAULT_BUFFER_SIZE;
	String encoding = DEFAULT_ENCODING;
	private long startTimeStamp = 0;
	private long endTimeStamp = 0;
	static final ConcurrentHashMap<Object, HttpAsyncQuery> taskQueue = new ConcurrentHashMap<Object, HttpAsyncQuery>();

	private HttpAsyncQuery(HttpQuery<?> http, HttpQueryCallBack callBack) {
		mHttpCallBack = callBack;
		mHttp = http;
		if (callBack != null && (callBack instanceof CancelListener)) {
			setCancelListener((CancelListener) callBack);
		}
	}

	@Override
	protected void onPreExecute() {
		// TODO Auto-generated method stub
		super.onPreExecute();
		// running = true;
		startTimeStamp = System.currentTimeMillis();
		taskQueue.put(mHttpCallBack != null ? mHttpCallBack : Math.random()
				+ "", this);
	}

	@Override
	protected Void doInBackground(String... urls) {
		// TODO Auto-generated method stub
		for (String url : urls) {
			if (isCancelled()) {
				break;
			}
			InputStream stream = null;
			Exception error = null;
			Log.d("HttpAsyncQuery.doInBackground", "type=" + type);
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
					stream = mHttp.doQuery(url, "MOVE");
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
			} catch (Exception e) {
				error = e;
			}
			HttpQueryResponse response = new HttpQueryResponse(stream, error,
					encoding, buffersize, this);
			if (!mHttp.isAborted() || !isCancelled()) {
				publishProgress(response);
			} else {
				Log.i("istat.http.asyncQuery.query", "was aborded");
			}
		}
		return null;
	}

	@Override
	protected void onProgressUpdate(HttpQueryResponse... values) {
		// TODO Auto-generated method stub
		HttpQueryResponse response = values.length > 0 ? values[0] : null;
		if (mHttpCallBack != null && !mHttp.isAborted() && !isCancelled()) {
			mHttpCallBack.onHttRequestComplete(response);
		}
	}

	@Override
	protected void onPostExecute(Void result) {
		// TODO Auto-generated method stub
		super.onPostExecute(result);
		// complete = true;
		// running = false;
		// Log.e("TAGI TAG", "post Execute");
		taskQueue.values().removeAll(Collections.singletonList(this));
	}

	@Override
	protected void onCancelled() {
		Log.d("HttpAsyncQuery", "onCancelled");
		endTimeStamp = System.currentTimeMillis();
		// if (mHttpCallBack != null && mHttpCallBack instanceof HttpCallBack) {
		// ((HttpCallBack) mHttpCallBack).onHttpAborted();
		// }
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

	public static HttpAsyncQuery doAsyncGet(HttpQuery<?> http, String... urls) {
		return doAsyncQuery(http, TYPE_GET, DEFAULT_BUFFER_SIZE,
				http.mOptions.encoding, null, urls);
	}

	public static HttpAsyncQuery doAsyncGet(HttpQuery<?> http,
			HttpQueryCallBack callBack, String... urls) {
		return doAsyncQuery(http, TYPE_GET, DEFAULT_BUFFER_SIZE,
				http.mOptions.encoding, callBack, urls);
	}

	public static HttpAsyncQuery doAsyncQuery(HttpQuery<?> http, int queryType,
			int buffersize, String encoding, HttpQueryCallBack callBack,
			QueryProcessCallBack<?> processCallBack, String... urls) {
		return doAsyncQuery(http, queryType, buffersize, encoding, callBack,
				processCallBack, null, urls);
	}

	public static HttpAsyncQuery doAsyncQuery(HttpQuery<?> http, int queryType,
			int buffersize, String encoding, HttpQueryCallBack callBack,
			String... urls) {
		return doAsyncQuery(http, queryType, buffersize, encoding, callBack,
				null, null, urls);
	}

	public static HttpAsyncQuery doAsyncPost(HttpQuery<?> http, String... urls) {
		return doAsyncQuery(http, TYPE_POST, DEFAULT_BUFFER_SIZE,
				http.mOptions.encoding, null, urls);
	}

	public static HttpAsyncQuery doAsyncPost(HttpQuery<?> http,
			HttpQueryCallBack callBack, String... urls) {
		return doAsyncQuery(http, TYPE_POST, DEFAULT_BUFFER_SIZE,
				http.mOptions.encoding, callBack, urls);
	}

	public static HttpAsyncQuery doAsyncPost(HttpQuery<?> http,
			HttpQueryCallBack callBack,
			UploadProcessCallBack<?> uploadCallback, String... urls) {
		HttpAsyncQuery query = doAsyncQuery(http, TYPE_POST,
				DEFAULT_BUFFER_SIZE, http.mOptions.encoding, callBack, urls);
		query.setUploadProcessCallBack(uploadCallback);
		return query;
	}

	public static HttpAsyncQuery doAsyncPost(MultipartHttpQuery http,
			int buffersize, String encoding, HttpQueryCallBack callBack,
			QueryProcessCallBack<?> processCallBack,
			CancelListener cancelCallback,
			UploadProcessCallBack<?> uploadCallBack, String... urls) {
		HttpAsyncQuery query = new HttpAsyncQuery(http, callBack);
		query.setProgressCallBack(processCallBack);
		query.setCancelListener(cancelCallback);
		query.setUploadProcessCallBack(uploadCallBack);
		query.type = TYPE_POST;
		query.encoding = encoding;
		query.buffersize = buffersize;
		query.execute(urls);
		return query;
	}

	boolean setUploadProcessCallBack(UploadProcessCallBack<?> uploadCallBack) {
		// TODO Auto-generated method stub
		if (uploadCallBack != null && mHttp instanceof MultipartHttpQuery) {
			MultipartHttpQuery multipartHttp = (MultipartHttpQuery) mHttp;
			multipartHttp.setUploadHandler(uploadCallBack);
			return true;
		}
		return false;
	}

	public static HttpAsyncQuery doAsyncQuery(HttpQuery<?> http, int queryType,
			int buffersize, String encoding, HttpQueryCallBack callBack,
			QueryProcessCallBack<?> processCallBack,
			CancelListener mOnQueryCancel, String... urls) {
		HttpAsyncQuery query = new HttpAsyncQuery(http, callBack);
		query.setProgressCallBack(processCallBack);
		query.setCancelListener(mOnQueryCancel);
		query.type = queryType;
		query.encoding = encoding;
		query.buffersize = buffersize;
		query.execute(urls);
		return query;
	}

	public boolean isComplete() {
		if (isCancelled())
			return false;
		return this.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	public boolean isRunning() {
		return this.getStatus().equals(AsyncTask.Status.PENDING);
	}

	public long getExecutionTime() {
		if (endTimeStamp <= startTimeStamp)
			return getDuration();
		return endTimeStamp - startTimeStamp;
	}

	public final boolean cancel() {
		Log.d("HttAsyncQuery", "cancel_start");
		if (mHttp.hasRunningRequest()) {
			Log.d("HttQuery", "cancel_has_running");
			new Thread(httpAbortRunnable).start();
		}
		return cancel(true);
	}

	private long getDuration() {
		return System.currentTimeMillis() - startTimeStamp;
	}

	// DEFAULT PROCESS CALLBACK IF USER DONT HAS DEFINE it Own
	QueryProcessCallBack<?> processCallBack = new QueryProcessCallBack<Integer>() {
		{
			this.query = HttpAsyncQuery.this;
		}

		@Override
		public String onBuildResponseBody(HttpURLConnection currentConnexion,
				InputStream stream, HttpAsyncQuery query) {
			// TODO Auto-generated method stub
			return Stream.streamToString(stream, buffersize, encoding,
					getQueryer().mHttp);
		}

		@Override
		public void onUpdateQueryProcess(HttpAsyncQuery query, Integer... vars) {
			// NOTHIG TO DO

		}

	};

	public boolean setProgressCallBack(QueryProcessCallBack<?> callBack) {
		if (callBack == null || processCallBack == callBack) {
			return false;
		}
		this.processCallBack = callBack;
		this.processCallBack.query = this;
		return true;
	}

	public static class HttpQueryResponse {
		Object body;
		Exception error;
		HttpAsyncQuery mAsyncQ;
		int code = -1;
		String message;
		HttpURLConnection connexion;
		Map<String, List<String>> headers = new HashMap<String, List<String>>();

		public static HttpQueryResponse getErrorInstance(Exception e) {
			return new HttpQueryResponse(null, e, null);
		}

		public int getCode() {
			return code;
		}

		public String getMessage() {
			return message;
		}

		HttpQueryResponse(InputStream stream, Exception e, HttpAsyncQuery asyncQ) {
			mAsyncQ = asyncQ;
			init(stream, Stream.DEFAULT_ENCODING, Stream.DEFAULT_BUFFER_SIZE, e);
		}

		HttpQueryResponse(InputStream stream, Exception e, String encoding,
				int buffersize, HttpAsyncQuery asyncQ) {
			mAsyncQ = asyncQ;
			init(stream, encoding, buffersize, e);
		}

		private void init(InputStream stream, String encoding, int buffersize,
				Exception e) {
			HttpQuery<?> http = mAsyncQ.mHttp;
			connexion = http.getCurrentConnexion();
			this.error = e;
			this.code = http.getCurrentResponseCode();
			this.message = http.getCurrentResponseMessage();
			if (connexion != null) {
				try {
					this.body = null;
					if (stream != null) {
						this.body = mAsyncQ.processCallBack.buildResponseBody(
								connexion, stream);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
					code = 0;
					this.error = ex;
				}
				this.headers = connexion.getHeaderFields();
			}
			if (e == null && !isSuccess() && !TextUtils.isEmpty(message)
					&& code > 0) {
				this.error = new HttpQueryException(code, message);
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

		public long getHeaderAslong(String name) {
			return getHeaderAslong(name, 0);
		}

		public long getHeaderAslong(String name, long deflt) {
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
			return !hasError();// error==null && isSuccessCode(code);
		}

		public boolean isAccepeted() {
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

		public String getBodyAsString() {
			if (body == null)
				return null;
			return body.toString();
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

	public static interface HttpQueryCallBack {
		public abstract void onHttRequestComplete(HttpQueryResponse result);
	}

	public static interface CancelListener {
		public abstract void onCancelled(HttpAsyncQuery asyncQ);
	}

	public static interface OnHttpQueryComplete extends HttpQueryCallBack {

		public abstract void onHttpRequestSuccess(HttpQueryResponse result);

		public abstract void onHttpRequestError(HttpQueryResponse result,
				HttpQueryException e);

		public abstract void onHttpRequestFail(Exception e);

		public abstract void onHttRequestComplete(HttpQueryResponse result);

		public abstract void onHttpAborted();
	}

	public static abstract class HttpCallBack implements OnHttpQueryComplete,
			CancelListener {
		public final void onHttRequestComplete(HttpQueryResponse resp) {
			if (resp.isAccepeted()) {
				if (resp.isSuccess()) {
					onHttpRequestSuccess(resp);
				} else {
					onHttpRequestError(resp,
							new HttpQueryException(resp.getError()));
				}
			} else {
				onHttpRequestFail(resp.getError());
			}
			onHttRequestCompleted(resp);
		}

		@Override
		public final void onCancelled(HttpAsyncQuery asyncQ) {
			// TODO Auto-generated method stub
			onHttpAborted();
		}

		public abstract void onHttRequestCompleted(HttpQueryResponse result);

	}

	public void setCancelListener(CancelListener listener) {
		if (listener != null) {
			this.mCancelListener = listener;
		}
	}

	public void addTocken(String unikToken) {
		taskQueue.put(unikToken, this);
	}

	public static HttpAsyncQuery getTask(HttpQueryCallBack callback) {
		return taskQueue.get(callback);
	}

	public static HttpAsyncQuery getTask(Object tocken) {
		return taskQueue.get(tocken);
	}

	public static List<HttpAsyncQuery> getTaskqueue() {
		return new ArrayList<HttpAsyncQuery>(taskQueue.values());
	}

	public static void cancelAll() {
		for (HttpAsyncQuery http : getTaskqueue()) {
			http.cancel(true);
		}
	}

	public static void cancel(Object tocken) {
		HttpAsyncQuery http = getTask(tocken);
		if (http != null) {
			http.cancel(true);
		}
	}

	<K, V> K getKeyByValue(Map<K, V> map, V value) {
		for (Map.Entry<K, V> entry : map.entrySet()) {
			if (value.equals(entry.getValue())) {
				return entry.getKey();
			}
		}
		return null;
	}

	public static abstract class UploadProcessCallBack<ProgressVar> implements
			UpLoadHandler {
		Handler handler;
		HttpAsyncQuery query;

		public UploadProcessCallBack(Handler handler) {
			this.handler = handler;
		}

		public UploadProcessCallBack() {
			try {
				this.handler = getHandler();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		private Handler getHandler() {
			if (handler == null) {
				handler = new Handler(Looper.getMainLooper());
			}
			return handler;
		}

		protected void onProcessFail(Exception e) {

		}

		Runnable publishRunner = new Runnable() {
			@Override
			public void run() {
				// TODO Auto-generated method stub
				onUpdateUploadProcess(query, processVars);
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
		public final void onProceedStreamUpload(MultipartHttpQuery httpQuery,
				DataOutputStream request, InputStream stream)
				throws IOException {
			// TODO Auto-generated method stub
			try {
				onProceedStreamUpload(httpQuery, request, stream, query);
			} catch (final Exception e) {
				e.printStackTrace();
				Handler tmpHandler = getHandler();
				if (tmpHandler != null) {
					getHandler().post(new Runnable() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							onProcessFail(e);
						}
					});
				}
			}
		}

		public abstract void onProceedStreamUpload(
				MultipartHttpQuery httpQuery, DataOutputStream request,
				InputStream stream, HttpAsyncQuery asyc) throws IOException;

		public abstract void onUpdateUploadProcess(HttpAsyncQuery query,
				ProgressVar... vars);
	}

	public static abstract class QueryProcessCallBack<ProgressVar> {
		Handler handler;
		HttpAsyncQuery query;

		public QueryProcessCallBack(Handler handler) {
			this.handler = handler;
		}

		public QueryProcessCallBack() {
			try {
				handler = getHandler();
			} catch (Exception e) {

			}
		}

		public int getConnexionContentLenght() {
			return query != null && query.mHttp != null
					&& query.mHttp.currentConnexion != null ? query.mHttp.currentConnexion
					.getContentLength() : 0;
		}

		public String getConnexionContentType() {
			return query != null && query.mHttp != null
					&& query.mHttp.currentConnexion != null ? query.mHttp.currentConnexion
					.getContentType() : null;
		}

		public HttpAsyncQuery getQueryer() {
			return query;
		}

		public HttpQuery<?> getQuery() {
			return getQueryer().mHttp;
		}

		private Handler getHandler() {
			if (handler == null) {
				handler = new Handler(Looper.getMainLooper());
			}
			return handler;
		}

		protected void onProcessFail(Exception e) {

		}

		Object buildResponseBody(HttpURLConnection connexion, InputStream stream) {
			try {
				return onBuildResponseBody(connexion, stream, query);
			} catch (final Exception e) {
				e.printStackTrace();
				Handler tmpHandler = getHandler();
				if (tmpHandler != null) {
					getHandler().post(new Runnable() {
						@Override
						public void run() {
							// TODO Auto-generated method stub
							onProcessFail(e);
						}
					});
				}
			}
			return null;
		}

		public void publishProgression(final ProgressVar... vars) {
			getHandler().post(new Runnable() {
				@Override
				public void run() {
					// TODO Auto-generated method stub
					onUpdateQueryProcess(query, vars);
				}
			});
		}

		public abstract Object onBuildResponseBody(HttpURLConnection connexion,
				InputStream stream, HttpAsyncQuery query);

		public abstract void onUpdateQueryProcess(HttpAsyncQuery query,
				ProgressVar... vars);
	}

}
