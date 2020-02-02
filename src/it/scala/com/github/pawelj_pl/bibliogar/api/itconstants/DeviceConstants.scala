package com.github.pawelj_pl.bibliogar.api.itconstants

import com.github.pawelj_pl.bibliogar.api.domain.device.{Device, DeviceDescription}
import io.chrisdavenport.fuuid.FUUID

trait DeviceConstants extends CommonConstants with UserConstants {
  final val TestDeviceId = FUUID.fuuid("ba5a68e4-734d-42f7-ae87-6b7f32669b5c")
  final val TestUniqueId = "myDeviceId"
  final val TestBrand = "myBrand"
  final val TestDescriptionId = "someDescriptionId"
  final val TestDeviceName = "myDeviceName"
  final val TestDescription = DeviceDescription(TestBrand, TestDescriptionId, TestDeviceName)
  final val ExampleDevice = Device(TestDeviceId, ExampleUser.id, TestUniqueId, TestDescription, Now, Now)
}
