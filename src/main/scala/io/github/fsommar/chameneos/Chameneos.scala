/**
 * Author: Fredrik Sommar
 */
package io.github.fsommar.chameneos

import io.github.fsommar.{TypedActor => SafeActor, TypedActorRef => SafeActorRef}

import akka.actor.{ActorSystem, Props}
import akka.event.Logging


object Chameneos {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("Chameneos")

    val mallActor = SafeActorRef[Message](system.actorOf(Props(
      new ChameneosMallActor(
        /* ChameneosConfig.numMeetings */ 2000,
        /* ChameneosConfig.numChameneos */ 10))))
    SafeActorRef.init(mallActor)
    Thread.sleep(2000)
    system.terminate()
  }

  sealed trait Message
  case class MeetMsg(color: Color, sender: SafeActorRef[Message]) extends Message
  case class ChangeMsg(color: Color, sender: SafeActorRef[Message]) extends Message
  case class MeetingCountMsg(count: Int, sender: SafeActorRef[Message]) extends Message
  case class ExitMsg(sender: SafeActorRef[Message]) extends Message

  private class ChameneosMallActor(numMeetings: Int, numChameneos: Int) extends SafeActor[Message] {
    val log = Logging(context.system, this)

    var numMeetingsLeft: Int = numMeetings
    var sumMeetings: Int = 0
    var numFaded: Int = 0
    var waitingChameneo: Option[SafeActorRef[Message]] = None

    override def init() = {
      val colors = List(YELLOW, BLUE, RED)
      1 to numChameneos foreach { i =>
        val color = colors(i % 3)
        val chameneoActor = SafeActorRef[Message](context.system.actorOf(Props(
          new ChameneoActor(self, i, color))))
        SafeActorRef.init(chameneoActor)
      }
    }

    override def receive(msg: Message): Unit = {
      msg match {
        case message: MeetingCountMsg =>
          numFaded += 1
          sumMeetings += message.count
          if (numFaded == numChameneos) {
            log.info("stopping")
            context.stop(self)
          }
        case message: MeetMsg =>
          if (numMeetingsLeft > 0) {
            if (waitingChameneo == None) {
              waitingChameneo = Some(message.sender)
            } else {
              numMeetingsLeft -= 1
              waitingChameneo.get ! message
              waitingChameneo = None
            }
          } else {
            message.sender ! new ExitMsg(self)
          }
        case _ => ???
      }
    }

  }

  private class ChameneoActor(mall: SafeActorRef[Message], id: Int, var color: Color) extends SafeActor[Message] {
    val log = Logging(context.system, this)

    private var meetings: Int = 0

    override def init() = {
      mall ! new MeetMsg(color, self)
    }

    override def receive(msg: Message): Unit = {
      msg match {
        case message: MeetMsg =>
          color = color.complement(message.color)
          meetings += 1
          message.sender ! new ChangeMsg(color, self)
          mall ! new MeetMsg(color, self)
        case message: ChangeMsg =>
          color = message.color
          meetings += 1
          mall ! new MeetMsg(color, self)
        case message: ExitMsg =>
          color = FADED
          log.info(s"Chameneo #${id} is now a faded color.")
          message.sender ! new MeetingCountMsg(meetings, self)
          context.stop(self)
        case _ => ???
      }
    }
  }

}

sealed trait Color {

  def complement(otherColor: Color): Color = {
    this match {
      case RED =>
        otherColor match {
          case RED => RED
          case YELLOW => BLUE
          case BLUE => YELLOW
          case FADED => FADED
        }
      case YELLOW =>
        otherColor match {
          case RED => BLUE
          case YELLOW => YELLOW
          case BLUE => RED
          case FADED => FADED
        }
      case BLUE =>
        otherColor match {
          case RED => YELLOW
          case YELLOW => RED
          case BLUE => BLUE
          case FADED => FADED
        }
      case FADED => FADED
    }
  }
}

case object RED extends Color
case object YELLOW extends Color
case object BLUE extends Color
case object FADED extends Color
