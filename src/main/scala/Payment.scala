import Payer.TestStatus
import Payment._
import akka.actor.SupervisorStrategy.{Escalate, Restart, Resume, Stop}
import akka.actor.{ActorRef, FSM, OneForOneStrategy, Props}

import scala.concurrent.duration._

object Payment {
  sealed trait State
  case object WaitingForPay extends State
  case object WaitingForResult extends State
  case object Finished extends State

  sealed trait Data
  case class Empty() extends Data
  case class SenderContainer(actor: ActorRef) extends Data

  sealed trait Command
  case object Pay extends Command

  sealed trait Event
  case object PaymentReceived extends Event
}

class Payment(val getStatus: Int => TestStatus) extends FSM[Payment.State, Payment.Data] {

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = Duration.Inf) {
      case Payer.InternalPaymentServiceException() => Restart
      case _ => Escalate
    }

  startWith(WaitingForPay, Empty())

  when(WaitingForPay) {
    case Event(Pay, Empty()) =>
      val orderManager = sender
      doPay()
      goto(WaitingForResult) using SenderContainer(sender)
  }

  when(WaitingForResult) {
    case Event(Payer.Success, SenderContainer(manager)) =>
      println("Got success from child")
      manager ! PaymentReceived
      context.parent ! PaymentReceived
      goto(Finished) using Empty()
  }

  when(Finished) {
    case _ =>
      println("finished")
      stay
  }

  private def doPay(): Unit = {
    val payer = context.actorOf(Props(new Payer(getStatus)), "payer")
  }

}
