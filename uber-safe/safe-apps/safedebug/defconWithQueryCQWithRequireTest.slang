defcon policySnippet() :-
  spec('xxx'),
  {
    p(?X) :- q(?X).
    m(?Y) :- n(?Y).
    name("policy rule").
  }.

defcon mypolicy() :-
  {
    q(alice).
    q(bob).
    name("my policy").
  }.

(* defguard mymethod(?X) :- *)
defguard mymethod() :-
  ?RuleRef   := policySnippet(),
  ?PolicyRef := mypolicy(),
  { 
    import($RuleRef).
    import($PolicyRef).
    l(alice)? =:= allow
    m(?X)? =:= require
  }.

definit mymethod().

(*    q($X).
    allow() :- p($X).
    allow()?
*)


    (* p(alice) =:= require *)
