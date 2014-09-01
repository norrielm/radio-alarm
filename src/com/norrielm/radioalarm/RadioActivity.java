package com.norrielm.radioalarm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

/**
 * This activity will start the specified radio, which will stop when the activity is stopped.
 */
public class RadioActivity extends Activity implements OnItemSelectedListener {

	private static final String TAG = "RADIO ALARM";
	private static final boolean DEBUG = false;

	private static final String ALARM_HOUR_OF_DAY = "alarm-hour";
	private static final String ALARM_MIN = "alarm-min";
	private static final String RADIO_ALARM = "RadioAlarmPref";
	private static final String ALARM_ENABLED = "alarm-enabled";
	private static final String RADIO_URL = "radio-url";
	private static final int ALARM_REQUEST_CODE = 1;

	// http://fm4.orf.at
	private static final String AUSTRIAN_RADIO_LIVE_STREAM = 
			"http://mp3stream1.apasf.apa.at:8000";
	// http://www.bbc.co.uk/6music
	private static final String BBC_6_MUSIC_LIVE_PLAYLIST = 
			"http://www.bbc.co.uk/radio/listen/live/r6_aaclca.pls";
	private static final String[] RADIO_URLS = new String[]{
		AUSTRIAN_RADIO_LIVE_STREAM, 
		BBC_6_MUSIC_LIVE_PLAYLIST
	};

	private String radioUrl;
	private MediaPlayer player;
	private TextView alarmText;
	private CheckBox alarmCheck;
	private CheckBox playCheck;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		alarmText = (TextView) findViewById(R.id.alarmText);
		alarmCheck = (CheckBox) findViewById(R.id.alarm_checkbox);
		playCheck = (CheckBox) findViewById(R.id.play_checkbox);

		radioUrl = getChosenUrl();

