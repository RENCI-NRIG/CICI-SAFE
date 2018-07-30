defcon grantSpeaksfor(?IssuerID) :-
  spec('A subject issues a statement to grant speaksFor to an issuer'),
  {
    speaksFor($IssuerID, false).
    label("grantSpeaksfor/$IssuerID").
  }.

defpost postASpeaksfor(?IssuerID) :- [grantSpeaksfor(?IssuerID)].

defcon speaksForPolicySet() :-
  {
     validSpeaksFor(?IssuerID, ?SubjectID) :- ?SubjectID: speaksFor(?IssuerID, _).
     // TODO: dealing with delegation of speaksFor
     label('speaksfor-policy').
  }.

definit speaksForPolicySet().
 

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
    // the subject can use this as credential by creating a linking
    // to this set.
  }.

defpost postAccessPriv(?SubjectID, ?SpeaksForRef, ?GroupID, ?ObjID) :-
  [accessPrivBySpeaksFor(?SubjectID, ?SpeaksForRef, ?GroupID, ?ObjID)].

defguard validateSpeaksFor(?IssuerID, ?SubjectID, ?SpeaksForToken) :-
  spec('Check eligibility of speaksFor'),
  ?PolicySet := label('speaksfor-policy'),
  {
    link($SpeaksForToken).
    link($PolicySet).
    validSpeaksFor($IssuerID, $SubjectID)?
  }.
