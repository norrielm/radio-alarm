package com.norrielm.radioalarm;

import java.util.Calendar;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
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
 * This activity will start the specified radio station when an alarm goes off.
 */
public class RadioActivity extends Activity implements OnItemSelectedListener, Radio.PlayingHandler {

	private static final String TAG = "RADIO ALARM";
	private static final boolean DEBUG = false;

	private static final String ALARM_HOUR_OF_DAY = "alarm-hour";
	private static final String ALARM_MIN = "alarm-min";
	private static final String RADIO_ALARM = "RadioAlarmPref";
	private static final String ALARM_ENABLED = "alarm-enabled";
	private static final String RADIO_URL = "radio-url";
	private static final String STARTED_BY_ALARM = "alarm-intent";
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
	private CheckBox playCheck;
	private TextView alarmText;
	private CheckBox alarmCheck;
	private Radio mRadio;	

	/**
	 * Starts the radio once wifi is connected.
	 */
	private BroadcastReceiver wifiStateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
		    final String action = intent.getAction();
		    if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
		    	NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
		        boolean connected = info.isConnected();
		        if (connected) {
		        	if (DEBUG) Log.d(TAG, "wifi enabled");
		        	// Start the player.
		    		if (isAlarmEnabled()) {
		    			mRadio.play();
		    		}
		        }
		    }
		}	
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		alarmText = (TextView) findViewById(R.id.alarmText);
		alarmCheck = (CheckBox) findViewById(R.id.alarm_checkbox);
		playCheck = (CheckBox) findViewById(R.id.play_checkbox);

		radioUrl = getChosenUrl();
		mRadio = new Radio(radioUrl, this);

		final Spinner spinner = (Spinner) findViewById(R.id.radio_url_spinner);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
		        android.R.layout.simple_spinner_item, RADIO_URLS);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
		spinner.setSelection(radioUrl.equals(AUSTRIAN_RADIO_LIVE_STREAM) ? 0 : 1);
		// Avoid the spinner starting the radio when it is initialised.
		spinner.post(new Runnable() {
		    public void run() {
		        spinner.setOnItemSelectedListener(RadioActivity.this);
		    }
		});

		// Update the UI
		alarmCheck.setChecked(isAlarmEnabled());
		updateAlarmStatusInUI();

		// Check if we were started by an alarm.
		onNewIntent(getIntent());
	}

	 @Override
	 protected void onNewIntent(Intent intent) {
		if (intent.hasExtra(STARTED_BY_ALARM)) {
        	if (DEBUG) Log.d(TAG, "Started by alarm");
			// Start the player.
			if (isAlarmEnabled() && checkWifiState()) {
				mRadio.play();
			} // Note, a backup should play if this were to be used as an actual alarm.
			// An alarm has gone off. Now set the alarm for the next time.
			enableNextAlarm();
		} else {
        	if (DEBUG) Log.d(TAG, "Started by user");
		}
	    super.onNewIntent(intent);
	 }

	/**
	 * Make sure that WiFi is connected before attempting to stream the radio.
	 * This method will attempt to connect the WiFi automatically so that it can be turned off
	 * during the night.
	 */
	private boolean checkWifiState() {
		WifiManager manager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		if (!manager.isWifiEnabled()) {
			manager.setWifiEnabled(true);
		}
		if (manager.getConnectionInfo().getNetworkId() == -1) {
			// We are not connected.
			monitorWifiState();
			return manager.reconnect();
		}
		return true;
	}

	public void monitorWifiState() {
		// Register an intent to detect when we have reconnected
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
		registerReceiver(wifiStateReceiver, intentFilter);
	}

	@Override
	public void finish() {
		// Will keep playing until back button is clicked or the play checkbox is unticked.
		mRadio.stop();
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
		// Update UI
		updateAlarmStatusInUI();
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
			break;
			// Play
		case R.id.play_checkbox:
			if (checked) {
				mRadio.play();
			} else {
				mRadio.pause();
			}
			break;
		}
	}

	public boolean isAlarmEnabled() {
		SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_READABLE);
		boolean enabled = prefs.getBoolean(ALARM_ENABLED, false);
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
		if (time < System.currentTimeMillis()) {
			// Time is not in the future
			return;
		}
		AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent radioIntent = new Intent(this, RadioActivity.class);
		radioIntent.putExtra(STARTED_BY_ALARM, true);
		PendingIntent pendingRadioIntent = PendingIntent.getActivity(this, ALARM_REQUEST_CODE, 
				radioIntent, PendingIntent.FLAG_CANCEL_CURRENT | Intent.FLAG_ACTIVITY_NEW_TASK | 
				Intent.FLAG_ACTIVITY_CLEAR_TASK);
		manager.set(AlarmManager.RTC_WAKEUP, time, pendingRadioIntent);
	}

	/** 
	 * Cancels any alarm to launch this activity.
	 */
	public void cancelAlarm() {
		AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
		Intent radioIntent = new Intent(this, RadioActivity.class);
		PendingIntent pendingRadioIntent = PendingIntent.getActivity(this, ALARM_REQUEST_CODE, 
				radioIntent, PendingIntent.FLAG_CANCEL_CURRENT | Intent.FLAG_ACTIVITY_NEW_TASK | 
				Intent.FLAG_ACTIVITY_CLEAR_TASK);
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
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		radioUrl = RADIO_URLS[position];
		if (DEBUG) Log.d(TAG, "url selected: " + radioUrl);
		storeChosenUrl(radioUrl);
		mRadio.setRadioUrl(radioUrl);
		mRadio.stop();
		mRadio.play();
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
		}

		public void storeAlarm(int hourOfDay, int minute) {
			SharedPreferences prefs = getSharedPreferences(RADIO_ALARM, MODE_WORLD_WRITEABLE);
			SharedPreferences.Editor editor = prefs.edit();
			editor.putInt(ALARM_HOUR_OF_DAY, hourOfDay);
			editor.putInt(ALARM_MIN, minute);
			editor.commit();
		}
	}

	@Override
	public void onPlaying(final boolean isPlaying) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				playCheck.setChecked(isPlaying);
			}
		});
	}
}