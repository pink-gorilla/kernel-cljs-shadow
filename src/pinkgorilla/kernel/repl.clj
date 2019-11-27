(ns pinkgorilla.kernel.repl)


(defmacro doc
  "Prints documentation for a var or special form given its name"
  [name]
  `(pinkgorilla.kernel.repl/doc* '~name))
