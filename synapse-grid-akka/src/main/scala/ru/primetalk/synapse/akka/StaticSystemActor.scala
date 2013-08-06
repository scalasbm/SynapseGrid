///////////////////////////////////////////////////////////////
// © ООО «Праймтолк», 2011-2013                              //
// Все права принадлежат компании ООО «Праймтолк».           //
///////////////////////////////////////////////////////////////
/**
 * SynapseGrid
 * © Primetalk Ltd., 2013.
 * All rights reserved.
 * Authors: A.Zhizhelev, A.Nehaev, P. Popov
 * (2-clause BSD license) See LICENSE
 *
 * Created: 01.07.13, zhizhelev
 */
package ru.primetalk.synapse.akka

import ru.primetalk.synapse.core._
import akka.event.{LoggingReceive, Logging}
import org.slf4j.MDC
import ru.primetalk.synapse.akka.SpecialActorContacts._
import akka.actor._
import ru.primetalk.synapse.core.Signal
import ru.primetalk.synapse.akka.SpecialActorContacts.InitCompleted
import akka.actor.SupervisorStrategy.Escalate
import SignalProcessing._
/** Escalates all exceptions to upper level. This actor is an appropriate default for
  *  in-channel actors.*/
trait EscalatingActor extends Actor {
  override val supervisorStrategy =
    AllForOneStrategy(){
      case _:Throwable=>Escalate
    }
}
/**
  * Actor that corresponds to the given static system. It will work according to
  * the schema of the system.
  * If there are ActorInnerSubsystem-s within the system, then they will become children actors of this one.
  *
  * @param systemPath the list of intermediate systems from parent actor to the system of this actor
  */
class StaticSystemActor(systemPath:List[String], system: StaticSystem) extends EscalatingActor {
	val log = Logging(context.system, this)

//	var emptyContext = system.s0// Map[Contact[_], Any]()
  var systemState = system.s0
	val processor = StaticSystemActor.toSingleSignalProcessor(context, self)(systemPath, system)

	private def innerProcessSignals(ls: List[Signal[_]]) {
		MDC.put("akkaSource", "" + self.path)
		val results = ls.flatMap {
			signal: Signal[_] =>
				val res = processor(systemState, signal)
        systemState = res._1
        res._2
		}
		if (!results.isEmpty)
			context.parent ! InternalSignals(systemPath, results)
	}

	val processSignals =
		if (system.inputContacts.contains(SenderInput)) // the check is done at the beginning.
			(ls: List[Signal[_]]) ⇒
				innerProcessSignals(Signal(SenderInput, sender) :: ls)
		else
			innerProcessSignals _


	def receive = LoggingReceive {
		case s@Signal(_, _) ⇒
			processSignals(s :: Nil)
		case InternalSignals(path, signals) =>
			processSignals(signals.map(signal =>
        (signal.asInstanceOf[Signal[Any]] /: path )((s, name:String) =>
          Signal(SubsystemSpecialContact:Contact[SubsystemDirectSignal], SubsystemDirectSignal(name, s)).asInstanceOf[Signal[Any]]
        )
      ))
		case nonSignalMessage ⇒
			val s = Signal(NonSignalWithSenderInput, (sender, nonSignalMessage))
			processSignals(s :: Nil)
	}

	override def preStart() {
		if (system.inputContacts.contains(ContextInput))
			processSignals(Signal(ContextInput, context) :: Nil)
		if (system.inputContacts.contains(PreStartInput))
			processSignals(Signal(PreStartInput, context) :: Nil)
		context.parent ! InitCompleted(self)
	}

	override def postStop() {
		if (system.inputContacts.contains(PostStopInput))
			processSignals(Signal(PostStopInput, PostStop) :: Nil)
	}
}

object StaticSystemActor {


	def toSingleSignalProcessor(actorRefFactory: ActorRefFactory, self: ActorRef = Actor.noSender)(path:List[String], system: StaticSystem): SingleSignalProcessor = {
		val actorInnerSubsystemConverter: SubsystemConverter = {
			case (path1, _, ActorInnerSubsystem(subsystem)) =>
				val actorRef = actorRefFactory.actorOf(Props(
					new StaticSystemActor(path1,subsystem)),
					subsystem.name)
				(context: Context, signal) => {
					actorRef.tell(signal, self)
					(context, List())
				}
		}


    val converter = {
      val c = SignalProcessing.componentToSignalProcessor
      c += actorInnerSubsystemConverter
      c
    }
		val signalProcessors = SignalProcessing.systemToSignalProcessors(path, system, converter)
		val proc = new SignalProcessor(signalProcessors, system.name, system.inputContacts, system.outputContacts).processInnerSignals
		proc
	}

	/** Converts top level system to top level actor. */
	def toActorTree(actorRefFactory: ActorRefFactory)(path:List[String], system: StaticSystem): ActorRef =
		actorRefFactory.actorOf(Props(
			new StaticSystemActor(path,system)),
			system.name)
}