import CartFSM.CartChangeEvent
import akka.actor.{Actor, ActorSystem, FSM, Props}

import scala.concurrent.duration._
import TimerAPI.Timer
import akka.persistence.{Recovery, SnapshotSelectionCriteria}
import akka.persistence.fsm.PersistentFSM
import akka.persistence.fsm.PersistentFSM.FSMState

import scala.reflect._



object CartFSM {

  //commands
  sealed trait Command
  case object Init extends Command
  case class AddItem(item: Item) extends Command
  case class RemoveItem(item: Item) extends Command
  case object GetState extends Command
  case object CheckoutStart extends Command

  sealed trait TestAPICommand extends Command
  case object GetItems extends TestAPICommand

  sealed trait Event
  case object Done extends Event
  case object Timeout

  case object CartTimer extends Timer

  sealed trait State extends FSMState
  case object Empty extends State{
    override def identifier: String = "empty"
  }
  case object NonEmpty extends State{
    override def identifier: String = "non-empty"
  }
  case object InCheckout extends State{
    override def identifier: String = "in-checkout"
  }
  sealed trait CartChangeEvent
  case class ItemAdded(item: Item) extends CartChangeEvent
  case class ItemRemoved(item: Item) extends CartChangeEvent
  case object CartSetEmpty extends CartChangeEvent
  case class TimerSet(timer: String) extends CartChangeEvent
  case class TimerCancel(timer: String) extends CartChangeEvent

}


class CartFSM extends Actor with PersistentFSM[CartFSM.State, Cart, CartChangeEvent] {
  import CartFSM._

  override def persistenceId = "persistent-cart-fsm-id-1"
  override def domainEventClassTag: ClassTag[CartChangeEvent] = classTag[CartChangeEvent]

  startWith(Empty, Cart.empty)

  when(Empty) {
    case Event(AddItem(item), s) if s.isEmpty =>
      println("Already have items: " + s)
      goto(NonEmpty) applying ItemAdded(item) applying TimerSet("timeout") replying Done
  }

  when(NonEmpty) {
    case Event(AddItem(item), items) =>
      stay applying ItemAdded(item) replying Done
    case Event(RemoveItem(itemToRemove), items) if items.size == 1 && items.contains(itemToRemove) =>
      goto(Empty) applying CartSetEmpty applying TimerCancel("timeout") replying Done
    case Event(RemoveItem(item), items) =>
      stay applying ItemRemoved(item) replying Done
    case Event(CheckoutStart, items) =>
      val checkout = context.actorOf(Props(classOf[CheckoutFSM]))
      sender ! OrderManager.CheckoutStarted(checkout)
      goto(InCheckout)
  }

  when(InCheckout) {
    case Event(CheckoutFSM.CheckoutFailed, data) =>
      goto(NonEmpty)
    case Event(CheckoutFSM.CheckoutClosed(_), data) =>
      goto(Empty) applying CartSetEmpty
  }

  override def applyEvent(cartChangeEvent: CartChangeEvent, dataBeforeEvent: Cart): Cart =
    cartChangeEvent match {
      case ItemAdded(item) => dataBeforeEvent + item
      case ItemRemoved(item) => dataBeforeEvent - item
      case CartSetEmpty => Cart.empty
      case TimerSet(timer) =>
        println("Setting timer")
        setTimer(timer, Timeout, 5 second, repeat = false)
        dataBeforeEvent
      case TimerCancel(timer) =>
        println("Cancelling timer")
        cancelTimer(timer)
        dataBeforeEvent
    }

  whenUnhandled {
    // common code for both states
    case Event(Timeout, _) ⇒
      println("Timeout. Going empty")
      goto(Empty) applying CartSetEmpty applying TimerCancel("timeout")
    case Event(GetItems, items) =>
      stay replying items
  }


//  onTransition {
//    case Empty -> NonEmpty =>
//      println("will set timer")
//      goto(NonEmpty) applying TimerSet("timeout")
//    case NonEmpty -> some if some == Empty || some == InCheckout  =>
//      println("will cancel timer")
//      goto(NonEmpty) applying TimerCancel("timeout")
//  }

}
