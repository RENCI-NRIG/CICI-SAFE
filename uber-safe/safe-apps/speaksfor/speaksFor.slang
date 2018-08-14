defcon speaksForPolicySet() :-
  {
     validSpeaksFor(?IssuerID, ?SubjectID) :- ?SubjectID: speaksFor(?IssuerID, _).
     // TODO: extending to deal with delegation of speaksFor
     label('speaksfor-policy').
  }.

definit speaksForPolicySet().
 
defguard validateSpeaksFor(?IssuerID, ?SubjectID, ?SpeaksForToken) :-
  spec('Check eligibility of speaksFor'),
  ?PolicySet := label('speaksfor-policy'),
  {
    link($SpeaksForToken).
    link($PolicySet).
    validSpeaksFor($IssuerID, $SubjectID)?
  }.
