package sanctuary.app.shared.platform

import platform.UIKit.UIDevice

class ApplePlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = ApplePlatform()
