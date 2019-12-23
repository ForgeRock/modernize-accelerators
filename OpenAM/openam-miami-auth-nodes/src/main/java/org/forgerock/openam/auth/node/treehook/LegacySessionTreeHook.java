package org.forgerock.openam.auth.node.treehook;

import javax.inject.Inject;

import org.forgerock.http.protocol.Request;
import org.forgerock.http.protocol.Response;
import org.forgerock.openam.auth.node.api.TreeHook;
import org.forgerock.openam.auth.node.api.TreeHookException;
import org.forgerock.openam.auth.nodes.SetPersistentCookieNode;
import org.forgerock.openam.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.assistedinject.Assisted;
import com.iplanet.dpro.session.SessionException;

/**
 * A TreeHook for creating persistent cookies.
 */
@TreeHook.Metadata(configClass = SetPersistentCookieNode.Config.class)
public class LegacySessionTreeHook implements TreeHook {

	private final Session session;
	private final Response response;
	private final Request request;
	private Logger LOGGER = LoggerFactory.getLogger(LegacySessionTreeHook.class);

	/**
	 * The LegacySessionTreeHook constructor.
	 *
	 * @param session  the session.
	 * @param response the response.
	 * @param request  the request.
	 */
	@Inject
	public LegacySessionTreeHook(@Assisted Session session, @Assisted Response response, @Assisted Request request) {
		this.session = session;
		this.response = response;
		this.request = request;
	}

	@Override
	public void accept() throws TreeHookException {
		LOGGER.debug("Creating legacy cookie tree hook");
		String legacyCookie = null;
		try {
			legacyCookie = session.getProperty("legacyCookie");
			LOGGER.info("accept():: " + legacyCookie);
		} catch (SessionException e) {
			e.printStackTrace();
		}
		response.getHeaders().add("set-cookie", legacyCookie);
	}

}
