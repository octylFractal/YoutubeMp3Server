package loader

import play.api.ApplicationLoader.Context
import play.api.{Application, ApplicationLoader, LoggerConfigurator}

class YoutubeMp3Server extends ApplicationLoader {
    override def load(context: Context): Application = {
        LoggerConfigurator(context.environment.classLoader).foreach(f => {
            f.configure(context.environment)
        })
        new YoutubeMp3Components(context).application
    }
}
