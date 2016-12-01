/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2016 Board of Regents of the University of
 * Wisconsin-Madison, Broad Institute of MIT and Harvard, and Max Planck
 * Institute of Molecular Cell Biology and Genetics.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

package net.imagej.usage;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.scijava.log.LogService;
import org.scijava.util.DigestUtils;

/**
 * Uploads a JSON string to a server.
 *
 * @author Curtis Rueden
 */
public class JSONUploader {

	/** System properties to include in the uploaded usage report. */
	private static final String[] SYSTEM_PROPERTIES = { "user.country",
		"user.language", "user.timezone", "os.arch", "os.name", "os.version",
		"java.runtime.name", "java.runtime.version", "java.specification.name",
		"java.specification.vendor", "java.specification.version", "java.vendor",
		"java.version", "java.vm.name", "java.vm.specification.name",
		"java.vm.specification.vendor", "java.vm.specification.version",
		"java.vm.vendor", "java.vm.version" };

	/** JSON object to upload. */
	private final JSONObject json;

	private final LogService log;

	public JSONUploader(final JSONObject json, final LogService log) {
		this.json = json;
		this.log = log;
	}

	/**
	 * Sends the JSON string to the given URL, associated with the specified
	 * anonymized user.
	 *
	 * @see UsageUploadService#getServerURL()
	 * @see UsageUploadService#getAnonymizedUser()
	 */
	public void upload(final String user, final String url) {
		if (!uploadNeeded()) return; // NB: No statistics.
		json.put("user", user);
		addSystemProperties();
		try {
			final String raw = upload(url);
			handleResponse(raw);
		}
		catch (final IOException exc) {
			log.error("Cannot upload usage statistics", exc);
		}
	}

	// -- Helper methods --

	/** Determines whether there are any statistics to upload. */
	private boolean uploadNeeded() {
		final JSONArray sitesArray = jsonArray(json, "sites");
		if (sitesArray == null) return false; // no update sites declared
		for (int i = 0; i < sitesArray.length(); i++) {
			final JSONObject child = sitesArray.getJSONObject(i);
			final JSONArray statsArray = jsonArray(child, "stats");
			if (statsArray != null && statsArray.length() > 0) {
				// this update site has stats to upload
				return true;
			}
		}
		// could not find any statistics
		return false;
	}

	private void addSystemProperties() {
		for (final String key : SYSTEM_PROPERTIES) {
			addSystemProperty(key);
		}
	}

	private void addSystemProperty(String key) {
		final String value = System.getProperty(key);
		if (value != null) json.put(key.replaceAll("\\.", "_"), value);
	}

	private JSONArray jsonArray(final JSONObject obj, final String key) {
		if (!obj.has(key)) return null;
		final Object child = json.get("sites");
		if (!(child instanceof JSONArray)) return null;
		return (JSONArray) child;
	}

	/**
	 * Uploads the JSON data to a URL using POST.
	 * <p>
	 * Thanks to Alexandre Lavoie for <a
	 * href="http://stackoverflow.com/a/17181533">his code on Stack Overflow</a>.
	 * </p>
	 * 
	 * @return The response from the web server.
	 */
	private String upload(final String urlSpec) throws IOException {
		final byte[] data = DigestUtils.bytes(json.toString());
		final String contentLength = Integer.toString(data.length);

		final URL url = new URL(urlSpec);
		final HttpURLConnection connection =
			(HttpURLConnection) url.openConnection();
		connection.setDoOutput(true);
		connection.setDoInput(true);
		connection.setRequestMethod("POST");
		connection.setRequestProperty("Content-Type", "application/json");
		connection.setRequestProperty("charset", "UTF-8");
		connection.setRequestProperty("Content-Length", contentLength);

		final DataOutputStream wr =
			new DataOutputStream(connection.getOutputStream());
		wr.write(data);
		wr.close();

		 // retrieve response
		final BufferedReader in =
			new BufferedReader(new InputStreamReader(connection.getInputStream()));
		final StringBuilder response = new StringBuilder();
		while (true) {
			final String line = in.readLine();
			if (line == null) break;
			response.append(line);
			response.append("\n");
		}
		in.close();

		// close the connection
		connection.disconnect();

		return response.toString();
	}

	/** Handles a response from the server. */
	private void handleResponse(final String raw) {
		// NB: For now, we just log the response, and any errors that occurred.
		try {
			final JSONObject response = new JSONObject(raw);
			final String message = response.getString("message");
			log.info("Uploaded usage statistics with response: " + message);
		}
		catch (final JSONException exc) {
			log.error("Invalid response: " + raw, exc);
		}
	}

}
