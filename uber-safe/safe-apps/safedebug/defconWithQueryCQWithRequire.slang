defcon policySnippet() :-
   spec('xxx'),
   {
     p(?X) :- z(?X).
     p(?X) :- q(?X), t(?X).
     name("policy rule").
   }.

defcon mypolicy() :-
  {
    q(alice).
    t(alice).
    name("my policy").
  }.

(* defguard mymethod(?X) :- *)
defguard mymethod() :-
  ?RuleRef   := policySnippet(),
  ?PolicyRef := mypolicy(),
  { 
    import($RuleRef).
    import($PolicyRef).
    p(alice)?
  }.

definit mymethod().

(*    q($X).
    allow() :- p($X).
    allow()?
*)
