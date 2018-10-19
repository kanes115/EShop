import akka.actor.{Actor, ActorSystem, FSM, Props}

import scala.concurrent.duration._
import Cart._


object CartFSM {

  sealed trait State

  case object Empty extends State

  case object NonEmpty extends State

  case object InCheckout extends State

}


class CartFSM extends Actor with FSM[CartFSM.State, List[Item]] {
  import CartFSM._

  startWith(Empty, List()) // change list for set

  when(Empty) {
    case Event(Cart.AddItem(item), Nil) =>
      goto(NonEmpty) using List(item)
  }

  when(NonEmpty, stateTimeout = 2 seconds) {
    case Event(AddItem(item), list) =>
      stay using item :: list
    case Event(RemoveItem(itemToRemove), x :: Nil) if itemToRemove == x =>
      goto(Empty) using Nil
    case Event(RemoveItem(item), list) =>
      stay using list.filter(_ != item)
    case Event(CheckoutStart, list) =>
      println("Checkouting with " + list.toString)
      //do sth
      goto(InCheckout) using list
  }

  when(InCheckout) {
    case Event(CheckoutFail, data) =>
      goto(NonEmpty) using data
    case Event(CheckoutClose, data) =>
      goto(Empty) using Nil
  }


  whenUnhandled {
    // common code for both states
    case Event(StateTimeout, _) ⇒
      println("Timeout. Going empty")
      goto(Empty) using Nil
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
