import CartFSM.GetItems
import CheckoutFSM.{Done => _, Pay => _, _}
import OrderManager.{Error, _}

import scala.concurrent.duration._
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.persistence.inmemory.extension.{InMemoryJournalStorage, InMemorySnapshotStorage, StorageExtension}
import akka.testkit.{ImplicitSender, TestActors, TestFSMRef, TestKit, TestProbe}
import akka.util.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Milliseconds, Span}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Matchers, WordSpecLike}

class OrderManagerTest
  extends TestKit(ActorSystem("OrderManagerTest"))
    with WordSpecLike
    with BeforeAndAfterAll
    with BeforeAndAfterEach
    with ScalaFutures
    with Matchers
    with ImplicitSender {

  override protected def beforeEach(): Unit = {
    val tp = TestProbe()
    tp.send(StorageExtension(system).journalStorage, InMemoryJournalStorage.ClearJournal)
    tp.expectMsg(akka.actor.Status.Success(""))
    tp.send(StorageExtension(system).snapshotStorage, InMemorySnapshotStorage.ClearSnapshots)
    tp.expectMsg(akka.actor.Status.Success(""))
    super.beforeEach()
  }

  override implicit def patienceConfig: PatienceConfig = PatienceConfig(timeout = Span(5000, Milliseconds))

  implicit val timeout: Timeout = 5.second

  def sendMessageAndValidateState(
                                       orderManager: TestFSMRef[OrderManager.State, OrderManager.Data, OrderManager],
                                       message: OrderManager.Command,
                                       expectedState: OrderManager.State
                                     )(implicit ack: Ack): Unit = {
        (orderManager ? message).mapTo[OrderManager.Ack].futureValue shouldBe ack
        orderManager.stateName shouldBe expectedState
      }

  def syncSend(to: ActorRef, msg: Any, expectedRes: Any) = {
    to ! msg
    expectMsg(expectedRes)
  }

  implicit val ack: Ack = Done
  implicit val getStatus = (counter: Int) => Payer.Ok

  "An order manager" must {

    // getStatus must return 200 after a few tries
    "supervise whole order process" in {
      implicit val getStatus = (counter: Int) => if(counter < 2) Payer.Internal else Payer.Ok

      val orderManager = TestFSMRef[OrderManager.State, OrderManager.Data, OrderManager](new OrderManager("orderManagerId"))
      orderManager.stateName shouldBe OrderManager.Uninitialized

      sendMessageAndValidateState(orderManager, AddItem(Item("rollerblades")), Open)

      sendMessageAndValidateState(orderManager, Buy, InCheckout)

      sendMessageAndValidateState(orderManager, SelectDeliveryAndPaymentMethod(Courier, Cows), InPayment)

      sendMessageAndValidateState(orderManager, Pay, Finished)
    }

  }

  // getStatus must return 503
  "supervise whole order process and gets error if payment service returns 503" in {

      implicit val getStatus = (counter: Int) => Payer.Unavailable

      val orderManager = TestFSMRef[OrderManager.State, OrderManager.Data, OrderManager](new OrderManager("orderManagerId"))
      orderManager.stateName shouldBe OrderManager.Uninitialized

      sendMessageAndValidateState(orderManager, AddItem(Item("rollerblades")), Open)

      sendMessageAndValidateState(orderManager, Buy, InCheckout)

      sendMessageAndValidateState(orderManager, SelectDeliveryAndPaymentMethod(Courier, Cows), InPayment)

      sendMessageAndValidateState(orderManager, Pay, Finished)(Error)
    }

  // I don't know how to escalate on exceeding maxNrOfRetries (in Payment actor) instead of just stopping Payer child
  // uncomment when you know
  // getStatus must return 500 all the time
//  "supervise whole order process and gets error if payment service returns 500 more than 3 times in 1 minute" in {
//      implicit val getStatus = (_: Int) => Payer.Internal
//
//      val orderManager = TestFSMRef[OrderManager.State, OrderManager.Data, OrderManager](new OrderManager("orderManagerId"))
//      orderManager.stateName shouldBe OrderManager.Uninitialized
//
//      sendMessageAndValidateState(orderManager, AddItem(Item("rollerblades")), Open)
//
//      sendMessageAndValidateState(orderManager, Buy, InCheckout)
//
//      sendMessageAndValidateState(orderManager, SelectDeliveryAndPaymentMethod(Courier, Cows), InPayment)
//
//      sendMessageAndValidateState(orderManager, Pay, Finished)(Error)
//    }

  // We use in-memory journal for convienience
  "A Cart" must {
    "return to persisted state" in {
      val cart = system.actorOf(Props(new CartFSM()))
      syncSend(cart, GetItems, Cart.empty)

      syncSend(cart, CartFSM.AddItem(Item("rollerblades")), CartFSM.Done)
      syncSend(cart, CartFSM.AddItem(Item("ala")), CartFSM.Done)

      syncSend(cart, GetItems, Cart(Set(Item("rollerblades"), Item("ala"))))

      cart ! PoisonPill

      val cart2 = system.actorOf(Props(new CartFSM()))
      syncSend(cart2, GetItems, Cart(Set(Item("rollerblades"), Item("ala"))))

    }

    "recover 5 seconds timer" in {
      val cart = system.actorOf(Props(new CartFSM()))

      syncSend(cart, CartFSM.AddItem(Item("rollerblades")), CartFSM.Done)

      cart ! PoisonPill

      val cart2 = system.actorOf(Props(new CartFSM()))
      Thread.sleep(6000)
      syncSend(cart2, GetItems, Cart.empty)

    }

    "remove items" in {
      val cart = system.actorOf(Props(new CartFSM()))
      syncSend(cart, GetItems, Cart.empty)

      syncSend(cart, CartFSM.AddItem(Item("rollerblades")), CartFSM.Done)
      syncSend(cart, CartFSM.AddItem(Item("ala")), CartFSM.Done)

      syncSend(cart, GetItems, Cart(Set(Item("rollerblades"), Item("ala"))))

      syncSend(cart, CartFSM.RemoveItem(Item("ala")), CartFSM.Done)
    }
  }

  "A checkout" must {
    "restore state" in {
      val checkout = system.actorOf(Props(new CheckoutFSM()))
      syncSend(checkout, Init, CheckoutFSM.Done)
      syncSend(checkout, GetData, Methods(None, None))

      syncSend(checkout, SetDeliveryMethod(Courier), CheckoutFSM.Done)
      syncSend(checkout, GetData, Methods(Some(Courier), None))

      checkout ! PoisonPill

      val checkout2 = system.actorOf(Props(new CheckoutFSM()))
      syncSend(checkout2, GetData, Methods(Some(Courier), None))
    }

    "restore timers" in {
      val checkout = system.actorOf(Props(new CheckoutFSM()))
      syncSend(checkout, Init, CheckoutFSM.Done)
      syncSend(checkout, GetState, SelectingDelivery)
      syncSend(checkout, GetData, Methods(None, None))

      syncSend(checkout, SetDeliveryMethod(Courier), CheckoutFSM.Done)
      syncSend(checkout, GetData, Methods(Some(Courier), None))

      checkout ! PoisonPill

      val checkout2 = system.actorOf(Props(new CheckoutFSM()))
      syncSend(checkout2, GetData, Methods(Some(Courier), None))
      Thread.sleep(6000)
      syncSend(checkout2, GetState, Cancelled)

    }
  }


}
