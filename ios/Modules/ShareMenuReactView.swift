//
//  ShareMenuReactView.swift
//  RNShareMenu
//
//  Created by Gustavo Parreira on 28/07/2020.
//

import MobileCoreServices

@objc(ShareMenuReactView)
public class ShareMenuReactView: NSObject {
    static var viewDelegate: ReactShareViewDelegate?

    @objc
    static public func requiresMainQueueSetup() -> Bool {
        return false
    }

    public static func attachViewDelegate(_ delegate: ReactShareViewDelegate!) {
        guard (ShareMenuReactView.viewDelegate == nil) else { return }

        ShareMenuReactView.viewDelegate = delegate
    }

    public static func detachViewDelegate() {
        ShareMenuReactView.viewDelegate = nil
    }

    @objc(dismissExtension:)
    func dismissExtension(_ error: String?) {
        guard let extensionContext = ShareMenuReactView.viewDelegate?.loadExtensionContext() else {
            print("Error: \(NO_EXTENSION_CONTEXT_ERROR)")
            return
        }
      
        if error != nil {
            let exception = NSError(
                domain: Bundle.main.bundleIdentifier!,
                code: DISMISS_SHARE_EXTENSION_WITH_ERROR_CODE,
                userInfo: ["error": error!]
            )
            extensionContext.cancelRequest(withError: exception)
            return
        }

        extensionContext.completeRequest(returningItems: [], completionHandler: nil)
    }

    @objc
    func openApp() {
        guard let viewDelegate = ShareMenuReactView.viewDelegate else {
            print("Error: \(NO_DELEGATE_ERROR)")
            return
        }
      
        viewDelegate.openApp()
    }

    @objc(continueInApp:)
    func continueInApp(_ extraData: [String:Any]?) {
        guard let viewDelegate = ShareMenuReactView.viewDelegate else {
            print("Error: \(NO_DELEGATE_ERROR)")
            return
        }

        let extensionContext = viewDelegate.loadExtensionContext()

        guard let item = extensionContext.inputItems.first as? NSExtensionItem else {
            print("Error: \(COULD_NOT_FIND_ITEM_ERROR)")
            return
        }

        viewDelegate.continueInApp(with: item, and: extraData)
    }

    @objc(data:reject:)
    func data(_
            resolve: @escaping RCTPromiseResolveBlock,
            reject: @escaping RCTPromiseRejectBlock) {
        guard let extensionContext = ShareMenuReactView.viewDelegate?.loadExtensionContext() else {
            print("Error: \(NO_EXTENSION_CONTEXT_ERROR)")
            return
        }

        extractDataFromContext(context: extensionContext) { (datas, error) in
            guard (error == nil) else {
                reject("error", error?.description, nil)
                return
            }

            resolve(datas)
        }
    }

    func extractDataFromContext(context: NSExtensionContext, withCallback callback: @escaping ([[String: String]]?, NSException?) -> Void) {
        let item:NSExtensionItem! = context.inputItems.first as? NSExtensionItem
        let attachments:[AnyObject]! = item.attachments
        var datas: [[String: String]] = []
        let group = DispatchGroup()

        for provider in attachments {
            var urlProvider:NSItemProvider! = nil
            var imageProvider:NSItemProvider! = nil
            var textProvider:NSItemProvider! = nil
            var dataProvider:NSItemProvider! = nil
            var data = [String: String]()
          
            if provider.hasItemConformingToTypeIdentifier(kUTTypeURL as String) {
                urlProvider = provider as? NSItemProvider
            } else if provider.hasItemConformingToTypeIdentifier(kUTTypeText as String) {
                textProvider = provider as? NSItemProvider
            } else if provider.hasItemConformingToTypeIdentifier(kUTTypeImage as String) {
                imageProvider = provider as? NSItemProvider
            } else if provider.hasItemConformingToTypeIdentifier(kUTTypeData as String) {
                dataProvider = provider as? NSItemProvider
            }
          
            if (urlProvider != nil) {
              group.enter()
              urlProvider.loadItem(forTypeIdentifier: kUTTypeURL as String, options: nil) { (item, error) in
                let url: URL! = item as? URL
                data = [DATA_KEY: url.absoluteString, MIME_TYPE_KEY: "text/plain"]
                group.leave()
              }
            } else if (imageProvider != nil) {
              group.enter()
              imageProvider.loadItem(forTypeIdentifier: kUTTypeImage as String, options: nil) { (item, error) in
                let url: URL! = item as? URL
                if (url != nil) {
                  data = [DATA_KEY: url.absoluteString, MIME_TYPE_KEY: self.extractMimeType(from: url)]
                  group.leave()
                } else {
                  let image: UIImage! = item as? UIImage

                  if (image != nil) {
                    let imageData: Data! = image.pngData();

                    // Creating temporary URL for image data (UIImage)
                    guard let imageURL = NSURL(fileURLWithPath: NSTemporaryDirectory()).appendingPathComponent("tempscreenshot.png") else {
                      return
                    }

                    do {
                      // Writing the image to the URL
                      try imageData.write(to: imageURL)
                      data = [DATA_KEY: imageURL.absoluteString, MIME_TYPE_KEY: imageURL.extractMimeType()]
                      group.leave()
                    } catch {
                      callback(nil, NSException(name: NSExceptionName(rawValue: "Error"), reason:"Can't load image", userInfo:nil))
                    }
                  } else {
                    callback(nil, NSException(name: NSExceptionName(rawValue: "Error"), reason:"Can't load image", userInfo:nil))
                  }
                }
              }
            } else if (textProvider != nil) {
              group.enter()
              textProvider.loadItem(forTypeIdentifier: kUTTypeText as String, options: nil) { (item, error) in
                let text:String! = item as? String
                data = [DATA_KEY: text, MIME_TYPE_KEY: "text/plain"]
                group.leave()
              }
            }  else if (dataProvider != nil) {
              group.enter()
              dataProvider.loadItem(forTypeIdentifier: kUTTypeData as String, options: nil) { (item, error) in
                let url: URL! = item as? URL
                data = [DATA_KEY: url.absoluteString, MIME_TYPE_KEY: self.extractMimeType(from: url)]
                group.leave()
              }
            } else {
              callback(nil, NSException(name: NSExceptionName(rawValue: "Error"), reason:"couldn't find provider", userInfo:nil))
            }

            group.wait()
            datas.append(data)
        }
      
        group.notify(queue: .main) {
          if (datas.count > 0) {
            callback(datas, nil)
          } else {
            callback(nil, NSException(name: NSExceptionName(rawValue: "Error"), reason:"couldn't find provider", userInfo:nil))
          }
        }
    }

    func extractMimeType(from url: URL) -> String {
      let fileExtension: CFString = url.pathExtension as CFString
      guard let extUTI = UTTypeCreatePreferredIdentifierForTag(
              kUTTagClassFilenameExtension,
              fileExtension,
              nil
      )?.takeUnretainedValue() else { return "" }

      guard let mimeUTI = UTTypeCopyPreferredTagWithClass(extUTI, kUTTagClassMIMEType)
      else { return "" }

      return mimeUTI.takeUnretainedValue() as String
    }
}
