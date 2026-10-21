package com.elozino.splitfy.security

import com.elozino.splitfy.api.dto.DevicePlatform
import platform.UIKit.UIDevice

/** Since iOS 16 this is the generic model name ("iPhone") unless Apple grants the user-name entitlement. */
actual fun currentDeviceIdentity(): DeviceIdentity =
    DeviceIdentity(name = UIDevice.currentDevice.name, platform = DevicePlatform.IOS)
