import Checkout._
import akka.actor.{ActorSystem, FSM, Props}

sealed trait State
case object SelectingDelivery extends State
case object SelectingPayment extends State
case object ProcessingPayment extends State
case object Cancelled extends State
case object Closed extends State

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
      goto(ProcessingPayment) using Data(delivery, Some(method))
  }

  when(ProcessingPayment) {
    case Event(Pay, data) =>
      close(data)
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
    goto(Closed) using data
    //stop()
  }

  private def cancel(data: Data) = {
    println("Cancelling with " + data)
    goto(Cancelled) using data
    //stop() this is to be resolved on next labs (as we will put together actors)
  }

  initialize()

}

object CheckoutFSMApp extends App {
  val system = ActorSystem("Reactive2")
  val checkout = system.actorOf(Props(classOf[CheckoutFSM]))
  checkout ! SetDeliveryMethod(Courier)
  checkout ! SetPaymentMethod(Cows)
  Thread.sleep(2500) // move this line between messages to test each use case
  checkout ! Pay
}
