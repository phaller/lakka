package io.github.fsommar

import scala.language.implicitConversions

import akka.actor.{Actor, ActorRef}


private case object InitMessage

/**
 * An actor implementation that mimics SafeActor[T], provided by LaCasa.
 */
trait TypedActor[T] extends Actor {

  implicit def actorRefToTypedActorRef(actorRef: ActorRef) = TypedActorRef[T](actorRef)

  protected final val safeSelf: TypedActorRef[T] = self

  def receive(msg: T): Unit
  def init(): Unit = {}

  final def receive = {
    case InitMessage =>
      init()

    case msg =>
      receive(msg.asInstanceOf[T])
  }

}


object TypedActorRef {
  implicit def typedActorRefToActorRef(typedActorRef: TypedActorRef[_]): ActorRef = typedActorRef.ref

  def apply[T](ref: ActorRef): TypedActorRef[T] =
    new TypedActorRef[T](ref)

  def init[T](ref: TypedActorRef[T]): Unit = {
    ref.ref ! InitMessage
  }

}

/**
 * The actor ref is intentionally kept as simple as possible
 */
class TypedActorRef[T](private val ref: ActorRef) {

  def ! (msg: T): Unit = {
    ref ! msg
  }
}
