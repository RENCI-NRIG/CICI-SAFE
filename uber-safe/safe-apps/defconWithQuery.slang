defcon policySnippet() :-
   spec('xxx'),
   {
     p(?X) :- q(?X).
     m(?Z) :- n(?Z).
     label("policy rule").
   }.

defcon mypolicy() :-
  {
    q(alice).
    n(bob).
    label("my policy").
  }.

defguard mymethod() :-
  ?RuleRef   := policySnippet(),
  ?PolicyRef := mypolicy(),
  { 
    link($RuleRef).
    link($PolicyRef).
    p(?X)?
    m(?X)?
  }.

definit mymethod().
