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

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import net.imagej.updater.UpdateService;

import org.json.JSONObject;
import org.scijava.event.ContextDisposingEvent;
import org.scijava.event.EventHandler;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;
import org.scijava.usage.UsageService;
import org.scijava.usage.UsageStats;
import org.scijava.util.ByteArray;
import org.scijava.util.DigestUtils;
import org.scijava.util.IteratorPlus;

/**
 * Default service for uploading anonymous usage statistics.
 *
 * @author Curtis Rueden
 */
@Plugin(type = Service.class)
public class DefaultUsageUploadService extends AbstractService implements
	UsageUploadService
{

	@Parameter
	private LogService log;

	@Parameter
	private UsageService usageService;

	@Parameter
	private UpdateService updateService;

	private Timer timer;

	// -- UsageUploadService methods --

	@Override
	public synchronized void uploadUsageStatistics() {
		// get usage statistics
		final Map<String, UsageStats> stats = usageService.getStats();

		// convert and filter stats to JSON, then upload to the server
		final JSONObject json = json(stats);
		final String user = getAnonymizedUser();
		final String url = getServerURL();
		new JSONUploader(json, log).upload(user, url);
	}

	@Override
	public String getServerURL() {
		return "http://usage.imagej.net/stats.php";
	}

	@Override
	public String getAnonymizedUser() {
		// get array of bytes corresponding to the user name
		final ByteArray byteArray = new ByteArray(userName().getBytes());
		// append bytes corresponding to the first known MAC address
		byteArray.addAll(new ByteArray(macAddress()));
		// return the cryptographic hash of the bytes
		return DigestUtils.bestBase64(byteArray.copyArray());
	}

	// -- Service methods --

	@Override
	public void initialize() {
		// compile usage statistics once per hour
		final long rate = 1000 * 60 * 60; // one hour's worth of milliseconds
		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				uploadUsageStatistics();
			}
		}, rate, rate);
	}

	@Override
	public void dispose() {
		if (timer != null) timer.cancel();
		timer = null;
	}

	// -- Event handlers --

	/** Compiles usage statistics one last time, just before shutting down. */
	@EventHandler
	private void onEvent(
		@SuppressWarnings("unused") final ContextDisposingEvent evt)
	{
		// upload usage statistics one last time before shutting down
		uploadUsageStatistics();
	}

	// -- Helper methods --

	/** Gets the first known MAC address of this machine, or null if none. */
	private byte[] macAddress() {
		try {
			final Enumeration<NetworkInterface> en =
				NetworkInterface.getNetworkInterfaces();
			for (final NetworkInterface ni : new IteratorPlus<NetworkInterface>(en))
			{
				return ni.getHardwareAddress();
			}
		}
		catch (final SocketException exc) {
			log.error(exc);
		}
		return null;
	}

	private String userName() {
		return System.getProperty("user.name");
	}

	/** Builds a JSON object of aggregated usage statistics. */
	private JSONObject json(final Map<String, UsageStats> stats) {
		final JSONBuilder builder = new JSONBuilder(updateService, log);
		for (final UsageStats usage : stats.values()) {
			builder.append(usage);
		}
		return builder.getJSON();
	}

}
