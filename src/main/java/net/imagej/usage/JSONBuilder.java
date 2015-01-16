/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2015 Board of Regents of the University of
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

import java.io.File;
import java.util.HashMap;

import net.imagej.updater.UpdateService;
import net.imagej.updater.UpdateSite;

import org.json.JSONArray;
import org.json.JSONObject;
import org.scijava.log.LogService;
import org.scijava.util.FileUtils;

/**
 * Builds up a JSON object from usage statistics.
 * <p>
 * Here is an example structure:
 * </p>
 *
 * <pre>
 * {
 *     "user": "a87BcCD/2h394#EAg",
 *     "user_country": "US",
 *     "user_language": "en",
 *     "user_timezone": "America/Chicago",
 *     "os_arch": "x86_64",
 *     "os_name": "Mac OS X",
 *     "os_version": "10.9.4",
 *     "java_runtime_name": "Java(TM) SE Runtime Environment",
 *     "java_runtime_version": "1.6.0_65-b14-462-11M4609",
 *     "java_specification_name": "Java Platform API Specification",
 *     "java_specification_vendor": "Sun Microsystems Inc.",
 *     "java_specification_version": "1.6",
 *     "java_vendor": "Apple Inc.",
 *     "java_version": "1.6.0_65",
 *     "java_vm_name": "Java HotSpot(TM) 64-Bit Server VM",
 *     "java_vm_specification_name": "Java Virtual Machine Specification",
 *     "java_vm_specification_vendor": "Sun Microsystems Inc.",
 *     "java_vm_specification_version": "1.0",
 *     "java_vm_vendor": "Apple Inc.",
 *     "java_vm_version": "20.65-b04-462"
 *     "sites": [
 *         {
 *             "name": "ImageJ",
 *             "url": "http://update.imagej.net/",
 *             "stats": [
 *                 {
 *                     "id": "command:net.imagej.ui.swing.updater.ImageJUpdater",
 *                     "count": 3
 *                 },
 *                 {
 *                     "id": "command:org.scijava.plugins.commands.debug.SystemInformation",
 *                     "count": 6
 *                 },
 *                 {
 *                     "id": "legacy:ij.plugin.filter.Filters(\"edge\")",
 *                     "count": 15
 *                 }
 *             ]
 *         },
 *         {
 *             "name": "Fiji"
 *             "url": "http://fiji.sc/update/",
 *             "stats": [
 *                 {
 *                     "id": "script:plugins/Scripts/Image/Adjust/Scale_to_DPI.js",
 *                     "count": 4
 *                 },
 *                 {
 *                     "id": "legacy:fiji.SampleImageLoader(\"http://fiji.sc/samples/new-lenna.jpg\")",
 *                     "count": 362
 *                 }
 *             ]
 *         }
 *     ]
 * }
 * </pre>
 *
 * @author Curtis Rueden
 */
public class JSONBuilder {

	private final UpdateService updateService;
	private final LogService log;
	private final JSONObject jsonRoot;

	private final HashMap<String, JSONObject> sites =
		new HashMap<String, JSONObject>();

	public JSONBuilder(final UpdateService updateService, final LogService log) {
		this.updateService = updateService;
		this.log = log;
		jsonRoot = new JSONObject();
		jsonRoot.put("sites", new JSONArray());
	}

	/** Gets the JSON object. */
	public JSONObject getJSON() {
		return jsonRoot;
	}

	/** Appends the given usage statistics to the JSON structure. */
	public void append(final UsageStats usage) {
		final File file = getFile(usage);
		if (file == null) return;
		final UpdateSite updateSite = updateService.getUpdateSite(file);
		if (updateSite == null) return; // NB: No associated update site.
		if (!updateSite.isOfficial()) return; // NB: Not a known update site.
		final JSONObject jsonSite = jsonSite(updateSite);
		appendStats(jsonSite, usage);
	}

	// -- Helper methods --

	/** Gets the location of the given {@link UsageStats} as a {@link File}. */
	private File getFile(final UsageStats usage) {
		final String url = usage.getLocation();
		if (url == null) return null;
		final File file;
		try {
			file = FileUtils.urlToFile(url);
		}
		catch (final IllegalArgumentException exc) {
			final String id = usage.getIdentifier();
			if (log != null) {
				log.warn("No file for id '" + id + "' with location: " + url, exc);
			}
			return null;
		}
		return file;
	}

	/** Gets the JSON object corresponding to the given update site. */
	private JSONObject jsonSite(final UpdateSite updateSite) {
		final String siteURL = updateSite.getURL();
		if (!sites.containsKey(siteURL)) {
			final JSONObject jsonSite = new JSONObject();
			jsonSite.put("name", updateSite.getName());
			jsonSite.put("url", siteURL);
			jsonSite.put("stats", new JSONArray());
			jsonRoot.append("sites", jsonSite);
			sites.put(siteURL, jsonSite);
		}
		return sites.get(siteURL);
	}

	/** Appends the specified usage statistics to the given JSON site object. */
	private void appendStats(final JSONObject jsonSite, final UsageStats usage) {
		final JSONObject jsonUsage = new JSONObject();
		put(jsonUsage, "id", usage.getIdentifier());
		put(jsonUsage, "name", usage.getName());
		put(jsonUsage, "label", usage.getLabel());
		put(jsonUsage, "description", usage.getDescription());
		put(jsonUsage, "version", usage.getVersion());
		jsonUsage.put("count", usage.getCount());
		jsonSite.append("stats", jsonUsage);
	}

	/**
	 * Puts the given key/value pair into the specified JSON object, but only when
	 * the value is non-null.
	 */
	private void put(final JSONObject json, final String key, final String value)
	{
		if (value == null) return;
		json.put(key, value);
	}
}
