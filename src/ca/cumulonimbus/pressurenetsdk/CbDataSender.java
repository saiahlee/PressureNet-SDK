package ca.cumulonimbus.pressurenetsdk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONTokener;

import android.content.Context;
import android.os.AsyncTask;


/**
 * Securely send collected data to servers.
 * 
 * @author jacob
 *
 */

public class CbDataSender  extends AsyncTask<String, Integer, String> {

	private String responseText = "";
	private static final String PREFS_NAME = "pressureNETPrefs";
	private CbLocationManager locationManager;
	
	private CbSettingsHandler settings;
	
	private CbDataCollector dataCollector;
	
	private Context context;
	private String mAppDir;
	
	public CbDataSender(Context ctx) {
		this.context = ctx;
		setUpFiles();
	}
	
	public CbSettingsHandler getSettings() {
		return settings;
	}
	public void setSettings(CbSettingsHandler settings, CbLocationManager locationManager, CbDataCollector dataCollector) {
		this.settings = settings;
		this.locationManager = locationManager;
		this.dataCollector = dataCollector;
	}

	@Override
	protected String doInBackground(String... params) {
		//System.out.println("cb send do in bg");
		DefaultHttpClient client = new DefaultHttpClient();
		try {
			ArrayList<NameValuePair> nvps = new ArrayList<NameValuePair>();
			boolean isCbOb = true; // TODO: fix hack to determine the data type sent
			for(String singleParam : params) {
				String[] fromCSV = singleParam.split(",");
				String key = fromCSV[0];
				String value = fromCSV[1];
				// TODO: fix hack. put any lost commas back.
				if(fromCSV.length > 2) {
					for(int i = 2; i < fromCSV.length; i++) {
						value += "," + fromCSV[i];
					}
				}
				nvps.add(new BasicNameValuePair(key, value));
				if(key.equals("general_condition")) {
					isCbOb = false;
				}
			} 
			String serverURL = settings.getServerURL();
			//System.out.println("settings url " + serverURL);
			if(isCbOb) {
				// cb observation
				serverURL += "add/";
			} else {
				// current condition
				serverURL += "conditions/add/";
			}
			
			HttpPost httppost = new HttpPost(serverURL);
			httppost.setEntity(new UrlEncodedFormEntity(nvps));
			
			//System.out.println("executing post URL " + serverURL);
			
			HttpResponse resp = client.execute(httppost);
			HttpEntity responseEntity = resp.getEntity();

			String addResp = "";
			BufferedReader r = new BufferedReader(new InputStreamReader(
					responseEntity.getContent()));

			StringBuilder total = new StringBuilder();
			String line;
			if (r != null) {
				while ((line = r.readLine()) != null) {
					total.append(line);
				}
				addResp = total.toString();
			///	dataCollector.stopCollectingData();
				
			}
			//System.out.println("addresp " + addResp);
			
		} catch(ClientProtocolException cpe) {
			cpe.printStackTrace();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		} catch(ArrayIndexOutOfBoundsException aioobe) {
			aioobe.printStackTrace();
		}
		return responseText;
	}
	
	public void log(String message) {
		//logToFile(message);
		////System.out.println(message);
	}

    /**
     * Prepare to write a log to SD card. Not used unless logging enabled.
     */
    public void setUpFiles() {
    	try {
	    	File homeDirectory = context.getExternalFilesDir(null);
	    	if(homeDirectory!=null) {
	    		mAppDir = homeDirectory.getAbsolutePath();
	    	}
    	} catch (Exception e) {
    		e.printStackTrace();
    	}
    }

	@Override
	protected void onPostExecute(String result) {
		//System.out.println("data sender post execute " + result);
		if(locationManager!=null) {
			locationManager.stopGettingLocations();
		}
		super.onPostExecute(result);
	}
	
	/**
	 * Log data to SD card for debug purposes.
	 * To enable logging, ensure the Manifest allows writing to SD card.
	 * @param text
	 */
	public void logToFile(String text) {
		try {
			OutputStream output = new FileOutputStream(mAppDir + "/log.txt", true);
			String logString = (new Date()).toString() + ": " + text + "\n";
			output.write(logString.getBytes());
			output.close();
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		}
	}
	
}
