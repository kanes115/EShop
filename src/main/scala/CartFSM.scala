import akka.actor.{Actor, ActorSystem, FSM, Props}

import scala.concurrent.duration._
import Cart._


object CartFSM {

  sealed trait State

  case object Empty extends State

  case object NonEmpty extends State

  case object InCheckout extends State

}


class CartFSM extends Actor with FSM[CartFSM.State, Set[Item]] {
  import CartFSM._

  startWith(Empty, Set.empty) // change list for set

  when(Empty) {
    case Event(Cart.AddItem(item), s) if s.isEmpty =>
      println("sending done")
      sender ! Done
      goto(NonEmpty) using Set(item)
  }

  when(NonEmpty, stateTimeout = 2 seconds) {
    case Event(AddItem(item), items) =>
      println("sending done")
      sender ! Done
      stay using items + item
    case Event(RemoveItem(itemToRemove), items) if items.size == 1 && items(itemToRemove) =>
      goto(Empty) using Set.empty
    case Event(RemoveItem(item), items) =>
      stay using items - item
    case Event(CheckoutStart, items) =>
      println("Checkouting with " + items.toString)
      //do sth
      val checkout = context.actorOf(Props(classOf[CheckoutFSM]))
      sender ! OrderManager.CheckoutStarted(checkout)
      goto(InCheckout) using items
  }

  when(InCheckout) {
    case Event(CheckoutFail, data) =>
      goto(NonEmpty) using data
    case Event(Checkout.CheckoutClosed(_), data) =>
      goto(Empty) using Set.empty
  }


  whenUnhandled {
    // common code for both states
    case Event(StateTimeout, _) ⇒
      println("Timeout. Going empty")
      goto(Empty) using Set.empty
  }

  initialize()

}

object CartFSMApp extends App {
  val system = ActorSystem("Reactive2")
  val cart = system.actorOf(Props(classOf[CartFSM]))
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
}
