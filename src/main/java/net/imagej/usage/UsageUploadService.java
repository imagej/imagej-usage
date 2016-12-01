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

import net.imagej.ImageJService;

/**
 * Interface for service that uploads anonymous usage statistics.
 * 
 * @author Curtis Rueden
 */
public interface UsageUploadService extends ImageJService {

	/** Uploads anonymized usage statistics to the ImageJ server. */
	void uploadUsageStatistics();

	/** Gets the server URL where anonymized usage statistics should be sent. */
	String getServerURL();

	/**
	 * Gets a unique but anonymous identifier for the current user and machine.
	 * <p>
	 * This identifier is associated with batches of usage statistics, so that we
	 * can answer questions such as: 'What percentage of users used command X
	 * within the last 12 months?'
	 * </p>
	 * <p>
	 * Such identifiers are typically generated as hashes of identifying
	 * information (usernames, MAC addresses, etc.), which helps protect
	 * anonymity. Of course, if one knows a person's username, MAC address, etc.,
	 * one can compute the corresponding hash and cross-reference it with the
	 * database of anonymized information. But quick verifiability is (probably)
	 * not quick solvability!
	 * </p>
	 */
	String getAnonymizedUser();

}
