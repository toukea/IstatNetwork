package istat.android.network.http.tools;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;

import istat.android.network.http.HttpAsyncQuery;
import istat.android.network.http.HttpAsyncQuery.UploadProcessCallBack;
import istat.android.network.http.MultipartHttpQuery;
import istat.android.network.util.ToolKits.Stream;

public class WritenByteProcessCallBack extends UploadProcessCallBack<Integer> {
	int buffer = Stream.DEFAULT_BUFFER_SIZE;
	String encoding = Stream.DEFAULT_ENCODING;

	public WritenByteProcessCallBack() {

	}

	public WritenByteProcessCallBack(String encoding, int bufferSize) {
		this.encoding = encoding;
		this.buffer = bufferSize;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public void setBuffer(int buffer) {
		this.buffer = buffer;
	}

	public int getBuffer() {
		return buffer;
	}

	@Override
	public void onProceedStreamUpload(MultipartHttpQuery httpQuery,
			DataOutputStream request, InputStream stream, HttpAsyncQuery asyc)
			throws IOException {
		// TODO Auto-generated method stub
		byte[] b = new byte[buffer];
		int writen = 0;
		int totalWriten = 0;
		int uploadSize = stream.available();
		while ((writen = stream.read(b)) > -1) {
			if (httpQuery.isAborted()) {
				stream.close();
				httpQuery.getCurrentConnexion().disconnect();
				break;
			}
			request.write(b, 0, writen);
			totalWriten += writen;
			int writenPercentage = uploadSize > 0 ? (100 * totalWriten / uploadSize)
					: -1;
			publishProgression(totalWriten, uploadSize, writenPercentage);
		}
		stream.close();
	}

	@Override
	public void onUpdateUploadProcess(HttpAsyncQuery query, Integer... vars) {
		// TODO Auto-generated method stub

	}

}