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
    v(bob).
    v(tom)? =@= allow 
    //v(alice)? =@= allow 
    v(cindy)? =@= allow
    v(bob)? =@= require
  }.

definit mymethod().

