import akka.actor.TimerScheduler

object TimerAPI {
  import scala.concurrent.duration._

  trait Timer
  trait Event
  case object Timeout extends Event

  def startTimer(timer: Timer)(implicit timers: TimerScheduler) =
    timers.startSingleTimer(timer, Timeout, 2 seconds)

  def stopTimer(timer: Timer)(implicit timers: TimerScheduler) =
    timers.cancel(timer)

  def resetTimer(timer: Timer)(implicit timers: TimerScheduler) = {
    stopTimer(timer)
    startTimer(timer)
  }

}
