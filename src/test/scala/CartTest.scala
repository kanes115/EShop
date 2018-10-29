import CartFSM._
import akka.actor.{ActorSystem, Props}
import akka.testkit._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class CartTest extends TestKit(ActorSystem("CartSystem"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  override def afterAll(): Unit = {
    system.terminate
  }

  "Cart" must {
    "Fill items list with new item" in {
      import akka.testkit.TestFSMRef

      val fsm = TestFSMRef(new CartFSM)

      val mustBeTypedProperly: TestActorRef[CartFSM] = fsm

      assert(fsm.stateName == Empty)
      assert(fsm.stateData == Set())
      fsm ! AddItem(Item("bike"))
      assert(fsm.stateName == NonEmpty)
      assert(fsm.stateData == Set(Item("bike")))
    }

    "remove items from set" in {
      import akka.testkit.TestFSMRef

      val fsm = TestFSMRef(new CartFSM)

      val mustBeTypedProperly: TestActorRef[CartFSM] = fsm

      assert(fsm.stateName == Empty)
      assert(fsm.stateData == Set())
      fsm ! AddItem(Item("bike"))
      assert(fsm.stateName == NonEmpty)
      assert(fsm.stateData == Set(Item("bike")))
      fsm ! RemoveItem(Item("bike"))
      assert(fsm.stateName == Empty)
      assert(fsm.stateData == Set())
    }

    "get Done after adding item" in {
      val cart = system.actorOf(Props(classOf[CartFSM]))
      cart ! AddItem(Item("kot"))
      expectMsg(Done)
    }
  }



}
