import akka.actor.{Actor, ActorSystem, Props, TimerScheduler, Timers}
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import TimerAPI._

case class Item(name: String)

object Cart {

  //commands
  sealed trait Command
  case object Init extends Command
  case class AddItem(item: Item) extends Command
  case class RemoveItem(item: Item) extends Command
  case object GetState extends Command
  case object CheckoutStart extends Command
  case object CheckoutFail extends Command

  case object CartTimer extends Timer

}

class Cart extends Actor with Timers {
  import Cart._

  var items: List[Item] = List()
  implicit val timerScheduler: TimerScheduler = timers

  override def receive: Receive = LoggingReceive {
    case Init =>
      println("I am instantiated!")
      context become empty
  }


  def empty: Receive = LoggingReceive {
      case Cart.AddItem(item) =>
        println("Adding item " + item.name)
        items = item :: items
        context become nonEmpty
    }

  def nonEmpty: Receive = LoggingReceive {
      case Timeout =>
        println("Got timeout")
        clearItems
        context become empty
      case AddItem(item) =>
        resetTimer(CartTimer)
        println("Adding item " + item.name)
        items = item :: items
        context become nonEmpty
      case RemoveItem(item) =>
        println("Removing item " + item.name)
        resetTimer(CartTimer)
        items = items.filter(_ != item) // Delete it in another way (so that we can have multiple the same items
        if(items.isEmpty){
          stopTimer(CartTimer)
          println("Am empty again")
          context become empty
        }
      case CheckoutStart =>
        // here probably communicate with checkout actor
        println("In checkout with items: " + items.toString)
        context become inCheckout
    }

  def inCheckout: Receive = LoggingReceive {
    case CheckoutFail =>
      clearItems
      context become empty
  }

  private def clearItems = items = List()

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
  val mainActor = system.actorOf(Props[Customer], "mainActor")

  mainActor ! "Init"
  mainActor ! Cart.AddItem(Item("koka kola"))
  mainActor ! Cart.AddItem(Item("ziemniak"))
  mainActor ! Cart.RemoveItem(Item("ziemniak"))
  Thread.sleep(10059)

  Await.result(system.whenTerminated, Duration.Inf)
}
