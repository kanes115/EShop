import Payer.{InternalPaymentServiceException, MakePayment, PaymentServiceTemporarilyUnavailable, Success}
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

  // Exceptions
  //   this needs retry
  case class InternalPaymentServiceException() extends Exception
  //   this needs to abort the procedure
  case class PaymentServiceTemporarilyUnavailable() extends Exception

  var counter: Int = 0
  def ++ = counter += 1
}

class Payer extends Actor {

    import akka.pattern.pipe
    import context.dispatcher

    final implicit val materializer: ActorMaterializer = ActorMaterializer(ActorMaterializerSettings(context.system))

    val http = Http(context.system)

    override def preStart() = {
      val status = getStatus
      Payer.++
      println("Now status is: " + status.toString)
      http.singleRequest(HttpRequest(uri = "http://localhost:80/status/" + status.toString))
        .pipeTo(self)
    }

    def receive = {
      case HttpResponse(StatusCodes.OK, headers, entity, _) =>
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
          println("Got response, body: " + body.utf8String)
        }
        context.parent ! Success
      case resp @ HttpResponse(StatusCodes.ServiceUnavailable, _, _, _) =>
        println("Request failed, response code: 503")
        resp.discardEntityBytes()
        throw new PaymentServiceTemporarilyUnavailable()
      case resp @ HttpResponse(code, _, _, _) =>
        println("Request failed, response code: " + code)
        resp.discardEntityBytes()
        throw new InternalPaymentServiceException()
    }

  private def getStatus =
    if(Payer.counter < 2)
      500 // we will fail 3 times for fun
    else
      200

  // uncomment for the test called "supervise whole order process and gets error if payment service returns 503"
  // private def getStatus = 503
}
