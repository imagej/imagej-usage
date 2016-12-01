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

import java.util.HashMap;
import java.util.Map;

import org.scijava.Identifiable;
import org.scijava.Locatable;
import org.scijava.event.EventHandler;
import org.scijava.module.ModuleInfo;
import org.scijava.module.event.ModuleExecutedEvent;
import org.scijava.plugin.Plugin;
import org.scijava.service.AbstractService;
import org.scijava.service.Service;
import org.scijava.thread.ThreadService;

/**
 * Default service for tracking anonymous usage statistics.
 * <p>
 * Please note that that this implementation is <em>not</em> thread safe. It is
 * up to the caller to ensure that multiple threads do not attempt to modify
 * statistics at the same time. One way to do that (but not the only way) is to
 * only call {@link #increment(Object)} from the event dispatch thread (i.e.,
 * when {@link ThreadService#isDispatchThread()} returns true). And one easy way
 * to accomplish that is by calling the {@link UsageService} only from event
 * handler methods.
 * </p>
 * 
 * @author Curtis Rueden
 */
@Plugin(type = Service.class)
public class DefaultUsageService extends AbstractService
	implements UsageService
{

	/** Table of usage statistics. */
	private HashMap<String, UsageStats> stats = new HashMap<String, UsageStats>();

	// -- UsageService methods --

	@Override
	public Map<String, UsageStats> getStats() {
		return stats;
	}

	@Override
	public void clearStats() {
		// NB: Rather than calling stats.clear(), we allocate a new object
		// so that references to the old table are not modified. In this way,
		// any code that obtained the old table reference directly by calling
		// getStats() can continue working with it unimpeded.
		stats = new HashMap<String, UsageStats>();
	}

	@Override
	public UsageStats getUsage(final Object o) {
		if (!(o instanceof Identifiable && o instanceof Locatable)) {
			// only track objects with an identifier and a location
			return null;
		}
		final String id = ((Identifiable) o).getIdentifier();
		if (!stats.containsKey(id)) stats.put(id, new UsageStats(o));
		return stats.get(id);
	}

	@Override
	public void increment(final Object o) {
		final UsageStats usageStats = getUsage(o);
		if (usageStats == null) return;
		usageStats.increment();
	}

	// -- Event handlers --

	@EventHandler
	private void onEvent(final ModuleExecutedEvent evt) {
		final ModuleInfo info = evt.getModule().getInfo();
		increment(info);
	}

}
