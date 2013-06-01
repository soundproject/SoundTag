package il.ac.idc.milab.soundtag;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class PlayingActivity extends Activity {

	private SoundPlayer m_Player;
	
	private Button m_PlayButton;
	private Button m_YesButton;
	private Button m_NoButton;
	
	private JSONArray m_NoiseIdList;
	private int m_CurrentIndex = 0;
	
	private TextView m_Noise;
	
	private JSONObject m_CurrentNoise;
	private boolean m_Verified = false;
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_playing);
		
		// Init the connectivity manager
		ConnectivityManager connectivityManager 
		        = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
				NetworkUtils.init(connectivityManager);
		
		NetworkUtils.init(connectivityManager);
		
		// Get noise list from server 
		m_NoiseIdList = getNoisesIdList();
		String sid = m_NoiseIdList.optJSONObject(m_CurrentIndex).optString("sid");
		m_CurrentNoise = getNoiseById(sid);
		
		// Prep the file for play
		initSoundFile(m_CurrentNoise);
		
		// Init our player
		m_Player = new SoundPlayer();
		m_Player.initPlayer(getFilesDir() + "/temp");		
		
		// Init our timer
		
		
		// Init buttons
		m_PlayButton = (Button)findViewById(R.id.play_button);
		m_PlayButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				if(m_Player.isPlaying()) {
					m_PlayButton.setText("Press to play!");
					m_Player.stopPlaying();
				}
				else {
					m_PlayButton.setText("Playing...");
					m_Player.startPlaying();
					MediaPlayer mp = m_Player.getActiveMediaPlayer();
					mp.setOnCompletionListener(new OnCompletionListener() {
						
						@Override
						public void onCompletion(MediaPlayer mp) {
							m_PlayButton.setText("Press to play!");
						}
					});
				}
			}
		});
		
		m_YesButton = (Button)findViewById(R.id.verification_button_yes);
		m_YesButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_Verified = true;
				sendUpdateToServer(m_Verified);
			}
		});
		
		m_NoButton = (Button)findViewById(R.id.verification_button_no);
		m_NoButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				m_Verified = false;
				sendUpdateToServer(m_Verified);
			}
		});
		
		m_Noise = (TextView)findViewById(R.id.noise);
		m_Noise.setText(m_CurrentNoise.optString("sName", "Something went wrong\nPlease talk to Tal"));
	}

	protected void sendUpdateToServer(boolean m_Verified2) {
		if(updateDatabase()) {
			m_CurrentIndex++;
			String sid = m_NoiseIdList.optJSONObject(m_CurrentIndex).optString("sid");
			m_CurrentNoise = getNoiseById(sid);
			
			TextView title = (TextView)findViewById(R.id.title);
			title.setText("You are listening to noise number: " + (m_CurrentIndex + 1));
			
			// Prep the file for play
			initSoundFile(m_CurrentNoise);
			m_Noise.setText(m_CurrentNoise.optString("sName", "Something went wrong\nPlease talk to Tal"));
			
			new AlertDialog.Builder(PlayingActivity.this)
		    .setMessage("Server was updated successfully :)")
		    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {

		        }
		    }).show();
		}
		else {
			new AlertDialog.Builder(PlayingActivity.this)
		    .setMessage("Failed to update server, please try again.")
		    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int whichButton) {

		        }
		    }).show();
		}
	}

	private void initSoundFile(JSONObject noise) {
		String encodedFile = noise.optString("sBase64Encode");
		byte[] soundFile = Base64.decode(encodedFile, Base64.DEFAULT);
		
		FileOutputStream fileOutputStream;
		try {
			fileOutputStream = openFileOutput("temp", Context.MODE_PRIVATE);
			fileOutputStream.write(soundFile);
			fileOutputStream.close();
		} catch (FileNotFoundException e) {
			Log.d("FILE_NOT_FOUND", "");
			e.printStackTrace();
		} catch (IOException e) {
			Log.d("IO_EXCEPTION", "");
			e.printStackTrace();
		}
	}

	private JSONObject getNoiseById(String sid) {
		JSONObject request = buildRequestForNoise(sid);
		Log.d("REQUEST", request.toString());
		JSONObject response = NetworkUtils.serverRequests.sendRequestToServer(request, PlayingActivity.this);
		JSONObject noise = null;
		
		if(response != null && response.optInt(ServerRequests.RESPONSE_FIELD_SUCCESS) == ServerRequests.RESPONSE_VALUE_SUCCESS) {
			Log.d("RESPONSE", response.toString());
			noise = response.optJSONObject("noise");
		}
		
		return noise;
	}

	private JSONArray getNoisesIdList() {
		JSONObject request = buildRequestForNoiseList();
		Log.d("REQUEST", request.toString());
		JSONObject response = NetworkUtils.serverRequests.sendRequestToServer(request, PlayingActivity.this);
		JSONArray noiseIdList = null;
		
		if(response != null && response.optInt(ServerRequests.RESPONSE_FIELD_SUCCESS) == ServerRequests.RESPONSE_VALUE_SUCCESS) {
			Log.d("RESPONSE", response.toString());
			noiseIdList = response.optJSONArray("noiseIdList");
		}
		
		return noiseIdList;
	}
	
	protected boolean updateDatabase() {
		boolean success = false;
		
		JSONObject request = buildRequestToUpdateNoise();
		Log.d("REQUEST", request.toString());
		JSONObject response = NetworkUtils.serverRequests.sendRequestToServer(request, PlayingActivity.this);
		
		if(response != null && response.optInt(ServerRequests.RESPONSE_FIELD_SUCCESS) == ServerRequests.RESPONSE_VALUE_SUCCESS) {
			Log.d("RESPONSE", response.toString());
			success = true;
		}
		
		return success;
	}

	private JSONObject buildRequestToUpdateNoise() {
		JSONObject request = new JSONObject();
		try {
			request.put(ServerRequests.REQUEST_ACTION, ServerRequests.REQUEST_ACTION_SET);
			request.put(ServerRequests.REQUEST_SUBJECT, "noise");
			request.put("sid", m_CurrentNoise.optString("sid"));
			request.put("verify", m_Verified);
			
		} catch (JSONException e) {
			e.printStackTrace();
			request = null;
		}

		return request;
	}

	/**
	 * This function gets the noise list
	 * @return a JSON object representing all noises in the DB or null if 
	 * response was not valid
	 */
	public JSONObject buildRequestForNoiseList() {
		JSONObject request = new JSONObject();
		try {
			request.put(ServerRequests.REQUEST_ACTION, ServerRequests.REQUEST_ACTION_GET);
			request.put(ServerRequests.REQUEST_SUBJECT, "noiseList");
			
		} catch (JSONException e) {
			e.printStackTrace();
			request = null;
		}

		return request;
	}
	
	/**
	 * This function gets a noise by it's ID
	 * @return a JSON object representing a request for a specific noise by 
	 * it's id or null response was not valid
	 */
	public JSONObject buildRequestForNoise(String sid) {
		JSONObject request = new JSONObject();
		try {
			request.put(ServerRequests.REQUEST_ACTION, ServerRequests.REQUEST_ACTION_GET);
			request.put(ServerRequests.REQUEST_SUBJECT, "noise");
			request.put("sid", sid);
			
		} catch (JSONException e) {
			e.printStackTrace();
			request = null;
		}

		return request;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.recording, menu);
		return true;
	}
	
	

}
