case class Cart(items: Set[Item]) {

  def +(item: Item) = copy(items + item)

  def -(item: Item) = copy(items - item)

  def isEmpty: Boolean = items.isEmpty

  def size: Integer = items.size

  def contains(item: Item): Boolean = items.contains(item)
}

object Cart {
  val empty = new Cart(Set.empty)
}
