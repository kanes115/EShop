import Payer.{MakePayment, Success}
import akka.actor.Actor
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString

object Payer {
  sealed trait Command
  case object MakePayment extends Command

  sealed trait Event
  case object Success extends Event
}

class Payer extends Actor {

    import akka.pattern.pipe
    import context.dispatcher

    final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val http = Http(context.system)

    override def preStart() = {
      http.singleRequest(HttpRequest(uri = "http://localhost:80/"))
        .pipeTo(self)
    }

    def receive = {
      case HttpResponse(StatusCodes.OK, headers, entity, _) =>
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
          println("Got response, body: " + body.utf8String)
        }
        println("Sending Success to pareent: " + context.parent)
        context.parent ! Success
        // this case below is to be deleted
      case resp @ HttpResponse(code, _, _, _) =>
        println("Request failed, response code: " + code)
        resp.discardEntityBytes()
    }

}
