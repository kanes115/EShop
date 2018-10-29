import Payment._
import akka.actor.{ActorRef, FSM}

object Payment {
  sealed trait State
  case object WaitingForPay extends State
  case object Finished extends State

  sealed trait Data
  case class Empty() extends Data

  sealed trait Command
  case object Pay extends Command

  sealed trait Event
  case object PaymentReceived extends Event
}

class Payment extends FSM[Payment.State, Payment.Data] {

  startWith(WaitingForPay, Empty())

  when(WaitingForPay) {
    case Event(Pay, Empty()) =>
      sender ! PaymentReceived
      context.parent ! PaymentReceived
      goto(Finished) using Empty()
  }

}
