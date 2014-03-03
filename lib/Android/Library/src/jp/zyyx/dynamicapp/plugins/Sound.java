package jp.zyyx.dynamicapp.plugins;

import java.io.File;

import org.json.JSONException;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;

import jp.zyyx.dynamicapp.core.DynamicAppPlugin;
import jp.zyyx.dynamicapp.utilities.DebugLog;
import jp.zyyx.dynamicapp.utilities.DynamicAppUtils;

/**
 * @author Zyyx
 *
 */
public class Sound extends DynamicAppPlugin implements MediaPlayer.OnBufferingUpdateListener{
	private static final String TAG = "Sound";

	private static final int SOUND_STATE = 1;
	private static final int SOUND_DURATION = 2;
	private static final int SOUND_POSITION = 3;
	
	private static final int SOUND_ERROR = 9;
	private static final int SOUND_NONE = 0;
	private static final int SOUND_STARTING = 1;
	private static final int SOUND_RUNNING = 2;
	private static final int SOUND_PAUSED = 3;
	private static final int SOUND_STOPPED = 4;
	
	private static final int ERROR_ABORTED = 1;
	private static final int ERROR_NETWORK = 2;
	private static final int ERROR_DECODE = 3;
	private static final int ERROR_NONE_SUPPORTED = 4;
	
	private static Sound instance = null;
	private MediaPlayer mBGMPlayer = null;
	private String audioFile = null;
	private String mediaId = null;
	private int loopCounter = 0;
	private int numberOfLoops = 0;
	private int position = 0;
	private int duration = 0;
	private int currentState = 0;
	private boolean isPaused = false;
	private boolean isReseted = false;
	private boolean isCancelled = false;
	private boolean onMediaPlayerError = false;

    private Sound() {
    	mBGMPlayer = new MediaPlayer();
    }

    public static synchronized Sound getInstance() {
            if (instance == null) {
                    instance = new Sound();
            }
            
            return instance;
    }
    
    public void init(String methodName, String params, String callbackId){
    	super.init(methodName, params, callbackId);
    	
    }
    
	@Override
	public void execute() {
		DebugLog.i(TAG, "method " + methodName + " is executed.");
		DebugLog.i(TAG, "parameters are: " + params);
		this.mediaId = param.get("mediaId", "");
		
		if (methodName.equalsIgnoreCase("play")) {	
			this.audioFile = param.get("audioFile", "");	
			this.numberOfLoops = param.get("numberOfLoops", 1);
			numberOfLoops = 1;
			isCancelled = false;
			onMediaPlayerError = false;
			
			if(!isPaused){
				this.openBGM();
			} else {
				String script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
						+ Sound.SOUND_DURATION + "," + getBGMDuration() +");";
				dynamicApp.callJsEvent(script);
				this.playBGM();
			}
		}else if(methodName.equalsIgnoreCase("pause")) {
			this.pauseBGM();
		}else if(methodName.equalsIgnoreCase("stop")) {
			dynamicApp.callJsEvent(PROCESSING_FALSE);
			this.stopBGM();
		}else if(methodName.equalsIgnoreCase("release")) {
			dynamicApp.callJsEvent(PROCESSING_FALSE);
			this.releaseBGM();
		}else if(methodName.equalsIgnoreCase("getCurrentPosition")) {
			dynamicApp.callJsEvent(PROCESSING_FALSE);
			this.getBGMPosition();
		}else if(methodName.equalsIgnoreCase("setCurrentPosition")) {
			dynamicApp.callJsEvent(PROCESSING_FALSE);
			this.position = param.get("position", 1);
			this.seekBGM(position);
		}
	}
	
