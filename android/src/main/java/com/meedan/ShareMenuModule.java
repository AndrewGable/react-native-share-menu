package com.meedan;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import org.json.JSONObject;

public class ShareMenuModule extends ReactContextBaseJavaModule implements ActivityEventListener {
  // Constants
  private static final String NEW_SHARE_EVENT = "NewShareEvent";
  private static final String MIME_TYPE_KEY = "mimeType";
  private static final String DATA_KEY = "data";
  private static final String EXTRA_DATA_KEY = "extraData";
  private static final String TEXT_PLAIN_TYPE = "text/plain";
  private static final String TEXT_UIC918_TYPE = "text/uic918";

  private ReactContext mReactContext;

  public ShareMenuModule(ReactApplicationContext reactContext) {
    super(reactContext);
    mReactContext = reactContext;

    mReactContext.addActivityEventListener(this);
  }

  @NonNull
  @Override
  public String getName() {
    return "ShareMenu";
  }

  @Nullable
  private ReadableMap extractShared(Intent intent)  {
    String type = intent.getType();
    String action = intent.getAction();
    
    Log.d(TAG, "extractShared: Action: " + action);
    
    if (type == null) {
      Log.d(TAG, "extractShared: null type received");
      Log.d(TAG, "Intent details - Action: " + intent.getAction() 
          + ", Categories: " + (intent.getCategories() != null ? intent.getCategories().toString() : "null")
          + ", Data URI: " + (intent.getData() != null ? intent.getData().toString() : "null"));
      
      Bundle extras = intent.getExtras();
      if (extras != null) {
        Log.d(TAG, "Intent extras:");
        for (String key : extras.keySet()) {
          Log.d(TAG, "  " + key + ": " + extras.get(key));
        }
      } else {
        Log.d(TAG, "No extras in intent");
      }
      return null;
    }

    Log.d(TAG, "extractShared: Processing share of type: " + type);
    WritableMap data = Arguments.createMap();
    data.putString(MIME_TYPE_KEY, type);

    if (Intent.ACTION_SEND.equals(action)) {
      Log.d(TAG, "Processing ACTION_SEND");
      if (TEXT_PLAIN_TYPE.equals(type) || TEXT_UIC918_TYPE.equals(type)) {
        String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
        Log.d(TAG, "Text content received: " + (extraText != null ? "length=" + extraText.length() : "null"));
        data.putString(DATA_KEY, extraText);
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
          WritableMap record = new WritableNativeMap();
          for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value != null) {
              record.putString(key, value.toString());
            }
          }
          try {
            JSONObject jsonRecord = MapUtil.toJSONObject(record);
            data.putString(EXTRA_DATA_KEY, jsonRecord.toString());
          } catch(Exception e) {
            data.putString(EXTRA_DATA_KEY, null);
          }
        }
        return data;
      }

      Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
      if (fileUri != null) {
        Log.d(TAG, "File URI received: " + fileUri.toString());
        data.putString(DATA_KEY, fileUri.toString());
        return data;
      }
    } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
      Log.d(TAG, "Processing ACTION_SEND_MULTIPLE");
      ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
      if (fileUris != null) {
        Log.d(TAG, "Multiple files received: count=" + fileUris.size());
        WritableArray uriArr = Arguments.createArray();
        for (Uri uri : fileUris) {
          Log.d(TAG, "File URI: " + uri.toString());
          uriArr.pushString(uri.toString());
        }
        data.putArray(DATA_KEY, uriArr);
        return data;
      }
    }

    Log.d(TAG, "No matching action/type combination found");
    return null;
  }

  @ReactMethod
  public void getSharedText(Callback successCallback, Callback errorCallback) {
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      Log.e(TAG, "getSharedText: No activity found");
      errorCallback.invoke("No activity found");
      return;
    }

    try {
      Log.d(TAG, "getSharedText: Processing share request");
      if (!currentActivity.isTaskRoot()) {
        Intent newIntent = new Intent(currentActivity.getIntent());
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        currentActivity.startActivity(newIntent);

        ReadableMap shared = extractShared(newIntent);
        successCallback.invoke(shared);
        clearSharedText();
        currentActivity.finish();
        return;
      }

      Intent intent = currentActivity.getIntent();
      ReadableMap shared = extractShared(intent);
      successCallback.invoke(shared);
      clearSharedText();
    } catch (Exception e) {
      Log.e(TAG, "getSharedText: Error processing share", e);
      errorCallback.invoke(e.getMessage());
    }
  }

  @ReactMethod
  public void addListener(String eventName) {
    // Required for RN built in Event Emitter Calls.
  }

  @ReactMethod
  public void removeListeners(Integer count) {
    // Required for RN built in Event Emitter Calls.
  }

  private void dispatchEvent(ReadableMap shared) {
    if (mReactContext == null || !mReactContext.hasActiveCatalystInstance()) {
      Log.w(TAG, "dispatchEvent: Unable to emit event - React context is null or catalyst instance inactive");
      return;
    }

    Log.d(TAG, "dispatchEvent: Emitting new share event");
    mReactContext
            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
            .emit(NEW_SHARE_EVENT, shared);
  }

  public void clearSharedText() {
    Activity mActivity = getCurrentActivity();
    
    if(mActivity == null) { return; }

    Intent intent = mActivity.getIntent();
    String type = intent.getType();

    if (type == null) {
      return;
    }

    if ("text/plain".equals(type)) {
      intent.removeExtra(Intent.EXTRA_TEXT);
      return;
    }

    intent.removeExtra(Intent.EXTRA_STREAM);
  }

  @Override
  public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
    // DO nothing
  }

  @Override
  public void onNewIntent(Intent intent) {
    Log.d(TAG, "onNewIntent: Received new share intent");
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      Log.e(TAG, "onNewIntent: No activity found");
      return;
    }

    ReadableMap shared = extractShared(intent);
    dispatchEvent(shared);

    // Update intent in case the user calls `getSharedText` again
    currentActivity.setIntent(intent);
  }
}
