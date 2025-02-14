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
    if (type == null) {
      return null;
    }

    String action = intent.getAction();
    WritableMap data = Arguments.createMap();
    data.putString(MIME_TYPE_KEY, type);

    if (Intent.ACTION_SEND.equals(action)) {
      if (TEXT_PLAIN_TYPE.equals(type) || TEXT_UIC918_TYPE.equals(type)) {
        String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
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
        data.putString(DATA_KEY, fileUri.toString());
        return data;
      }
    } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
      ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
      if (fileUris != null) {
        WritableArray uriArr = Arguments.createArray();
        for (Uri uri : fileUris) {
          uriArr.pushString(uri.toString());
        }
        data.putArray(DATA_KEY, uriArr);
        return data;
      }
    }

    return null;
  }

  @ReactMethod
  public void getSharedText(Callback successCallback, Callback errorCallback) {
    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      errorCallback.invoke("No activity found");
      return;
    }

    try {
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
      return;
    }

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
    // Possibly received a new share while the app was already running

    Activity currentActivity = getCurrentActivity();

    if (currentActivity == null) {
      return;
    }

    ReadableMap shared = extractShared(intent);
    dispatchEvent(shared);

    // Update intent in case the user calls `getSharedText` again
    currentActivity.setIntent(intent);
  }
}