	private void createPlayer() {
		mBGMPlayer.setOnBufferingUpdateListener(this);
    	mBGMPlayer.setOnErrorListener(new OnErrorListener(){
			@Override
			public boolean onError(MediaPlayer mp, int what, int extra) {
				int error = ERROR_DECODE;
				dynamicApp.callJsEvent(PROCESSING_FALSE);
				DebugLog.e(TAG, "Error: " + what + "," + extra);
				onMediaPlayerError = true;
				mBGMPlayer.reset();
				isReseted = true;
				isPaused = false;
				
				switch(what) {
					case MediaPlayer.MEDIA_ERROR_UNKNOWN:
						error = ERROR_DECODE;
					break;
					case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
						error = ERROR_NETWORK;
					break;
				}
				
				if (dynamicApp.getWindow() != null) {
					String script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
							+ Sound.SOUND_ERROR + ",\"" + error + "\");";
					dynamicApp.callJsEvent(script);
				}
				return true;
		}});
    	mBGMPlayer.setOnCompletionListener(new OnCompletionListener() {
			public void onCompletion(MediaPlayer mp) {
				String script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
						+ Sound.SOUND_POSITION + "," + 0 +");";
				dynamicApp.callJsEvent(script);
				dynamicApp.callJsEvent(PROCESSING_FALSE);
				DebugLog.i(TAG, "media player onCompletion");
				
				if(numberOfLoops > 1) {
					loopCounter++;
					if(loopCounter < numberOfLoops) {
						playBGM();
					} else {
						loopCounter = 0;
						script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
								+ Sound.SOUND_STATE + "," + Sound.SOUND_STOPPED +");";
						dynamicApp.callJsEvent(script);
						mBGMPlayer.reset();
						isReseted = true;
					}
				} else {
					script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
							+ Sound.SOUND_STATE + "," + Sound.SOUND_STOPPED +");";
					dynamicApp.callJsEvent(script);
					mBGMPlayer.reset();
					isReseted = true;
				}
				dynamicApp.callJsEvent(PROCESSING_FALSE);
			}
		});
		
		mBGMPlayer.setOnPreparedListener(new OnPreparedListener()
	    {
			public void onPrepared(MediaPlayer mp) {
				DebugLog.i(TAG, "[sound] @onPrepared");
				if(!isCancelled && !onMediaPlayerError) {
					String script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
							+ Sound.SOUND_DURATION + "," + getBGMDuration() +");";
					dynamicApp.callJsEvent(script);
					dynamicApp.callJsEvent(PROCESSING_FALSE);
					playBGM();
				}
			}
	    });
	}
	
	/**
	 * play media file (for BGM)
	 * 
	 * @param filename
	 * @param loopMode
	 * @param audioType (music || ring)
	 * @return MediaPlayer
	 */
	public void openBGM() {
		createPlayer();
		String script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
				+ Sound.SOUND_STATE + "," + Sound.SOUND_STARTING +");";
		dynamicApp.callJsEvent(script);
		currentState = SOUND_STARTING;
		new Thread() {
			public void run() {
				try {
					isReseted = false;
					mBGMPlayer.reset();
					if(audioFile.indexOf("http://") != -1) {
						mBGMPlayer.setDataSource(dynamicApp, Uri.parse(audioFile));
					} else {
						String path = "";
						path = DynamicAppUtils.makePath(audioFile);
						DebugLog.e(TAG, "[audio] try:" + path);
						if (new File(path).exists()) {
							mBGMPlayer.setDataSource(path);
						} else {
							processError(ERROR_ABORTED);
						}
					}
					
					mBGMPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
					mBGMPlayer.prepare();
				} catch (Exception e) {
					processError(ERROR_NONE_SUPPORTED);
				}
			}
		}.start();
	}
	
	/**
	 * Plays or resumes the sound.
	 */
	public void playBGM() {
		dynamicApp.callJsEvent(PROCESSING_FALSE);
		mBGMPlayer.setLooping(false);
		mBGMPlayer.start();
		String script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
				+ Sound.SOUND_STATE + "," + Sound.SOUND_RUNNING +");";
		dynamicApp.callJsEvent(script);
		currentState = SOUND_RUNNING;
		isPaused = false;
	}

