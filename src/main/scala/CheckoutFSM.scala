import CheckoutFSM._
import TimerAPI.Timer
import akka.actor.{ActorRef, ActorSystem, FSM, Props}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.reflect._
import scala.reflect.ClassTag


object CheckoutFSM {

  //commands
  sealed trait Command
  case object Init extends Command
  case class SetDeliveryMethod(deliveryMethod: DeliveryMethod) extends Command
  case class SetPaymentMethod(paymentMethod: PaymentMethod) extends Command
  case object Pay extends Command

  sealed trait TestAPICommand extends Command
  case object GetData extends TestAPICommand
  case object GetState extends TestAPICommand

  sealed trait Event
  case class CheckoutClosed(paymentRef: ActorRef) extends Event
  case object CheckoutFailed extends Event

  sealed trait State extends FSMState
  case object Uninitialized extends State {
    override def identifier: String = "uninitialized"
  }
  case object SelectingDelivery extends State {
    override def identifier: String = "delivery-select"
  }
  case object SelectingPayment extends State{
    override def identifier: String = "payment-select"
  }
  case object ProcessingPayment extends State{
    override def identifier: String = "payment-processingg"
  }
  case object Cancelled extends State{
    override def identifier: String = "cancelled"
  }
  case object Closed extends State{
    override def identifier: String = "closed"
  }


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

  case object Done


  sealed trait CheckoutChangeEvent
  case class DeliveryWasSet(method: DeliveryMethod) extends CheckoutChangeEvent
  case class PaymentWasSet(method: PaymentMethod) extends CheckoutChangeEvent
  case class TimerSet(timer: String) extends CheckoutChangeEvent
  case class TimerCancel(timer: String) extends CheckoutChangeEvent


}

case class Data(deliveryMethod: Option[DeliveryMethod],
                paymentMethod: Option[PaymentMethod])


class CheckoutFSM extends PersistentFSM[State, Data, CheckoutChangeEvent] {
  import scala.concurrent.duration._

  case object Timeout

  override def persistenceId = "persistent-checkout-fsm-id-1"
  override def domainEventClassTag: ClassTag[CheckoutChangeEvent] = classTag[CheckoutChangeEvent]

  startWith(Uninitialized, Data(None, None))

  when(Uninitialized) {
    case Event(Init, data) =>
      goto(SelectingDelivery) applying TimerSet("checkoutTimeout") replying Done
  }

  when(SelectingDelivery) {
    case Event(SetDeliveryMethod(method), Data(None, None)) =>
      goto(SelectingPayment) applying DeliveryWasSet(method) replying Done // Data(Some(method), None)
  }

  when(SelectingPayment) {
    case Event(SetPaymentMethod(method), Data(delivery, None)) =>
      close(Data(delivery, Some(method)))
  }

  when(Cancelled) {
    case Event(GetState, _) =>
      stay replying stateName
    case Event(_, data) =>
      println("cancelled")
      stay
  }

  when(Closed) {
    case Event(GetState, _) =>
      stay replying stateName
    case Event(_, data) =>
      println("closed")
      stay
  }

  whenUnhandled {
    case Event(Timeout, data) ⇒
      cancel(data)
    case Event(GetData, data) =>
      stay replying data
    case Event(GetState, _) =>
      stay replying stateName
  }


  override def applyEvent(domainEvent: CheckoutChangeEvent, currentData: Data): Data = domainEvent match {
    case DeliveryWasSet(method) => Data(Some(method), None)
    case PaymentWasSet(method) => Data(currentData.deliveryMethod, Some(method))
    case TimerSet(timer) =>
      println("Setting timer")
      setTimer(timer, Timeout, 5 second, repeat = false)
      currentData
    case TimerCancel(timer) =>
      println("Cancelling timer")
      cancelTimer(timer)
      currentData

  }

  private def close(data: Data) =  {
    println("closing with " + data)
    val paymentRef = context.actorOf(Props(classOf[Payment]))
    context.parent ! CheckoutClosed(paymentRef)
    sender ! CheckoutClosed(paymentRef)
    goto(Closed) applying PaymentWasSet(data.paymentMethod.get) applying TimerCancel("checkoutTiemer")
    //stop()
  }

  private def cancel(data: Data) = {
    println("Cancelling with " + data)
    goto(Cancelled)
    //stop() this is to be resolved on next labs (as we will put together actors)
  }

}
