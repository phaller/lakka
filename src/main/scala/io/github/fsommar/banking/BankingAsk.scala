/**
 * Author: Fredrik Sommar
 */
package io.github.fsommar.banking

import io.github.fsommar.{TypedActor => SafeActor, TypedActorRef => SafeActorRef}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Random

import akka.actor.{ActorSystem, Props}
import akka.event.Logging
import akka.pattern.ask
import akka.util.Timeout


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
      for (_ <- 1 to numBankings) {
        generateWork()
      }
    }

    override def receive(msg: Message): Unit = {
      msg match {
        case sm: ReplyMsg =>
          numCompletedBankings += 1
          if (numCompletedBankings == numBankings) {
            for (account <- accounts) {
              account ! new StopMsg()
            }
            log.info("stopping")
            context.stop(self)
          }

        case _ => ???
      }
    }

    def generateWork(): Unit = {
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
    // implicit val ec: ExecutionContext = context.dispatcher
    implicit val timeout = Timeout(6 seconds)

    override def receive(msg: Message): Unit = {
      msg match {
        case dm: DebitMsg =>
          balance += dm.amount
          sender() ! new ReplyMsg()

        case cm: CreditMsg =>
          balance -= cm.amount
          val future = ask(cm.recipient, new DebitMsg(self, cm.amount))
          Await.result(future, Duration.Inf)
          // Once the transaction is complete, we alert the teller.
          cm.sender ! new ReplyMsg()

        case _: StopMsg =>
          context.stop(self)

        case _ => ???
      }
    }
  }

}
