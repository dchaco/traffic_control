/*
 * Copyright 2015 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.comcast.cdn.traffic_control.traffic_router.core.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;


public class Fetcher {
	private static final Logger LOGGER = Logger.getLogger(Fetcher.class);
	protected static final String GET_STR = "GET";
	protected static final String POST_STR = "POST";
	protected static final String UTF8_STR = "UTF-8";
	protected static final int DEFAULT_TIMEOUT = 10000;
	protected int timeout = DEFAULT_TIMEOUT; // override if you want something different
	protected final Map<String, String> requestProps = new HashMap<String, String>();

	static {
		try {
			// TODO: make disabling self signed certificates configurable
			final SSLContext ctx = SSLContext.getInstance("SSL");
			ctx.init(null, new TrustManager[] {new DefaultTrustManager()}, new SecureRandom());
			SSLContext.setDefault(ctx);
			HttpsURLConnection.setDefaultSSLSocketFactory(ctx.getSocketFactory());
		} catch (Exception e) {
			LOGGER.warn(e,e);
		}
	}

	private static class DefaultTrustManager implements X509TrustManager {
		@Override
		public void checkClientTrusted(final X509Certificate[] arg0, final String arg1) throws CertificateException {}
		@Override
		public void checkServerTrusted(final X509Certificate[] arg0, final String arg1) throws CertificateException {}
		@Override
		public X509Certificate[] getAcceptedIssuers() { return null; }
	}

	protected HttpURLConnection getConnection(final String url, final String data, final String requestMethod, final long lastFetchTime) throws IOException {
		String method = GET_STR;

		if (requestMethod != null) {
			method = requestMethod;
		}

		LOGGER.info(method + "ing: " + url + "; timeout is " + timeout);

		final URLConnection connection = new URL(url).openConnection();

		connection.setIfModifiedSince(lastFetchTime);

		if (timeout != 0) {
			connection.setConnectTimeout(timeout);
			connection.setReadTimeout(timeout);
		}

		final HttpURLConnection http = (HttpURLConnection) connection;

		if (connection instanceof HttpsURLConnection) {
			final HttpsURLConnection https = (HttpsURLConnection) connection;
			https.setHostnameVerifier(new HostnameVerifier() {
				@Override
				public boolean verify(final String arg0, final SSLSession arg1) {
					return true;
				}
			});
		}

		http.setInstanceFollowRedirects(false);
		http.setRequestMethod(method);
		http.setAllowUserInteraction(true);

		for (final String key : requestProps.keySet()) {
			http.addRequestProperty(key, requestProps.get(key));
		}

		if (method.equals(POST_STR) && data != null) {
			http.setDoOutput(true); // Triggers POST.

			OutputStream output = null;

			try {
				output = http.getOutputStream();
				output.write(data.getBytes(UTF8_STR));
			} finally {
				if (output != null) {
					output.close(); // will throw IOException if there's an issue
				}
			}
		}

		connection.connect();

		return http;
	}

	public String fetchIfModifiedSince(final String url, final long lastFetchTime) throws IOException {
		return fetchIfModifiedSince(url, null, null, lastFetchTime);
	}

	public String fetch(final String url) throws IOException {
		return fetch(url, null, null);
	}

	private String fetchIfModifiedSince(final String url, final String data, final String method, final long lastFetchTime) throws IOException {
		final OutputStream out = null;
		try {
			final HttpURLConnection connection = getConnection(url, data, method, lastFetchTime);
			connection.getInputStream();

			if (connection.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
				return null;
			}

			final StringBuilder sb = new StringBuilder();
			final BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

			String input;

			while ((input = in.readLine()) != null) {
				sb.append(input);
			}

			in.close();

			return sb.toString();
		} finally {
			IOUtils.closeQuietly(out);
		}
	}

	public String fetch(final String url, final String data, final String method) throws IOException {
		return fetchIfModifiedSince(url, data, method, 0L);
	}

	@Override
	@SuppressWarnings("PMD.IfStmtsMustUseBraces")
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		final Fetcher fetcher = (Fetcher) o;

		if (timeout != fetcher.timeout) return false;
		return !(requestProps != null ? !requestProps.equals(fetcher.requestProps) : fetcher.requestProps != null);

	}

	@Override
	public int hashCode() {
		int result = timeout;
		result = 31 * result + (requestProps != null ? requestProps.hashCode() : 0);
		return result;
	}
}
