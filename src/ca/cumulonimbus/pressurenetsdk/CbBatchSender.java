package ca.cumulonimbus.pressurenetsdk;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Messenger;
import android.provider.Settings.Secure;


/**
 * Securely send collected data to servers.
 * 
 * @author jacob
 *
 */

public class CbBatchSender  extends AsyncTask<CbObservation, Integer, String> {

	private String responseText = "";
	private CbLocationManager locationManager;
	
	private CbSettingsHandler settings;
	
	private Messenger messenger = null;
	
	private Context context;
	private String mAppDir;
	private boolean userSent;
	
	public CbBatchSender(Context ctx) {
		this.context = ctx;
		setUpFiles();
	}
	
	public CbSettingsHandler getSettings() {
		return settings;
	}
	public void setSettings(CbSettingsHandler settings, CbLocationManager locationManager, Messenger notifyMessenger, boolean fromUser) {
		this.settings = settings;
		this.locationManager = locationManager;
		this.messenger = notifyMessenger;
		this.userSent = fromUser;
	}

	 private void returnResult(String result, String condition, long time, double pressure) {
    	boolean success = true;
    	String errorMessage = "";
    	try {
    		JSONObject jsonResult = new JSONObject(result);
    		if(jsonResult.has("success")) {
    			success = jsonResult.getBoolean("success");
    		}
    		if(jsonResult.has("errors")) {
    			if(jsonResult.getString("errors").length()> 1) {
    				errorMessage = "error" + jsonResult.getString("errors");
    				log("add errormessage " + errorMessage);
    			}
    		}
    		// notify
			long now = System.currentTimeMillis();
			if(now - time > 1000 * 10) {
				log("cbdatasender not notifying, time too long " + (now - time));
			} else {
				log("cbdatasender notifying, time " + (now - time));
				if(userSent) {
	    			log("cbdatasender notifying result of data submission");
	    			if(condition.length()>1) {
	    				errorMessage = condition;
	    				Intent intent = new Intent();
						intent.setAction(CbService.CONDITION_SENT_TOAST);
						intent.putExtra("ca.cumulonimbus.pressurenetsdk.conditionSent", condition);
						context.sendBroadcast(intent);
	    			} else {
	    				Intent intent = new Intent();
						intent.setAction(CbService.PRESSURE_SENT_TOAST);
						intent.putExtra("ca.cumulonimbus.pressurenetsdk.pressureSent", pressure);
						context.sendBroadcast(intent);
	    			}
	    			
    				userSent = false;
				} else {
					log("cbdatasender not notifying result");
				}
			
			}
			
    		
    	} catch(JSONException jsone) {
    		log("error " + result);
    		jsone.printStackTrace();
    	}
	}
	
	@Override
	protected String doInBackground(CbObservation... allObs) {
		log("cb batch send do in bg");
		DefaultHttpClient client = new DefaultHttpClient();
		try {
			
			 JSONObject data = new JSONObject();
			 JSONArray jsonArray = new JSONArray();
		

			 log("cb batchdata looping through obs");
			 JSONObject object = new JSONObject();
			 for(CbObservation ob : allObs) {
				 
				 if(ob == null) {
					 log("cb batch data encountered null observation, bailing");
					 continue;
				 }
				 
				 String[] params = ob.getObservationAsParams();
				 log("cbbatch object param size " + params.length);
				 
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
				
				    try {
					  object.put(key, value);
					  log("POST batch adding " + key + ", " + value + " to json");
					  
			        } catch (Exception ex) {
			        	log("cb batch sender exception " + ex.getMessage());
			        }
		
				} 
				 
				jsonArray.put(object);
				 
			 }
			
			String serverURL = CbConfiguration.SERVER_URL_SECONDARY; //settings.getServerURL();
			log("settings url " + serverURL);
			
	
			try {
				
				data.put("data", jsonArray);
				data.put("source", "pressurenet");
				data.put("user_id", getID());
			}catch(JSONException jsone) {
				log("json error " + jsone.getMessage());
			}
		
		
			
			HttpPost httppost = new HttpPost(serverURL);
			//httppost.setEntity(new UrlEncodedFormEntity(nvps));
			String message;
			 
			try {
		        message = data.toString();
			  
			  httppost.setEntity(new StringEntity(message, "UTF8"));
			  httppost.setHeader("Content-type", "application/json");
			  httppost.addHeader("Accept","application/json");
			} catch(Exception e) {
				
			}
			
			log("POST Secondary: " + EntityUtils.toString(httppost.getEntity()));

			
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
			log("addresp Secondary " + addResp);
			
			
		} catch(ClientProtocolException cpe) {
			cpe.printStackTrace();
		} catch(IOException ioe) {
			ioe.printStackTrace();
		} catch(ArrayIndexOutOfBoundsException aioobe) {
			aioobe.printStackTrace();
		}
		log("responsetext" + responseText);
		return responseText;
	}
	
	/**
	 * Get a hash'd device ID
	 * 
	 * @return
	 */
	public String getID() {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");

			String actual_id = Secure.getString(context
					.getContentResolver(), Secure.ANDROID_ID);
			byte[] bytes = actual_id.getBytes();
			byte[] digest = md.digest(bytes);
			StringBuffer hexString = new StringBuffer();
			for (int i = 0; i < digest.length; i++) {
				hexString.append(Integer.toHexString(0xFF & digest[i]));
			}
			return hexString.toString();
		} catch (Exception e) {
			return "--";
		}
	}
	
	public void log(String message) {
		if(CbConfiguration.DEBUG_MODE) {
			//logToFile(message);
			System.out.println(message);
		}
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
    		//e.printStackTrace();
    	}
    }

   
    
	@Override
	protected void onPostExecute(String result) {
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
			//e.printStackTrace();
		} catch(IOException ioe) {
			//ioe.printStackTrace();
		}
	}
	
}
