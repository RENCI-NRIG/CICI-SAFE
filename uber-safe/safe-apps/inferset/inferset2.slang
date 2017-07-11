defcon subfacts() :- 
  spec('www'),
  {
    tagFile(file1, tag0).
    tagFile(file1, tag1).
    label("sub facts").
  }.

defcon facts() :-
  spec('yyy'),
  ?SubfactsRef := label("sub facts"),
  {
    link($SubfactsRef).
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

defcon policySnippet() :-
  spec('xxx'),
  {
    p(?X) :- q(?X).
    m(?Y) :- n(?Y).
    v(?X) :- p(?X).
    v(?X) :- m(?X).
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
  ?policyRuleRef := policySnippet(),
  {
    link($SubgoalsRef). 
    link($myPolicyRef).
    link($policyRuleRef).
    v(bob)? =@= require 
  }.

definit subfacts().
//definit mymethod().

