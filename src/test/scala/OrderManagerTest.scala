import CartFSM.GetItems
import CheckoutFSM.{Done => _, Pay => _, _}
import OrderManager._

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
                                     ): Unit = {
        (orderManager ? message).mapTo[OrderManager.Ack].futureValue shouldBe Done
        orderManager.stateName shouldBe expectedState
      }

  def syncSend(to: ActorRef, msg: Any, expectedRes: Any) = {
    to ! msg
    expectMsg(expectedRes)
  }

  "An order manager" must {
    "supervise whole order process" in {

      val orderManager = TestFSMRef[OrderManager.State, OrderManager.Data, OrderManager](new OrderManager("orderManagerId"))
      orderManager.stateName shouldBe OrderManager.Uninitialized

      sendMessageAndValidateState(orderManager, AddItem(Item("rollerblades")), Open)

      sendMessageAndValidateState(orderManager, Buy, InCheckout)

      sendMessageAndValidateState(orderManager, SelectDeliveryAndPaymentMethod(Courier, Cows), InPayment)

      sendMessageAndValidateState(orderManager, Pay, Finished)
    }

  }

  // We use in-memory journal for convienience
  "A Cart" must {
    "return to persisted state" in {
      val cart = system.actorOf(Props(classOf[CartFSM]))
      syncSend(cart, GetItems, Cart.empty)

      syncSend(cart, CartFSM.AddItem(Item("rollerblades")), CartFSM.Done)
      syncSend(cart, CartFSM.AddItem(Item("ala")), CartFSM.Done)

      syncSend(cart, GetItems, Cart(Set(Item("rollerblades"), Item("ala"))))

      cart ! PoisonPill

      val cart2 = system.actorOf(Props(classOf[CartFSM]))
      syncSend(cart2, GetItems, Cart(Set(Item("rollerblades"), Item("ala"))))

    }

    "recover 5 seconds timer" in {
      val cart = system.actorOf(Props(classOf[CartFSM]))

      syncSend(cart, CartFSM.AddItem(Item("rollerblades")), CartFSM.Done)

      cart ! PoisonPill

      val cart2 = system.actorOf(Props(classOf[CartFSM]))
      Thread.sleep(6000)
      syncSend(cart2, GetItems, Cart.empty)

    }

    "remove items" in {
      val cart = system.actorOf(Props(classOf[CartFSM]))
      syncSend(cart, GetItems, Cart.empty)

      syncSend(cart, CartFSM.AddItem(Item("rollerblades")), CartFSM.Done)
      syncSend(cart, CartFSM.AddItem(Item("ala")), CartFSM.Done)

      syncSend(cart, GetItems, Cart(Set(Item("rollerblades"), Item("ala"))))

      syncSend(cart, CartFSM.RemoveItem(Item("ala")), CartFSM.Done)
    }
  }

  "A checkout" must {
    "restore state" in {
      val checkout = system.actorOf(Props(classOf[CheckoutFSM]))
      syncSend(checkout, Init, CheckoutFSM.Done)
      syncSend(checkout, GetData, Data(None, None))

      syncSend(checkout, SetDeliveryMethod(Courier), CheckoutFSM.Done)
      syncSend(checkout, GetData, Data(Some(Courier), None))

      checkout ! PoisonPill

      val checkout2 = system.actorOf(Props(classOf[CheckoutFSM]))
      syncSend(checkout2, GetData, Data(Some(Courier), None))
    }

    "restore timers" in {
      val checkout = system.actorOf(Props(classOf[CheckoutFSM]))
      syncSend(checkout, Init, CheckoutFSM.Done)
      syncSend(checkout, GetState, SelectingDelivery)
      syncSend(checkout, GetData, Data(None, None))

      syncSend(checkout, SetDeliveryMethod(Courier), CheckoutFSM.Done)
      syncSend(checkout, GetData, Data(Some(Courier), None))

      checkout ! PoisonPill

      val checkout2 = system.actorOf(Props(classOf[CheckoutFSM]))
      syncSend(checkout2, GetData, Data(Some(Courier), None))
      Thread.sleep(6000)
      syncSend(checkout2, GetState, Cancelled)

    }
  }


}
