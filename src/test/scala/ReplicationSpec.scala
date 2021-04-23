import cats.effect.IO
import cats.effect.unsafe.implicits.global
import fs2.Stream
import fs2.io.file.Files
import cats.implicits._
import io.circe._
import mx.cinvestav.Application.csvToWorkload
import mx.cinvestav.LoadBalancer
import mx.cinvestav.config.DefaultConfig
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import org.http4s._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.client.blaze._
import org.http4s.client._
import org.http4s.multipart.{Multipart, Part}
import org.typelevel.ci.CIString

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Paths, Files => FFF}
import scala.concurrent.ExecutionContext.{global => G}
import scala.language.postfixOps
import scala.concurrent.duration._

class ReplicationSpec extends munit .FunSuite {
  implicit val C: DefaultConfig = ConfigSource.default.load[DefaultConfig].getOrElse(DefaultConfig("","",Nil,""))
  private val PATH = "/home/nacho/Documents/test/tmp"
  private val counter = collection.mutable.Map[String,Int]()
  def initCounter():IO[Unit]= {
    IO(C.nodes.foreach(x=>counter.updateWith(x.nodeId)(_.getOrElse(0).some) ))
  }


  test("Replication: Gamma & Beta & Alpha"){
    val clientBuilder = BlazeClientBuilder[IO](G).resource

    val stream = Stream.eval(initCounter())++csvToWorkload()
      .map{ w=>
        val path = Paths.get(PATH+"/"+String.format("%02d",w.fileSize)+".txt")
        (path.toFile,w)
      }
      .evalMap {
        case (file, workload)=>
          val attr= FFF.readAttributes(Paths.get(file.getPath),classOf[BasicFileAttributes])
          val nodes = C.nodes.filter(_.role==workload.role)
          val node = nodes.head
//          val node = LoadBalancer(C.loadBalancer,counter)
//          val url  =  Uri.unsafeFromString(s"${node.url}/${node.nodeId}/health-check")
          val url  =  Uri.unsafeFromString(s"${node.url}/${node.nodeId}")
         val multipart = Multipart[IO](Vector(Part.fileData(file.getName,file)))
          val req  = Request[IO](Method.POST,url)
            .withEntity(multipart)
            .withHeaders(multipart
              .headers
              .headers
              .concat(Headers(Header.Raw(CIString("filename"),file.getName),Header.Raw(CIString("size"),attr.size()
                .toString))
                .headers)
            )

          counter.updateWith(node.nodeId)(_.map(_+1))
          clientBuilder.use{ client=>
            val res= client.expect[Json](req)
            res.flatMap(IO.println)
          }
      }.metered( .5 seconds)
//      .debug()
    stream
      .compile
      .drain
      .unsafeRunSync()
    println(counter)
  }

}
