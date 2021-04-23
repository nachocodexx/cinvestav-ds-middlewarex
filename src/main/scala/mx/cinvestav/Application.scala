package mx.cinvestav

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import fs2.Stream
import fs2.io.file.Files
import fs2.text

import java.nio.file.Paths
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import mx.cinvestav.config.DefaultConfig
import org.http4s._
import org.http4s.circe._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.circe.CirceEntityDecoder._

import scala.language.postfixOps
import scala.concurrent.duration._
import pureconfig._
import pureconfig.generic.auto._

import scala.concurrent.ExecutionContext.global

object Application extends IOApp{
//  val storagePoolsUrls = Map("Gamma"->"")
//implicit val stringDecoder = jsonOf[IO, String]
  case class Workload(requestType:String,userId:Long,role:String,fileId:Int,fileSize:Int,topicRole:String)

  def csvToWorkload()(implicit C:DefaultConfig): Stream[IO, Workload] =
    Files[IO].readAll(Paths.get(C.workloadPath),4096)
      .through(text.utf8Decode)
      .through(text.lines)
      .tail
      .filter(_.nonEmpty)
      .map(_.split(','))
      .map(x=>(x(0),x(1).toLong,x(2),x(3).toInt,x(4).toInt,x(5)))
      .map(x=> Workload tupled x )

  def startDev()(implicit C:DefaultConfig): IO[ExitCode] = {
    val clientBuilder = BlazeClientBuilder[IO](global).resource
      csvToWorkload()
      .filter(_.role==="Gamma")
      .take(2)
      .evalMap{ x=>
//        clientBuilder.use{ client=>
//          client.expect[String]("http://localhost:5000/sp-00/health-check")
//        }
        IO.unit
      }
      .debug()
      .compile
      .drain
      .as(ExitCode.Success)
  }


  override def run(args: List[String]): IO[ExitCode] = {
    val config =ConfigSource.default.load[DefaultConfig]
    config match {
      case Left(value) =>
        println(value)
        IO.unit.as(ExitCode.Error)
      case Right(cfg) =>
        println(cfg)
        startDev()(cfg)
    }
  }

}
