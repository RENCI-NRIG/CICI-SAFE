defcall postIdSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?CN) :-
  {
    postIdSet($ServerJVM, $ServerPrincipal, $Envs, $CN).
  }.

defcall tagAccessTest(?ServerJVM, ?ServerPrincipal, ?Envs, ?U) :-
  {
    tagAccessTest($ServerJVM, $ServerPrincipal, $Envs, $U).
  }.


defcall tagAccessTestForAlice(?ServerJVM, ?Envs, ?U) :-
  {
    tagAccessTest($ServerJVM, alice, $Envs, $U).
  }.


defcall postPolicy(?ServerJVM, ?ServerPrincipal, ?Envs, ?PolicyId) :-
  {
    postPolicy($ServerJVM, $ServerPrincipal, $Envs, $PolicyId).
  }.


defcall postSubjectSet(?ServerJVM, ?ServerPrincipal, ?Envs) :-
  {
    postSubjectSet($ServerJVM, $ServerPrincipal, $Envs).
  }.

defcall updateSubjectSet(?ServerJVM, ?ServerPrincipal, ?Envs, ?Token) :-
  {
    updateSubjectSet($ServerJVM, $ServerPrincipal, $Envs, $Token).
  }.


//hangCall(?A, ?B, ?C) :- ?Token := tagAccessTestForAlice(?A, ?B, ?C), updateSubjectSet(...., ?Token).
