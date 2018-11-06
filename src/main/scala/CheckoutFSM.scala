import CheckoutFSM._
import TimerAPI.Timer
import akka.actor.{ActorRef, ActorSystem, FSM, Props}


object CheckoutFSM {

  //commands
  sealed trait Command
  case object Init extends Command
  case class SetDeliveryMethod(deliveryMethod: DeliveryMethod) extends Command
  case class SetPaymentMethod(paymentMethod: PaymentMethod) extends Command
  case object Pay extends Command

  sealed trait Event
  case class CheckoutClosed(paymentRef: ActorRef) extends Event
  case object CheckoutFailed extends Event

  sealed trait State
  case object SelectingDelivery extends State
  case object SelectingPayment extends State
  case object ProcessingPayment extends State
  case object Cancelled extends State
  case object Closed extends State


  sealed trait DeliveryMethod
  case object Courier extends DeliveryMethod
  case object Post extends DeliveryMethod
  case object PersonalReception extends DeliveryMethod


  sealed trait PaymentMethod
  case object Card extends PaymentMethod
  case object Cash extends PaymentMethod
  case object Cows extends PaymentMethod

  case object CheckoutTimer extends Timer
  case object PaymentTimer extends Timer
}

case class Data(deliveryMethod: Option[DeliveryMethod],
                paymentMethod: Option[PaymentMethod])


class CheckoutFSM extends FSM[State, Data] {
  import scala.concurrent.duration._

  case object Timeout

  startWith(SelectingDelivery, Data(None, None))

  when(SelectingDelivery) {
    case Event(SetDeliveryMethod(method), Data(None, None)) =>
      goto(SelectingPayment) using Data(Some(method), None)
  }

  when(SelectingPayment) {
    case Event(SetPaymentMethod(method), Data(delivery, None)) =>
      close(Data(delivery, Some(method)))
  }

  when(Cancelled) {
    case Event(_, data) =>
      println("cancelled")
      stay using data
  }

  when(Closed) {
    case Event(_, data) =>
      println("closed")
      stay using data
  }

  onTransition {
    case _ -> SelectingDelivery =>
      setTimer("checkoutTimeout", Timeout, 2 seconds, repeat = false)
    case SelectingPayment -> ProcessingPayment =>
      cancelTimer("checkoutTimeout")
      setTimer("paymentTimeout", Timeout, 2 seconds, repeat = false)
    case ProcessingPayment -> _ =>
      cancelTimer("paymentTimeout")
  }

  whenUnhandled {
    case Event(Timeout, data) ⇒
      cancel(data)
  }

  private def close(data: Data) =  {
    println("closing with " + data)
    val paymentRef = context.actorOf(Props(classOf[Payment]))
    context.parent ! CheckoutClosed(paymentRef)
    sender ! CheckoutClosed(paymentRef)
    goto(Closed) using data
    //stop()
  }

  private def cancel(data: Data) = {
    println("Cancelling with " + data)
    context.parent ! CheckoutFailed
    goto(Cancelled) using data
    //stop() this is to be resolved on next labs (as we will put together actors)
  }

  initialize()

}
