package mobi.boilr.boilr.activities;

import java.util.ArrayList;
import java.util.List;

import mobi.boilr.boilr.R;
import mobi.boilr.boilr.listeners.OnSwipeTouchListener;
import mobi.boilr.boilr.services.LocalBinder;
import mobi.boilr.boilr.services.StorageAndControlService;
import mobi.boilr.boilr.utils.Languager;
import mobi.boilr.boilr.utils.Log;
import mobi.boilr.boilr.utils.Themer;
import mobi.boilr.boilr.utils.VersionTracker;
import mobi.boilr.boilr.views.fragments.AboutDialogFragment;
import mobi.boilr.boilr.widget.AlarmListAdapter;
import mobi.boilr.libpricealarm.Alarm;
import android.app.Activity;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.SearchView.OnQueryTextListener;

public class AlarmListActivity extends ListActivity {

	private static int REQUEST_SETTINGS = 0, REQUEST_CREATE = 1;
	private static final int[] attrs = new int[] { R.attr.ic_action_search /*
																			 * index
																			 * 0
																			 */};
	private AlarmListAdapter adapter;
	private SearchView searchView;
	private StorageAndControlService mStorageAndControlService;
	private boolean mBound;
	private ServiceConnection mStorageAndControlServiceConnection = new ServiceConnection() {

		@SuppressWarnings("unchecked")
		@Override
		public void onServiceConnected(ComponentName className, IBinder binder) {
			mStorageAndControlService = ((LocalBinder<StorageAndControlService>) binder).getService();
			mBound = true;

			// Callback action performed after the service has been bound
			List<Alarm> alarms = mStorageAndControlService.getAlarms();
			adapter.clear();
			adapter.addAll(alarms);
			mStorageAndControlService.scheduleOffedAlarms();
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			mBound = false;
		}
	};

	private OnQueryTextListener queryListener = new OnQueryTextListener() {
		@Override
		public boolean onQueryTextSubmit(String query) {
			return true;
		}

		@Override
		public boolean onQueryTextChange(String newText) {
			adapter.getFilter().filter(newText);
			return true;
		}

	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Themer.applyTheme(this);
		Languager.setLanguage(this);
		VersionTracker.showChangeLog(this);
		
		setContentView(R.layout.alarm_list);
		PreferenceManager.setDefaultValues(this, R.xml.app_settings, false);

		getListView().setOnTouchListener(new OnSwipeTouchListener(this));

		adapter = new AlarmListAdapter(AlarmListActivity.this, new ArrayList<Alarm>());
		setListAdapter(adapter);

		Intent serviceIntent = new Intent(this, StorageAndControlService.class);
		startService(serviceIntent);
		bindService(serviceIntent, mStorageAndControlServiceConnection, Context.BIND_AUTO_CREATE);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.alarm_list, menu);
		searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
		searchView.setOnQueryTextListener(queryListener);
		/*
		 * Hack to keep the search icon consistent between themes. Without this
		 * the icon for the light theme is smaller than the one on the dark
		 * theme. By just_user on Stack Overflow http://stackoverflow.com/questions/10445760/how-to-change-the-default-icon-on-the-searchview-to-be-use-in-the-action-bar-on/18360563#18360563
		 */
		int searchImgId = getResources().getIdentifier("android:id/search_button", null, null);
		ImageView view = (ImageView) searchView.findViewById(searchImgId);
		TypedArray ta = obtainStyledAttributes(attrs);
		view.setImageResource(ta.getResourceId(0, R.drawable.ic_action_remove_light));
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		switch(item.getItemId()) {
			case R.id.action_add_alarm:
				startActivityForResult(new Intent(this, AlarmCreationActivity.class), REQUEST_CREATE);
				return true;
			case R.id.action_settings:
				startActivityForResult(new Intent(this, SettingsActivity.class), REQUEST_SETTINGS);
				return true;
			case R.id.action_about:
				(new AboutDialogFragment()).show(getFragmentManager(), "about");
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long layout) {
		Alarm alarm = adapter.getItem(position);
		Intent alarmSettingsIntent = new Intent(this, AlarmSettingsActivity.class);
		alarmSettingsIntent.putExtra(AlarmSettingsActivity.alarmID, alarm.getId());
		alarmSettingsIntent.putExtra(AlarmSettingsActivity.alarmType, alarm.getClass().getSimpleName());
		if(!alarm.isOn()){			
			v.setBackgroundColor(getResources().getColor(R.color.highlightblue));
		}
		startActivity(alarmSettingsIntent);
	}

	public void onToggleClicked(View view) {
		int id = (Integer) view.getTag();
		if(mBound) {
			mStorageAndControlService.toggleAlarm(id);
			adapter.notifyDataSetChanged();
		} else {
			Log.e(getString(R.string.not_bound, "AlarmListActivity"));
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUEST_SETTINGS && resultCode == SettingsActivity.RESULT_RESTART) {
			finish();
			startActivity(getIntent());
		} else if(requestCode == REQUEST_CREATE && resultCode == Activity.RESULT_OK) {
			int alarmID = data.getIntExtra("alarmID", Integer.MIN_VALUE);
			if(alarmID != Integer.MIN_VALUE) {
				if(mBound) {
					adapter.add(mStorageAndControlService.getAlarm(alarmID));
				} else {
					Log.e(getString(R.string.not_bound, "AlarmListActivity"));
				}
			}
		}
	}

	@Override
	protected void onDestroy() {
		if(mBound) {
			mStorageAndControlService.unscheduleOffedAlarms();
			unbindService(mStorageAndControlServiceConnection);
		} else {
			Log.e(getString(R.string.not_bound, "AlarmListActivity"));
		}
		super.onDestroy();
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onRestart() {
		super.onRestart();
	}

	@Override
	public void onResume() {
		super.onResume();
		adapter.notifyDataSetChanged();
	}

	public boolean ismBound() {
		return mBound;
	}

	public StorageAndControlService getmStorageAndControlService() {
		return mStorageAndControlService;
	}
}