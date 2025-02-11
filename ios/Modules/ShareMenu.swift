@objc(ShareMenu)
class ShareMenu: RCTEventEmitter {

    private(set) static var _shared: ShareMenu?
    @objc public static var shared: ShareMenu
    {
        get {
            return ShareMenu._shared!
        }
    }

    var sharedData: [[String:String]?]?

    static var initialShare: (UIApplication, URL, [UIApplication.OpenURLOptionsKey : Any])?

    var hasListeners = false

    var _targetUrlScheme: String?
    var targetUrlScheme: String
    {
        get {
            return _targetUrlScheme!
        }
    }

    public override init() {
        super.init()
        ShareMenu._shared = self

        if let (app, url, options) = ShareMenu.initialShare {
            share(application: app, openUrl: url, options: options)
        }
    }

    override static public func requiresMainQueueSetup() -> Bool {
        return false
    }

    open override func supportedEvents() -> [String]! {
        return [NEW_SHARE_EVENT]
    }

    open override func startObserving() {
        hasListeners = true
    }

    open override func stopObserving() {
        hasListeners = false
    }

    public static func messageShare(
        application app: UIApplication,
        openUrl url: URL,
        options: [UIApplication.OpenURLOptionsKey : Any]
    ) {
        guard (ShareMenu._shared != nil) else {
            initialShare = (app, url, options)
            return
        }
        
        ShareMenu.shared.share(application: app, openUrl: url, options: options)
    }
    
    func share(
        application app: UIApplication,
        openUrl url: URL,
        options: [UIApplication.OpenURLOptionsKey : Any]) {
        if _targetUrlScheme == nil {
            guard let bundleUrlTypes = Bundle.main.object(forInfoDictionaryKey: "CFBundleURLTypes") as? [NSDictionary] else {
                print("Error: \(NO_URL_TYPES_ERROR_MESSAGE)")
                return
            }
            guard let bundleUrlSchemes = bundleUrlTypes.first?.value(forKey: "CFBundleURLSchemes") as? [String] else {
                print("Error: \(NO_URL_SCHEMES_ERROR_MESSAGE)")
                return
            }
            guard let expectedUrlScheme = bundleUrlSchemes.first else {
                print("Error \(NO_URL_SCHEMES_ERROR_MESSAGE)")
                return
            }

            _targetUrlScheme = expectedUrlScheme
        }

        guard let scheme = url.scheme, scheme == targetUrlScheme else { return }
        guard let bundleId = Bundle.main.bundleIdentifier else { return }
        guard let userDefaults = UserDefaults(suiteName: "group.\(bundleId)") else {
            print("Error: \(NO_APP_GROUP_ERROR)")
            return
        }

        let extraData = userDefaults.object(forKey: USER_DEFAULTS_EXTRA_DATA_KEY) as? [String:Any]

        if let data = userDefaults.object(forKey: USER_DEFAULTS_KEY) as? [[String:String]] {
            sharedData = data
            dispatchEvent(with: data, and: extraData)
            userDefaults.removeObject(forKey: USER_DEFAULTS_KEY)
        }
    }

    @objc(getSharedText:)
    func getSharedText(callback: RCTResponseSenderBlock) {
        var data = [DATA_KEY: sharedData] as [String: Any]

        if let bundleId = Bundle.main.bundleIdentifier, let userDefaults = UserDefaults(suiteName: "group.\(bundleId)") {
            data[EXTRA_DATA_KEY] = userDefaults.object(forKey: USER_DEFAULTS_EXTRA_DATA_KEY) as? [String: Any]
        } else {
            print("Error: \(NO_APP_GROUP_ERROR)")
        }

        callback([data as Any])
        sharedData = []
    }
    
    func dispatchEvent(with data: [[String:String]], and extraData: [String:Any]?) {
        guard hasListeners else { return }

        var finalData = [DATA_KEY: data] as [String: Any]
        if (extraData != nil) {
            finalData[EXTRA_DATA_KEY] = extraData
        }
        
        sendEvent(withName: NEW_SHARE_EVENT, body: finalData)
    }
}
