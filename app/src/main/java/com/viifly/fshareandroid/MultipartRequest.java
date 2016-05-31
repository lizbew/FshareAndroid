package com.viifly.fshareandroid;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;

import org.apache.http.HttpEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

public class MultipartRequest extends Request<String> {
    private HttpEntity mHttpEntity;
    private final Response.Listener<String> mListener;

    public MultipartRequest(String url, InputStream inputStream, String name, long size,
                            Response.Listener<String> listener,
                            Response.ErrorListener errorListener) {
        super(Method.POST, url, errorListener);

        mListener = listener;
        mHttpEntity = buildMultipartEntity(inputStream, name, size);
    }

    private HttpEntity buildMultipartEntity(InputStream inputStream, String name, long size) {
        FileInputStreamContentBody fileBody = new FileInputStreamContentBody(inputStream, name, size);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addPart("uploadfile", fileBody);
        builder.addTextBody("client", Utils.getDeviceName());
        return builder.build();
    }

    @Override
    public String getBodyContentType() {
        return mHttpEntity.getContentType().getValue();
    }

    @Override
    public byte[] getBody() throws AuthFailureError {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            mHttpEntity.writeTo(bos);
        } catch (IOException e) {
            VolleyLog.e("IOException writing to ByteArrayOutputStream");
        }
        return bos.toByteArray();
    }

    @Override
    protected Response<String> parseNetworkResponse(NetworkResponse response) {
        String parsed;
        try {
            parsed = new String(response.data, HttpHeaderParser.parseCharset(response.headers));
        } catch (UnsupportedEncodingException e) {
            parsed = new String(response.data);
        }
        return Response.success(parsed, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(String response) {
        mListener.onResponse(response);
    }
}
