/*
 * This file is part of WinRM.
 *
 * WinRM is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * WinRM is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with WinRM.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.xebialabs.winrm;

import com.xebialabs.winrm.exception.BlankValueRuntimeException;
import com.xebialabs.winrm.exception.InvalidFilePathRuntimeException;
import com.xebialabs.winrm.exception.WinRMRuntimeIOException;
import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 */
public class JdkHttpConnector implements HttpConnector {

	public enum Protocol {
		HTTP, HTTPS;

		public String get() {
			return toString().toLowerCase();
		}
	}

	private final URL targetURL;

	private final TokenGenerator tokenGenerator;

	private final Protocol protocol;

	enum AuthenticationMode {
		BASIC, KERBEROS

	}

	;

	private AuthenticationMode authenticationMode = AuthenticationMode.KERBEROS;


	public JdkHttpConnector(String host, int port, String username, String password, Protocol protocol) {
		this.protocol = protocol;
		targetURL = getURL(host, port);
		logger.debug("target URL is " + targetURL);
		tokenGenerator = new KerberosTokenGenerator(host, username, password);
	}

	public JdkHttpConnector(String host, int port, String username, String password) {
		this(host, port, username, password, Protocol.HTTP);
	}


	private URL getURL(String host, int port) {
		try {
			//Only http is supported....
			return new URL(protocol.get(), host, port, "/wsman");
		} catch (MalformedURLException e) {
			throw new WinRMRuntimeIOException("Cannot build a new URL using host " + host + " and port " + port, e);
		}
	}

	@Override
	public Document sendMessage(Document requestDocument, SoapAction soapAction) {
		Document responseDocument = null;
		try {
			final URLConnection urlConnection = targetURL.openConnection();
			HttpURLConnection con = (HttpURLConnection) urlConnection;

			if (con instanceof HttpsURLConnection) {
				HttpsURLConnection sslConn = (HttpsURLConnection) con;

				/*SSLSocketFactory factory =
						(SSLSocketFactory) properties.get("SSLSocketFactory");

				X509TrustManager tm =
						(X509TrustManager) properties.get("X509TrustManager");

				HostnameVerifier verifier =
						(HostnameVerifier) properties.get("HostnameVerifier");

				X509KeyManager km =
						(X509KeyManager) properties.get("X509KeyManager"); */

			}
			con.setDoInput(true);
			con.setDoOutput(true);
			con.setRequestMethod("POST");
			con.setRequestProperty("Content-Type", "application/soap+xml; charset=UTF-8");
			final String authToken = tokenGenerator.generateToken();
			if (authToken != null)
				con.addRequestProperty("Authorization", authToken);

			if (soapAction != null) {
				con.setRequestProperty("SOAPAction", soapAction.getValue());
			}

			final String requestDocAsString = toString(requestDocument);
			logger.debug("send message:request {}", requestDocAsString);
			BufferedWriter bw = new BufferedWriter(
					new OutputStreamWriter(
							con.getOutputStream()));
			bw.write(requestDocAsString, 0, requestDocAsString.length());
			bw.flush();
			bw.close();

			InputStream is = urlConnection.getInputStream();
			Writer writer = new StringWriter();
			try {
				int n;
				char[] buffer = new char[1024];
				Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				while ((n = reader.read(buffer)) != -1) {
					writer.write(buffer, 0, n);
				}
			} finally {
				IOUtils.closeQuietly(is);
			}

			if (logger.isDebugEnabled()) {
				for (int i = 0; i < con.getHeaderFields().size(); i++) {
					logger.debug("Header {} --> {}", con.getHeaderFieldKey(i), con.getHeaderField(i));
				}
			}

			final String text = writer.toString();
			logger.debug("send message:response {}", text);
			responseDocument = DocumentHelper.parseText(text);
			return responseDocument;

		} catch (BlankValueRuntimeException bvrte) {
			throw bvrte;
		} catch (InvalidFilePathRuntimeException ifprte) {
			throw ifprte;
		} catch (Exception e) {
			throw new WinRMRuntimeIOException("send message on " + targetURL + " error ", requestDocument, responseDocument, e);
		}
	}

	@Override
	public URL getURL() {
		return targetURL;
	}


	private String toString(Document doc) {
		StringWriter stringWriter = new StringWriter();
		XMLWriter xmlWriter = new XMLWriter(stringWriter, OutputFormat.createPrettyPrint());
		try {
			xmlWriter.write(doc);
			xmlWriter.close();
		} catch (IOException e) {
			throw new WinRMRuntimeIOException("error ", e);
		}
		return stringWriter.toString();
	}

	private static Logger logger = LoggerFactory.getLogger(JdkHttpConnector.class);
}
