package com.norrielm.radioalarm;

import java.io.IOException;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.util.Log;

/**
 * Handles the functionality of the MediaPlayer.
 */
public class Radio implements PlsParser.UrlMatchHandler, OnPreparedListener {

	private static final String TAG = "RADIO ALARM";
	private static final boolean DEBUG = false;

	private static MediaPlayer mPlayer;
	private String mRadioUrl;
	private PlayingHandler mPlayingHandler;
	
	/**
	 * A handler to inform the UI that the player is now playing.
	 */
	public interface PlayingHandler {
		void onPlaying(boolean isPlaying);
	}

	public Radio(String radioUrl, PlayingHandler handler) {
		this.mRadioUrl = radioUrl;
		this.mPlayingHandler = handler;
	}

	public void setRadioUrl(String radioUrl) {
		this.mRadioUrl = radioUrl;
	}
	/**
	 * Start the radio player. Called when the app is launched or play is ticked.
	 */
	private void launchPlayer() {
		if (DEBUG) Log.d(TAG, "launching player");
		// Initialise Player
		if (mRadioUrl.endsWith(".pls")) {
			// The stream will be initialised once a stream has been found.
			new PlsParser(this).execute(mRadioUrl);
			if (DEBUG) Log.d(TAG, "Looking for a stream to play...");
			return;
		} else {
			initPlayer(mRadioUrl);
		}
	}

	@Override
	public void onUrlFound(String result) {
		initPlayer(result);
	}

	public void play() {
		if (mPlayer == null) {
			// Initialise the player with a stream. It will play once initialised.
			launchPlayer();
			return;
		} else if (mPlayer.isPlaying()) {
			mPlayingHandler.onPlaying(mPlayer != null);
			return;
		}
		try {
			mPlayer.start();
			mPlayingHandler.onPlaying(true);
		} catch (IllegalArgumentException e) {
			if (DEBUG) Log.e(TAG, "Failed to start player " + mRadioUrl + " " + e.toString());
		} catch (IllegalStateException e) {
			if (DEBUG) Log.e(TAG, "Failed to start player " + mRadioUrl + " " + e.toString());
		}
	}

	/**
	 * Pause the player so that it can be resumed later.
	 */
	public void pause() {
		if (mPlayer == null || !mPlayer.isPlaying()) {
			return;
		}
		mPlayer.pause();
	}

	/**
	 * Pause the player so that it can be resumed later.
	 */
	public void stop() {
		if (mPlayer == null) {
			return;
		}
		mPlayer.stop();
		mPlayer = null;
	}

	/**
	 * Ensures that a single player exists.
	 */
	private MediaPlayer getPlayer() {
		stop();
		return new MediaPlayer();
	}

	/**
	 * Starts the specified radio stream
	 */
	private void initPlayer(String url) {
		try {
			mPlayer = getPlayer();
	    	mPlayer.setOnCompletionListener(new OnCompletionListener() {
				@Override
				public void onCompletion(MediaPlayer mp) {
					if (DEBUG) Log.d(TAG, "Playback complete");
					mPlayer = null;
					mPlayingHandler.onPlaying(false);
				}
	    	});
			mPlayer.setDataSource(url);
			mPlayer.setOnPreparedListener(this);
			mPlayer.prepareAsync();
			return;
		} catch (IllegalArgumentException e) {
			if (DEBUG) Log.e(TAG, "Failed to init player " + url + " " + e.toString());
		} catch (IllegalStateException e) {
			if (DEBUG) Log.e(TAG, "Failed to init player " + url + " " + e.toString());
		} catch (IOException e) {
			if (DEBUG) Log.e(TAG, "Failed to init player " + url + " " + e.toString());
		}
		// We were unable to initialise the player due to an exception.
		mPlayer = null;
		mPlayingHandler.onPlaying(false);
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		play();
	}
}
