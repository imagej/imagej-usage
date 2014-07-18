/*
 * #%L
 * ImageJ software for multidimensional image processing and analysis.
 * %%
 * Copyright (C) 2009 - 2014 Board of Regents of the University of
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

import static org.junit.Assert.assertEquals;

import java.io.File;

import net.imagej.updater.UpdateService;
import net.imagej.updater.UpdateSite;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.scijava.Identifiable;
import org.scijava.Locatable;
import org.scijava.service.AbstractService;
import org.scijava.usage.UsageStats;

/**
 * Tests {@link JSONBuilder}.
 *
 * @author Curtis Rueden
 */
public class JSONBuilderTest {

	@Test
	public void testBuilder() {
		final MockUpdateService updateService = new MockUpdateService();
		final JSONBuilder builder = new JSONBuilder(updateService, null);

		final String initialJSON = builder.getJSON().toString();
		assertEquals("{\"sites\":[]}", initialJSON);

		final String siteNameA = "ImageJ";
		final String siteURLA = "http://update.imagej.net/";
		final String siteNameB = "Fiji";
		final String siteURLB = "http://fiji.sc/update/";

		final String id1 = "command:net.imagej.ui.swing.updater.ImageJUpdater";
		final long count1 = 3;
		builder.append(usageStats(id1, count1, updateService, siteNameA, siteURLA));

		final String id2 =
			"command:org.scijava.plugins.commands.debug.SystemInformation";
		final long count2 = 6;
		builder.append(usageStats(id2, count2, updateService, siteNameA, siteURLA));

		final String id3 = "legacy:ij.plugin.filter.Filters?edge";
		final long count3 = 15;
		builder.append(usageStats(id3, count3, updateService, siteNameA, siteURLA));

		final String id4 = "script:plugins/Scripts/Image/Adjust/Scale_to_DPI.js";
		final long count4 = 4;
		builder.append(usageStats(id4, count4, updateService, siteNameB, siteURLB));

		final String id5 =
			"legacy:fiji.SampleImageLoader?http://fiji.sc/samples/new-lenna.jpg";
		final long count5 = 362;
		builder.append(usageStats(id5, count5, updateService, siteNameB, siteURLB));

		final JSONObject completeJSON = builder.getJSON();

		// verify update sites
		final JSONArray sitesArray = completeJSON.getJSONArray("sites");
		assertEquals(2, sitesArray.length());
		final JSONObject siteA = sitesArray.getJSONObject(0);
		final JSONObject siteB = sitesArray.getJSONObject(1);
		assertEquals(siteNameA, siteA.get("name"));
		assertEquals(siteURLA, siteA.get("url"));
		assertEquals(siteNameB, siteB.get("name"));
		assertEquals(siteURLB, siteB.get("url"));

		// verify usage stats
		final JSONArray statsA = siteA.getJSONArray("stats");
		final JSONArray statsB = siteB.getJSONArray("stats");
		assertEquals(3, statsA.length());
		assertEquals(2, statsB.length());
		final JSONObject stat1 = statsA.getJSONObject(0);
		final JSONObject stat2 = statsA.getJSONObject(1);
		final JSONObject stat3 = statsA.getJSONObject(2);
		final JSONObject stat4 = statsB.getJSONObject(0);
		final JSONObject stat5 = statsB.getJSONObject(1);
		assertEquals(id1, stat1.get("id"));
		assertEquals(count1, stat1.getLong("count"));
		assertEquals(id2, stat2.get("id"));
		assertEquals(count2, stat2.getLong("count"));
		assertEquals(id3, stat3.get("id"));
		assertEquals(count3, stat3.getLong("count"));
		assertEquals(id4, stat4.get("id"));
		assertEquals(count4, stat4.getLong("count"));
		assertEquals(id5, stat5.get("id"));
		assertEquals(count5, stat5.getLong("count"));
	}

	// -- Helper methods --

	private UsageStats usageStats(final String id, final long count,
		final MockUpdateService updateService, final String siteName,
		final String siteURL)
	{
		updateService.setSiteName(siteName);
		updateService.setSiteURL(siteURL);
		final IdentifiableObject o = new IdentifiableObject(id, "file:/x");
		final UsageStats usageStats = new UsageStats(o);
		for (int i = 0; i < count; i++) {
			usageStats.increment();
		}
		return usageStats;
	}

	// -- Helper classes --

	private static class IdentifiableObject implements Identifiable, Locatable {

		private final String id, location;

		public IdentifiableObject(final String id, final String location) {
			this.id = id;
			this.location = location;
		}

		@Override
		public String getIdentifier() {
			return id;
		}

		@Override
		public String getLocation() {
			return location;
		}
	}

	private static class MockUpdateService extends AbstractService implements
		UpdateService
	{

		private String siteName;
		private String siteURL;

		public void setSiteName(final String siteName) {
			this.siteName = siteName;
		}

		public void setSiteURL(final String siteURL) {
			this.siteURL = siteURL;
		}

		@Override
		public UpdateSite getUpdateSite(final File file) {
			final UpdateSite updateSite =
				new UpdateSite(siteName, siteURL, null, null, null, null, 0);
			updateSite.setOfficial(true);
			return updateSite;
		}

	}

}
