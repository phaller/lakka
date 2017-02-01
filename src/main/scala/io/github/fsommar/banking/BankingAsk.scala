/**
 * Author: Fredrik Sommar
 */
package io.github.fsommar.banking

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.util.Timeout

import lacasa.{Box, CanAccess, Safe, Utils}
import Box._

import lacasa.akka.{SafeActor, SafeActorRef}


object BankingAsk {

  def main(args: Array[String]) {
    val system = ActorSystem("BankingAsk")

    val master = SafeActorRef[Message](system.actorOf(Props(
      new Teller(
        /*BankingConfig.A*/ 1000,
        /*BankingConfig.N*/ 50000))))
    SafeActorRef.init(master)
    Thread.sleep(6000)
    system.terminate()
  }

  object Message {
    implicit val MessageIsSafe = new Safe[Message] {}
    implicit val ReplyMsgIsSafe = new Safe[ReplyMsg] {}
    implicit val StopMsgIsSafe = new Safe[StopMsg] {}
    implicit val DebitMsgIsSafe = new Safe[DebitMsg] {}
    implicit val CreditMsgIsSafe = new Safe[CreditMsg] {}
  }

  sealed trait Message
  case class ReplyMsg() extends Message
  case class StopMsg() extends Message
  case class DebitMsg(sender: SafeActorRef[Message], amount: Double) extends Message
  case class CreditMsg(sender: SafeActorRef[Message], amount: Double, recipient: SafeActorRef[Message]) extends Message

  protected class Teller(numAccounts: Int, numBankings: Int) extends SafeActor[Message] {
    val log = Logging(context.system, this)

    private val accounts = Array.tabulate[SafeActorRef[Message]](numAccounts)((i) => {
      SafeActorRef[Message](context.system.actorOf(Props(
        new Account(
          i,
          /*BankingConfig.INITIAL_BALANCE*/ Double.MaxValue / (1000 * 50000)))))
    })
    private var numCompletedBankings = 0
    private val randomGen = new Random(123456)


    override def init() = {
      Utils.loop((1 to numBankings).toIterator) { _ =>
        generateWork()
      }
    }

    override def receive(box: Box[Message])(implicit acc: CanAccess { type C = box.C }): Unit = {
      val msg: Message = box.extract(identity)
      msg match {
        case sm: ReplyMsg =>
          numCompletedBankings += 1
          if (numCompletedBankings == numBankings) {
            Utils.loopAndThen(accounts.toIterator)({ account =>
              account ! new StopMsg()
            }) { () =>
              log.info("stopping")
              context.stop(self)
            }
          }

        case _ => ???
      }
    }

    def generateWork(): Nothing = {
      // src is lower than dest id to ensure there is never a deadlock
      val srcAccountId = randomGen.nextInt((accounts.length / 10) * 8)
      var loopId = randomGen.nextInt(accounts.length - srcAccountId)
      if (loopId == 0) {
        loopId += 1
      }

      val destAccountId = srcAccountId + loopId
      val srcAccount = accounts(srcAccountId)
      val destAccount = accounts(destAccountId)
      val amount = Math.abs(randomGen.nextDouble()) * 1000

      srcAccount ! new CreditMsg(self, amount, destAccount)
    }
  }

  protected class Account(id: Int, var balance: Double) extends SafeActor[Message] {

    // These implicits are required for the `ask` pattern to work.
    implicit val ec: ExecutionContext = context.dispatcher
    implicit val timeout = Timeout(6 seconds)

    override def receive(box: Box[Message])(implicit acc: CanAccess { type C = box.C }): Unit = {
      val msg: Message = box.extract(identity)
      msg match {
        case dm: DebitMsg =>
          balance += dm.amount
          sender() ! new ReplyMsg()

        case cm: CreditMsg =>
          balance -= cm.amount
          mkBoxOf(new DebitMsg(self, cm.amount)) { packed =>
            implicit val acc = packed.access
            cm.recipient.ask(packed.box) { future =>
              Await.result(future, Duration.Inf)
              // Once the transaction is complete, we alert the teller.
              cm.sender ! new ReplyMsg()
            }
          }

        case _: StopMsg =>
          context.stop(self)

        case _ => ???
      }
    }
  }

}
