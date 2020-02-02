package com.github.pawelj_pl.bibliogar.api.constants

import com.github.pawelj_pl.bibliogar.api.domain.device.{Device, DeviceDescription}

trait DeviceConstants extends CommonConstants with ResourcesIdentifiers {
  final val TestUniqueId = "myDeviceId"
  final val TestBrand = "myBrand"
  final val TestDescriptionId = "someDescriptionId"
  final val TestDeviceName = "myDeviceName"
  final val TestDescription = DeviceDescription(TestBrand, TestDescriptionId, TestDeviceName)
  final val ExampleDevice = Device(TestDeviceId, TestUserId, TestUniqueId, TestDescription, Now, Now)
}
