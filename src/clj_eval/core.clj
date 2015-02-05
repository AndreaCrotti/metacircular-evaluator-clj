(ns clj-eval.core)

(defn atom? [x]
  (or (not (seq? x))
      (empty? x)))

(defn null? [x]
  (and (seq? x)
       (empty? x)))

(defn pair
  [xs ys]
  {:pre [(= (count xs) (count ys))]}
  (into {} (map vector xs ys)))

(comment
(into {}  (pair '(x y z) '(1 2 3)))
  )

(defn pair? [sx]
  (= (count sx) 2))

; Some aliases.
(def append concat)

(defn third [x]
  (second (rest x)))
(defn fourth [x]
  (second (rest (rest x))))

(defn error [& msg] (apply println msg))
(defn last-exp? [exps] (= (count exps) 1))

(def ^:private env
  (read-string "{
f (fn [a] (+ 1 a))
g (fn [a b] (+ a b))
x 1
y 2
z 2
}"))

(comment
 (def program (read-string "1"))
  (def program1 (read-string "y"))
  (def program2 (read-string "(+ 1 2)"))
  (def exp (read-string "((fn [x] (+ 2 x)) 3)"))
  (def exp (read-string "(fn [x] (+ 2 x))"))

  (def exp program2)
  (def exp '(procedure [x] ((+ 2 x)) {f (fn [a b] (+ a b)), g (fn [a b] (- a b)), x 1, y 2, z 2}))
  (def exp '((procedure [x] ((+ 2 x)) {f (fn [a b] (+ a b)), g (fn [a b] (- a b)), x 1, y 2, z 2}) 1))
  (def exp '+)
  (l-eval program env)
  (l-eval program1 env)
  (l-eval program2 env)
  (l-eval (read-string "(+ 2 y)") env)
  (l-eval (read-string "(+ (+ 2 y) 4)") env)
  (l-eval (read-string "((fn [x] (+ 2 x)) 3)") env)
  (l-eval 'f env)
  (l-eval '(f 2) env)
  (l-eval '((fn [x] (+ 1 x)) 42) env)
  (l-eval '(g 3 2) env)
  (l-eval (read-string "(f 2)") env)
  (l-eval (read-string "(f 2)") env)
  (l-eval (read-string "(fn [x] (+ 2 x))") env)
  (l-eval '(procedure [x] ((+ 2 x)) {f (fn [a b] (+ a b)), g (fn [a b] (- a b)), x 1, y 2, z 2}) env)
  (operator program2)
  (operands program2)

  (def exp '(f 2))
  (def procedure (l-eval (operator exp) env))
  (def args (list-of-values (operands exp) env))

  (merge (pair '(x y z) '(1 2 3) ) {'a 7})

  (def env '{z 2, y 2, g (fn [a b] (- a b)), f (fn [a b] (+ a b)), x 1})
  (def exps '((+ 2 x)))

  (l-eval '+ env)
  (l-eval '(+ 2 x) env)
  (def exp (first exps))
  (l-eval (first exps) env)
  )


(declare self-evaluating? variable? lookup-var quoted? text-of-quotation assignment? eval-assignment definition? eval-definition
         if? eval-if lambda? make-procedure lambda-parameters lambda-body
         begin? eval-sequence begin-actions cond? cond->if application? l-apply operator operands list-of-values error
         primitive-procedure? apply-primitive-procedure no-operands? first-operand rest-operands
         compound-procedure? eval-sequence procedure-parameters procedure-environment procedure-body
         extend-environment if-predicate if-consequent if-alternative tagged-list? lambda?)

(defn l-eval [exp env]
  (cond (self-evaluating? exp) exp
        (variable? exp) (lookup-var exp env)
        (quoted? exp) (text-of-quotation exp)
        (assignment? exp) (eval-assignment exp env)
        (definition? exp) (eval-definition exp env)
        ;;(if? exp) (eval-if exp env)
        (lambda? exp)
        (make-procedure (lambda-parameters exp)
                        (lambda-body exp)
                        env)
        ;;(begin? exp)
        ;;(eval-sequence (begin-actions exp) env)
        ;;(cond? exp) (l-eval (cond->if exp) env)
        (application? exp)
        (l-apply (l-eval (l-eval (operator exp) env) env)
                 (list-of-values (operands exp) env))
        :else
        (error "Unknown expression type -- EVAL" exp)))

(defn l-apply [procedure args]
  (cond (primitive-procedure? procedure) (apply-primitive-procedure procedure args)
        (compound-procedure? procedure)
        (eval-sequence
         (procedure-body procedure)
         (extend-environment
          (procedure-parameters procedure)
          args
          (procedure-environment procedure)))
        :else (error "Unknown procedure type -- APPLY" procedure)))

(comment
  (list-of-values '(2 x) env)
  (def exps '(x))
  (def exp 'x)
  (def exps '((+ 2 x)))
  (eval-sequence '(+ 3 y) env)
  (l-eval '(+ 3 y) env)
  )

(defn list-of-values [exps env]
  (if (no-operands? exps)
      '()
      (cons (l-eval (first-operand exps) env)
            (list-of-values (rest-operands exps) env))))

(defn extend-environment [procedure-parameters
                          args
                          procedure-environment]
  (merge procedure-environment (pair procedure-parameters args )))


(defn eval-if [exp env]
  (if (true? (l-eval (if-predicate exp) env))
      (l-eval (if-consequent exp) env)
      (l-eval (if-alternative exp) env)))

(defn eval-sequence [exps env]
  (cond (last-exp? exps) (l-eval (first exps) env)
        :else (do (l-eval (first exps) env)
                  (eval-sequence (rest exps) env))))


(defn primitive-procedure? [procedure]
  (true? (some #(= procedure %) '(+))))

(defn apply-primitive-procedure [proc args]
  (if (= proc '+) (apply + args)))

(defn make-procedure [parameters body env]
  (list 'procedure parameters body env))

(defn compound-procedure? [p]
  (tagged-list? p 'procedure))
(defn procedure-parameters [p] (second p))
(defn procedure-body [p] (third p))
(defn procedure-environment [p] (fourth p))

(defn self-evaluating? [exp]
  (cond (number? exp) true
        (string? exp) true
        (= exp '+) true
        (or (= 'procedure exp) (= 'procedure (and (seq? exp) (first exp)))) true
        :else false))

(defn variable? [exp] (symbol? exp))

(defn lookup-var [var env]
  (let [item (env var)]
    (if (nil? item)
      (when (some #(= var %) '(+))
        var)
      item)))

(defn quoted? [exp]
  (tagged-list? exp 'quote))

(defn tagged-list? [exp tag]
  (= (first exp) tag))

(defn assignment? [exp]
  (tagged-list? exp 'set!))
(defn assignment-variable [exp] (second exp))
(defn assignment-value [exp] (first (rest (rest exp))))

(defn definition? [exp]
  (tagged-list? exp 'defn))

(comment
  (lambda? '(fn [x] (+ 2 x)))
  (tagged-list? '(fn [x] (+ 2 x)) 'fn))

(defn lambda? [exp] (tagged-list? exp 'fn))
(defn lambda-parameters [exp] (second exp))
(defn lambda-body [exp] (rest (rest exp)))

(defn operator [exp] (first exp))
(defn operands [exp] (rest exp))
(defn no-operands? [ops] (empty? ops))
(defn first-operand [ops] (first ops))
(defn rest-operands [ops] (rest ops))

(defn application? [exp] true)



(comment


  (lookup-var 'y env)
  (quoted? '(quote (1 2 3) ))
  (pair? '( 1 2))

  )
