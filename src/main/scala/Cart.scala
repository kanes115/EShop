import Cart.GetState
import akka.actor.{Actor, ActorSystem, Props, Timers}
import akka.event.LoggingReceive

import scala.concurrent.Await
import scala.concurrent.duration.Duration

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

  sealed trait Event
  case object Timeout extends Event
  case object CartTimer
}

class Cart extends Actor with Timers {
  import Cart._
  import scala.concurrent.duration._

  var items: List[Item] = List()

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
      case GetState =>
        println("Currently items are: " + items)
    }

  def nonEmpty: Receive = LoggingReceive {
      case Timeout =>
        clearItems
        context become empty
      case Cart.AddItem(item) =>
        resetClock
        println("Adding item " + item.name)
        items = item :: items
        context become nonEmpty
      case Cart.RemoveItem(item) =>
        println("Removing item " + item.name)
        resetClock
        items = items.filter(_ != item) // Delete it in another way (so that we can have multiple the same items
        if(items.isEmpty){
          timers.cancel(CartTimer)
          context become empty
        }
      case Cart.CheckoutStart =>
        // here probably communicate with checkout actor
        context become inCheckout
      case GetState =>
        println("Currently items are: " + items)
    }

  def inCheckout: Receive = LoggingReceive {
    case Cart.CheckoutFail =>
      clearItems
      context become empty
  }

  private def resetClock = {
    timers.cancel(CartTimer)
    timers.startSingleTimer(CartTimer, Timeout, 10 seconds)
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
  mainActor ! GetState
  mainActor ! Cart.RemoveItem(Item("koka kola"))
  mainActor ! GetState
  Thread.sleep(10059)
  mainActor ! GetState

  Await.result(system.whenTerminated, Duration.Inf)
}
