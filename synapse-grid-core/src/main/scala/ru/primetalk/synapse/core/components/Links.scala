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
 * Created: 28.06.13, zhizhelev
 */
package ru.primetalk.synapse.core.components

import scala.collection.GenTraversableOnce

/**
 * The Link is represented with a triple of two contacts and a linkInfo
 *
 * Special care is taken for type variants.
 * Contacts are invariant because data is "set" and "get".
 * However, the function is contra- and co- variant, because it is a function. So we have two additional types TL1, TL2.
 */
case class Link[T1, T2, -TL1 >: T1, +TL2 <: T2](from: Contact[T1], to: Contact[T2],
                                                name: String,
                                                info: LinkInfo[TL1, TL2]) extends Named with TwoPoleComponent[T1, T2] {
  def toTriple = (from, to, info)
  override def toString = s"Link($name, ${info.getClass.getSimpleName})"
}

/** LinkInfo are individually processed functions with different signatures.
  * */
sealed trait LinkInfo[-T1, +T2]

/**
 * The kind of link that does sequential transformation of data.
 */
case class FlatMapLink[-T1, +T2](f: T1 ⇒ GenTraversableOnce[T2])
  extends LinkInfo[T1, T2]

/**
 * The kind of link that does sequential transformation of data.
 * The function itself has state that is transformed every time.
 * Prefer to use StateZipLink (?)
 */
case class StatefulFlatMapLink[S, -T1, +T2](
	f: (S, T1) ⇒ (S, GenTraversableOnce[T2]),
  stateHolder: StateHandle[S])
  extends LinkInfo[T1, T2]

/** Zips state value with the inner data and */
case class StateZipLink[S, -T1, +T2 >: T1](stateHolder: StateHandle[S])
  extends LinkInfo[T1, (S, T2)]

/**
 * This link can connect contacts of the same type.
 */
case class NopLink[-T1, +T2 >: T1]()
  extends LinkInfo[T1, T2]


/** Prioritize contacts when some data passes through this link. Processes until the data is only on stopContacts.
  * "Fires" starting from the initial contact until "firewall" contacts.
  * TODO: move to Components.
  */
case class RedMapLink[-T1, +T2](stopContacts: Set[Contact[_]]) extends LinkInfo[T1, T2]

