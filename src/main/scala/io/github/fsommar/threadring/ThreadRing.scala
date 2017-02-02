/**
 * Author: Philipp Haller
 */
package io.github.fsommar.threadring

import io.github.fsommar.{TypedActor => SafeActor, TypedActorRef => SafeActorRef}

import akka.actor.{ActorSystem, Props}
import akka.event.Logging


sealed trait Message

class PingMessage(val pingsLeft: Int) extends Message {
  def hasNext: Boolean =
    pingsLeft > 0

  def next(): PingMessage =
    new PingMessage(pingsLeft - 1)
}

class ExitMessage(val exitsLeft: Int) extends Message {
  def hasNext: Boolean =
    exitsLeft > 0

  def next(): ExitMessage =
    new ExitMessage(exitsLeft - 1)
}

class DataMessage(val data: AnyRef) extends Message


sealed abstract class Action
final case class SendPingMessage(pm: PingMessage) extends Action
final case class SendExitMessage(em: ExitMessage) extends Action
final case class SendLastExitMessage(em: ExitMessage) extends Action
final case class StopSelf() extends Action
final case class SetNextActor(nextActor: SafeActorRef[Message]) extends Action

/* Steps when converting an existing Akka program to LaCasa:
 *
 * 1. Use trait `SafeActor` instead of `Actor`; `SafeActor` provides a `receive`
 *    method with a different signature.
 * 2. Send and receive boxes as messages.
 *    This requires changes such as opening each received box.
 * 3. Creating boxes for messages: here, sent message classes may need to be
 *    changed to provide a no-arg constructor.
 */
private class ThreadRingActor(id: Int, numActorsInRing: Int) extends SafeActor[Message] {
  val log = Logging(context.system, this)

  private var nextActor: SafeActorRef[Message] = _

  def receive(msg: Message): Unit = {
    val action = msg match {
      case pm: PingMessage =>
        log.info(s"received PingMessage: pings left == ${pm.pingsLeft}")
        if (pm.hasNext) SendPingMessage(pm.next())
        else SendExitMessage(new ExitMessage(numActorsInRing))

      case em: ExitMessage =>
        if (em.hasNext) SendLastExitMessage(em.next())
        else StopSelf()

      case dm: DataMessage =>
        log.info(s"received DataMessage: ${dm.data}")
        SetNextActor(dm.data.asInstanceOf[SafeActorRef[Message]])
    }

    // carry out `action`
    action match {
      case SendPingMessage(pm) =>
        nextActor ! pm

      case SendExitMessage(em) =>
        nextActor ! em

      case SendLastExitMessage(em) =>
        nextActor ! em
        log.info(s"stopping ${self.path}")
        context.stop(self)

      case StopSelf() =>
        log.info(s"stopping ${self.path}")
        context.stop(self)

      case SetNextActor(na) =>
        nextActor = na
    }
  }

}

private class PingStartActor(numActorsInRing: Int) extends SafeActor[Any] {

  override def init() = {
    import context._

    val ringActors = Array.tabulate[SafeActorRef[Message]](numActorsInRing)(i => {
      SafeActorRef[Message](system.actorOf(Props(new ThreadRingActor(i, numActorsInRing))))
    })

    val iter = ringActors.view.zipWithIndex.toIterator
    for (elem <- iter) {
      val (loopActor, i) = elem
      val nextActor = ringActors((i + 1) % numActorsInRing)
      loopActor ! new DataMessage(nextActor)
    }
    ringActors(0) ! new PingMessage(10)
  }

  override def receive(msg: Any) = ???

}

object ThreadRing {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("ThreadRing")

    val pingStartActor = SafeActorRef[Any](system.actorOf(Props(
      new PingStartActor(/* ThreadRingConfig.N */ 2))))

    SafeActorRef.init(pingStartActor)
    Thread.sleep(2000)
    system.terminate()
  }

}
