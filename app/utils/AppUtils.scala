import scala.concurrent.Future

class Stack[+A] {
  def push[B >: A](elem: B): Stack[B] = new Stack[B] {
    override def top: B = elem
    override def pop: Stack[B] = Stack.this
    override def toString() = elem.toString() + " " +
      Stack.this.toString()
  }
  def top: A = sys.error("no element on stack")
  def pop: Stack[A] = sys.error("no element on stack")
  override def toString() = ""
}

object VariancesTest extends App {

  import scala.concurrent.ExecutionContext.Implicits.global

  val f1 = Future {
    Thread.sleep(100)
    1 + 2
  }

  for (
    v1 <- f1
  ) yield {
    println("v1 " + v1)
  }

  f1.onComplete(i => println(i))
  f1.onComplete(i => println(i))
  f1.onComplete(i => println(i.map(_ + 1)))

  f1.onSuccess {case i => println("v2 " + i) }

  println("Waiting for future")

  Thread.sleep(1000)
}