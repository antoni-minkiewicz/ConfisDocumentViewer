package eu.dcotta.confis.model

import eu.dcotta.confis.dsl.AgreementBuilder
import eu.dcotta.confis.model.Clause.Permission

data class Agreement(
    val clauses: List<Clause>,
    val parties: List<Party>,
    val title: String? = null,
    val introduction: String? = null
) {
    val actions by lazy { clauses.flatMap { it.extractActions() }.toSet() }
}

fun Agreement(builder: AgreementBuilder.() -> Unit): Agreement = AgreementBuilder(builder)

sealed interface NoCircumstance

sealed interface Clause {
    @JvmInline
    value class Text(val string: String) : Clause, NoCircumstance

    data class PermissionWithCircumstances(
        val permission: Permission,
        val circumstanceAllowance: Allowance,
        val circumstances: CircumstanceMap,
    ) : Clause {
        val sentence by permission::sentence
    }

    data class RequirementWithCircumstances(
        val sentence: Sentence,
        val circumstances: CircumstanceMap,
    ) : Clause

    data class Requirement(val sentence: Sentence) : Clause, NoCircumstance {
        val subject by sentence::subject
        val obj by sentence::obj
        val action by sentence::action

        override fun toString() = "Requirement($sentence)"
    }

    data class Permission(val allowance: Allowance, val sentence: Sentence) : Clause, NoCircumstance {
        val subject by sentence::subject
        val obj by sentence::obj
        val action by sentence::action

        override fun toString() = "Rule($allowance $sentence)"
    }
}
