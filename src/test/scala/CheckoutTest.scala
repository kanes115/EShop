import CheckoutFSM._
import akka.actor.{ActorSystem, Props}
import akka.testkit._
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

class CheckoutTest extends TestKit(ActorSystem("CheckoutSystem"))
  with WordSpecLike with BeforeAndAfterAll with ImplicitSender {

  override def afterAll(): Unit = {
    system.terminate
  }

  "Checkout" must {
    "ala" in {
      val cart = TestProbe()
      val checkout = cart.childActorOf(Props(new CheckoutFSM()))
      checkout ! Init
      checkout ! SetDeliveryMethod(Courier)
      checkout ! SetPaymentMethod(Cows)
      cart.expectMsgType[CheckoutClosed]
    }
  }

}
