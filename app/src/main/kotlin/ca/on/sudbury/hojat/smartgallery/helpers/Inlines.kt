package ca.on.sudbury.hojat.smartgallery.helpers

inline fun <T> Iterable<T>.sumByLong(selector: (T) -> Long) = this.sumOf { selector(it) }

inline fun <T> Iterable<T>.sumByInt(selector: (T) -> Int) = this.sumOf { selector(it) }
