package conduit

import cats.effect._
import cats.implicits._
import conduit.modules._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.server.blaze.BlazeServerBuilder
import scala.concurrent.ExecutionContext

object Main extends IOApp {

  implicit val logger = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] =
    config.load[IO].flatMap { cfg =>
      Logger[IO].info(s"Loaded config $cfg") >>
        AppResources.make[IO](cfg).use { res =>
          for {
            security <- Security.make[IO](cfg, res.psql, res.redis)
            algebras <- Algebras.make[IO](res.redis, res.psql)
            api <- HttpApi.make[IO](algebras, security)
            _ <- BlazeServerBuilder[IO](ExecutionContext.global)
                  .bindHttp(
                    cfg.httpServerConfig.port.value,
                    cfg.httpServerConfig.host.value
                  )
                  .withHttpApp(api.httpApp)
                  .serve
                  .compile
                  .drain
          } yield ExitCode.Success
        }
    }

}
