
defenv Z :- 6.

defun funTest(?X, ?Y) :-
  ?Y := 2,
  `
     println("hi world")
     $X + $Y.toInt * $Z.toInt
  `.

defun funTest(?X, ?Y) :-
  ?Y := 4,
  false,
  `
     $X.toInt * $Y.toInt * $Z.toInt
  `.

assert defun funTest(?X, ?Y) if
  ?Y is 4 and
  true and // ;
  `
     $X.toInt * $Y.toInt * $Z.toInt
  `
  end

//funTest(?X, ?Y) :- ?Y := 4, -(?X, ?Y); ?Y := 10, -(?Y, ?X). // unsound rule


defun funTest(?X, ?Y) :-
  ?Z := 2,
  ?Y := 4,
  `
     $X.toInt * $Y.toInt * $Z.toInt
  `.

defun doubleFunTest(?X, ?Y) :-
  ?Z := funTest(?X, ?Y), // can diverge here due to multiple funTest definitions
  ?Y := 2,
  `
     $X.toInt * $Y.toInt * $Z.toInt
  `.

(*
defun nestedFunTest(?X := funTest(4, 6), ?Y) :-
  ?Y := 2,
  `
     $X.toInt * $Y.toInt // TODO: ?X.toInt throws None.get error; suppress trace and write custom error
  `.
*)

defun nestedFunTest(funTest(4, 6), ?Y) :-
  ?Y := 2,
  `
     $Z.toInt * $Y.toInt
  `.

//definit funTest(8, ?A).
definit doubleFunTest(8, ?A).
//definit nestedFunTest(?A, ?B).
