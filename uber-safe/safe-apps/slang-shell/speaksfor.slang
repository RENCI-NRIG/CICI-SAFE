defcall postIdSet(?CN) :-
  {
    postIdSet($ServerJVM, $Envs, $CN).
  }.

defcall setupSpeaksFor(?IssuerID) :-
  {
     setupSpeaksFor($ServerJVM, $Envs, $IssuerID).
  }.

defcall validateSpeaksFor(?IssuerID, ?SubjectID, ?SpeaksForToken) :-
  {
    validateSpeaksFor($ServerJVM, $Envs, $IssuerID, $SubjectID, $SpeaksForToken).
  }.

defcall postAccessPriv(?SubjectID, ?SpeaksForRef, ?GroupID, ?ObjID) :-
  {
    postAccessPriv($ServerJVM, $Envs, $SubjectID, $SpeaksForRef, $GroupID, $ObjID).
  }.

defcall checkPriv(?Authority, ?GroupID, ?ObjectID) :-
  {
    checkPriv($ServerJVM, $Envs, $Authority, $GroupID, $ObjectID).
  }.