	/**
	 * Stops the sound.
	 */
	public void stopBGM() {
		dynamicApp.callJsEvent(PROCESSING_FALSE);
		isCancelled = (currentState == SOUND_STARTING);
		isPaused = false;
		loopCounter = 0;
		new Thread() {
			public void run() {
			if (mBGMPlayer.isPlaying()){
				String script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
						+ Sound.SOUND_STATE + "," + Sound.SOUND_STOPPED +");";
				dynamicApp.callJsEvent(script);
				currentState = SOUND_STOPPED;
				mBGMPlayer.stop();
				mBGMPlayer.reset();
			}
		}}.start();
		
	}

	public boolean isPlayingBGM() {
		return (mBGMPlayer.isPlaying());
	}

	/**
	 * Pause the sound.
	 */
	public void pauseBGM() {
		dynamicApp.callJsEvent(PROCESSING_FALSE);
		try {
			jsonObject.put("message", Sound.SOUND_PAUSED);
		} catch (JSONException e) {
			e.printStackTrace();
		}
		this.onSuccess();
		mBGMPlayer.pause();
		currentState = SOUND_PAUSED;
		isPaused = true;
	}
	
	public void releaseBGM(){
		dynamicApp.callJsEvent(PROCESSING_FALSE);
		mBGMPlayer.release();
		Sound.instance = null;
		String script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
				+ Sound.SOUND_STATE + "," + Sound.SOUND_NONE +");";
		dynamicApp.callJsEvent(script);
	}

	/**
	 * Seek the sound.
	 */
	public void seekBGM(final int time) {
		dynamicApp.callJsEvent(PROCESSING_FALSE);
		new Thread() {
			public void run() {
				mBGMPlayer.seekTo(time * 1000);
				int pos = mBGMPlayer.getCurrentPosition()/1000;
				try {
					jsonObject.put("message", pos);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				onSuccess();
		}}.start();
	}

	public int getBGMDuration() {
		dynamicApp.callJsEvent(PROCESSING_FALSE);
		duration = (onMediaPlayerError) ? 0 : mBGMPlayer.getDuration()/1000;
		return duration;
	}

	/**
	 * Position of the sound.
	 */
	public void getBGMPosition() {
		dynamicApp.callJsEvent(PROCESSING_FALSE);
		new Thread() {
			public void run() {
				if(mBGMPlayer.isPlaying() && !isReseted && !onMediaPlayerError) {
					position = mBGMPlayer.getCurrentPosition()/1000;
					try {
						jsonObject.put("message", position);
					} catch (JSONException e) {
						e.printStackTrace();
					}
					onSuccess();
				}
		}}.start();
	}

	private void processError(int err) {
		dynamicApp.callJsEvent(PROCESSING_FALSE);
		String script = null;
		switch(err) {
			case ERROR_DECODE:
				script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
						+ Sound.SOUND_ERROR + ",\"" + ERROR_DECODE + "\");";
				break;
			case ERROR_ABORTED:
				script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
						+ Sound.SOUND_ERROR + ",\"" + ERROR_ABORTED + "\");";
				break;
			case ERROR_NONE_SUPPORTED:
				script = "DynamicApp.Sound.onStatus(\"" + mediaId + "\","
						+ Sound.SOUND_ERROR + ",\"" + ERROR_NONE_SUPPORTED + "\");";
				break;
		}
		
		if (dynamicApp.getWindow() != null && script != null) {
			dynamicApp.callJsEvent(PROCESSING_FALSE);
			onMediaPlayerError = true;
			mBGMPlayer.reset();
			isReseted = true;
			isPaused = false;
			dynamicApp.callJsEvent(script);
		}
	}
	
	@Override
	public void onBackKeyDown() {
		this.stopBGM();
		DynamicAppUtils.currentCommandRef = null;
		instance = null;
	}

	@Override
	public void onBufferingUpdate(MediaPlayer mp, int percent) {
		//DebugLog.i(TAG, "sound", "buffering" + percent);
	}
}
