package adpro
import scala.language.higherKinds

// This implementation is designed to be eager, following the regular strictness
// of Scala.  However it would be an interesting exercise to extend it so that
// it is possibly lazy, like in the paper of Hinze and Paterson.  The obvious
// choice is to make values of elements stored in the queue lazy.  Then there is
// also a discussion of possible suspension of the middle element of the tree on
// page 7.


object data {

  // The interface spec for reducible structures, plus two useful derived
  // reducers that the paper introduces (toList and toTree)


  trait Reduce[F[_]] {
    def reduceR[A, B] (opr: (A,B) => B) (fa: F[A], b: B) :B
    def reduceL[A, B] (opl: (B,A) => B) (b: B, fa: F[A]) :B

    // page 3

    def toList[A](fa: F[A]) :List[A] = reduceL[A, List[A]]((a, b) => b :: a )( Nil, fa)

    def toTree[A](fa: F[A]) :FingerTree[A] = reduceL[A, FingerTree[A]]( (a, b) => 
                                                  FingerTree.addL[A](b, a))(Empty(), fa)
  }


  type Digit[A] = List[A]

  sealed trait Node[+A]{ 

     def toList :List[A] = Node.toList (this)
  }


  case class Node2[A] (l :A, r :A) extends Node[A]
  case class Node3[A] (l: A, m: A, r: A) extends Node[A]

  sealed trait FingerTree[+A] {


     def addL[B >:A] (b: B) :FingerTree[B] = FingerTree.addL (b,this)
     def addR[B >:A] (b: B) :FingerTree[B] = FingerTree.addR (this,b)

     def toList :List[A] = FingerTree.toList (this)

     def headL :A = FingerTree.headL (this)
     def tailL :FingerTree[A] = FingerTree.tailL (this)
     def headR :A = FingerTree.headR (this)
     def tailR :FingerTree[A] = FingerTree.tailR (this)

    // page 7 (but this version uses polymorphis for efficiency, so we can
    // implement it differently; If you want to follow the paper closely move them to
    // FingerTree object and delegate the methods, so my tests still work.
    //
     def empty :Boolean = this match {
       case Empty() => true
       case _ => false
     }
     def nonEmpty :Boolean = this match {
       case Empty() => false
       case _ => true
     }
  }
  case class Empty () extends FingerTree[Nothing] {

    // page 7
    //
     override def empty =  true 
     override def nonEmpty = false
  }
  case class Single[A] (data: A) extends FingerTree[A]
  // paramter names: pr - prefix, m - middle, sf - suffix
  case class Deep[A] (pr: Digit[A], m: FingerTree[Node[A]], sf: Digit[A]) extends FingerTree[A]

  // page 6
  //
  // Types of views on trees
  // The types are provided for educational purposes.  I do not use the view
  // types in my implementation. I implement views as Scala extractors.
  // But you may want to implement views first like in the paper, and then
  // conver them to Scala extractors.

  // In the paper views are generic in the type of tree used. Here I make them
  // fixed for FingerTrees.

  sealed trait ViewL[+A]
  case class NilTree () extends ViewL[Nothing]
  case class ConsL[A] (hd: A, tl: FingerTree[A]) extends ViewL[A]

  // Left extractors for Finger Trees (we use the same algorithm as viewL in the
  // paper). You can do this, once you implemented the views the book way.
  // Once the extractors are implemented you can pattern match on NilTree, ConsL
  // and ConsR
  //
  // See an example extractor implemented for Digit below (Digit.unapply)

  object NilTree { // we use the same extractor for both left and right views
     def unapply[A] (t: FingerTree[A]) :Boolean = t.empty 
  }

  object ConsL {
     def unapply[A] (t: FingerTree[A]) :Option[(A,FingerTree[A])] = t match{
       case Empty() => None
       case Single(x) => Option((x, Empty()))
       case Deep((h :: t), m, sf) => Some((h ,FingerTree.deepL[A](t, m, sf)))   
     }
  }

  object ConsR {
     def unapply[A] (t: FingerTree[A]) :Option[(FingerTree[A],A)] = t match {
       case Empty() => None 
       case Single(x) => Some((Empty(), x))
       case Deep((h :: t), m, sf) => Some((FingerTree.deepL[A](t, m, sf), h))
     }
  }

  object Digit  extends Reduce[Digit] {  

     def reduceR[A, B] (opr: (A, B) => B) (d: Digit[A], z: B) :B = d.foldRight(z)(opr)
     def reduceL[A, B] (opl: (B, A) => B) (z: B, d: Digit[A]) :B = d.foldLeft(z)(opl)


    def apply[A] (as: A*) : Digit[A] = List(as:_*)

    def unapplySeq[A] (d: Digit[A]): Option[Seq[A]] = Some (d)
  }


  object Node  extends Reduce[Node] {

