defenv Selfie() :-
  spec('Load the principal keypair'),
  principal($1).

defcon facts() :-
  spec('yyy'),
  {
    tagFile(file1, tag0).
    tagFile(file1, tag1).
    tagFile(file1, tag2).
    tagFile(file1, tag3).
    label("some facts").
  }.

defcon rules() :-
  spec('zzz'),
  {
    accessTag(?P, ?T) :- tagFile(?F, ?T), accessFile(?P, ?F).
    accessFile(alice, file1).
    accessTag(?P, ?T)?
    label("some rules").
  }.

defcon policySnippet(?Param) :-
  spec('xxx'),
  {
    test($Param).
    p(?X) :- q(?X).
    m(?Y) :- n(?Y).
    v(?X) :- p(?X).
    v(?X) :- m(?X).
    exam(?X) :- test(?X).
    label("policy rule").
  }.

defcon mypolicy() :-
  {
    accessTag(alice, tag0).
    accessTag(alice, tag1).
    accessTag(alice, tag2).
    accessTag(alice, tag3).
    q(bob).
    n(bob).
    label("my policy").
  }.

defguard mymethod() :-
  ?FactsRef := facts(),
  ?RulesRef := rules(),
  ?SubgoalsRef := inferQuerySet(?RulesRef, ?FactsRef),  
  ?myPolicyRef := mypolicy(),
  ?policyRuleRef := policySnippet(xvalue),
  {
    link($SubgoalsRef). 
    link($myPolicyRef).
    link($policyRuleRef).
    v(bob)? =@= require 
    exam(?X)? =@= require 
  }.

//alice: v(bob)? =@= require 
//definit mymethod().

