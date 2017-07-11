defcall postIdSet(?CN) :-
  {
    postIdSet($ServerJVM, $Envs, $CN).
  }.

defcall tagAccessTest(?U) :-
  {
    tagAccessTest($ServerJVM, $Envs, $U).
  }.

defcall postPolicyIPV4() :-
  {
    postPolicyIPV4($ServerJVM, $Envs).
  }.

defcall testipv4(?IP) :-
  {
    testipv4($ServerJVM, $Envs, $IP).
  }.

defcall tagAccessTestForAlice(?U) :-
  {
    tagAccessTest($ServerJVM, $Envs, $U).
  }.


defcall postPolicy(?PolicyId) :-
  {
    postPolicy($ServerJVM, $Envs, $PolicyId).
  }.


defcall postSubjectSet() :-
  {
    postSubjectSet($ServerJVM, $Envs).
  }.

defcall updateSubjectSet(?Token) :-
  {
    updateSubjectSet($ServerJVM, $Envs, $Token).
  }.


//hangCall(?A, ?B, ?C) :- ?Token := tagAccessTestForAlice(?A, ?B, ?C), updateSubjectSet(...., ?Token).