     def reduceR[A, B] (opr: (A, B) => B) (n :Node[A], z: B) :B = 
       n match {
         case Node2(a, b) => opr(a, opr(b, z))
         case Node3(a, b, c) => opr(a, opr(b, opr(c ,z)))
       }
     def reduceL[A, B] (opl: (B, A) => B) (z: B, n :Node[A]) :B = 
       n match {
         case Node2(a, b) =>  opl(opl(z, a), b)
         case Node3(a, b, c) => opl(opl(opl(z, a), b), c)
       }
    override def toList[A](n: Node[A]) : List[A] = n match{
       case Node2(a, b) => List(a, b)
       case Node3(a, b, c) => List(a, b, c)
     }
   }


  object FingerTree extends Reduce[FingerTree] { 

   
    def reduceR[A, B] (opr: (A, B) => B) (t: FingerTree[A], z: B) :B =  
      t match {
        case Empty () => z
        case Single(x) => opr(x, z)
        case Deep(pr, m, ps) => {
          val oprNode: (Node[A], B) => B  = Node.reduceR[A, B](opr)(_ , _) 
          Digit.reduceR[A, B](opr)(pr, FingerTree.reduceR[Node[A] ,B](oprNode)(m, Digit.reduceR[A ,B](opr)(pr, z)))
        }
      }

     def reduceL[A, B] (opl: (B, A) => B) (z: B, t: FingerTree[A]) :B = 
       t match {
         case Empty() => z
         case Single(x) => opl(z, x)
         case Deep(pr, m, ps) => {
           val oplNode: (B, Node[A]) => B = Node.reduceL[A, B](opl)(_ , _)
           Digit.reduceL[A, B](opl)(FingerTree.reduceL[Node[A], B](oplNode)( Digit.reduceL[A, B](opl)(z, pr),m), pr)
         }
       }
    // page 5 bottom (the left triangle); Actually we could use the left
    // triangle in Scala but I am somewhat old fashioned ...

     def addL[A](a: A, t: FingerTree[A]): FingerTree[A]  = t match {
       case Empty() => Single(a)
       case Single(x) => Deep(Digit(a), Empty(), Digit(x)) 
       case Deep(Digit(b, c, d, e), m, ps) => Deep(Digit(a,b), addL(Node3(c, d, e), m), ps)
       case Deep(pr, m, ps) => Deep( a :: pr, m, ps)
     }

     def addR[A] (t: FingerTree[A], a: A): FingerTree[A]  = t match {
       case Empty() => Single(a)
       case Single(x) => Deep(Digit(x), Empty(), Digit(a))
       case Deep(pr, m, Digit(b, c, d, e)) => Deep(pr, addR(m, Node3(b, c, d)), Digit(e, a))
       case Deep(pr, m ,ps) => Deep(pr, m, ps ++  Digit(a))
     }
     override def toList[A](t: FingerTree[A]): List[A] =
       FingerTree.reduceL[A, List[A]]((a, b) =>  b :: a )(Nil, t)

    // page 6
    //
    // This is a direct translation of view to Scala. You can replace it later
    // with extractors in Scala, see above objects NilTree and ConsL (this is an
    // alternative formulation which is more idiomatic Scala, and slightly
    // better integrated into the language than the Haskell version).
    // In Haskell we need to call viewL(t) to pattern match on views.  In Scala,
    // with extractors in place, we can directly pattern match on t.
    //


    // A smart constructor that allows pr to be empty
    def deepL[A] (pr: Digit[A], m: FingerTree[Node[A]], sf: Digit[A]) :FingerTree[A] = 
      pr match {
        case Nil => 
          m match {
            case Empty() => Digit.reduceL[A, FingerTree[A]]((a, b) => FingerTree.addL[A](b, a))(Empty(), sf)
            case ConsL(a, tail) => Deep(Node.reduceL[A, List[A]]((a, b) => b::a)(Nil, a), m, sf)
          }
       case _ => Deep(pr, m, sf)
      }



    // A smart constructor that allows sf to be empty
     def deepR[A](pr: Digit[A], m: FingerTree[Node[A]], sf: Digit[A]) : FingerTree[A]  = 
       sf match {
         case Nil =>
         m match{
           case Empty() => Digit.reduceR[A, FingerTree[A]]((b, a) => FingerTree.addR[A](a, b))(pr, Empty())
           case ConsR(tail, a) => Deep(Node.reduceR[A, List[A]]((b, a) => b :: a)(a, Nil), m, pr)
         }
           case _ => Deep(pr, m, sf)
       }


    // page 7
     def headL[A] (t: FingerTree[A]): A = t match { 
         case ConsL(a, h) => a
       }
         
     def tailL[A] (t: FingerTree[A]): FingerTree[A] = t match {
       case ConsL(h, t: FingerTree[A]) => t
     }
       
     def headR[A] (t: FingerTree[A]): A = t match {
       case ConsR(_, a) => a
     }
     def tailR[A] (t: FingerTree[A]) :FingerTree[A] = t match {
       case ConsR(tail : FingerTree[A], _) => tail
     }
  }
  
}



