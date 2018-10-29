import Checkout.{CheckoutClosed, SetDeliveryMethod, SetPaymentMethod}
import OrderManager._
import Payment.PaymentReceived
import akka.actor.{Actor, ActorRef, FSM, Props}

object OrderManager {
  sealed trait State
  case object Uninitialized extends State
  case object Open          extends State
  case object CartClosed    extends State
  case object InCheckout    extends State
  case object InPayment     extends State
  case object Finished      extends State

  sealed trait Command
  case class AddItem(item: Item)                                               extends Command
  case class RemoveItem(item: Item)                                            extends Command
  case class SelectDeliveryAndPaymentMethod(delivery: Checkout.DeliveryMethod, payment: Checkout.PaymentMethod) extends Command
  case object Buy                                                              extends Command
  case object Pay                                                              extends Command

  sealed trait Event
  case class CheckoutStarted(checkoutRef: ActorRef) extends Event

  sealed trait Ack
  case object Done extends Ack //trivial ACK

  sealed trait Data
  case class Empty()                                                           extends Data
  case class CartData(cartRef: ActorRef)                                       extends Data
  case class DeliveryData(delivery: Checkout.DeliveryMethod)                   extends Data
  case class CartDataWithSender(cartRef: ActorRef, sender: ActorRef)           extends Data
  case class InCheckoutData(checkoutRef: ActorRef)                             extends Data
  case class InCheckoutDataWithSender(checkoutRef: ActorRef, sender: ActorRef) extends Data
  case class InPaymentData(paymentRef: ActorRef)                               extends Data
  case class InPaymentDataWithSender(paymentRef: ActorRef, sender: ActorRef)   extends Data
}

class OrderManager(val id: String) extends FSM[OrderManager.State, OrderManager.Data] {

  startWith(Uninitialized, Empty())

  when(Uninitialized) {
    case Event(AddItem(item), _) =>
      val cart = context.actorOf(Props(classOf[CartFSM]))
      cart ! Cart.AddItem(item)
      goto(Open) using CartDataWithSender(cart, sender)
  }

  when(Open) {
    case Event(AddItem(item), _) =>
      val cart = context.actorOf(Props(classOf[CartFSM]))
      cart ! Cart.AddItem(item)
      stay using CartData(cart)
    case Event(RemoveItem(item), data @ CartDataWithSender(cart, _)) =>
      cart ! Cart.RemoveItem(item)
      stay using data
    case Event(Buy, data @ CartDataWithSender(cart, _)) =>
      cart ! Cart.CheckoutStart
      stay using CartDataWithSender(cart, sender)
    case Event(CheckoutStarted(checkoutRef), CartDataWithSender(_, originalSender)) =>
      originalSender ! Done
      goto(InCheckout) using InCheckoutDataWithSender(checkoutRef, sender)
  }

  when(InCheckout) {
    case Event(SelectDeliveryAndPaymentMethod(delivery, payment), InCheckoutDataWithSender(checkoutRef, _)) =>
      checkoutRef ! Checkout.SetDeliveryMethod(delivery)
      checkoutRef ! Checkout.SetPaymentMethod(payment)
      stay using InCheckoutDataWithSender(checkoutRef, sender)
    case Event(Checkout.CheckoutClosed(paymentRef), InCheckoutDataWithSender(checkoutRef, originalSender)) =>
      originalSender ! Done
      goto(InPayment) using InPaymentData(paymentRef)
  }

  when(InPayment) {
    case Event(Pay, InPaymentData(paymentRef)) =>
      paymentRef ! Payment.Pay
      stay using InPaymentDataWithSender(paymentRef, sender)
    case Event(PaymentReceived, InPaymentDataWithSender(_, originalSender)) =>
      originalSender ! Done
      goto(Finished) using Empty()
  }

  when(Finished) {
    case _ => stay using Empty()
  }

  whenUnhandled {
    case Event(Cart.Done, data @ CartDataWithSender(_, originalSender)) =>
      originalSender ! OrderManager.Done
      stay using data
  }

}