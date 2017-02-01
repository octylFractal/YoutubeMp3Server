package loader

import controllers.Server
import play.api.ApplicationLoader.Context
import play.api.BuiltInComponentsFromContext
import play.api.routing.Router

class YoutubeMp3Components(context: Context) extends BuiltInComponentsFromContext(context) {
    lazy val router: Router = Server.getRouter(httpErrorHandler, materializer).asScala
}
