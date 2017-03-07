package org.kframework.minikore

import org.junit.Assert.assertEquals
import org.junit.Test
import org.kframework.attributes.Att
import org.kframework.definition
import org.kframework.definition.{ModuleName, NonTerminal}
import org.kframework.kore.ADT
import org.kframework.kore.SortedADT.SortedKVariable
import org.kframework.minikore.converters.KoreToMini._
import org.kframework.minikore.implementation.MiniKore._
import org.kframework.minikore.interfaces.pattern.{Label, Name, Sort, Value}

/**
  * Created by daejunpark on 12/28/16.
  */
class KoreToMiniTest {

  val Int = ADT.Sort("Int", ModuleName("INT-SYNTAX"))
  val Exp = ADT.Sort("Exp", ModuleName("A-SYNTAX"))


  @Test def production1(): Unit = {
    val klabelatt = Application(Label("klabel"), Seq(DomainValue(Label("KString@KSTRING"), Value("_+_"))))
    assertEquals(
      apply(definition.Production.apply("_+_", Exp, Seq(), Att())),
      SymbolDeclaration(Exp.name, "_+_", Seq(), Seq(klabelatt))
    )
  }

  @Test def production2(): Unit = {
    assertEquals(
      apply(new definition.Production(Exp, Seq(NonTerminal(Int)), Att())),
      SymbolDeclaration(Exp.name, "#None", Seq(Int.name), Seq(Application(iNonTerminal, Seq(S(Int.name)))))
    )
  }

  @Test def k1(): Unit = {
    assertEquals(
      apply(SortedKVariable("x", Att())),
      Variable(Name("x"), Sort("K@SORT-K"))
    )
  }
}
