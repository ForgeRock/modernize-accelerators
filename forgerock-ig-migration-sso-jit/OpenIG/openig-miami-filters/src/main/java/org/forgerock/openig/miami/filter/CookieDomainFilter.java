/***************************************************************************
 *  Copyright 2019 ForgeRock AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ***************************************************************************/
package org.forgerock.openig.miami.filter;

import static org.forgerock.openig.el.Bindings.bindings;

import org.forgerock.http.Filter;
import org.forgerock.http.Handler;
import org.forgerock.http.protocol.Message;
import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openig.el.Bindings;
import org.forgerock.openig.heap.GenericHeaplet;
import org.forgerock.openig.heap.HeapException;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.NeverThrowsException;
import org.forgerock.util.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CookieDomainFilter implements Filter {

	private Logger LOGGER = LoggerFactory.getLogger(CookieDomainFilter.class);

	@Override
	public Promise<Response, NeverThrowsException> filter(Context context, Request request, Handler next) {
		Promise<Response, NeverThrowsException> promise = next.handle(context, request);
		return promise.thenOnResult(response -> process(response, bindings(context, request, response)));

	}

	private void process(Message<?> message, Bindings bindings) {
		LOGGER.debug("process()::Received response.");
		String location = message.getHeaders().getFirst("Location");
		location = location.replaceAll("miamitest.frdpcloud.com", "ig.dev.miami-accelerators.com");
		location = location.replaceAll("as.legacy.miami-accelerators.com", "ig.dev.miami-accelerators.com");
		message.getHeaders().remove("Location");
		message.getHeaders().add("Location", location);
	}

	/**
	 * Create and initialize the filter, based on the configuration. The filter
	 * object is stored in the heap.
	 */
	public static class Heaplet extends GenericHeaplet {

		/**
		 * Create the filter object in the heap, setting the header name and value for
		 * the filter, based on the configuration.
		 *
		 * @return The filter object.
		 * @throws HeapException Failed to create the object.
		 */
		@Override
		public Object create() throws HeapException {
			CookieDomainFilter filter = new CookieDomainFilter();
			return filter;
		}
	}
}
