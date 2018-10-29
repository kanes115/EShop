import akka.actor.{Actor, ActorSystem, Props, TimerScheduler, Timers}
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import TimerAPI._


object Cart {

  //commands
  sealed trait Command
  case object Init extends Command
  case class AddItem(item: Item) extends Command
  case class RemoveItem(item: Item) extends Command
  case object GetState extends Command
  case object CheckoutStart extends Command
  case object CheckoutFail extends Command
  case object CheckoutClose extends Command

  sealed trait Event
  case object Done extends Event

  case object CartTimer extends Timer


}

import Cart._
class Cart extends Actor with Timers {

  var items: Set[Item] = Set.empty
  implicit val timerScheduler: TimerScheduler = timers

  override def receive: Receive = LoggingReceive {
    case Init =>
      println("I am instantiated!")
      context become empty
  }


  def empty: Receive = LoggingReceive {
      case Cart.AddItem(item) =>
        println("Adding item " + item.name)
        items = items + item
        context become nonEmpty
    }

  def nonEmpty: Receive = LoggingReceive {
      case Timeout =>
        println("Got timeout")
        clearItems
        context become empty
      case AddItem(item) =>
        resetTimer(CartTimer)
        items = items + item
        context become nonEmpty
      case RemoveItem(item) =>
        resetTimer(CartTimer)
        items = items - item
        if(items.isEmpty){
          stopTimer(CartTimer)
          context become empty
        }
      case CheckoutStart =>
        // here probably communicate with checkout actor
        println("In checkout with items: " + items.toString)
        context become inCheckout
    }

  def inCheckout: Receive = LoggingReceive {
    case CheckoutFail =>
      println("Checkouting failed")
      context become empty
    case CheckoutClose =>
      println("Checkouting with " + items)
      clearItems
      context become nonEmpty

  }

  private def clearItems = items = Set.empty

}

class Customer extends Actor {

  val cart = context.actorOf(Props[Cart], "cart")

  override def receive: Receive = LoggingReceive {
    case "Init" =>
      cart ! Cart.Init
    case e =>
      cart ! e
  }
}

object CartApp extends App{
  val system = ActorSystem("Reactive2")
  val cart = system.actorOf(Props[Customer], "mainActor")

  cart ! "Init"
  cart ! AddItem(Item("ala"))
  cart ! AddItem(Item("ma kota"))
  cart ! CheckoutStart
  cart ! CheckoutClose
  cart ! AddItem(Item("ala"))
  cart ! AddItem(Item("ma kota"))
  cart ! CheckoutStart
  cart ! CheckoutFail
  cart ! AddItem(Item("kicia"))
  cart ! AddItem(Item("misisa"))
  cart ! CheckoutStart
  cart ! CheckoutClose
  cart ! AddItem(Item("lol"))
  Thread.sleep(3000)
  cart ! AddItem(Item("ola"))
  cart ! CheckoutStart
  cart ! CheckoutClose

  Await.result(system.whenTerminated, Duration.Inf)
}
