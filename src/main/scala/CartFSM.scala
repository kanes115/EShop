import akka.actor.{Actor, ActorSystem, FSM, Props}

import scala.concurrent.duration._
import TimerAPI.Timer


object CartFSM {

  //commands
  sealed trait Command
  case object Init extends Command
  case class AddItem(item: Item) extends Command
  case class RemoveItem(item: Item) extends Command
  case object GetState extends Command
  case object CheckoutStart extends Command

  sealed trait Event
  case object Done extends Event

  case object CartTimer extends Timer

  sealed trait State
  case object Empty extends State
  case object NonEmpty extends State
  case object InCheckout extends State

}


class CartFSM extends Actor with FSM[CartFSM.State, Set[Item]] {
  import CartFSM._

  startWith(Empty, Set.empty) // change list for set

  when(Empty) {
    case Event(AddItem(item), s) if s.isEmpty =>
      println(sender)
      sender ! Done
      goto(NonEmpty) using Set(item)
  }

  when(NonEmpty, stateTimeout = 2 seconds) {
    case Event(AddItem(item), items) =>
      sender ! Done
      stay using items + item
    case Event(RemoveItem(itemToRemove), items) if items.size == 1 && items(itemToRemove) =>
      goto(Empty) using Set.empty
    case Event(RemoveItem(item), items) =>
      stay using items - item
    case Event(CheckoutStart, items) =>
      val checkout = context.actorOf(Props(classOf[CheckoutFSM]))
      sender ! OrderManager.CheckoutStarted(checkout)
      goto(InCheckout) using items
  }

  when(InCheckout) {
    case Event(CheckoutFSM.CheckoutFailed, data) =>
      goto(NonEmpty) using data
    case Event(CheckoutFSM.CheckoutClosed(_), data) =>
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
