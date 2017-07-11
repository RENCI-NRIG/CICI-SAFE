defcall checkMembership() :-
  {
    checkMembership($ServerJVM, $Envs).
  }.

defcall tagAccessTest(?U) :-
  {
    tagAccessTest($ServerJVM, $Envs, $U).
  }.

