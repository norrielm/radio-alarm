package com.norrielm.radioalarm;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import java.util.List;

import android.os.AsyncTask;
import android.util.Log;


/**
 * Helps to parse playlist streams. Starts the player when a stream has been found.
 * 
 * Based on the NPR PlsParser: https://code.google.com/p/npr-android-app/
 */
public class PlsParser extends AsyncTask<String, Void, Void> {

	private static final String TAG = "RADIO ALARM";
	private static final boolean DEBUG = false;

	private UrlMatchHandler mHandler;

	/**
	 * A handler to inform the player that a stream has been found in the playlist.
	 */
	public interface UrlMatchHandler {
		void onUrlFound(String result);
	}

	public PlsParser(UrlMatchHandler handler) {
		this.mHandler = handler;
	}

	@Override
	protected Void doInBackground(String... params) {
		String url = params[0];
		URLConnection urlConnection;
		try {
			urlConnection = new URL(url).openConnection();
			BufferedReader reader = 
					new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
			String result = getUrls(reader).get(0);
			if (DEBUG) Log.d(TAG, "Got stream " + result);
			// Initialise the stream
			mHandler.onUrlFound(result);
			if (DEBUG) Log.d(TAG, "Playing...");
			return null;
		} catch (MalformedURLException e) {
			if (DEBUG) Log.e(TAG, "Failed to parse url " + url + " " + e.toString());
		} catch (IOException e) {
			if (DEBUG) Log.e(TAG, "Failed to parse url " + url + " " + e.toString());
		}
		return null;
	}

	public List<String> getUrls(BufferedReader reader) {
		LinkedList<String> urls = new LinkedList<String>();
		while (true) {
			try {
				String line = reader.readLine();
				if (line == null) {
					break;
				}
				String url = parseLine(line);
				if (url != null && !url.isEmpty()) {
					if (DEBUG) Log.d(TAG, "Found url " + url);
					urls.add(url);
				}
			} catch (IOException e) {
				if (DEBUG) Log.e(TAG, "Failed to parse playlist " + e.toString());
			}
		}
		return urls;
	}

	private String parseLine(String line) {
		if (line == null) {
			return null;
		}
		String trimmed = line.trim();
		if (trimmed.indexOf("http") >= 0) {
			return trimmed.substring(trimmed.indexOf("http"));
		}
		return "";
	}
}