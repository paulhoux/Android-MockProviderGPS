/*
 Copyright (C)2011 Paul Houx
 All rights reserved.
 
 Based on code written by Pedro Assuncao, see:
 http://pedroassuncao.com/2009/11/android-location-provider-mock/

 Redistribution and use in source and binary forms, with or without modification, 
 are permitted without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
 TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 POSSIBILITY OF SUCH DAMAGE.
*/

package nl.cowlumbus.android.mockgps;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class MockGpsProviderActivity extends Activity implements LocationListener {
	public static final String LOG_TAG = "MockGpsProviderActivity";	
	private static final String MOCK_GPS_PROVIDER_INDEX = "GpsMockProviderIndex";
	
	private MockGpsProvider mMockGpsProviderTask = null;
	private Integer mMockGpsProviderIndex = 0;
	
    /** Called when the activity is first created. */
    /* (non-Javadoc)
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        /** Use saved instance state if necessary. */
        if(savedInstanceState instanceof Bundle) {
        	/** Let's find out where we were. */
        	mMockGpsProviderIndex = savedInstanceState.getInt(MOCK_GPS_PROVIDER_INDEX, 0);
        }
        
        /** Setup GPS. */
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){ 
        	// use real GPS provider if enabled on the device
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        }
        else if(!locationManager.isProviderEnabled(MockGpsProvider.GPS_MOCK_PROVIDER)) {
        	// otherwise enable the mock GPS provider
        	locationManager.addTestProvider(MockGpsProvider.GPS_MOCK_PROVIDER, false, false,
        			false, false, true, false, false, 0, 5);
        	locationManager.setTestProviderEnabled(MockGpsProvider.GPS_MOCK_PROVIDER, true);
        }  
        
        if(locationManager.isProviderEnabled(MockGpsProvider.GPS_MOCK_PROVIDER)) {
        	locationManager.requestLocationUpdates(MockGpsProvider.GPS_MOCK_PROVIDER, 0, 0, this);

        	/** Load mock GPS data from file and create mock GPS provider. */
        	try {
        		// create a list of Strings that can dynamically grow
        		List<String> data = new ArrayList<String>();

        		/** read a CSV file containing WGS84 coordinates from the 'assets' folder
        		 * (The website http://www.gpsies.com offers downloadable tracks. Select
        		 * a track and download it as a CSV file. Then add it to your assets folder.)
        		 */			
        		InputStream is = getAssets().open("mock_gps_data.csv");
        		BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        		// add each line in the file to the list
        		String line = null;
        		while ((line = reader.readLine()) != null) {
        			data.add(line);
        		}

        		// convert to a simple array so we can pass it to the AsyncTask
        		String[] coordinates = new String[data.size()];
        		data.toArray(coordinates);

        		// create new AsyncTask and pass the list of GPS coordinates
        		mMockGpsProviderTask = new MockGpsProvider();
        		mMockGpsProviderTask.execute(coordinates);
        	} 
        	catch (Exception e) {}
        }
    }
    
    @Override
    public void onDestroy() {
    	super.onDestroy();
    	
    	// stop the mock GPS provider by calling the 'cancel(true)' method
    	try {
    		mMockGpsProviderTask.cancel(true);
    		mMockGpsProviderTask = null;
    	}
    	catch (Exception e) {}
    	
    	// remove it from the location manager
    	try {
    		LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    		locationManager.removeTestProvider(MockGpsProvider.GPS_MOCK_PROVIDER);
    	}
    	catch (Exception e) {}
    }
    
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
    	// store where we are before closing the app, so we can skip to the location right away when restarting
    	savedInstanceState.putInt(MOCK_GPS_PROVIDER_INDEX, mMockGpsProviderIndex);
    	super.onSaveInstanceState(savedInstanceState);
    }

	@Override
	public void onLocationChanged(Location location) {
		// show the received location in the view
		TextView view = (TextView) findViewById(R.id.text);
		view.setText( "index:" + mMockGpsProviderIndex
				+ "\nlongitude:" + location.getLongitude() 
				+ "\nlatitude:" + location.getLatitude() 
				+ "\naltitude:" + location.getAltitude() );		
	}

	@Override
	public void onProviderDisabled(String provider) {
		// TODO Auto-generated method stub		
	}


	@Override
	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub		
	}


	@Override
	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub		
	}
    

	/** Define a mock GPS provider as an asynchronous task of this Activity. */
	private class MockGpsProvider extends AsyncTask<String, Integer, Void> {
		public static final String LOG_TAG = "GpsMockProvider";
		public static final String GPS_MOCK_PROVIDER = "GpsMockProvider";
		
		/** Keeps track of the currently processed coordinate. */
		public Integer index = 0;

		@Override
		protected Void doInBackground(String... data) {			
			// process data
			for (String str : data) {
				// skip data if needed (see the Activity's savedInstanceState functionality)
				if(index < mMockGpsProviderIndex) {
					index++;
					continue;
				}				
				
				// let UI Thread know which coordinate we are processing
				publishProgress(index);
				
				// retrieve data from the current line of text
				Double latitude = null;
				Double longitude = null;
				Double altitude= null;
				try {
					String[] parts = str.split(",");
					latitude = Double.valueOf(parts[0]);
					longitude = Double.valueOf(parts[1]);
					altitude = Double.valueOf(parts[2]);
				}
				catch(NullPointerException e) { break; }		// no data available
				catch(Exception e) { continue; }				// empty or invalid line

				// translate to actual GPS location
				Location location = new Location(GPS_MOCK_PROVIDER);
				location.setLatitude(latitude);
				location.setLongitude(longitude);
				location.setAltitude(altitude);
				location.setTime(System.currentTimeMillis());

				// show debug message in log
				Log.d(LOG_TAG, location.toString());

				// provide the new location
				LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
				locationManager.setTestProviderLocation(GPS_MOCK_PROVIDER, location);
				
				// sleep for a while before providing next location
				try {
					Thread.sleep(200);
					
					// gracefully handle Thread interruption (important!)
					if(Thread.currentThread().isInterrupted())
						throw new InterruptedException("");
				} catch (InterruptedException e) {
					break;
				}
				
				// keep track of processed locations
				index++;
			}

			return null;
		}
		
		@Override
		protected void onProgressUpdate(Integer... values) {
			Log.d(LOG_TAG, "onProgressUpdate():"+values[0]);
			mMockGpsProviderIndex = values[0];
		}
	}
}