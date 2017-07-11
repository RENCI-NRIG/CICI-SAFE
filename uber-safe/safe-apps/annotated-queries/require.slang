defcon policySnippet() :-
  spec('xxx'),
  {
    p(?X) :- q(?X).
    m(?Y) :- n(?Y).
    v(?X) :- p(?X).
    v(?X) :- m(?X).
    label("policy rule").
  }.

defguard mymethod() :-
  ?PolicyRuleRef := policySnippet(),
  {
    link($PolicyRuleRef).
    q(bob).
    n(alice).
    //q(david)?
    v(bob)? =@= require 
    v(alice)? =@= require 
  }.

definit mymethod().

