package plugins.N2NChat;

import freenet.client.filter.ContentFilter;
import freenet.clients.http.RedirectException;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.api.HTTPRequest;
import freenet.support.io.Closer;
import org.apache.commons.io.IOUtils;

import javax.tools.JavaCompiler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.FileNameMap;
import java.net.URI;
import java.net.URLConnection;

/**
 * Returns static files . Based off of Bombe's CSSWebInterfaceToadlet.
 */
public class StaticResourceToadlet extends Toadlet {

	public static String PATH = "/n2n-chat/static/";

	@Override
	public String path() {
		return PATH;
	}

	/**
	 *
	 * @param pr Used to constuct the Toadlet.
	 */
	public StaticResourceToadlet(PluginRespirator pr) {
		super(pr.getHLSimpleClient());
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		//Include starting slash.
		String fileName = '/'+uri.getPath().substring(PATH.length());
		URLConnection inputURL = getClass().getResource(fileName).openConnection();
		InputStream inputStream = null;
		byte[] output = new byte[0];
		try {
			inputURL.setUseCaches(false);
			inputStream = inputURL.getInputStream();
			String mimeType = getMimeType(fileName);
			if (inputStream != null) {
				//TODO: What can go through the filter?
				//Can filter.
				if (fileName.endsWith(".css")) {
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					ContentFilter.filter(inputStream, outputStream, mimeType, uri, null, null, null);
					output = outputStream.toByteArray();
				} else {
					//Raw copy.
					output = IOUtils.toByteArray(inputStream);
				}
			}
			writeReply(ctx, 200, mimeType, "OK", output, 0, output.length);
		} finally {
			Closer.close(inputStream);
		}
	}

	private static String getMimeType(String URLString) {
                URL urlName = new URL(URLString);
                URLConnection connect = urlName.openConnection();
		return connect.getContentType();
	}
}

