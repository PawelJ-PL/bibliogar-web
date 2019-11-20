package com.github.pawelj_pl.bibliogar.api.infrastructure.config

import pureconfig.error.CannotConvert
import pureconfig.{ConfigObjectCursor, ConfigReader}

object CorrespondenceConfigModule {
  import pureconfig.generic.semiauto.deriveReader

  private val consoleImplReader: ConfigReader[Config.CorrespondenceConfig.ConsoleCorrespondenceConfig] =
    deriveReader[Config.CorrespondenceConfig.ConsoleCorrespondenceConfig]
  private val emailImplReader: ConfigReader[Config.CorrespondenceConfig.EmailCorrespondenceConfig] =
    deriveReader[Config.CorrespondenceConfig.EmailCorrespondenceConfig]

  private def extractByType(notificationType: String, objCur: ConfigObjectCursor): ConfigReader.Result[Config.CorrespondenceConfig] =
    notificationType match {
      case "console" => consoleImplReader.from(objCur)
      case "email"   => emailImplReader.from(objCur)
      case t         => objCur.failed(CannotConvert(objCur.value.toString, "Config.CorrespondenceConfig", s"Unsupported notification type $t"))
    }

  implicit val notificationConfigReader: ConfigReader[Config.CorrespondenceConfig] = ConfigReader.fromCursor { cur =>
    for {
      objCur  <- cur.asObjectCursor
      typeCur <- objCur.atKey("correspondence-type")
      typeStr <- typeCur.asString
      conf    <- extractByType(typeStr, objCur)
    } yield conf
  }
}
