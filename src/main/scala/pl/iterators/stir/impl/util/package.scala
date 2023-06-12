package pl.iterators.stir.impl

package object util {
  private[stir] implicit def enhanceByteArray(array: Array[Byte]): EnhancedByteArray = new EnhancedByteArray(array)
  private[stir] implicit def enhanceString_(s: String): EnhancedString = new EnhancedString(s)
//  private[http] implicit def enhanceRegex(regex: Regex): EnhancedRegex = new EnhancedRegex(regex)
//  private[http] implicit def enhanceByteStrings(byteStrings: TraversableOnce[ByteString]): EnhancedByteStringTraversableOnce =
//    new EnhancedByteStringTraversableOnce(byteStrings)
}
