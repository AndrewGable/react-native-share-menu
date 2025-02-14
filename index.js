import { NativeModules, NativeEventEmitter } from "react-native";

const { ShareMenu } = NativeModules;

const EventEmitter = new NativeEventEmitter(ShareMenu);

const NEW_SHARE_EVENT_NAME = "NewShareEvent";

export const ShareMenuReactView = {
  dismissExtension(error = null) {
    NativeModules.ShareMenuReactView.dismissExtension(error);
  },
  openApp() {
    NativeModules.ShareMenuReactView.openApp();
  },
  continueInApp(extraData = null) {
    NativeModules.ShareMenuReactView.continueInApp(extraData);
  },
  data() {
    return NativeModules.ShareMenuReactView.data();
  },
};

export default {
  /**
   * @deprecated Use `getInitialShare` instead. This is here for backwards compatibility.
   */
  getSharedText(callback) {
    this.getInitialShare(callback);
  },
  getInitialShare(callback, errorCallback = console.error) {
    ShareMenu.getSharedText(
      (data) => callback(data),
      (error) => errorCallback('ShareMenu error:', error)
    );
  },
  addNewShareListener(callback) {
    const subscription = EventEmitter.addListener(
      NEW_SHARE_EVENT_NAME,
      callback
    );

    return subscription;
  },
};