		Spinner spinner = (Spinner) findViewById(R.id.radio_url_spinner);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
		        android.R.layout.simple_spinner_item, RADIO_URLS);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setSelection(radioUrl.equals(AUSTRIAN_RADIO_LIVE_STREAM) ? 0 : 1);
		spinner.setOnItemSelectedListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.d(TAG, "onResume");

		// Update UI
		updateAlarmStatusInUI();

		// Set the alarm for the next time.
		enableNextAlarm();

		// Start the player.
		if (isAlarmEnabled()) {
			launchPlayer();
		}
	}

	/**
	 * Start the radio player.
	 */
	public void launchPlayer() {
		if (checkWifiState()) {
			startPlayer();
		}
	}

	/**
	 * Initialises a player if necessary. Called when the app is launched or play is ticked.
	 */
	private void startPlayer() {
		if (player == null) {
			// Initialise Player
			if (radioUrl.endsWith(".pls")) {
				// The stream will be initialised once a stream has been found.
				new PlsParser().execute(radioUrl);
				Toast.makeText(this, "Locating stream in the playlist...", 
						Toast.LENGTH_LONG).show();
				return;
			} else {
				initPlayer(radioUrl);
			}
		}
		pause();
		play();
	}
	
	/**
	 * Starts the specified radio stream
	 */
	private void initPlayer(String url) {
		try {
			player = new MediaPlayer();
			player.setDataSource(url);
			player.prepare();
		} catch (IllegalArgumentException e) {
			if (DEBUG) Log.e(TAG, "Failed to init player " + url + " " + e.toString());
		} catch (IllegalStateException e) {
			if (DEBUG) Log.e(TAG, "Failed to init player " + url + " " + e.toString());
		} catch (IOException e) {
			if (DEBUG) Log.e(TAG, "Failed to init player " + url + " " + e.toString());
		}
	}

	/**
	 * Pause the player so that it can be resumed later.
	 */
	public void pause() {
		if (player == null || !player.isPlaying()) {
			return;
		}
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				player.pause();
				playCheck.setChecked(false);
			}
		});
	}

	/**
	 * Pause the player so that it can be resumed later.
	 */
	public void stop() {
		if (player == null) {
			return;
		}
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				player.stop();
				player = null;
				playCheck.setChecked(false);
			}
		});
	}

	private void play() {
		try {
			player.start();
			playCheck.setChecked(true);
		} catch (IllegalArgumentException e) {
			if (DEBUG) Log.e(TAG, "Failed to start player " + radioUrl + " " + e.toString());
		} catch (IllegalStateException e) {
			if (DEBUG) Log.e(TAG, "Failed to start player " + radioUrl + " " + e.toString());
		}
	}

	/**
	 * Make sure that WiFi is connected before attempting to stream the radio.
	 * This method will attempt to connect the WiFi automatically so that it can be turned off
	 * during the night.  
	 */
	private boolean checkWifiState() {
		WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		if (!manager.isWifiEnabled()) { 
			// Connect to wifi is not connected
			manager.setWifiEnabled(true);
		}
		if (manager.getConnectionInfo().getNetworkId() == -1) {
			return manager.reconnect();
		}
		return true;
	}

	@Override
	public void finish() {
		// Will keep playing until back button is clicked or the play checkbox is unticked.
		stop();
		super.finish();
	}

	/**
	 * Register an intent to launch the media player at the alarm time.
	 */
	private void enableNextAlarm() {
		long alarmTime = getNextAlarmFromStore();
		boolean enabled = isAlarmEnabled();
		if (alarmTime == -1) {
			storeAlarmEnabled(false);
			alarmCheck.setChecked(false);
		} else if (!enabled) {
			cancelAlarm();
			alarmCheck.setChecked(false);
		} else {
			setAlarm(alarmTime);
			Toast.makeText(this, "Radio Alarm set", Toast.LENGTH_SHORT).show();
			alarmCheck.setChecked(true);
		}
	}

	/**
	 * Assigned to the checkboxes in the layout file.
	 */
	public void onCheckboxClicked(View view) {
		boolean checked = ((CheckBox) view).isChecked();
		switch(view.getId()) {
		// Alarm
		case R.id.alarm_checkbox:
			if (checked) {
				storeAlarmEnabled(true);
				enableNextAlarm();
			} else {
				storeAlarmEnabled(false);
				enableNextAlarm();
			}
			updateAlarmStatusInUI();
			break;
			// Play
		case R.id.play_checkbox:
			if (checked) {
				launchPlayer();
			} else {
				pause();
			}
			break;
		}
	}

	public boolean isAlarmEnabled() {
		SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_READABLE);
		boolean enabled = prefs.getBoolean(ALARM_ENABLED, false);
		alarmCheck.setChecked(enabled);
		return enabled;
	}

	public void storeAlarmEnabled(boolean isEnabled) {
		SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_WRITEABLE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putBoolean(ALARM_ENABLED, isEnabled);
		editor.commit();
	}

	public long getNextAlarmFromStore() {
		SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_READABLE);
		int alarmHour = prefs.getInt(ALARM_HOUR_OF_DAY, -1);
		int alarmMin = prefs.getInt(ALARM_MIN, -1);
		return getNextAlarmTime(alarmHour, alarmMin);
	}

	/** 
	 * Sets an alarm to launch this activity at the given time.
	 */
	public void setAlarm(long time) {
		AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent radioIntent = new Intent(this, RadioActivity.class);
		PendingIntent pendingRadioIntent = PendingIntent.getActivity(this, ALARM_REQUEST_CODE, 
				radioIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		manager.set(AlarmManager.RTC_WAKEUP, time, pendingRadioIntent);
	}

	/** 
	 * Cancels any alarm to launch this activity.
	 */
	public void cancelAlarm() {
		AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent radioIntent = new Intent(this, RadioActivity.class);
		PendingIntent pendingRadioIntent = PendingIntent.getActivity(this, ALARM_REQUEST_CODE, 
				radioIntent, PendingIntent.FLAG_CANCEL_CURRENT);
		manager.cancel(pendingRadioIntent);
	}

	/**
	 * Update the UI with the current status of the alarm
	 */
	public void updateAlarmStatusInUI() {
		boolean enabled = isAlarmEnabled();
		long time = getNextAlarmFromStore();
		// Work out the status of the alarm
		java.text.DateFormat tf = DateFormat.getTimeFormat(this);
		java.text.DateFormat df = DateFormat.getLongDateFormat(this);
		final String alarmStatus =
				(time == -1) ? "Alarm not set" :
				((!enabled) ? "Alarm disabled" :
				String.format("Alarm scheduled at %s on %s.", tf.format(time), df.format(time)));
		// Update the UI
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				alarmText.setText(alarmStatus);	
			}
		});
		if (DEBUG) Log.i(TAG, alarmStatus);
	}

	/**
	 * Return the alarm time in milliseconds. Set today, if before the alarm time, or tomorrow.
	 */
	private long getNextAlarmTime(int hour, int mins) {
		if (hour == -1) {
			return -1;
		}
		// The next alarm time might be tomorrow.
		Calendar cal = Calendar.getInstance();
		if (cal.get(Calendar.HOUR_OF_DAY) >= hour && cal.get(Calendar.MINUTE) >= mins) {
			cal.add(Calendar.DATE, 1);
		} 
		cal.set(Calendar.HOUR_OF_DAY, hour);
		cal.set(Calendar.MINUTE, mins);
		cal.set(Calendar.SECOND, 0);

		return cal.getTimeInMillis();
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position,
			long id) {
		radioUrl = RADIO_URLS[position];
		storeChosenUrl(radioUrl);
		stop();
		launchPlayer();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {
		//
	}

	public String getChosenUrl() {
		SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_READABLE);
		String url = prefs.getString(RADIO_URL, RADIO_URLS[0]);
		return url;
	}

	public void storeChosenUrl(String url) {
		SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_WRITEABLE);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(RADIO_URL, url);
		editor.commit();
	}

	/**
	 * Assigned to the button in the layout file.
	 */
	public void showTimePickerDialog(View v) {
		DialogFragment newFragment = new TimePickerFragment();
		newFragment.show(getFragmentManager(), "timePicker");
	}

	/**
	 * Displays a dialog to choose the alarm time.
	 */
	public class TimePickerFragment extends DialogFragment 
	implements TimePickerDialog.OnTimeSetListener {

		@Override
		public Dialog onCreateDialog(Bundle savedInstanceState) {
			SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_READABLE);
			int hour = prefs.getInt(ALARM_HOUR_OF_DAY, -1);
			int minute = prefs.getInt(ALARM_MIN, -1);
			if (hour == -1) {
				// Use the current time as the default values for the picker
				final Calendar c = Calendar.getInstance();
				hour = c.get(Calendar.HOUR_OF_DAY);
				minute = c.get(Calendar.MINUTE);
			}

			// Create a new instance of TimePickerDialog and return it
			return new TimePickerDialog(getActivity(), this, hour, minute,
					DateFormat.is24HourFormat(getActivity()));
		}

		public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
			// Store the alarm time for later
			storeAlarm(hourOfDay, minute);
			// Now update the alarm
			storeAlarmEnabled(true);
			enableNextAlarm();
			updateAlarmStatusInUI();
		}

		public void storeAlarm(int hourOfDay, int minute) {
			SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_WRITEABLE);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt(ALARM_HOUR_OF_DAY, hourOfDay);
			editor.putInt(ALARM_MIN, minute);
			editor.commit();
		}
	}

	/**
	 * Helps to parse playlist streams. Starts the player when a stream has been found.
	 * 
	 * Based on the NPR PlsParser: https://code.google.com/p/npr-android-app/
	 */
	public class PlsParser extends AsyncTask<String, Void, String> {

		@Override
		protected String doInBackground(String... params) {
			String url = params[0];
			URLConnection urlConnection;
			try {
				urlConnection = new URL(url).openConnection();
				BufferedReader reader = 
						new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
				String result = getUrls(reader).get(0);
				if (DEBUG) Log.d(TAG, "Got stream " + result);
				initPlayer(result);
				return result;
			} catch (MalformedURLException e) {
				if (DEBUG) Log.e(TAG, "Failed to init player " + radioUrl + " " + e.toString());
			} catch (IOException e) {
				if (DEBUG) Log.e(TAG, "Failed to init player " + radioUrl + " " + e.toString());
			}
			return null;
		}

		protected void onPostExecute(String result) {			
			// Now that we have found a stream, play it.
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(RadioActivity.this, "Radio stream loaded", 
							Toast.LENGTH_SHORT).show();
					pause();
					play();
				}
			});
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
					if (DEBUG) Log.e(TAG, "Failed to init player " + radioUrl + " " + e.toString());
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
}