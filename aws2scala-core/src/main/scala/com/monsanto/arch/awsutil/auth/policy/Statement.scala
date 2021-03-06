package com.monsanto.arch.awsutil.auth.policy

/** A statement describes a rule for allowing or denying access to a specific
  * AWS resource based on how the resource is being accessed, and who is
  * attempting to access the resource.  Statements can also optionally contain
  * a list of conditions that specify when a statement is to be honored.
  *
  * @param id         the optional identifier for the statement
  * @param principals indicates the user (IAM user, federated user, or
  *                   assumed-role user), AWS account, AWS service, or other
  *                   principal entity that it allowed or denied access to a
  *                   resource.  Note that you can use
  *                   [[Statement.allPrincipals allPrincipals]] as a handy
  *                   value to match any principal.
  * @param effect     whether the statement allows or denies access
  * @param actions    indicates the ways in which the principals are trying to
  *                   interact with the resources
  * @param resources  the AWS entities the principal is trying to access
  * @param conditions optional constraints that indicate when to allow or deny
  *                   a principal access to a resources
  */
case class Statement(id: Option[String],
                     principals: Set[Principal],
                     effect: Statement.Effect,
                     actions: Seq[Action],
                     resources: Seq[Resource],
                     conditions: Set[Condition]) {
  if (principals != Statement.allPrincipals && principals.contains(Principal.AllPrincipals)) {
    throw new IllegalArgumentException("You may only use the AllPrincipals by itself.")
  }

  if (actions != Statement.allActions && actions.contains(Action.AllActions)) {
    throw new IllegalArgumentException("You may only use the AllActions by itself.")
  }

  if (resources != Statement.allResources && resources.contains(Resource.AllResources)) {
    throw new IllegalArgumentException("You may only use the AllResources by itself.")
  }
}

object Statement {
  /** A constant for applying a statement to all principals. */
  val allPrincipals: Set[Principal] = Set(Principal.AllPrincipals)

  /** A constant for applying a statement to all principals. */
  val allActions: Seq[Action] = Seq(Action.AllActions)

  /** A constant for applying a statement to all resources. */
  val allResources: Seq[Resource] = Seq(Resource.AllResources)

  /** Enumeration type for statement effects. */
  sealed abstract class Effect(val name: String)

  object Effect {
    /** Explicitly allow access. */
    case object Allow extends Effect("Allow")
    /** Explicitly deny access. */
    case object Deny extends Effect("Deny")

    /** List of all possible `Effect` values. */
    val values: Seq[Effect] = Seq(Allow,Deny)

    /** Utility to build/extract `Effect` objects from their name. */
    object fromName {
      /** Gets the `Effect` instance corresponding to the given name.
        *
        * @param name the name of the effect
        * @throws java.lang.IllegalArgumentException if no Effect exists with the given name
        */
      def apply(name: String): Effect =
        unapply(name).getOrElse(throw new IllegalArgumentException(s"’$name‘ is not a valid effect name."))

      /** Extracts an `Effect` from a string. */
      def unapply(name: String): Option[Effect] = values.find(_.name == name)
    }
  }
}
