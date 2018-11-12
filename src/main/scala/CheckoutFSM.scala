import CheckoutFSM._
import Payer.{Ok, TestStatus}
import TimerAPI.Timer
import akka.actor.SupervisorStrategy.Stop
import akka.actor.{ActorRef, ActorSystem, FSM, OneForOneStrategy, Props}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.reflect._
import scala.reflect.ClassTag


object CheckoutFSM {

  implicit val getStatus: Int => TestStatus = _ => Ok

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

  sealed trait ErrorReason extends Event {
    def toMsg: String
  }
  case object PaymentServiceUnavailable extends ErrorReason {
    override def toMsg: String = "Payment service unavailable"
  }
  case object PaymentServiceCrashes extends ErrorReason {
    override def toMsg: String = "Payment service crashes"
  }
  case class Error(msg: String) extends Event

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
  case class PaymentWasSet(method: PaymentMethod, manager: ActorRef) extends CheckoutChangeEvent
  case class TimerSet(timer: String) extends CheckoutChangeEvent
  case class TimerCancel(timer: String) extends CheckoutChangeEvent


}

sealed trait Data

case class Methods(deliveryMethod: Option[DeliveryMethod],
                   paymentMethod: Option[PaymentMethod]) extends Data


case class DataWithManager(deliveryMethod: Option[DeliveryMethod],
                paymentMethod: Option[PaymentMethod], manager: ActorRef) extends Data


class CheckoutFSM(implicit val getStatus: Int => Payer.TestStatus) extends PersistentFSM[State, Data, CheckoutChangeEvent] {
  import scala.concurrent.duration._

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = Duration.Inf) {
      case Payer.PaymentServiceTemporarilyUnavailable() =>
        self ! PaymentServiceUnavailable
        Stop
      case _ =>
        // this does not get called because exceeding maxNrOfRetries in Payment actor only stops the child but does not
        // escalate - how to escalate?
        self ! PaymentServiceCrashes
        Stop
    }

  case object Timeout

  override def persistenceId = "persistent-checkout-fsm-id-1"
  override def domainEventClassTag: ClassTag[CheckoutChangeEvent] = classTag[CheckoutChangeEvent]

  startWith(Uninitialized, Methods(None, None))

  when(Uninitialized) {
    case Event(Init, data) =>
      goto(SelectingDelivery) applying TimerSet("checkoutTimeout") replying Done
  }

  when(SelectingDelivery) {
    case Event(SetDeliveryMethod(method), Methods(None, None)) =>
      goto(SelectingPayment) applying DeliveryWasSet(method) replying Done // Data(Some(method), None)
  }

  when(SelectingPayment) {
    case Event(SetPaymentMethod(method), Methods(delivery, None)) =>
      close(Methods(delivery, Some(method)))
  }

  when(Cancelled) {
    case Event(GetState, _) =>
      stay replying stateName
    case Event(_, data) =>
      println("cancelled")
      stay
  }

  when(Closed) {
    case Event(e: ErrorReason, DataWithManager(deliveryMethod, paymentMethod, manager)) =>
      val msg: String = e.toMsg
      manager ! Error(msg)
      context.parent ! Error(msg)
      println("Checkout knows payment service is down")
      stay
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
    case DeliveryWasSet(method) => Methods(Some(method), None)
    case PaymentWasSet(method, manager) =>
      DataWithManager(currentData.asInstanceOf[Methods].deliveryMethod, Some(method), manager)
    case TimerSet(timer) =>
      println("Setting timer")
      setTimer(timer, Timeout, 5 second, repeat = false)
      currentData
    case TimerCancel(timer) =>
      println("Cancelling timer")
      cancelTimer(timer)
      currentData

  }

  private def close(data: Methods) =  {
    println("closing with " + data)
    val paymentRef = context.actorOf(Props(new Payment(getStatus)))
    context.parent ! CheckoutClosed(paymentRef)
    sender ! CheckoutClosed(paymentRef)
    goto(Closed) applying PaymentWasSet(data.paymentMethod.get, sender) applying TimerCancel("checkoutTiemer")
    //stop()
  }

  private def cancel(data: Data) = {
    println("Cancelling with " + data)
    goto(Cancelled)
    //stop() this is to be resolved on next labs (as we will put together actors)
  }

}
