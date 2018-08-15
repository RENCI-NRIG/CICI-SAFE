// import("../speaksFor.slang").

defcon grantSpeaksFor(?IssuerID) :-
  spec('A subject issues a statement to grant speaksFor to an issuer'),
  {
    speaksFor($IssuerID, false).
    label("grantSpeaksfor/$IssuerID").
  }.

defpost setupSpeaksFor(?IssuerID) :- [grantSpeaksFor(?IssuerID)].

defcon makeMyIdentitySet(?CN) :-
  spec('Construct an identity set for self'),
  {
    principal($SelfKey) :- spec("identity set for $CN").
  }.

defpost postIdSet(?CN) :- [makeMyIdentitySet(?CN)].

defcon accessPrivBySpeaksFor(?SubjectID, ?SpeaksForRef, ?GroupID, ?ObjID) :-
  spec('exercise speaksFor'),
  {
    subject($SubjectID, $SpeaksForRef).
    grantAccessPriv($GroupID, $ObjID).
    label("speaksForExample/$GroupID/$ObjID"). 
    // this set is stored under the issuer's namespace in SafeSets
    // the subject can use this by creating a credential set containing
    // a link to this set.
  }.

defpost postAccessPriv(?SubjectID, ?SpeaksForRef, ?GroupID, ?ObjID) :-
  [accessPrivBySpeaksFor(?SubjectID, ?SpeaksForRef, ?GroupID, ?ObjID)].

defcon privPolicySet() :-
  {
    approvedPriv(?Authority, ?GroupID, ?ObjectID) :- ?Authority: grantAccessPriv(?GroupID, ?ObjectID).
    label('priv-policy').
  }.

definit privPolicySet().
 
defguard checkPriv(?Authority, ?GroupID, ?ObjectID) :-
  spec('Check privilege delegation'),
  ?PolicySet := label('priv-policy'),
  {
    link($BearerRef).
    link($PolicySet).
    approvedPriv($Authority, $GroupID, $ObjectID)?
  }.
