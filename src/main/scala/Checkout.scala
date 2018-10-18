import akka.actor.{Actor, ActorSystem, Props, Timers}
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object Checkout {

  //commands
  sealed trait Command
  case object Init extends Command
  case class SetDeliveryMethod(deliveryMethod: DeliveryMethod) extends Command
  case class SetPaymentMethod(paymentMethod: PaymentMethod) extends Command
  case object Pay extends Command

  sealed trait Event

  sealed trait Timer
  case object CheckoutTimer extends Timer
  case object PaymentTimer extends Timer
  case object Timeout extends Event

  sealed trait DeliveryMethod
  case object Courier extends DeliveryMethod
  case object Post extends DeliveryMethod
  case object PersonalReception extends DeliveryMethod

  sealed trait PaymentMethod
  case object Card extends PaymentMethod
  case object Cash extends PaymentMethod
  case object Cows extends PaymentMethod
}

class Checkout extends Actor with Timers {
  import Checkout._
  import scala.concurrent.duration._

  var deliveryMethod: Option[DeliveryMethod] = None
  var paymentMethod: Option[PaymentMethod] = None

  override def receive: Receive = LoggingReceive {
    case Init =>
      println("I am instantiated!")
      resetClock(CheckoutTimer)
      context become selectingDelivery
  }


  def selectingDelivery: Receive = LoggingReceive {
    case SetDeliveryMethod(method) =>
      setDeliveryMethod(method)
      context become selectingPaymentMethod
    case Timeout => cancel
    }

  def selectingPaymentMethod: Receive = LoggingReceive {
    case SetPaymentMethod(method) =>
      setPaymentMethod(method)
      timers.cancel(CheckoutTimer)
      resetClock(PaymentTimer)
      context become processingPayment
    case Timeout => cancel
  }

  def processingPayment: Receive = LoggingReceive {
    case Pay => close
    case Timeout => cancel
  }

  def closed: Receive = LoggingReceive {
    case _ => println("closed")
  }

  def cancelled: Receive = {
    case _ => println("cancelled")
  }

  private def setDeliveryMethod(method: DeliveryMethod) =
    this.deliveryMethod = Some(method)

  private def setPaymentMethod(method: PaymentMethod) =
    this.paymentMethod = Some(method)


  private def resetClock(timer: Timer) = {
    timers.cancel(timer)
    timers.startSingleTimer(timer, Timeout, 10 seconds)
  }

  private def close = {
    println("closing - payment method: " + paymentMethod.get + " and delivery method: " + deliveryMethod.get)
    context become closed
  }

  private def cancel = {
    println("closing...")
    context become cancelled
  }
}

class ProbablyCart extends Actor {

  val checkout = context.actorOf(Props[Checkout], "checkout")

  override def receive: Receive = {
    case "Init" =>
      checkout ! Checkout.Init
    case e =>
      checkout ! e
  }
}

object Checkoutpp extends App{
  import Checkout._
  val system = ActorSystem("Reactive2")
  val mainActor = system.actorOf(Props[ProbablyCart], "mainActor")

  mainActor ! "Init"
  mainActor ! SetDeliveryMethod(Courier)
  mainActor ! SetPaymentMethod(Cows)
  mainActor ! Pay

  Await.result(system.whenTerminated, Duration.Inf)
}
