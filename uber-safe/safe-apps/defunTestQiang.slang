
defenv Z :- 6.


defun funTestString(?X, ?Y) :-
  ?Z := 2,
  `
     val s =  $X + $Y.toInt * $Z.toInt
     println(s)
     s 
  `.

defun funTest(?X, ?Y) :-
  ?Z := 2,
  `
     println("hi world")
     val s =  $X + $Y.toInt * $Z.toInt
     s 
  `.

defun funTest1(?X, ?Y) :-
  ?Z := 4,
  false,
  `
     $X.toInt * $Y.toInt * $Z.toInt
  `.

assert defun funTestAssert(?X, ?Y) if
  ?Y is 4 and
  true and // ;
  `
     $X.toInt * $Y.toInt * $Z.toInt
  `
  end

//funTest(?X, ?Y) :- ?Y := 4, -(?X, ?Y); ?Y := 10, -(?Y, ?X). // unsound rule


defun doubleFunTest(?X, ?Y) :-
  ?Z := funTest(?X, ?Y), // can diverge here due to multiple funTest definitions
  `
     $X.toInt * $Y.toInt * $Z.toInt
  `.

defun nestedFunTest(funTest(4, 6), ?Y) :-
  ?Z := 2,
  `
     $Z.toInt * $Y.toInt
  `.

//definit funTest(8, ?A).
//definit doubleFunTest(8, ?A).
//definit nestedFunTest(?A, ?B).
