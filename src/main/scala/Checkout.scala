import TimerAPI._
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

class Checkout extends Actor with Timers {
  import Checkout._

  var deliveryMethod: Option[DeliveryMethod] = None
  var paymentMethod: Option[PaymentMethod] = None
  implicit val timerScheduler = super.timers

  override def receive: Receive = LoggingReceive {
    case Init =>
      println("I am instantiated!")
      startTimer(CheckoutTimer)
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
      startTimer(PaymentTimer)
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

  private def close = {
    println("closing - payment method: " + paymentMethod.get + " and delivery method: " + deliveryMethod.get)
    context become closed
  }

  private def cancel = {
    println("cancelling...")
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
  Thread.sleep(3000)
  //mainActor ! SetPaymentMethod(Cows)
  //mainActor ! Pay

  Await.result(system.whenTerminated, Duration.Inf)
}
